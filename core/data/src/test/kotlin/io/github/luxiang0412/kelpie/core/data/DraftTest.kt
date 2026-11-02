package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.data.drafts.DraftEntity
import io.github.luxiang0412.kelpie.core.data.drafts.DraftKey
import io.github.luxiang0412.kelpie.core.data.drafts.DraftStore
import io.github.luxiang0412.kelpie.core.data.drafts.SendTargetCheck
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTest {

    private fun pane(
        id: String = "w1:p1",
        agent: String? = "pi",
        session: String? = "/home/u/.pi/sessions/a.jsonl",
        title: String = "t",
        cwd: String = "/repo",
    ) = Pane(id, "term-$id", "w1", "w1:t1", agent, AgentStatus.WORKING, title, cwd, 0, agentSessionPath = session)

    // ---------------- DraftKey ----------------

    @Test
    fun differentHosts_differentKeys() {
        val p = pane()
        assertNotEquals(DraftKey.of("h1", "test", p), DraftKey.of("h2", "test", p))
    }

    @Test
    fun differentSessions_differentKeys() {
        val p = pane()
        assertNotEquals(DraftKey.of("h1", "test", p), DraftKey.of("h1", "prod", p))
    }

    @Test
    fun differentAgentSessions_differentKeys() {
        assertNotEquals(
            DraftKey.of("h1", "test", pane(session = "/a.jsonl")),
            DraftKey.of("h1", "test", pane(session = "/b.jsonl")),
        )
    }

    @Test
    fun missingAgentSession_usesOccupancyFingerprint() {
        val k1 = DraftKey.of("h1", "test", pane(session = null, title = "agent-A"))
        val k2 = DraftKey.of("h1", "test", pane(session = null, title = "agent-B"))
        assertNotEquals("pane 上换了 agent 后不应复用旧草稿", k1, k2)
        assertTrue(k1.startsWith("h1|test|pane:"))
    }

    @Test
    fun nullPane_stableKey() {
        assertEquals(DraftKey.of("h1", "test", null), DraftKey.of("h1", "test", null))
    }

    // ---------------- SendTargetCheck ----------------

    @Test
    fun writableMatchingTarget_isOk() {
        val p = pane()
        assertEquals(
            SendTargetCheck.Result.Ok,
            SendTargetCheck.check(p, true, p.agentSessionPath, DraftKey.occupancyFingerprint(p)),
        )
    }

    @Test
    fun offline_isBlocked_notQueued() {
        val p = pane()
        val r = SendTargetCheck.check(p, writable = false, editedAgentSession = p.agentSessionPath, editedOccupancy = null)
        assertTrue(r is SendTargetCheck.Result.Blocked)
        assertEquals("not_writable", (r as SendTargetCheck.Result.Blocked).code)
    }

    @Test
    fun missingPane_isBlocked() {
        assertTrue(SendTargetCheck.check(null, true, null, null) is SendTargetCheck.Result.Blocked)
    }

    @Test
    fun agentGone_isBlocked() {
        val p = pane(agent = null)
        val r = SendTargetCheck.check(p, true, null, null) as SendTargetCheck.Result.Blocked
        assertEquals("no_agent", r.code)
    }

    @Test
    fun agentSessionChanged_needsConfirm() {
        val p = pane(session = "/new.jsonl")
        val r = SendTargetCheck.check(p, true, "/old.jsonl", null)
        assertTrue(r is SendTargetCheck.Result.NeedsConfirm)
    }

    @Test
    fun occupancyChanged_needsConfirm() {
        val p = pane(session = null, title = "agent-B")
        val r = SendTargetCheck.check(p, true, null, "pane:deadbeef")
        assertTrue(r is SendTargetCheck.Result.NeedsConfirm)
    }

    // ---------------- DraftStore ----------------

    private class FakeDao : io.github.luxiang0412.kelpie.core.data.drafts.DraftDao {
        val rows = LinkedHashMap<String, DraftEntity>()
        override suspend fun get(key: String) = rows[key]
        override suspend fun upsert(draft: DraftEntity) { rows[draft.key] = draft }
        override suspend fun delete(key: String) { rows.remove(key) }
        override suspend fun deleteByHost(hostId: String) { rows.values.removeAll { it.hostId == hostId } }
        override suspend fun count() = rows.size
    }

    @Test
    fun saveAndLoad_roundTrips() = runBlocking {
        val store = DraftStore(FakeDao())
        store.save("k", "h1", "test", "/a.jsonl", "hello")
        assertEquals("hello", store.load("k"))
    }

    @Test
    fun emptyText_deletesDraft() = runBlocking {
        val store = DraftStore(FakeDao())
        store.save("k", "h1", "test", null, "x")
        store.save("k", "h1", "test", null, "")
        assertNull(store.load("k"))
        assertEquals(0, store.count())
    }

    @Test
    fun oversizedDraft_isTruncated() = runBlocking {
        val store = DraftStore(FakeDao())
        store.save("k", "h1", "test", null, "a".repeat(DraftStore.MAX_CHARS + 500))
        assertEquals(DraftStore.MAX_CHARS, store.load("k")!!.length)
    }

    @Test
    fun deleteByHost_onlyAffectsThatHost() = runBlocking {
        val store = DraftStore(FakeDao())
        store.save("a", "h1", "s", null, "1")
        store.save("b", "h2", "s", null, "2")
        store.deleteByHost("h1")
        assertNull(store.load("a"))
        assertEquals("2", store.load("b"))
        assertFalse(store.count() == 0)
    }
}
