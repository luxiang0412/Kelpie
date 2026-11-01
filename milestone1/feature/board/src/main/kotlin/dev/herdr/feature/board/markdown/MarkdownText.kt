package dev.herdr.feature.board.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.MdH1Style
import dev.herdr.feature.board.design.MdH2Style
import dev.herdr.feature.board.design.MdH3Style
import dev.herdr.feature.board.design.MonoStyle
import dev.herdr.feature.board.design.Radius
import dev.herdr.feature.board.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 子集渲染（选型见 docs/design/MARKDOWN-RENDERING-DECISION.md）。
 *
 * 性能约定：
 *  - 解析在 [Dispatchers.Default] 上执行，主线程只做排版；
 *  - 解析结果按 [source] 缓存（remember key），消息文本不变就不重复解析；
 *  - 解析完成前先显示纯文本，避免出现空白闪烁。
 *  - 代码块默认最多渲染 [CodePreviewLines] 行，展开后才渲染全部（B4「长输出按需渲染」）。
 */
private const val SYNC_PARSE_LIMIT = 2_000

@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    // 短文本（绝大多数消息）同步解析：<2k 字符的解析是微秒级，
    // 异步反而会在滚动时闪出「原始 Markdown」（``` 与 * 直接可见）。
    // 长文本仍走后台线程，避免主线程做重活。
    val syncBlocks = if (source.length <= SYNC_PARSE_LIMIT) MarkdownAstCache.getOrParse(source) else null
    val blocks by produceState<List<MdBlock>?>(initialValue = syncBlocks, source) {
        if (value == null) value = withContext(Dispatchers.Default) { MarkdownAstCache.getOrParse(source) }
    }
    val palette = LocalHerdrPalette.current

    val parsed = blocks
    if (parsed == null) {
        // 解析中的一次近似渲染：纯文本，不做任何 Markdown 解释。
        Text(
            text = source,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = color ?: palette.textPrimary,
        )
        return
    }

    // 段落间距 12dp（规格 10–14dp）；标题另有上下间距（见 RenderBlock）
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parsed.forEach { block -> RenderBlock(block, color ?: palette.textPrimary) }
    }
}

@Composable
private fun RenderBlock(block: MdBlock, textColor: androidx.compose.ui.graphics.Color) {
    val palette = LocalHerdrPalette.current
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.spans),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            style = when (block.level) {
                1 -> MdH1Style
                2 -> MdH2Style
                else -> MdH3Style
            },
            color = textColor,
        )

        is MdBlock.Paragraph -> Text(
            text = inline(block.spans),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
        )

        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(palette.borderSubtle))
            Spacer(Modifier.width(12.dp))
            Text(
                text = inline(block.spans),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
        }

        // 列表悬挂缩进：标记占固定列宽，正文左缘对齐，换行后不会顶到标记下面
        is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text(
                        "•",
                        color = palette.textSecondary,
                        modifier = Modifier.width(20.dp),
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        inline(item),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text(
                        "${block.start + i}.",
                        color = palette.textSecondary,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.Start,
                    )
                    Text(
                        inline(item),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        is MdBlock.CodeFence -> CodeBlock(block)
    }
}

private const val CodePreviewLines = 12

@Composable
private fun CodeBlock(block: MdBlock.CodeFence) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember(block.code) { mutableStateOf(false) }
    var copied by remember(block.code) { mutableStateOf(false) }

    val lineCount = remember(block.code) { block.code.count { it == '\n' } + 1 }
    val collapsible = lineCount > CodePreviewLines

    Surface(
        color = palette.surfaceRaised,
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.m, end = Space.xs, top = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (block.language ?: "text").lowercase() + if (!block.closed) " · 未闭合" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    fontFamily = MonoStyle.fontFamily,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(block.code))
                        copied = true
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (copied) "已复制" else "复制", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (expanded || !collapsible) 2000.dp else 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (expanded || !collapsible) block.code else previewLines(block.code),
                    style = MonoStyle,
                    color = palette.textPrimary,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.m, vertical = Space.xs)
                        .semantics { contentDescription = "${block.language ?: "text"} 代码块，共 $lineCount 行" },
                )
            }
            if (collapsible) {
                Text(
                    text = if (expanded) "收起代码" else "展开全部 $lineCount 行",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.statusFg(dev.herdr.core.herdr.AgentStatus.WORKING),
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(horizontal = Space.m, vertical = Space.m)
                        .heightIn(min = 24.dp),
                )
            }
        }
    }
}

private fun previewLines(code: String): String {
    var count = 0
    var idx = -1
    while (true) {
        idx = code.indexOf('\n', idx + 1)
        if (idx < 0) break
        count++
        if (count == CodePreviewLines) return code.take(idx) + "\n…"
    }
    return code
}

/** 行内 span → AnnotatedString；不可信链接只做样式，不注册点击。 */
@Composable
private fun inline(spans: List<MdSpan>): AnnotatedString {
    val palette = LocalHerdrPalette.current
    val mono = MonoStyle.fontFamily
    val codeBg = palette.surfaceRaised
    val linkColor = palette.statusFg(dev.herdr.core.herdr.AgentStatus.WORKING)

    fun build(builder: AnnotatedString.Builder, list: List<MdSpan>) {
        list.forEach { s ->
            when (s) {
                is MdSpan.Text -> builder.append(s.text)
                is MdSpan.Bold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { build(builder, s.spans) }
                is MdSpan.Italic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { build(builder, s.spans) }
                // UX-01b：原实现用 " code " 的前后空格伪造 padding，会污染真实文本（复制/选区/搜索都会带上空格）。
                // 改为保真追加：只用背景 Span 做视觉区分，不再改变字符内容。
                is MdSpan.Code -> builder.withStyle(
                    SpanStyle(fontFamily = mono, background = codeBg, fontSize = 14.sp),
                ) { builder.append(s.text) }
                is MdSpan.Link -> {
                    if (s.safe) {
                        val url = s.url
                        builder.withLink(LinkAnnotation.Url(url)) {
                            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                append(s.text)
                            }
                        }
                    } else {
                        // 不安全的协议：显示文字 + 明确标注，不给可点击链接（B3「安全处理不可信 URL」）。
                        builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            builder.append(s.text)
                        }
                        builder.append("（链接已停用）")
                    }
                }
            }
        }
    }

    return buildAnnotatedString { build(this, spans) }
}
