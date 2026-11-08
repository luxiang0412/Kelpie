package io.github.luxiang0412.kelpie.core.data

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * v3 → 最新版本的迁移：用 v3 的表结构手工建库（v3 没有导出 schema），再用正式的 [BoardDatabase.build] 打开。
 * Room 打开时会按 v4 的实体校验表结构，列 / 默认值对不上会直接抛异常。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardMigrationTest {

    @Test
    fun `v3 database opens at the latest version, keeps panes and clears transcript cache`() = runBlocking {
        val ctx = RuntimeEnvironment.getApplication()
        val file = ctx.getDatabasePath("kelpie-board.db").also { it.parentFile?.mkdirs(); it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V3_SCHEMA.forEach(db::execSQL)
            db.execSQL("INSERT INTO panes VALUES ('h','test','p1',NULL,'w','t',NULL,'IDLE',NULL,NULL,1,NULL,NULL,0)")
            db.execSQL("INSERT INTO transcript_messages VALUES ('h','/f',0,'USER','hi',NULL,NULL,NULL,NULL,NULL,NULL)")
            db.execSQL("INSERT INTO transcript_cursor VALUES ('h','/f',300000000,'hash',0)")
            db.version = 3
        }

        val board = BoardDatabase.build(ctx)
        try {
            assertEquals(1, board.boardDao().all().size)
            assertEquals(-1, board.transcriptDao().maxSeq("h", "/f"))
            assertNull(board.transcriptDao().cursor("h", "/f"))
            // 新列可用，默认值是 false
            board.transcriptDao().saveCursor(TranscriptCursorEntity("h", "/f", 1, null, 0))
            assertFalse(board.transcriptDao().cursor("h", "/f")!!.truncated)
        } finally {
            board.close()
        }
    }

    private companion object {
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `panes` (`hostId` TEXT NOT NULL, `session` TEXT NOT NULL, `paneId` TEXT NOT NULL, `terminalId` TEXT, `workspaceId` TEXT NOT NULL, `tabId` TEXT NOT NULL, `agent` TEXT, `status` TEXT NOT NULL, `title` TEXT, `cwd` TEXT, `revision` INTEGER NOT NULL, `agentSessionPath` TEXT, `agentSessionId` TEXT, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`hostId`, `session`, `paneId`))",
            "CREATE TABLE IF NOT EXISTS `session_meta` (`hostId` TEXT NOT NULL, `session` TEXT NOT NULL, `version` TEXT NOT NULL, `protocol` INTEGER NOT NULL, `fetchedAtMs` INTEGER NOT NULL, `stale` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`hostId`, `session`))",
            "CREATE TABLE IF NOT EXISTS `transcript_messages` (`hostId` TEXT NOT NULL, `path` TEXT NOT NULL, `seq` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, `toolName` TEXT, `msgId` TEXT, `parentId` TEXT, `toolCallId` TEXT, `timestamp` INTEGER, `structured` TEXT, PRIMARY KEY(`hostId`, `path`, `seq`))",
            "CREATE TABLE IF NOT EXISTS `transcript_cursor` (`hostId` TEXT NOT NULL, `path` TEXT NOT NULL, `offset` INTEGER NOT NULL, `headHash` TEXT, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`hostId`, `path`))",
        )
    }
}
