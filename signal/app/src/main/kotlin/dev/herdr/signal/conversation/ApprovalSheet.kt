package dev.herdr.signal.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.runtime.ApprovalItem
import dev.herdr.core.data.runtime.ApprovalOutcome
import dev.herdr.signal.container
import dev.herdr.signal.designsystem.HoldToConfirmButton
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalButton
import dev.herdr.signal.designsystem.SignalIcons
import dev.herdr.signal.designsystem.SignalRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 结构化审批弹层（规范 5.13 ApprovalSheet）。
 * 只处理带 nonce 的文件请求；“允许”必须按住 600ms，读屏用户走二次确认对话框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(item: ApprovalItem, onDismiss: () -> Unit, onOpenConversation: (() -> Unit)?) {
    val c = Signal.colors
    val center = LocalContext.current.container.approvals
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var a11yConfirm by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val r = item.request
    val left = ((r.expiresAtMs - now) / 1000).coerceAtLeast(0)
    val expired = left <= 0

    fun answer(allow: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            result = when (val o = center.answer(item, allow)) {
                is ApprovalOutcome.Written -> null.also { onDismiss() }
                is ApprovalOutcome.Failed -> o.reason
                ApprovalOutcome.Expired -> "请求已过期"
            }
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = SignalRadius.sheet, topEnd = SignalRadius.sheet),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(listOfNotNull(r.cwd?.trimEnd('/')?.substringAfterLast('/'), r.agent, item.hostLabel).joinToString(" · "),
                style = Signal.type.meta, color = c.textSecondary)
            Text(if (r.command != null) "允许运行这条命令？" else r.question, style = Signal.type.title, color = c.textPrimary)

            if (r.command != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(SignalRadius.l)).background(c.codeBlockBg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    r.cwd?.let { Text(it.replace(Regex("^/home/[^/]+"), "~") + " $", style = Signal.type.codeSmall, color = c.codeBlockMuted) }
                    Text(r.command!!, style = Signal.type.code, color = c.codeBlockFg)
                }
                if (r.question.isNotBlank() && r.question != r.command) Text(r.question, style = Signal.type.body, color = c.textSecondary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.tool?.let { KV("工具", it) }
                KV("请求编号", r.requestId.take(8) + " · 带一次性校验码")
                KV("有效期", if (expired) "已过期" else "还剩 %d:%02d".format(left / 60, left % 60))
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(SignalIcons.InfoSmall, null, tint = c.iconMuted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text("只对这一次请求生效。电脑上已经处理过的话，这里的回答会被忽略，不会再执行一次。",
                    style = Signal.type.caption, color = c.textSecondary)
            }
            result?.let { Text(it, style = Signal.type.metaStrong, color = c.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalButton("拒绝", { answer(false) }, Modifier.weight(1f), primary = false, enabled = !busy && !expired)
                HoldToConfirmButton(
                    text = if (busy) "正在提交…" else "按住允许",
                    onConfirm = { answer(true) },
                    onAccessibilityConfirm = { a11yConfirm = true },
                    modifier = Modifier.weight(1.4f),
                    enabled = !busy && !expired,
                )
            }
            if (onOpenConversation != null) {
                Text(
                    "查看完整对话", style = Signal.type.label, color = c.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(SignalRadius.m))
                        .clickable { onDismiss(); onOpenConversation() }.padding(12.dp),
                )
            }
        }
    }

    if (a11yConfirm) {
        AlertDialog(
            onDismissRequest = { a11yConfirm = false },
            containerColor = c.surface,
            title = { Text("确认允许？", style = Signal.type.headline, color = c.textPrimary) },
            text = { Text(r.command ?: r.question, style = Signal.type.body, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { a11yConfirm = false; answer(true) }) { Text("允许", color = c.textPrimary) } },
            dismissButton = { TextButton(onClick = { a11yConfirm = false }) { Text("取消", color = c.textSecondary) } },
        )
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row {
        Text(k, style = Signal.type.meta, color = Signal.colors.textSecondary, modifier = Modifier.fillMaxWidth(0.28f))
        Text(v, style = Signal.type.meta, color = Signal.colors.textPrimary)
    }
}
