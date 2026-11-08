package io.github.luxiang0412.kelpie.conversation.model

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import io.github.luxiang0412.kelpie.core.herdr.ClaudeTranscriptParser

/**
 * 增量映射器（M2-C1 / I-79）。
 *
 * 目标：普通追加与局部更新的**映射成本取决于变化量**，不随历史消息数增长。
 *
 * 三种模式（每次 `update` 返回本次使用的模式，供测试/指标断言）：
 *  - [Mode.APPEND]：新消息是旧序列的严格扩展 → 只为新增消息建条目（O(k)）。
 *    若新增的是能配对的 ToolResult，则**只原位替换**对应 Tool 条目（O(1) 查找 + O(n) 浅拷贝，无重新解析）。
 *  - [Mode.PATCH]：长度不变、仅少量非工具消息内容变化 → 只重映射变化条目（O(changed)）。
 *  - [Mode.FULL]：分支/会话切换、工具结构变化、乱序、id 冲突 → 受控全量重建（允许）。
 *
 * 线程模型：**非线程安全**，必须在单一调度器内调用（UI 侧 ViewModel）；
 * 会话切换由 [sessionKey] 或 [reset] 清理派生状态，避免旧会话覆盖新会话。
 */
class IncrementalConversationMapper {

    enum class Mode { NONE, APPEND, PATCH, FULL }

    data class Stats(val mode: Mode, val mapped: Int, val reused: Int, val total: Int) {
        val mappingCostRatio: Double get() = if (total == 0) 0.0 else mapped.toDouble() / total
    }

    private var sessionKey: Any? = null
    private var prevMessages: List<ChatMessage> = emptyList()
    private var prevItems: List<ConversationItem> = emptyList()
    /** callId → 条目下标（仅带 callId 的 Tool 条目），增量维护。 */
    private val toolItemByCallId = HashMap<String, Int>()
    /** 已出现过的消息 id（重复事件丢弃，与参考实现的去重语义一致）。 */
    private val seenIds = HashSet<String>()

    var stats: Stats = Stats(Mode.NONE, 0, 0, 0)
        private set

    fun reset() {
        sessionKey = null
        prevMessages = emptyList()
        prevItems = emptyList()
        toolItemByCallId.clear()
        seenIds.clear()
        stats = Stats(Mode.NONE, 0, 0, 0)
    }

    /** @param sessionKey Host + agentSession + 当前分支；变化即视为会话切换。 */
    fun update(messages: List<ChatMessage>, sessionKey: Any? = null): List<ConversationItem> {
        val sessionChanged = sessionKey != this.sessionKey
        if (sessionChanged) {
            this.sessionKey = sessionKey
            prevMessages = emptyList()
            prevItems = emptyList()
            toolItemByCallId.clear()
            seenIds.clear()
        }

        if (messages.isEmpty()) {
            prevMessages = messages
            prevItems = emptyList()
            toolItemByCallId.clear()
            seenIds.clear()
            stats = Stats(Mode.NONE, 0, 0, 0)
            return prevItems
        }

        val prefix = commonPrefix(prevMessages, messages)
        return when {
            sessionChanged -> fullRebuild(messages)
            prefix == messages.size && messages.size == prevMessages.size -> {
                stats = Stats(Mode.NONE, 0, messages.size, messages.size)
                prevItems
            }
            prefix == prevMessages.size && messages.size > prevMessages.size -> appendTail(messages, prefix)
            prefix == messages.size && messages.size == prevMessages.size -> applyPatch(messages)
            else -> fullRebuild(messages)
        }
    }

    private fun appendTail(messages: List<ChatMessage>, prefix: Int): List<ConversationItem> {
        // 新增 TOOL_CALL 若其 id 已存在“更早的独立结果”，参考实现会把该结果移动到调用位置
        // → 结构变化，交给全量重建（罕见：结果先于调用到达）。
        for (j in prefix until messages.size) {
            val m = messages[j]
            val id = m.toolCallId
            if (m.kind == ChatMessage.Kind.TOOL_CALL && id != null && isEarlyResult(id)) {
                return fullRebuild(messages)
            }
        }

        var items: List<ConversationItem> = prevItems
        var mapped = 0
        var patched = 0

        for (j in prefix until messages.size) {
            val m = messages[j]
            // 重复事件（同消息 id 已出现）：参考实现保留首次，这里直接丢弃
            val messageId = m.id
            if (messageId != null && !seenIds.add(messageId)) continue
            when (m.kind) {
                ChatMessage.Kind.SYSTEM -> Unit

                ChatMessage.Kind.TOOL_RESULT -> {
                    val id = m.toolCallId
                    val at = id?.let { toolItemByCallId[it] }
                    if (id != null && at != null && at < items.size) {
                        val old = items[at] as ConversationItem.Tool
                        items = items.toMutableList().also {
                            it[at] = old.copy(
                                result = m.text,
                                resultError = ConversationItemMapper.looksLikeError(m.text),
                                pairedById = true,
                                answers = m.answers,
                            )
                        }
                        patched++
                    } else {
                        items = items + ConversationItem.Tool(
                            index = j,
                            name = m.toolName ?: "tool",
                            argumentsSummary = "",
                            result = m.text,
                            resultError = ConversationItemMapper.looksLikeError(m.text),
                            pairedById = false,
                            callId = id,
                            answers = m.answers,
                        )
                        mapped++
                    }
                }

                else -> {
                    val item = single(m, j)
                    items = items + item
                    if (item is ConversationItem.Tool && item.callId != null) {
                        toolItemByCallId[item.callId] = items.lastIndex
                    }
                    mapped++
                }
            }
        }

        prevMessages = messages
        prevItems = items
        stats = Stats(Mode.APPEND, mapped, items.size - mapped, messages.size)
        return items
    }

    private fun applyPatch(messages: List<ChatMessage>): List<ConversationItem> {
        var changed = 0
        var changedTool = false
        for (i in messages.indices) {
            if (messages[i] == prevMessages[i]) continue
            changed++
            if (isTool(messages[i]) || isTool(prevMessages[i])) {
                changedTool = true
                break
            }
        }
        if (changed == 0) {
            stats = Stats(Mode.NONE, 0, messages.size, messages.size)
            return prevItems
        }
        // 工具变化会影响配对；工具条目数 != 消息数时下标不可直接对应 → 全量重建。
        if (changedTool || prevItems.size != prevMessages.size) return fullRebuild(messages)

        val items = ArrayList<ConversationItem>(prevItems.size)
        prevItems.forEach { item ->
            items += if (messages[item.index] != prevMessages[item.index]) single(messages[item.index], item.index) else item
        }
        prevMessages = messages
        prevItems = items
        stats = Stats(Mode.PATCH, changed, items.size - changed, messages.size)
        return items
    }

    private fun fullRebuild(messages: List<ChatMessage>): List<ConversationItem> {
        val items = ConversationItemMapper.map(messages)
        prevMessages = messages
        prevItems = items
        toolItemByCallId.clear()
        seenIds.clear()
        messages.forEach { m -> m.id?.let(seenIds::add) }
        items.forEachIndexed { i, it -> if (it is ConversationItem.Tool && it.callId != null) toolItemByCallId[it.callId] = i }
        stats = Stats(Mode.FULL, messages.size, 0, messages.size)
        return items
    }

    private fun isEarlyResult(callId: String): Boolean = prevItems.any {
        it is ConversationItem.Tool && it.callId == callId && it.result != null && it.argumentsSummary.isEmpty()
    }

    private fun isTool(m: ChatMessage) =
        m.kind == ChatMessage.Kind.TOOL_CALL || m.kind == ChatMessage.Kind.TOOL_RESULT

    private fun single(m: ChatMessage, index: Int): ConversationItem = when (m.kind) {
        ChatMessage.Kind.USER -> ConversationItem.User(index, ClaudeTranscriptParser.unwrapPasted(m.text))
        ChatMessage.Kind.ASSISTANT -> ConversationItem.Assistant(index, m.text)
        ChatMessage.Kind.THINKING -> ConversationItem.Thinking(index, m.text)
        ChatMessage.Kind.TOOL_CALL -> ConversationItem.Tool(
            index = index,
            name = m.toolName ?: "tool",
            argumentsSummary = m.text,
            result = null,
            resultError = false,
            pairedById = false,
            callId = m.toolCallId,
            questions = m.questions,
            title = m.title,
        )
        else -> ConversationItem.Unknown(index, m.text)
    }

    private fun commonPrefix(a: List<ChatMessage>, b: List<ChatMessage>): Int {
        val n = minOf(a.size, b.size)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }
}
