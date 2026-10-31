package dev.herdr.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.SettingsStore
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.ConnectionSupervisor
import dev.herdr.core.herdr.Pane
import dev.herdr.core.ssh.HostKeyUnknownException
import dev.herdr.core.ssh.SshConfig
import dev.herdr.core.ssh.SshConnector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * 看板的单一数据源：把 ConnectionSupervisor 的实时状态 + Room 缓存合成 UI 状态。
 *
 * 单向数据流：UI 只发 Action，所有状态由这里产出。
 */
class BoardViewModel(
    private val settings: SettingsStore,
    private val repository: BoardRepository,
    private val connector: SshConnector,
) : ViewModel() {

    data class HostKeyPrompt(val hostId: String, val fingerprint: String)

    data class UiState(
        val host: SettingsStore.Host = SettingsStore.Host(),
        val hasKey: Boolean = false,
        val connKind: ConnKind = ConnKind.IDLE,
        val connText: String = "未连接",
        val groups: List<BoardGrouper.Group> = emptyList(),
        val counts: Map<AgentStatus, Int> = emptyMap(),
        val panes: List<Pane> = emptyList(),
        val version: String = "",
        val protocol: Int = 0,
        val updatedAtMs: Long = 0,
        val stale: Boolean = true,
        val refreshing: Boolean = false,
        val lastError: String? = null,
        val hostKeyPrompt: HostKeyPrompt? = null,
        val selectedPane: Pane? = null,
    )

    enum class ConnKind { IDLE, CONNECTING, AWAITING_HOST_KEY, CONNECTED, STALE, RECONNECTING, FAILED }

    private val _state = MutableStateFlow(UiState(hasKey = settings.hasKey()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val supervisor = ConnectionSupervisor(
        scope = viewModelScope,
        connector = connector,
        configProvider = { settings.sshConfig() },
        onUnknownHostKey = { ex -> awaitHostKeyDecision(ex) },
    )

    private var pendingHostKey: CompletableDeferred<Boolean>? = null

    init {
        supervisor.start()
        observeSupervisor()
        observeReconciler()
        observeCache()
        refreshHostFields()
    }

    // ---------- Actions ----------

    fun refreshHostFields() {
        _state.value = _state.value.copy(host = settings.host(), hasKey = settings.hasKey())
    }

    fun saveHost(h: SettingsStore.Host) {
        settings.saveHost(h)
        refreshHostFields()
    }

    fun importKey(text: String): Result<File> {
        val r = settings.importKey(text)
        refreshHostFields()
        return r
    }

    fun clearKey() {
        settings.clearKey()
        refreshHostFields()
    }

    fun connect() = supervisor.reconnectNow()

    fun disconnect() = supervisor.stop()

    fun reconnect() = supervisor.reconnectNow()

    fun refreshNow() = supervisor.reconciler.markDirty()

    fun selectPane(pane: Pane?) {
        _state.value = _state.value.copy(selectedPane = pane)
    }

    fun answerHostKey(accept: Boolean) {
        pendingHostKey?.complete(accept)
        pendingHostKey = null
        _state.value = _state.value.copy(hostKeyPrompt = null)
    }

    private fun awaitHostKeyDecision(ex: HostKeyUnknownException): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingHostKey?.cancel()
        pendingHostKey = deferred
        _state.value = _state.value.copy(
            hostKeyPrompt = HostKeyPrompt(ex.id.toString(), ex.fingerprint),
            connKind = ConnKind.AWAITING_HOST_KEY,
            connText = "等待确认主机指纹",
        )
        // sshj 的校验线程上同步等待用户确认（最多 90s）
        return runBlocking { withTimeoutOrNull(90_000) { deferred.await() } ?: false }
    }

    // ---------- 状态收集 ----------

    private fun observeSupervisor() {
        viewModelScope.launch {
            supervisor.state.collect { st ->
                val (kind, text) = when (st) {
                    is ConnectionSupervisor.State.Idle -> ConnKind.IDLE to "未连接"
                    is ConnectionSupervisor.State.Connecting -> ConnKind.CONNECTING to "连接中…（第 ${st.attempt} 次）"
                    is ConnectionSupervisor.State.AwaitingHostKey ->
                        ConnKind.AWAITING_HOST_KEY to "等待确认主机指纹"
                    is ConnectionSupervisor.State.Connected ->
                        ConnKind.CONNECTED to "已连接 · herdr ${st.version} · protocol ${st.protocol}"
                    is ConnectionSupervisor.State.Stale ->
                        ConnKind.STALE to "数据可能过期：${st.reason}"
                    is ConnectionSupervisor.State.Reconnecting ->
                        ConnKind.RECONNECTING to "重连中（第 ${st.attempt} 次，${st.delayMs / 1000}s 后）"
                    is ConnectionSupervisor.State.Failed ->
                        ConnKind.FAILED to st.message
                }
                _state.value = _state.value.copy(
                    connKind = kind,
                    connText = text,
                    lastError = (st as? ConnectionSupervisor.State.Failed)?.message,
                )
            }
        }
    }

    private fun observeReconciler() {
        viewModelScope.launch {
            supervisor.reconciler.state.collect { rs ->
                if (rs.fetchedAtMs > 0 || rs.panes.isNotEmpty()) {
                    val host = settings.host()
                    // 持久化到 Room（离线时 UI 仍能显示）
                    runCatching {
                        repository.replace(
                            hostId = host.hostId,
                            session = host.session,
                            panes = rs.panes,
                            version = rs.version,
                            protocol = rs.protocol,
                            fetchedAtMs = rs.fetchedAtMs,
                            stale = rs.stale,
                            lastError = rs.lastError,
                        )
                    }
                }
                _state.value = _state.value.copy(
                    refreshing = rs.refreshing,
                    stale = rs.stale || rs.fetchedAtMs == 0L,
                )
            }
        }
    }

    private fun observeCache() {
        viewModelScope.launch {
            val host = settings.host()
            repository.observe(host.hostId, host.session).collect { cached ->
                // 实时数据优先；只有缓存比界面新时才覆盖（离线场景）
                _state.value = _state.value.copy(
                    panes = cached.panes,
                    groups = BoardGrouper.group(cached.panes),
                    counts = BoardGrouper.counts(cached.panes),
                    version = cached.version,
                    protocol = cached.protocol,
                    updatedAtMs = cached.fetchedAtMs,
                    stale = cached.stale,
                )
                // 选中的 pane 也要跟着更新
                val sel = _state.value.selectedPane
                if (sel != null) {
                    _state.value = _state.value.copy(
                        selectedPane = cached.panes.firstOrNull { it.paneId == sel.paneId },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        supervisor.stop()
        super.onCleared()
    }
}
