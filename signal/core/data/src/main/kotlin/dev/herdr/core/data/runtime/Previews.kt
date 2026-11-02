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
        /** 取最后一条有意义的助手输出；最后一条是未完成的工具调用时带上工具名。 */
        fun fromTranscript(msgs: List<ChatMessage>): BlockedPreview? {
            val lastTool = msgs.lastOrNull { it.kind == ChatMessage.Kind.TOOL_CALL }
            val lastResultIdx = msgs.indexOfLast { it.kind == ChatMessage.Kind.TOOL_RESULT }
            val toolPending = lastTool != null && msgs.indexOf(lastTool) > lastResultIdx
            val assistant = msgs.lastOrNull { it.kind == ChatMessage.Kind.ASSISTANT && it.text.isNotBlank() }
                ?: return if (toolPending) BlockedPreview(lastTool!!.text.take(MAX), BlockedPreview.Source.Transcript, lastTool.toolName) else null
            return BlockedPreview(assistant.text.trim().take(MAX), BlockedPreview.Source.Transcript, if (toolPending) lastTool!!.toolName else null)
        }

        /** 终端尾部：去掉空行与框线，保留最后 8 行。 */
        fun fromScreen(raw: String): BlockedPreview? {
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
