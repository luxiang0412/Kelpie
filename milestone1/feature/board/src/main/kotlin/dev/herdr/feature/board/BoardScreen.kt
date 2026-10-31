package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.BoardGrouper
import dev.herdr.core.herdr.Pane
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardViewModel.UiState,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onOpenHosts: () -> Unit,
    onSelectPane: (Pane) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Herdr 看板") },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Text(if (state.refreshing) "刷新中…" else "刷新")
                    }
                    TextButton(onClick = onOpenHosts) { Text("主机") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ConnectionBanner(state, onReconnect, onOpenHosts)
            if (state.panes.isEmpty()) {
                EmptyState(state, onReconnect, onOpenHosts)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "h-${group.status}") {
                            GroupHeader(group)
                        }
                        items(group.panes, key = { it.paneId }) { pane ->
                            PaneCard(pane) { onSelectPane(pane) }
                        }
                    }
                    item(key = "meta") { MetaFooter(state) }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(state: BoardViewModel.UiState, onReconnect: () -> Unit, onOpenHosts: () -> Unit) {
    val color = when (state.connKind) {
        BoardViewModel.ConnKind.CONNECTED -> MaterialTheme.colorScheme.secondaryContainer
        BoardViewModel.ConnKind.STALE, BoardViewModel.ConnKind.RECONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
        BoardViewModel.ConnKind.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(state.connKind)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.connText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (state.stale && state.updatedAtMs > 0) {
                    Text(
                        "缓存数据 · 更新于 " + timeAgo(state.updatedAtMs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            when (state.connKind) {
                BoardViewModel.ConnKind.IDLE, BoardViewModel.ConnKind.FAILED ->
                    TextButton(onClick = onOpenHosts) { Text("设置") }
                BoardViewModel.ConnKind.CONNECTED, BoardViewModel.ConnKind.STALE ->
                    OutlinedButton(onClick = onReconnect) { Text("重连") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun StatusDot(kind: BoardViewModel.ConnKind) {
    val color = when (kind) {
        BoardViewModel.ConnKind.CONNECTED -> Color(0xFF2F9E44)
        BoardViewModel.ConnKind.STALE -> Color(0xFFF08C00)
        BoardViewModel.ConnKind.RECONNECTING, BoardViewModel.ConnKind.CONNECTING -> Color(0xFF1C7ED6)
        BoardViewModel.ConnKind.FAILED -> Color(0xFFC92A2A)
        else -> Color(0xFF868E96)
    }
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = "连接状态：$kind" },
    )
}

@Composable
private fun GroupHeader(group: BoardGrouper.Group) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(StatusColors.of(group.status), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            BoardGrouper.title(group.status),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(6.dp))
        Text("${group.panes.size}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PaneCard(pane: Pane, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "agent ${pane.agent ?: "无"}，状态 ${pane.status.wire}，pane ${pane.paneId}"
            },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pane.agent ?: "shell",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(pane.status)
                Spacer(Modifier.weight(1f))
                Text(pane.paneId, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                pane.title ?: "(无标题)",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    pane.workspaceId.ifBlank { null },
                    pane.tabId.ifBlank { null },
                    pane.cwd,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusChip(status: AgentStatus) {
    val c = StatusColors.of(status)
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
        Text(
            status.wire,
            color = c,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(state: BoardViewModel.UiState, onReconnect: () -> Unit, onOpenHosts: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有数据", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            when (state.connKind) {
                BoardViewModel.ConnKind.IDLE -> "先配置主机和私钥，然后连接"
                BoardViewModel.ConnKind.FAILED -> state.lastError ?: "连接失败"
                else -> "正在连接，请稍候…"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenHosts) { Text("打开主机设置") }
            OutlinedButton(onClick = onReconnect) { Text("重试") }
        }
    }
}

@Composable
private fun MetaFooter(state: BoardViewModel.UiState) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            "herdr ${state.version.ifBlank { "?" }} · protocol ${state.protocol} · " +
                "${state.panes.size} 个 pane",
            style = MaterialTheme.typography.labelSmall,
        )
        if (state.lastError != null) {
            Text(
                "最近错误：${state.lastError}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "只读模式（Milestone 1）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

fun timeAgo(ms: Long): String {
    if (ms <= 0) return "未知"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 5_000 -> "刚刚"
        d < 60_000 -> "${d / 1000} 秒前"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }
}
