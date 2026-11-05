package io.github.luxiang0412.kelpie.conversation

import io.github.luxiang0412.kelpie.core.herdr.t

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.luxiang0412.kelpie.core.data.ClaudeSessionResolver
import io.github.luxiang0412.kelpie.core.data.CodexSessionResolver
import io.github.luxiang0412.kelpie.core.data.TranscriptTailer
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.drafts.DraftKey
import io.github.luxiang0412.kelpie.core.data.runtime.Receipt
import io.github.luxiang0412.kelpie.core.data.runtime.SendBlocked
import io.github.luxiang0412.kelpie.core.data.runtime.SendRecord
import io.github.luxiang0412.kelpie.core.data.runtime.Sender
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.container
import io.github.luxiang0412.kelpie.conversation.model.ConversationBranch
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
import io.github.luxiang0412.kelpie.conversation.model.IncrementalConversationMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 对话页的数据来源：会话文件（Pi / Claude）或只有终端（Codex 等）。 */
sealed interface Source {
    data object Locating : Source
    data class Transcript(val path: String, val truncated: Boolean) : Source
    data class TerminalOnly(val reason: String) : Source
}

class ConversationViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private lateinit var key: AgentKey

    private val _item = MutableStateFlow<AgentItem?>(null)
    val item: StateFlow<AgentItem?> = _item.asStateFlow()

    private val _source = MutableStateFlow<Source>(Source.Locating)
    val source: StateFlow<Source> = _source.asStateFlow()

    private val _items = MutableStateFlow<List<ConversationItem>>(emptyList())
    val items: StateFlow<List<ConversationItem>> = _items.asStateFlow()

    private val _terminal = MutableStateFlow<String?>(null)
    val terminal: StateFlow<String?> = _terminal.asStateFlow()

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    /** 发送被拦下（目标变了 / 未连接）。needsConfirm = 可以坚持发送。 */
    private val _blocked = MutableStateFlow<SendBlocked?>(null)
    val blocked: StateFlow<SendBlocked?> = _blocked.asStateFlow()

    lateinit var receipt: StateFlow<SendRecord?>
        private set

    lateinit var online: StateFlow<Boolean>
        private set

    /** 最近一次在手机上回答选择题（进度 / 结果）。 */
    lateinit var answer: StateFlow<io.github.luxiang0412.kelpie.core.data.runtime.AnswerRecord?>
        private set

    private var editedAgentSession: String? = null
    private var editedOccupancy: String? = null
    private var draftKey: String? = null
    private var draftJob: Job? = null
    private var started = false
    private val mapper = IncrementalConversationMapper()

    fun start(hostId: String, paneId: String, prefill: String?) {
        if (started) return
        started = true
        key = AgentKey(hostId, paneId)
        c.notifier.clear(hostId, paneId)
        receipt = c.sender.records.map { it[key] }.stateIn(viewModelScope, SharingStarted.Eagerly, c.sender.records.value[key])
        answer = c.sender.answers.map { it[key] }.stateIn(viewModelScope, SharingStarted.Eagerly, c.sender.answers.value[key])
        online = c.fleet.links.map { it[hostId]?.reach == io.github.luxiang0412.kelpie.core.data.attention.HostReach.Online }
            .stateIn(viewModelScope, SharingStarted.Eagerly, c.fleet.isOnline(hostId))

        viewModelScope.launch {
            c.fleet.home.map { h -> (h.needsYou + h.working + h.recentlyDone + h.quiet).firstOrNull { it.key == key } }
                .distinctUntilChanged()
                .collect { _item.value = it }
        }
        // 草稿：打开时记下目标身份（发送前校验用），然后载入草稿；预填只在草稿为空时生效
        viewModelScope.launch {
            val pane = c.fleet.pane(key)
            editedAgentSession = pane?.agentSessionPath?.takeIf { it.isNotBlank() }
            editedOccupancy = Sender.occupancyOf(pane)
            val session = c.fleet.config(hostId)?.session ?: "test"
            draftKey = DraftKey.of(hostId, session, pane)
            val draft = draftKey?.let { c.drafts.load(it) }.orEmpty()
            _text.value = if (draft.isBlank() && prefill != null) prefill else draft
        }
        viewModelScope.launch { locateAndTail() }
    }

    private suspend fun locateAndTail() {
        // 等连接与 pane 就绪
        var pane = c.fleet.pane(key)
        while (viewModelScope.isActive && (pane == null || c.fleet.connection(key.hostId) == null)) {
            delay(500); pane = c.fleet.pane(key)
        }
        pane ?: return
        val conn = c.fleet.connection(key.hostId) ?: return
        val path = pane.agentSessionPath?.takeIf { it.isNotBlank() } ?: run {
            if (pane.agent == "codex") {
                val sameCwd = c.fleet.panes(key.hostId).count { it.agent == "codex" && it.cwd == pane.cwd }
                when (val r = CodexSessionResolver().resolve(conn, pane.cwd, pane.agentSessionId, sameCwd)) {
                    is ClaudeSessionResolver.Result.Found -> r.path
                    is ClaudeSessionResolver.Result.Failure -> { _source.value = Source.TerminalOnly(r.message); null }
                }
            } else if (pane.agentSessionId != null || pane.agent?.startsWith("claude") == true) {
                when (val r = ClaudeSessionResolver().resolve(conn, pane.cwd, pane.agentSessionId)) {
                    is ClaudeSessionResolver.Result.Found -> r.path
                    is ClaudeSessionResolver.Result.Failure -> { _source.value = Source.TerminalOnly(r.message); null }
                }
            } else {
                _source.value = Source.TerminalOnly(t("${io.github.luxiang0412.kelpie.core.data.attention.AgentNames.display(pane.agent)} 没有可读的会话记录", "${io.github.luxiang0412.kelpie.core.data.attention.AgentNames.display(pane.agent)} has no readable transcript"))
                null
            }
        }
        if (path == null) { pollTerminal(); return }

        val tailer = TranscriptTailer(conn, c.transcripts, key.hostId, path)
        viewModelScope.launch {
            c.transcripts.observe(key.hostId, path)
                .map { msgs -> mapper.update(ConversationBranch.effective(msgs), path) }
                .flowOn(Dispatchers.Default)
                .collect { _items.value = it }
        }
        // 只在页面打开期间追尾：工作中 / 等你 1.5 秒一次，其他 5 秒
        while (viewModelScope.isActive) {
            runCatching { tailer.sync() }
            _source.value = Source.Transcript(path, tailer.backfillTruncated)
            val busy = _item.value?.status.let { it == AgentStatus.WORKING || it == AgentStatus.BLOCKED }
            delay(if (busy) 1_500 else 5_000)
        }
    }

    private suspend fun pollTerminal() {
        while (viewModelScope.isActive) {
            refreshTerminal()
            delay(2_000)
        }
    }

    fun refreshTerminal() {
        viewModelScope.launch {
            val api = c.fleet.connection(key.hostId)?.let { conn ->
                c.fleet.config(key.hostId)?.let { io.github.luxiang0412.kelpie.core.herdr.HerdrApi(conn, it.herdrBin, it.session) }
            } ?: return@launch
            runCatching { api.readPane(key.paneId, "recent", 120) }.onSuccess { _terminal.value = it }
        }
    }

    fun setText(t: String) {
        _text.value = t
        _blocked.value = null
        if (receipt.value?.receipt is Receipt.Rejected) c.sender.dismiss(key)
        val k = draftKey ?: return
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(400)
            if (t.isBlank()) c.drafts.clear(k) else c.drafts.save(k, key.hostId, c.fleet.config(key.hostId)?.session ?: "test", editedAgentSession, t)
        }
    }

    /** 在手机上回答选择题：替你在终端里按键（每步读屏核对），只发一次。返回拦下的原因。 */
    fun answerQuestions(tool: ConversationItem.Tool, replies: List<io.github.luxiang0412.kelpie.core.data.runtime.AskPicker.Reply>): SendBlocked? =
        c.sender.answerQuestions(key, tool.callId, tool.questions, replies, editedAgentSession, editedOccupancy)

    fun dismissAnswer() = c.sender.dismissAnswer(key)

    /** 快捷回复：填进输入框（追加），绝不直接发送。 */
    fun applyQuick(q: String) = setText(if (_text.value.isBlank()) q else _text.value.trimEnd() + " " + q)

    fun send(confirmed: Boolean = false) {
        val body = _text.value
        val r = c.sender.send(key, body, editedAgentSession, editedOccupancy, confirmed)
        _blocked.value = r
        if (r != null) return
        // 已提交：等回执。Accepted 且输入框没被改过 → 清空并删除草稿
        viewModelScope.launch {
            val rec = receipt.first { it != null && it.text == body.trim() && it.receipt !is Receipt.Sending } ?: return@launch
            if (rec.receipt is Receipt.Accepted) {
                if (_text.value == body) {
                    _text.value = ""
                    draftKey?.let { c.drafts.clear(it) }
                }
                // 之后的目标身份以发送成功后的 pane 为准
                editedAgentSession = c.fleet.pane(key)?.agentSessionPath?.takeIf { it.isNotBlank() }
                editedOccupancy = Sender.occupancyOf(c.fleet.pane(key))
            }
        }
    }

    fun dismissBlocked() { _blocked.value = null }
}
