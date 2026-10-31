package dev.herdr.feature.board

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val SCREEN_BOARD = "board"
private const val SCREEN_HOSTS = "hosts"
private const val SCREEN_DETAIL = "detail"
private const val SCREEN_TRANSCRIPT = "transcript"

@Composable
fun AppRoot(vm: HostsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(SCREEN_BOARD) }

    BackHandler(enabled = screen != SCREEN_BOARD) {
        if (screen == SCREEN_DETAIL) vm.selectPane(null)
        if (screen == SCREEN_TRANSCRIPT) vm.closeTranscript()
        screen = SCREEN_BOARD
    }

    when (screen) {
        SCREEN_HOSTS -> HostsScreen(
            state = state,
            onSave = { cfg -> vm.addHost(cfg) },
            onDelete = vm::removeHost,
            onImportKey = vm::importKey,
            onDeleteKey = vm::deleteKey,
            onConnect = { id -> vm.selectHost(id); vm.connect(id) },
            onDisconnect = vm::disconnect,
            onBack = { screen = SCREEN_BOARD },
        )

        SCREEN_DETAIL -> PaneDetailScreen(
            pane = state.board.selectedPane,
            onBack = {
                vm.selectPane(null)
                screen = SCREEN_BOARD
            },
            onOpenTranscript = state.board.selectedPane?.agentSessionPath?.let {
                {
                    state.board.selectedPane?.let { p -> vm.openTranscript(p) }
                    screen = SCREEN_TRANSCRIPT
                }
            },
        )

        SCREEN_TRANSCRIPT -> TranscriptScreen(
            state = transcript,
            onRefresh = vm::reloadTranscript,
            onBack = { screen = SCREEN_DETAIL },
        )

        else -> BoardScreen(
            state = state,
            onSelectHost = vm::selectHost,
            onRefresh = vm::refreshActive,
            onReconnect = { vm.reconnect(state.activeId ?: return@BoardScreen) },
            onOpenHosts = { screen = SCREEN_HOSTS },
            onSelectPane = { pane ->
                vm.selectPane(pane)
                screen = SCREEN_DETAIL
            },
        )
    }

    state.hostKeyPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { /* 必须显式选择 */ },
            title = { Text("确认主机指纹（请带外核对）") },
            text = {
                Column {
                    Text("${prompt.label} · ${prompt.hostId}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        prompt.fingerprint,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
