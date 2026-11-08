package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.herdr.t

import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.drafts.DraftKey
import io.github.luxiang0412.kelpie.core.data.drafts.SendTargetCheck
import io.github.luxiang0412.kelpie.core.herdr.PendingWriteSink
import io.github.luxiang0412.kelpie.core.herdr.WriteLedger
import io.github.luxiang0412.kelpie.core.herdr.WriteOutcome
import io.github.luxiang0412.kelpie.core.herdr.WriteRecord
import io.github.luxiang0412.kelpie.core.ssh.SshFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 发送回执的四种状态（规范 5.12 SendReceipt）。
 *
 * Unknown 永远不提供“重试”：herdr 的写操作没有幂等键，超时时远端可能已经执行。
 */
sealed interface Receipt {
    data object Sending : Receipt
    /** herdr 已接收（不代表 agent 已经执行完）。 */
    data class Accepted(val atMs: Long) : Receipt
    /** 明确被拒绝，内容保留在输入框里可以改了再发。 */
    data class Rejected(val code: String, val message: String) : Receipt
    /** 无法确认是否送达：不会自动重发，让用户去对话里确认。 */
    data class Unknown(val reason: String) : Receipt
}

data class SendRecord(
    val id: String,
    val key: AgentKey,
    val text: String,
    val receipt: Receipt,
    val atMs: Long,
)

/** 在手机上回答选择题的进度 / 结果（见 [AskPicker]）。 */
sealed interface AnswerState {
    data object Sending : AnswerState
    /** 提交后终端选择框关掉了；最终以会话记录里出现回答为准。 */
    data class Submitted(val atMs: Long) : AnswerState
    /** 停下了，没有提交。touched = 终端里已经选了一部分（没提交，对 Claude 没影响）。 */
    data class Stopped(val reason: String, val touched: Boolean) : AnswerState
    /** 不确定有没有提交：不会自动重发。 */
    data class Unknown(val reason: String) : AnswerState
}

/** [callId] = 回答的是哪一次 AskUserQuestion（tool_use id），界面只把结果挂在这张卡片上。 */
data class AnswerRecord(val id: String, val key: AgentKey, val callId: String?, val state: AnswerState, val atMs: Long)

/** 发送前需要用户确认的情况（目标变了）。 */
data class SendBlocked(val code: String, val message: String, val needsConfirm: Boolean)

/**
 * 写操作：`agent.prompt`（发消息）、选择题按键、中断（一次 Esc）。每次发送都先进 [WriteLedger]（未确认的写会落库，进程重建后可见）。
 */
class Sender(
    private val fleet: Fleet,
    private val scope: CoroutineScope,
    sink: PendingWriteSink? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val ledger = WriteLedger(sink = sink)

    private val _records = MutableStateFlow<Map<AgentKey, SendRecord>>(emptyMap())
    /** 每个 pane 最近一次发送。 */
    val records: StateFlow<Map<AgentKey, SendRecord>> = _records.asStateFlow()

    private val _answers = MutableStateFlow<Map<AgentKey, AnswerRecord>>(emptyMap())
    /** 每个 pane 最近一次在手机上回答选择题。 */
    val answers: StateFlow<Map<AgentKey, AnswerRecord>> = _answers.asStateFlow()

    private val _interrupts = MutableStateFlow<Map<AgentKey, SendRecord>>(emptyMap())
    /** 每个 pane 最近一次从手机中断（发一次 Esc）。text 固定为 "Esc"。 */
    val interrupts: StateFlow<Map<AgentKey, SendRecord>> = _interrupts.asStateFlow()

    /**
     * 进程重建后：未确认的写操作回到账本，**也回到界面**（首页“结果未知”），否则用户看不到。
     * 目标写成 `<hostId>/<paneId>`；旧格式（只有 paneId）找不到主机，只留在账本里。
     */
    fun restore(pending: List<WriteRecord>) {
        ledger.restore(pending, t("发送时 App 被关闭", "The app was closed while sending"))
        val restored = pending.mapNotNull { ledger.get(it.clientRequestId) }
            .filter { it.outcome is WriteOutcome.Unknown }
            .mapNotNull { r ->
                val key = targetKey(r.target) ?: return@mapNotNull null
                SendRecord(r.clientRequestId, key, "", Receipt.Unknown((r.outcome as WriteOutcome.Unknown).reason), r.sentAtMs)
            }
            .groupBy { it.key }.mapValues { (_, v) -> v.maxBy { it.atMs } }
        if (restored.isNotEmpty()) _records.update { cur -> restored + cur } // 已有的（这次进程里发的）优先
    }

    /**
     * 校验目标；通过才真正发送。
     * [editedAgentSession]/[editedOccupancy] 是用户开始编辑时的目标身份（打开对话时记下）。
     * [confirmed] = 用户已经看过“目标变了”的提示并坚持发送。
     */
    fun send(
        key: AgentKey,
        text: String,
        editedAgentSession: String?,
        editedOccupancy: String?,
        confirmed: Boolean = false,
    ): SendBlocked? {
        val body = text.trim()
        if (body.isEmpty()) return SendBlocked("empty", t("内容为空", "Nothing to send"), false)
        if (_records.value[key]?.receipt == Receipt.Sending) return SendBlocked("busy", t("上一条还在发送", "Previous message is still sending"), false)
        val pane = fleet.pane(key)
        when (val c = SendTargetCheck.check(pane, fleet.isOnline(key.hostId), editedAgentSession, editedOccupancy)) {
            is SendTargetCheck.Result.Ok -> Unit
            is SendTargetCheck.Result.Blocked -> return SendBlocked(c.code, c.reason, false)
            is SendTargetCheck.Result.NeedsConfirm -> if (!confirmed) return SendBlocked("target_changed", c.reason, true)
        }
        val api = fleet.runtime(key.hostId)?.api() ?: return SendBlocked("not_connected", t("主机未连接", "Host not connected"), false)

        val rec = ledger.begin("agent.prompt", target(key), body.toByteArray())
        val id = rec.clientRequestId
        put(SendRecord(id, key, body, Receipt.Sending, clock()))
        scope.launch {
            val r = runCatching { api.prompt(key.paneId, body) }
            val receipt = r.fold(
                onSuccess = { ledger.accept(id, "agent_prompted"); Receipt.Accepted(clock()) },
                onFailure = { e -> classify(id, e) },
            )
            put(SendRecord(id, key, body, receipt, clock()))
            // 发送成功后马上对账一次，让状态尽快从 blocked 变成 working
            if (receipt is Receipt.Accepted) fleet.refresh(key.hostId)
        }
        return null
    }

    /**
     * 在手机上回答 Claude 的 AskUserQuestion：替用户在终端选择框里按键，每一步读屏核对（[AskPicker]）。
     * 只发一次；停下 / 不确定都不会自动重来。
     */
    fun answerQuestions(
        key: AgentKey,
        callId: String?,
        questions: List<io.github.luxiang0412.kelpie.core.herdr.AskQuestion>,
        replies: List<AskPicker.Reply>,
        editedAgentSession: String?,
        editedOccupancy: String?,
    ): SendBlocked? {
        if (questions.isEmpty() || questions.size != replies.size || questions.indices.any { !replies[it].isComplete(questions[it]) }) {
            return SendBlocked("incomplete", t("还有题没答完", "Some questions aren't answered yet"), false)
        }
        if (_answers.value[key]?.state == AnswerState.Sending || _records.value[key]?.receipt == Receipt.Sending) {
            return SendBlocked("busy", t("上一次还在发送", "The previous send is still in progress"), false)
        }
        val pane = fleet.pane(key)
        when (val c = SendTargetCheck.check(pane, fleet.isOnline(key.hostId), editedAgentSession, editedOccupancy)) {
            is SendTargetCheck.Result.Ok -> Unit
            is SendTargetCheck.Result.Blocked -> return SendBlocked(c.code, c.reason, false)
            // 选择题不提供“仍然发送”：目标变了，选择框多半也不是这一组
            is SendTargetCheck.Result.NeedsConfirm -> return SendBlocked("target_changed", c.reason, false)
        }
        val api = fleet.runtime(key.hostId)?.api() ?: return SendBlocked("not_connected", t("主机未连接", "Host not connected"), false)

        val payload = questions.indices.joinToString("\n") { questions[it].question + "=" + AskPicker.expectedAnswer(questions[it], replies[it]) }
        val rec = ledger.begin("ask.answer", target(key), payload.toByteArray())
        val id = rec.clientRequestId
        _answers.value[key]?.let { old -> if (old.state is AnswerState.Unknown) ledger.acknowledge(old.id) }
        _answers.update { it + (key to AnswerRecord(id, key, callId, AnswerState.Sending, clock())) }
        val term = object : AskPicker.Terminal {
            override suspend fun read() = api.readPane(key.paneId, "visible", 60)
            override suspend fun key(k: String) = api.sendKeys(key.paneId, listOf(k))
            override suspend fun text(s: String) = api.sendText(key.paneId, s)
        }
        scope.launch {
            val state = try {
                when (val o = AskPicker.answer(term, questions, replies)) {
                    AskPicker.Outcome.Submitted -> { ledger.accept(id, "picker_closed"); AnswerState.Submitted(clock()) }
                    is AskPicker.Outcome.Stopped -> { ledger.reject(id, if (o.touched) "partial" else "stopped", o.reason); AnswerState.Stopped(o.reason, o.touched) }
                    is AskPicker.Outcome.Unsure -> { ledger.markUnknown(id, o.reason); AnswerState.Unknown(o.reason) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 网络中途断了：按到哪一步不知道，只能算不确定
                val reason = e.message ?: e.javaClass.simpleName
                ledger.markUnknown(id, reason)
                AnswerState.Unknown(reason)
            }
            _answers.update { it + (key to AnswerRecord(id, key, callId, state, clock())) }
            fleet.refresh(key.hostId)
        }
        return null
    }

    /**
     * 中断正在工作的 agent：在它的终端里按一次 Esc（Claude Code / Codex / Pi 都是这个键）。
     * 和发消息一样先进账本、给回执；结果不确定时**不会自动重发**（多按一次 Esc 在有的界面里含义不同）。
     * 只在它还在工作时发：已经停下（或者变成在等你）就拦下，免得 Esc 落到别的界面上。
     */
    fun interrupt(key: AgentKey, editedAgentSession: String?, editedOccupancy: String?): SendBlocked? {
        if (_interrupts.value[key]?.receipt == Receipt.Sending) return SendBlocked("busy", t("上一次中断还在发送", "The previous interrupt is still sending"), false)
        val pane = fleet.pane(key)
        when (val c = SendTargetCheck.check(pane, fleet.isOnline(key.hostId), editedAgentSession, editedOccupancy)) {
            is SendTargetCheck.Result.Ok -> Unit
            is SendTargetCheck.Result.Blocked -> return SendBlocked(c.code, c.reason, false)
            // 目标变了就不按：Esc 会落到另一个 agent 上
            is SendTargetCheck.Result.NeedsConfirm -> return SendBlocked("target_changed", c.reason, false)
        }
        if (pane?.status != io.github.luxiang0412.kelpie.core.herdr.AgentStatus.WORKING) {
            return SendBlocked("not_working", t("它已经不在工作了，没有发送", "It's no longer working; nothing was sent"), false)
        }
        val api = fleet.runtime(key.hostId)?.api() ?: return SendBlocked("not_connected", t("主机未连接", "Host not connected"), false)

        val rec = ledger.begin("pane.interrupt", target(key), "Escape".toByteArray())
        val id = rec.clientRequestId
        _interrupts.value[key]?.let { old -> if (old.receipt is Receipt.Unknown) ledger.acknowledge(old.id) }
        _interrupts.update { it + (key to SendRecord(id, key, "Esc", Receipt.Sending, clock())) }
        scope.launch {
            val r = runCatching { api.sendKeys(key.paneId, listOf("Escape")) }
            val receipt = r.fold(
                onSuccess = { ledger.accept(id, "key_sent"); Receipt.Accepted(clock()) },
                onFailure = { e -> classify(id, e) },
            )
            _interrupts.update { it + (key to SendRecord(id, key, "Esc", receipt, clock())) }
            fleet.refresh(key.hostId)
        }
        return null
    }

    /** 关掉中断的回执；Unknown 同时从账本确认掉。 */
    fun dismissInterrupt(key: AgentKey) {
        _interrupts.value[key]?.let { if (it.receipt is Receipt.Unknown) ledger.acknowledge(it.id) }
        _interrupts.update { it - key }
    }

    /** 关掉选择题的结果提示；Unknown 同时从账本确认掉。 */
    fun dismissAnswer(key: AgentKey) {
        _answers.value[key]?.let { if (it.state is AnswerState.Unknown) ledger.acknowledge(it.id) }
        _answers.update { it - key }
    }

    /** 用户关掉了回执（含“结果未知”）：Unknown 同时从账本确认掉，重启后不再出现。 */
    fun dismiss(key: AgentKey) {
        _records.value[key]?.let { if (it.receipt is Receipt.Unknown) ledger.acknowledge(it.id) }
        _records.update { it - key }
    }

    private fun classify(id: String, e: Throwable): Receipt = when (e) {
        is SshFailure.Protocol -> {
            val msg = e.message ?: "protocol error"
            val code = msg.substringBefore(":").ifBlank { "error" }
            ledger.reject(id, code, msg)
            Receipt.Rejected(code, msg)
        }
        else -> {
            val reason = e.message ?: e.javaClass.simpleName
            ledger.markUnknown(id, reason)
            Receipt.Unknown(reason)
        }
    }

    private fun put(r: SendRecord) {
        // 对同一个 agent 发了新消息 = 用户已经处理过上一条“结果未知”
        _records.value[r.key]?.let { old -> if (old.id != r.id && old.receipt is Receipt.Unknown) ledger.acknowledge(old.id) }
        _records.update { it + (r.key to r) }
    }

    companion object {
        fun target(key: AgentKey) = "${key.hostId}/${key.paneId}"
        fun targetKey(target: String): AgentKey? {
            val i = target.indexOf('/')
            if (i <= 0 || i == target.length - 1) return null
            return AgentKey(target.substring(0, i), target.substring(i + 1))
        }

        fun occupancyOf(p: io.github.luxiang0412.kelpie.core.herdr.Pane?) = p?.let { DraftKey.occupancyFingerprint(it) }
    }
}
