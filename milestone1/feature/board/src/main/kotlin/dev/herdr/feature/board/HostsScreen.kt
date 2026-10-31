package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(
    state: HostsViewModel.UiState,
    onSave: (HostRepository.HostConfig) -> Unit,
    onDelete: (String) -> Unit,
    onImportKey: (String, String) -> Result<Unit>,
    onDeleteKey: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { dev.herdr.core.data.AppPrefs(context.applicationContext) }
    var monitoring by remember { mutableStateOf(prefs.monitoringEnabled) }
    val monitor = LocalMonitorController.current
    var editing by remember { mutableStateOf<HostRepository.HostConfig?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var keyTarget by remember { mutableStateOf<HostsViewModel.HostState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主机") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = { isNew = true; editing = null }) { Text("添加") }
                },
            )
        },
    ) { padding ->
        if (state.hosts.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有主机", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("点右上角“添加”，填入主机 / 用户 / herdr 会话，并导入 OpenSSH 私钥。")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { isNew = true; editing = null }) { Text("添加主机") }
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                // UX-05b：App 语言（跟随系统 / 简体中文 / English）。
                // 仅 Android 13+ 用系统级 per-app locale；旧版本不引入 AppCompat 架构迁移，明确说明不可用。
                LanguageSection(context)

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("后台监控（常驻通知）", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "切到后台也保持连接；agent 需要你处理或完成时发本地通知。受 Android 15 配额限制。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = monitoring,
                        onCheckedChange = { on ->
                            monitoring = on
                            prefs.monitoringEnabled = on
                            if (on) {
                                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                                    context is android.app.Activity
                                ) {
                                    context.requestPermissions(
                                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42,
                                    )
                                }
                                monitor?.start()
                            } else {
                                monitor?.stop()
                            }
                        },
                    )
                }
                LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.hosts, key = { it.cfg.id }) { h ->
                    HostCard(
                        host = h,
                        active = h.cfg.id == state.activeId,
                        onSelect = { onConnect(h.cfg.id) },
                        onDisconnect = { onDisconnect(h.cfg.id) },
                        onEdit = { isNew = false; editing = h.cfg },
                        onDelete = { onDelete(h.cfg.id) },
                        onKey = { keyTarget = h },
                    )
                }
            }
            }
        }
    }

    if (isNew || editing != null) {
        HostEditorDialog(
            initial = editing,
            onDismiss = { editing = null; isNew = false },
            onSave = { cfg ->
                onSave(cfg)
                editing = null
                isNew = false
            },
        )
    }

    keyTarget?.let { h ->
        KeyDialog(
            host = h,
            onDismiss = { keyTarget = null },
            onImport = { text -> onImportKey(h.cfg.id, text) },
            onDeleteKey = { onDeleteKey(h.cfg.id); keyTarget = null },
        )
    }
}

@Composable
private fun HostCard(
    host: HostsViewModel.HostState,
    active: Boolean,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onKey: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(10.dp)
                        .background(connColor(host.connKind), CircleShape),
                )
                Text(
                    host.cfg.label.ifBlank { host.cfg.host },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${host.cfg.user}@${host.cfg.host}:${host.cfg.port} · session ${host.cfg.session}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(host.connText, style = MaterialTheme.typography.labelSmall)
            if (host.lastError != null && host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED) {
                Text(host.lastError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                if (host.hasKey) "私钥已导入" else "缺少私钥",
                style = MaterialTheme.typography.labelSmall,
                color = if (host.hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.IDLE || host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED) {
                    Button(onClick = onSelect) { Text("连接") }
                } else {
                    OutlinedButton(onClick = onDisconnect) { Text("断开") }
                }
                OutlinedButton(onClick = onKey) { Text("私钥") }
                OutlinedButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun HostEditorDialog(
    initial: HostRepository.HostConfig?,
    onDismiss: () -> Unit,
    onSave: (HostRepository.HostConfig) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var session by remember { mutableStateOf(initial?.session ?: "test") }
    var herdrBin by remember { mutableStateOf(initial?.herdrBin ?: "/home/luxiang/.local/bin/herdr") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加主机" else "编辑主机") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text("名称（可选）") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("主机地址") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("端口") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true)
                }
                OutlinedTextField(session, { session = it }, label = { Text("herdr 会话") }, singleLine = true)
                OutlinedTextField(herdrBin, { herdrBin = it }, label = { Text("herdr 绝对路径") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        HostRepository.HostConfig(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString().take(8),
                            label = label.trim().ifBlank { host.trim() },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 22,
                            user = user.trim(),
                            session = session.trim().ifBlank { "test" },
                            herdrBin = herdrBin.trim(),
                        ),
                    )
                },
                enabled = host.isNotBlank() && user.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun KeyDialog(
    host: HostsViewModel.HostState,
    onDismiss: () -> Unit,
    onImport: (String) -> Result<Unit>,
    onDeleteKey: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("私钥 · ${host.cfg.label}") },
        text = {
            Column {
                Text(
                    "粘贴 OpenSSH 私钥（-----BEGIN OPENSSH PRIVATE KEY-----）。只写入本机 app 私有目录，不外传。",
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    text, { text = it; error = null },
                    label = { Text("私钥内容") },
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = onImport(text)
                if (r.isSuccess) onDismiss() else error = r.exceptionOrNull()?.message
            }) { Text("导入") }
        },
        dismissButton = {
            Row {
                if (host.hasKey) TextButton(onClick = onDeleteKey) { Text("删除已有私钥") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/**
 * UX-05b：语言选择。
 * 只改 UI 语言，不翻译代码/命令/Agent transcript。
 * Android 13+ 走官方 `LocaleManager`（系统设置里也能看到本 App 的语言项）；
 * 13 以下不做 AppCompat 迁移（会牵动整个 Activity 基类），如实提示"跟随系统"。
 */
@Composable
private fun LanguageSection(context: android.content.Context) {
    val isAtLeast33 = android.os.Build.VERSION.SDK_INT >= 33
    var current by remember { mutableStateOf(currentAppLanguage(context)) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("语言 / Language", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (isAtLeast33) "只影响界面文案，不翻译代码、命令与 Agent 回复。"
            else "当前系统版本不支持应用内切换语言（需 Android 13+），将跟随系统。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("" to "跟随系统", "zh-CN" to "简体中文", "en" to "English").forEach { (tag, label) ->
                androidx.compose.material3.FilterChip(
                    selected = current == tag,
                    enabled = isAtLeast33,
                    onClick = {
                        applyAppLanguage(context, tag)
                        current = tag
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

private fun currentAppLanguage(context: android.content.Context): String {
    if (android.os.Build.VERSION.SDK_INT < 33) return ""
    val lm = context.getSystemService(android.app.LocaleManager::class.java) ?: return ""
    val locales = lm.applicationLocales
    return if (locales.isEmpty) "" else locales[0].toLanguageTag()
}

private fun applyAppLanguage(context: android.content.Context, tag: String) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val lm = context.getSystemService(android.app.LocaleManager::class.java) ?: return
    lm.applicationLocales = if (tag.isEmpty()) android.os.LocaleList.getEmptyLocaleList()
    else android.os.LocaleList.forLanguageTags(tag)
}
