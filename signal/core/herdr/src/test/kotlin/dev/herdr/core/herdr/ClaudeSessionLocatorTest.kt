package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSessionLocatorTest {

    private val H = 3600_000L

    @Test
    fun slug_matchesClaudeProjectsNaming() {
        assertEquals(
            "-home-luxiang-project-work-xn-ontology",
            ClaudeSessionLocator.slugFor("/home/luxiang/project/work/xn-ontology"),
        )
        assertEquals(
            "-home-luxiang-project-private-herdr-gui-research",
            ClaudeSessionLocator.slugFor("/home/luxiang/project/private/herdr-gui-research/"),
        )
    }

    @Test
    fun pickLatest_prefersNewestWithinWindow() {
        val now = 1_000 * H
        val picked = ClaudeSessionLocator.pickLatest(
            listOf(
                ClaudeSessionLocator.Candidate("/a/old.jsonl", now - 5 * H),
                ClaudeSessionLocator.Candidate("/a/new.jsonl", now - 1 * H),
            ),
            nowMs = now,
            windowMs = 24 * H,
        )
        assertEquals("/a/new.jsonl", picked?.path)
    }

    @Test
    fun pickLatest_rejectsStale() {
        val now = 100 * H
        val picked = ClaudeSessionLocator.pickLatest(
            listOf(ClaudeSessionLocator.Candidate("/a/old.jsonl", now - 30 * H)),
            nowMs = now,
            windowMs = 24 * H,
        )
        assertNull("超出时间窗必须放弃", picked)
    }

    @Test
    fun sessionId_isReadFromHeadLines() {
        val head = listOf(
            """{"type":"mode","mode":"normal","sessionId":"f576f230-03c2-41ca-9267-26118466d305"}""",
            """{"type":"user","uuid":"u1","sessionId":"f576f230-03c2-41ca-9267-26118466d305"}""",
        )
        assertEquals("f576f230-03c2-41ca-9267-26118466d305", ClaudeSessionLocator.sessionIdOf(head))
    }

    @Test
    fun sessionIdMatches_requiresBothSides() {
        assertTrue(ClaudeSessionLocator.sessionIdMatches("abc", "abc"))
        assertTrue(ClaudeSessionLocator.sessionIdMatches("sess-abc", "abc"))
        assertFalse(ClaudeSessionLocator.sessionIdMatches("abc", "abd"))
        assertFalse("缺失一侧不得放行", ClaudeSessionLocator.sessionIdMatches(null, "abc"))
        assertFalse("缺失一侧不得放行", ClaudeSessionLocator.sessionIdMatches("abc", null))
    }
}
