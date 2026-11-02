package dev.herdr.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Space

@Composable
internal fun BlockedNotice() {
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

/** 对话副标题：Agent · 主机 · 状态（状态用真实文字）。 */
internal fun conversationSubtitle(pane: Pane, statusLabel: String): String {
    val agent = pane.agent?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Agent"
    val project = pane.cwd?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: pane.workspaceId
    return "$agent · $project · $statusLabel"
}

@Composable
internal fun TerminalFallback(
    output: String,
    onReload: () -> Unit,
    reason: String? = null,
    hasSessionId: Boolean = false,
) {
    val palette = LocalHerdrPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    reason != null -> "未使用结构化对话：$reason"
                    hasSessionId -> "未使用结构化对话（未定位到会话文件），下面是终端输出"
                    else -> "没有结构化会话（agent_session 未上报），下面是终端输出"
                },
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
internal fun LoadingBlock(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(Space.s))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ErrorBlock(error: String, onReload: () -> Unit) {
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
internal fun EmptyBlock(text: String) {
    val palette = LocalHerdrPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
    }
}
