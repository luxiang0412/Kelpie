package dev.herdr.feature.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.StatusText

/**
 * W3 技术信息 Sheet：替代原来的“Pane 详情”整页。
 * 技术字段默认折叠；值可复制；revision 明确标注为诊断用途（不是内容版本号）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalInfoSheet(
    pane: Pane,
    hostLabel: String,
    lastSyncMs: Long,
    pendingWrites: List<dev.herdr.core.herdr.WriteRecord> = emptyList(),
    onDismiss: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Space.page).padding(bottom = Space.xxl)) {
            Text(
                displayTitle(pane),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            if (pendingWrites.isNotEmpty()) {
                Spacer(Modifier.height(Space.m))
                Text(
                    "待确认写操作（${pendingWrites.size}）",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.statusFg(AgentStatus.UNKNOWN),
                )
                Text(
                    "这些操作结果未知，不会自动重发；确认前请先在会话里核对。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                )
                pendingWrites.forEach { rec ->
                    Column(Modifier.padding(top = Space.s)) {
                        Text(
                            "${rec.method} → ${rec.target}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                        )
                        Text(
                            "action ${rec.clientRequestId.take(8)} · ${timeAgoShort(rec.sentAtMs)} · " +
                                "payload ${rec.payloadSha256.take(12)}… · ${
                                    (rec.outcome as? dev.herdr.core.herdr.WriteOutcome.Unknown)?.reason ?: "pending"
                                }",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Text("状态：${StatusText.label(pane.status)}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Text("主机：$hostLabel", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            Text(
                "最近同步：${if (lastSyncMs > 0) timeAgoShort(lastSyncMs) else "未知"}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(Space.m))
            HorizontalDivider(color = palette.outline)
            Spacer(Modifier.height(Space.m))
            Text("技术信息", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary)
            Spacer(Modifier.height(Space.xs))
            CopyField("pane_id", pane.paneId)
            CopyField("agent_session", pane.agentSessionPath ?: "null（未上报）")
            CopyField("cwd", pane.cwd ?: "-")
            CopyField("terminal_id", pane.terminalId ?: "-")
            CopyField("workspace / tab", "${pane.workspaceId} / ${pane.tabId}")
            Text(
                "revision ${pane.revision}（诊断用途，不代表屏幕内容版本）",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = Space.xs),
            )
            Spacer(Modifier.height(Space.l))
            Text(
                "审批与终端接管尚未接入，此页仅供诊断。",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun CopyField(label: String, value: String) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { clipboard.setText(AnnotatedString(value)) }
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { clipboard.setText(AnnotatedString(value)) }) { Text("复制") }
    }
}

private fun timeAgoShort(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 5_000 -> "刚刚"
        d < 60_000 -> "${d / 1000} 秒前"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        else -> "${d / 3_600_000} 小时前"
    }
}
