package dev.herdr.feature.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.herdr.core.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    state: BoardViewModel.UiState,
    onSave: (SettingsStore.Host) -> Unit,
    onImportKey: (String) -> Result<java.io.File>,
    onClearKey: () -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    var host by remember(state.host) { mutableStateOf(state.host.host) }
    var port by remember(state.host) { mutableStateOf(state.host.port.toString()) }
    var user by remember(state.host) { mutableStateOf(state.host.user) }
    var session by remember(state.host) { mutableStateOf(state.host.session) }
    var herdrBin by remember(state.host) { mutableStateOf(state.host.herdrBin) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主机设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(host, { host = it }, label = { Text("主机地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit) },
                    label = { Text("端口") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(2f))
            }
            OutlinedTextField(session, { session = it }, label = { Text("herdr 会话名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                herdrBin, { herdrBin = it },
                label = { Text("herdr 绝对路径") },
                supportingText = { Text("非交互 shell 的 PATH 通常不含 ~/.local/bin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            Text("私钥（只读界面，密钥仅存于本机 app 私有目录）", style = MaterialTheme.typography.titleSmall)
            Text(
                if (state.hasKey) "已导入私钥 ✓" else "尚未导入私钥",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showKeyDialog = true }) { Text("导入私钥（粘贴）") }
                if (state.hasKey) OutlinedButton(onClick = onClearKey) { Text("删除私钥") }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            SettingsStore.Host(
                                hostId = state.host.hostId.ifBlank { "default" },
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 22,
                                user = user.trim(),
                                session = session.trim().ifBlank { "test" },
                                herdrBin = herdrBin.trim(),
                            ),
                        )
                        onConnect()
                    },
                    enabled = host.isNotBlank() && user.isNotBlank() && state.hasKey,
                ) { Text("保存并连接") }
                OutlinedButton(onClick = onBack) { Text("取消") }
            }

            Text(
                "指纹确认：首次连接会弹出主机指纹，请与 `ssh-keygen -lf` 的结果逐字核对后再信任。\n" +
                    "本页不会修改远端任何配置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("粘贴 OpenSSH 私钥") },
            text = {
                Column {
                    Text(
                        "以 -----BEGIN OPENSSH PRIVATE KEY----- 开头。内容不会上传，只写入本机私有目录。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        keyText,
                        { keyText = it; keyError = null },
                        label = { Text("私钥内容") },
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                    )
                    if (keyError != null) Text(keyError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = onImportKey(keyText)
                    if (r.isSuccess) { showKeyDialog = false; keyText = "" } else keyError = r.exceptionOrNull()?.message
                }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showKeyDialog = false }) { Text("取消") } },
        )
    }
}
