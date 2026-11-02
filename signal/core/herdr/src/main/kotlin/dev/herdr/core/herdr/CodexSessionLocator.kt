package dev.herdr.core.herdr

/**
 * Codex 会话文件定位 —— **纯逻辑**，IO 在 `CodexSessionResolver`。
 *
 * herdr 的 Codex 集成会在 SessionStart 时上报 `transcript_path`；但这个 hook 被信任之前就已启动的 Codex
 * （以及 hook 失败时）pane 上没有路径，这时才用这里的规则去 `~/.codex/sessions` 里找。
 *
 * 规则（任何一条不满足就放弃，宁可只看终端也不挂错会话）：
 *  1. 文件头 `session_meta.cwd` 与 pane 的 cwd 完全相等
 *  2. 是交互式会话：`originator` 不是 `codex_exec`、`source` 不是 `exec`
 *     （同一目录里常有别的 agent 调 `codex exec` 做评审，那些不是这个 pane）
 *  3. mtime 在时间窗内
 *  4. 同一主机同一 cwd 下只有一个 Codex pane（否则分不清是哪一个）
 * 满足的有多个时取最新（重启过 Codex 的情况：旧会话已经停止写入）。
 */
object CodexSessionLocator {

    data class Meta(val cwd: String?, val originator: String?, val source: String?, val sessionId: String?)

    data class Candidate(val path: String, val mtimeMs: Long, val meta: Meta)

    private fun field(head: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").find(head) ?: return null
        return runCatching { (kotlinx.serialization.json.Json.parseToJsonElement(m.groupValues[1]) as kotlinx.serialization.json.JsonPrimitive).content }.getOrNull()
    }

    /** 从文件开头（第一行是 session_meta，可能很长，只需要前几 KB）取字段。 */
    fun metaOf(head: String): Meta? {
        val first = head.substringBefore('\n')
        if (!first.contains("\"session_meta\"")) return null
        return Meta(field(first, "cwd"), field(first, "originator"), field(first, "source"), field(first, "session_id") ?: field(first, "id"))
    }

    fun isInteractive(m: Meta) = m.originator != "codex_exec" && m.source != "exec"

    fun pick(candidates: List<Candidate>, cwd: String, nowMs: Long, windowMs: Long): Candidate? {
        val want = cwd.trimEnd('/')
        return candidates
            .filter { it.mtimeMs in (nowMs - windowMs)..(nowMs + 60_000) }
            .filter { it.meta.cwd?.trimEnd('/') == want && isInteractive(it.meta) }
            .maxByOrNull { it.mtimeMs }
    }

    /** `…/sessions` 下的日期目录，取最近 [days] 个：`2026/11/03`、`2026/11/02` …（名字排序即时间排序）。 */
    fun recentDayDirs(years: List<String>, monthsOf: (String) -> List<String>, daysOf: (String, String) -> List<String>, days: Int = 2): List<String> {
        val out = ArrayList<String>()
        for (y in years.filter { it.all(Char::isDigit) }.sortedDescending()) {
            for (m in monthsOf(y).filter { it.all(Char::isDigit) }.sortedDescending()) {
                for (d in daysOf(y, m).filter { it.all(Char::isDigit) }.sortedDescending()) {
                    out += "$y/$m/$d"
                    if (out.size >= days) return out
                }
            }
        }
        return out
    }
}
