package io.github.luxiang0412.kelpie.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import io.github.luxiang0412.kelpie.conversation.model.ConversationSearch
import io.github.luxiang0412.kelpie.core.herdr.ToolCalls
import io.github.luxiang0412.kelpie.core.herdr.t

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.Durations
import io.github.luxiang0412.kelpie.core.data.runtime.Receipt
import io.github.luxiang0412.kelpie.core.data.runtime.SendRecord
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
import io.github.luxiang0412.kelpie.core.designsystem.Dot
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieChip
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIconButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSize
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSpace
import io.github.luxiang0412.kelpie.home.QuickReplies
import io.github.luxiang0412.kelpie.home.clock
import io.github.luxiang0412.kelpie.core.markdown.MarkdownText
import io.github.luxiang0412.kelpie.ui.BackTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationRoute(hostId: String, paneId: String, prefill: String?, onBack: () -> Unit, openTerminal: Boolean = false, vm: ConversationViewModel = viewModel()) {
    remember(hostId, paneId) { vm.start(hostId, paneId, prefill); paneId } // 幂等；必须在读取下面的 flow 之前（同步执行，所以不用 LaunchedEffect）
    val item by vm.item.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val terminal by vm.terminal.collectAsStateWithLifecycle()
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val answer by vm.answer.collectAsStateWithLifecycle()
    var answering by remember { mutableStateOf<ConversationItem.Tool?>(null) }
    var answerBlocked by remember { mutableStateOf<String?>(null) }
    // 从首页提问卡片的“看终端”进来：直接拉起终端（只读）
    var showTerminal by rememberSaveable { mutableStateOf(openTerminal) }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (openTerminal) vm.refreshTerminal() }
    var menu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchPos by rememberSaveable { mutableStateOf(0) }
    var fullTool by remember { mutableStateOf<ConversationItem.Tool?>(null) }
    var copying by remember { mutableStateOf<ConversationItem?>(null) }
    val context = LocalContext.current
    val c = Kelpie.colors
    val now = remember(items, item) { System.currentTimeMillis() }
    // 搜索命中：从最新往旧排（和列表倒序一致），第一个命中就是离底部最近的那条
    val matches = remember(items, query) { if (query.isBlank()) emptyList() else ConversationSearch.matches(items, query).asReversed() }
    val current = matches.getOrNull(matchPos.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        if (searching) {
            SearchBar(
                query = query,
                onQuery = { query = it; matchPos = 0 },
                count = matches.size,
                pos = matchPos,
                onPrev = { if (matches.isNotEmpty()) matchPos = (matchPos + 1) % matches.size }, // 更旧的
                onNext = { if (matches.isNotEmpty()) matchPos = (matchPos - 1 + matches.size) % matches.size }, // 更新的
                onClose = { searching = false; query = ""; matchPos = 0 },
            )
        } else BackTopBar(
            title = item?.title ?: paneId,
            onBack = onBack,
            subtitle = item?.let { statusLine(it, now) } ?: hostId,
            actions = {
                KelpieIconButton(KelpieIcons.Terminal, t("查看终端", "View terminal"), { vm.refreshTerminal(); showTerminal = true }, tint = c.textPrimary)
                Box {
                    KelpieIconButton(KelpieIcons.More, t("更多", "More"), { menu = true }, tint = c.textPrimary)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                        DropdownMenuItem(
                            text = { Text(t("在对话中搜索", "Search in conversation"), style = Kelpie.type.body, color = if (items.isNotEmpty()) c.textPrimary else c.textTertiary) },
                            leadingIcon = { Icon(KelpieIcons.Search, null, tint = c.iconMuted, modifier = Modifier.size(18.dp)) },
                            enabled = items.isNotEmpty(),
                            onClick = { menu = false; searching = true },
                        )
                        val path = (source as? Source.Transcript)?.path
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(t("复制会话文件路径", "Copy session file path"), style = Kelpie.type.body, color = if (path != null) c.textPrimary else c.textTertiary)
                                    if (path == null) Text(t("这个 agent 没有会话文件", "This agent has no session file"), style = Kelpie.type.caption, color = c.textTertiary)
                                }
                            },
                            leadingIcon = { Icon(KelpieIcons.Copy, null, tint = c.iconMuted, modifier = Modifier.size(18.dp)) },
                            enabled = path != null,
                            onClick = { menu = false; path?.let { copyText(context, t("会话文件路径", "Session file path"), it) } },
                        )
                        DropdownMenuItem(
                            text = { Text(t("复制工作目录", "Copy working directory"), style = Kelpie.type.body, color = c.textPrimary) },
                            leadingIcon = { Icon(KelpieIcons.File, null, tint = c.iconMuted, modifier = Modifier.size(18.dp)) },
                            enabled = item?.pane?.cwd != null,
                            onClick = { menu = false; item?.pane?.cwd?.let { copyText(context, t("工作目录", "Working directory"), it) } },
                        )
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth().padding(0.dp).background(c.hairline).heightIn(min = 1.dp, max = 1.dp))

        Box(Modifier.weight(1f)) {
            when (val s = source) {
                is Source.TerminalOnly -> TerminalBody(terminal, s.reason)
                else -> MessageList(
                    items, item, receipt, now,
                    truncated = (s as? Source.Transcript)?.truncated == true,
                    loading = s is Source.Locating,
                    highlight = current,
                    onFullTool = { fullTool = it },
                    onCopy = { copying = it },
                    answer = answer,
                    // 只有 Claude、在线、而且是最后一道还没答的选择题，才能在手机上回答
                    onAnswer = if (online && item?.lastSeen != true && item?.pane?.agent?.startsWith("claude") == true) { tool -> answerBlocked = null; answering = tool } else null,
                    onTerminal = { vm.refreshTerminal(); showTerminal = true },
                    onDismissAnswer = vm::dismissAnswer,
                )
            }
        }

        Composer(
            text = text,
            onText = vm::setText,
            onSend = { vm.send() },
            enabled = online && item?.lastSeen != true,
            placeholder = if (!online) t("联网后才能发送 · 草稿会保留", "Can't send while offline · draft is kept") else t("回复 ${item?.title ?: ""}…", "Reply to ${item?.title ?: ""}…"),
            // 卡在一道选择题上时“继续 / 好的”没有意义（发进选择框只会被当成按键）
            quick = if (item?.status == AgentStatus.BLOCKED && online && !pendingQuestion(items)) QuickReplies else emptyList(),
            onQuick = vm::applyQuick,
            sending = receipt?.receipt == Receipt.Sending,
        )
    }

    blocked?.let { b ->
        if (b.needsConfirm) {
            AlertDialog(
                onDismissRequest = vm::dismissBlocked,
                containerColor = c.surface,
                title = { Text(t("目标变了", "Target changed"), style = Kelpie.type.headline, color = c.textPrimary) },
                text = { Text(b.message + t("。确定还要发给它吗？", ". Send it anyway?"), style = Kelpie.type.body, color = c.textSecondary) },
                confirmButton = { TextButton(onClick = { vm.send(confirmed = true) }) { Text(t("仍然发送", "Send anyway"), color = c.textPrimary) } },
                dismissButton = { TextButton(onClick = vm::dismissBlocked) { Text(t("取消", "Cancel"), color = c.textSecondary) } },
            )
        }
    }

    fullTool?.let { tool -> FullToolOutput(tool, onDismiss = { fullTool = null }) }
    copying?.let { m -> MessageTextSheet(m, onDismiss = { copying = null }) }

    answering?.let { tool ->
        AnswerSheet(
            questions = tool.questions,
            subtitle = listOfNotNull(item?.title, item?.agentName).joinToString(" · "),
            onDismiss = { answering = null },
            onSubmit = { replies ->
                val b = vm.answerQuestions(tool, replies)
                if (b == null) answering = null else answerBlocked = b.message
            },
        )
        answerBlocked?.let { msg ->
            AlertDialog(
                onDismissRequest = { answerBlocked = null },
                containerColor = c.surface,
                title = { Text(t("没有提交", "Not submitted"), style = Kelpie.type.headline, color = c.textPrimary) },
                text = { Text(msg, style = Kelpie.type.body, color = c.textSecondary) },
                confirmButton = { TextButton(onClick = { answerBlocked = null }) { Text(t("知道了", "OK"), color = c.textPrimary) } },
            )
        }
    }

    if (showTerminal) {
        ModalBottomSheet(
            onDismissRequest = { showTerminal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
            shape = RoundedCornerShape(topStart = KelpieRadius.sheet, topEnd = KelpieRadius.sheet),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("终端 · 最近 120 行", "Terminal · last 120 lines"), style = Kelpie.type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
                    KelpieChip(t("刷新", "Refresh"), onClick = vm::refreshTerminal, container = c.surfaceSubtle, height = KelpieSize.chipSmall)
                }
                Text(t("只读。需要在终端里操作时请回到电脑。", "Read-only. Use the computer to interact with the terminal."), style = Kelpie.type.caption, color = c.textSecondary)
                TerminalText(terminal, Modifier.fillMaxWidth().heightIn(max = 520.dp))
            }
        }
    }
}

private fun statusLine(it: AgentItem, now: Long): String {
    val st = when {
        it.lastSeen -> t("最后看到", "Last seen")
        it.status == AgentStatus.BLOCKED -> t("在等你", "Waiting for you")
        it.status == AgentStatus.WORKING -> t("工作中", "Working")
        it.status == AgentStatus.DONE -> t("已完成", "Done")
        it.status == AgentStatus.IDLE -> t("空闲", "Idle")
        else -> t("状态未知", "Status unknown")
    }
    return "$st · ${it.agentName} · ${it.hostLabel}"
}

@Composable
internal fun MessageList(
    items: List<ConversationItem>,
    agent: AgentItem?,
    receipt: SendRecord?,
    now: Long,
    truncated: Boolean,
    loading: Boolean,
    highlight: Int? = null,
    onFullTool: (ConversationItem.Tool) -> Unit = {},
    /** 长按消息：打开全文（可复制 / 选择）。 */
    onCopy: (ConversationItem) -> Unit = {},
    answer: io.github.luxiang0412.kelpie.core.data.runtime.AnswerRecord? = null,
    onAnswer: ((ConversationItem.Tool) -> Unit)? = null,
    onTerminal: () -> Unit = {},
    onDismissAnswer: () -> Unit = {},
) {
    val c = Kelpie.colors
    val state = rememberLazyListState()
    // 倒序布局：最新的在底部，新消息到来时如果用户停在底部就保持贴底
    val reversed = remember(items) { items.asReversed() }
    val lastUserIdx = remember(items) { items.indexOfLast { it is ConversationItem.User } }
    val lastToolIndex = remember(items) { items.lastOrNull { it is ConversationItem.Tool }?.index }
    LaunchedEffect(highlight) {
        val h = highlight ?: return@LaunchedEffect
        val pos = reversed.indexOfFirst { it.index == h }
        if (pos < 0) return@LaunchedEffect
        // 消息前面还有“状态”一项，可能还有“发送中”一项
        val head = state.layoutInfo.visibleItemsInfo.count { it.key == "status" || it.key == "pending-send" }.coerceAtLeast(1)
        state.animateScrollToItem(pos + head)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = KelpieSpace.pageConversation, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        item(key = "status") { LiveStatus(agent, now, asking = pendingQuestion(items)) }
        // 回执挂在最后一条用户消息下面；如果那条还没出现在会话文件里，先单独显示
        if (receipt != null && (lastUserIdx < 0 || (items[lastUserIdx] as ConversationItem.User).text.trim() != receipt.text)) {
            item(key = "pending-send") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserBubble(receipt.text)
                    ReceiptLine(receipt)
                }
            }
        }
        items(reversed, key = { "m" + it.index }, contentType = { it::class }) { m ->
            val hl = if (m.index == highlight) Modifier.border(2.dp, c.attention, RoundedCornerShape(KelpieRadius.m)).padding(4.dp) else Modifier
            Box(hl.then(if (copyText(m) != null) Modifier.longPressCopy { onCopy(m) } else Modifier)) { when (m) {
                is ConversationItem.User -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserBubble(m.text)
                    if (m.index == items.getOrNull(lastUserIdx)?.index && receipt != null && m.text.trim() == receipt.text) ReceiptLine(receipt)
                }
                is ConversationItem.Assistant -> MarkdownText(m.text, Modifier.fillMaxWidth())
                is ConversationItem.Thinking -> ThinkingRow(m.text)
                is ConversationItem.Tool -> if (m.questions.isNotEmpty()) {
                    val last = m.result == null && m.index == lastToolIndex
                    AskQuestionCard(m, answer, onAnswer = if (last && onAnswer != null) ({ onAnswer(m) }) else null, onTerminal = onTerminal, onDismissAnswer = onDismissAnswer)
                } else ToolCard(m, cwd = agent?.pane?.cwd, onFull = { onFullTool(m) })
                is ConversationItem.Unknown -> Text(m.text, style = Kelpie.type.caption, color = c.textTertiary)
            } }
        }
        if (truncated) item(key = "truncated") {
            Text(t("更早的消息没有加载（只保留最近一段）", "Older messages not loaded (only the recent part is kept)"), style = Kelpie.type.caption, color = c.textTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        if (loading && items.isEmpty()) item(key = "loading") {
            Text(t("正在读取会话…", "Loading conversation…"), style = Kelpie.type.meta, color = c.textSecondary)
        }
    }
}

@Composable
private fun LiveStatus(agent: AgentItem?, now: Long, asking: Boolean = false) {
    val c = Kelpie.colors
    agent ?: return
    val (color, text) = when {
        agent.lastSeen -> c.idle to t("连不上主机 · 下面是最后看到的内容", "Host unreachable · showing what was last seen")
        agent.status == AgentStatus.BLOCKED -> c.attention to t("在等你回答 · ", "Waiting for your answer · ") + Durations.waited(agent.sinceMs, agent.sinceIsLowerBound, now) +
            // 选择题开着时发消息会被终端当成按键
            if (asking) t(" · 先在终端里答完选择题再发消息", " · answer the question in the terminal before sending") else ""
        agent.status == AgentStatus.WORKING -> c.working to t("正在工作 · ", "Working · ") + Durations.waited(agent.sinceMs, agent.sinceIsLowerBound, now)
        else -> return
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(color, 6.dp)
        Text(text, style = Kelpie.type.metaStrong.copy(fontSize = Kelpie.type.caption.fontSize), color = if (agent.status == AgentStatus.BLOCKED) c.attentionText else c.textSecondary)
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = Kelpie.colors
    Text(
        text,
        style = Kelpie.type.body,
        color = c.textPrimary,
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp))
            .background(c.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** 规范 5.12：四种回执。Unknown 不给“重试”。 */
@Composable
private fun ReceiptLine(r: SendRecord) {
    val c = Kelpie.colors
    val (text, color) = when (val x = r.receipt) {
        Receipt.Sending -> t("发送中…", "Sending…") to c.textTertiary
        is Receipt.Accepted -> t("已送达 · ${clock(x.atMs)}", "Delivered · ${clock(x.atMs)}") to c.textTertiary
        is Receipt.Rejected -> t("没有发出：${x.message} · 内容还在输入框里", "Not sent: ${x.message} · text is still in the box") to c.error
        is Receipt.Unknown -> t("无法确认是否送达，不会自动重发 · 看看终端再决定", "Couldn't confirm delivery; won't resend automatically · check the terminal first") to c.warning
    }
    Text(text, style = Kelpie.type.micro, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun ThinkingRow(text: String) {
    val c = Kelpie.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.m)).clickable { open = !open }.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (open) KelpieIcons.ChevronDown else KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(14.dp))
            Text(t("思考过程", "Thinking"), style = Kelpie.type.meta, color = c.textSecondary)
        }
        if (open) Text(text, style = Kelpie.type.meta, color = c.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
    }
}

@Composable
private fun ToolCard(t: ConversationItem.Tool, cwd: String?, onFull: () -> Unit) {
    val c = Kelpie.colors
    var open by rememberSaveable(t.index) { mutableStateOf(false) }
    // 有一句话说明就显示说明；没有就显示命令，去掉开头指向会话目录的 `cd … &&`
    val command = remember(t.argumentsSummary, cwd) { ToolCalls.stripCwd(t.argumentsSummary, cwd, homeOf(cwd)) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(KelpieRadius.l))
            .border(1.dp, c.outlineCard, RoundedCornerShape(KelpieRadius.l))
            .clickable { open = !open }
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(if (t.name.contains("edit", true) || t.name.contains("write", true)) KelpieIcons.File else KelpieIcons.Prompt, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
            Text(
                t.title ?: command.ifBlank { t.name },
                style = if (t.title != null) Kelpie.type.meta else Kelpie.type.codeSmall,
                color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            when {
                t.result == null -> Text(t("运行中", "Running"), style = Kelpie.type.caption, color = c.workingText)
                t.resultError -> Text(t("出错", "Error"), style = Kelpie.type.caption, color = c.error)
                else -> Icon(KelpieIcons.Check, t("完成", "Done"), tint = c.done, modifier = Modifier.size(14.dp))
            }
        }
        // 标题是说明时，展开后先给出命令本身（完整的，不去 cd）
        if (open && t.title != null && t.argumentsSummary.isNotBlank()) {
            Text(
                t.argumentsSummary,
                style = Kelpie.type.codeSmall, color = c.textPrimary, maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(c.cardHeaderBg).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (open && t.result != null) {
            Text(
                t.result.take(PREVIEW_CHARS),
                style = Kelpie.type.codeSmall,
                color = if (t.resultError) c.diffDelFg else c.textSecondary,
                modifier = Modifier.fillMaxWidth().background(c.cardHeaderBg).padding(horizontal = 12.dp, vertical = 8.dp)
                    .heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = t("查看完整输出", "View full output"), onClick = onFull)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(KelpieIcons.Expand, null, tint = c.iconMuted, modifier = Modifier.size(14.dp))
                Text(
                    if (t.result.length > PREVIEW_CHARS) t("完整输出（共 ${t.result.lines().size} 行）", "Full output (${t.result.lines().size} lines)")
                    else t("全屏查看 · 复制", "Full screen · copy"),
                    style = Kelpie.type.caption, color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    placeholder: String,
    quick: List<String>,
    onQuick: (String) -> Unit,
    sending: Boolean,
) {
    val c = Kelpie.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (quick.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { q -> KelpieChip(q, onClick = { onQuick(q) }, container = c.surface, border = c.outline, height = KelpieSize.chipSmall) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = KelpieSize.composerMin).clip(RoundedCornerShape(26.dp)).background(c.surfaceSubtle)
                .padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (text.isEmpty()) Text(placeholder, style = Kelpie.type.body, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    textStyle = Kelpie.type.body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.textPrimary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                )
            }
            val can = enabled && text.isNotBlank() && !sending
            // 多行时贴底：和最后一行对齐，不悬在输入框中间（单行时最小高度刚好等于按钮，位置不变）
            Box(
                Modifier.align(Alignment.Bottom).size(KelpieSize.sendButton).clip(CircleShape).background(if (can) c.primary else c.outline)
                    .clickable(enabled = can, onClickLabel = t("发送", "Send"), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(KelpieIcons.Send, t("发送", "Send"), tint = if (can) c.onPrimary else c.textTertiary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun TerminalBody(terminal: String?, reason: String) {
    val c = Kelpie.colors
    Column(Modifier.fillMaxSize().padding(horizontal = KelpieSpace.pageConversation, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("$reason，这里显示终端的最近内容（只读）。", "$reason. Showing recent terminal output (read-only)."), style = Kelpie.type.meta, color = c.textSecondary)
        TerminalText(terminal, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun TerminalText(terminal: String?, modifier: Modifier) {
    val c = Kelpie.colors
    val scroll = rememberScrollState()
    LaunchedEffect(terminal) { scroll.scrollTo(scroll.maxValue) }
    Box(modifier.clip(RoundedCornerShape(KelpieRadius.l)).background(c.codeBlockBg)) {
        Text(
            terminal?.trimEnd() ?: t("读取中…", "Loading…"),
            style = Kelpie.type.codeSmall.copy(fontSize = Kelpie.type.micro.fontSize),
            color = c.codeBlockFg,
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
    Spacer(Modifier.size(0.dp))
}

private const val PREVIEW_CHARS = 4_000

/** 从工作目录推出家目录（`cd ~/…` 也算回到会话目录）：/home/<user> 或 /root。 */
private fun homeOf(cwd: String?): String? = cwd?.let { Regex("^(/home/[^/]+|/root)(?=/|$)").find(it)?.value }

/** 能长按复制的消息的原文（Assistant 是 Markdown 源码）；工具卡片点开有自己的复制。 */
private fun copyText(m: ConversationItem): String? = when (m) {
    is ConversationItem.User -> m.text
    is ConversationItem.Assistant -> m.text
    is ConversationItem.Thinking -> m.text
    is ConversationItem.Unknown -> m.text
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * 长按 → [onLongPress]。不用 combinedClickable：它会把整条消息变成一个可点的按钮（涟漪、读屏念“按钮”），
 * 还会抢走消息里代码块“复制”、思考过程展开这些子控件的点击。读屏用户通过自定义操作“复制”拿到同样的功能。
 */
@Composable
private fun Modifier.longPressCopy(onLongPress: () -> Unit): Modifier {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val label = t("复制", "Copy")
    return this
        .pointerInput(onLongPress) {
            detectTapGestures(onLongPress = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onLongPress()
            })
        }
        .semantics { onLongClick(label) { onLongPress(); true } }
}

/** 一条消息的全文：顶部“复制全部”，正文可以选择一部分复制。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageTextSheet(m: ConversationItem, onDismiss: () -> Unit) {
    val c = Kelpie.colors
    val context = LocalContext.current
    val text = copyText(m) ?: return
    val title = when (m) {
        is ConversationItem.User -> t("你的消息", "Your message")
        is ConversationItem.Thinking -> t("思考过程", "Thinking")
        is ConversationItem.Assistant -> t("回复", "Reply")
        else -> t("说明", "Note")
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = KelpieRadius.sheet, topEnd = KelpieRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Kelpie.type.headline, color = c.textPrimary)
                    Text(t("长按文字可以只选一部分", "Long-press the text to select part of it"), style = Kelpie.type.caption, color = c.textSecondary)
                }
                KelpieChip(t("复制全部", "Copy all"), onClick = { copyText(context, title, text); onDismiss() }, container = c.surfaceSubtle, height = KelpieSize.chipSmall)
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(KelpieRadius.l)).background(c.surfaceSubtle)) {
                SelectionContainer {
                    Text(
                        text.take(200_000) + if (text.length > 200_000) t("\n…（只显示前 200 KB，复制全部可得全文）", "\n… (first 200 KB shown; Copy all for the full text)") else "",
                        style = Kelpie.type.body,
                        color = c.textPrimary,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ 系统自己会提示“已复制”
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, t("已复制", "Copied"), Toast.LENGTH_SHORT).show()
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    pos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Kelpie.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(KelpieSize.topBarConversation).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KelpieIconButton(KelpieIcons.Close, t("关闭搜索", "Close search"), onClose)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(t("搜索这段对话", "Search this conversation"), style = Kelpie.type.body, color = c.textTertiary)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Kelpie.type.body.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onPrev() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = t("搜索这段对话", "Search this conversation") },
            )
        }
        if (query.isNotBlank()) {
            Text(
                if (count == 0) t("无结果", "No results") else "${pos + 1}/$count",
                style = Kelpie.type.caption, color = c.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val navTint = if (count > 0) c.textPrimary else c.textTertiary
        KelpieIconButton(KelpieIcons.ChevronDown, t("更早的结果", "Older match"), onPrev, tint = navTint, modifier = Modifier.rotate(180f))
        KelpieIconButton(KelpieIcons.ChevronDown, t("更新的结果", "Newer match"), onNext, tint = navTint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullToolOutput(tool: ConversationItem.Tool, onDismiss: () -> Unit) {
    val c = Kelpie.colors
    val context = LocalContext.current
    val result = tool.result.orEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = KelpieRadius.sheet, topEnd = KelpieRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(tool.title ?: tool.name, style = Kelpie.type.headline, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        t("${result.lines().size} 行 · ${result.length} 字符", "${result.lines().size} lines · ${result.length} chars"),
                        style = Kelpie.type.caption, color = c.textSecondary,
                    )
                }
                KelpieChip(t("复制", "Copy"), onClick = { copyText(context, tool.name, result) }, container = c.surfaceSubtle, height = KelpieSize.chipSmall)
            }
            if (tool.argumentsSummary.isNotBlank()) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.argumentsSummary, style = Kelpie.type.codeSmall, color = c.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    KelpieChip(t("复制命令", "Copy command"), onClick = { copyText(context, tool.name, tool.argumentsSummary) }, container = c.surfaceSubtle, height = KelpieSize.chipSmall)
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(KelpieRadius.l)).background(c.codeBlockBg)) {
                // 很长的输出（上 MB）只渲染前 200 KB，复制按钮给的是全文
                SelectionContainer {
                    Text(
                        result.take(200_000) + if (result.length > 200_000) t("\n…（只显示前 200 KB，复制可得全文）", "\n… (first 200 KB shown; copy for the full text)") else "",
                        style = Kelpie.type.codeSmall.copy(fontSize = Kelpie.type.micro.fontSize),
                        color = if (tool.resultError) c.diffDelFg else c.codeBlockFg,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        }
    }
}

/** 最后一个工具条目是还没回答的 AskUserQuestion。 */
internal fun pendingQuestion(items: List<ConversationItem>): Boolean =
    (items.lastOrNull { it is ConversationItem.Tool } as? ConversationItem.Tool)?.let { it.questions.isNotEmpty() && it.result == null } == true
