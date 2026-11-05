package io.github.luxiang0412.kelpie.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.luxiang0412.kelpie.core.data.runtime.AskPicker
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIconButton
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.herdr.AskQuestion
import io.github.luxiang0412.kelpie.core.herdr.t

/**
 * 在手机上回答 AskUserQuestion（画布 B 行）：一题一页 → 确认页 → 提交。
 * 提交后由 [io.github.luxiang0412.kelpie.core.data.runtime.Sender.answerQuestions] 替你在终端里按键。
 */
@Composable
internal fun AnswerSheet(
    questions: List<AskQuestion>,
    subtitle: String,
    onDismiss: () -> Unit,
    onSubmit: (List<AskPicker.Reply>) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AnswerContent(questions, subtitle, onDismiss, onSubmit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerContent(
    questions: List<AskQuestion>,
    subtitle: String,
    onDismiss: () -> Unit,
    onSubmit: (List<AskPicker.Reply>) -> Unit,
    initial: List<AskPicker.Reply>? = null,
    initialPage: Int = 0,
) {
    val c = Kelpie.colors
    val n = questions.size
    val replies = remember(questions) { mutableStateListOf(*(initial ?: questions.map { AskPicker.Reply() }).toTypedArray()) }
    // 每题自己写的文字单独存：取消勾选“自己写答案”时文字不丢
    val drafts = remember(questions) { mutableStateListOf(*(initial?.map { it.text.orEmpty() } ?: questions.map { "" }).toTypedArray()) }
    var page by remember(questions) { mutableIntStateOf(initialPage) } // n = 确认页
    val complete = { i: Int -> replies[i].isComplete(questions[i]) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            KelpieIconButton(KelpieIcons.Close, t("关闭", "Close"), onDismiss)
            Column(Modifier.weight(1f)) {
                Text(if (n > 1) t("回答 $n 个问题", "Answer $n questions") else t("回答问题", "Answer the question"), style = Kelpie.type.headline, color = c.textPrimary)
                Text(subtitle, style = Kelpie.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // 步骤：每题一个 + 确认；答完的变绿，可以点回去改
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            questions.forEachIndexed { i, q ->
                Step(
                    label = (if (complete(i) && i != page) "" else "${i + 1} ") + (q.header ?: t("第 ${i + 1} 题", "Q${i + 1}")),
                    on = i == page, done = complete(i), enabled = (0 until i).all(complete),
                ) { page = i }
            }
            Step(t("确认", "Review"), on = page == n, done = false, enabled = (0 until n).all(complete)) { page = n }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page < n) {
                val q = questions[page]
                val r = replies[page]
                Text(q.question, style = Kelpie.type.bodyReading, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                if (q.multiSelect) Text(t("可以多选", "Choose any that apply"), style = Kelpie.type.caption, color = c.textSecondary)
                Spacer(Modifier.size(2.dp))
                q.options.forEachIndexed { i, op ->
                    val sel = i in r.picked
                    OptionCard(sel, q.multiSelect, onClick = {
                        replies[page] = if (q.multiSelect) r.copy(picked = if (sel) r.picked - i else r.picked + i)
                        else AskPicker.Reply(picked = setOf(i)) // 单选：选了选项就不用自己写的
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(op.plainLabel, style = Kelpie.type.rowTitle, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
                            if (op.recommended) RecommendedTag()
                        }
                        if (!op.description.isNullOrBlank()) Text(op.description!!, style = Kelpie.type.meta, color = c.textSecondary)
                    }
                }
                // 自己写答案（= 终端里的 Type something）
                val own = r.text != null
                OptionCard(own, q.multiSelect, onClick = {
                    replies[page] = when {
                        own -> r.copy(text = null)
                        q.multiSelect -> r.copy(text = drafts[page])
                        else -> AskPicker.Reply(text = drafts[page])
                    }
                }) {
                    Text(t("自己写答案", "Write my own answer"), style = Kelpie.type.rowTitle, color = c.textPrimary)
                    if (own) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.s)).background(c.surface)
                                .border(1.dp, c.outline, RoundedCornerShape(KelpieRadius.s)).padding(10.dp),
                        ) {
                            if (drafts[page].isEmpty()) Text(t("写给 Claude 的话…", "What to tell Claude…"), style = Kelpie.type.body, color = c.textTertiary)
                            BasicTextField(
                                value = drafts[page],
                                onValueChange = { v -> drafts[page] = v; replies[page] = replies[page].copy(text = v) },
                                textStyle = Kelpie.type.body.copy(color = c.textPrimary),
                                cursorBrush = SolidColor(c.textPrimary),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                            )
                        }
                        Text(t("原样发给 Claude，和你在终端里选「Type something」一样；换行会变成空格", "Sent to Claude as is, like choosing “Type something” in the terminal; line breaks become spaces"), style = Kelpie.type.caption, color = c.textTertiary)
                    }
                }
            } else {
                Text(t("确认这 $n 个回答", if (n > 1) "Confirm these $n answers" else "Confirm your answer"), style = Kelpie.type.headline, color = c.textPrimary)
                questions.forEachIndexed { i, q ->
                    val r = replies[i]
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tag = when {
                                q.multiSelect -> t(" · 多选", " · multiple")
                                r.cleanText != null -> t(" · 自己写的", " · your own")
                                else -> ""
                            }
                            Text((q.header ?: q.question) + tag, style = Kelpie.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.clip(RoundedCornerShape(KelpieRadius.s)).clickable(role = Role.Button) { page = i }.heightIn(min = 40.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                Text(t("改", "Edit"), style = Kelpie.type.metaStrong, color = c.textSecondary)
                            }
                        }
                        Text(
                            (q.options.indices.filter { it in r.picked }.map { q.options[it].plainLabel } + listOfNotNull(r.cleanText)).joinToString(t("、", ", ")),
                            style = Kelpie.type.rowTitle, color = c.textPrimary,
                        )
                    }
                    Box(Modifier.fillMaxWidth().background(c.hairline).heightIn(min = 1.dp, max = 1.dp))
                }
                Text(
                    t("提交后，手机会替你在电脑终端的选择框里逐项选好再提交，每一步都先核对屏幕。终端里已经有人动过、或者屏幕对不上，就停下来不提交。", "Your phone will pick each answer in the terminal's question box on your computer and submit it, checking the screen at every step. If someone already started answering there, or the screen doesn't match, it stops without submitting."),
                    style = Kelpie.type.caption, color = c.textSecondary,
                )
            }
        }
        // 底部：上一题 / 下一题，确认页上是提交
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page > 0) KelpieButton(t("上一题", "Back"), onClick = { page-- }, primary = false, modifier = Modifier.weight(1f))
            if (page < n) {
                KelpieButton(
                    if (page == n - 1) t("下一步：确认", "Next: review") else t("下一题", "Next"),
                    onClick = { page++ }, enabled = complete(page), modifier = Modifier.weight(if (page > 0) 1.4f else 1f),
                )
            } else {
                KelpieButton(
                    if (n > 1) t("提交 $n 个回答", "Submit $n answers") else t("提交回答", "Submit answer"),
                    onClick = { onSubmit(replies.toList()) }, enabled = (0 until n).all(complete), modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun Step(label: String, on: Boolean, done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Kelpie.colors
    val (bg, fg, border) = when {
        on -> Triple(c.primary, c.onPrimary, c.primary)
        done -> Triple(c.doneContainer, c.done, c.doneContainer)
        else -> Triple(c.surface, if (enabled) c.textSecondary else c.textTertiary, c.outlineCard)
    }
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .semantics { selected = on; if (done) stateDescription = t("已答", "Answered") }
            .heightIn(min = 32.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (done && !on) Icon(KelpieIcons.Check, null, tint = c.done, modifier = Modifier.size(12.dp))
        Text(label, style = Kelpie.type.caption, color = fg, maxLines = 1)
    }
}

@Composable
private fun OptionCard(selected: Boolean, multi: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Kelpie.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.l))
            .border(1.5.dp, if (selected) c.primary else c.outlineCard, RoundedCornerShape(KelpieRadius.l))
            .background(if (selected) c.surfaceSubtle else c.surface)
            .clickable(role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 1.dp).size(20.dp), contentAlignment = Alignment.Center) {
            if (multi) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (selected) c.primary else c.surface)
                        .border(2.dp, if (selected) c.primary else c.iconMuted, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(KelpieIcons.Check, null, tint = c.onPrimary, modifier = Modifier.size(13.dp)) }
            } else {
                Box(Modifier.size(20.dp).clip(CircleShape).border(if (selected) 6.dp else 2.dp, if (selected) c.primary else c.iconMuted, CircleShape))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
