package dev.herdr.signal.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.attention.AgentKey
import dev.herdr.core.data.attention.Durations
import dev.herdr.core.data.runtime.BlockedPreview
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalChip
import dev.herdr.signal.designsystem.SignalIcons
import dev.herdr.signal.designsystem.SignalRadius
import dev.herdr.signal.designsystem.SignalSize

/** 快捷回复（规范 5.3）：点了只填进输入框并打开对话，**从不直接发送**。 */
val QuickReplies = listOf("继续", "好的", "先解释一下")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(e: AttentionEntry.Question, nowMs: Long, offline: Boolean, onOpen: (AgentKey, String?) -> Unit) {
    val c = Signal.colors
    val item = e.item
    val waited = Durations.waited(item.sinceMs, item.sinceIsLowerBound, nowMs)
    val stale = item.lastSeen
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SignalRadius.card))
            .background(c.attentionContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(SignalRadius.m)).clickable { onOpen(item.key, null) }
                .semantics(mergeDescendants = true) { contentDescription = "${item.title}，${item.agentName}，等了 $waited" },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardHeader(
                icon = if (e.preview?.pendingTool != null) SignalIcons.Terminal else SignalIcons.Spark,
                title = item.title,
                sub = "${item.agentName} · ${item.hostLabel}",
                trailing = if (stale) "最后看到" else "等了 $waited",
            )
            PreviewBody(e.preview, stale)
        }
        if (stale || offline) {
            Text("联网后才能回复 · 草稿会保留", style = Signal.type.meta, color = c.attentionOnContainerMeta)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickReplies.forEach { q ->
                    SignalChip(q, onClick = { onOpen(item.key, q) }, container = c.attentionContainerInner, content = c.attentionOnContainerBody)
                }
                Spacer(Modifier.weight(1f))
                SignalChip("回复", onClick = { onOpen(item.key, null) }, container = c.primary, content = c.onPrimary, trailing = SignalIcons.ChevronRight)
            }
        }
    }
}

@Composable
private fun PreviewBody(p: BlockedPreview?, stale: Boolean) {
    val c = Signal.colors
    when {
        p == null -> Text(
            if (stale) "离线前它在等你回复。" else "正在读取它问了什么…",
            style = Signal.type.bodyCard, color = c.attentionOnContainerMeta,
        )
        p.source == BlockedPreview.Source.Screen -> Text(
            p.text, style = Signal.type.code, color = c.attentionOnContainerBody, maxLines = 8, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.m)).background(c.attentionContainerInner).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stripMarkdown(p.text), style = Signal.type.bodyCard, color = c.attentionOnContainerBody, maxLines = 6, overflow = TextOverflow.Ellipsis)
            if (p.pendingTool != null) {
                Text("等待批准 · ${p.pendingTool}", style = Signal.type.metaStrong, color = c.attentionText)
            }
        }
    }
}

@Composable
fun PermissionCard(e: AttentionEntry.Permission, nowMs: Long, offline: Boolean, onOpen: () -> Unit) {
    val c = Signal.colors
    val r = e.approval.request
    val title = e.item?.title ?: r.cwd?.trimEnd('/')?.substringAfterLast('/') ?: e.approval.hostLabel
    val agent = e.item?.agentName ?: r.agent ?: "Agent"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SignalRadius.card))
            .background(c.attentionContainer)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardHeader(
            icon = SignalIcons.Shield,
            title = title,
            sub = "$agent · ${e.approval.hostLabel} · 请求权限",
            trailing = Durations.ago(r.createdAtMs, nowMs).removeSuffix("前").ifBlank { "刚刚" },
        )
        Text(
            r.command ?: r.question,
            style = if (r.command != null) Signal.type.code else Signal.type.bodyCard,
            color = c.attentionOnContainerBody,
            maxLines = 4, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.m)).background(c.attentionContainerInner).padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (offline) "可能已过期，联网后重新确认" else (r.tool?.let { "$it · " } ?: "") + "${(r.expiresAtMs - nowMs).coerceAtLeast(0) / 1000} 秒内有效",
                style = Signal.type.meta, color = c.attentionOnContainerMeta, modifier = Modifier.weight(1f),
            )
            if (!offline) {
                Text("查看并处理", style = Signal.type.metaStrong, color = c.attentionText)
                Icon(SignalIcons.ChevronRight, null, tint = c.attentionText, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, sub: String, trailing: String) {
    val c = Signal.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(SignalSize.statusIcon).clip(RoundedCornerShape(SignalRadius.s)).background(c.attentionContainerInner),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.attention, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Signal.type.itemTitle, color = c.attentionOnContainerBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = Signal.type.caption, color = c.attentionOnContainerMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = Signal.type.metaStrong, color = c.attentionText)
    }
}

/** 卡片里只放纯文本：去掉最常见的 Markdown 记号，完整渲染留给对话页。 */
internal fun stripMarkdown(s: String): String = s
    .replace(Regex("```[a-zA-Z]*\\n?"), "")
    .replace(Regex("(?m)^#{1,6}\\s+"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

@Composable
fun OfflineBanner(b: Banner, nowMs: Long, onRetry: () -> Unit) {
    val c = Signal.colors
    val (title, sub) = when (b) {
        is Banner.PhoneOffline -> "手机没有网络" to (if (b.lastAtMs > 0) "下面是 ${Durations.ago(b.lastAtMs, nowMs)}的内容 · 联网后自动更新" else "联网后自动更新")
        is Banner.HostsUnreachable -> "连不上 ${b.labels.joinToString("、")}" to (b.detail.ifBlank { "正在自动重连" })
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.l)).background(c.offlineContainer).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SignalIcons.WifiOff, null, tint = c.offlineIcon, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Signal.type.rowTitle, color = c.textPrimary)
            Text(sub, style = Signal.type.meta, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SignalChip("重试", onClick = onRetry, container = c.surface, border = c.outline, height = SignalSize.chipSmall)
    }
}
