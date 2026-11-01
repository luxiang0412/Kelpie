package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.feature.board.design.ComposerGutter
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.TouchTargetIconButton

@Composable
internal fun ComposerBar(
    composer: HostConnectionManager.ComposerState,
    pane: Pane,
    offline: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissOutcome: () -> Unit,
    onOpenSendRecord: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    // UX-04a（CMP-STYLE-001 暂定）：轻量双层 Surface —— 外层是页面底色，
    // 输入容器用 surface + 20dp 圆角（窄屏 18dp），Send 是唯一 Accent 操作。
    var focusedComposer by remember { mutableStateOf(false) }
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val gutter = if (compact) ComposerGutter.compact else ComposerGutter.normal
    val radius = if (compact) 18.dp else 20.dp
    var longEditor by remember(pane.paneId) { mutableStateOf(false) }
    var confirmResend by remember { mutableStateOf(false) }
    val hasAgent = pane.agent != null
    // COMPOSER-SPEC §5.1 OfflineDraft：离线/不可写时 Send 直接禁用（不让用户点了才被拒）
    val canSend = hasAgent && !composer.sending && composer.text.isNotBlank() && !offline

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = gutter, vertical = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = if (screenWidth >= 600) 720.dp else Int.MAX_VALUE.dp)
                .fillMaxWidth(),
        ) {
        // COMPOSER-SPEC §5.1 IdentityUncertain：身份不可靠（无 agent_session）且有草稿时，
        // 明确告知草稿归属不确定；发送前仍会走 NeedsConfirm，不会静默发到新 Agent。
        if (pane.agentSessionPath.isNullOrBlank() && composer.text.isNotBlank()) {
            Text(
                "会话身份无法完全确认：这份草稿可能来自之前的 Agent，发送前会再次校验目标",
                style = MaterialTheme.typography.labelSmall,
                color = palette.statusFg(AgentStatus.UNKNOWN),
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }

        if (offline) {
            Text(
                "已离线，可继续编辑草稿（不会排队发送）",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = Space.xs),
            )
        }

        // 发送结果：Accepted / Rejected / Unknown 三态文案不同，TalkBack 也读得到
        composer.message?.let { msg ->
            val color = when (composer.outcome) {
                is WriteOutcome.Accepted -> palette.statusFg(AgentStatus.DONE)
                is WriteOutcome.Rejected -> palette.statusFg(AgentStatus.BLOCKED)
                is WriteOutcome.Unknown -> palette.statusFg(AgentStatus.UNKNOWN)
                else -> palette.textSecondary
            }
            Row(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissOutcome, modifier = Modifier.heightIn(min = 48.dp)) { Text("知道了") }
            }
        }

        // Unknown：保留待确认文本，重发必须显式确认（会产生新的 Action ID）
        composer.pendingText?.let { pending ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待确认：${pending.lineSequence().first().take(40)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.statusFg(AgentStatus.UNKNOWN),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onOpenSendRecord,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.composer_review_action)) }
                TextButton(
                    onClick = { confirmResend = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("重新发送…") }
            }
        }

        // 只保留一层容器：平时 1dp borderSubtle，聚焦时 2dp accent（不再叠加输入框自身描边）
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(radius),
            border = androidx.compose.foundation.BorderStroke(
                if (focusedComposer) 2.dp else 1.dp,
                if (focusedComposer) palette.focusRing else palette.borderSubtle,
            ),
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 140.dp)
                        .onFocusChanged { focusedComposer = it.isFocused }
                        .semantics {
                            contentDescription = "指令输入框"
                            stateDescription = when {
                                composer.sending -> "发送中"
                                composer.text.isBlank() -> "空"
                                else -> "${composer.text.length} 个字符"
                            }
                        },
                    placeholder = {
                        Text(
                            if (!hasAgent) stringResource(R.string.composer_no_agent) else stringResource(R.string.composer_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    // 1–2 行起步，最多约 5 行后内部滚动；Enter 插入换行（不自动发送）
                    minLines = 1,
                    maxLines = 5,
                    enabled = hasAgent && !composer.sending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    ),
                )
                TouchTargetIconButton(
                    iconRes = dev.herdr.feature.board.R.drawable.ic_fullscreen,
                    contentDescription = "打开长 Prompt 编辑",
                    onClick = { longEditor = true },
                )
                Surface(
                    color = if (canSend) palette.accent else palette.outline,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier
                        .padding(Space.xs)
                        .size(48.dp)          // 点击区 48dp
                        .clickable(enabled = canSend, onClick = onSend)
                        .semantics { contentDescription = if (composer.sending) "发送中，请稍候" else "发送" },
                ) {
                    // 视觉圆面 40dp，落在 48dp 点击区中央（COMPOSER-SPEC §3.1）
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).background(
                                if (canSend) palette.accent else palette.outline,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_send),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(
                                    if (canSend) palette.accentOn else palette.textSecondary,
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        // COMPOSER-SPEC §2.1：默认不显示常驻说明文案（高频无效信息）。
        // 安全说明移到「技术信息」页常驻，发送结果相关文案仍按状态临时出现。
        }
    }

    if (longEditor) {
        var snapshot by remember { mutableStateOf(composer.text) }
        LongPromptEditor(
            text = composer.text,
            pane = pane,
            onChange = onTextChange,
            onCancel = {
                // UX-04b：取消回退到打开编辑前的草稿内容（不发送、不保留半成品）
                onTextChange(snapshot)
                longEditor = false
            },
            onDismiss = { longEditor = false },   // 「完成」只写回本地草稿，绝不触发发送
        )
    }

    if (confirmResend) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResend = false },
            title = { Text("重新发送待确认内容？") },
            text = {
                Text(
                    "上一次发送结果未知，远端可能已经执行。重新发送会创建新的操作记录，并可能导致重复执行。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmResend = false
                    onSend()
                }) { Text("确认重新发送") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResend = false }) { Text("取消") }
            },
        )
    }
}

@Composable
internal fun LongPromptEditor(
    text: String,
    pane: Pane,
    onChange: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = palette.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // 原型 focus-*.png：← 返回 + 标题 + 副标题（Agent · 项目 · 草稿已保存）+ 完成
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TouchTargetIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = stringResource(R.string.composer_long_cancel),
                        onClick = onCancel,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.composer_long_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                        )
                        Text(
                            conversationSubtitle(pane, stringResource(R.string.composer_draft_saved)),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.composer_long_done), style = MaterialTheme.typography.labelLarge)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = palette.borderSubtle)
                Text(
                    stringResource(R.string.composer_long_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s),
                )
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = Space.page),
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                stringResource(R.string.composer_long_hint),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${text.length} 字符",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.composer_draft_saved),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                }
                // 与原型一致：底部只保留「完成编辑」主操作；**不提供发送**（完成 = 写回草稿）
                Box(
                    Modifier.fillMaxWidth().padding(Space.page),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    androidx.compose.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.composer_long_done)) }
                }

                Text(
                    "语音仅作为转文字草稿，绝不自动发送。",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s),
                )
            }
        }
    }
}
