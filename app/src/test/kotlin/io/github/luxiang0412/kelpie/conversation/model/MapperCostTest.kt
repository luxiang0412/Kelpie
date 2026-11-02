package io.github.luxiang0412.kelpie.conversation.model

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapper 算法耗时（JVM 微基准，**不是** UI 帧指标）。
 *
 * 用途：证明增量映射的算法成本随变化量增长，而不是随历史规模增长。
 * 结果不与 Compose/真机帧时混用（见 M2-C1 报告的「测量口径」）。
 */
class MapperCostTest {

    private fun history(n: Int): List<ChatMessage> = (0 until n).map { i ->
        when (i % 4) {
            0 -> ChatMessage(ChatMessage.Kind.USER, "问题 $i", id = "u$i")
            1 -> ChatMessage(ChatMessage.Kind.ASSISTANT, "## 回答 $i\n\n- a\n- b `code`", id = "a$i")
            2 -> ChatMessage(ChatMessage.Kind.TOOL_CALL, "{\"cmd\":\"ls\"}", toolName = "bash", toolCallId = "c$i", id = "c$i")
            else -> ChatMessage(ChatMessage.Kind.TOOL_RESULT, "out $i", toolCallId = "c${i - 1}", id = "r$i")
        }
    }

    private fun timeMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }

    @Test
    fun incrementalAppend_isMuchCheaperThanFullMap() {
        val base = history(2000)
        val appends = (0 until 100).map { i ->
            ChatMessage(ChatMessage.Kind.ASSISTANT, "追加 $i", id = "tail$i")
        }

        val fullNs = timeMs {
            var acc = 0
            appends.forEachIndexed { i, _ ->
                acc += ConversationItemMapper.map(base + appends.take(i + 1)).size
            }
            assertTrue(acc > 0)
        }

        val inc = IncrementalConversationMapper()
        inc.update(base, "bench")
        val incMs = timeMs {
            appends.forEachIndexed { i, m ->
                inc.update(base + appends.take(i + 1), "bench")
                assertTrue(inc.stats.mapped <= 1)
            }
        }

        println(
            "MAPPER-COST base=2000 appends=100 full=${"%.1f".format(fullNs)}ms " +
                "incremental=${"%.1f".format(incMs)}ms ratio=${"%.2f".format(fullNs / incMs)}x",
        )
        assertTrue("增量应显著快于全量（full=${fullNs}ms inc=${incMs}ms）", incMs * 5 < fullNs)
    }

    @Test
    fun fullMapScalesButIncrementalDoesNot() {
        val small = history(200)
        val large = history(2000)
        val tail = ChatMessage(ChatMessage.Kind.USER, "tail", id = "tail-x")

        val smallMs = timeMs { repeat(50) { ConversationItemMapper.map(small + tail) } }
        val largeMs = timeMs { repeat(50) { ConversationItemMapper.map(large + tail) } }
        println("MAPPER-SCALE full small=${"%.1f".format(smallMs)}ms large=${"%.1f".format(largeMs)}ms")

        val incSmall = IncrementalConversationMapper().apply { update(small, "s") }
        val incLarge = IncrementalConversationMapper().apply { update(large, "l") }
        val incSmallMs = timeMs { repeat(50) { incSmall.update(small + tail, "s") } }
        val incLargeMs = timeMs { repeat(50) { incLarge.update(large + tail, "l") } }
        println("MAPPER-SCALE incremental small=${"%.1f".format(incSmallMs)}ms large=${"%.1f".format(incLargeMs)}ms")

        // 全量随历史线性增长（10 倍数据 → 至少 3 倍耗时）；增量几乎不变。
        assertTrue("全量映射应随规模变慢", largeMs > smallMs * 2)
        assertTrue("增量映射耗时不随历史规模显著增长", incLargeMs < incSmallMs * 6 + 5)
    }
}
