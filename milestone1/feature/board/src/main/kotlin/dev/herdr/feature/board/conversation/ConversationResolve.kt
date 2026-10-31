package dev.herdr.feature.board.conversation

import dev.herdr.core.herdr.ChatMessage

/**
 * 关联解析（唯一真相）：把扁平消息序列解析成「渲染条目」的规范计划。
 *
 * 配对规则（M2-C1，**不做位置猜测**）：
 *  1. 只有 `toolCallId` 相等才算精确配对；
 *  2. 结果先于调用到达 → 仍然按 id 配对，条目落在 **调用的位置**；
 *  3. 没有任何 id 的结果 → 独立条目（`pairedById = false`），绝不并入相邻调用；
 *  4. 调用没有 id → 独立条目，不会吸收后续任意结果；
 *  5. 重复事件（同一条消息 id 出现多次）→ 保留首次，其余丢弃；
 *  6. 同一 id 有多个结果 → 取最后一个（结果更新语义）。
 */
object ConversationResolve {

    sealed interface Entry {
        val index: Int
        data class Single(override val index: Int, val message: ChatMessage) : Entry
        data class Tool(
            override val index: Int,
            val call: ChatMessage?,
            val result: ChatMessage?,
            val resultIndex: Int?,
        ) : Entry
    }

    fun resolve(messages: List<ChatMessage>): List<Entry> {
        // 1. 去重（同 id 只保留首次出现）
        val seen = HashSet<String>(messages.size)
        val unique = ArrayList<ChatMessage>(messages.size)
        val originIndex = ArrayList<Int>(messages.size)
        messages.forEachIndexed { i, m ->
            val key = m.id
            if (key != null && !seen.add(key)) return@forEachIndexed
            unique += m
            originIndex += i
        }

        // 2. id → 调用位置 / id → 结果位置（结果取最后一个 = 更新）
        val callIndexById = HashMap<String, Int>()
        val resultIndexById = HashMap<String, Int>()
        unique.forEachIndexed { i, m ->
            val id = m.toolCallId
            if (id == null) return@forEachIndexed
            when (m.kind) {
                ChatMessage.Kind.TOOL_CALL -> if (!callIndexById.containsKey(id)) callIndexById[id] = i
                ChatMessage.Kind.TOOL_RESULT -> resultIndexById[id] = i
                else -> Unit
            }
        }

        val consumedResults = HashSet<Int>()
        val out = ArrayList<Entry>(unique.size)
        unique.forEachIndexed { i, m ->
            when (m.kind) {
                ChatMessage.Kind.TOOL_CALL -> {
                    val id = m.toolCallId
                    val resultIdx = id?.let { resultIndexById[it] }?.takeIf { callIndexById[id] == i }
                    if (resultIdx != null) consumedResults += resultIdx
                    out += Entry.Tool(originIndex[i], m, resultIdx?.let { unique[it] }, resultIdx)
                }

                ChatMessage.Kind.TOOL_RESULT -> {
                    if (i in consumedResults) return@forEachIndexed
                    val id = m.toolCallId
                    // 结果先到：等调用出现时在调用位置合并，这里不渲染
                    if (id != null && callIndexById.containsKey(id)) return@forEachIndexed
                    out += Entry.Tool(originIndex[i], null, m, originIndex[i])
                }

                ChatMessage.Kind.SYSTEM -> Unit
                else -> out += Entry.Single(originIndex[i], m)
            }
        }
        return out
    }

    fun toItem(entry: Entry): ConversationItem = when (entry) {
        is Entry.Single -> when (entry.message.kind) {
            ChatMessage.Kind.USER -> ConversationItem.User(entry.index, entry.message.text)
            ChatMessage.Kind.ASSISTANT -> ConversationItem.Assistant(entry.index, entry.message.text)
            ChatMessage.Kind.THINKING -> ConversationItem.Thinking(entry.index, entry.message.text)
            else -> ConversationItem.Unknown(entry.index, entry.message.text)
        }

        is Entry.Tool -> ConversationItem.Tool(
            index = entry.index,
            name = entry.call?.toolName ?: entry.result?.toolName ?: "tool",
            argumentsSummary = entry.call?.text ?: "",
            result = entry.result?.text,
            resultError = entry.result?.text?.let(ConversationItemMapper::looksLikeError) ?: false,
            // 只有双方都带 toolCallId 且相等，才是精确配对
            pairedById = entry.call?.toolCallId != null && entry.call?.toolCallId == entry.result?.toolCallId,
            callId = entry.call?.toolCallId ?: entry.result?.toolCallId,
        )
    }
}
