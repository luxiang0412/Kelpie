package dev.herdr.signal.conversation.model

/** 对话内搜索：按条目文本做不区分大小写的子串匹配，返回命中条目的 index（从旧到新）。 */
object ConversationSearch {
    fun text(item: ConversationItem): String = when (item) {
        is ConversationItem.User -> item.text
        is ConversationItem.Assistant -> item.text
        is ConversationItem.Thinking -> item.text
        is ConversationItem.Tool -> item.name + "\n" + item.argumentsSummary + "\n" + item.result.orEmpty()
        is ConversationItem.Unknown -> item.text
    }

    fun matches(items: List<ConversationItem>, query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return items.filter { text(it).contains(q, ignoreCase = true) }.map { it.index }
    }
}
