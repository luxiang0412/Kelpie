package dev.herdr.signal.settings

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
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostRepository
import dev.herdr.signal.container
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalButton
import dev.herdr.signal.designsystem.SignalRadius
import dev.herdr.signal.designsystem.SignalSize
import dev.herdr.signal.ui.BackTopBar

/**
 * 添加 / 编辑主机。私钥只在本页粘贴，保存后写入 app 私有目录，界面上永远不再显示原文。
 * 二维码配对（规范 v1 范围外）以后在这里加入口。
 */
@Composable
fun HostEditRoute(hostId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ctx.container.hosts
    val existing = remember(hostId) { hostId?.let { repo.get(it) } }
    val hasKey = remember(hostId) { hostId?.let { repo.hasKey(it) } ?: false }
    val c = Signal.colors

    var label by rememberSaveable { mutableStateOf(existing?.label.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(existing?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by rememberSaveable { mutableStateOf(existing?.user.orEmpty()) }
    var session by rememberSaveable { mutableStateOf(existing?.session ?: "test") }
    var bin by rememberSaveable { mutableStateOf(existing?.herdrBin.orEmpty()) }
    var key by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val p = port.toIntOrNull()
        error = when {
            host.isBlank() -> "请填写地址"
            user.isBlank() -> "请填写用户名"
            p == null || p !in 1..65535 -> "端口不对"
            session.isBlank() -> "请填写 herdr 会话名"
            existing == null && key.isBlank() -> "新主机需要粘贴私钥"
            key.isNotBlank() && !key.contains("PRIVATE KEY-----") -> "内容不像私钥（缺少 PRIVATE KEY 头）"
            else -> null
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
            ),
        )
        onDone()
    }

    Column(Modifier.fillMaxSize().background(c.bgGrouped).statusBarsPadding().imePadding()) {
        BackTopBar(if (existing == null) "添加主机" else "编辑主机", onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupLabel("连接")
            Group {
                Field("名称", label, { label = it }, "例如：家里的台式机")
                Divider()
                Field("地址", host, { host = it }, "IP 或域名", KeyboardType.Uri)
                Divider()
                Field("端口", port, { port = it.filter(Char::isDigit).take(5) }, "22", KeyboardType.Number)
                Divider()
                Field("用户名", user, { user = it }, "SSH 用户")
            }
            GroupLabel("herdr")
            Group {
                Field("会话", session, { session = it }, "test")
                Divider()
                Field("程序路径", bin, { bin = it }, "默认 ~/.local/bin/herdr")
            }
            GroupLabel(if (hasKey) "私钥（已导入，留空表示不更换）" else "私钥")
            Group {
                Field("", key, { key = it }, "粘贴 OpenSSH 私钥（-----BEGIN … PRIVATE KEY-----）", secret = true, singleLine = false)
            }
            Text(
                "私钥只保存在这台手机的 App 私有目录里。第一次连接时会请你核对主机指纹。",
                style = Signal.type.caption, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            error?.let { Text(it, style = Signal.type.metaStrong, color = c.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "删除这台主机",
                    style = Signal.type.label, color = c.error,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.xl)).background(c.surface)
                        .clickable { confirmDelete = true }.padding(16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            SignalButton("保存", ::save, Modifier.fillMaxWidth(), height = SignalSize.primaryButton)
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("删除 ${existing.label}？", style = Signal.type.headline, color = c.textPrimary) },
            text = { Text("会同时删除这台主机的私钥。远端的 agent 不受影响。", style = Signal.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { repo.remove(existing.id); confirmDelete = false; onDone() }) { Text("删除", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消", color = c.textSecondary) } },
        )
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
    val c = Signal.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (label.isNotEmpty()) Text(label, style = Signal.type.body, color = c.textPrimary, modifier = Modifier.padding(end = 12.dp).fillMaxWidth(0.28f))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Signal.type.body, color = c.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 4,
                maxLines = if (singleLine) 1 else 6,
                textStyle = (if (secret) Signal.type.code else Signal.type.body).copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.textPrimary),
                visualTransformation = if (secret && value.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
