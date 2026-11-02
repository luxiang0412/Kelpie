package dev.herdr.signal.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Signal 设计 token。数值与 docs/signal/DESIGN-SPEC.md 第 1–3 节一一对应；
 * 改这里必须同步改规范，并让 ContrastTest 通过。
 */
@Immutable
data class SignalColors(
    val isDark: Boolean,
    // 1.1 基础色
    val bg: Color,
    val bgGrouped: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val outline: Color,
    val outlineCard: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val iconMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val scrim: Color,
    // 1.2 状态色
    val attention: Color,
    val attentionText: Color,
    val attentionContainer: Color,
    val attentionContainerInner: Color,
    val attentionOnContainerBody: Color,
    val attentionOnContainerMeta: Color,
    val working: Color,
    val workingTrack: Color,
    val workingContainer: Color,
    val workingText: Color,
    val done: Color,
    val doneContainer: Color,
    val idle: Color,
    val warning: Color,
    val error: Color,
    val diffAddBg: Color,
    val diffAddFg: Color,
    val diffDelBg: Color,
    val diffDelFg: Color,
    val codeBlockBg: Color,
    val codeBlockFg: Color,
    val codeBlockMuted: Color,
    val cardHeaderBg: Color,
    val offlineContainer: Color,
    val offlineIcon: Color,
    val logoSide: Color,
)

val LightSignalColors = SignalColors(
    isDark = false,
    bg = Color(0xFFFFFFFF),
    bgGrouped = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF4F5F7),
    surfaceRaised = Color(0xFFF1F2F4),
    hairline = Color(0xFFF0F1F3),
    outline = Color(0xFFE3E5E9),
    outlineCard = Color(0xFFECEDF0),
    textPrimary = Color(0xFF101217),
    textSecondary = Color(0xFF5A606B),
    textTertiary = Color(0xFF6B7280),
    iconMuted = Color(0xFF7D838D),
    primary = Color(0xFF101217),
    onPrimary = Color(0xFFFFFFFF),
    scrim = Color(0x6B101217),
    attention = Color(0xFFC2410C),
    attentionText = Color(0xFF9A3412),
    attentionContainer = Color(0xFFFFF1E8),
    attentionContainerInner = Color(0xFFFFFFFF),
    attentionOnContainerBody = Color(0xFF101217),
    attentionOnContainerMeta = Color(0xFF5A606B),
    working = Color(0xFF1E5BD6),
    workingTrack = Color(0xFFE4ECFB),
    workingContainer = Color(0xFFF4F7FD),
    workingText = Color(0xFF1E4FB8),
    done = Color(0xFF157347),
    doneContainer = Color(0xFFEAF6EF),
    idle = Color(0xFF9AA0A9),
    warning = Color(0xFF8A4A0B),
    error = Color(0xFFB42318),
    diffAddBg = Color(0xFFEAF7EF),
    diffAddFg = Color(0xFF0E5A37),
    diffDelBg = Color(0xFFFDEDEC),
    diffDelFg = Color(0xFFB42318),
    codeBlockBg = Color(0xFF101217),
    codeBlockFg = Color(0xFFF4F5F7),
    codeBlockMuted = Color(0xFF9AA0A9),
    cardHeaderBg = Color(0xFFFAFAFB),
    offlineContainer = Color(0xFFF6F3F1),
    offlineIcon = Color(0xFF8A6A5A),
    logoSide = Color(0xFFC9CDD4),
)

val DarkSignalColors = SignalColors(
    isDark = true,
    bg = Color(0xFF0E1013),
    bgGrouped = Color(0xFF0E1013),
    surface = Color(0xFF171A1F),
    surfaceSubtle = Color(0xFF171A1F),
    surfaceRaised = Color(0xFF20242B),
    hairline = Color(0xFF1C2026),
    outline = Color(0xFF2A2F37),
    outlineCard = Color(0xFF252A31),
    textPrimary = Color(0xFFECEEF1),
    textSecondary = Color(0xFF9AA1AC),
    textTertiary = Color(0xFF868D98),
    iconMuted = Color(0xFF7B828D),
    primary = Color(0xFFECEEF1),
    onPrimary = Color(0xFF0E1013),
    scrim = Color(0x99000000),
    attention = Color(0xFFFF8A3D),
    attentionText = Color(0xFFFFA36B),
    attentionContainer = Color(0xFF2A1A10),
    attentionContainerInner = Color(0xFF3A2618),
    attentionOnContainerBody = Color(0xFFF3E9E1),
    attentionOnContainerMeta = Color(0xFFB9A797),
    working = Color(0xFF7CB7FF),
    workingTrack = Color(0xFF1B2A40),
    workingContainer = Color(0xFF14202F),
    workingText = Color(0xFF9FCBFF),
    done = Color(0xFF6EDBA8),
    doneContainer = Color(0xFF12271D),
    idle = Color(0xFF6B7280),
    warning = Color(0xFFF5B76B),
    error = Color(0xFFFF8A80),
    diffAddBg = Color(0xFF12271D),
    diffAddFg = Color(0xFF8EE6BB),
    diffDelBg = Color(0xFF2A1513),
    diffDelFg = Color(0xFFFF8A80),
    codeBlockBg = Color(0xFF08090B),
    codeBlockFg = Color(0xFFECEEF1),
    codeBlockMuted = Color(0xFF868D98),
    cardHeaderBg = Color(0xFF14171B),
    offlineContainer = Color(0xFF1E1915),
    offlineIcon = Color(0xFFB09484),
    logoSide = Color(0xFF3A3F48),
)

val LocalSignalColors = staticCompositionLocalOf { LightSignalColors }

/** 第 2 节字阶。界面用系统字体，不打包字体。 */
@Immutable
data class SignalType(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val appName: TextStyle,
    val itemTitle: TextStyle,
    val rowTitle: TextStyle,
    val bodyReading: TextStyle,
    val bodyCard: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val meta: TextStyle,
    val metaStrong: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val code: TextStyle,
    val codeSmall: TextStyle,
)

val DefaultSignalType = SignalType(
    display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    title = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    headline = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    appName = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    itemTitle = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    rowTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyReading = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyCard = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    label = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    meta = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    metaStrong = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    caption = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    micro = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    code = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace),
    codeSmall = TextStyle(fontSize = 12.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace),
)

val LocalSignalType = staticCompositionLocalOf { DefaultSignalType }

/** 第 3.1 节间距。 */
object SignalSpace {
    val s1 = 4.dp
    val s2 = 6.dp
    val s3 = 8.dp
    val s4 = 10.dp
    val s5 = 12.dp
    val s6 = 16.dp
    val s7 = 20.dp
    val s8 = 24.dp
    val s9 = 28.dp
    val pageHome = 16.dp
    val pageConversation = 20.dp
    val pagePairing = 24.dp
}

/** 第 3.2 节圆角。 */
object SignalRadius {
    val xs = 5.dp
    val s = 10.dp
    val m = 12.dp
    val l = 14.dp
    val xl = 18.dp
    val card = 20.dp
    val sheet = 28.dp
}

/** 第 3.3 节尺寸。 */
object SignalSize {
    val touch = 48.dp
    val iconButton = 44.dp
    val topBarHome = 56.dp
    val topBarConversation = 60.dp
    val statusIcon = 32.dp
    val chip = 36.dp
    val chipSmall = 34.dp
    val composerMin = 52.dp
    val sendButton = 44.dp
    val sheetButton = 54.dp
    val primaryButton = 56.dp
    val rowMin = 60.dp
}
