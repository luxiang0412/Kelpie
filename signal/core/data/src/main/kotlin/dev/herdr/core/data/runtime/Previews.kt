package dev.herdr.core.data.runtime

import dev.herdr.core.data.ClaudeSessionResolver
import dev.herdr.core.data.attention.AgentItem
import dev.herdr.core.data.attention.AgentKey
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.TranscriptLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 阻塞卡片里那段“它在问什么”（规范 5.3 AttentionCard 的问题正文）。 */
data class BlockedPreview(
    val text: String,
    val source: Source,
    /** 最后一条是还没出结果的工具调用时，工具名（“想运行 bash”）。 */
    val pendingTool: String? = null,
    /** 待批准工具调用的参数摘要（命令 / 文件路径），没有时为 null。 */
    val pendingArgs: String? = null,
) {
    enum class Source { Transcript, Screen }
}

/**
 * 只为 **blocked** 的 pane 取预览，并按“阻塞轮次”缓存：同一次阻塞只读一次远端。
 *
 * - Pi：herdr 给了会话文件路径 → 读文件尾，取最后一条助手消息；
 * - Claude：herdr 只给 session id → 按 cwd 定位会话文件（只读）后同上；
 * - 其他（Codex 等）：读终端最近几行。屏幕内容**只用来展示**，绝不据此一键批准。
 */
class Previews(
    private val fleet: Fleet,
    private val scope: CoroutineScope,
) {
    private val loader = TranscriptLoader()
    private val resolver = ClaudeSessionResolver()
    private val _map = MutableStateFlow<Map<AgentKey, BlockedPreview>>(emptyMap())
    val map: StateFlow<Map<AgentKey, BlockedPreview>> = _map.asStateFlow()

    /** key → 已经为哪一轮阻塞取过（sinceMs）。 */
    private val fetchedFor = HashMap<AgentKey, Long>()
    private val jobs = HashMap<AgentKey, Job>()

    /** 首页每次变化时调用。只处理在线主机上的 blocked pane。 */
    @Synchronized
    fun sync(blocked: List<AgentItem>) {
        val live = blocked.filter { !it.lastSeen }
        val keys = live.map { it.key }.toSet()
        // 不再阻塞的：丢掉缓存，下次阻塞重新取
        (fetchedFor.keys - keys).forEach { k -> fetchedFor.remove(k); jobs.remove(k)?.cancel() }
        _map.update { m -> m.filterKeys { it in keys } }
        live.forEach { item ->
            val round = item.sinceMs ?: 0
            if (fetchedFor[item.key] == round) return@forEach
            fetchedFor[item.key] = round
            jobs[item.key]?.cancel()
            jobs[item.key] = scope.launch {
                val p = runCatching { load(item) }.getOrNull() ?: return@launch
                _map.update { it + (item.key to p) }
            }
        }
    }

    private suspend fun load(item: AgentItem): BlockedPreview? {
        val conn = fleet.connection(item.key.hostId) ?: return null
        val pane = item.pane
        val path = pane.agentSessionPath?.takeIf { it.isNotBlank() }
            ?: if (pane.agentSessionId != null || pane.agent?.startsWith("claude") == true) {
                (resolver.resolve(conn, pane.cwd, pane.agentSessionId) as? ClaudeSessionResolver.Result.Found)?.path
            } else null
        if (path != null) {
            val msgs = loader.load(conn, path, maxBytes = 96 * 1024, maxMessages = 30)
            fromTranscript(msgs)?.let { return it }
        }
        val screen = fleet.runtime(item.key.hostId)?.api()?.readPane(pane.paneId, "recent", 40) ?: return null
        return fromScreen(screen)
    }

    companion object {
        /**
         * 只看**这一轮**（最后一条用户消息之后）：取最后一条助手输出；最后是还没出结果的工具调用时，
         * 带上工具名和参数摘要。Claude 弹权限框时常常这一轮没有任何正文，以前会拿上一轮的回复来充数。
         */
        fun fromTranscript(msgs: List<ChatMessage>): BlockedPreview? {
            val turn = msgs.drop(msgs.indexOfLast { it.kind == ChatMessage.Kind.USER } + 1)
            val lastTool = turn.lastOrNull { it.kind == ChatMessage.Kind.TOOL_CALL }
            val lastResultIdx = turn.indexOfLast { it.kind == ChatMessage.Kind.TOOL_RESULT }
            val toolPending = lastTool != null && turn.lastIndexOf(lastTool) > lastResultIdx
            val assistant = turn.lastOrNull { it.kind == ChatMessage.Kind.ASSISTANT && it.text.isNotBlank() }
            val args = if (toolPending) argsSummary(lastTool!!.text) else null
            return when {
                assistant != null -> BlockedPreview(assistant.text.trim().take(MAX), BlockedPreview.Source.Transcript, lastTool?.toolName?.takeIf { toolPending }, args)
                toolPending -> BlockedPreview("", BlockedPreview.Source.Transcript, lastTool!!.toolName ?: "tool", args)
                else -> null
            }
        }

        /** 工具参数 JSON → 一行能看懂的摘要：优先 command / file_path / path / url / pattern。 */
        fun argsSummary(json: String): String? {
            val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject }.getOrNull()
                ?: return json.trim().takeIf { it.isNotEmpty() }?.take(MAX)
            for (k in listOf("command", "file_path", "path", "url", "pattern", "query")) {
                val v = runCatching { (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull()
                if (!v.isNullOrBlank()) return v.take(MAX)
            }
            return json.take(MAX)
        }

        /**
         * Claude Code 的终端确认框：
         * ```
         * ────────
         *  Bash command            ← 标题（工具）
         *  Print current dir       ← 描述
         * ╌╌╌╌╌╌╌╌
         *  pwd                     ← 命令
         * ╌╌╌╌╌╌╌╌
         *  Do you want to proceed?
         *  ❯ 1. Yes
         * ```
         * 认出来就只保留标题和框里的内容，不要“1. Yes / 2. No / Esc to cancel”这些。
         */
        fun claudePrompt(raw: String): BlockedPreview? {
            val lines = raw.lines().map { it.trimEnd() }
            val ask = lines.indexOfLast { it.contains("Do you want to proceed?") || it.contains("Do you want to make this edit") || it.contains("Do you want to create") }
            if (ask < 0) return null
            val top = (ask - 1 downTo 0).firstOrNull { i -> lines[i].trim().let { l -> l.length >= 10 && l.all { it == '─' } } } ?: return null
            val body = lines.subList(top + 1, ask)
                .map { it.trim().removePrefix("│").trim() }
                .filter { l -> l.isNotEmpty() && !l.all { it in "╌─┄ " } }
            if (body.isEmpty()) return null
            val header = body.first()
            val rest = body.drop(1)
            return BlockedPreview(rest.joinToString("\n").take(MAX), BlockedPreview.Source.Screen, pendingTool = header.take(60))
        }

        /** 终端尾部：先认 Claude 的确认框；否则去掉空行与框线，保留最后 8 行。 */
        fun fromScreen(raw: String): BlockedPreview? {
            claudePrompt(raw)?.let { return it }
            val lines = raw.lines().map { it.trimEnd() }
                .filter { it.isNotBlank() && !it.all { c -> c in BOX_CHARS || c == ' ' } }
                .takeLast(8)
            if (lines.isEmpty()) return null
            return BlockedPreview(lines.joinToString("\n").take(MAX), BlockedPreview.Source.Screen)
        }

        private const val MAX = 600
        private const val BOX_CHARS = "─│┌┐└┘├┤┬┴┼╭╮╯╰═║╔╗╚╝━┃>"
    }
}
