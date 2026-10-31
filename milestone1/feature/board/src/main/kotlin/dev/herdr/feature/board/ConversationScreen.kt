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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
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
import dev.herdr.feature.board.design.LocalHerdrPalette
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.textPrimary,
                ),
                navigationIcon = { TextButton(onClick = onBack) { Text("看板") } },
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            projectLine(pane),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    StatusChip(pane.status)
                    Spacer(Modifier.width(Space.xs))
                    TextButton(onClick = onOpenTechSheet) { Text("技术信息") }
                },
            )
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
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        itemsIndexed(
            items = messages,
            key = { index, m -> "${pane.paneId}-$index-${m.kind}" },
            contentType = { _, m -> m.kind },
        ) { _, message ->
            when (message.kind) {
                ChatMessage.Kind.USER -> UserMessage(message.text)
                ChatMessage.Kind.ASSISTANT -> AssistantMessage(message.text)
                ChatMessage.Kind.THINKING -> CollapsibleRow("思考过程", message.text, muted = true)
                ChatMessage.Kind.TOOL_CALL -> CollapsibleRow(
                    "工具 · ${message.toolName ?: "?"}",
                    message.text.ifBlank { "(无参数)" },
                    mono = true,
                )
                ChatMessage.Kind.TOOL_RESULT -> CollapsibleRow("工具结果", message.text, mono = true, muted = true)
                ChatMessage.Kind.SYSTEM -> CollapsibleRow("系统", message.text, muted = true)
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

@Composable
private fun AssistantMessage(text: String) {
    val palette = LocalHerdrPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Pi",
            style = MaterialTheme.typography.labelSmall,
            color = palette.statusFg(AgentStatus.DONE),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
    }
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
    val canSend = pane.agent != null && !composer.sending
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.page, vertical = Space.s),
    ) {
        composer.message?.let { msg ->
            val color = when (composer.outcome) {
                is WriteOutcome.Rejected -> palette.statusFg(AgentStatus.BLOCKED)
                is WriteOutcome.Unknown -> palette.statusFg(AgentStatus.UNKNOWN)
                else -> palette.textSecondary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissOutcome) { Text("知道了") }
            }
        }
        Surface(color = palette.surfaceRaised, shape = RoundedCornerShape(Radius.card)) {
            Row(Modifier.padding(start = Space.m, end = Space.s, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                    placeholder = {
                        Text(
                            if (pane.agent == null) "这个 pane 没有运行 agent" else "发送下一步指令…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    maxLines = 5,
                    enabled = canSend,
                )
                Spacer(Modifier.width(Space.s))
                Surface(
                    color = if (canSend && composer.text.isNotBlank()) palette.accent else palette.outline,
                    shape = RoundedCornerShape(Radius.chip),
                    modifier = Modifier.clickable(enabled = canSend && composer.text.isNotBlank(), onClick = onSend),
                ) {
                    Text(
                        if (composer.sending) "发送中" else "发送",
                        color = palette.accentOn,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
                    )
                }
            }
        }
        Text(
            "已接受（Accepted）只代表远端收到，不代表执行完成；超时=结果未知，不会自动重发。",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = Space.xs),
        )
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
