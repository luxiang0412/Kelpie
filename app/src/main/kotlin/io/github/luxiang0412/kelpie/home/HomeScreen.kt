package io.github.luxiang0412.kelpie.home

import io.github.luxiang0412.kelpie.core.herdr.plural
import io.github.luxiang0412.kelpie.core.herdr.t

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.attention.Durations
import io.github.luxiang0412.kelpie.core.data.runtime.HostKeyPrompt
import io.github.luxiang0412.kelpie.core.designsystem.DoneBadge
import io.github.luxiang0412.kelpie.core.designsystem.Dot
import io.github.luxiang0412.kelpie.core.designsystem.SectionLabel
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIconButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import io.github.luxiang0412.kelpie.core.designsystem.KelpieLogo
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSize
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSpace
import io.github.luxiang0412.kelpie.core.designsystem.UnknownRing
import io.github.luxiang0412.kelpie.core.designsystem.WorkingRing
import io.github.luxiang0412.kelpie.core.data.runtime.ApprovalItem

@Composable
fun HomeRoute(
    onOpen: (AgentKey, String?) -> Unit,
    onSettings: () -> Unit,
    onAddHost: () -> Unit,
    onOpenTerminal: (AgentKey) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prompt by vm.hostKeyPrompt.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ApprovalItem?>(null) }
    HomeScreen(ui, onOpen, { sheet = it }, onSettings, onAddHost, onRetry = vm::retry, onOpenTerminal = onOpenTerminal)
    prompt?.let { HostKeyDialog(it, vm::answerHostKey) }
    sheet?.let { a ->
        // 请求从列表里消失（已回答 / 过期 / 电脑上处理了）时自动关闭
        val live = ui.entries.any { it is AttentionEntry.Permission && it.approval.request.requestId == a.request.requestId }
        androidx.compose.runtime.LaunchedEffect(live) { if (!live) sheet = null }
        val pane = a.request.paneId
        io.github.luxiang0412.kelpie.conversation.ApprovalSheet(
            a, onDismiss = { sheet = null },
            onOpenConversation = pane?.let { p -> { onOpen(AgentKey(a.hostId, p), null) } },
        )
    }
}

@Composable
fun HomeScreen(
    ui: HomeUi,
    onOpen: (AgentKey, String?) -> Unit,
    onOpenApproval: (ApprovalItem) -> Unit,
    onSettings: () -> Unit,
    onAddHost: () -> Unit,
    onRetry: () -> Unit,
    /** 打开对话页并直接拉起终端（提问卡片上的“看终端”）。 */
    onOpenTerminal: (AgentKey) -> Unit = {},
) {
    val c = Kelpie.colors
    val s = ui.state
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showQuiet by rememberSaveable { mutableStateOf(false) }
    val offline = ui.banner != null

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        HomeTopBar(attention = ui.entries.isNotEmpty(), onSettings = onSettings)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = KelpieSpace.pageHome, end = KelpieSpace.pageHome, top = 4.dp),
        ) {
            ui.banner?.let { b -> item(key = "banner") { Box(Modifier.padding(bottom = 12.dp)) { OfflineBanner(b, ui.nowMs, onRetry) } } }

            item(key = "header") { Box(Modifier.padding(bottom = 16.dp)) { Header(ui) } }

            if (s.noHosts) {
                item(key = "empty") {
                    if (ui.disabledHosts > 0) AllDisabled(onSettings) else NoHosts(onAddHost)
                }
            }

            items(ui.entries, key = { it.stableKey }) { e ->
                Box(Modifier.padding(bottom = 12.dp)) {
                    when (e) {
                        is AttentionEntry.Question -> QuestionCard(e, ui.nowMs, offline, onOpen, onOpenTerminal)
                        is AttentionEntry.Permission -> PermissionCard(e, ui.nowMs, offline) { onOpenApproval(e.approval) }
                    }
                }
            }

            val dupTitles = (s.needsYou + s.working + s.recentlyDone + s.quiet)
                .groupBy { it.key.hostId to it.title }.filterValues { it.size > 1 }.values.flatten().map { it.key }.toSet()
            items(ui.unknownSends, key = { "u:" + it.first.id }) { (rec, title) ->
                Box(Modifier.padding(bottom = 12.dp)) { UnknownSendRow(title) { onOpen(rec.key, null) } }
            }

            if (s.working.isNotEmpty()) {
                item(key = "working-label") { SectionLabel(if (offline) t("离线前在工作 · ${s.working.size}", "Working before offline · ${s.working.size}") else t("工作中 · ${s.working.size}", "Working · ${s.working.size}")) }
                items(s.working, key = { "w:" + it.key.hostId + it.key.paneId }) { item ->
                    AgentRow(item, ui.nowMs, multiHost = s.hosts.size > 1, showPane = item.key in dupTitles) { onOpen(item.key, null) }
                }
            }

            if (s.recentlyDone.isNotEmpty() || s.quiet.isNotEmpty()) {
                item(key = "toggles") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.recentlyDone.isNotEmpty()) {
                            ToggleTile(Modifier.weight(1f), t("刚完成", "Just done"), s.recentlyDone.size, showDone, done = true) { showDone = !showDone }
                        }
                        if (s.quiet.isNotEmpty()) {
                            ToggleTile(Modifier.weight(1f), t("空闲", "Idle"), s.quiet.size, showQuiet, done = false) { showQuiet = !showQuiet }
                        }
                    }
                }
            }
            if (showDone) {
                if (showQuiet) item(key = "done-label") { SectionLabel(t("刚完成 · ${s.recentlyDone.size}", "Just done · ${s.recentlyDone.size}")) }
                items(s.recentlyDone, key = { "d:" + it.key.hostId + it.key.paneId }) { item ->
                    AgentRow(item, ui.nowMs, multiHost = s.hosts.size > 1, showPane = item.key in dupTitles) { onOpen(item.key, null) }
                }
            }
            if (showQuiet) {
                if (showDone) item(key = "quiet-label") { SectionLabel(t("空闲 · ${s.quiet.size}", "Idle · ${s.quiet.size}")) }
                items(s.quiet, key = { "i:" + it.key.hostId + it.key.paneId }) { item ->
                    AgentRow(item, ui.nowMs, multiHost = s.hosts.size > 1, showPane = item.key in dupTitles) { onOpen(item.key, null) }
                }
            }
            item(key = "bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(24.dp)) }
        }
    }
}

@Composable
private fun HomeTopBar(attention: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(KelpieSize.topBarHome).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KelpieLogo(size = 22.dp, attention = attention)
        Text("Kelpie", style = Kelpie.type.appName, color = Kelpie.colors.textPrimary, modifier = Modifier.weight(1f))
        KelpieIconButton(KelpieIcons.Settings, t("设置", "Settings"), onSettings, tint = Kelpie.colors.textPrimary)
    }
}

@Composable
private fun Header(ui: HomeUi) {
    val c = Kelpie.colors
    val s = ui.state
    val n = ui.waitingAgents
    val title = when {
        s.noHosts && ui.disabledHosts > 0 -> t("主机都已停用", "All hosts are disabled")
        s.noHosts -> t("还没有主机", "No hosts yet")
        s.loading -> t("正在连接…", "Connecting…")
        n > 0 -> t("$n 个 agent 在等你", (if (n == 1) "1 agent is waiting for you" else "$n agents waiting for you"))
        else -> t("没有 agent 在等你", "No agents waiting for you")
    }
    val total = s.hosts.size
    val (dot, sub) = when {
        s.noHosts && ui.disabledHosts > 0 -> null to t("在设置里打开要连接的主机", "Turn on the hosts you want to connect in Settings")
        s.noHosts -> null to t("添加一台运行 herdr 的电脑，就能在这里看到它的 agent", "Add a computer running herdr to see its agents here")
        ui.banner is Banner.PhoneOffline || (ui.banner != null && ui.onlineHosts == 0) -> {
            val at = s.hosts.maxOfOrNull { it.fetchedAtMs } ?: 0
            c.warning to (if (at > 0) t("截至 ${clock(at)} · 现在的状态未知", "As of ${clock(at)} · current status unknown") else t("还没拿到过数据", "No data yet"))
        }
        ui.banner != null -> c.warning to t("${ui.onlineHosts} 台在线 · ${total - ui.onlineHosts} 台连不上", "${ui.onlineHosts} online · ${total - ui.onlineHosts} unreachable")
        s.loading -> c.iconMuted to t("$total 台主机", plural(total, "host"))
        // 有缓存但一台都还没连上：不能说“有新情况会通知你”
        ui.onlineHosts == 0 -> c.iconMuted to t("正在连接 $total 台主机…", "Connecting to " + plural(total, "host") + "…")
        n == 0 -> c.done to t("${ui.onlineHosts} 台主机在线 · 有新情况会通知你", plural(ui.onlineHosts, "host") + " online · you'll be notified of changes")
        else -> c.done to t("${ui.onlineHosts} 台主机在线 · 实时", plural(ui.onlineHosts, "host") + " online · live")
    }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Kelpie.type.display, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 圆点对齐第一行文字（副标题在大字体下会折行）
            if (dot != null) {
                val lh = with(androidx.compose.ui.platform.LocalDensity.current) { Kelpie.type.meta.lineHeight.toDp() }
                Box(Modifier.height(lh), contentAlignment = Alignment.Center) { Dot(dot) }
            }
            Text(sub, style = Kelpie.type.meta, color = c.textSecondary)
        }
    }
}

@Composable
fun AgentRow(item: AgentItem, nowMs: Long, multiHost: Boolean, showPane: Boolean = false, onClick: () -> Unit) {
    val c = Kelpie.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = KelpieSize.rowMin)
            .clip(RoundedCornerShape(KelpieRadius.m))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            item.lastSeen -> UnknownRing()
            item.status == io.github.luxiang0412.kelpie.core.herdr.AgentStatus.WORKING -> WorkingRing(spinning = true)
            item.status == io.github.luxiang0412.kelpie.core.herdr.AgentStatus.DONE || item.finishedAtMs != null && item.status == io.github.luxiang0412.kelpie.core.herdr.AgentStatus.IDLE -> DoneBadge()
            else -> Box(Modifier.size(KelpieSize.statusIcon), contentAlignment = Alignment.Center) { Dot(c.idle, 8.dp) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = Kelpie.type.rowTitle, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(item.agentName, style = Kelpie.type.caption, color = c.textSecondary, maxLines = 1)
            }
            val sub = buildString {
                if (item.lastSeen) append(t("最后看到 · ", "Last seen · "))
                append(if (multiHost) item.hostLabel else item.pane.cwd?.replace(Regex("^/home/[^/]+"), "~") ?: item.hostLabel)
                // 同一台主机上标题重名时，带上 pane 编号区分
                if (showPane) append(" · ").append(item.pane.paneId)
            }
            Text(sub, style = Kelpie.type.meta, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val trailing = when (item.status) {
            io.github.luxiang0412.kelpie.core.herdr.AgentStatus.WORKING -> Durations.waited(item.sinceMs, item.sinceIsLowerBound, nowMs)
            else -> Durations.ago(item.finishedAtMs ?: item.sinceMs, nowMs).takeIf { item.finishedAtMs != null }.orEmpty()
        }
        if (trailing.isNotEmpty()) Text(trailing, style = Kelpie.type.caption, color = c.textSecondary)
    }
}

@Composable
private fun ToggleTile(modifier: Modifier, text: String, count: Int, open: Boolean, done: Boolean, onClick: () -> Unit) {
    val c = Kelpie.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(KelpieRadius.l))
            .background(c.surfaceSubtle)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (done) Icon(KelpieIcons.Check, null, tint = c.done, modifier = Modifier.size(16.dp)) else Dot(c.idle, 8.dp)
        // 大字体 + 窄屏时标签可以省略，数字永远完整显示
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = Kelpie.type.label, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("$count", style = Kelpie.type.label, color = c.textPrimary, maxLines = 1, softWrap = false)
        }
        Icon(if (open) KelpieIcons.ChevronDown else KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun UnknownSendRow(title: String, onClick: () -> Unit) {
    val c = Kelpie.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(KelpieRadius.l))
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KelpieIcons.Info, null, tint = c.warning, modifier = Modifier.size(18.dp))
        Text(t("1 条发送结果无法确认 · $title", "1 send couldn't be confirmed · $title"), style = Kelpie.type.label, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AllDisabled(onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KelpieButton(t("去设置", "Open Settings"), onSettings, Modifier.fillMaxWidth(), height = KelpieSize.primaryButton, leading = KelpieIcons.Settings)
    }
}

@Composable
private fun NoHosts(onAddHost: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KelpieButton(t("添加主机", "Add host"), onAddHost, Modifier.fillMaxWidth(), height = KelpieSize.primaryButton, leading = KelpieIcons.Plus)
    }
}

@Composable
fun HostKeyDialog(p: HostKeyPrompt, onAnswer: (Boolean) -> Unit) {
    val c = Kelpie.colors
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        containerColor = c.surface,
        title = { Text(t("确认 ${p.label} 的指纹", "Verify fingerprint for ${p.label}"), style = Kelpie.type.headline, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("第一次连接 ${p.host}。在电脑上运行 ssh-keyscan ${p.host} | ssh-keygen -lf - 对照，一致再信任。", "First connection to ${p.host}. On your computer run ssh-keyscan ${p.host} | ssh-keygen -lf - and trust only if it matches."), style = Kelpie.type.body, color = c.textSecondary)
                if (p.algorithm.isNotBlank()) Text(p.algorithm, style = Kelpie.type.metaStrong, color = c.textSecondary)
                Text(p.fingerprint, style = Kelpie.type.code, color = c.textPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(KelpieRadius.m)).background(c.surfaceSubtle).padding(10.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(t("信任并连接", "Trust and connect"), color = c.textPrimary, style = Kelpie.type.label) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(t("取消", "Cancel"), color = c.textSecondary, style = Kelpie.type.label) } },
    )
}

internal fun clock(ms: Long): String =
    if (ms <= 0) "--:--" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
