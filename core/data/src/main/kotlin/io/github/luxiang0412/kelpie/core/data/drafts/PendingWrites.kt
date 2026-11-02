package io.github.luxiang0412.kelpie.core.data.drafts

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.luxiang0412.kelpie.core.herdr.PendingWriteSink
import io.github.luxiang0412.kelpie.core.herdr.WriteOutcome
import io.github.luxiang0412.kelpie.core.herdr.WriteRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

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
abstract class PendingWriteDao {
    @Query("SELECT * FROM pending_writes")
    abstract suspend fun all(): List<PendingWriteEntity>

    @Upsert
    abstract suspend fun upsert(rows: List<PendingWriteEntity>)

    @Query("DELETE FROM pending_writes")
    abstract suspend fun clear()

    @Query("DELETE FROM pending_writes WHERE clientRequestId = :id")
    abstract suspend fun delete(id: String)

    /** 整表替换，在一个事务里：中途进程被杀也不会只剩“清空了一半”的表。 */
    @Transaction
    open suspend fun replaceAll(rows: List<PendingWriteEntity>) {
        clear()
        upsert(rows)
    }
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

/**
 * Room 实现：整表替换（未确认记录通常个位数）。
 *
 * 每次变化都会触发一次保存；保存是异步的，连续两次变化时必须保证**新的快照最后落盘**：
 * 用一把锁串行化，并且拿到锁时如果已经有更新的快照，就跳过这次（旧的不再写）。
 */
class RoomPendingWriteSink(
    private val dao: PendingWriteDao,
    private val scope: CoroutineScope,
) : PendingWriteSink {

    private val lock = Mutex()
    private val latest = AtomicLong(0)

    override fun onChanged(pending: List<WriteRecord>) {
        val rows = pending.map { it.toEntity() }
        val seq = latest.incrementAndGet()
        scope.launch {
            lock.withLock {
                if (seq != latest.get()) return@withLock // 已经有更新的快照排在后面
                runCatching { dao.replaceAll(rows) }
            }
        }
    }

    suspend fun load(): List<WriteRecord> = dao.all().map { it.toRecord() }
}
