package io.github.luxiang0412.kelpie.conversation.model

import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import io.github.luxiang0412.kelpie.core.herdr.ChatMessage

/** 对话列表的一条可渲染条目（M2-C1：与传输层 ChatMessage 解耦，且不再因位置猜测而虚构关联）。 */
sealed interface ConversationItem {
    val index: Int

    data class User(override val index: Int, val text: String) : ConversationItem
    data class Assistant(override val index: Int, val text: String) : ConversationItem
    data class Thinking(override val index: Int, val text: String) : ConversationItem

    /**
     * toolCall / toolResult 条目。
     * [callId] 为配对依据；[pairedById] 只有在双方 `toolCallId` 相等时才为 true。
     */
    data class Tool(
        override val index: Int,
        val name: String,
        val argumentsSummary: String,
        val result: String?,
        val resultError: Boolean,
        val pairedById: Boolean,
        val callId: String? = null,
        /** AskUserQuestion 的问题（来自调用）与回答（来自结果）；其他工具为空 / null。 */
        val questions: List<AskQuestion> = emptyList(),
        val answers: Map<String, String>? = null,
        /** 一句话说明（Claude Bash 的 description）；有它时卡片显示它，命令放到展开 / 全屏里。 */
        val title: String? = null,
    ) : ConversationItem

    data class Unknown(override val index: Int, val text: String) : ConversationItem
}

/**
 * 参考实现（Reference Full Mapper）：每次都对全量消息做一次规范解析。
 * 增量实现必须与之逐条一致（见 `IncrementalMapperEquivalenceTest`）。
 */
object ConversationItemMapper {

    fun map(messages: List<ChatMessage>): List<ConversationItem> =
        ConversationResolve.resolve(messages).map(ConversationResolve::toItem)

    /** 结果文本里的错误信号（启发式，仅用于视觉提示，不改变真实状态）。 */
    fun looksLikeError(text: String): Boolean {
        val head = text.take(200).lowercase()
        return head.startsWith("error") || head.contains("exit code 1") ||
            head.contains("traceback (most recent call last)") || head.contains("failed:")
    }
}
