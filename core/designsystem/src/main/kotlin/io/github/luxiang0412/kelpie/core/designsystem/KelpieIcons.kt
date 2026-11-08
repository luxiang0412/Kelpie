package io.github.luxiang0412.kelpie.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 线性图标（24 网格，圆角端点），与设计画布里的内联 SVG 同源。
 * 颜色由调用方用 `Icon(tint = …)` 决定，这里只给黑色占位。
 */
object KelpieIcons {
    val Back = stroke("M15 6l-6 6 6 6", 2f)
    val ChevronRight = stroke("M9 6l6 6-6 6", 2f)
    val ChevronDown = stroke("M6 9l6 6 6-6", 2f)
    val ChevronUp = stroke("M6 15l6-6 6 6", 2f)
    val Settings = stroke("M4 7h10M18 7h2M4 17h4M12 17h8" + circle(16f, 7f, 2f) + circle(10f, 17f, 2f), 1.8f)
    val Spark = stroke("M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M5.6 18.4l2.8-2.8M15.6 8.4l2.8-2.8", 2f)
    val Shield = stroke("M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z", 2f)
    val Info = stroke(circle(12f, 12f, 9f) + "M12 8v4M12 16h.01", 2f)
    /** 问号：agent 在问你一道选择题（AskUserQuestion）。 */
    val Question = stroke(circle(12f, 12f, 9f) + "M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17h.01", 2f)
    val InfoSmall = stroke(circle(12f, 12f, 9f) + "M12 11v5M12 8h.01", 2f)
    val Check = stroke("M5 12l5 5L20 7", 2.4f)
    val Terminal = stroke(rect(3f, 4f, 18f, 16f, 3f) + "M7 9l3 3-3 3M13 15h4", 1.8f)
    val Prompt = stroke("M7 9l3 3-3 3M13 15h4", 2f)
    val Plus = stroke("M12 5v14M5 12h14", 1.8f)
    val Mic = stroke(rect(9f, 3f, 6f, 11f, 3f) + "M5 11a7 7 0 0 0 14 0M12 18v3", 1.8f)
    val Send = stroke("M12 19V5M6 11l6-6 6 6", 2.2f)
    val File = stroke("M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6", 2f)
    val WifiOff = stroke("M2 8.5a15 15 0 0 1 4.5-2.8M10 5.1A15 15 0 0 1 22 8.5M5 12a10 10 0 0 1 4-2.3M14.5 9.8A10 10 0 0 1 19 12M8.5 15.5a5 5 0 0 1 7 0M12 19h.01M3 3l18 18", 2f)
    val Scan = stroke("M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10", 2f)
    val Copy = stroke(rect(9f, 9f, 11f, 11f, 2f) + "M5 15V5a2 2 0 0 1 2-2h10", 2f)
    val Eye = stroke("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" + circle(12f, 12f, 3f), 2f)
    val Lock = stroke(rect(5f, 11f, 14f, 10f, 2f) + "M8 11V7a4 4 0 0 1 8 0v4", 2.2f)
    val Pulse = stroke("M3 12h4l3-8 4 16 3-8h4", 2f)
    val Steps = stroke("M4 6h16M4 12h10M4 18h6", 2f)
    val Search = stroke(circle(11f, 11f, 7f) + "M20 20l-3.5-3.5", 1.9f)
    val Close = stroke("M6 6l12 12M18 6L6 18", 2f)
    val Expand = stroke("M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7", 2f)
    val Key = stroke(circle(8f, 15f, 4f) + "M10.8 12.2L20 3M16 7l3 3M14 9l2 2", 2f)

    /** 实心三点（“更多”）。 */
    val More: ImageVector = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes(circle(5f, 12f, 1.8f) + circle(12f, 12f, 1.8f) + circle(19f, 12f, 1.8f)),
            fill = SolidColor(Color.Black),
        )
    }.build()

    private fun stroke(d: String, width: Float): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r" +
            "v${h - 2 * r}a$r $r 0 0 1 ${-r} $r" +
            "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}" +
            "v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
}
