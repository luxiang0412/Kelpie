package io.github.luxiang0412.kelpie.core.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAgentTest {
    @Test fun dirMustBeAbsoluteOrHome() {
        assertEquals("~/project/x", NewAgent.normalizeDir(" ~/project/x/ ").getOrThrow())
        assertEquals("/", NewAgent.normalizeDir("/").getOrThrow())
        assertTrue(NewAgent.normalizeDir("project/x").isFailure)
        assertTrue(NewAgent.normalizeDir("").isFailure)
        assertTrue(NewAgent.normalizeDir("/a\nb").isFailure)
    }

    @Test fun homeIsExpandedOnlyWhenNeeded() {
        assertEquals("/home/u/p", NewAgent.resolve("~/p") { "/home/u/" })
        assertEquals("/home/u", NewAgent.resolve("~") { "/home/u" })
        assertEquals("/srv/p", NewAgent.resolve("/srv/p") { error("must not ask for home") })
    }

    @Test fun nameIsSafeAndUnique() {
        val a = NewAgent.name("/home/u/项目 x", "claude", 1_000)
        val b = NewAgent.name("/home/u/项目 x", "claude", 2_000)
        assertTrue(a, a.matches(Regex("[A-Za-z0-9._-]+")))
        assertTrue(a.startsWith("x-claude-"))
        assertTrue(a != b)
        assertEquals("agent-pi-", NewAgent.name("/home/u/项目", "pi", 0).dropLast(4))
    }

    @Test fun recentDirsPreferAgentsAndSkipDeleted() {
        val dirs = NewAgent.recentDirs(listOf("/a" to false, "/b" to true, "/a" to true, "/c (deleted)" to true, "rel" to true))
        assertEquals(listOf("/b", "/a"), dirs)
    }
}
