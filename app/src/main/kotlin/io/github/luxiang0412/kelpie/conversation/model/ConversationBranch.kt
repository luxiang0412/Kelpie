package io.github.luxiang0412.kelpie.conversation.model

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage

/**
 * Pi 的会话是**树**（每条事件带 `id` / `parentId`）。UI 只应展示「当前有效分支」：
 * 从最后一条事件沿 `parentId` 回溯到根，再反转。
 *
 * 约束：
 *  - 只在消息带 `id` 时启用；全部没有 id 时按线性顺序返回（旧格式/夹具）；
 *  - parentId 指向的不是消息（Pi 的 compaction / model_change / thinking_level_change / context_edit，
 *    解析器不输出它们）→ 接着回溯**列表里的前一条**。2026-11-20 实测 323 条这类记录，父节点全都是
 *    紧挨着的上一行，所以这样接是准的。以前在这里停下，压缩或切模型之前的历史整段消失；
 *  - parentId 为 null（根）→ 停止；环 → 停止并返回已收集部分，绝不抛异常；
 *  - 分支切换 = 用新分支的最后一条事件重新调用本函数（受控全量重建）。
 */
object ConversationBranch {

    fun effective(messages: List<ChatMessage>): List<ChatMessage> {
        val indexed = messages.filter { it.id != null }
        if (indexed.isEmpty()) return messages
        // 只有「最后一条带 parentId」时才说明这是真正的树（Pi）。Claude 的 JSONL 里夹着
        // api-request-blob / attachment 等非消息事件，parentUuid 链会断在消息序列中途；
        // 此时按树回溯会把断点之前的消息（例如工具调用卡）整段丢掉 —— 如实退化为线性。
        if (indexed.last().parentId == null) return messages

        // 同一事件会拆成多条消息（一条 assistant 里有思考 + 文字 + 工具调用），它们 id 相同。
        // 回溯按事件走，最后把链上事件的**所有**消息按原顺序放回去
        // （以前按 id 去重，每个事件只剩最后一条，工具调用前的文字和思考都丢了）。
        val position = HashMap<String, Int>(indexed.size)
        indexed.forEachIndexed { i, m -> m.id?.let { position.putIfAbsent(it, i) } }

        val onChain = HashSet<String>(indexed.size)
        var at: Int? = indexed.lastIndex
        while (at != null) {
            val cursor = indexed[at]
            val id = cursor.id ?: break
            if (!onChain.add(id)) break // 环
            val parentId = cursor.parentId ?: break
            at = position[parentId] ?: (position.getValue(id) - 1).takeIf { it >= 0 }
        }
        return indexed.filter { it.id in onChain }
    }
}
