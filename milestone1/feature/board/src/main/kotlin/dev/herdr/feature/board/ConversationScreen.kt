package dev.herdr.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.ChatMessage
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.feature.board.conversation.ConversationItem
import dev.herdr.feature.board.conversation.ConversationBranch
import dev.herdr.feature.board.conversation.IncrementalConversationMapper
import dev.herdr.feature.board.design.ComposerGutter
import dev.herdr.feature.board.design.DisclosureIndicator
import dev.herdr.feature.board.design.TouchTargetIconButton
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.MonoStyle
import dev.herdr.feature.board.markdown.MarkdownText
import kotlinx.coroutines.launch
import dev.herdr.feature.board.design.Radius
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.StatusText

/**
 * W2 对话：角色区分 + thinking/tool 折叠 + 固定 Composer。
 * 无结构化会话（agent_session 为 null）时降级为“终端尾部”，不假装有对话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    pane: Pane?,
    transcript: HostsViewModel.TranscriptState,
    output: String,
    composer: HostConnectionManager.ComposerState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissOutcome: () -> Unit,
    onReload: () -> Unit,
    onOpenTechSheet: () -> Unit,
    onBack: () -> Unit,
    onQuote: (String) -> Unit = {},
    /** UX-03e：连接中断/重连中时为 true —— 保留缓存内容，但明确标注"正在重新同步"。 */
    resyncing: Boolean = false,
) {
    val palette = LocalHerdrPalette.current
    if (pane == null) {
        Text("会话已不存在", Modifier.padding(Space.xxl))
        return
    }
    val hasTranscript = pane.agentSessionPath != null
    val title = displayTitle(pane)

    Scaffold(
        containerColor = palette.background,
        topBar = {
            // UX-01a：不用固定高度的 TopAppBar（大字体下会裁剪并压住首条消息）。
            // 行高由内容决定：标题单行省略，副标题仅在正常字号显示，返回/更多恒为 48dp。
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.background)
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.conversation_back), style = MaterialTheme.typography.labelLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (LocalDensity.current.fontScale <= 1.05f) {
                            Text(
                                projectLine(pane),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    StatusChip(pane.status)
                    TextButton(onClick = onOpenTechSheet, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.conversation_more), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        bottomBar = {
            ComposerBar(composer, pane, onTextChange, onSend, onDismissOutcome)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (pane.status == AgentStatus.BLOCKED) {
                BlockedNotice()
            }
            if (resyncing) {
                Surface(color = palette.surfaceRaised, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "重新同步中 · 下面显示的是已缓存内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.xs),
                    )
                }
            }
            when {
                !hasTranscript -> TerminalFallback(output, onReload)
                transcript.loading -> LoadingBlock("读取会话…")
                transcript.error != null -> ErrorBlock(transcript.error, onReload)
                transcript.messages.isEmpty() -> EmptyBlock("还没有消息。发一句话开始。")
                else -> MessageList(transcript.messages, pane, onQuote)
            }
        }
    }
}

@Composable
private fun BlockedNotice() {
    val palette = LocalHerdrPalette.current
    Surface(color = palette.statusBg(AgentStatus.BLOCKED), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Space.page, vertical = Space.s)) {
            Text(
                "需要你：等待下一步指令",
                style = MaterialTheme.typography.labelLarge,
                color = palette.statusFg(AgentStatus.BLOCKED),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "结构化审批尚未接入；请先查看对话内容再回复。",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }
    }
}

/** UX-03e：阅读位置状态（真实手势驱动，不用 index 猜测）。 */
private enum class ReadMode { FollowingLatest, BrowsingHistory, RestoringAnchor, Empty }

@Composable
private fun MessageList(messages: List<ChatMessage>, pane: Pane, onQuote: (String) -> Unit) {
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
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
                    is ConversationItem.Tool -> ToolRow(item)
                    is ConversationItem.Unknown -> CollapsibleRow("未知内容", item.text, muted = true)
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

/** toolCall + toolResult：名称、执行状态、参数摘要、输出按需展开（B4）。 */
@Composable
private fun ToolRow(item: ConversationItem.Tool) {
    val palette = LocalHerdrPalette.current
    var expanded by remember(item.index, item.result) { mutableStateOf(false) }
    val hasResult = item.result != null
    // UX-03c：结果"已返回"≠"执行成功"；也不从输出里的 error 字样盲猜失败。
    // 只如实区分「还没结果 / 已返回」，错误关键字仅作为低强调提示。
    val statusColor = if (hasResult) palette.textSecondary else palette.statusFg(AgentStatus.WORKING)
    val statusText = if (hasResult) "已返回" else "运行中"

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { expanded = !expanded }
                .semantics {
                    contentDescription =
                        "工具 ${item.name}，$statusText，${if (expanded) "已展开" else "已折叠"}，点击切换"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisclosureIndicator(expanded)
            Spacer(Modifier.width(Space.xs))
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Space.s))
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
            if (!item.pairedById && hasResult) {
                Spacer(Modifier.width(Space.s))
                Text("未配对（无可靠 ID）", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            if (item.resultError && hasResult) {
                Spacer(Modifier.width(Space.s))
                Text("输出含 error 关键字", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        if (item.argumentsSummary.isNotBlank()) {
            Text(
                item.argumentsSummary.replace('\n', ' '),
                style = MonoStyle,
                color = palette.textSecondary,
                maxLines = if (expanded) 6 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.l, end = Space.s, bottom = Space.xs),
            )
        }
        AnimatedVisibility(visible = expanded) {
            val body = item.result
            if (body == null) {
                Text(
                    "还没有结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = Space.l, top = Space.xs, bottom = Space.xs),
                )
            } else {
                Surface(
                    color = palette.surfaceRaised,
                    shape = RoundedCornerShape(Radius.code),
                    modifier = Modifier.fillMaxWidth().padding(start = Space.l).heightIn(max = 260.dp),
                ) {
                    Box(Modifier.padding(Space.m).verticalScroll(rememberScrollState())) {
                        Text(
                            body,
                            style = MonoStyle,
                            color = palette.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessage(text: String) {
    val palette = LocalHerdrPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = palette.surfaceRaised,
            shape = RoundedCornerShape(Radius.card),
            modifier = Modifier.fillMaxWidth(0.86f),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            )
        }
    }
}

/**
 * assistant 正文：Markdown 子集渲染（标题/列表/引用/行内样式/代码块）。
 * 超长消息折叠预览；解析在 [MarkdownText] 内部的后台线程完成。
 */
@Composable
private fun AssistantMessage(text: String, onQuote: (String) -> Unit) {
    val palette = LocalHerdrPalette.current
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val collapsible = text.length > 2500 || lineCount > 40
    var expanded by remember(text) { mutableStateOf(!collapsible) }
    Column(Modifier.fillMaxWidth()) {
        // CONV-ACTIONS-001：正文支持系统长按选择（选区不被父级手势拦截）
        androidx.compose.foundation.text.selection.SelectionContainer {
            MarkdownText(
                source = if (expanded || !collapsible) text else cutAtLine(text, 1200),
                modifier = Modifier.fillMaxWidth(),
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

/**
 * CONV-ACTIONS-001（已批准）：Agent 消息末尾常显低强调「复制 + 更多」。
 * - 复制只取可阅读正文（源的 Markdown 原文），不含 Agent 名/时间/按钮字样；
 * - 更多菜单只放**真正实现**的动作，不放 Quote/Share 占位。
 */
@Composable
private fun MessageActions(text: String, onQuote: (String) -> Unit) {
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
                Text(stringResource(R.string.action_more), style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
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
private fun cutAtLine(text: String, max: Int): String {
    if (text.length <= max) return text
    val nl = '\n'
    val tick = '`'
    val cut = text.lastIndexOf(nl, max).takeIf { it > max / 2 } ?: max
    val head = text.take(cut)
    val fences = head.count { it == tick } / 3
    val suffix = if (fences % 2 == 1) "$nl$nl…$nl$tick$tick$tick" else "$nl$nl…"
    return head + suffix
}

/**
 * Agent 分组表头：连续多条 Agent 内容共享一个标识。
 * 未知 agent 时用中性「Agent」，不编造产品名。
 */
@Composable
private fun AgentGroupHeader(pane: Pane) {
    val palette = LocalHerdrPalette.current
    val name = pane.agent?.takeIf { it.isNotBlank() }?.let {
        // 已知 agent 名保留原样（pi / claude），但使用中性色，不再用状态绿当装饰
        it.replaceFirstChar { c -> c.uppercase() }
    } ?: "Agent"
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        color = palette.textSecondary,
        modifier = Modifier.padding(top = Space.xs),
    )
}

/** thinking / tool：默认折叠，一行摘要。 */
@Composable
private fun CollapsibleRow(title: String, body: String, mono: Boolean = false, muted: Boolean = false) {
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

@Composable
private fun TerminalFallback(output: String, onReload: () -> Unit) {
    val palette = LocalHerdrPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "没有结构化会话（agent_session 未上报），下面是终端输出",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReload) { Text("刷新") }
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(horizontal = Space.page)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                output.ifBlank { "（暂无输出）" },
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = palette.textPrimary,
                modifier = Modifier.padding(bottom = Space.xxl),
            )
        }
    }
}

@Composable
private fun ComposerBar(
    composer: HostConnectionManager.ComposerState,
    pane: Pane,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissOutcome: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    // UX-04a（CMP-STYLE-001 暂定）：轻量双层 Surface —— 外层是页面底色，
    // 输入容器用 surface + 20dp 圆角（窄屏 18dp），Send 是唯一 Accent 操作。
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val gutter = if (compact) ComposerGutter.compact else ComposerGutter.normal
    val radius = if (compact) 18.dp else 20.dp
    var longEditor by remember(pane.paneId) { mutableStateOf(false) }
    var confirmResend by remember { mutableStateOf(false) }
    val hasAgent = pane.agent != null
    val canSend = hasAgent && !composer.sending && composer.text.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = gutter, vertical = Space.s),
    ) {
        // 发送结果：Accepted / Rejected / Unknown 三态文案不同，TalkBack 也读得到
        composer.message?.let { msg ->
            val color = when (composer.outcome) {
                is WriteOutcome.Accepted -> palette.statusFg(AgentStatus.DONE)
                is WriteOutcome.Rejected -> palette.statusFg(AgentStatus.BLOCKED)
                is WriteOutcome.Unknown -> palette.statusFg(AgentStatus.UNKNOWN)
                else -> palette.textSecondary
            }
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissOutcome, modifier = Modifier.heightIn(min = 48.dp)) { Text("知道了") }
            }
        }

        // Unknown：保留待确认文本，重发必须显式确认（会产生新的 Action ID）
        composer.pendingText?.let { pending ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待确认：${pending.lineSequence().first().take(40)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.statusFg(AgentStatus.UNKNOWN),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { confirmResend = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("重新发送…") }
            }
        }

        Surface(color = palette.surface, shape = RoundedCornerShape(radius)) {
            Row(
                Modifier.padding(start = Space.m, end = Space.xs, top = Space.xs, bottom = Space.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 140.dp)
                        .semantics {
                            contentDescription = "指令输入框"
                            stateDescription = when {
                                composer.sending -> "发送中"
                                composer.text.isBlank() -> "空"
                                else -> "${composer.text.length} 个字符"
                            }
                        },
                    placeholder = {
                        Text(
                            if (!hasAgent) stringResource(R.string.composer_no_agent) else stringResource(R.string.composer_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    // 1–2 行起步，最多约 5 行后内部滚动；Enter 插入换行（不自动发送）
                    minLines = 1,
                    maxLines = 5,
                    enabled = hasAgent && !composer.sending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    ),
                )
                TouchTargetIconButton(
                    iconRes = dev.herdr.feature.board.R.drawable.ic_fullscreen,
                    contentDescription = "打开长 Prompt 编辑",
                    onClick = { longEditor = true },
                )
                Surface(
                    color = if (canSend) palette.accent else palette.outline,
                    shape = RoundedCornerShape(Radius.chip),
                    modifier = Modifier
                        .padding(Space.xs)
                        .heightIn(min = 48.dp)
                        .widthIn(min = 64.dp)
                        .clickable(enabled = canSend, onClick = onSend)
                        .semantics { contentDescription = if (composer.sending) "发送中，请稍候" else "发送" },
                ) {
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (composer.sending) stringResource(R.string.composer_sending) else stringResource(R.string.composer_send),
                            color = palette.accentOn,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Space.m),
                        )
                    }
                }
            }
        }
        Text(
            "已接受（Accepted）只代表远端收到，不代表执行完成；结果未知（Unknown）不会自动重发。",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = Space.xs),
        )
    }

    if (longEditor) {
        var snapshot by remember { mutableStateOf(composer.text) }
        LongPromptEditor(
            text = composer.text,
            onChange = onTextChange,
            onCancel = {
                // UX-04b：取消回退到打开编辑前的草稿内容（不发送、不保留半成品）
                onTextChange(snapshot)
                longEditor = false
            },
            onDismiss = { longEditor = false },   // 「完成」只写回本地草稿，绝不触发发送
        )
    }

    if (confirmResend) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResend = false },
            title = { Text("重新发送待确认内容？") },
            text = {
                Text(
                    "上一次发送结果未知，远端可能已经执行。重新发送会创建新的操作记录，并可能导致重复执行。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmResend = false
                    onSend()
                }) { Text("确认重新发送") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResend = false }) { Text("取消") }
            },
        )
    }
}

/** 长 Prompt 编辑：全屏纯文本编辑，粘贴代码/堆栈不挤压列表。 */
@Composable
private fun LongPromptEditor(
    text: String,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = palette.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.composer_cancel)) }
                    Text(stringResource(R.string.composer_long_prompt), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.composer_done)) }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Space.page),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("粘贴代码、错误堆栈或长指令…") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    ),
                )
                Text(
                    "${text.length} 字符（草稿会自动保存）",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(Space.page),
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(Space.s))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ErrorBlock(error: String, onReload: () -> Unit) {
    val palette = LocalHerdrPalette.current
    Column(Modifier.fillMaxSize().padding(Space.xxl), verticalArrangement = Arrangement.Center) {
        Text("读取失败", style = MaterialTheme.typography.titleMedium, color = palette.statusFg(AgentStatus.BLOCKED))
        Spacer(Modifier.height(Space.s))
        Text(error, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        Spacer(Modifier.height(Space.m))
        TextButton(onClick = onReload) { Text("重试") }
    }
}

@Composable
private fun EmptyBlock(text: String) {
    val palette = LocalHerdrPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
    }
}
