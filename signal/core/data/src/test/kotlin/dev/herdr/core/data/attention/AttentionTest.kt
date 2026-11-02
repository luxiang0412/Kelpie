package dev.herdr.core.data.attention

import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTest {

    private fun pane(id: String, s: AgentStatus, seq: Long? = 1, agent: String? = "pi", title: String? = "π - repo", cwd: String? = "/home/u/repo") =
        Pane(id, null, "w", "t", agent, s, title, cwd, 1, completionSeq = seq)

    private fun host(id: String, panes: List<Pane>, reach: HostReach = HostReach.Online) =
        HostInput(id, id.uppercase(), reach, panes, fetchedAtMs = 1)

    @Test fun firstSightingIsLowerBound_changeIsExact() {
        val t = AttentionTracker()
        t.update("h", listOf(pane("p", AgentStatus.BLOCKED)), 1_000)
        val o1 = t.observation(AgentKey("h", "p"))!!
        assertTrue(o1.sinceIsLowerBound)
        assertEquals(1_000, o1.sinceMs)

        t.update("h", listOf(pane("p", AgentStatus.BLOCKED)), 5_000)
        assertEquals("unchanged status keeps original since", 1_000, t.observation(AgentKey("h", "p"))!!.sinceMs)

        t.update("h", listOf(pane("p", AgentStatus.WORKING)), 9_000)
        t.update("h", listOf(pane("p", AgentStatus.BLOCKED)), 12_000)
        val o2 = t.observation(AgentKey("h", "p"))!!
        assertFalse(o2.sinceIsLowerBound)
        assertEquals(12_000, o2.sinceMs)
    }

    @Test fun completionSeqCatchesDoneWorkingDoneBetweenSamples() {
        val t = AttentionTracker()
        t.update("h", listOf(pane("p", AgentStatus.IDLE, seq = 4)), 0)
        // 两次采样之间跑了一整轮：状态没变，但序号涨了
        val tr = t.update("h", listOf(pane("p", AgentStatus.IDLE, seq = 5)), 60_000)
        assertEquals(1, tr.size)
        assertTrue(tr[0].completed)
        assertEquals(60_000L, t.observation(AgentKey("h", "p"))!!.finishedAtMs)
    }

    @Test fun closedPanesAreForgotten_otherHostsUntouched() {
        val t = AttentionTracker()
        t.update("a", listOf(pane("p1", AgentStatus.IDLE), pane("p2", AgentStatus.IDLE)), 0)
        t.update("b", listOf(pane("p1", AgentStatus.IDLE)), 0)
        t.update("a", listOf(pane("p1", AgentStatus.IDLE)), 1)
        assertNull(t.observation(AgentKey("a", "p2")))
        assertTrue(t.observation(AgentKey("b", "p1")) != null)
    }

    @Test fun deriveSortsLongestWaitFirst_andGroups() {
        val t = AttentionTracker()
        val now = 10 * 60_000L
        t.update("a", listOf(pane("old", AgentStatus.IDLE), pane("new", AgentStatus.IDLE)), 0)
        t.update("a", listOf(pane("old", AgentStatus.BLOCKED), pane("new", AgentStatus.IDLE)), 60_000)
        t.update("a", listOf(pane("old", AgentStatus.BLOCKED), pane("new", AgentStatus.BLOCKED)), 5 * 60_000)
        val panes = listOf(
            pane("old", AgentStatus.BLOCKED), pane("new", AgentStatus.BLOCKED),
            pane("w", AgentStatus.WORKING), pane("d", AgentStatus.DONE), pane("i", AgentStatus.IDLE),
        )
        t.update("a", panes, now)
        val home = HomeDerivation.derive(listOf(host("a", panes)), t.snapshot(), now)
        assertEquals(listOf("old", "new"), home.needsYou.map { it.key.paneId })
        assertEquals(listOf("w"), home.working.map { it.key.paneId })
        assertEquals(listOf("d"), home.recentlyDone.map { it.key.paneId })
        assertEquals(listOf("i"), home.quiet.map { it.key.paneId })
        assertFalse(home.allClear)
    }

    @Test fun unreachableHostKeepsLastSeen_sortedAfterLive() {
        val t = AttentionTracker()
        val live = listOf(pane("x", AgentStatus.BLOCKED))
        val dead = listOf(pane("y", AgentStatus.BLOCKED))
        t.update("dead", dead, 0)
        t.update("live", live, 100)
        val home = HomeDerivation.derive(
            listOf(host("dead", dead, HostReach.Unreachable), host("live", live)), t.snapshot(), 200,
        )
        assertEquals(listOf("x", "y"), home.needsYou.map { it.key.paneId })
        assertTrue(home.needsYou[1].lastSeen)
        assertEquals(1, home.unreachableHosts.size)
    }

    @Test fun recentlyFinishedIdleCountsAsDone_untilWindowExpires() {
        val t = AttentionTracker()
        t.update("a", listOf(pane("p", AgentStatus.IDLE, seq = 1)), 0)
        t.update("a", listOf(pane("p", AgentStatus.IDLE, seq = 2)), 1_000)
        val panes = listOf(pane("p", AgentStatus.IDLE, seq = 2))
        assertEquals(1, HomeDerivation.derive(listOf(host("a", panes)), t.snapshot(), 2_000).recentlyDone.size)
        val later = 1_000 + HomeDerivation.DEFAULT_RECENT_WINDOW_MS + 1
        assertEquals(0, HomeDerivation.derive(listOf(host("a", panes)), t.snapshot(), later).recentlyDone.size)
    }

    @Test fun emptyAndLoadingStates() {
        assertTrue(HomeDerivation.derive(emptyList(), emptyMap(), 0).noHosts)
        val loading = HomeDerivation.derive(listOf(HostInput("a", "A", HostReach.Connecting, emptyList(), 0)), emptyMap(), 0)
        assertTrue(loading.loading)
        assertFalse(loading.allClear)
    }

    @Test fun titles() {
        assertEquals("spike-sandbox", Titles.of(pane("p", AgentStatus.IDLE, title = "π - spike-sandbox")))
        assertEquals("repo", Titles.of(pane("p", AgentStatus.IDLE, agent = "claude", title = "Claude Code")))
        assertEquals("修复登录超时", Titles.of(pane("p", AgentStatus.IDLE, agent = "claude", title = "✳ 修复登录超时")))
        assertEquals("repo", Titles.of(pane("p", AgentStatus.IDLE, agent = "codex", title = "")))
    }

    @Test fun durations() {
        assertEquals("至少 12 分钟", Durations.waited(0, true, 12 * 60_000))
        assertEquals("12 分钟", Durations.waited(0, false, 12 * 60_000))
        assertEquals("刚刚", Durations.waited(0, false, 30_000))
        assertEquals("2 小时 15 分", Durations.waited(0, false, 135 * 60_000))
        assertEquals("1 小时", Durations.waited(0, false, 65 * 60_000))
        assertEquals("3 分钟前", Durations.ago(0, 3 * 60_000))
    }
}
