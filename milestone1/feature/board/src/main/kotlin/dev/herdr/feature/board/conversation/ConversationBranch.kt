package dev.herdr.feature.board.conversation

import dev.herdr.core.herdr.ChatMessage

/**
 * Pi 的会话是**树**（每条事件带 `id` / `parentId`）。UI 只应展示「当前有效分支」：
 * 从最后一条事件沿 `parentId` 回溯到根，再反转。
 *
 * 约束：
 *  - 只在消息带 `id` 时启用；全部没有 id 时按线性顺序返回（旧格式/夹具）；
 *  - 环或悬空 parentId → 停止回溯并返回已收集部分，绝不抛异常；
 *  - 分支切换 = 用新分支的最后一条事件重新调用本函数（受控全量重建）。
 */
object ConversationBranch {

    fun effective(messages: List<ChatMessage>): List<ChatMessage> {
        val indexed = messages.filter { it.id != null }
        if (indexed.isEmpty()) return messages

        val byId = HashMap<String, ChatMessage>(indexed.size)
        indexed.forEach { m -> m.id?.let { byId[it] = m } }

        val chain = ArrayList<ChatMessage>(indexed.size)
        val visited = HashSet<String>(indexed.size)
        var cursor: ChatMessage? = indexed.last()

        while (cursor != null) {
            val id = cursor.id
            if (id != null && !visited.add(id)) break
            chain += cursor
            val parentId = cursor.parentId ?: break
            cursor = byId[parentId]
        }
        chain.reverse()
        return chain
    }
}
