package io.github.luxiang0412.kelpie.core.herdr

/**
 * Claude 会话文件定位（方案 C）—— **纯逻辑**，不做 IO，便于单测。
 *
 * 背景：herdr 对 Claude 上报的 `agent_session` 是 `kind: "id"`（只有会话 id，没有路径），
 * 所以必须按 pane 的 `cwd` 推断 `~/.claude/projects/<slug>/` 目录，再挑出对应的 jsonl。
 *
 * 实测规则（2026-11-01 远端核对）：
 *   cwd  `/home/luxiang/project/work/xn-ontology`
 *   dir  `-home-luxiang-project-work-xn-ontology`   ← 绝对路径把 `/` 换成 `-`
 *
 * 三重校验（任何一条不满足就**放弃**，绝不错挂会话）：
 *  1. 目录名必须与 `slugFor(cwd)` 完全相等（不做模糊匹配）
 *  2. 候选文件的 mtime 必须落在 [now - windowMs, now] 内
 *  3. 候选文件头部的 `sessionId` 必须与 herdr 给的 id 完全相等
 */
object ClaudeSessionLocator {

    /** `/a/b/c` → `-a-b-c`（Claude Code 的 projects 目录命名规则）。 */
    fun slugFor(cwd: String): String =
        cwd.trim().trimEnd('/').replace('/', '-').ifEmpty { "-" }

    data class Candidate(val path: String, val mtimeMs: Long)

    /**
     * 在候选里挑最新的一个；超出时间窗则返回 null。
     * @param nowMs 当前时间；测试可注入
     */
    fun pickLatest(candidates: List<Candidate>, nowMs: Long, windowMs: Long): Candidate? {
        val fresh = candidates.filter { it.mtimeMs in (nowMs - windowMs)..nowMs }
        return fresh.maxByOrNull { it.mtimeMs }
    }

    /**
     * 从文件头部若干行里找 `sessionId`。
     * Claude 的事件行普遍带 `"sessionId":"<uuid>"`，第一行（`type=mode`）通常就有。
     */
    fun sessionIdOf(headLines: List<String>): String? {
        val re = Regex("\"sessionId\"\\s*:\\s*\"([^\"]+)\"")
        headLines.forEach { line ->
            re.find(line)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    /**
     * 会话 id 对账：**必须双向非空且完全相等**才通过。
     * herdr 的 value 可能是 `sess-…` 前缀形式，允许去掉该前缀后比较。
     */
    fun sessionIdMatches(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrBlank() || actual.isNullOrBlank()) return false
        fun norm(s: String) = s.trim().removePrefix("sess-").removePrefix("session-")
        return norm(expected) == norm(actual)
    }
}
