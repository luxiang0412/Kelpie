package dev.herdr.core.data.drafts

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dev.herdr.core.herdr.PendingWriteSink
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.core.herdr.WriteRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 未确认写操作（Pending/Unknown）的持久化（M2-C2-V / I-88）。
 *
 * 只存审计必需字段；不额外复制完整 prompt（敏感文本仍只在草稿库/待确认面板里，由用户可见）。
 */
@Entity(tableName = "pending_writes")
data class PendingWriteEntity(
    @PrimaryKey val clientRequestId: String,
    val method: String,
    val target: String,
    val payloadSha256: String,
    val sentAtMs: Long,
    val attempts: Int,
    val outcomeKind: String,
    val reason: String?,
    val evidence: String?,
)

@Dao
interface PendingWriteDao {
    @Query("SELECT * FROM pending_writes")
    suspend fun all(): List<PendingWriteEntity>

    @Upsert
    suspend fun upsert(rows: List<PendingWriteEntity>)

    @Query("DELETE FROM pending_writes")
    suspend fun clear()

    @Query("DELETE FROM pending_writes WHERE clientRequestId = :id")
    suspend fun delete(id: String)
}

fun PendingWriteEntity.toRecord(): WriteRecord = WriteRecord(
    clientRequestId = clientRequestId,
    method = method,
    target = target,
    payloadSha256 = payloadSha256,
    sentAtMs = sentAtMs,
    attempts = attempts,
    outcome = when (outcomeKind) {
        "unknown" -> WriteOutcome.Unknown(reason ?: "unknown")
        else -> WriteOutcome.Pending
    },
    evidence = evidence,
)

fun WriteRecord.toEntity(): PendingWriteEntity = PendingWriteEntity(
    clientRequestId = clientRequestId,
    method = method,
    target = target,
    payloadSha256 = payloadSha256,
    sentAtMs = sentAtMs,
    attempts = attempts,
    outcomeKind = if (outcome is WriteOutcome.Unknown) "unknown" else "pending",
    reason = (outcome as? WriteOutcome.Unknown)?.reason,
    evidence = evidence,
)

/** Room 实现：整表替换（未确认记录通常个位数）。 */
class RoomPendingWriteSink(
    private val dao: PendingWriteDao,
    private val scope: CoroutineScope,
) : PendingWriteSink {

    override fun onChanged(pending: List<WriteRecord>) {
        val rows = pending.map { it.toEntity() }
        scope.launch { runCatching { dao.clear(); dao.upsert(rows) } }
    }

    suspend fun load(): List<WriteRecord> = dao.all().map { it.toRecord() }
}
