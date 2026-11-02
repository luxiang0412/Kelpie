package io.github.luxiang0412.kelpie.core.herdr

/**
 * Codex 会话文件定位 —— **纯逻辑**，IO 在 `CodexSessionResolver`。
 *
 * herdr 0.9.5 的 Codex 集成在 SessionStart 时上报的是会话 id（`agent_session.kind = "id"`），不是路径。
 *
 * 1. 有 id：文件名就是 `rollout-<本地时间>-<id>.jsonl`，按 id 精确找。id 是 UUIDv7，前 48 位是创建时间（毫秒），
 *    所以除了最近两天，还会去创建日期前后各一天的目录找（跑了几天的会话）。
 * 2. 没有 id（hook 被信任之前就启动的 Codex，或 hook 失败）：按 cwd 猜，规则如下，任何一条不满足就放弃：
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

    /** UUIDv7 → 创建时间（毫秒）；不是 v7 返回 null。 */
    fun uuidV7Millis(id: String): Long? {
        val hex = id.replace("-", "")
        if (hex.length != 32 || hex[12] != '7' || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return hex.substring(0, 12).toLongOrNull(16)
    }

    /** 创建日期前后各一天（日期目录用的是开发机本地时区，这里不知道时区，所以放宽一天）。 */
    fun dayDirsAround(ms: Long): List<String> {
        val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return (-1..1).map { d.plusDays(it.toLong()) }.map { "%04d/%02d/%02d".format(it.year, it.monthValue, it.dayOfMonth) }
    }

    fun matchesId(fileName: String, id: String) = fileName.startsWith("rollout-") && fileName.endsWith("-$id.jsonl")

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
