package dev.herdr.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    state: HostsViewModel.TranscriptState,
    composer: HostsViewModel.ComposerState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissOutcome: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("对话（只读）", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.path ?: "-",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = onRefresh, enabled = !state.loading) { Text("刷新") } },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()               // 键盘弹出时把输入栏顶上去
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                composer.message?.let { msg ->
                    val color = when (composer.outcome) {
                        is dev.herdr.core.herdr.WriteOutcome.Rejected -> MaterialTheme.colorScheme.error
                        is dev.herdr.core.herdr.WriteOutcome.Unknown -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismissOutcome) { Text("知道了") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = composer.text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("给 agent 发消息…") },
                        maxLines = 4,
                        enabled = !composer.sending,
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = onSend,
                        enabled = composer.text.isNotBlank() && !composer.sending,
                    ) { Text(if (composer.sending) "发送中" else "发送") }
                }
                Text(
                    "写操作超时会标记为“未知”，不会自动重发（D2/D21）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("读取最近 256 KB…", style = MaterialTheme.typography.labelSmall)
                }

                state.error != null -> Column(Modifier.padding(20.dp)) {
                    Text("读取失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Text(state.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "提示：需要在主机上安装 herdr 的 pi 集成（D35），并让 herdr 上报 agent_session 路径。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                state.messages.isEmpty() -> Column(Modifier.padding(20.dp)) {
                    Text("没有可显示的消息", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("可能是会话刚开始，或该 agent 的格式暂不支持。", style = MaterialTheme.typography.labelSmall)
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = state.messages,
                        key = { index, msg -> "$index-${msg.kind}-${msg.text.length}" },
                        contentType = { _, msg -> msg.kind },
                    ) { index, msg -> MessageCard(index, msg) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(index: Int, msg: ChatMessage) {
    val (label, tint) = when (msg.kind) {
        ChatMessage.Kind.USER -> "你" to MaterialTheme.colorScheme.primary
        ChatMessage.Kind.ASSISTANT -> "assistant" to MaterialTheme.colorScheme.secondary
        ChatMessage.Kind.THINKING -> "thinking" to MaterialTheme.colorScheme.outline
        ChatMessage.Kind.TOOL_CALL -> ("tool · ${msg.toolName ?: "?"}") to MaterialTheme.colorScheme.tertiary
        ChatMessage.Kind.TOOL_RESULT -> "tool result" to MaterialTheme.colorScheme.outline
        ChatMessage.Kind.SYSTEM -> "system" to MaterialTheme.colorScheme.outline
    }
    // 性能取舍：整条消息只用一个 Text（AnnotatedString 分段着色），
    // 避免 Card+Row+Surface+多个 Text 带来的多次 measure/layout。
    val body = msg.text.ifBlank { "（空）" }
    val text = androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = tint, fontWeight = FontWeight.Medium)) {
            append(label)
        }
        withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.outline)) {
            append("  #${index + 1}\n")
        }
        withStyle(
            androidx.compose.ui.text.SpanStyle(
                fontFamily = if (msg.kind == ChatMessage.Kind.TOOL_CALL) FontFamily.Monospace else null,
            ),
        ) { append(body) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}
