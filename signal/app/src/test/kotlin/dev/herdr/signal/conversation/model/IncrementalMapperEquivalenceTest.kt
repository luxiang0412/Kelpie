package dev.herdr.signal.conversation.model

import dev.herdr.core.herdr.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 增量 vs 参考实现的一致性（M2-C1 §6）。
 *
 * 固定种子生成事件序列（追加 / 重复事件 / 乱序结果 / 内容更新 / 未知类型），
 * 每一步都要求两边**逐条完全一致**（内容、顺序、关联、pairedById）。
 * 失败时可复现：种子与步数都写在断言消息里。
 */
class IncrementalMapperEquivalenceTest {

    private class Gen(val seed: Long) {
        val rnd = Random(seed)
        val messages = ArrayList<ChatMessage>()
        val callIds = ArrayList<String>()
        private var n = 0
        private var lastId: String? = null

        private fun id() = "id-${n++}"

        fun snapshot(): List<ChatMessage> = ArrayList(messages)

        fun step(): List<ChatMessage> {
            val roll = rnd.nextInt(100)
            val newId = id()
            val msg = when {
                roll < 55 -> {
                    val kinds = listOf(
                        ChatMessage.Kind.ASSISTANT to "## 结论 ${n}\n\n- 项 A\n- 项 B\n\n`code` 和 [链接](https://e.com/$n)",
                        ChatMessage.Kind.USER to "第 ${n} 条指令 mixed English",
                        ChatMessage.Kind.THINKING to "思考 ${n}",
                    )
                    val (k, t) = kinds[rnd.nextInt(kinds.size)]
                    ChatMessage(k, t, id = newId, parentId = lastId)
                }
                roll < 70 -> {
                    val cid = "call-${n}"
                    callIds += cid
                    ChatMessage(ChatMessage.Kind.TOOL_CALL, """{"cmd":"ls $n"}""", toolName = "bash", toolCallId = cid, id = newId, parentId = lastId)
                }
                roll < 82 && callIds.isNotEmpty() -> {
                    val cid = callIds[rnd.nextInt(callIds.size)]
                    ChatMessage(ChatMessage.Kind.TOOL_RESULT, "out $n", toolCallId = cid, id = newId, parentId = lastId)
                }
                roll < 88 -> ChatMessage(ChatMessage.Kind.TOOL_RESULT, "orphan $n", toolCallId = "unknown-${n}", id = newId, parentId = lastId)
                roll < 94 && messages.isNotEmpty() -> messages[rnd.nextInt(messages.size)].copy() // 重复事件（同 id）
                else -> ChatMessage(ChatMessage.Kind.SYSTEM, "system $n", id = newId, parentId = lastId)
            }
            messages += msg
            lastId = msg.id
            return snapshot()
        }

        /** 就地更新已有消息内容（upsert 语义）。 */
        fun mutate(): List<ChatMessage> {
            val candidates = messages.indices.filter {
                when (messages[it].kind) {
                    ChatMessage.Kind.USER, ChatMessage.Kind.ASSISTANT, ChatMessage.Kind.THINKING -> true
                    else -> false
                }
            }
            if (candidates.isEmpty()) return snapshot()
            val at = candidates[rnd.nextInt(candidates.size)]
            messages[at] = messages[at].copy(text = messages[at].text + " · 更新${n++}")
            return snapshot()
        }
    }

    private fun assertSame(expected: List<ConversationItem>, actual: List<ConversationItem>, context: String) {
        assertEquals("$context: 条目数不一致", expected.size, actual.size)
        expected.forEachIndexed { i, e ->
            assertEquals("$context: 第 $i 条不一致", e, actual[i])
        }
    }

    @Test
    fun randomSequences_matchReference() {
        for (seed in 1L..12L) {
            val gen = Gen(seed)
            val inc = IncrementalConversationMapper()
            repeat(120) { step ->
                val snap = gen.step()
                val got = inc.update(snap, "session")
                val want = ConversationItemMapper.map(snap)
                assertSame(ConversationItemMapper.map(ConversationBranch.effective(snap)), ConversationItemMapper.map(ConversationBranch.effective(snap)), "seed=$seed step=$step sanity")
                assertSame(want, got, "seed=$seed step=$step")
            }
        }
    }

    @Test
    fun updates_matchReference() {
        for (seed in 20L..28L) {
            val gen = Gen(seed)
            val inc = IncrementalConversationMapper()
            repeat(60) {
                inc.update(gen.step(), "session")
                val snap = gen.mutate()
                assertSame(ConversationItemMapper.map(snap), inc.update(snap, "session"), "seed=$seed upsert")
            }
        }
    }

    @Test
    fun branchDerivedSequences_matchReference() {
        for (seed in 40L..46L) {
            val gen = Gen(seed)
            val inc = IncrementalConversationMapper()
            repeat(30) { inc.update(gen.step(), "session") }
            // 制造分支：复制第 10 条为新的叶子，其 parentId 指向第 5 条
            val base = gen.snapshot()
            val branchLeaf = base[10].copy(id = "branch-leaf", parentId = base[5].id, text = "分支内容")
            val withBranch = ArrayList(base) .apply { add(branchLeaf) }
            val effective = ConversationBranch.effective(withBranch)
            assertSame(ConversationItemMapper.map(effective), inc.update(effective, "session#branch"), "seed=$seed branch")
        }
    }

    @Test
    fun sessionSwitch_discardsPreviousState() {
        val gen = Gen(7)
        val inc = IncrementalConversationMapper()
        repeat(5) { inc.update(gen.step(), "session-A") }
        val other = listOf(ChatMessage(ChatMessage.Kind.USER, "另一个会话第一条", id = "o1"))
        val items = inc.update(other, "session-B")
        assertEquals(listOf<ConversationItem>(ConversationItem.User(0, "另一个会话第一条")), items)
        assertEquals(IncrementalConversationMapper.Mode.FULL, inc.stats.mode)
    }

    @Test
    fun appendMapsOnlyTheDelta() {
        val gen = Gen(99)
        val inc = IncrementalConversationMapper()
        var snap = gen.snapshot()
        repeat(2000) { snap = gen.step() }
        inc.update(snap, "s") // 首次全量
        val appended = Gen(1000)
        val extra = appended.step().last()

        val next = ArrayList(snap).apply { add(extra.copy(id = "tail-1", parentId = snap.last().id)) }
        inc.update(next, "s")
        assertEquals(IncrementalConversationMapper.Mode.APPEND, inc.stats.mode)
        assertEquals("追加 1 条只应映射 1 条", 1, inc.stats.mapped)
        assertTrue("映射成本比例应远小于 1", inc.stats.mappingCostRatio < 0.01)
        assertEquals(ConversationItemMapper.map(next), inc.update(next, "s"))
    }

    @Test
    fun toolResultAppendPatchesWithoutRemappingHistory() {
        val call = ChatMessage(ChatMessage.Kind.TOOL_CALL, "{}", toolName = "bash", toolCallId = "c1", id = "m1")
        val head = listOf(ChatMessage(ChatMessage.Kind.USER, "hi", id = "m0"), call)
        val inc = IncrementalConversationMapper()
        inc.update(head, "s")

        val withResult = head + ChatMessage(ChatMessage.Kind.TOOL_RESULT, "done", toolCallId = "c1", id = "m2")
        val items = inc.update(withResult, "s")
        assertEquals(IncrementalConversationMapper.Mode.APPEND, inc.stats.mode)
        assertEquals(0, inc.stats.mapped)
        assertEquals("done", (items[1] as ConversationItem.Tool).result)
        assertTrue((items[1] as ConversationItem.Tool).pairedById)
        assertEquals(ConversationItemMapper.map(withResult), items)
    }

    @Test
    fun emptyThenReset_isStable() {
        val inc = IncrementalConversationMapper()
        assertEquals(emptyList<ConversationItem>(), inc.update(emptyList(), "s"))
        inc.reset()
        val one = listOf(ChatMessage(ChatMessage.Kind.USER, "x", id = "1"))
        assertEquals(ConversationItemMapper.map(one), inc.update(one, "s"))
    }
}
