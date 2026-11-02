package dev.herdr.core.data.drafts

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dev.herdr.core.herdr.Pane

/**
 * 草稿（M2-C2）。
 *
 * 独立数据库（`herdr-drafts.db`）而不是塞进 `herdr.db`：后者用
 * `fallbackToDestructiveMigration` 作为缓存策略，升级时会清空——草稿不能跟着丢。
 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val key: String,
    val text: String,
    val updatedAtMs: Long,
    val hostId: String,
    val session: String,
    val agentSession: String?,
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM drafts WHERE hostId = :hostId")
    suspend fun deleteByHost(hostId: String)

    @Query("SELECT COUNT(*) FROM drafts")
    suspend fun count(): Int
}

@Database(
    entities = [DraftEntity::class, PendingWriteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DraftDatabase : RoomDatabase() {
    abstract fun dao(): DraftDao
    abstract fun pendingWrites(): PendingWriteDao

    companion object {
        fun build(context: Context): DraftDatabase =
            Room.databaseBuilder(context, DraftDatabase::class.java, "signal-drafts.db")
                .build()
    }
}

/**
 * 草稿身份（DraftKey）。必须同时区分：
 *  - 主机（不同主机的同名 session 不能互串）
 *  - Herdr session
 *  - Agent session（`agent_session_path`）
 *
 * 当 `agent_session` 缺失时退化为「pane 占用指纹」：paneId + agent + title + cwd。
 * 这只能降低串稿概率，**不能**保证绝对不串——因此发送前还有目标校验（见 [SendTargetCheck]），
 * 身份变化时必须让用户重新确认，绝不直接发出旧草稿。
 */
object DraftKey {

    fun of(hostId: String, session: String, pane: Pane?): String {
        val scope = pane?.agentSessionPath?.takeIf { it.isNotBlank() }
            ?: occupancyFingerprint(pane)
        return "$hostId|$session|$scope"
    }

    fun occupancyFingerprint(pane: Pane?): String {
        if (pane == null) return "no-pane"
        val raw = listOf(pane.paneId, pane.agent ?: "-", pane.title ?: "-", pane.cwd ?: "-").joinToString("|")
        return "pane:" + Integer.toHexString(raw.hashCode())
    }
}

/**
 * 发送前目标校验（P0 §5）。只做「可检测到的不一致」防护，不宣称消除 TOCTOU。
 */
object SendTargetCheck {

    sealed interface Result {
        data object Ok : Result
        data class Blocked(val code: String, val reason: String) : Result
        data class NeedsConfirm(val reason: String) : Result
    }

    /**
     * @param editedAgentSession 编辑草稿时看到的 agent session（可能为 null）
     * @param editedOccupancy 编辑草稿时的 pane 占用指纹
     * @param writable 当前连接是否可写（已连接且未在重连）
     */
    fun check(
        pane: Pane?,
        writable: Boolean,
        editedAgentSession: String?,
        editedOccupancy: String?,
    ): Result {
        if (pane == null) return Result.Blocked("pane_gone", "目标 pane 已不存在")
        if (pane.agent == null) return Result.Blocked("no_agent", "这个 pane 没有运行 agent")
        if (!writable) return Result.Blocked("not_writable", "当前连接不可写（未连接或重连中）")

        val nowAgent = pane.agentSessionPath?.takeIf { it.isNotBlank() }
        if (editedAgentSession != null && nowAgent != null && editedAgentSession != nowAgent) {
            return Result.NeedsConfirm("agent session 已变化，发送将进入新会话")
        }
        if (editedAgentSession != null && nowAgent == null) {
            return Result.NeedsConfirm("当前 pane 已没有 agent session")
        }
        if (editedOccupancy != null && nowAgent == null && DraftKey.occupancyFingerprint(pane) != editedOccupancy) {
            return Result.NeedsConfirm("pane 上的 agent 可能已更换")
        }
        return Result.Ok
    }
}

/** 草稿读写（Room）。防抖由调用方（ViewModel）负责。 */
class DraftStore(private val dao: DraftDao) {

    suspend fun load(key: String): String? = dao.get(key)?.text

    suspend fun save(key: String, hostId: String, session: String, agentSession: String?, text: String) {
        if (text.isEmpty()) {
            dao.delete(key)
            return
        }
        val bounded = if (text.length > MAX_CHARS) text.take(MAX_CHARS) else text
        dao.upsert(
            DraftEntity(
                key = key,
                text = bounded,
                updatedAtMs = System.currentTimeMillis(),
                hostId = hostId,
                session = session,
                agentSession = agentSession,
            ),
        )
    }

    suspend fun clear(key: String) = dao.delete(key)

    suspend fun deleteByHost(hostId: String) = dao.deleteByHost(hostId)

    suspend fun count(): Int = dao.count()

    companion object {
        /** 单条草稿上限（约 8K 字符）：足够长 prompt，又不会让 DB/内存失控。 */
        const val MAX_CHARS = 8_000
        const val DEBOUNCE_MS = 400L
    }
}
