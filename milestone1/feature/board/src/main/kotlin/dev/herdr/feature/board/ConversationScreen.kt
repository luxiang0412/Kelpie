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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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
                        Text("看板", style = MaterialTheme.typography.labelLarge)
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
                        Text("更多", style = MaterialTheme.typography.labelLarge)
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
            when {
                !hasTranscript -> TerminalFallback(output, onReload)
                transcript.loading -> LoadingBlock("读取会话…")
                transcript.error != null -> ErrorBlock(transcript.error, onReload)
                transcript.messages.isEmpty() -> EmptyBlock("还没有消息。发一句话开始。")
                else -> MessageList(transcript.messages, pane)
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

@Composable
private fun MessageList(messages: List<ChatMessage>, pane: Pane) {
    val palette = LocalHerdrPalette.current
    // 增量映射：分支解析 → 增量映射（历史条目复用；会话 key 变化时清空派生状态）
    val effective = remember(messages) { ConversationBranch.effective(messages) }
    val mapper = remember(pane.paneId) { IncrementalConversationMapper() }
    val sessionKey = "${pane.terminalId}|${pane.agentSessionPath}"
    val items = remember(effective, sessionKey) { mapper.update(effective, sessionKey) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pendingNew by remember(pane.paneId) { mutableStateOf(false) }
    var initialised by remember(pane.paneId) { mutableStateOf(false) }

    // 首次进入定位到最新；之后仅在用户处于底部附近时跟随，
    // 否则提示「新消息」——阅读历史时不被拉回底部（B5）。
    LaunchedEffect(items.size) {
        if (items.isEmpty()) return@LaunchedEffect
        if (!initialised) {
            listState.scrollToItem(items.lastIndex)
            initialised = true
            return@LaunchedEffect
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (items.lastIndex - lastVisible <= 2) {
            listState.animateScrollToItem(items.lastIndex)
        } else {
            pendingNew = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            items(
                items = items,
                key = { "${pane.paneId}-${it.index}" },
                contentType = { it.javaClass.simpleName },
            ) { item ->
                when (item) {
                    is ConversationItem.User -> UserMessage(item.text)
                    is ConversationItem.Assistant -> AssistantMessage(item.text)
                    is ConversationItem.Thinking -> CollapsibleRow("思考过程", item.text, muted = true)
                    is ConversationItem.Tool -> ToolRow(item)
                    is ConversationItem.Unknown -> CollapsibleRow("未知内容", item.text, muted = true)
                }
            }
        }
        if (pendingNew) {
            Surface(
                color = palette.accent,
                shape = RoundedCornerShape(Radius.chip),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.l)
                    .clickable {
                        pendingNew = false
                        scope.launch { listState.animateScrollToItem(items.lastIndex.coerceAtLeast(0)) }
                    }
                    .semantics { contentDescription = "跳到最新消息" },
            ) {
                Text(
                    "↓ 新消息",
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
    val statusColor = when {
        item.resultError -> palette.statusFg(AgentStatus.BLOCKED)
        hasResult -> palette.textSecondary
        else -> palette.statusFg(AgentStatus.WORKING)
    }
    val statusText = when {
        item.resultError -> "失败"
        hasResult -> "已返回"
        else -> "运行中"
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded }
                .semantics {
                    contentDescription =
                        "工具 ${item.name}，$statusText，${if (expanded) "已展开" else "已折叠"}，点击切换"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
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
                            color = if (item.resultError) palette.statusFg(AgentStatus.BLOCKED) else palette.textPrimary,
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
            modifier = Modifier.widthIn(max = 300.dp),
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
private fun AssistantMessage(text: String) {
    val palette = LocalHerdrPalette.current
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val collapsible = text.length > 2500 || lineCount > 40
    var expanded by remember(text) { mutableStateOf(!collapsible) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Pi",
            style = MaterialTheme.typography.labelSmall,
            color = palette.statusFg(AgentStatus.DONE),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(Space.xs))
        MarkdownText(
            source = if (expanded || !collapsible) text else cutAtLine(text, 1200),
            modifier = Modifier.fillMaxWidth(),
        )
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
    }
}

/** 折叠预览在换行处截断，避免把代码围栏切成“未闭合”。 */
private fun cutAtLine(text: String, max: Int): String {
    if (text.length <= max) return text
    val cut = text.lastIndexOf('\n', max).takeIf { it > max / 2 } ?: max
    return text.take(cut) + "\n\n…"
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
            Text(
                if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
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
    var longEditor by remember(pane.paneId) { mutableStateOf(false) }
    var confirmResend by remember { mutableStateOf(false) }
    val hasAgent = pane.agent != null
    val canSend = hasAgent && !composer.sending && composer.text.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.page, vertical = Space.s),
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

        Surface(color = palette.surfaceRaised, shape = RoundedCornerShape(Radius.card)) {
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
                            if (!hasAgent) "这个 pane 没有运行 agent" else "发送下一步指令…",
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
                TextButton(
                    onClick = { longEditor = true },
                    modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
                ) {
                    Text("⤢", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
                }
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
                            if (composer.sending) "发送中" else "发送",
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
        LongPromptEditor(
            text = composer.text,
            onChange = onTextChange,
            onDismiss = { longEditor = false },
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
private fun LongPromptEditor(text: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHerdrPalette.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = palette.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("长 Prompt", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("完成") }
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
