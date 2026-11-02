package dev.herdr.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** 规范 1.3：所有组合在日间 / 夜间都必须达到门槛。 */
class ContrastTest {

    private fun lum(c: Color): Double {
        val argb = c.toArgb()
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun pairs(c: SignalColors) = listOf(
        Triple("textPrimary/bg", c.textPrimary to c.bg, 4.5),
        Triple("textSecondary/bg", c.textSecondary to c.bg, 4.5),
        Triple("textTertiary/bg", c.textTertiary to c.bg, 4.5),
        Triple("textSecondary/surfaceSubtle", c.textSecondary to c.surfaceSubtle, 4.5),
        Triple("textSecondary/bgGrouped", c.textSecondary to c.bgGrouped, 4.5),
        Triple("textSecondary/surfaceRaised", c.textSecondary to c.surfaceRaised, 4.5),
        Triple("attentionText/attentionContainer", c.attentionText to c.attentionContainer, 4.5),
        Triple("attentionOnContainerBody/attentionContainer", c.attentionOnContainerBody to c.attentionContainer, 4.5),
        Triple("attentionOnContainerMeta/attentionContainer", c.attentionOnContainerMeta to c.attentionContainer, 4.5),
        Triple("attention/bg", c.attention to c.bg, 3.0),
        Triple("working/bg", c.working to c.bg, 3.0),
        Triple("workingText/workingContainer", c.workingText to c.workingContainer, 4.5),
        Triple("done/bg", c.done to c.bg, 3.0),
        Triple("warning/bg", c.warning to c.bg, 4.5),
        Triple("error/bg", c.error to c.bg, 4.5),
        Triple("onPrimary/primary", c.onPrimary to c.primary, 4.5),
        Triple("iconMuted/surface", c.iconMuted to c.surface, 3.0),
        Triple("diffAddFg/diffAddBg", c.diffAddFg to c.diffAddBg, 4.5),
        Triple("diffDelFg/diffDelBg", c.diffDelFg to c.diffDelBg, 4.5),
        Triple("codeBlockFg/codeBlockBg", c.codeBlockFg to c.codeBlockBg, 4.5),
        Triple("textSecondary/offlineContainer", c.textSecondary to c.offlineContainer, 4.5),
    )

    @Test fun light() = check(LightSignalColors, "light")

    @Test fun dark() = check(DarkSignalColors, "dark")

    private fun check(c: SignalColors, name: String) {
        val failures = pairs(c).mapNotNull { (label, p, min) ->
            val r = ratio(p.first, p.second)
            if (r < min) "$name $label = ${"%.2f".format(r)} < $min" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
