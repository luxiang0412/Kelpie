package herdrspike.write

import java.security.MessageDigest
import java.util.UUID

/**
 * 写操作三态。Unknown 永远不会被自动当成成功。
 */
sealed interface WriteOutcome {
    data object Pending : WriteOutcome
    data object Accepted : WriteOutcome
    data class Rejected(val code: String, val message: String) : WriteOutcome
    data class Unknown(val reason: String) : WriteOutcome
}

data class WriteRecord(
    val clientRequestId: String,
    val method: String,
    val target: String,
    val payloadSha256: String,
    val sentAtMs: Long,
    val attempts: Int,
    val outcome: WriteOutcome,
    val evidence: String? = null,
)

class NotSentException(msg: String) : IllegalStateException(msg)

/**
 * 写操作账本：
 * - 同一个 clientRequestId 只能发送一次；
 * - 超时 / 断线 → Unknown，不允许自动重试；
 * - Unknown 只有在权威读取拿到正向证据后才允许升级成 Accepted；
 * - 重发必须换新 id，原记录保留 Unknown（审计能看到两次独立尝试）。
 */
class WriteLedger(private val clock: () -> Long = System::currentTimeMillis) {

    private val records = LinkedHashMap<String, WriteRecord>()

    fun begin(method: String, target: String, payload: ByteArray): WriteRecord {
        val id = UUID.randomUUID().toString()
        val rec = WriteRecord(
            clientRequestId = id,
            method = method,
            target = target,
            payloadSha256 = sha256(payload),
            sentAtMs = clock(),
            attempts = 1,
            outcome = WriteOutcome.Pending,
        )
        records[id] = rec
        return rec
    }

    fun accept(id: String, evidence: String? = null) {
        val r = require(id)
        if (r.outcome !is WriteOutcome.Pending && r.outcome !is WriteOutcome.Unknown) {
            throw IllegalStateException("cannot accept from ${r.outcome}")
        }
        records[id] = r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
    }

    fun reject(id: String, code: String, message: String) {
        val r = require(id)
        if (r.outcome !is WriteOutcome.Pending) {
            throw IllegalStateException("cannot reject from ${r.outcome}")
        }
        records[id] = r.copy(outcome = WriteOutcome.Rejected(code, message))
    }

    fun markUnknown(id: String, reason: String) {
        val r = require(id)
        if (r.outcome !is WriteOutcome.Pending) {
            throw IllegalStateException("cannot mark unknown from ${r.outcome}")
        }
        records[id] = r.copy(outcome = WriteOutcome.Unknown(reason))
    }

    /**
     * 权威读取的结论。delivered=true 才能升级成 Accepted；false 保持 Unknown
     * （“确认没送达”也仍然是 Unknown，因为用户可能已经看到输入被部分写入）。
     */
    fun applyEvidence(id: String, delivered: Boolean, evidence: String) {
        val r = require(id)
        if (r.outcome !is WriteOutcome.Unknown) {
            throw IllegalStateException("evidence only applies to Unknown, was ${r.outcome}")
        }
        if (delivered) {
            records[id] = r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
        } else {
            records[id] = r.copy(evidence = evidence) // 仍是 Unknown
        }
    }

    /** 网络层是否允许自动重试。永远 false —— 这是契约，不是策略。 */
    fun autoRetryAllowed(id: String): Boolean = false

    /** 人工重发：生成新 id 的新记录，旧记录保持原状。 */
    fun manualResend(id: String): WriteRecord {
        val r = require(id)
        if (r.outcome is WriteOutcome.Pending) throw NotSentException("still pending: $id")
        val newRec = r.copy(
            clientRequestId = UUID.randomUUID().toString(),
            sentAtMs = clock(),
            attempts = 1,
            outcome = WriteOutcome.Pending,
            evidence = null,
        )
        records[newRec.clientRequestId] = newRec
        return newRec
    }

    fun get(id: String): WriteRecord? = records[id]
    fun all(): List<WriteRecord> = records.values.toList()

    private fun require(id: String): WriteRecord =
        records[id] ?: throw NoSuchElementException("unknown clientRequestId $id")

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
