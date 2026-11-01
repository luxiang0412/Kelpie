package dev.herdr.feature.board.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * UX-00a：色值对比度（WCAG 2.1）。
 * 文本 ≥ 4.5:1；状态色在最差常用底色上 ≥ 3:1（非文本 UI）。
 */
class ContrastTest {

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val r = ratio(fg, bg)
        println("CONTRAST $name = %.2f (min %.1f)".format(r, min))
        assertTrue("$name 对比度 %.2f < %.1f".format(r, min), r >= min)
    }

    @Test
    fun light_theme_textContrast() {
        val p = LightPalette
        check("light textPrimary/canvas", p.textPrimary, p.background, 4.5)
        check("light textPrimary/surface", p.textPrimary, p.surface, 4.5)
        check("light textPrimary/raised", p.textPrimary, p.surfaceRaised, 4.5)
        check("light textSecondary/canvas", p.textSecondary, p.background, 4.5)
        check("light textSecondary/raised", p.textSecondary, p.surfaceRaised, 4.5)
    }

    @Test
    fun dark_theme_textContrast() {
        val p = DarkPalette
        check("dark textPrimary/canvas", p.textPrimary, p.background, 4.5)
        check("dark textPrimary/surface", p.textPrimary, p.surface, 4.5)
        check("dark textPrimary/raised", p.textPrimary, p.surfaceRaised, 4.5)
        check("dark textSecondary/canvas", p.textSecondary, p.background, 4.5)
        check("dark textSecondary/raised", p.textSecondary, p.surfaceRaised, 4.5)
    }

    @Test
    fun statusForegrounds_areLegibleOnTheirBackgrounds() {
        val pairs = listOf(
            "light" to LightPalette,
            "dark" to DarkPalette,
        )
        pairs.forEach { (name, p) ->
            dev.herdr.core.herdr.AgentStatus.entries.forEach { st ->
                check("$name status ${st.name}/statusBg", p.statusFg(st), p.statusBg(st), 3.0)
                check("$name status ${st.name}/canvas", p.statusFg(st), p.background, 3.0)
            }
        }
    }

    @Test
    fun errorAndFocusTokens_areReadable() {
        check("light errorFg/errorBg", LightPalette.errorFg, LightPalette.errorBg, 4.5)
        check("dark errorFg/errorBg", DarkPalette.errorFg, DarkPalette.errorBg, 4.5)
        // focus ring 必须能与 canvas 明显区分（≥3:1）
        check("light focusRing/canvas", LightPalette.focusRing, LightPalette.background, 3.0)
        check("dark focusRing/canvas", DarkPalette.focusRing, DarkPalette.background, 3.0)
    }

    @Test
    fun accentPair_isReadable() {
        check("light accent/onAccent", LightPalette.accent, LightPalette.accentOn, 4.5)
        check("dark accent/onAccent", DarkPalette.accent, DarkPalette.accentOn, 4.5)
    }
}
