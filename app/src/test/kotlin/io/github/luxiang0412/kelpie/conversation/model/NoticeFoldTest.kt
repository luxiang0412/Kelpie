package io.github.luxiang0412.kelpie.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeFoldTest {
    private fun n(i: Int, text: String) = ConversationItem.Unknown(i, text)

    @Test
    fun `consecutive notices with the same title fold, in order`() {
        val items = listOf(
            ConversationItem.User(0, "盯一下 CI"),
            n(1, "后台任务 · PR #480 CI\ncli: pass"),
            n(2, "后台任务 · PR #480 CI\nsandbox: pass"),
            n(3, "后台任务 · PR #480 CI\nfrontend: pass"),
        )
        val out = NoticeFold.fold(items)
        assertEquals(2, out.size)
        val f = out[1] as ConversationItem.Unknown
        assertEquals(3, f.count)
        assertEquals(3, f.index)
        assertEquals("后台任务 · PR #480 CI\ncli: pass\nsandbox: pass\nfrontend: pass", f.text)
    }

    @Test
    fun `different titles or something in between are not folded`() {
        val items = listOf(
            n(0, "后台任务 · A\nx"),
            n(1, "后台任务 · B\ny"),
            ConversationItem.Assistant(2, "还在等"),
            n(3, "后台任务 · B\nz"),
        )
        assertEquals(items, NoticeFold.fold(items))
    }
}
