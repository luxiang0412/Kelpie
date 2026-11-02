package dev.herdr.signal.designsystem

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 系统“移除动画”是否开启（规范第 6 节降级）。 */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** 三点 logo：中间点表示“有 agent 在等你”。[attention] 为 false 时三点全灰（规范 4）。 */
@Composable
fun SignalLogo(size: Dp = 22.dp, attention: Boolean = true, modifier: Modifier = Modifier) {
    val c = Signal.colors
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        drawCircle(c.logoSide, radius = 2.6f * u, center = Offset(6f * u, 15f * u))
        drawCircle(if (attention) c.attention else c.logoSide, radius = 3.4f * u, center = Offset(12f * u, 9f * u))
        drawCircle(c.logoSide, radius = 2.6f * u, center = Offset(18f * u, 15f * u))
    }
}

/** 32dp 进度环：工作中匀速旋转（不是真实进度）。[spinning]=false 时静止为 3/4 圆弧。 */
@Composable
fun WorkingRing(spinning: Boolean, modifier: Modifier = Modifier) {
    val c = Signal.colors
    val reduce = rememberReduceMotion()
    val angle = if (spinning && !reduce) {
        val t = rememberInfiniteTransition(label = "ring")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    Canvas(modifier.size(SignalSize.statusIcon)) {
        val w = 3.dp.toPx()
        val inset = w / 2 + 1.5.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawCircle(c.workingTrack, radius = arcSize.width / 2, style = Stroke(w))
        drawArc(
            c.working, startAngle = -90f + angle, sweepAngle = if (spinning) 90f else 270f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** 离线时“最后看到”的虚线圆。 */
@Composable
fun UnknownRing(modifier: Modifier = Modifier) {
    val c = Signal.colors
    Canvas(modifier.size(SignalSize.statusIcon)) {
        val w = 3.dp.toPx()
        drawCircle(
            c.outline, radius = size.width / 2 - w, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
    }
}

/** 完成：32dp 圆底 + 对勾。 */
@Composable
fun DoneBadge(modifier: Modifier = Modifier) {
    val c = Signal.colors
    Box(modifier.size(SignalSize.statusIcon).clip(CircleShape).background(c.doneContainer), contentAlignment = Alignment.Center) {
        Icon(SignalIcons.Check, contentDescription = null, tint = c.done, modifier = Modifier.size(16.dp))
    }
}

/** 小圆点。 */
@Composable
fun Dot(color: Color, size: Dp = 7.dp, hollow: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).then(
            if (hollow) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color),
        ),
    )
}

/** 44dp 图标按钮（48dp 触控）。 */
@Composable
fun SignalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Signal.colors.textPrimary,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(SignalSize.touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 胶囊芯片 / 次级按钮。 */
@Composable
fun SignalChip(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color = Signal.colors.textPrimary,
    border: Color? = null,
    height: Dp = SignalSize.chip,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(container)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = Signal.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, null, tint = content, modifier = Modifier.size(14.dp))
    }
}

/** 大按钮（弹层 54dp / 配对页 56dp）。 */
@Composable
fun SignalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    height: Dp = SignalSize.sheetButton,
    leading: ImageVector? = null,
) {
    val c = Signal.colors
    val bg = when {
        !enabled -> c.outline
        primary -> c.primary
        else -> c.surfaceSubtle
    }
    val fg = when {
        !enabled -> c.textTertiary
        primary -> c.onPrimary
        else -> c.textPrimary
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(SignalRadius.xl))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, style = Signal.type.itemTitle, color = fg)
    }
}

/**
 * 按住确认（规范 5.13）：按住 600ms 填满后触发，松手回退。
 * 读屏用户无法按住：语义上的 onClick 交给 [onAccessibilityConfirm]（由调用方弹确认框）。
 */
@Composable
fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    onAccessibilityConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdMillis: Int = 600,
) {
    val c = Signal.colors
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var fired by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(SignalSize.sheetButton)
            .clip(RoundedCornerShape(SignalRadius.xl))
            .background(if (enabled) c.primary else c.outline)
            .semantics {
                role = Role.Button
                onClick(label = text) { onAccessibilityConfirm(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    fired = false
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        fired = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    }
                    waitForUpOrCancellation()
                    if (!fired) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(150)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(c.attention),
        )
        Text(text, style = Signal.type.itemTitle, color = if (enabled) c.onPrimary else c.textTertiary)
    }
}

/** 分组标题（“工作中 · 2”）。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Signal.colors.textSecondary) {
    Text(text, style = Signal.type.metaStrong, color = color, modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
}

/** 带描边的圆角容器。 */
fun Modifier.signalCard(color: Color, radius: Dp, border: Color? = null): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color)
        .then(if (border != null) Modifier.border(BorderStroke(1.dp, border), RoundedCornerShape(radius)) else Modifier)

/** 一行：左图标 + 主体 + 右侧内容。 */
@Composable
fun SignalRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
