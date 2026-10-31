package dev.herdr.feature.board

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import dev.herdr.feature.board.design.Space
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.herdr.feature.board.design.HerdrThemeCompat
import dev.herdr.feature.board.design.LocalHerdrPalette

private const val SCREEN_BOARD = "board"
private const val SCREEN_CONVERSATION = "conversation"
private const val SCREEN_HOSTS = "hosts"

/**
 * 导航（M1）：看板 ⇄ 对话；主机是第二个一级目的地；技术信息走 Bottom Sheet。
 * 会话途中不再经过“Pane 详情”整页。
 */
@Composable
fun AppRoot(vm: HostsViewModel) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    HerdrThemeCompat(dark) { AppContent(vm) }
}

@Composable
private fun AppContent(vm: HostsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val composer by vm.composer.collectAsStateWithLifecycle()
    val approvals by vm.approvals.collectAsStateWithLifecycle()
    val pendingWrites by vm.pendingWrites.collectAsStateWithLifecycle()

    // I-90：进程可能随时被杀，ON_STOP 时立刻落盘，不等 400ms 防抖
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.flushDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val paneOutput by vm.paneOutput.collectAsStateWithLifecycle()
    val palette = LocalHerdrPalette.current

    var screen by rememberSaveable { mutableStateOf(SCREEN_BOARD) }
    var techSheet by rememberSaveable { mutableStateOf(false) }
    var pendingSheet by rememberSaveable { mutableStateOf(false) }
    var quoteTooLong by rememberSaveable { mutableStateOf(false) }
    val pane = state.board.selectedPane
    val activeHost = state.hosts.firstOrNull { it.cfg.id == state.activeId }

    BackHandler(enabled = screen != SCREEN_BOARD) {
        if (screen == SCREEN_CONVERSATION) {
            vm.closeConversation()
            screen = SCREEN_BOARD
        } else {
            screen = SCREEN_BOARD
        }
    }

    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            if (screen != SCREEN_CONVERSATION) {
                NavigationBar(containerColor = palette.surface) {
                    NavigationBarItem(
                        selected = screen == SCREEN_BOARD,
                        onClick = { screen = SCREEN_BOARD },
                        icon = { Text("▤", style = MaterialTheme.typography.titleMedium) },
                        label = { Text("看板") },
                    )
                    NavigationBarItem(
                        selected = screen == SCREEN_HOSTS,
                        onClick = { screen = SCREEN_HOSTS },
                        icon = { Text("⚙", style = MaterialTheme.typography.titleMedium) },
                        label = { Text("主机") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                SCREEN_HOSTS -> HostsScreen(
                    state = state,
                    onSave = vm::addHost,
                    onDelete = vm::removeHost,
                    onImportKey = vm::importKey,
                    onDeleteKey = vm::deleteKey,
                    onConnect = { id -> vm.selectHost(id); vm.connect(id) },
                    onDisconnect = vm::disconnect,
                    onBack = { screen = SCREEN_BOARD },
                )

                SCREEN_CONVERSATION -> ConversationScreen(
                    pane = pane,
                    transcript = transcript,
                    output = paneOutput,
                    composer = composer,
                    onTextChange = vm::updateComposerText,
                    onSend = { pane?.let { vm.sendPrompt(it) } },
                    onDismissOutcome = vm::clearComposerOutcome,
                    onReload = vm::reloadConversation,
                    onOpenTechSheet = { techSheet = true },
                    onQuote = { quoted ->
                        if (!vm.quoteIntoDraft(quoted)) quoteTooLong = true
                    },
                    onBack = {
                        vm.closeConversation()
                        screen = SCREEN_BOARD
                    },
                )

                else -> BoardScreen(
                    state = state,
                    approvals = approvals,
                    onApproval = { item, allow -> vm.answerApproval(item, allow) },
                    onSelectHost = vm::selectHost,
                    onRefresh = vm::refreshActive,
                    onReconnect = { vm.reconnect(state.activeId ?: return@BoardScreen) },
                    onOpenHosts = { screen = SCREEN_HOSTS },
                    onSelectAgent = { p ->
                        vm.openConversation(p)
                        screen = SCREEN_CONVERSATION
                    },
                    pendingWrites = pendingWrites,
                    onOpenPending = { pendingSheet = true },
                )
            }
        }
    }

    // UX-04c：引用超出 8000 字符上限时明确拒绝，不静默截断
    if (quoteTooLong) {
        AlertDialog(
            onDismissRequest = { quoteTooLong = false },
            title = { Text("内容太长") },
            text = { Text("当前草稿加上这段引用会超过 8000 字符上限，未写入。请先精简草稿，或单独复制引用内容。") },
            confirmButton = { TextButton(onClick = { quoteTooLong = false }) { Text("知道了") } },
        )
    }

    // UX-02c：Unknown 写操作详情（可从工作台 Banner 直接进入，不必先进对话/技术信息）
    if (pendingSheet) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingSheet = false },
            title = { Text("待确认的发送（${pendingWrites.size}）") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text(
                        "这些操作的结果无法确认，远端可能已经执行。不会自动重发；如需再发送同样内容，"
                            + "由你自行确认，并会产生一条新的操作记录。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    pendingWrites.forEach { rec ->
                        Column {
                            Text(
                                "${rec.method} → ${rec.target}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "action ${rec.clientRequestId.take(8)} · ${rec.attempts} 次 · " +
                                    (rec.outcome as? dev.herdr.core.herdr.WriteOutcome.Unknown)?.reason.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingSheet = false }) { Text("关闭") }
            },
        )
    }

    if (techSheet && pane != null) {
        TechnicalInfoSheet(
            pane = pane,
            hostLabel = activeHost?.cfg?.label?.ifBlank { activeHost.cfg.host } ?: "-",
            lastSyncMs = state.board.updatedAtMs,
            pendingWrites = pendingWrites,
            onDismiss = { techSheet = false },
        )
    }

    state.hostKeyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { /* 必须显式选择 */ },
            title = { Text("确认主机指纹（请带外核对）") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("${prompt.label} · ${prompt.hostId}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        prompt.fingerprint,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        "请与主机上 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` 的结果逐字核对。\n" +
                            "不一致请点“取消”。首次连接必须人工确认，绝不自动信任。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.answerHostKey(true) }) { Text("信任该主机") } },
            dismissButton = { TextButton(onClick = { vm.answerHostKey(false) }) { Text("取消") } },
        )
    }
}
