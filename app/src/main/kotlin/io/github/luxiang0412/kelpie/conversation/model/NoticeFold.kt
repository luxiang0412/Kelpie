package io.github.luxiang0412.kelpie.conversation.model

/**
 * 连续几条标题相同的说明（例如 Monitor 一次次报 “PR #480 CI check results”）折成一条：
 * 内容按时间顺序拼在一起，[ConversationItem.Unknown.count] 记条数，下标取最新那条。
 * 中间夹着别的消息就不折（那是两段不同的上下文）。
 */
object NoticeFold {
    fun title(text: String): String = text.substringBefore('\n')
    fun body(text: String): String = text.substringAfter('\n', "")

    fun fold(items: List<ConversationItem>): List<ConversationItem> {
        if (items.count { it is ConversationItem.Unknown } < 2) return items
        val out = ArrayList<ConversationItem>(items.size)
        for (m in items) {
            val prev = out.lastOrNull()
            if (m is ConversationItem.Unknown && prev is ConversationItem.Unknown && title(prev.text) == title(m.text)) {
                val bodies = listOf(body(prev.text), body(m.text)).filter { it.isNotBlank() }
                out[out.lastIndex] = ConversationItem.Unknown(
                    index = m.index,
                    text = (listOf(title(m.text)) + bodies).joinToString("\n"),
                    count = prev.count + m.count,
                )
            } else {
                out += m
            }
        }
        return out
    }
}
