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
    val agentSessionPath: String? = null,
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

@Entity(tableName = "transcript_messages", primaryKeys = ["hostId", "path", "seq"])
data class TranscriptMessageEntity(
    val hostId: String,
    val path: String,
    val seq: Int,
    val kind: String,
    val text: String,
    val toolName: String?,
    val timestamp: Long?,
)

@Entity(tableName = "transcript_cursor", primaryKeys = ["hostId", "path"])
data class TranscriptCursorEntity(
    val hostId: String,
    val path: String,
    val offset: Long,
    val headHash: String?,
    val updatedAtMs: Long,
)

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_messages WHERE hostId = :hostId AND path = :path ORDER BY seq")
    fun observe(hostId: String, path: String): Flow<List<TranscriptMessageEntity>>

    @Query("SELECT COALESCE(MAX(seq), -1) FROM transcript_messages WHERE hostId = :hostId AND path = :path")
    suspend fun maxSeq(hostId: String, path: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun append(messages: List<TranscriptMessageEntity>)

    @Query("DELETE FROM transcript_messages WHERE hostId = :hostId AND path = :path")
    suspend fun clearMessages(hostId: String, path: String)

    @Query("SELECT * FROM transcript_cursor WHERE hostId = :hostId AND path = :path")
    suspend fun cursor(hostId: String, path: String): TranscriptCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(cursor: TranscriptCursorEntity)

    @Query("DELETE FROM transcript_cursor WHERE hostId = :hostId AND path = :path")
    suspend fun clearCursor(hostId: String, path: String)

    @Query("DELETE FROM transcript_messages WHERE hostId = :hostId")
    suspend fun clearHost(hostId: String)
}

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

@Database(
    entities = [PaneEntity::class, SessionMetaEntity::class, TranscriptMessageEntity::class, TranscriptCursorEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class BoardDatabase : RoomDatabase() {
    abstract fun boardDao(): BoardDao
    abstract fun transcriptDao(): TranscriptDao
}
