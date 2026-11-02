package dev.herdr.core.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.drafts.DraftDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 草稿库迁移 1 → 2（I-89）。
 *
 * 用手写的 v1 库执行 [DraftDatabase.MIGRATION_1_2]，断言：① 草稿数据不丢；② pending_writes 被创建。
 * 说明：不是 MigrationTestHelper 的全量校验（不含 Room 主表哈希），但足以证明迁移语句非破坏性。
 */
@RunWith(AndroidJUnit4::class)
class DraftMigrationTest {

    private val dbName = "draft-migration-test.db"

    @Test
    fun migration_1_2_preservesDrafts_andAddsPendingWrites() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase(dbName)

        var version = 1
        val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `drafts` (`key` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                            "`updatedAtMs` INTEGER NOT NULL, `hostId` TEXT NOT NULL, `session` TEXT NOT NULL, " +
                            "`agentSession` TEXT, PRIMARY KEY(`key`))",
                    )
                    db.execSQL(
                        "INSERT INTO drafts VALUES ('h1|test|/a.jsonl', 'keep me 保留我', 1, 'h1', 'test', '/a.jsonl')",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        DraftDatabase.MIGRATION_1_2.migrate(db)
        version = 2
        assertEquals(2, version)

        val cursor = db.query("SELECT text FROM drafts")
        cursor.moveToFirst()
        assertEquals("keep me 保留我", cursor.getString(0))
        cursor.close()

        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pending_writes'")
        assertTrue("pending_writes 表应被创建", tables.count == 1)
        tables.close()

        db.close()
        ctx.deleteDatabase(dbName)
    }
}
