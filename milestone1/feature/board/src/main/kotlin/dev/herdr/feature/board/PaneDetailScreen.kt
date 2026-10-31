package dev.herdr.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.Pane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaneDetailScreen(pane: Pane?, onBack: () -> Unit, onOpenTranscript: (() -> Unit)? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pane 详情") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        if (pane == null) {
            Text("pane 已不存在", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(pane.agent ?: "shell", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                StatusChipPublic(pane)
            }
            Field("pane_id", pane.paneId)
            Field("terminal_id", pane.terminalId ?: "-")
            Field("workspace", pane.workspaceId)
            Field("tab", pane.tabId)
            Field("agent_status", pane.status.wire)
            Field("revision", pane.revision.toString())
            Field("cwd", pane.cwd ?: "-")
            Field("title", pane.title ?: "-")
            Field("agent_session", pane.agentSessionPath ?: "null（未安装/未上报集成）")
            if (onOpenTranscript != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(onClick = onOpenTranscript) { Text("查看对话（只读）") }
            }

            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "只读视图。Milestone 1 不提供发送输入、审批或终端接管；\n" +
                        "`revision` 只作参考，不代表屏幕内容版本（RESULTS §9.10）。",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusChipPublic(pane: Pane) {
    val c = StatusColors.of(pane.status)
    Surface(color = c.copy(alpha = 0.14f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        Text(
            pane.status.wire,
            color = c,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
