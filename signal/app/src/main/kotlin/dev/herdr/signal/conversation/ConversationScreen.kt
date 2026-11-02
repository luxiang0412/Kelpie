package dev.herdr.signal.conversation

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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.herdr.core.data.attention.AgentItem
import dev.herdr.core.data.attention.Durations
import dev.herdr.core.data.runtime.Receipt
import dev.herdr.core.data.runtime.SendRecord
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.signal.conversation.model.ConversationItem
import dev.herdr.signal.designsystem.Dot
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalChip
import dev.herdr.signal.designsystem.SignalIconButton
import dev.herdr.signal.designsystem.SignalIcons
import dev.herdr.signal.designsystem.SignalRadius
import dev.herdr.signal.designsystem.SignalSize
import dev.herdr.signal.designsystem.SignalSpace
import dev.herdr.signal.home.QuickReplies
import dev.herdr.signal.home.clock
import dev.herdr.signal.markdown.MarkdownText
import dev.herdr.signal.ui.BackTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationRoute(hostId: String, paneId: String, prefill: String?, onBack: () -> Unit, vm: ConversationViewModel = viewModel()) {
    remember(hostId, paneId) { vm.start(hostId, paneId, prefill) } // 幂等；必须在读取下面的 flow 之前
    val item by vm.item.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val terminal by vm.terminal.collectAsStateWithLifecycle()
    val receipt by vm.receipt.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    var showTerminal by rememberSaveable { mutableStateOf(false) }
    val c = Signal.colors
    val now = remember(items, item) { System.currentTimeMillis() }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().imePadding()) {
        BackTopBar(
            title = item?.title ?: paneId,
            onBack = onBack,
            subtitle = item?.let { statusLine(it, now) } ?: hostId,
            actions = {
                SignalIconButton(SignalIcons.Terminal, "查看终端", { vm.refreshTerminal(); showTerminal = true }, tint = c.textPrimary)
            },
        )
        Box(Modifier.fillMaxWidth().padding(0.dp).background(c.hairline).heightIn(min = 1.dp, max = 1.dp))

        Box(Modifier.weight(1f)) {
            when (val s = source) {
                is Source.TerminalOnly -> TerminalBody(terminal, s.reason)
                else -> MessageList(items, item, receipt, now, truncated = (s as? Source.Transcript)?.truncated == true, loading = s is Source.Locating)
            }
        }

        Composer(
            text = text,
            onText = vm::setText,
            onSend = { vm.send() },
            enabled = online && item?.lastSeen != true,
            placeholder = if (!online) "联网后才能发送 · 草稿会保留" else "回复 ${item?.title ?: ""}…",
            quick = if (item?.status == AgentStatus.BLOCKED && online) QuickReplies else emptyList(),
            onQuick = vm::applyQuick,
            sending = receipt?.receipt == Receipt.Sending,
        )
    }

    blocked?.let { b ->
        if (b.needsConfirm) {
            AlertDialog(
                onDismissRequest = vm::dismissBlocked,
                containerColor = c.surface,
                title = { Text("目标变了", style = Signal.type.headline, color = c.textPrimary) },
                text = { Text(b.message + "。确定还要发给它吗？", style = Signal.type.body, color = c.textSecondary) },
                confirmButton = { TextButton(onClick = { vm.send(confirmed = true) }) { Text("仍然发送", color = c.textPrimary) } },
                dismissButton = { TextButton(onClick = vm::dismissBlocked) { Text("取消", color = c.textSecondary) } },
            )
        }
    }

    if (showTerminal) {
        ModalBottomSheet(
            onDismissRequest = { showTerminal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
            shape = RoundedCornerShape(topStart = SignalRadius.sheet, topEnd = SignalRadius.sheet),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("终端 · 最近 120 行", style = Signal.type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
                    SignalChip("刷新", onClick = vm::refreshTerminal, container = c.surfaceSubtle, height = SignalSize.chipSmall)
                }
                Text("只读。需要在终端里操作时请回到电脑。", style = Signal.type.caption, color = c.textSecondary)
                TerminalText(terminal, Modifier.fillMaxWidth().heightIn(max = 520.dp))
            }
        }
    }
}

private fun statusLine(it: AgentItem, now: Long): String {
    val st = when {
        it.lastSeen -> "最后看到"
        it.status == AgentStatus.BLOCKED -> "在等你"
        it.status == AgentStatus.WORKING -> "工作中"
        it.status == AgentStatus.DONE -> "已完成"
        it.status == AgentStatus.IDLE -> "空闲"
        else -> "状态未知"
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
) {
    val c = Signal.colors
    val state = rememberLazyListState()
    // 倒序布局：最新的在底部，新消息到来时如果用户停在底部就保持贴底
    val reversed = remember(items) { items.asReversed() }
    val lastUserIdx = remember(items) { items.indexOfLast { it is ConversationItem.User } }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = SignalSpace.pageConversation, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        item(key = "status") { LiveStatus(agent, now) }
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
            when (m) {
                is ConversationItem.User -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UserBubble(m.text)
                    if (m.index == items.getOrNull(lastUserIdx)?.index && receipt != null && m.text.trim() == receipt.text) ReceiptLine(receipt)
                }
                is ConversationItem.Assistant -> MarkdownText(m.text, Modifier.fillMaxWidth())
                is ConversationItem.Thinking -> ThinkingRow(m.text)
                is ConversationItem.Tool -> ToolCard(m)
                is ConversationItem.Unknown -> Text(m.text, style = Signal.type.caption, color = c.textTertiary)
            }
        }
        if (truncated) item(key = "truncated") {
            Text("更早的消息没有加载（只取最近 1 MB）", style = Signal.type.caption, color = c.textTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        if (loading && items.isEmpty()) item(key = "loading") {
            Text("正在读取会话…", style = Signal.type.meta, color = c.textSecondary)
        }
    }
}

@Composable
private fun LiveStatus(agent: AgentItem?, now: Long) {
    val c = Signal.colors
    agent ?: return
    val (color, text) = when {
        agent.lastSeen -> c.idle to "连不上主机 · 下面是最后看到的内容"
        agent.status == AgentStatus.BLOCKED -> c.attention to "在等你回答 · " + Durations.waited(agent.sinceMs, agent.sinceIsLowerBound, now)
        agent.status == AgentStatus.WORKING -> c.working to "正在工作 · " + Durations.waited(agent.sinceMs, agent.sinceIsLowerBound, now)
        else -> return
    }
    Row(
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Dot(color, 6.dp)
        Text(text, style = Signal.type.metaStrong.copy(fontSize = Signal.type.caption.fontSize), color = if (agent.status == AgentStatus.BLOCKED) c.attentionText else c.textSecondary)
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = Signal.colors
    Text(
        text,
        style = Signal.type.body,
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
    val c = Signal.colors
    val (text, color) = when (val x = r.receipt) {
        Receipt.Sending -> "发送中…" to c.textTertiary
        is Receipt.Accepted -> "已送达 · ${clock(x.atMs)}" to c.textTertiary
        is Receipt.Rejected -> "没有发出：${x.message} · 内容还在输入框里" to c.error
        is Receipt.Unknown -> "无法确认是否送达，不会自动重发 · 看看终端再决定" to c.warning
    }
    Text(text, style = Signal.type.micro, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun ThinkingRow(text: String) {
    val c = Signal.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.m)).clickable { open = !open }.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (open) SignalIcons.ChevronDown else SignalIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(14.dp))
            Text("思考过程", style = Signal.type.meta, color = c.textSecondary)
        }
        if (open) Text(text, style = Signal.type.meta, color = c.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 6.dp))
    }
}

@Composable
private fun ToolCard(t: ConversationItem.Tool) {
    val c = Signal.colors
    var open by rememberSaveable(t.index) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(SignalRadius.l))
            .border(1.dp, c.outlineCard, RoundedCornerShape(SignalRadius.l))
            .clickable { open = !open }
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(if (t.name.contains("edit", true) || t.name.contains("write", true)) SignalIcons.File else SignalIcons.Prompt, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
            Text(
                t.argumentsSummary.ifBlank { t.name },
                style = Signal.type.codeSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            when {
                t.result == null -> Text("运行中", style = Signal.type.caption, color = c.workingText)
                t.resultError -> Text("出错", style = Signal.type.caption, color = c.error)
                else -> Icon(SignalIcons.Check, "完成", tint = c.done, modifier = Modifier.size(14.dp))
            }
        }
        if (open && t.result != null) {
            Text(
                t.result.take(4_000),
                style = Signal.type.codeSmall,
                color = if (t.resultError) c.diffDelFg else c.textSecondary,
                modifier = Modifier.fillMaxWidth().background(c.cardHeaderBg).padding(horizontal = 12.dp, vertical = 8.dp)
                    .heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
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
    val c = Signal.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (quick.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { q -> SignalChip(q, onClick = { onQuick(q) }, container = c.surface, border = c.outline, height = SignalSize.chipSmall) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = SignalSize.composerMin).clip(RoundedCornerShape(26.dp)).background(c.surfaceSubtle)
                .padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (text.isEmpty()) Text(placeholder, style = Signal.type.body, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    textStyle = Signal.type.body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.textPrimary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                )
            }
            val can = enabled && text.isNotBlank() && !sending
            Box(
                Modifier.size(SignalSize.sendButton).clip(CircleShape).background(if (can) c.primary else c.outline)
                    .clickable(enabled = can, onClickLabel = "发送", onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(SignalIcons.Send, "发送", tint = if (can) c.onPrimary else c.textTertiary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun TerminalBody(terminal: String?, reason: String) {
    val c = Signal.colors
    Column(Modifier.fillMaxSize().padding(horizontal = SignalSpace.pageConversation, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$reason，这里显示终端的最近内容（只读）。", style = Signal.type.meta, color = c.textSecondary)
        TerminalText(terminal, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun TerminalText(terminal: String?, modifier: Modifier) {
    val c = Signal.colors
    val scroll = rememberScrollState()
    LaunchedEffect(terminal) { scroll.scrollTo(scroll.maxValue) }
    Box(modifier.clip(RoundedCornerShape(SignalRadius.l)).background(c.codeBlockBg)) {
        Text(
            terminal?.trimEnd() ?: "读取中…",
            style = Signal.type.codeSmall.copy(fontSize = Signal.type.micro.fontSize),
            color = c.codeBlockFg,
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
    Spacer(Modifier.size(0.dp))
}
