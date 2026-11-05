package io.github.luxiang0412.kelpie.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.luxiang0412.kelpie.conversation.model.ConversationItem
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import io.github.luxiang0412.kelpie.core.herdr.t

/**
 * 对话里的 AskUserQuestion：按题目 / 选项排版，不再是一行 JSON。
 *
 * - 等你选：一次显示一道题，上面一排题目标签可切换（对应终端里的题目 tab）；
 * - 答完 / 取消：收起成「题 → 答」，点开看全部题目和选项。
 *
 * 只展示，不能在这里作答：终端里的选择框要用方向键操作，手机看不到光标位置，
 * 发按键选错了撤不回来。以后从手机作答要走审批 hook（updatedInput.answers），不发按键。
 */
@Composable
internal fun AskQuestionCard(tool: ConversationItem.Tool) {
    val c = Kelpie.colors
    val pending = tool.result == null
    val answered = tool.answers != null
    // 有结果但没有结构化回答：被拒绝 / Esc 取消 / 出错，原样给出结果文字
    val dismissed = !pending && !answered
    val qs = tool.questions
    var open by rememberSaveable(tool.index) { mutableStateOf(false) }
    var page by rememberSaveable(tool.index) { mutableIntStateOf(0) }
    val at = page.coerceIn(0, qs.lastIndex)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(KelpieRadius.l))
            .border(if (pending) 1.5.dp else 1.dp, if (pending) c.attention else c.outlineCard, RoundedCornerShape(KelpieRadius.l))
            .then(if (pending) Modifier else Modifier.clickable(onClickLabel = if (open) t("收起", "Collapse") else t("展开", "Expand")) { open = !open })
            .animateContentSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (pending || open) 12.dp else 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KelpieIcons.Question, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
            Text(
                when {
                    pending && qs.size > 1 -> t("提问 · 第 ${at + 1} / ${qs.size} 题", "Question ${at + 1} of ${qs.size}")
                    qs.size > 1 -> t("提问 · ${qs.size}", "Questions · ${qs.size}")
                    else -> t("提问", "Question")
                },
                style = Kelpie.type.metaStrong, color = c.textSecondary, modifier = Modifier.weight(1f),
            )
            when {
                pending -> Text(t("等你选择", "Waiting for you"), style = Kelpie.type.caption, color = c.attentionText)
                answered -> Icon(KelpieIcons.Check, t("已回答", "Answered"), tint = c.done, modifier = Modifier.size(14.dp))
                else -> Text(t("未回答", "Not answered"), style = Kelpie.type.caption, color = c.textTertiary)
            }
            if (!pending) Icon(if (open) KelpieIcons.ChevronDown else KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(14.dp))
        }
        when {
            pending -> {
                if (qs.size > 1) QuestionTabs(qs, at, onPick = { page = it })
                QuestionBlock(qs[at], answer = null, answered = false, showHeader = qs.size == 1)
                Row(
                    Modifier.fillMaxWidth().background(c.hairline).heightIn(min = 1.dp, max = 1.dp),
                ) {}
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("在电脑终端里选择", "Choose in the terminal on your computer"), style = Kelpie.type.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
                    if (at > 0) PageLink(t("‹ 上一题", "‹ Previous")) { page = at - 1 }
                    if (at < qs.lastIndex) PageLink(t("下一题 ›", "Next ›")) { page = at + 1 }
                }
            }
            open -> qs.forEach { q -> QuestionBlock(q, tool.answers?.get(q.question), answered, showHeader = true) }
            else -> qs.forEach { q -> AnswerLine(q, tool.answers?.get(q.question)) }
        }
        if (dismissed) {
            Text(tool.result.orEmpty(), style = Kelpie.type.caption, color = c.textTertiary, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 题目标签：等你选时用来切换题目；当前题实心。没有 header 的题用“第 n 题”。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionTabs(qs: List<AskQuestion>, current: Int, onPick: (Int) -> Unit) {
    val c = Kelpie.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        qs.forEachIndexed { i, q ->
            val on = i == current
            val label = (q.header ?: t("第 ${i + 1} 题", "Q${i + 1}")) + if (q.multiSelect) t(" · 多选", " · multi") else ""
            Box(
                Modifier.clip(RoundedCornerShape(KelpieRadius.m))
                    .background(if (on) c.primary else c.surface)
                    .border(1.dp, if (on) c.primary else c.outlineCard, RoundedCornerShape(KelpieRadius.m))
                    .clickable(role = Role.Tab) { onPick(i) }
                    .semantics { selected = on }
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Kelpie.type.caption, color = if (on) c.onPrimary else c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PageLink(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(KelpieRadius.s)).clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Kelpie.type.metaStrong, color = Kelpie.colors.attentionText)
    }
}

/** 收起时的一行：题目（有 header 用 header）→ 回答。 */
@Composable
private fun AnswerLine(q: AskQuestion, answer: String?) {
    val c = Kelpie.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q.header ?: q.question, style = Kelpie.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            answer?.let { a -> a.split(", ").joinToString(t("、", ", ")) { part -> q.options.firstOrNull { it.label == part.trim() }?.plainLabel ?: part } }
                ?: t("没有回答", "No answer"),
            style = Kelpie.type.metaStrong, color = if (answer != null) c.textPrimary else c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuestionBlock(q: AskQuestion, answer: String?, answered: Boolean, showHeader: Boolean) {
    val c = Kelpie.colors
    // 多选题的回答是逗号分隔的标签
    val picked = answer?.split(", ")?.map { it.trim() }?.toSet().orEmpty()
    val matched = q.options.filter { it.label in picked || it.plainLabel in picked }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeader) q.header?.let { HeaderTag(it) }
        Text(q.question, style = Kelpie.type.body, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        if (q.multiSelect) Text(t("可多选", "Multiple choice"), style = Kelpie.type.caption, color = c.textTertiary)
        q.options.forEachIndexed { i, op ->
            val chosen = op in matched
            val dim = answered && !chosen
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (chosen) Icon(KelpieIcons.Check, t("选了", "Chosen"), tint = c.done, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}.", style = Kelpie.type.meta, color = if (dim) c.textTertiary else c.textSecondary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            op.plainLabel, style = if (chosen) Kelpie.type.metaStrong else Kelpie.type.meta,
                            color = if (dim) c.textTertiary else c.textPrimary, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (op.recommended) RecommendedTag()
                    }
                    if (!op.description.isNullOrBlank() && !dim) {
                        Text(op.description!!, style = Kelpie.type.caption, color = c.textSecondary)
                    }
                }
            }
        }
        // 回答不是任何一个选项（Type something 里打的字）
        if (answer != null && matched.isEmpty()) {
            Text(t("回答：$answer", "Answer: $answer"), style = Kelpie.type.metaStrong, color = c.textPrimary)
        }
    }
}

@Composable
internal fun HeaderTag(text: String, container: Color = Kelpie.colors.surfaceSubtle, content: Color = Kelpie.colors.textSecondary) {
    Text(
        text, style = Kelpie.type.caption, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(KelpieRadius.s)).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun RecommendedTag(container: Color = Kelpie.colors.doneContainer, content: Color = Kelpie.colors.done) {
    Text(
        t("推荐", "Recommended"), style = Kelpie.type.micro, color = content,
        modifier = Modifier.clip(RoundedCornerShape(KelpieRadius.s)).background(container).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * 首页卡片里的选择题摘要：一排题目标签（多题时编号，第一题实心）+ 白底块里的第一题和选项标题。
 * 选项说明不在首页显示，卡片高度可控。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskQuestionPreview(questions: List<AskQuestion>) {
    val c = Kelpie.colors
    val q = questions.firstOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (questions.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                questions.forEachIndexed { i, qq ->
                    val label = "${i + 1} " + (qq.header ?: t("第 ${i + 1} 题", "Q${i + 1}"))
                    if (i == 0) HeaderTag(label, container = c.primary, content = c.onPrimary)
                    else HeaderTag(label, container = c.attentionContainerInner, content = c.attentionOnContainerMeta)
                }
            }
        } else q.header?.let { HeaderTag(it, container = c.attentionContainerInner, content = c.attentionOnContainerBody) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.m)).background(c.attentionContainerInner).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(q.question, style = Kelpie.type.bodyCard, color = c.attentionOnContainerBody, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                q.options.take(MAX_OPTIONS).forEachIndexed { i, op ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${i + 1}.", style = Kelpie.type.meta, color = c.attentionOnContainerMeta)
                        Text(
                            op.plainLabel, style = Kelpie.type.meta, color = c.attentionOnContainerBody,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (op.recommended) RecommendedTag()
                    }
                }
                if (q.options.size > MAX_OPTIONS) {
                    Text(t("还有 ${q.options.size - MAX_OPTIONS} 个选项", "${q.options.size - MAX_OPTIONS} more options"), style = Kelpie.type.caption, color = c.attentionOnContainerMeta)
                }
            }
        }
    }
}

private const val MAX_OPTIONS = 4
