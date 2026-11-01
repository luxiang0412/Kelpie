package dev.herdr.feature.board.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.herdr.feature.board.R

/**
 * UX-00a 共享组件契约。
 *
 * 主要点击/指示控件不再用 `›` `▸` `▾` `⤢` 这类**字体字形**：
 * 字形在不同字体与字号下基线与视觉尺寸不一致，且不吃 `LocalContentColor`。
 */

/** 页面左右留白：常规 20dp，<360dp 用 16dp。 */
object PageGutter {
    val normal = 20.dp
    val compact = 16.dp
    fun of(width: Dp): Dp = if (width < 360.dp) compact else normal
}

/** Composer 侧边距：常规 16dp，<360dp 用 12dp。 */
object ComposerGutter {
    val normal = 16.dp
    val compact = 12.dp
    fun of(width: Dp): Dp = if (width < 360.dp) compact else normal
}

/** 图标按钮：视觉 20dp，触达 **≥48×48dp**（UX-D0 §2.2）。只用于已有真实行为的操作。 */
@Composable
fun TouchTargetIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalHerdrPalette.current.textSecondary,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 行内「可进入」指示：装饰性，不参与语义（点击由整行承担）。 */
@Composable
fun ChevronAffordance(
    modifier: Modifier = Modifier,
    tint: Color = LocalHerdrPalette.current.textSecondary,
) {
    Box(modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 折叠行指示符（展开/收起），语义由外层行提供。 */
@Composable
fun DisclosureIndicator(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalHerdrPalette.current.textSecondary,
) {
    Image(
        painter = painterResource(if (expanded) R.drawable.ic_expanded else R.drawable.ic_collapsed),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(16.dp),
    )
}

/**
 * 语义字阶（UX-D0 §2.2）。业务页面只从这里取，不再散落字号。
 * 值为 @Composable getter：与 Material 字阶同源，避免"两个真相"。
 */
object AppTextStyles {
    val displayPage: TextStyle @Composable get() = MaterialTheme.typography.titleLarge
    val titlePage: TextStyle @Composable get() = MaterialTheme.typography.titleSmall
    val titleTask: TextStyle @Composable get() = MaterialTheme.typography.titleSmall
    val titleSection: TextStyle @Composable get() = MaterialTheme.typography.labelLarge
    val bodyReading: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge
    val bodyInput: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge
    val bodySecondary: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium
    val meta: TextStyle @Composable get() = MaterialTheme.typography.labelSmall
    val labelAction: TextStyle @Composable get() = MaterialTheme.typography.labelLarge
    val code: TextStyle get() = MonoStyle
}

/**
 * UX-08b：统一空态/异常态组件（DESIGN-SYSTEM §5）。
 * 结构固定为「图标 + 标题 + 一句真实解释 + 一个真实可用动作」，不放大插画、不放假动作。
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val palette = LocalHerdrPalette.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(16.dp))
                androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.size(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = onSecondary,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) { Text(secondaryLabel) }
            }
        }
    }
}
