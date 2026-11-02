package dev.herdr.signal.markdown

import dev.herdr.core.herdr.t

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
import androidx.compose.ui.draw.clip
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
import dev.herdr.signal.designsystem.LocalSignalColors
import dev.herdr.signal.designsystem.SignalRadius
import dev.herdr.signal.designsystem.SignalSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 子集渲染（选型见 docs/signal/MARKDOWN-RENDERING.md）。
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
    /** UX-06：搜索命中高亮词（大小写不敏感）；null 表示不高亮。 */
    highlight: String? = null,
) {
    // 短文本（绝大多数消息）同步解析：<2k 字符的解析是微秒级，
    // 异步反而会在滚动时闪出「原始 Markdown」（``` 与 * 直接可见）。
    // 长文本仍走后台线程，避免主线程做重活。
    val syncBlocks = if (source.length <= SYNC_PARSE_LIMIT) MarkdownAstCache.getOrParse(source) else null
    val blocks by produceState<List<MdBlock>?>(initialValue = syncBlocks, source) {
        if (value == null) value = withContext(Dispatchers.Default) { MarkdownAstCache.getOrParse(source) }
    }
    val palette = LocalSignalColors.current

    val parsed = blocks
    if (parsed == null) {
        // 解析中的一次近似渲染：纯文本，不做任何 Markdown 解释。
        Text(
            text = source,
            modifier = modifier,
            style = MdBody,
            color = color ?: palette.textPrimary,
        )
        return
    }

    // 段落间距 12dp（规格 10–14dp）；标题另有上下间距（见 RenderBlock）
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parsed.forEach { block -> RenderBlock(block, color ?: palette.textPrimary, highlight) }
    }
}

@Composable
private fun RenderBlock(block: MdBlock, textColor: androidx.compose.ui.graphics.Color, highlight: String? = null) {
    val palette = LocalSignalColors.current
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.spans, highlight),
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            style = when (block.level) {
                1 -> MdH1Style
                2 -> MdH2Style
                else -> MdH3Style
            },
            color = textColor,
        )

        is MdBlock.Paragraph -> Text(
            text = inline(block.spans, highlight),
            style = MdBody,
            color = textColor,
        )

        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(palette.outlineCard))
            Spacer(Modifier.width(12.dp))
            Text(
                text = inline(block.spans, highlight),
                style = MdBodySmall,
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
                        style = MdBody,
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
                        style = MdBody,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        is MdBlock.CodeFence -> CodeBlock(block)

        is MdBlock.Table -> TableBlock(block, textColor, highlight)
    }
}

/**
 * GFM 表格：按列等分（Compose 文本会自然换行，不做横向滚动，避免与气泡宽度打架）。
 * 表头加重 + 底部 1dp 分隔线；行之间 1dp 细分隔线由背景色承担，保证深色也能看清。
 */
@Composable
private fun TableBlock(
    table: MdBlock.Table,
    textColor: androidx.compose.ui.graphics.Color,
    highlight: String?,
) {
    val palette = LocalSignalColors.current
    val columns = table.header.size.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SignalRadius.l / 2))
            .background(palette.surfaceSubtle),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            table.header.forEachIndexed { idx, cell ->
                Text(
                    text = inline(cell, highlight),
                    style = MdBodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier
                        .weight(if (idx == columns - 1) 1.2f else 1f)
                        .padding(end = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.outlineCard))
        table.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.outlineCard))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                row.forEachIndexed { idx, cell ->
                    Text(
                        text = inline(cell, highlight),
                        style = MdBodySmall,
                        color = textColor,
                        modifier = Modifier
                            .weight(if (idx == columns - 1) 1.2f else 1f)
                            .padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

private const val CodePreviewLines = 12

@Composable
private fun CodeBlock(block: MdBlock.CodeFence) {
    val palette = LocalSignalColors.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember(block.code) { mutableStateOf(false) }
    var copied by remember(block.code) { mutableStateOf(false) }

    val lineCount = remember(block.code) { block.code.count { it == '\n' } + 1 }
    val collapsible = lineCount > CodePreviewLines

    Surface(
        color = palette.surfaceSubtle,
        shape = RoundedCornerShape(SignalRadius.l),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = SignalSpace.s5, end = SignalSpace.s1, top = SignalSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (block.language ?: "text").lowercase() + if (!block.closed) t(" · 未闭合", " · unclosed") else "",
                    style = MdCaption,
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
                    Text(if (copied) t("已复制", "Copied") else t("复制", "Copy"), style = MdLabel)
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
                        .padding(horizontal = SignalSpace.s5, vertical = SignalSpace.s1)
                        .semantics { contentDescription = t("${block.language ?: "text"} 代码块，共 $lineCount 行", "${block.language ?: "text"} code block, $lineCount lines") },
                )
            }
            if (collapsible) {
                Text(
                    text = if (expanded) t("收起代码", "Collapse code") else t("展开全部 $lineCount 行", "Show all $lineCount lines"),
                    style = MdLabel,
                    color = palette.working,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(horizontal = SignalSpace.s5, vertical = SignalSpace.s5)
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
private fun inline(spans: List<MdSpan>, highlight: String? = null): AnnotatedString {
    val palette = LocalSignalColors.current
    val mono = MonoStyle.fontFamily
    val codeBg = palette.surfaceSubtle
    val linkColor = palette.working

    fun build(builder: AnnotatedString.Builder, list: List<MdSpan>) {
        list.forEach { s ->
            when (s) {
                is MdSpan.Text -> appendWithHighlight(builder, s.text, highlight, palette)
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
                        builder.append(t("（链接已停用）", " (link disabled)"))
                    }
                }
            }
        }
    }

    return buildAnnotatedString { build(this, spans) }
}

/** 把一段纯文本按命中词拆分：命中片段加背景色，其余原样（不改动字符内容）。 */
private fun appendWithHighlight(
    builder: AnnotatedString.Builder,
    text: String,
    highlight: String?,
    palette: dev.herdr.signal.designsystem.SignalColors,
) {
    val needle = highlight?.trim().orEmpty()
    if (needle.isEmpty()) {
        builder.append(text)
        return
    }
    val bg = palette.attentionContainer
    var index = 0
    while (index < text.length) {
        val hit = text.indexOf(needle, index, ignoreCase = true)
        if (hit < 0) {
            builder.append(text.substring(index))
            break
        }
        if (hit > index) builder.append(text.substring(index, hit))
        builder.withStyle(SpanStyle(background = bg)) {
            builder.append(text.substring(hit, hit + needle.length))
        }
        index = hit + needle.length
    }
}
