package dev.herdr.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.HostRepository
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.ConnectionSupervisor
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.SessionReconciler
import dev.herdr.core.ssh.HostKeyUnknownException
import dev.herdr.core.ssh.SshConnector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 多主机 / 多会话（M2-1）。
 *
 * - 每台主机独立一个 [ConnectionSupervisor]（连接代次、退避、订阅、Stale 完全隔离）；
 * - 所有主机都会连接（配置了私钥的），看板只展示“当前选中主机”的数据；
 * - Room 以 (hostId, session) 为主键，天然隔离多主机缓存。
 */
class HostsViewModel(
    private val hosts: HostRepository,
    private val boards: BoardRepository,
    private val transcriptStore: dev.herdr.core.data.TranscriptStore,
    private val connectorFor: (String) -> SshConnector,
) : ViewModel() {

    enum class ConnKind { IDLE, CONNECTING, AWAITING_HOST_KEY, CONNECTED, STALE, RECONNECTING, FAILED }

    data class HostState(
        val cfg: HostRepository.HostConfig,
        val hasKey: Boolean,
        val connKind: ConnKind = ConnKind.IDLE,
        val connText: String = "未连接",
        val lastError: String? = null,
        val paneCount: Int = 0,
    )

    data class BoardView(
        val hostId: String? = null,
        val panes: List<Pane> = emptyList(),
        val groups: List<BoardGrouper.Group> = emptyList(),
        val counts: Map<AgentStatus, Int> = emptyMap(),
        val version: String = "",
        val protocol: Int = 0,
        val updatedAtMs: Long = 0,
        val stale: Boolean = true,
        val refreshing: Boolean = false,
        val selectedPane: Pane? = null,
    )

    data class HostKeyPrompt(val hostId: String, val label: String, val fingerprint: String)

    data class TranscriptState(
        val path: String? = null,
        val loading: Boolean = false,
        val messages: List<dev.herdr.core.herdr.ChatMessage> = emptyList(),
        val error: String? = null,
    )

    data class UiState(
        val hosts: List<HostState> = emptyList(),
        val activeId: String? = null,
        val board: BoardView = BoardView(),
        val hostKeyPrompt: HostKeyPrompt? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow(TranscriptState())
    val transcript: StateFlow<TranscriptState> = _transcript.asStateFlow()
    private var transcriptTailer: dev.herdr.core.data.TranscriptTailer? = null
    private var transcriptPoll: Job? = null
    private var transcriptObserve: Job? = null

    private class Runtime(
        val cfg: HostRepository.HostConfig,
        val connector: SshConnector,
        var supervisor: ConnectionSupervisor? = null,
        var persistJob: Job? = null,
        var pendingKey: CompletableDeferred<Boolean>? = null,
    )

    private val runtimes = LinkedHashMap<String, Runtime>()
    private var boardJob: Job? = null

    init {
        reloadHosts(startConnections = true)
    }

    // ---------------- 主机管理 ----------------

    fun reloadHosts(startConnections: Boolean = false) {
        val list = hosts.list()
        val active = hosts.activeId() ?: list.firstOrNull()?.id
        if (active != null) hosts.setActive(active)
        val next = LinkedHashMap<String, Runtime>()
        list.forEach { cfg ->
            val old = runtimes[cfg.id]
            next[cfg.id] = if (old != null && old.cfg == cfg) old
            else Runtime(cfg, connectorFor(cfg.id))
        }
        // 停掉被删除主机的运行时
        runtimes.filterKeys { it !in next.keys }.values.forEach { stopRuntime(it) }
        runtimes.clear()
        runtimes.putAll(next)
        _state.value = _state.value.copy(
            hosts = list.map { cfg ->
                HostState(
                    cfg = cfg,
                    hasKey = hosts.hasKey(cfg.id),
                    connKind = runtimes[cfg.id]?.supervisor?.let { kindOf(it) } ?: ConnKind.IDLE,
                )
            },
            activeId = active,
        )
        if (startConnections) runtimes.values.forEach { ensureStarted(it) }
        switchBoard(active)
    }

    fun addHost(cfg: HostRepository.HostConfig) {
        hosts.upsert(cfg)
        reloadHosts()
    }

    fun updateHost(cfg: HostRepository.HostConfig) {
        hosts.upsert(cfg)
        reloadHosts()
        ensureStarted(runtimes[cfg.id] ?: return)
    }

    fun removeHost(id: String) {
        runtimes[id]?.let { stopRuntime(it) }
        runtimes.remove(id)
        hosts.remove(id)
        reloadHosts()
    }

    fun selectHost(id: String) {
        hosts.setActive(id)
        _state.value = _state.value.copy(activeId = id)
        switchBoard(id)
    }

    fun importKey(hostId: String, text: String): Result<Unit> {
        val r = hosts.importKey(hostId, text).map { }
        reloadHosts()
        if (r.isSuccess) runtimes[hostId]?.let { ensureStarted(it) }
        return r
    }

    fun deleteKey(hostId: String) {
        hosts.deleteKey(hostId)
        runtimes[hostId]?.let { stopRuntime(it) }
        reloadHosts()
    }

    // ---------------- 连接控制 ----------------

    fun connect(id: String) = runtimes[id]?.let { ensureStarted(it) } ?: Unit

    fun disconnect(id: String) = runtimes[id]?.let { stopRuntime(it) } ?: Unit

    fun reconnect(id: String) = runtimes[id]?.supervisor?.reconnectNow() ?: Unit

    fun refreshActive() {
        val id = _state.value.activeId ?: return
        runtimes[id]?.supervisor?.reconciler?.markDirty()
    }

    // ---------------- transcript（只读，增量 + Room 缓存） ----------------

    fun openTranscript(pane: Pane) {
        val path = pane.agentSessionPath
        if (path == null) {
            _transcript.value = TranscriptState(error = "这台 pane 没有上报 transcript 路径（agent_session=null）")
            return
        }
        val hostId = _state.value.activeId ?: run {
            _transcript.value = TranscriptState(error = "没有活动主机")
            return
        }
        val conn = runtimes[hostId]?.supervisor?.currentConnection()
        if (conn == null) {
            _transcript.value = TranscriptState(path = path, error = "主机未连接")
            return
        }
        closeTranscriptInternal()
        _transcript.value = TranscriptState(path = path, loading = true)

        // 1) 先订阅本地缓存（离线也能看到历史）
        transcriptObserve = viewModelScope.launch {
            transcriptStore.observe(hostId, path).collect { msgs ->
                if (_transcript.value.path != path) return@collect
                _transcript.value = _transcript.value.copy(messages = msgs)
            }
        }

        // 2) 增量同步：先跑一次（首次会回填尾部），之后每 1.5s 增量
        val tailer = dev.herdr.core.data.TranscriptTailer(conn, transcriptStore, hostId, path)
        transcriptTailer = tailer
        transcriptPoll = viewModelScope.launch {
            var first = true
            while (true) {
                val r = runCatching { tailer.sync() }
                if (first) {
                    _transcript.value = _transcript.value.copy(
                        loading = false,
                        error = r.exceptionOrNull()?.message,
                    )
                    first = false
                }
                kotlinx.coroutines.delay(1_500)
            }
        }
    }

    fun reloadTranscript() {
        val path = _transcript.value.path ?: return
        val tailer = transcriptTailer ?: return
        viewModelScope.launch {
            _transcript.value = _transcript.value.copy(loading = true)
            val r = runCatching { tailer.sync() }
            _transcript.value = _transcript.value.copy(
                loading = false,
                error = r.exceptionOrNull()?.message,
            )
        }
    }

    fun closeTranscript() {
        closeTranscriptInternal()
        _transcript.value = TranscriptState()
    }

    private fun closeTranscriptInternal() {
        transcriptPoll?.cancel(); transcriptPoll = null
        transcriptObserve?.cancel(); transcriptObserve = null
        transcriptTailer?.close(); transcriptTailer = null
    }

    fun selectPane(pane: Pane?) {
        _state.value = _state.value.copy(board = _state.value.board.copy(selectedPane = pane))
    }

    fun answerHostKey(accept: Boolean) {
        val prompt = _state.value.hostKeyPrompt ?: return
        runtimes[prompt.hostId]?.pendingKey?.complete(accept)
        _state.value = _state.value.copy(hostKeyPrompt = null)
    }

    // ---------------- 内部 ----------------

    private fun ensureStarted(rt: Runtime) {
        if (rt.supervisor != null) {
            rt.supervisor?.reconnectNow()
            return
        }
        if (!hosts.hasKey(rt.cfg.id)) {
            updateHostState(rt.cfg.id, ConnKind.IDLE, "未导入私钥", null)
            return
        }
        val sup = ConnectionSupervisor(
            scope = viewModelScope,
            connector = rt.connector,
            configProvider = { hosts.sshConfig(rt.cfg.id) },
            onUnknownHostKey = { ex -> awaitHostKey(rt, ex) },
        )
        rt.supervisor = sup
        sup.start()   // 必须先 start：reconciler 在 start() 里初始化
        rt.persistJob = viewModelScope.launch { persistLoop(rt, sup.reconciler) }
        viewModelScope.launch { collectSupervisor(rt, sup) }
    }

    private fun stopRuntime(rt: Runtime) {
        rt.persistJob?.cancel()
        rt.persistJob = null
        rt.supervisor?.stop()
        rt.supervisor = null
        updateHostState(rt.cfg.id, ConnKind.IDLE, "已断开", null)
    }

    private fun awaitHostKey(rt: Runtime, ex: HostKeyUnknownException): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        rt.pendingKey = deferred
        _state.value = _state.value.copy(
            hostKeyPrompt = HostKeyPrompt(rt.cfg.id, rt.cfg.label, ex.fingerprint),
        )
        updateHostState(rt.cfg.id, ConnKind.AWAITING_HOST_KEY, "等待确认主机指纹", null)
        return runBlocking { withTimeoutOrNull(90_000) { deferred.await() } ?: false }
    }

    private suspend fun collectSupervisor(rt: Runtime, sup: ConnectionSupervisor) {
        sup.state.collect { st ->
            val (kind, text, err) = when (st) {
                is ConnectionSupervisor.State.Idle -> Triple(ConnKind.IDLE, "未连接", null)
                is ConnectionSupervisor.State.Connecting -> Triple(ConnKind.CONNECTING, "连接中…（第 ${st.attempt} 次）", null)
                is ConnectionSupervisor.State.AwaitingHostKey -> Triple(ConnKind.AWAITING_HOST_KEY, "等待确认主机指纹", null)
                is ConnectionSupervisor.State.Connected -> Triple(
                    ConnKind.CONNECTED, "已连接 · herdr ${st.version} · protocol ${st.protocol}", null,
                )
                is ConnectionSupervisor.State.Stale -> Triple(ConnKind.STALE, "数据可能过期：${st.reason}", st.reason)
                is ConnectionSupervisor.State.Reconnecting -> Triple(
                    ConnKind.RECONNECTING, "重连中（第 ${st.attempt} 次，${st.delayMs / 1000}s 后）", st.lastError,
                )
                is ConnectionSupervisor.State.Failed -> Triple(ConnKind.FAILED, st.message, st.message)
            }
            updateHostState(rt.cfg.id, kind, text, err)
        }
    }

    /** 把每台主机的快照写进 Room（凭 hostId+session 隔离）。 */
    private suspend fun persistLoop(rt: Runtime, reconciler: SessionReconciler) {
        reconciler.state.collect { rs ->
            if (rs.fetchedAtMs <= 0 && rs.panes.isEmpty()) return@collect
            runCatching {
                boards.replace(
                    hostId = rt.cfg.id,
                    session = rt.cfg.session,
                    panes = rs.panes,
                    version = rs.version,
                    protocol = rs.protocol,
                    fetchedAtMs = rs.fetchedAtMs,
                    stale = rs.stale,
                    lastError = rs.lastError,
                )
            }
            if (_state.value.activeId == rt.cfg.id) {
                _state.value = _state.value.copy(
                    board = _state.value.board.copy(
                        refreshing = rs.refreshing,
                        stale = rs.stale || rs.fetchedAtMs == 0L,
                    ),
                )
            }
            updateHostState(rt.cfg.id, panes = rs.panes.size)
        }
    }

    private fun switchBoard(hostId: String?) {
        boardJob?.cancel()
        _state.value = _state.value.copy(
            board = BoardView(hostId = hostId, stale = true),
        )
        if (hostId == null) return
        val cfg = hosts.get(hostId) ?: return
        boardJob = viewModelScope.launch {
            boards.observe(hostId, cfg.session).collect { cached ->
                if (_state.value.activeId != hostId) return@collect
                val sel = _state.value.board.selectedPane
                _state.value = _state.value.copy(
                    board = BoardView(
                        hostId = hostId,
                        panes = cached.panes,
                        groups = BoardGrouper.group(cached.panes),
                        counts = BoardGrouper.counts(cached.panes),
                        version = cached.version,
                        protocol = cached.protocol,
                        updatedAtMs = cached.fetchedAtMs,
                        stale = cached.stale,
                        refreshing = _state.value.board.refreshing,
                        selectedPane = sel?.let { s -> cached.panes.firstOrNull { it.paneId == s.paneId } },
                    ),
                )
            }
        }
    }

    private fun updateHostState(
        id: String,
        kind: ConnKind? = null,
        text: String? = null,
        error: String? = null,
        panes: Int? = null,
    ) {
        _state.value = _state.value.copy(
            hosts = _state.value.hosts.map { hs ->
                if (hs.cfg.id != id) hs else hs.copy(
                    connKind = kind ?: hs.connKind,
                    connText = text ?: hs.connText,
                    lastError = error ?: hs.lastError,
                    paneCount = panes ?: hs.paneCount,
                )
            },
        )
    }

    private fun kindOf(sup: ConnectionSupervisor): ConnKind = when (sup.state.value) {
        is ConnectionSupervisor.State.Idle -> ConnKind.IDLE
        is ConnectionSupervisor.State.Connecting -> ConnKind.CONNECTING
        is ConnectionSupervisor.State.AwaitingHostKey -> ConnKind.AWAITING_HOST_KEY
        is ConnectionSupervisor.State.Connected -> ConnKind.CONNECTED
        is ConnectionSupervisor.State.Stale -> ConnKind.STALE
        is ConnectionSupervisor.State.Reconnecting -> ConnKind.RECONNECTING
        is ConnectionSupervisor.State.Failed -> ConnKind.FAILED
    }

    override fun onCleared() {
        runtimes.values.forEach { rt ->
            rt.persistJob?.cancel()
            rt.supervisor?.stop()
        }
        runtimes.clear()
        super.onCleared()
    }
}
