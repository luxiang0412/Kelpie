package dev.herdr.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.data.HostRepository
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI 层（M4）：只做“选中主机 / 选中 pane / 对话页 / 写操作转发”。
 * 连接与状态同步已经移到进程级的 [HostConnectionManager]，所以 App 切后台（前台服务）也不会断。
 */
class HostsViewModel(
    private val manager: HostConnectionManager,
    private val hosts: HostRepository,
    private val drafts: dev.herdr.core.data.drafts.DraftStore? = null,
) : ViewModel() {

    data class HostState(
        val cfg: HostRepository.HostConfig,
        val hasKey: Boolean,
        val connKind: HostConnectionManager.ConnKind = HostConnectionManager.ConnKind.IDLE,
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

    data class TranscriptState(
        val path: String? = null,
        val loading: Boolean = false,
        val messages: List<ChatMessage> = emptyList(),
        val error: String? = null,
    )

    data class UiState(
        val hosts: List<HostState> = emptyList(),
        val activeId: String? = null,
        val board: BoardView = BoardView(),
        val hostKeyPrompt: HostConnectionManager.HostKeyPrompt? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow(TranscriptState())
    val transcript: StateFlow<TranscriptState> = _transcript.asStateFlow()

    val composer: StateFlow<HostConnectionManager.ComposerState> = manager.composer
    val approvals: StateFlow<List<HostConnectionManager.ApprovalItem>> = manager.approvals

    /** 待确认（Pending/Unknown）写操作：进程重建后也能在这里看到（I-92）。 */
    private val _pendingWrites = MutableStateFlow<List<dev.herdr.core.herdr.WriteRecord>>(emptyList())
    val pendingWrites: StateFlow<List<dev.herdr.core.herdr.WriteRecord>> = _pendingWrites.asStateFlow()

    private fun refreshPendingWrites() {
        _pendingWrites.value = manager.recentWrites().filter {
            it.outcome is WriteOutcome.Pending || it.outcome is WriteOutcome.Unknown
        }
    }

    init {
        viewModelScope.launch { manager.composer.collect { onComposerChanged(it) } }
        refreshPendingWrites()
    }

    private val _paneOutput = MutableStateFlow("")
    val paneOutput: StateFlow<String> = _paneOutput.asStateFlow()
    private var outputJob: Job? = null

    private var boardJob: Job? = null
    private var transcriptTailer: dev.herdr.core.data.TranscriptTailer? = null
    private var transcriptPoll: Job? = null
    private var transcriptObserve: Job? = null

    init {
        viewModelScope.launch { manager.hostsState.collect { list -> syncHosts(list) } }
        viewModelScope.launch { manager.hostKeyPrompt.collect { p -> _state.value = _state.value.copy(hostKeyPrompt = p) } }
        val active = hosts.activeId() ?: hosts.list().firstOrNull()?.id
        if (active != null) {
            hosts.setActive(active)
            _state.value = _state.value.copy(activeId = active)
            switchBoard(active)
        }
    }

    private fun syncHosts(list: List<HostConnectionManager.HostState>) {
        _state.value = _state.value.copy(
            hosts = list.map {
                HostState(it.cfg, it.hasKey, it.connKind, it.connText, it.lastError, it.paneCount)
            },
        )
    }

    // ---------------- 主机管理 ----------------

    fun reloadHosts() {
        manager.reload()
        val active = hosts.activeId() ?: hosts.list().firstOrNull()?.id
        if (active != null && active != _state.value.activeId) selectHost(active)
    }

    fun addHost(cfg: HostRepository.HostConfig) {
        hosts.upsert(cfg)
        manager.reload(startConnections = true)
        reloadHosts()
    }

    fun removeHost(id: String) {
        // 删除主机时一并删除它的草稿（避免遗留敏感代码）
        viewModelScope.launch { runCatching { drafts?.deleteByHost(id) } }
        manager.disconnect(id)
        hosts.remove(id)
        manager.reload(startConnections = true)
        reloadHosts()
    }

    fun selectHost(id: String) {
        hosts.setActive(id)
        _state.value = _state.value.copy(activeId = id)
        switchBoard(id)
    }

    fun connect(id: String) = manager.connect(id)

    fun disconnect(id: String) = manager.disconnect(id)

    fun reconnect(id: String) = manager.reconnect(id)

    fun refreshActive() {
        _state.value.activeId?.let { manager.refresh(it) }
    }

    fun importKey(hostId: String, text: String): Result<Unit> {
        val r = hosts.importKey(hostId, text).map { }
        manager.reload()
        if (r.isSuccess) manager.connect(hostId)
        return r
    }

    fun deleteKey(hostId: String) {
        manager.disconnect(hostId)
        hosts.deleteKey(hostId)
        manager.reload()
    }

    fun answerHostKey(accept: Boolean) = manager.answerHostKey(accept)

    fun answerApproval(item: HostConnectionManager.ApprovalItem, allow: Boolean) =
        manager.answerApproval(item, allow)

    // ---------------- 看板 ----------------

    private fun switchBoard(hostId: String) {
        boardJob?.cancel()
        _state.value = _state.value.copy(board = BoardView(hostId = hostId, stale = true))
        val cfg = hosts.get(hostId) ?: return
        boardJob = viewModelScope.launch {
            manager.board(hostId, cfg.session).collect { cached ->
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

    fun selectPane(pane: Pane?) {
        _state.value = _state.value.copy(board = _state.value.board.copy(selectedPane = pane))
        outputJob?.cancel()
        _paneOutput.value = ""
        if (pane == null) return
        val hostId = _state.value.activeId ?: return
        outputJob = viewModelScope.launch {
            while (true) {
                manager.readPane(hostId, pane.paneId)?.let { _paneOutput.value = it }
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    // ---------------- 写操作 ----------------

    fun updateComposerText(text: String) {
        manager.updateComposer(_state.value.activeId, text)
        scheduleDraftSave(text)
    }

    fun clearComposerOutcome() = manager.clearComposerOutcome()

    fun sendPrompt(pane: Pane) {
        val hostId = _state.value.activeId ?: return
        val writable = _state.value.hosts.firstOrNull { it.cfg.id == hostId }
            ?.connKind == HostConnectionManager.ConnKind.CONNECTED
        manager.sendPrompt(hostId, pane, writable)
        // 发送后立刻刷一次输出，别等 2s 轮询
        viewModelScope.launch {
            manager.readPane(hostId, pane.paneId)?.let { _paneOutput.value = it }
        }
    }

    // ---------------- 草稿（M2-C2） ----------------

    private var draftKey: String? = null
    private var draftJob: Job? = null

    /** 当前 pane 对应的草稿身份（Host + Herdr session + agent session/占用指纹）。 */
    private fun draftKeyFor(pane: Pane?): String? {
        val hostId = _state.value.activeId ?: return null
        val cfg = hosts.get(hostId) ?: return null
        return dev.herdr.core.data.drafts.DraftKey.of(hostId, cfg.session, pane)
    }

    private fun loadDraft(pane: Pane) {
        val key = draftKeyFor(pane) ?: return
        draftKey = key
        val store = drafts ?: return
        viewModelScope.launch {
            val saved = runCatching { store.load(key) }.getOrNull() ?: return@launch
            // 不覆盖用户在加载完成前已经开始输入的内容
            if (draftKey != key) return@launch
            if (manager.composer.value.text.isEmpty()) {
                manager.updateComposer(_state.value.activeId, saved)
            }
        }
    }

    private fun scheduleDraftSave(text: String) {
        val key = draftKey ?: return
        val store = drafts ?: return
        val hostId = _state.value.activeId ?: return
        val session = hosts.get(hostId)?.session ?: return
        val pane = _state.value.board.selectedPane
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(dev.herdr.core.data.drafts.DraftStore.DEBOUNCE_MS)
            runCatching { store.save(key, hostId, session, pane?.agentSessionPath, text) }
        }
    }

    /** 页面离开/生命周期切换时尽力写入最新内容（取消防抖等待）。 */
    fun flushDraft() {
        val key = draftKey ?: return
        val store = drafts ?: return
        val hostId = _state.value.activeId ?: return
        val session = hosts.get(hostId)?.session ?: return
        val pane = _state.value.board.selectedPane
        val text = manager.composer.value.text
        draftJob?.cancel()
        viewModelScope.launch {
            runCatching { store.save(key, hostId, session, pane?.agentSessionPath, text) }
        }
    }

    /** Accepted 后若输入已清空，说明这份草稿已被消费。 */
    private fun onComposerChanged(state: HostConnectionManager.ComposerState) {
        refreshPendingWrites()
        if (state.outcome !is WriteOutcome.Accepted) return
        if (state.text.isNotEmpty()) return
        val key = draftKey ?: return
        val store = drafts ?: return
        viewModelScope.launch { runCatching { store.clear(key) } }
    }

    // ---------------- 对话（只读） ----------------

    /** W2：进入对话（有 agent_session 走结构化；否则降级终端输出）。 */
    fun openConversation(pane: Pane) {
        selectPane(pane)   // 启动终端尾部轮询，作为兜底
        manager.setComposerTarget(_state.value.activeId, pane)
        loadDraft(pane)
        if (pane.agentSessionPath != null) openTranscript(pane) else _transcript.value = TranscriptState()
    }

    fun closeConversation() {
        flushDraft()
        closeTranscript()
        selectPane(null)
        draftKey = null
    }

    fun reloadConversation() {
        if (_transcript.value.path != null) reloadTranscript()
        val hostId = _state.value.activeId ?: return
        val p = _state.value.board.selectedPane ?: return
        viewModelScope.launch { manager.readPane(hostId, p.paneId)?.let { _paneOutput.value = it } }
    }

    fun openTranscript(pane: Pane) {
        val path = pane.agentSessionPath
        val hostId = _state.value.activeId
        if (path == null || hostId == null) {
            _transcript.value = TranscriptState(error = "这台 pane 没有上报 transcript 路径（agent_session=null）")
            return
        }
        val tailer = manager.openTranscript(hostId, path)
        if (tailer == null) {
            _transcript.value = TranscriptState(path = path, error = "主机未连接")
            return
        }
        closeTranscriptInternal()
        _transcript.value = TranscriptState(path = path, loading = true)
        transcriptTailer = tailer

        transcriptObserve = viewModelScope.launch {
            manager.transcripts().observe(hostId, path).collect { msgs ->
                if (_transcript.value.path != path) return@collect
                _transcript.value = _transcript.value.copy(messages = msgs)
            }
        }
        transcriptPoll = viewModelScope.launch {
            var first = true
            while (true) {
                val r = runCatching { tailer.sync() }
                if (first) {
                    _transcript.value = _transcript.value.copy(loading = false, error = r.exceptionOrNull()?.message)
                    first = false
                }
                kotlinx.coroutines.delay(1_500)
            }
        }
    }

    fun reloadTranscript() {
        val tailer = transcriptTailer ?: return
        viewModelScope.launch {
            _transcript.value = _transcript.value.copy(loading = true)
            val r = runCatching { tailer.sync() }
            _transcript.value = _transcript.value.copy(loading = false, error = r.exceptionOrNull()?.message)
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

    override fun onCleared() {
        closeTranscriptInternal()
        super.onCleared()
    }
}
