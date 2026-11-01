package dev.herdr.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoardDaoTest {

    private lateinit var db: BoardDatabase
    private lateinit var dao: BoardDao

    @Before
    fun setUp() {
        val ctx = InstrumentationProvider.context()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).build()
        dao = db.boardDao()
    }

    @After
    fun tearDown() = db.close()

    private fun pane(host: String, session: String, id: String, status: String) = PaneEntity(
        hostId = host, session = session, paneId = id, terminalId = "t-$id",
        workspaceId = "w1", tabId = "w1:t1", agent = "pi", status = status,
        title = "title-$id", cwd = "/tmp", revision = 1, updatedAtMs = 1L,
    )

    private fun meta(host: String, session: String, stale: Boolean) = SessionMetaEntity(
        hostId = host, session = session, version = "0.9.5", protocol = 22,
        fetchedAtMs = 123L, stale = stale, lastError = null,
    )

    @Test
    fun replaceSnapshot_isolatesHostAndSession() = runBlocking {
        dao.replaceSnapshot("h1", "test", listOf(pane("h1", "test", "w1:p1", "idle")), meta("h1", "test", false))
        dao.replaceSnapshot("h1", "other", listOf(pane("h1", "other", "w1:p1", "working")), meta("h1", "other", false))

        val test = dao.observePanes("h1", "test").first()
        val other = dao.observePanes("h1", "other").first()
        assertEquals(1, test.size)
        assertEquals("idle", test[0].status)
        assertEquals(1, other.size)
        assertEquals("working", other[0].status)
    }

    @Test
    fun replaceSnapshot_dropsPanesThatDisappeared() = runBlocking {
        dao.replaceSnapshot(
            "h1", "test",
            listOf(pane("h1", "test", "w1:p1", "idle"), pane("h1", "test", "w1:p2", "done")),
            meta("h1", "test", false),
        )
        dao.replaceSnapshot("h1", "test", listOf(pane("h1", "test", "w1:p1", "blocked")), meta("h1", "test", false))

        val panes = dao.observePanes("h1", "test").first()
        assertEquals(listOf("w1:p1"), panes.map { it.paneId })
        assertEquals("blocked", panes[0].status)
    }

    @Test
    fun metaCarriesStaleAndError() = runBlocking {
        dao.replaceSnapshot("h1", "test", emptyList(), meta("h1", "test", true))
        val m = dao.observeMeta("h1", "test").first()
        assertEquals(true, m?.stale)
        assertEquals(22, m?.protocol)
    }

    @Test
    fun unknownHostSessionHasNoMeta() = runBlocking {
        assertNull(dao.observeMeta("nope", "test").first())
        assertEquals(0, dao.observePanes("nope", "test").first().size)
    }
}
