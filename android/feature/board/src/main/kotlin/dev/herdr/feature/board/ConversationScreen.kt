package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.Pane
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.StatusText
import dev.herdr.feature.board.design.TouchTargetIconButton

@Composable
fun ConversationScreen(
    pane: Pane?,
    transcript: HostsViewModel.TranscriptState,
    output: String,
    composer: HostConnectionManager.ComposerState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismissOutcome: () -> Unit,
    onReload: () -> Unit,
    onOpenTechSheet: () -> Unit,
    onBack: () -> Unit,
    onQuote: (String) -> Unit = {},
    /** UX-04b：打开「发送记录」（Pending/Unknown 的持久入口）。 */
    onOpenSendRecord: () -> Unit = {},
    /** UX-03e：连接中断/重连中时为 true —— 保留缓存内容，但明确标注"正在重新同步"。 */
    resyncing: Boolean = false,
) {
    val palette = LocalHerdrPalette.current
    if (pane == null) {
        Text("会话已不存在", Modifier.padding(Space.xxl))
        return
    }
    var searchOpen by remember(pane.paneId) { mutableStateOf(false) }
    // Claude 的会话文件由方案 C 定位，pane 上没有路径；以 transcript 状态为准
    val hasTranscript = pane.agentSessionPath != null || transcript.structured
    val truncatedStart = transcript.truncated
    val title = displayTitle(pane)

    Scaffold(
        containerColor = palette.background,
        topBar = {
            // UX-01a：不用固定高度的 TopAppBar（大字体下会裁剪并压住首条消息）。
            // 行高由内容决定：标题单行省略，副标题仅在正常字号显示，返回/更多恒为 48dp。
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.background)
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = Space.xs, end = Space.s, top = Space.xs, bottom = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TouchTargetIconButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = stringResource(R.string.conversation_back),
                        onClick = onBack,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 原型：副标题 = Agent · 主机 · 状态（状态用文字，不用彩色胶囊）
                        Text(
                            conversationSubtitle(pane, StatusText.label(pane.status)),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TouchTargetIconButton(
                        iconRes = R.drawable.ic_search,
                        contentDescription = stringResource(R.string.search_entry),
                        onClick = { searchOpen = true },
                    )
                    TouchTargetIconButton(
                        iconRes = R.drawable.ic_more,
                        contentDescription = stringResource(R.string.conversation_more),
                        onClick = onOpenTechSheet,
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = palette.borderSubtle)
            }
        },
        bottomBar = {
            ComposerBar(composer, pane, resyncing, onTextChange, onSend, onDismissOutcome, onOpenSendRecord)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (pane.status == AgentStatus.BLOCKED) {
                BlockedNotice()
            }
            if (resyncing) {
                Surface(color = palette.surfaceRaised, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "重新同步中 · 下面显示的是已缓存内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.xs),
                    )
                }
            }
            when {
                !hasTranscript -> TerminalFallback(output, onReload, transcript.error, pane.agentSessionId != null)
                transcript.loading -> LoadingBlock("读取会话…")
                transcript.error != null -> ErrorBlock(transcript.error, onReload)
                transcript.messages.isEmpty() -> EmptyBlock("还没有消息。发一句话开始。")
                else -> MessageList(
                    transcript.messages, pane, onQuote, searchOpen, { searchOpen = false },
                    truncatedStart = truncatedStart,
                )
            }
        }
    }
}
