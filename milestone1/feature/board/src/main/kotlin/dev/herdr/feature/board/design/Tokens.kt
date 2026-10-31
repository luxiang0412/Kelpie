package dev.herdr.feature.board.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.herdr.core.herdr.AgentStatus

/**
 * Quiet Command 设计系统（依据 HERDR-ANDROID-UX-DESIGN-REVIEW §4）。
 *
 * 原则：信息优先、克制；状态色只表意不装饰；深色用低对比层次而非阴影；
 * 所有尺寸/颜色/动效统一从这里取，业务页面不再硬编码。
 */
data class HerdrPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentOn: Color,
    val outline: Color,
    val statusFg: (AgentStatus) -> Color,
    val statusBg: (AgentStatus) -> Color,
)

private val LightPalette = HerdrPalette(
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF1F3F8),
    textPrimary = Color(0xFF172033),
    textSecondary = Color(0xFF526074),
    accent = Color(0xFF3556CE),
    accentOn = Color(0xFFFFFFFF),
    outline = Color(0xFFD6DDE7),
    statusFg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFF98470B)
            AgentStatus.WORKING -> Color(0xFF12558A)
            AgentStatus.DONE -> Color(0xFF17683E)
            AgentStatus.IDLE -> Color(0xFF596374)
            AgentStatus.UNKNOWN -> Color(0xFF74530A)
        }
    },
    statusBg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFFFFF3EA)
            AgentStatus.WORKING -> Color(0xFFEAF4FF)
            AgentStatus.DONE -> Color(0xFFEAF8EF)
            AgentStatus.IDLE -> Color(0xFFF0F2F5)
            AgentStatus.UNKNOWN -> Color(0xFFFFF7E0)
        }
    },
)

private val DarkPalette = HerdrPalette(
    background = Color(0xFF0B111C),
    surface = Color(0xFF141B29),
    surfaceRaised = Color(0xFF1C2636),
    textPrimary = Color(0xFFF1F5FA),
    textSecondary = Color(0xFFB4C0D0),
    accent = Color(0xFF9DB2FF),
    accentOn = Color(0xFF0B111C),
    outline = Color(0xFF344356),
    statusFg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFFFFB386)
            AgentStatus.WORKING -> Color(0xFF8BC5F9)
            AgentStatus.DONE -> Color(0xFF8CD8AE)
            AgentStatus.IDLE -> Color(0xFFC1CCD8)
            AgentStatus.UNKNOWN -> Color(0xFFEBD28D)
        }
    },
    statusBg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFF3B241A)
            AgentStatus.WORKING -> Color(0xFF142C43)
            AgentStatus.DONE -> Color(0xFF153326)
            AgentStatus.IDLE -> Color(0xFF28313D)
            AgentStatus.UNKNOWN -> Color(0xFF3B3119)
        }
    },
)

val LocalHerdrPalette = staticCompositionLocalOf { LightPalette }

/** 4/8/12/16/20/24/32 刻度。 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
    /** 页面左右留白 */
    val page = 16.dp
}

object Radius {
    val card = 18.dp
    val chip = 999.dp
    val sheet = 24.dp
    val code = 12.dp
}

/** 动效：所有动画必须有目的（状态切换/内容新增），时长克制。 */
object Motion {
    const val FAST = 120
    const val NORMAL = 200
    const val SLOW = 300
}

/** 状态文案（唯一来源；禁止在各页面各写一套）。 */
object StatusText {
    fun label(st: AgentStatus): String = when (st) {
        AgentStatus.BLOCKED -> "需要你"
        AgentStatus.WORKING -> "运行中"
        AgentStatus.DONE -> "已完成"
        AgentStatus.IDLE -> "空闲"
        AgentStatus.UNKNOWN -> "状态未知"
    }

    fun section(st: AgentStatus): String = when (st) {
        AgentStatus.BLOCKED -> "需要处理"
        AgentStatus.WORKING -> "运行中"
        AgentStatus.DONE -> "已完成"
        AgentStatus.IDLE -> "空闲"
        AgentStatus.UNKNOWN -> "其他"
    }
}

/** 等宽样式（路径、命令、tool 参数）。 */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
    )
}

private fun shapes(): Shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.sheet),
)

@Composable
fun HerdrThemeCompat(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentOn,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
        )
    }
    CompositionLocalProvider(LocalHerdrPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = shapes(), content = content)
    }
}
