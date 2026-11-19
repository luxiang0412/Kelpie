package io.github.luxiang0412.kelpie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.luxiang0412.kelpie.conversation.FullToolOutput
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import io.github.luxiang0412.kelpie.conversation.MessageList
import io.github.luxiang0412.kelpie.conversation.model.ConversationItemMapper
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieTheme

/**
 * 长对话性能 Fixture（debug / minified 包）：确定性生成 N 条消息，直接渲染对话页的消息列表。
 *   adb shell am start -n io.github.luxiang0412.kelpie.min/io.github.luxiang0412.kelpie.FixtureActivity --ei count 2000
 * 不连远端、不写数据库、不用随机数，可重复对比。
 *
 * `--ez markdown_board true`：改为显示 Markdown 画板的五组例子（嵌套列表、列表项下的代码块、分隔线、
 * 多行引用、表格 `<br>`），真机截图和画板逐项对照用；不影响性能基准用的默认内容。
 */
class FixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val messages = if (intent.getBooleanExtra("markdown_board", false)) Fixtures.markdownBoard()
        else Fixtures.messages(intent.getIntExtra("count", 500))
        val items = ConversationItemMapper.map(messages)
        val prefs = container.prefs
        JankMonitor.attach(this, "fixture")
        setContent {
            val mode by prefs.theme.collectAsStateWithLifecycle()
            KelpieTheme(mode) {
                Box(Modifier.fillMaxSize().background(Kelpie.colors.bg).systemBarsPadding()) {
                    var full by remember { mutableStateOf<ConversationItem.Tool?>(null) }
                    MessageList(items, agent = null, receipt = null, now = 0L, truncated = false, loading = false, onFullTool = { full = it })
                    full?.let { FullToolOutput(it) { full = null } }
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

    /** Markdown 画板（2026-11-20）的五组例子，和画板上的文字一致。 */
    fun markdownBoard(): List<ChatMessage> = listOf(
        "**结果**\n- **测试**：后端 434 项、前端 140 项通过。\n- **新改动**：\n  - 小结列了两个工具，两门课都和目录一致。\n" +
            "  - 32 条导师回复，审核都没有删改。\n  - 日志没有报错。\n- **回退**：31 秒，旧会话能打开。",
        "1. 拿到这个会话的所有请求 ID，可以从日志里先搜出来：\n   ```\n   docker logs api 2>&1 | grep req_id | sort -u\n   ```\n" +
            "2. 用这些 ID 再搜一遍，就能看到每轮的完整日志。\n   注意：日志只保留 7 天。",
        "没有 GPU 直通，H.264 软编码，所以 CPU 占用会比硬件编码高。\n\n---\n\n**一句话总结**：Chromium 156，LinuxServer 镜像，Selkies 串流。",
        "**X（中文）**\n\n> 离开电脑后，想知道开发机上的 agent 是不是在等我确认？\n>\n> 我做了个 Android App：Kelpie。\n" +
            "> · 所有 agent 按“谁在等你”排序\n> · 在手机上按住允许或拒绝",
        "| PR | 内容 |\n|---|---|\n| **日志和请求 ID** | • 请求 ID 由服务端生成<br>• ERROR 日志带上堆栈<br>• 去掉 MQ 正文预览 |",
    ).map { ChatMessage(ChatMessage.Kind.ASSISTANT, it) }

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
