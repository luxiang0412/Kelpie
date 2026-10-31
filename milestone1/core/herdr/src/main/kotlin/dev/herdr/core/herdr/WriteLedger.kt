package dev.herdr.core.herdr

import java.security.MessageDigest
import java.util.UUID

/**
 * 写操作三态账本（D2/D21）。
 *
 * - `Unknown` 永远不会被自动升级成成功，也不会被自动重试；
 * - 只有权威读取拿到正向证据才能升级成 `Accepted`；
 * - 人工重发必须用新的 clientRequestId，旧记录保留（审计能看到两次独立尝试）。
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
        require(r.outcome is WriteOutcome.Pending || r.outcome is WriteOutcome.Unknown) {
            "cannot accept from ${r.outcome}"
        }
        records[id] = r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
    }

    fun reject(id: String, code: String, message: String) {
        val r = require(id)
        require(r.outcome is WriteOutcome.Pending) { "cannot reject from ${r.outcome}" }
        records[id] = r.copy(outcome = WriteOutcome.Rejected(code, message))
    }

    fun markUnknown(id: String, reason: String) {
        val r = require(id)
        require(r.outcome is WriteOutcome.Pending) { "cannot mark unknown from ${r.outcome}" }
        records[id] = r.copy(outcome = WriteOutcome.Unknown(reason))
    }

    /** 权威读取的结论：只有 delivered=true 才升级为 Accepted。 */
    fun applyEvidence(id: String, delivered: Boolean, evidence: String) {
        val r = require(id)
        require(r.outcome is WriteOutcome.Unknown) { "evidence only applies to Unknown, was ${r.outcome}" }
        records[id] = if (delivered) {
            r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
        } else {
            r.copy(evidence = evidence) // 仍是 Unknown
        }
    }

    /** 网络层是否允许自动重试：永远 false（契约，不是策略）。 */
    fun autoRetryAllowed(id: String): Boolean = false

    fun get(id: String): WriteRecord? = records[id]

    fun recent(limit: Int = 50): List<WriteRecord> = records.values.toList().takeLast(limit)

    private fun require(id: String): WriteRecord =
        records[id] ?: throw NoSuchElementException("unknown clientRequestId $id")

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
