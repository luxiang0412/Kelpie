package dev.herdr.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * 本地缓存（只读看板用）。
 * 主键含 hostId + session，因为同一个 paneId 在不同主机会话里可能重复。
 */
@Entity(tableName = "panes", primaryKeys = ["hostId", "session", "paneId"])
data class PaneEntity(
    val hostId: String,
    val session: String,
    val paneId: String,
    val terminalId: String?,
    val workspaceId: String,
    val tabId: String,
    val agent: String?,
    val status: String,
    val title: String?,
    val cwd: String?,
    val revision: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "session_meta", primaryKeys = ["hostId", "session"])
data class SessionMetaEntity(
    val hostId: String,
    val session: String,
    val version: String,
    val protocol: Int,
    val fetchedAtMs: Long,
    val stale: Boolean,
    val lastError: String?,
)

@Dao
interface BoardDao {
    @Query("SELECT * FROM panes WHERE hostId = :hostId AND session = :session ORDER BY paneId")
    fun observePanes(hostId: String, session: String): Flow<List<PaneEntity>>

    @Query("SELECT * FROM session_meta WHERE hostId = :hostId AND session = :session")
    fun observeMeta(hostId: String, session: String): Flow<SessionMetaEntity?>

    @Query("SELECT * FROM panes")
    suspend fun all(): List<PaneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPanes(panes: List<PaneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: SessionMetaEntity)

    @Query("DELETE FROM panes WHERE hostId = :hostId AND session = :session")
    suspend fun clearPanes(hostId: String, session: String)

    @Transaction
    suspend fun replaceSnapshot(
        hostId: String,
        session: String,
        panes: List<PaneEntity>,
        meta: SessionMetaEntity,
    ) {
        clearPanes(hostId, session)
        upsertPanes(panes)
        upsertMeta(meta)
    }
}

@Database(entities = [PaneEntity::class, SessionMetaEntity::class], version = 1, exportSchema = false)
abstract class BoardDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
}
