package dev.herdr.core.herdr

/**
 * 按会话文件路径选择解析器。
 *
 * 依据（2026-11-01 实测）：
 *  - pi    → `~/.pi/agent/sessions/<slug>/<file>.jsonl`
 *  - Claude → `~/.claude/projects/<slug>/<uuid>.jsonl`
 *
 * 未知来源按 pi 解析（历史上唯一的实现），解析失败也只是丢消息，不会崩溃。
 */
object TranscriptParser {

    enum class Kind { PI, CLAUDE }

    fun kindOf(path: String?): Kind =
        if (path != null && path.contains("/.claude/projects/")) Kind.CLAUDE else Kind.PI

    fun parse(path: String?, lines: List<String>): List<ChatMessage> = when (kindOf(path)) {
        Kind.CLAUDE -> ClaudeTranscriptParser.parse(lines)
        Kind.PI -> PiTranscriptParser.parse(lines)
    }
}
