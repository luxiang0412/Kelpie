package dev.herdr.signal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.signal.conversation.MessageList
import dev.herdr.signal.conversation.model.ConversationItemMapper
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalTheme

/**
 * 长对话性能 Fixture（debug / minified 包）：确定性生成 N 条消息，直接渲染对话页的消息列表。
 *   adb shell am start -n dev.herdr.signal.min/dev.herdr.signal.FixtureActivity --ei count 2000
 * 不连远端、不写数据库、不用随机数，可重复对比。
 */
class FixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val items = ConversationItemMapper.map(Fixtures.messages(intent.getIntExtra("count", 500)))
        val prefs = container.prefs
        JankMonitor.attach(this, "fixture")
        setContent {
            val mode by prefs.theme.collectAsStateWithLifecycle()
            SignalTheme(mode) {
                Box(Modifier.fillMaxSize().background(Signal.colors.bg).systemBarsPadding()) {
                    MessageList(items, agent = null, receipt = null, now = 0L, truncated = false, loading = false)
                }
            }
        }
    }
}

/** 确定性 fixture：固定 ID、固定内容、无随机。 */
object Fixtures {

    val LONG_CODE = (1..40).joinToString("\n") { "  val item$it = computeSomething($it, \"路径/子目录/文件-$it.kt\")" }
    val LONG_TEXT = (1..12).joinToString("\n") {
        "这是第 $it 段用于测量长文本排版的说明文字，中英文混排 Mixed CJK and Latin content $it，"
    }

    fun messages(count: Int): List<ChatMessage> {
        val out = ArrayList<ChatMessage>(count)
        for (i in 0 until count) {
            out += when (i % 10) {
                0 -> ChatMessage(ChatMessage.Kind.USER, "第 $i 条：检查 $i 号模块的依赖关系，并给出结论（mixed English text）。")
                // Markdown 全要素：标题 + 列表 + 引用 + 行内样式 + 安全链接
                1 -> ChatMessage(
                    ChatMessage.Kind.ASSISTANT,
                    "## 结论 $i\n\n已检查 **$i 号模块**，`grep` 结果显示如下：\n\n" +
                        "- 依赖无环，*没有*循环引用\n- 但存在 2 处未使用导入\n- 参考 [文档](https://example.com/module/$i)\n\n" +
                        "> 建议：先跑一遍 `./gradlew check` 再重构。",
                )
                2 -> ChatMessage(ChatMessage.Kind.THINKING, "先确认 $i 号模块的入口，再决定是否需要重构。")
                // toolCall 带稳定 id，后面按 id 配对（中间隔着 4 号思考消息）
                3 -> ChatMessage(
                    ChatMessage.Kind.TOOL_CALL,
                    "{\"command\":\"rg -n 'import' src/module$i | head -20\"}",
                    toolName = "bash",
                    toolCallId = "call-$i",
                )
                4 -> ChatMessage(ChatMessage.Kind.ASSISTANT, "继续看第 $i 条的 *边界情况*，顺便验证链接 [不安全示例](javascript:alert(1)) 不会被点击。")
                5 -> ChatMessage(
                    ChatMessage.Kind.TOOL_RESULT,
                    // 每 20 条里有一条**超过 12 行**的输出，用于验证「查看完整输出」页
                    if (i % 20 == 5) {
                        (1..20).joinToString("") { n ->
                            "src/module$i/File$n.kt:$n: import kotlinx.coroutines.*  // 第 $n 行输出\n"
                        } + "Error: 第 $i 条里有一个警告（长输出，用于验证完整输出页）\n"
                    } else {
                        "src/module$i/Main.kt:3:import kotlinx.coroutines.*\nsrc/module$i/Util.kt:9:import java.io.File\n".repeat(3) +
                            "Error: 第 $i 条里有一个警告\n"
                    },
                    toolCallId = "call-${i - 2}",
                )
                6 -> ChatMessage(ChatMessage.Kind.ASSISTANT, LONG_TEXT)
                7 -> ChatMessage(ChatMessage.Kind.ASSISTANT, "```kotlin\n$LONG_CODE\n```")
                // 异常/未知内容（不进渲染模型时不得崩溃）
                8 -> ChatMessage(ChatMessage.Kind.ASSISTANT, "| 表格列 A | 表格列 B |\n|---|---|\n| $i | 未支持 |\n\n![](/nonexistent.png)")
                else -> ChatMessage(ChatMessage.Kind.ASSISTANT, "第 $i 条结果：/home/user/project/private/herdr-gui-research/module-$i/build/outputs/logs/fixture-long-path-$i.txt")
            }
        }
        return out
    }
}
