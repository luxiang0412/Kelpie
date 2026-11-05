package io.github.luxiang0412.kelpie.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
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
 * 只展示，不能在这里作答：终端里的选择框要用方向键操作，手机看不到光标位置，
 * 发按键选错了撤不回来。以后从手机作答要走审批 hook（updatedInput.answers），不发按键。
 */
@Composable
internal fun AskQuestionCard(tool: ConversationItem.Tool) {
    val c = Kelpie.colors
    val answered = tool.answers != null
    // 有结果但没有结构化回答：被拒绝 / Esc 取消 / 出错，原样给出结果文字
    val dismissed = tool.result != null && tool.answers == null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(KelpieRadius.l))
            .border(1.dp, if (tool.result == null) c.attention else c.outlineCard, RoundedCornerShape(KelpieRadius.l))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KelpieIcons.Steps, null, tint = c.iconMuted, modifier = Modifier.size(16.dp))
            Text(
                t("提问", "Question") + if (tool.questions.size > 1) " · ${tool.questions.size}" else "",
                style = Kelpie.type.metaStrong, color = c.textSecondary, modifier = Modifier.weight(1f),
            )
            when {
                tool.result == null -> Text(t("等你选择", "Waiting for you"), style = Kelpie.type.caption, color = c.attentionText)
                answered -> Icon(KelpieIcons.Check, t("已回答", "Answered"), tint = c.done, modifier = Modifier.size(14.dp))
                else -> Text(t("未回答", "Not answered"), style = Kelpie.type.caption, color = c.textTertiary)
            }
        }
        tool.questions.forEach { q -> QuestionBlock(q, tool.answers?.get(q.question), answered) }
        when {
            tool.result == null -> Text(t("在电脑终端里选择", "Choose in the terminal on your computer"), style = Kelpie.type.caption, color = c.textTertiary)
            dismissed -> Text(tool.result.orEmpty(), style = Kelpie.type.caption, color = c.textTertiary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuestionBlock(q: AskQuestion, answer: String?, answered: Boolean) {
    val c = Kelpie.colors
    // 多选题的回答是逗号分隔的标签
    val picked = answer?.split(", ")?.map { it.trim() }?.toSet().orEmpty()
    val matched = q.options.filter { it.label in picked || it.plainLabel in picked }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.header?.let { HeaderTag(it) }
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
 * 首页卡片里的选择题摘要：标签 + 题目 + 选项标题（不带描述）。
 * 多道题只完整展示第一道，其余只给数量，卡片高度可控。
 */
@Composable
fun AskQuestionPreview(questions: List<AskQuestion>) {
    val c = Kelpie.colors
    val q = questions.firstOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.header?.let { HeaderTag(it, container = c.attentionContainerInner, content = c.attentionOnContainerBody) }
        Text(q.question, style = Kelpie.type.bodyCard, color = c.attentionOnContainerBody, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.m)).background(c.attentionContainerInner).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            q.options.take(MAX_OPTIONS).forEachIndexed { i, op ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${i + 1}. ${op.plainLabel}", style = Kelpie.type.meta, color = c.attentionOnContainerBody,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (op.recommended) RecommendedTag()
                }
            }
            if (q.options.size > MAX_OPTIONS) {
                Text(t("还有 ${q.options.size - MAX_OPTIONS} 个选项", "${q.options.size - MAX_OPTIONS} more options"), style = Kelpie.type.caption, color = c.attentionOnContainerMeta)
            }
        }
        if (questions.size > 1) {
            Text(t("共 ${questions.size} 个问题", "${questions.size} questions"), style = Kelpie.type.caption, color = c.attentionOnContainerMeta)
        }
    }
}

private const val MAX_OPTIONS = 4
