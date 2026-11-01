package dev.herdr.feature.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.feature.board.conversation.ConversationItem
import dev.herdr.feature.board.design.DisclosureIndicator
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.MonoStyle
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.animationsEnabled

@Composable
internal fun ToolRow(item: ConversationItem.Tool, stateKey: String) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    var viewFull by remember { mutableStateOf(false) }

    val hasResult = item.result != null
    // 状态语义矩阵：只反映源事实，绝不推断「执行中 / 成功」
    val statusText = when {
        hasResult && item.pairedById -> stringResource(R.string.tool_status_received)
        hasResult -> stringResource(R.string.tool_status_unmatched)
        else -> stringResource(R.string.tool_status_pending)
    }
    val preview = item.result?.let { previewLines(it, 12) }

    Surface(
        color = palette.surfaceRaised,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.borderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { expanded = !expanded }
                    .padding(start = Space.m, end = Space.s, top = Space.s, bottom = Space.s)
                    .semantics {
                        contentDescription =
                            "工具 ${item.name}，$statusText，${if (expanded) "已展开" else "已折叠"}，点击切换"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_tool),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.textSecondary),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    item.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.xs))
                DisclosureIndicator(expanded)
            }

            // Summary：最多 2 行，只显示真实的参数/命令前缀
            if (item.argumentsSummary.isNotBlank() && !expanded) {
                Text(
                    item.argumentsSummary.replace('\n', ' '),
                    style = MonoStyle,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 40.dp, end = Space.m, bottom = Space.s),
                )
            }

            AnimatedVisibility(visible = expanded, enter = if (animationsEnabled()) fadeIn() + expandVertically() else EnterTransition.None, exit = if (animationsEnabled()) fadeOut() + shrinkVertically() else ExitTransition.None) {
                Column(Modifier.padding(start = Space.m, end = Space.m, bottom = Space.m)) {
                    if (item.argumentsSummary.isNotBlank()) {
                        DetailLabel(stringResource(R.string.tool_label_args))
                        Text(
                            item.argumentsSummary,
                            style = MonoStyle,
                            color = palette.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                    val body = item.result
                    DetailLabel(stringResource(R.string.tool_label_output))
                    if (body == null) {
                        Text(
                            stringResource(R.string.tool_status_pending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    } else {
                        Text(
                            preview ?: body,
                            style = MonoStyle,
                            color = palette.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (preview != null) {
                            // 规格禁止在 LazyColumn 内嵌竖向滚动 → 完整内容进全屏页
                            TextButton(
                                onClick = { viewFull = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.tool_view_full),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    Row {
                        if (item.argumentsSummary.isNotBlank()) {
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(item.argumentsSummary)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.tool_copy_command),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        if (body != null) {
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(body)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    stringResource(R.string.tool_copy_result),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewFull && item.result != null) {
        FullOutputDialog(text = item.result, onDismiss = { viewFull = false })
    }
}

@Composable
internal fun DetailLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalHerdrPalette.current.textSecondary,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
internal fun FullOutputDialog(text: String, onDismiss: () -> Unit) {
    val palette = LocalHerdrPalette.current
    val clipboard = LocalClipboardManager.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(color = palette.surface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.heightIn(max = 560.dp).padding(Space.l)) {
                Text(
                    stringResource(R.string.tool_label_output),
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(Space.s))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text,
                        style = MonoStyle,
                        color = palette.textPrimary,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(text)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(R.string.tool_copy_result),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(
                            stringResource(R.string.composer_done),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
