package dev.herdr.core.herdr

/**
 * 按会话文件路径选择解析器。
 *
 * 依据（2026-11-01 实测）：
 *  - pi    → `~/.pi/agent/sessions/<slug>/<file>.jsonl`
 *  - Claude → `~/.claude/projects/<slug>/<uuid>.jsonl`
 *  - Codex  → `~/.codex/sessions/YYYY/MM/DD/rollout-….jsonl`（2026-11-03 实测）
 *
 * 未知来源按 pi 解析（历史上唯一的实现），解析失败也只是丢消息，不会崩溃。
 */
object TranscriptParser {

    enum class Kind { PI, CLAUDE, CODEX }

    fun kindOf(path: String?): Kind = when {
        path == null -> Kind.PI
        path.contains("/.claude/projects/") -> Kind.CLAUDE
        path.contains("/.codex/sessions/") || path.substringAfterLast('/').startsWith("rollout-") -> Kind.CODEX
        else -> Kind.PI
    }

    fun parse(path: String?, lines: List<String>): List<ChatMessage> = when (kindOf(path)) {
        Kind.CLAUDE -> ClaudeTranscriptParser.parse(lines)
        Kind.PI -> PiTranscriptParser.parse(lines)
        Kind.CODEX -> CodexTranscriptParser.parse(lines)
    }
}
