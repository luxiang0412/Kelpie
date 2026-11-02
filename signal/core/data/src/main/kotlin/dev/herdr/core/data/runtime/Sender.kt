package dev.herdr.core.data.runtime

import dev.herdr.core.herdr.t

import dev.herdr.core.data.attention.AgentKey
import dev.herdr.core.data.drafts.DraftKey
import dev.herdr.core.data.drafts.SendTargetCheck
import dev.herdr.core.herdr.PendingWriteSink
import dev.herdr.core.herdr.WriteLedger
import dev.herdr.core.herdr.WriteRecord
import dev.herdr.core.ssh.SshFailure
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

/** 发送前需要用户确认的情况（目标变了）。 */
data class SendBlocked(val code: String, val message: String, val needsConfirm: Boolean)

/**
 * 写操作：只有 `agent.prompt`。每次发送都先进 [WriteLedger]（未确认的写会落库，进程重建后可见）。
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

    fun restore(pending: List<WriteRecord>) = ledger.restore(pending)

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

        val rec = ledger.begin("agent.prompt", key.paneId, body.toByteArray())
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

    fun dismiss(key: AgentKey) = _records.update { it - key }

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

    private fun put(r: SendRecord) = _records.update { it + (r.key to r) }

    companion object {
        fun occupancyOf(p: dev.herdr.core.herdr.Pane?) = p?.let { DraftKey.occupancyFingerprint(it) }
    }
}
