package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
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
    state: HostsViewModel.UiState,
    onSelectHost: (String) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onOpenHosts: () -> Unit,
    onSelectPane: (Pane) -> Unit,
) {
    val active = state.hosts.firstOrNull { it.cfg.id == state.activeId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Herdr 看板") },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.board.refreshing) {
                        Text(if (state.board.refreshing) "刷新中…" else "刷新")
                    }
                    TextButton(onClick = onOpenHosts) { Text("主机") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.hosts.size > 1) {
                HostChips(state, onSelectHost)
            }
            if (active != null) {
                ConnectionBanner(active, state.board, onReconnect)
            }
            if (state.board.panes.isEmpty()) {
                EmptyState(active, state.board, onReconnect, onOpenHosts)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.board.groups.forEach { group ->
                        item(key = "h-${group.status}") { GroupHeader(group) }
                        items(group.panes, key = { "${state.activeId}-${it.paneId}" }) { pane ->
                            PaneCard(pane) { onSelectPane(pane) }
                        }
                    }
                    item(key = "meta") { MetaFooter(state.board) }
                }
            }
        }
    }
}

@Composable
private fun HostChips(state: HostsViewModel.UiState, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.hosts.forEach { h ->
            val selected = h.cfg.id == state.activeId
            Surface(
                onClick = { onSelect(h.cfg.id) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(connColor(h.connKind), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(h.cfg.label.ifBlank { h.cfg.host }, style = MaterialTheme.typography.labelLarge)
                    if (h.paneCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("${h.paneCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    host: HostsViewModel.HostState,
    board: HostsViewModel.BoardView,
    onReconnect: () -> Unit,
) {
    val color = when (host.connKind) {
        dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED -> MaterialTheme.colorScheme.secondaryContainer
        dev.herdr.core.data.HostConnectionManager.ConnKind.STALE, dev.herdr.core.data.HostConnectionManager.ConnKind.RECONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
        dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(connColor(host.connKind), CircleShape)
                    .semantics { contentDescription = "连接状态：${host.connKind}" },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(host.connText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${host.cfg.host}:${host.cfg.port} · ${host.cfg.session}" +
                        if (board.updatedAtMs > 0) " · 更新于 ${timeAgo(board.updatedAtMs)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED || host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.STALE) {
                OutlinedButton(onClick = onReconnect) { Text("重连") }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: BoardGrouper.Group) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(StatusColors.of(group.status), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(BoardGrouper.title(group.status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                Text(pane.agent ?: "shell", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                StatusChip(pane.status)
                Spacer(Modifier.weight(1f))
                Text(pane.paneId, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(pane.title ?: "(无标题)", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(pane.workspaceId.ifBlank { null }, pane.tabId.ifBlank { null }, pane.cwd).joinToString(" · "),
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
private fun EmptyState(
    host: HostsViewModel.HostState?,
    board: HostsViewModel.BoardView,
    onReconnect: () -> Unit,
    onOpenHosts: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有数据", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                host == null -> "先添加一台主机"
                !host.hasKey -> "导入这台主机的私钥后再连接"
                host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED -> host.lastError ?: "连接失败"
                else -> "正在连接，请稍候…"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenHosts) { Text("主机设置") }
            OutlinedButton(onClick = onReconnect) { Text("重试") }
        }
        if (board.stale && board.updatedAtMs > 0) {
            Spacer(Modifier.height(8.dp))
            Text("显示的是缓存数据（${timeAgo(board.updatedAtMs)}）", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MetaFooter(board: HostsViewModel.BoardView) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            "herdr ${board.version.ifBlank { "?" }} · protocol ${board.protocol} · ${board.panes.size} 个 pane",
            style = MaterialTheme.typography.labelSmall,
        )
        Text("只读模式（Milestone 1）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

internal fun connColor(kind: dev.herdr.core.data.HostConnectionManager.ConnKind): Color = when (kind) {
    dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTED -> Color(0xFF2F9E44)
    dev.herdr.core.data.HostConnectionManager.ConnKind.STALE -> Color(0xFFF08C00)
    dev.herdr.core.data.HostConnectionManager.ConnKind.RECONNECTING, dev.herdr.core.data.HostConnectionManager.ConnKind.CONNECTING -> Color(0xFF1C7ED6)
    dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED -> Color(0xFFC92A2A)
    else -> Color(0xFF868E96)
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
