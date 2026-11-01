package dev.herdr.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.Pane
import dev.herdr.feature.board.conversation.ConversationBranch
import dev.herdr.feature.board.conversation.ConversationItem
import dev.herdr.feature.board.conversation.IncrementalConversationMapper
import dev.herdr.feature.board.design.DisclosureIndicator
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.MonoStyle
import dev.herdr.feature.board.design.Radius
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.markdown.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UX-03e：阅读位置状态（真实手势驱动，不用 index 猜测）。 */
internal enum class ReadMode { FollowingLatest, BrowsingHistory, RestoringAnchor, Empty }

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    pane: Pane,
    onQuote: (String) -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    truncatedStart: Boolean = false,
) {
    var highlight by remember { mutableStateOf<String?>(null) }
    val palette = LocalHerdrPalette.current
    // 增量映射：分支解析 → 增量映射（历史条目复用；会话 key 变化时清空派生状态）
    val effective = remember(messages) { ConversationBranch.effective(messages) }
    val mapper = remember(pane.paneId) { IncrementalConversationMapper() }
    val sessionKey = "${pane.terminalId}|${pane.agentSessionPath}"
    val items = remember(effective, sessionKey) { mapper.update(effective, sessionKey) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // UX-03e：显式阅读状态机（不再只靠 firstVisibleItemIndex 猜测）
    // FollowingLatest → 贴底跟随；BrowsingHistory → 停在原处并累计未读；
    // RestoringAnchor → 刚恢复锚点，暂不自动滚动；Empty → 无消息。
    var mode by remember(sessionKey) { mutableStateOf(if (items.isEmpty()) ReadMode.Empty else ReadMode.RestoringAnchor) }
    var unread by remember(sessionKey) { mutableStateOf(0) }
    var lastSeenIndex by remember(sessionKey) { mutableStateOf(items.lastIndex) }

    // 锚点：会话 + 条目稳定 key（paneId-index）+ 像素偏移，不只用 index
    var anchorKey by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }
    var anchorOffset by rememberSaveable(sessionKey) { mutableStateOf(0) }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 8
        }
    }

    // 用户手势决定状态：贴近底部 → 跟随；否则 → 浏览历史
    LaunchedEffect(atBottom) {
        if (items.isEmpty()) return@LaunchedEffect
        mode = if (atBottom) ReadMode.FollowingLatest else ReadMode.BrowsingHistory
        if (atBottom) unread = 0
    }

    // 首次进入：优先恢复锚点，否则定位最新
    LaunchedEffect(sessionKey, items.isNotEmpty()) {
        if (items.isEmpty()) { mode = ReadMode.Empty; return@LaunchedEffect }
        val key = anchorKey
        val idx = if (key != null) items.indexOfFirst { "${pane.paneId}-${it.index}" == key } else -1
        when {
            idx >= 0 -> {
                listState.scrollToItem(idx, anchorOffset)
                mode = ReadMode.RestoringAnchor
            }
            else -> listState.scrollToItem(items.lastIndex)
        }
        lastSeenIndex = items.lastIndex
    }

    // 新内容到达：仅在跟随态自动滚到底；否则累计未读并保留位置
    LaunchedEffect(items.lastIndex) {
        if (items.isEmpty()) return@LaunchedEffect
        when (mode) {
            ReadMode.FollowingLatest -> {
                listState.animateScrollToItem(items.lastIndex)
                lastSeenIndex = items.lastIndex
                unread = 0
            }
            ReadMode.BrowsingHistory, ReadMode.RestoringAnchor -> {
                if (items.lastIndex > lastSeenIndex) unread = items.lastIndex - lastSeenIndex
            }
            ReadMode.Empty -> Unit
        }
    }

    // 离开时记录锚点（切会话/返回看板后可回到原处；身份不匹配则保守回退到最新）
    DisposableEffect(sessionKey) {
        onDispose {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@onDispose
            val key = items.getOrNull(first.index)?.let { "${pane.paneId}-${it.index}" }
            anchorKey = key
            anchorOffset = first.offset
        }
    }

    // UX-06：会话内搜索，边界明确为「仅搜索已加载消息」
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchCursor by remember { mutableStateOf(0) }

    LaunchedEffect(query, items) {
        if (!searchOpen) return@LaunchedEffect
        val q = query.trim()
        if (q.isEmpty()) {
            matches = emptyList()
            highlight = null
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(250)   // 输入防抖
        matches = withContext(Dispatchers.Default) {
            items.mapIndexedNotNull { i, item ->
                val text = when (item) {
                    is ConversationItem.User -> item.text
                    is ConversationItem.Assistant -> item.text
                    is ConversationItem.Thinking -> item.text
                    is ConversationItem.Tool -> item.argumentsSummary + "\n" + (item.result ?: "")
                    is ConversationItem.Unknown -> item.text
                }
                if (text.contains(q, ignoreCase = true)) i else null
            }
        }
        matchCursor = 0
        matches.firstOrNull()?.let { idx ->
            mode = ReadMode.BrowsingHistory
            listState.animateScrollToItem(idx)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (searchOpen) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                matchCount = matches.size,
                cursor = matchCursor,
                onPrev = {
                    if (matches.isNotEmpty()) {
                        matchCursor = (matchCursor - 1 + matches.size) % matches.size
                        scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                    }
                },
                onNext = {
                    if (matches.isNotEmpty()) {
                        matchCursor = (matchCursor + 1) % matches.size
                        scope.launch { listState.animateScrollToItem(matches[matchCursor]) }
                    }
                },
                onClose = { query = ""; matches = emptyList(); highlight = null; onCloseSearch() },
            )
        }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 720.dp else Int.MAX_VALUE.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (truncatedStart) {
                // 会话文件超过回填上限：如实说明只加载了最近一段，而不是假装完整
                item(key = "truncated-notice") {
                    Text(
                        "只加载了最近的消息（更早的对话未下载）",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Space.s),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(
                items = items,
                key = { _, it -> "${pane.paneId}-${it.index}" },
                contentType = { _, it -> it.javaClass.simpleName },
            ) { i, item ->
                val prev = items.getOrNull(i - 1)
                when (item) {
                    is ConversationItem.User -> UserMessage(item.text)
                    is ConversationItem.Assistant -> {
                        // UX-03a：一组连续 Agent 回复只出现一次中性 Agent 标识（不再每条重复 11sp 绿色 "Pi"）
                        val startsGroup = prev !is ConversationItem.Assistant
                        if (startsGroup) AgentGroupHeader(pane)
                        AssistantMessage(item.text, onQuote)
                    }
                    is ConversationItem.Thinking -> CollapsibleRow(stringResource(R.string.label_thinking), item.text, muted = true)
                    is ConversationItem.Tool -> ToolRow(
                        item,
                        "${pane.terminalId}|${pane.agentSessionPath}|${pane.paneId}|${item.callId ?: item.index}",
                    )
                    is ConversationItem.Unknown -> UnknownContent(item.text)
                }
            }
        }
        if (unread > 0) {
            Surface(
                color = palette.accent,
                shape = RoundedCornerShape(Radius.chip),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.l)
                    .clickable {
                        unread = 0
                        mode = ReadMode.FollowingLatest
                        scope.launch { listState.animateScrollToItem(items.lastIndex.coerceAtLeast(0)) }
                    }
                    .semantics { contentDescription = "跳到最新消息" },
            ) {
                Text(
                    if (unread > 0) "↓ $unread 条新消息" else "↓ 回到最新",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accentOn,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
                )
            }
        }
    }
    }
}

@Composable
internal fun UserMessage(text: String) {
    val palette = LocalHerdrPalette.current
    // 用户消息靠右；宽度上限按 CONVERSATION-SPEC §3.3（88% / 86% / 520dp）
    val width = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val fraction = when {
        width < 360 -> 0.88f
        width < 600 -> 0.86f
        else -> 1f
    }
    val maxWidth = if (width >= 600) 520.dp else Dp.Unspecified
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = palette.surfaceRaised,
            // §5.1：圆角 16dp，靠尾部一角 6dp 作为角色识别
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 6.dp,
            ),
            modifier = if (maxWidth == Dp.Unspecified) Modifier.fillMaxWidth(fraction) else Modifier.widthIn(max = maxWidth),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
internal fun AssistantMessage(text: String, onQuote: (String) -> Unit, highlight: String? = null) {
    val palette = LocalHerdrPalette.current
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val collapsible = text.length > 2500 || lineCount > 40
    var expanded by remember(text) { mutableStateOf(!collapsible) }
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            MarkdownText(
                source = if (expanded || !collapsible) text else cutAtLine(text, 1200),
                modifier = Modifier.fillMaxWidth(),
                highlight = highlight,
            )
        }
        if (collapsible && !expanded) {
            Text(
                "展开全文（${text.length} 字符）",
                style = MaterialTheme.typography.labelMedium,
                color = palette.statusFg(AgentStatus.WORKING),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { expanded = true }
                    .padding(top = Space.s)
                    .semantics { contentDescription = "展开这条消息的全文" },
            )
        }
        MessageActions(text, onQuote)
    }
}

@Composable
internal fun MessageActions(text: String, onQuote: (String) -> Unit) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "复制这条回复" },
        ) {
            Text(
                if (copied) stringResource(R.string.action_copied) else stringResource(R.string.action_copy),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
        }
        Box {
            TextButton(
                onClick = { menu = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "更多操作" },
            ) {
                Text(
                    stringResource(R.string.action_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy_raw), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        clipboard.setText(AnnotatedString(text))
                        menu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_quote), style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        menu = false
                        onQuote(text)
                    },
                )
            }
        }
    }
}

/** 折叠预览在换行处截断；若截断在代码围栏内，补一个闭合 fence，避免被误报「未闭合」。 */
internal fun cutAtLine(text: String, max: Int): String {
    if (text.length <= max) return text
    val nl = '\n'
    val tick = '`'
    val cut = text.lastIndexOf(nl, max).takeIf { it > max / 2 } ?: max
    val head = text.take(cut)
    val fences = head.count { it == tick } / 3
    val suffix = if (fences % 2 == 1) "$nl$nl…$nl$tick$tick$tick" else "$nl$nl…"
    return head + suffix
}

@Composable
internal fun UnknownContent(text: String) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    var showRaw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.unknown_content_title),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
        Row {
            TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    if (showRaw) stringResource(R.string.tool_collapse) else stringResource(R.string.tool_expand),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.unknown_content_copy), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showRaw) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text, style = MonoStyle, color = palette.textPrimary)
            }
        }
    }
}

@Composable
internal fun AgentGroupHeader(pane: Pane) {
    val palette = LocalHerdrPalette.current
    val name = pane.agent?.takeIf { it.isNotBlank() }?.let {
        // 已知 agent 名保留原样（pi / claude），但使用中性色，不再用状态绿当装饰
        it.replaceFirstChar { c -> c.uppercase() }
    } ?: "Agent"
    Row(
        Modifier
            .padding(top = Space.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_agent),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.textSecondary),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall, // type.meta 13/19
            color = palette.textSecondary,
        )
    }
}

@Composable
internal fun CollapsibleRow(title: String, body: String, mono: Boolean = false, muted: Boolean = false) {
    val palette = LocalHerdrPalette.current
    var expanded by remember(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Space.xs)
                .semantics { contentDescription = "$title，${if (expanded) "已展开" else "已折叠"}，点击切换" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisclosureIndicator(expanded)
            Spacer(Modifier.width(Space.xs))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = if (muted) palette.textSecondary else palette.statusFg(AgentStatus.WORKING),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${body.length} 字符",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                color = palette.surfaceRaised,
                shape = RoundedCornerShape(Radius.code),
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(Space.m).verticalScroll(rememberScrollState()),
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        body,
                        style = if (mono) MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium,
                        color = if (muted) palette.textSecondary else palette.textPrimary,
                    )
                    }
                }
            }
        }
    }
}

/** 取前 n 行；行数不足返回 null（表示无需「查看完整输出」）。 */
internal fun previewLines(text: String, n: Int): String? {
    var count = 0
    var idx = -1
    while (true) {
        idx = text.indexOf('\n', idx + 1)
        if (idx < 0) return null
        count++
        if (count == n) return text.take(idx) + "\n…"
    }
}
