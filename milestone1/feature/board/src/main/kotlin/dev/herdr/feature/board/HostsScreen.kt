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
import androidx.compose.ui.res.stringResource
import dev.herdr.feature.board.R
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
                title = { Text(stringResource(R.string.hosts_0)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.hosts_1)) } },
                actions = {
                    TextButton(onClick = { isNew = true; editing = null }) { Text(stringResource(R.string.hosts_2)) }
                },
            )
        },
    ) { padding ->
        if (state.hosts.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.hosts_4), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.hosts_5))
                Spacer(Modifier.height(16.dp))
                Button(onClick = { isNew = true; editing = null }) { Text(stringResource(R.string.hosts_3)) }
            }
        } else {
            // UX-08a：整页可滚动，否则空态会占满视口，设置项（语言/后台监控）在无主机时不可达
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                // UX-05b：App 语言（跟随系统 / 简体中文 / English）。
                // 仅 Android 13+ 用系统级 per-app locale；旧版本不引入 AppCompat 架构迁移，明确说明不可用。
                LanguageSection(context)

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.hosts_25), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.hosts_26),
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
                if (host.hasKey) stringResource(R.string.hosts_19) else stringResource(R.string.hosts_20),
                style = MaterialTheme.typography.labelSmall,
                color = if (host.hasKey) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.IDLE || host.connKind == dev.herdr.core.data.HostConnectionManager.ConnKind.FAILED) {
                    Button(onClick = onSelect) { Text(stringResource(R.string.hosts_11)) }
                } else {
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.hosts_12)) }
                }
                OutlinedButton(onClick = onKey) { Text(stringResource(R.string.hosts_17)) }
                OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.hosts_6)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.hosts_8)) }
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
        title = { Text(if (initial == null) stringResource(R.string.hosts_3) else stringResource(R.string.hosts_7)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.hosts_24)) }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.hosts_13)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.hosts_15)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.hosts_14)) }, singleLine = true)
                }
                OutlinedTextField(session, { session = it }, label = { Text(stringResource(R.string.hosts_16)) }, singleLine = true)
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
            ) { Text(stringResource(R.string.hosts_9)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.hosts_10)) } },
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
        title = { Text(stringResource(R.string.hosts_key_label, host.cfg.label)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.hosts_23),
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    text, { text = it; error = null },
                    label = { Text(stringResource(R.string.hosts_18)) },
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
            }) { Text(stringResource(R.string.hosts_21)) }
        },
        dismissButton = {
            Row {
                if (host.hasKey) TextButton(onClick = onDeleteKey) { Text(stringResource(R.string.hosts_22)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.hosts_10)) }
            }
        },
    )
}

/**
 * UX-05b：语言选择。
 * 只改 UI 语言，不翻译代码/命令/Agent transcript。
 * Android 13+ 走官方 `LocaleManager`（系统设置里也能看到本 App 的语言项）；
 * 13 以下不做 AppCompat 迁移（会牵动整个 Activity 基类），如实提示stringResource(R.string.hosts_28)。
 */
@Composable
private fun LanguageSection(context: android.content.Context) {
    val isAtLeast33 = android.os.Build.VERSION.SDK_INT >= 33
    var current by remember { mutableStateOf(currentAppLanguage(context)) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.hosts_27), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (isAtLeast33) stringResource(R.string.hosts_30)
            else stringResource(R.string.hosts_31),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("" to stringResource(R.string.hosts_28), "zh-CN" to stringResource(R.string.hosts_29), "en" to "English").forEach { (tag, label) ->
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
