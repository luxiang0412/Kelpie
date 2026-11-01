package dev.herdr.feature.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostRepository
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.SectionTitleStyle
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.TouchTargetIconButton
import java.util.UUID

/**
 * 新增/编辑主机（UX-08a 视觉重做）。
 *
 * 结构按 DESIGN-SYSTEM §4/§5：全屏页 + 三段分组（连接 / Herdr / 认证），
 * 与长 Prompt 编辑页同一套头部与间距；私钥可直接粘贴或从文件选择。
 */
@Composable
fun HostEditorDialog(
    initial: HostRepository.HostConfig?,
    onDismiss: () -> Unit,
    onImportKey: (String, String) -> Result<Unit>,
    onSave: (HostRepository.HostConfig) -> Unit,
) {
    val palette = LocalHerdrPalette.current
    val context = LocalContext.current

    var label by remember { mutableStateOf(initial?.label ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var session by remember { mutableStateOf(initial?.session ?: "test") }
    var herdrBin by remember { mutableStateOf(initial?.herdrBin ?: "/home/luxiang/.local/bin/herdr") }
    var keyText by remember { mutableStateOf("") }
    var keyFileName by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }

    // SAF：从文件选择私钥（只读一次，内容读入内存后交给导入流程）
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.onSuccess { text ->
            if (text.isNullOrBlank()) {
                keyError = "文件为空"
            } else {
                keyText = text
                keyFileName = uri.lastPathSegment?.substringAfterLast('/')
                keyError = null
            }
        }.onFailure { keyError = it.message ?: "读取文件失败" }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = palette.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // 头部与长编辑页一致：← + 标题 + 右侧主操作
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TouchTargetIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = stringResource(R.string.hosts_1),
                        onClick = onDismiss,
                    )
                    Text(
                        stringResource(if (initial == null) R.string.hosts_3 else R.string.hosts_7),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val cfg = HostRepository.HostConfig(
                                id = initial?.id ?: UUID.randomUUID().toString().take(8),
                                label = label.trim().ifBlank { host.trim() },
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 22,
                                user = user.trim(),
                                session = session.trim().ifBlank { "test" },
                                herdrBin = herdrBin.trim(),
                            )
                            if (keyText.isNotBlank()) {
                                val r = onImportKey(cfg.id, keyText)
                                if (r.isFailure) {
                                    keyError = r.exceptionOrNull()?.message ?: "私钥导入失败"
                                    return@TextButton
                                }
                            }
                            onSave(cfg)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.hosts_9), style = MaterialTheme.typography.labelLarge)
                    }
                }
                HorizontalDivider(color = palette.borderSubtle)

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.page, vertical = Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    GroupTitle(stringResource(R.string.hosts_group_connection))
                    Field(label, { label = it }, stringResource(R.string.hosts_24), palette)
                    Field(host, { host = it }, stringResource(R.string.hosts_13), palette)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Field(
                            port,
                            { port = it.filter(Char::isDigit) },
                            stringResource(R.string.hosts_15),
                            palette,
                            Modifier.width(110.dp),
                            KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Field(user, { user = it }, stringResource(R.string.hosts_14), palette, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(Space.s))
                    GroupTitle(stringResource(R.string.hosts_group_herdr))
                    Field(session, { session = it }, stringResource(R.string.hosts_16), palette)
                    Field(herdrBin, { herdrBin = it }, stringResource(R.string.hosts_herdr_bin), palette)

                    Spacer(Modifier.height(Space.s))
                    GroupTitle(stringResource(R.string.hosts_group_auth))
                    Text(
                        stringResource(R.string.hosts_key_input_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { runCatching { picker.launch(arrayOf("*/*")) } },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.hosts_key_pick_file), style = MaterialTheme.typography.labelLarge)
                        }
                        keyFileName?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(start = Space.xs),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; keyError = null; keyFileName = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.hosts_key_input_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                            )
                        },
                        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors(palette),
                    )
                    keyError?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = palette.errorFg)
                    }
                    Spacer(Modifier.height(Space.xxl))
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = SectionTitleStyle,
        color = LocalHerdrPalette.current.textSecondary,
        modifier = Modifier.padding(top = Space.s),
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    palette: dev.herdr.feature.board.design.HerdrPalette,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(palette),
    )
}

/** 统一输入框样式：透明底、1dp 低对比边、聚焦 1.5dp accent（不叠多层框）。 */
@Composable
private fun fieldColors(palette: dev.herdr.feature.board.design.HerdrPalette) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.focusRing,
        unfocusedBorderColor = palette.borderSubtle,
        disabledBorderColor = palette.borderSubtle,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedLabelColor = palette.focusRing,
        unfocusedLabelColor = palette.textSecondary,
        cursorColor = palette.focusRing,
    )
