package io.github.luxiang0412.kelpie.settings

import io.github.luxiang0412.kelpie.core.data.ApprovalsPath
import io.github.luxiang0412.kelpie.core.herdr.t

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.luxiang0412.kelpie.core.data.PairingPayload
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.luxiang0412.kelpie.core.data.HostRepository
import io.github.luxiang0412.kelpie.container
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSize
import io.github.luxiang0412.kelpie.ui.BackTopBar

/**
 * 添加 / 编辑主机。私钥只在本页粘贴，保存后写入 app 私有目录，界面上永远不再显示原文。
 * 新主机可以改用二维码配对（`companion/pair/kelpie_pair.py`）：扫码或从图片识别，进入确认页。
 */
@Composable
fun HostEditRoute(hostId: String?, onDone: () -> Unit, onPair: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val repo = ctx.container.hosts
    val existing = remember(hostId) { hostId?.let { repo.get(it) } }
    val hasKey = remember(hostId) { hostId?.let { repo.hasKey(it) } ?: false }
    val c = Kelpie.colors

    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(existing?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by rememberSaveable { mutableStateOf(existing?.user.orEmpty()) }
    var session by rememberSaveable { mutableStateOf(existing?.session ?: "test") }
    var bin by rememberSaveable { mutableStateOf(existing?.herdrBin.orEmpty()) }
    var approvalsDir by rememberSaveable { mutableStateOf(existing?.approvalsDir?.takeIf { it != ApprovalsPath.DEFAULT }.orEmpty()) }
    var key by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun handleCode(text: String?) {
        when {
            text == null -> error = t("没有识别到二维码", "No QR code found")
            !PairingPayload.looksLikePairing(text) -> error = t("这不是 Kelpie 的配对二维码", "This isn't a Kelpie pairing code")
            else -> { error = null; onPair(text) }
        }
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r -> if (r.contents != null) handleCode(r.contents) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handleCode(QrImage.decode(ctx, uri))
    }

    fun save() {
        val p = port.toIntOrNull()
        error = when {
            host.isBlank() -> t("请填写地址", "Enter an address")
            user.isBlank() -> t("请填写用户名", "Enter a username")
            p == null || p !in 1..65535 -> t("端口不对", "Invalid port")
            session.isBlank() -> t("请填写 herdr 会话名", "Enter the herdr session name")
            existing == null && key.isBlank() -> t("新主机需要粘贴私钥", "A new host needs a private key")
            key.isNotBlank() && !key.contains("PRIVATE KEY-----") -> t("内容不像私钥（缺少 PRIVATE KEY 头）", "This doesn't look like a private key (no PRIVATE KEY header)")
            else -> ApprovalsPath.normalize(approvalsDir).exceptionOrNull()?.message
        }
        if (error != null) return
        val id = existing?.id ?: repo.newId()
        // 先导入私钥再写配置：配置一变连接层就会重连，那时私钥必须已经就位
        if (key.isNotBlank()) repo.importKey(id, key).onFailure { error = it.message; return }
        repo.upsert(
            HostRepository.HostConfig(
                id = id,
                label = label.trim().ifBlank { host.trim() },
                host = host.trim(), port = p!!, user = user.trim(), session = session.trim(),
                herdrBin = bin.trim().ifBlank { "/home/${user.trim()}/.local/bin/herdr" },
                approvalsDir = ApprovalsPath.normalize(approvalsDir).getOrThrow(),
            ),
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().background(c.bgGrouped).statusBarsPadding().imePadding()) {
        BackTopBar(if (existing == null) t("添加主机", "Add host") else t("编辑主机", "Edit host"), onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (existing == null) {
                GroupLabel(t("用二维码添加（推荐）", "Add with a QR code (recommended)"))
                Group {
                    ActionRow(KelpieIcons.Scan, t("扫描二维码", "Scan QR code")) {
                        scan.launch(
                            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
                                .setOrientationLocked(false).setPrompt(t("对准电脑上 kelpie_pair.py 显示的二维码", "Point at the code shown by kelpie_pair.py")),
                        )
                    }
                    Divider()
                    ActionRow(KelpieIcons.File, t("从图片识别", "From an image")) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                Text(
                    t("在开发机上运行 companion/pair/kelpie_pair.py，会显示带地址、主机指纹和手机专用私钥的二维码。也可以在下面手动填写。", "Run companion/pair/kelpie_pair.py on the dev machine to show a code with the address, host fingerprints and a key for this phone. Or fill in the fields below."),
                    style = Kelpie.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            GroupLabel(t("连接", "Connection"))
            Group {
                Field(t("名称", "Name"), label, { label = it }, t("例如：家里的台式机", "e.g. Home desktop"))
                Divider()
                Field(t("地址", "Address"), host, { host = it }, t("IP 或域名", "IP or hostname"), KeyboardType.Uri)
                Divider()
                Field(t("端口", "Port"), port, { port = it.filter(Char::isDigit).take(5) }, "22", KeyboardType.Number)
                Divider()
                Field(t("用户名", "Username"), user, { user = it }, t("SSH 用户", "SSH user"))
            }
            GroupLabel("herdr")
            Group {
                Field(t("会话", "Session"), session, { session = it }, "test")
                Divider()
                Field(t("程序路径", "Binary path"), bin, { bin = it }, t("默认 ~/.local/bin/herdr", "Default ~/.local/bin/herdr"))
            }
            GroupLabel(t("手机审批", "Phone approvals"))
            Group {
                Field(t("请求目录", "Request directory"), approvalsDir, { approvalsDir = it }, t("默认 ${ApprovalsPath.DEFAULT}", "Default ${ApprovalsPath.DEFAULT}"), KeyboardType.Uri)
            }
            Text(
                t("和开发机上审批 hook 的 KELPIE_APPROVALS_DIR 保持一致。可以写绝对路径，或者以 ~/ 开头。", "Must match KELPIE_APPROVALS_DIR of the approval hook on the dev machine. Absolute path or starting with ~/."),
                style = Kelpie.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            GroupLabel(if (hasKey) t("私钥（已导入，留空表示不更换）", "Private key (imported; leave empty to keep)") else t("私钥", "Private key"))
            Group {
                Field("", key, { key = it }, t("粘贴 OpenSSH 私钥（-----BEGIN … PRIVATE KEY-----）", "Paste an OpenSSH private key (-----BEGIN … PRIVATE KEY-----)"), secret = true, singleLine = false)
            }
            Text(
                t("私钥只保存在这台手机的 App 私有目录里。第一次连接时会请你核对主机指纹。", "The key stays in this app's private storage on this phone. You'll be asked to verify the host fingerprint on first connect."),
                style = Kelpie.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            error?.let { Text(it, style = Kelpie.type.metaStrong, color = c.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    t("删除这台主机", "Delete this host"),
                    style = Kelpie.type.label, color = c.error,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.xl)).background(c.surface)
                        .clickable { confirmDelete = true }.padding(16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            KelpieButton(t("保存", "Save"), ::save, Modifier.fillMaxWidth(), height = KelpieSize.primaryButton)
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text(t("删除 ${existing.label}？", "Delete ${existing.label}?"), style = Kelpie.type.headline, color = c.textPrimary) },
            text = { Text(t("会同时删除这台主机的私钥。远端的 agent 不受影响。", "This also deletes the host's private key. Remote agents are not affected."), style = Kelpie.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { repo.remove(existing.id); confirmDelete = false; onDone() }) { Text(t("删除", "Delete"), color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("取消", "Cancel"), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val c = Kelpie.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = text, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = c.iconMuted, modifier = Modifier.size(20.dp))
        Text(text, style = Kelpie.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Icon(KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
) {
    val c = Kelpie.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (label.isNotEmpty()) Text(label, style = Kelpie.type.body, color = c.textPrimary, modifier = Modifier.padding(end = 12.dp).fillMaxWidth(0.28f))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Kelpie.type.body, color = c.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                maxLines = if (singleLine) 1 else 6,
                textStyle = (if (secret) Kelpie.type.code else Kelpie.type.body).copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.textPrimary),
                visualTransformation = if (secret && value.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
                // 字段名和占位提示是旁边独立的 Text，读屏不会把它们和输入框关联起来：显式给输入框一个名字
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label.ifEmpty { placeholder } },
            )
        }
    }
}
