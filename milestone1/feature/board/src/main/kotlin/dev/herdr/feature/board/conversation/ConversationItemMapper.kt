package dev.herdr.feature.board.conversation

import dev.herdr.core.herdr.ChatMessage

/** 对话列表的一条可渲染条目（M2-B 渲染模型，与传输层的 ChatMessage 解耦）。 */
sealed interface ConversationItem {
    val index: Int

    data class User(override val index: Int, val text: String) : ConversationItem
    data class Assistant(override val index: Int, val text: String) : ConversationItem
    data class Thinking(override val index: Int, val text: String) : ConversationItem
    /** toolCall 与其结果的配对结果。 */
    data class Tool(
        override val index: Int,
        val name: String,
        val argumentsSummary: String,
        val result: String?,
        val resultError: Boolean,
        val pairedById: Boolean,
    ) : ConversationItem
    data class Unknown(override val index: Int, val text: String) : ConversationItem
}

/**
 * 把扁平的 ChatMessage 列表转成渲染条目。
 *
 * 配对规则（B4 要求，不能只靠相邻位置）：
 *  1. 双方都有 toolCallId → 按 id 配对；
 *  2. id 对不上 → 退化为“最近一个尚未配对的 toolCall”，并标记 pairedById = false，
 *     UI 不得声称这是精确配对；
 *  3. 找不到任何可配对的 call → 结果独立渲染。
 */
object ConversationItemMapper {

    fun map(messages: List<ChatMessage>): List<ConversationItem> {
        val out = ArrayList<ConversationItem>(messages.size)
        val pending = LinkedHashMap<Int, ChatMessage>()
        val indexById = HashMap<String, Int>()

        messages.forEachIndexed { i, m ->
            when (m.kind) {
                ChatMessage.Kind.USER -> out += ConversationItem.User(i, m.text)
                ChatMessage.Kind.ASSISTANT -> out += ConversationItem.Assistant(i, m.text)
                ChatMessage.Kind.THINKING -> out += ConversationItem.Thinking(i, m.text)
                ChatMessage.Kind.SYSTEM -> Unit
                ChatMessage.Kind.TOOL_CALL -> {
                    out += ConversationItem.Tool(
                        index = i,
                        name = m.toolName ?: "tool",
                        argumentsSummary = m.text,
                        result = null,
                        resultError = false,
                        pairedById = m.toolCallId != null,
                    )
                    pending[i] = m
                    m.toolCallId?.let { indexById[it] = i }
                }
                ChatMessage.Kind.TOOL_RESULT -> {
                    val byId = m.toolCallId?.let { indexById[it] }?.takeIf { it in pending }
                    val targetIndex = byId ?: pending.keys.lastOrNull()
                    if (targetIndex == null) {
                        out += ConversationItem.Tool(
                            index = i,
                            name = m.toolName ?: "tool",
                            argumentsSummary = "",
                            result = m.text,
                            resultError = looksLikeError(m.text),
                            pairedById = false,
                        )
                    } else {
                        val call = pending.remove(targetIndex)!!
                        val pos = out.indexOfFirst { it is ConversationItem.Tool && it.index == targetIndex }
                        if (pos >= 0) {
                            val item = out[pos] as ConversationItem.Tool
                            out[pos] = item.copy(
                                result = m.text,
                                resultError = looksLikeError(m.text),
                                pairedById = byId != null,
                            )
                        } else {
                            out += ConversationItem.Tool(
                                index = i,
                                name = call.toolName ?: "tool",
                                argumentsSummary = call.text,
                                result = m.text,
                                resultError = looksLikeError(m.text),
                                pairedById = byId != null,
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    /** 结果文本里的错误信号（启发式，仅用于视觉提示，不改变真实状态）。 */
    fun looksLikeError(text: String): Boolean {
        val head = text.take(200).lowercase()
        return head.startsWith("error") || head.contains("exit code 1") ||
            head.contains("traceback (most recent call last)") || head.contains("failed:")
    }
}
