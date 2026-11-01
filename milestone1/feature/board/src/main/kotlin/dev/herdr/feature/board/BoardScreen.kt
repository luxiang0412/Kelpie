package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.feature.board.design.DisclosureIndicator
import dev.herdr.feature.board.design.ChevronAffordance
import dev.herdr.feature.board.design.PageGutter
import dev.herdr.feature.board.design.SectionTitleStyle
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Motion
import dev.herdr.feature.board.design.Radius
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.StatusText

/**
 * W1 看板：紧急度分区（需要处理 → 运行中 → 已完成 → 其他）。
 * — blocked 用可读的大卡；其余用紧凑行提高单屏密度
 * — 主机筛选放在顶栏（避免无限横向 chips）
 * — “缓存/过期”与“传输状态”分开表达
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: HostsViewModel.UiState,
    approvals: List<HostConnectionManager.ApprovalItem>,
    onApproval: (HostConnectionManager.ApprovalItem, Boolean) -> Unit,
    onSelectHost: (String) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onOpenHosts: () -> Unit,
    onSelectAgent: (Pane) -> Unit,
    pendingWrites: List<dev.herdr.core.herdr.WriteRecord> = emptyList(),
    onOpenPending: () -> Unit = {},
) {
    val palette = LocalHerdrPalette.current
    val pageGutter = PageGutter.of(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
    val active = state.hosts.firstOrNull { it.cfg.id == state.activeId }
    var hostMenu by remember { mutableStateOf(false) }
    // BOARD-DONE-001（已批准）：已完成默认收起，仅用户主动展开
    var showDone by remember { mutableStateOf(false) }
    // 只有「已完成」默认收起（BOARD-DONE-001）；idle / unknown 数量少，默认展开
    var showIdle by remember { mutableStateOf(true) }
    var showUnknown by remember { mutableStateOf(true) }

    val hostLabel = active?.cfg?.label?.ifBlank { active.cfg.host } ?: "-"
    val panes = state.board.panes
    val blocked = panes.filter { it.status == AgentStatus.BLOCKED }
    val working = panes.filter { it.status == AgentStatus.WORKING }
    val done = panes.filter { it.status == AgentStatus.DONE }
    val idle = panes.filter { it.status == AgentStatus.IDLE }
    val unknown = panes.filter { it.status == AgentStatus.UNKNOWN }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            // 依据 ux-d0 原型 board-*.png 与 DESIGN-SYSTEM §4.1：
            // 品牌小标 → 标题行 → 主机胶囊 + 右侧低强调连接摘要 → 1dp 分隔线
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.background)
                    .statusBarsPadding()
                    .padding(start = pageGutter, end = pageGutter, top = Space.s),
            ) {
                Text(
                    "HERDR",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    letterSpacing = 2.sp,
                )
                val compactWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.board_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary,
                        maxLines = if (compactWidth) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onRefresh,
                        enabled = !state.board.refreshing,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (state.board.refreshing) stringResource(R.string.board_refreshing)
                            else stringResource(R.string.board_refresh),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            Modifier
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(Radius.chip))
                                .background(palette.surfaceRaised)
                                .clickable { hostMenu = true }
                                .semantics {
                                    contentDescription = "当前主机 " +
                                        (active?.cfg?.label?.ifBlank { active.cfg.host } ?: "未选择") + "，点击切换"
                                }
                                .padding(horizontal = Space.m, vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                active?.cfg?.label?.ifBlank { active.cfg.host }
                                    ?: stringResource(R.string.board_host_pick),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(Space.xs))
                            DisclosureIndicator(expanded = false, tint = palette.textSecondary)
                        }
                        DropdownMenu(expanded = hostMenu, onDismissRequest = { hostMenu = false }) {
                            state.hosts.forEach { h ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${h.cfg.label.ifBlank { h.cfg.host }}  ·  ${connLabel(h.connKind)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    onClick = { hostMenu = false; onSelectHost(h.cfg.id) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.board_host_manage), style = MaterialTheme.typography.bodyMedium) },
                                onClick = { hostMenu = false; onOpenHosts() },
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // 右侧：低强调连接摘要（不是永久成功横幅，只在有主机时一行）
                    if (active != null) {
                        val connected = active.connKind == HostConnectionManager.ConnKind.CONNECTED
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (connected) palette.statusFg(AgentStatus.DONE) else palette.statusFg(AgentStatus.WORKING),
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            if (connected) "${active.paneCount} 个 Agent" else active.connText,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Space.s))
                HorizontalDivider(color = palette.borderSubtle)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StatusLine(state, active, onReconnect, pendingWrites, onOpenPending)

            if (approvals.isNotEmpty()) {
                approvals.forEach { item ->
                    ApprovalCard(item, onApproval)
                }
            }

            if (panes.isEmpty()) {
                // UX-08b：统一空态组件（区分"无主机"与"主机在线但无 Agent"）
                if (active == null) {
                    dev.herdr.feature.board.design.EmptyState(
                        title = stringResource(R.string.board_empty_no_agents),
                        body = stringResource(R.string.board_empty_add_host),
                        actionLabel = stringResource(R.string.board_host_settings),
                        onAction = onOpenHosts,
                        secondaryLabel = stringResource(R.string.label_retry),
                        onSecondary = onReconnect,
                    )
                } else {
                    dev.herdr.feature.board.design.EmptyState(
                        title = stringResource(R.string.board_empty_no_agents),
                        body = active.connText,
                        actionLabel = stringResource(R.string.label_retry),
                        onAction = onReconnect,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 720.dp else Int.MAX_VALUE.dp)
                        .align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(start = pageGutter, end = pageGutter, bottom = Space.huge),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // Attention First：blocked 前 2 条用卡片，其余紧凑；working 紧凑；done 默认收起。
                    if (blocked.isNotEmpty()) {
                        item(key = "sec-blocked") { SectionHeader(StatusText.section(AgentStatus.BLOCKED), blocked.size) }
                        items(blocked.take(2), key = { "b-${it.paneId}" }) { pane ->
                            AttentionCard(pane, hostLabel, onSelectAgent)
                        }
                        if (blocked.size > 2) {
                            items(blocked.drop(2), key = { "bc-${it.paneId}" }) { pane ->
                                CompactAgentRow(pane, hostLabel, onSelectAgent)
                            }
                        }
                    }
                    if (working.isNotEmpty()) {
                        item(key = "sec-working") { SectionHeader(StatusText.section(AgentStatus.WORKING), working.size) }
                        items(working, key = { "w-${it.paneId}" }) { pane ->
                            CompactAgentRow(pane, hostLabel, onSelectAgent)
                        }
                    }
                    if (done.isNotEmpty()) {
                        item(key = "sec-done") {
                            CollapsibleHeader(StatusText.section(AgentStatus.DONE), done.size, showDone) {
                                showDone = !showDone
                            }
                        }
                        if (showDone) {
                            items(done, key = { "d-${it.paneId}" }) { pane ->
                                CompactAgentRow(pane, hostLabel, onSelectAgent)
                            }
                        }
                    }
                    // idle 与 unknown 语义不同，分成两个分区（不再合并成“其他”）
                    if (idle.isNotEmpty()) {
                        item(key = "sec-idle") {
                            CollapsibleHeader(StatusText.label(AgentStatus.IDLE), idle.size, showIdle) {
                                showIdle = !showIdle
                            }
                        }
                        if (showIdle) {
                            items(idle, key = { "i-${it.paneId}" }) { pane ->
                                CompactAgentRow(pane, hostLabel, onSelectAgent)
                            }
                        }
                    }
                    if (unknown.isNotEmpty()) {
                        item(key = "sec-unknown") {
                            CollapsibleHeader(StatusText.label(AgentStatus.UNKNOWN), unknown.size, showUnknown) {
                                showUnknown = !showUnknown
                            }
                        }
                        if (showUnknown) {
                            items(unknown, key = { "u-${it.paneId}" }) { pane ->
                                CompactAgentRow(pane, hostLabel, onSelectAgent)
                            }
                        }
                    }
                    item(key = "footer") {
                        Text(
                            "herdr ${state.board.version.ifBlank { "?" }} · protocol ${state.board.protocol}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = Space.m),
                        )
                    }
                }
            }
        }
    }
}

/** 语义色（供组件内部使用，保持调用处简洁）。 */
private object Palette {
    val blocked = AgentStatus.BLOCKED
    val working = AgentStatus.WORKING
    val done = AgentStatus.DONE
    val muted = AgentStatus.IDLE
}

@Composable
/**
 * 单条主 Banner（UX-02c / DESIGN-SYSTEM §3 优先级）：
 * 安全异常 > Unknown 写操作 > 鉴权失败 / 待确认指纹 > 离线或重连中 > 缓存过期 > 正常（不显示）。
 * 同屏只出现一条，避免多条横幅互相叠压；其余问题通过详情入口查看。
 */
private fun StatusLine(
    state: HostsViewModel.UiState,
    active: HostsViewModel.HostState?,
    onReconnect: () -> Unit,
    pendingWrites: List<dev.herdr.core.herdr.WriteRecord>,
    onOpenPending: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    val pageGutter = PageGutter.of(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp)
    val host = active ?: return
    var showAllIssues by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val kind = host.connKind
    val transportTrouble = kind != HostConnectionManager.ConnKind.CONNECTED
    val dataStale = state.board.stale && state.board.updatedAtMs > 0

    data class Banner(val text: String, val color: androidx.compose.ui.graphics.Color, val action: (() -> Unit)?, val actionLabel: String)

    // DESIGN-SYSTEM §3 优先级：安全阻断/主机身份 > Unknown 写操作 > 认证失败 > 连接/数据过期
    val issues = ArrayList<Banner>()
    if (kind == HostConnectionManager.ConnKind.AWAITING_HOST_KEY) {
        issues += Banner("主机身份待确认：${host.connText}", palette.statusFg(AgentStatus.BLOCKED), onReconnect, "重试")
    }
    if (pendingWrites.isNotEmpty()) {
        issues += Banner(
            "发送结果无法确认（${pendingWrites.size}）：远端可能已执行，不会自动重发",
            palette.statusFg(AgentStatus.UNKNOWN),
            onOpenPending,
            "查看发送记录",
        )
    }
    if (kind == HostConnectionManager.ConnKind.FAILED) {
        issues += Banner(host.connText, palette.errorFg, onReconnect, "重试")
    }
    if (kind == HostConnectionManager.ConnKind.RECONNECTING || kind == HostConnectionManager.ConnKind.CONNECTING ||
        kind == HostConnectionManager.ConnKind.IDLE || kind == HostConnectionManager.ConnKind.STALE
    ) {
        issues += Banner(host.connText, palette.statusFg(AgentStatus.WORKING), onReconnect, "重试")
    }
    if (dataStale) {
        issues += Banner(
            stringResource(R.string.board_cache_banner_prefix) + " · " + timeAgo(state.board.updatedAtMs),
            palette.statusFg(AgentStatus.UNKNOWN),
            null,
            "",
        )
    }
    val banner = issues.firstOrNull() ?: return  // 一切正常：不占常驻横幅

    // 低优先级问题不丢：通过「全部问题」入口可查看
    showAllIssues?.let { list ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAllIssues = null },
            title = { Text("全部问题（${list.size}）") },
            text = {
                Column {
                    list.forEach { (text, _) ->
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(Space.s))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllIssues = null }) { Text(stringResource(R.string.label_dismiss)) }
            },
        )
    }

    Surface(color = palette.surfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = pageGutter, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                banner.text,
                style = MaterialTheme.typography.labelLarge,
                color = banner.color,
                modifier = Modifier.weight(1f),
            )
            if (issues.size > 1) {
                TextButton(
                    onClick = { showAllIssues = issues.map { it.text to it.actionLabel } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("全部问题 ${issues.size}", style = MaterialTheme.typography.labelLarge)
                }
            }
            banner.action?.let { act ->
                TextButton(onClick = act, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(banner.actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val palette = LocalHerdrPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = Space.xxl, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = SectionTitleStyle, color = palette.textPrimary)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
    }
}

@Composable
private fun CollapsibleHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val palette = LocalHerdrPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = Space.m, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = SectionTitleStyle, color = palette.textSecondary)
        Spacer(Modifier.width(Space.s))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        Spacer(Modifier.weight(1f))
        DisclosureIndicator(expanded = expanded, tint = palette.textSecondary)
    }
}

/**
 * blocked 卡片（ux-d0 原型 board-*.png + BOARD-SPEC §4）：
 * eyebrow（真实状态词）+ 任务标题 + meta（Agent · 项目 · 主机），右上角是 Agent 名。
 * 没有可靠的"等待原因/等待时长"字段，因此**不编造摘要**。
 */
@Composable
private fun AttentionCard(pane: Pane, hostLabel: String, onSelectAgent: (Pane) -> Unit) {
    val palette = LocalHerdrPalette.current
    val statusLabel = StatusText.label(pane.status)
    Surface(
        color = palette.statusBg(AgentStatus.BLOCKED),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.statusFg(AgentStatus.BLOCKED).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectAgent(pane) }
            .semantics {
                contentDescription = "$statusLabel，${pane.agent ?: ""}，${pane.title ?: pane.paneId}，点击进入对话"
            },
    ) {
        Column(Modifier.padding(Space.l)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.statusFg(AgentStatus.BLOCKED),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        displayTitle(pane),
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    (pane.agent ?: "agent").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                )
            }
            Spacer(Modifier.height(Space.s))
            Text(
                metaLine(pane, hostLabel),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Agent · 项目 · 主机（全部来自真实字段）。 */
internal fun metaLine(pane: Pane, hostLabel: String): String {
    val agent = pane.agent?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Agent"
    val project = pane.cwd?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: pane.workspaceId
    return listOf(agent, project, hostLabel).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * 紧凑行（ux-d0 原型 board-*.png）：40dp 圆角图标块 + 任务标题 + meta（Agent · 项目 · 主机）+ chevron。
 * 状态只用一处语义色（文字），图标块与 chevron 保持中性。
 */
@Composable
private fun CompactAgentRow(pane: Pane, hostLabel: String, onSelectAgent: (Pane) -> Unit) {
    val palette = LocalHerdrPalette.current
    val statusLabel = StatusText.label(pane.status)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelectAgent(pane) }
            .heightIn(min = 64.dp)
            .padding(vertical = Space.s)
            .semantics(mergeDescendants = true) {
                contentDescription = "$statusLabel，${pane.agent ?: ""}，${pane.title ?: pane.paneId}，${metaLine(pane, hostLabel)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_tool),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(palette.textSecondary),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                displayTitle(pane),
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                metaLine(pane, hostLabel),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.s))
        ChevronAffordance()
    }
}

@Composable
internal fun StatusChip(status: AgentStatus) {
    val palette = LocalHerdrPalette.current
    val statusLabel = StatusText.label(status)
    Surface(color = palette.statusBg(status), shape = RoundedCornerShape(Radius.chip)) {
        Text(
            statusLabel,
            color = palette.statusFg(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Space.m, vertical = 3.dp),
        )
    }
}

@Composable
private fun ApprovalCard(
    item: HostConnectionManager.ApprovalItem,
    onDecision: (HostConnectionManager.ApprovalItem, Boolean) -> Unit,
) {
    val palette = LocalHerdrPalette.current
    Surface(
        color = palette.statusBg(AgentStatus.BLOCKED),
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.xs),
    ) {
        Column(Modifier.padding(Space.l)) {
            Text(
                "需要你确认 · ${item.hostLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.statusFg(AgentStatus.BLOCKED),
            )
            Spacer(Modifier.height(Space.s))
            Text(item.request.question, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Spacer(Modifier.height(Space.xs))
            Text(
                "request ${item.request.requestId.take(8)} · " + if (item.request.expired) "已过期" else "有效",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Surface(
                    color = palette.accent,
                    shape = RoundedCornerShape(Radius.chip),
                    modifier = Modifier.clickable { onDecision(item, true) },
                ) {
                    Text(
                        "允许",
                        color = palette.accentOn,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
                    )
                }
                TextButton(onClick = { onDecision(item, false) }) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun EmptyState(
    active: HostsViewModel.HostState?,
    state: HostsViewModel.UiState,
    onOpenHosts: () -> Unit,
    onReconnect: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    Column(
        Modifier.fillMaxSize().padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.board_empty_no_agents), style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
        Spacer(Modifier.height(Space.s))
        Text(
            when {
                active == null -> stringResource(R.string.board_empty_add_host)
                !active.hasKey -> "导入这台主机的私钥后再连接"
                active.connKind == HostConnectionManager.ConnKind.FAILED -> active.lastError ?: "连接失败"
                active.connKind != HostConnectionManager.ConnKind.CONNECTED -> "正在连接，或需要重新验证…"
                else -> "这个会话里还没有识别到 agent"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton(onClick = onOpenHosts) { Text(stringResource(R.string.board_host_settings)) }
            TextButton(onClick = onReconnect) { Text(stringResource(R.string.label_retry)) }
        }
        if (state.board.updatedAtMs > 0) {
            Spacer(Modifier.height(Space.s))
            Text("上次同步 ${timeAgo(state.board.updatedAtMs)}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}

/** 显示用标题：去掉 agent 自己加的装饰前缀，避免 "Pi · π - xxx" 重复。 */
internal fun displayTitle(pane: Pane): String {
    val raw = pane.title?.trim().orEmpty()
    val cleaned = raw.removePrefix("π - ").removePrefix("π · ").removePrefix("π ").trim()
    val agentLabel = pane.agent?.replaceFirstChar { it.uppercase() }
    val title = cleaned.ifBlank { pane.paneId }
    return if (agentLabel != null && !title.equals(agentLabel, ignoreCase = true)) "$agentLabel · $title" else title
}

internal fun projectLine(pane: Pane): String {
    val hostish = pane.workspaceId.ifBlank { "?" }
    val project = pane.cwd?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: pane.title?.takeIf { it.isNotBlank() }
        ?: pane.paneId
    return "$hostish · $project"
}

internal fun connColor(kind: HostConnectionManager.ConnKind): Color = when (kind) {
    HostConnectionManager.ConnKind.CONNECTED -> Color(0xFF2F9E44)
    HostConnectionManager.ConnKind.STALE,
    HostConnectionManager.ConnKind.RECONNECTING,
    HostConnectionManager.ConnKind.CONNECTING -> Color(0xFF1C7ED6)
    HostConnectionManager.ConnKind.FAILED -> Color(0xFFC92A2A)
    else -> Color(0xFF868E96)
}

private fun connLabel(kind: HostConnectionManager.ConnKind): String = when (kind) {
    HostConnectionManager.ConnKind.CONNECTED -> "已连接"
    HostConnectionManager.ConnKind.CONNECTING -> "连接中"
    HostConnectionManager.ConnKind.RECONNECTING -> "恢复中"
    HostConnectionManager.ConnKind.STALE -> "数据过期"
    HostConnectionManager.ConnKind.FAILED -> "连接失败"
    HostConnectionManager.ConnKind.AWAITING_HOST_KEY -> "待确认指纹"
    HostConnectionManager.ConnKind.IDLE -> "未连接"
}

private fun timeAgo(ms: Long): String {
    if (ms <= 0) return "未知"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 5_000 -> "刚刚"
        d < 60_000 -> "${d / 1000} 秒前"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        else -> "${d / 3_600_000} 小时前"
    }
}
