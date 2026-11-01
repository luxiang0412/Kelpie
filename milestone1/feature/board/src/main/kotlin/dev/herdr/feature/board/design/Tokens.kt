package dev.herdr.feature.board.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.herdr.core.herdr.AgentStatus
import dev.herdr.feature.board.R

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
    /** 轻分隔线（不可当作可点击边界）。 */
    val borderSubtle: Color,
    /** 明确失败/认证错误/Rejected（DESIGN-SYSTEM §2.1.2 error）。 */
    val errorFg: Color,
    val errorBg: Color,
    /** 输入焦点环（stroke.focus 2dp，需 ≥3:1）。 */
    val focusRing: Color,
    val statusFg: (AgentStatus) -> Color,
    val statusBg: (AgentStatus) -> Color,
)

internal val LightPalette = HerdrPalette(
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEFF2F6),
    textPrimary = Color(0xFF172033),
    textSecondary = Color(0xFF4F5D70),
    accent = Color(0xFF3556CE),
    accentOn = Color(0xFFFFFFFF),
    outline = Color(0xFFDFE5ED),
    borderSubtle = Color(0xFFDFE5ED),
    errorFg = Color(0xFFB42318),
    errorBg = Color(0xFFFFF0EF),
    focusRing = Color(0xFF3556CE),
    statusFg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFF8B3E0A)
            AgentStatus.WORKING -> Color(0xFF12558A)
            AgentStatus.DONE -> Color(0xFF17683E)
            AgentStatus.IDLE -> Color(0xFF596374)
            AgentStatus.UNKNOWN -> Color(0xFF74530A)
        }
    },
    statusBg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFFFFF2E6)
            AgentStatus.WORKING -> Color(0xFFEAF4FF)
            AgentStatus.DONE -> Color(0xFFEAF8EF)
            AgentStatus.IDLE -> Color(0xFFF0F2F5)
            AgentStatus.UNKNOWN -> Color(0xFFFFF7E0)
        }
    },
)

internal val DarkPalette = HerdrPalette(
    background = Color(0xFF10151D),
    surface = Color(0xFF181F2A),
    surfaceRaised = Color(0xFF222B39),
    textPrimary = Color(0xFFEEF2F8),
    textSecondary = Color(0xFFAEB9C8),
    accent = Color(0xFF9BB0FF),
    accentOn = Color(0xFF10151D),
    outline = Color(0xFF344052),
    borderSubtle = Color(0xFF344052),
    errorFg = Color(0xFFFFB4AB),
    errorBg = Color(0xFF402524),
    focusRing = Color(0xFF9BB0FF),
    statusFg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFFFFC28A)
            AgentStatus.WORKING -> Color(0xFF9FCEFF)
            AgentStatus.DONE -> Color(0xFF8ED6B0)
            AgentStatus.IDLE -> Color(0xFFBEC9D8)
            AgentStatus.UNKNOWN -> Color(0xFFE9CF87)
        }
    },
    statusBg = { st ->
        when (st) {
            AgentStatus.BLOCKED -> Color(0xFF3A2B23)
            AgentStatus.WORKING -> Color(0xFF1E344A)
            AgentStatus.DONE -> Color(0xFF1C352A)
            AgentStatus.IDLE -> Color(0xFF2A323E)
            AgentStatus.UNKNOWN -> Color(0xFF38321D)
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
    val card = 16.dp
    val chip = 999.dp
    val sheet = 24.dp
    val code = 12.dp
}

/** 动效：所有动画必须有目的（状态切换/内容新增），时长克制。 */
/** 系统「移除动画」时返回 false；调用方据此直接 snap（DESIGN-SYSTEM §6）。 */
@Composable
fun animationsEnabled(): Boolean {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    return remember(resolver) {
        android.provider.Settings.Global.getFloat(
            resolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

object Motion {
    const val FAST = 120
    const val NORMAL = 200
    const val SLOW = 300
}

/** 状态文案（唯一来源；禁止在各页面各写一套）。 */
object StatusText {
    /** 状态词（唯一来源，走 string 资源，支持 zh/en）。 */
    @Composable
    fun label(st: AgentStatus): String = stringResource(
        when (st) {
            AgentStatus.BLOCKED -> R.string.board_status_blocked
            AgentStatus.WORKING -> R.string.board_status_working
            AgentStatus.DONE -> R.string.board_status_done
            AgentStatus.IDLE -> R.string.board_status_idle
            AgentStatus.UNKNOWN -> R.string.board_status_unknown
        },
    )

    /** 分区标题（与状态词区分：blocked 用「需要处理」而不是「需要你」）。 */
    @Composable
    fun section(st: AgentStatus): String = stringResource(
        when (st) {
            AgentStatus.BLOCKED -> R.string.board_section_blocked
            AgentStatus.WORKING -> R.string.board_section_working
            AgentStatus.DONE -> R.string.board_section_done
            AgentStatus.IDLE -> R.string.board_section_idle
            AgentStatus.UNKNOWN -> R.string.board_section_unknown
        },
    )
}

/** 分区标题（DESIGN-SYSTEM type.titleSection 14/20 SemiBold）。 */
val SectionTitleStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.SemiBold,
)

/** Markdown 标题（type.mdH1/H2/H3 = 21/29、18/26、16/24 SemiBold）。 */
val MdH1Style = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
val MdH2Style = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
val MdH3Style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

/** 等宽样式（路径、命令、tool 参数）。 */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelSmall = base.labelSmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
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
