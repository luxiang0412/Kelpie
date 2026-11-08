package io.github.luxiang0412.kelpie.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.luxiang0412.kelpie.core.data.attention.HostInput
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.core.data.runtime.LaunchRecord
import io.github.luxiang0412.kelpie.core.data.runtime.LaunchState
import io.github.luxiang0412.kelpie.core.data.runtime.NewAgent
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieChip
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSize
import io.github.luxiang0412.kelpie.core.designsystem.kelpieCard
import io.github.luxiang0412.kelpie.core.herdr.t

/**
 * 新开一个 agent：选主机、agent、目录。在电脑上新建一个 herdr workspace（不抢焦点）再启动 agent。
 * 启动成功由调用方跳到它的对话；失败 / 不确定留在这里显示，不重试。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewAgentSheet(
    hosts: List<HostInput>,
    launch: LaunchRecord?,
    blocked: String?,
    onStart: (hostId: String, dir: String, kind: String) -> Unit,
    onDismiss: () -> Unit,
    /** 用户改了选择：上一次的错误提示不再对应现在的输入，清掉。 */
    onEdit: () -> Unit = {},
) {
    val c = Kelpie.colors
    val online = hosts.filter { it.reach == HostReach.Online }
    var hostId by rememberSaveable { mutableStateOf(online.firstOrNull()?.hostId) }
    var kind by rememberSaveable { mutableStateOf(NewAgent.KINDS.first().first) }
    var dir by rememberSaveable { mutableStateOf("") }
    val host = online.firstOrNull { it.hostId == hostId } ?: online.firstOrNull()
    val busy = launch?.state == LaunchState.Sending
    val recent = host?.let { h -> NewAgent.recentDirs(h.panes.asReversed().mapNotNull { p -> p.cwd?.let { it to (p.agent != null) } }) }.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = KelpieRadius.sheet, topEnd = KelpieRadius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("新开一个 agent", "Start a new agent"), style = Kelpie.type.headline, color = c.textPrimary)
            if (host == null) {
                Text(t("没有在线的主机。", "No host is online."), style = Kelpie.type.body, color = c.textSecondary)
                return@Column
            }
            if (online.size > 1) {
                Label(t("主机", "Host"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    online.forEach { h -> Choice(h.label, h.hostId == host.hostId, !busy) { hostId = h.hostId; onEdit() } }
                }
            }
            Label(t("Agent", "Agent"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NewAgent.KINDS.forEach { (k, name) -> Choice(name, k == kind, !busy) { kind = k; onEdit() } }
            }
            Label(t("目录", "Directory"))
            Box(Modifier.fillMaxWidth().kelpieCard(c.surfaceRaised, KelpieRadius.m).heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 13.dp)) {
                if (dir.isEmpty()) Text(t("~/project/… 或绝对路径", "~/project/… or an absolute path"), style = Kelpie.type.code, color = c.textTertiary)
                BasicTextField(
                    value = dir, onValueChange = { dir = it; onEdit() }, singleLine = true, enabled = !busy,
                    textStyle = Kelpie.type.code.copy(color = c.textPrimary), cursorBrush = SolidColor(c.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = t("目录", "Directory") },
                )
            }
            if (recent.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { d -> Choice(RowLabels.shortCwd(d) ?: d, d == dir, !busy) { dir = d; onEdit() } }
                }
            }
            Text(
                t("会在 ${host.label} 上新建一个 herdr workspace（电脑上不切过去），在这个目录里启动 ${NewAgent.KINDS.first { it.first == kind }.second}。",
                  "Creates a herdr workspace on ${host.label} (the computer keeps its focus) and starts ${NewAgent.KINDS.first { it.first == kind }.second} in this directory."),
                style = Kelpie.type.caption, color = c.textSecondary,
            )
            val status = blocked ?: when (val s = launch?.state) {
                is LaunchState.Failed -> s.message
                is LaunchState.Unknown -> s.reason
                else -> null
            }
            status?.let { Text(it, style = Kelpie.type.metaStrong, color = if (launch?.state is LaunchState.Unknown && blocked == null) c.warning else c.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KelpieButton(t("取消", "Cancel"), onDismiss, Modifier.weight(1f), primary = false)
                KelpieButton(
                    if (busy) t("正在启动…", "Starting…") else t("启动", "Start"),
                    { onStart(host.hostId, dir, kind) },
                    Modifier.weight(1.4f), enabled = !busy && dir.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = Kelpie.type.metaStrong, color = Kelpie.colors.textSecondary)

@Composable
private fun Choice(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Kelpie.colors
    KelpieChip(
        text, onClick,
        container = if (selected) c.primary else c.surface,
        content = if (selected) c.onPrimary else c.textPrimary,
        border = if (selected) null else c.outline,
        height = KelpieSize.chipSmall, enabled = enabled,
    )
}
