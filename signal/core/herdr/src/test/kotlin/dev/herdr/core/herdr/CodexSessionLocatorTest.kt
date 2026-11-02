package dev.herdr.core.herdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexSessionLocatorTest {

    private fun head(cwd: String, originator: String, source: String) =
        """{"timestamp":"2026-11-02T14:48:00.048Z","type":"session_meta","payload":{"session_id":"s","cwd":"$cwd","originator":"$originator","cli_version":"0.166.0","source":"$source","base_instructions":{"text":"…"}}}""" + "\n{}"

    private val now = 1_800_000_000_000L
    private val day = 24 * 3600_000L

    private fun cand(path: String, ageMs: Long, cwd: String = "/home/u/repo", originator: String = "codex-tui", source: String = "vscode") =
        CodexSessionLocator.Candidate(path, now - ageMs, CodexSessionLocator.metaOf(head(cwd, originator, source))!!)

    @Test fun metaFromHead() {
        val m = CodexSessionLocator.metaOf(head("/home/u/my \\\"repo\\\"", "codex-tui", "cli"))!!
        assertEquals("/home/u/my \"repo\"", m.cwd)
        assertEquals("codex-tui", m.originator)
        assertNull(CodexSessionLocator.metaOf("""{"type":"response_item"}"""))
    }

    @Test fun picksNewestInteractiveForCwd() {
        val c = listOf(
            cand("old", 3 * 3600_000L),
            cand("new", 60_000L),
            cand("exec-review", 1_000L, originator = "codex_exec", source = "exec"),
            cand("other-dir", 500L, cwd = "/home/u/other"),
            cand("stale", 2 * day),
        )
        assertEquals("new", CodexSessionLocator.pick(c, "/home/u/repo/", now, day)?.path)
        assertNull(CodexSessionLocator.pick(c.filter { it.path != "new" && it.path != "old" }, "/home/u/repo", now, day))
    }

    @Test fun recentDayDirsCrossMonthBoundary() {
        val d = CodexSessionLocator.recentDayDirs(
            listOf("2025", "2026", "tmp"),
            { y -> if (y == "2026") listOf("10", "11") else listOf("12") },
            { _, m -> if (m == "11") listOf("01") else listOf("30", "31") },
        )
        assertEquals(listOf("2026/11/01", "2026/10/31"), d)
    }

    @Test fun sessionIdHelpers() {
        val id = "01a19e58-8746-7013-bf32-e6e0b24c6128"
        val ms = CodexSessionLocator.uuidV7Millis(id)!!
        assertEquals("2026-11-02T22:19:24.358Z", java.time.Instant.ofEpochMilli(ms).toString())
        assertEquals(listOf("2026/11/01", "2026/11/02", "2026/11/03"), CodexSessionLocator.dayDirsAround(ms))
        assertNull(CodexSessionLocator.uuidV7Millis("15367e80-d73d-45f0-9d85-f699bf7fbab8")) // v4
        assertEquals(true, CodexSessionLocator.matchesId("rollout-2026-11-03T06-19-24-$id.jsonl", id))
        assertEquals(false, CodexSessionLocator.matchesId("rollout-2026-11-03T06-19-24-$id.jsonl.bak", id))
    }
}
