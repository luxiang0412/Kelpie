package io.github.luxiang0412.kelpie.home

import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowLabelsTest {
    private fun item(host: String, pane: String, title: String, cwd: String?) = AgentItem(
        key = AgentKey(host, pane), hostLabel = "luxiang-MS-7E07",
        pane = Pane(pane, null, "w", "t", "claude", AgentStatus.IDLE, title, cwd, 1),
        status = AgentStatus.IDLE, sinceMs = null, sinceIsLowerBound = false, finishedAtMs = null, lastSeen = false,
    )

    // 2026-11-08 真机：两台主机在线，这两行第二行都是 “luxiang-MS-7E07”，分不出来
    private val a = item("h", "p1", "ADR-041 重构交接", "/home/luxiang/project/work/xn-ontology")
    private val b = item("h", "p2", "ADR-041 重构交接会话…", "/home/luxiang/project/private/herdr-gui-research")
    private val other = item("h", "p3", "生成式UI概念讨论", "/home/luxiang/project/work/xn-ontology")

    @Test
    fun `prefix titles on the same host are similar`() {
        assertTrue(RowLabels.similar(a.title, b.title))
        assertFalse(RowLabels.similar(a.title, other.title))
        assertFalse(RowLabels.similar("ab", "abc")) // 太短的前缀不算
        assertEquals(setOf(a.key, b.key), RowLabels.ambiguous(listOf(a, b, other)))
    }

    @Test
    fun `similar titles on another host are not ambiguous`() {
        val elsewhere = item("h2", "p1", "ADR-041 重构交接", "/x")
        assertTrue(RowLabels.ambiguous(listOf(a, elsewhere)).isEmpty())
    }

    @Test
    fun `ambiguous rows add the working directory`() {
        val all = listOf(a, b, other)
        val amb = RowLabels.ambiguous(all)
        assertEquals("luxiang-MS-7E07 · ~/project/work/xn-ontology", RowLabels.subtitle(a, multiHost = true, amb, all))
        assertEquals("luxiang-MS-7E07", RowLabels.subtitle(other, multiHost = true, amb, all))
        // 单主机时第二行本来就是工作目录，不重复
        assertEquals("~/project/private/herdr-gui-research", RowLabels.subtitle(b, multiHost = false, amb, all))
    }

    @Test
    fun `same title in the same directory falls back to the pane id`() {
        val c = item("h", "p9", "ADR-041 重构交接", "/home/luxiang/project/work/xn-ontology")
        val all = listOf(a, c)
        val amb = RowLabels.ambiguous(all)
        assertEquals("luxiang-MS-7E07 · ~/project/work/xn-ontology · p9", RowLabels.subtitle(c, multiHost = true, amb, all))
    }
}
