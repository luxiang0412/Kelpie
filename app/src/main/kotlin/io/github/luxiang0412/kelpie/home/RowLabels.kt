package io.github.luxiang0412.kelpie.home

import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey

/**
 * 首页每行第二行写什么：多主机时是主机名，单主机时是工作目录。
 * 同一台主机上标题相同或相近（一个是另一个的前缀，比如 “ADR-041 重构交接” 和 “ADR-041 重构交接会话…”）时，
 * 只看主机名分不出来：带上工作目录；工作目录也一样才再带 pane 编号。
 */
object RowLabels {

    private fun norm(title: String) = title.trim().removeSuffix("…").removeSuffix("...").trim().lowercase()

    fun similar(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 4 && long.startsWith(short)
    }

    /** 需要额外区分的 agent：同主机上有标题相近的另一个。 */
    fun ambiguous(items: List<AgentItem>): Set<AgentKey> {
        val out = HashSet<AgentKey>()
        items.groupBy { it.key.hostId }.values.forEach { same ->
            for (i in same.indices) for (j in i + 1 until same.size) {
                if (similar(same[i].title, same[j].title)) { out += same[i].key; out += same[j].key }
            }
        }
        return out
    }

    fun shortCwd(cwd: String?): String? = cwd?.replace(Regex("^/home/[^/]+"), "~")

    fun subtitle(item: AgentItem, multiHost: Boolean, ambiguous: Set<AgentKey>, all: List<AgentItem>): String {
        val cwd = shortCwd(item.pane.cwd)
        val base = if (multiHost) item.hostLabel else cwd ?: item.hostLabel
        if (item.key !in ambiguous) return base
        val parts = mutableListOf(base)
        if (multiHost && cwd != null) parts += cwd
        // 工作目录也分不开（同目录开了两个）才用 pane 编号
        val sameCwd = all.any { it.key != item.key && it.key.hostId == item.key.hostId && similar(it.title, item.title) && it.pane.cwd == item.pane.cwd }
        if (cwd == null || sameCwd) parts += item.pane.paneId
        return parts.joinToString(" · ")
    }
}
