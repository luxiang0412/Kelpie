package dev.herdr.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    state: HostsViewModel.TranscriptState,
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
                    itemsIndexed(state.messages) { index, msg -> MessageCard(index, msg) }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(index: Int, msg: ChatMessage) {
    val label: String
    val tint = when (msg.kind) {
        ChatMessage.Kind.USER -> { label = "你"; MaterialTheme.colorScheme.primary }
        ChatMessage.Kind.ASSISTANT -> { label = "assistant"; MaterialTheme.colorScheme.secondary }
        ChatMessage.Kind.THINKING -> { label = "thinking"; MaterialTheme.colorScheme.outline }
        ChatMessage.Kind.TOOL_CALL -> { label = "tool · ${msg.toolName ?: "?"}"; MaterialTheme.colorScheme.tertiary }
        ChatMessage.Kind.TOOL_RESULT -> { label = "tool result"; MaterialTheme.colorScheme.outline }
        ChatMessage.Kind.SYSTEM -> { label = "system"; MaterialTheme.colorScheme.outline }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        label,
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("#${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                msg.text.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (msg.kind == ChatMessage.Kind.TOOL_CALL) FontFamily.Monospace else null,
            )
        }
    }
}
