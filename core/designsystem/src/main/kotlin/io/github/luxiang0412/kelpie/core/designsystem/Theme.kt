package io.github.luxiang0412.kelpie.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { System, Light, Dark }

/** 读取当前主题 token 的入口：`Kelpie.colors.bg`、`Kelpie.type.display`。 */
object Kelpie {
    val colors: KelpieColors
        @Composable @ReadOnlyComposable get() = LocalKelpieColors.current
    val type: KelpieType
        @Composable @ReadOnlyComposable get() = LocalKelpieType.current
}

@Composable
fun KelpieTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) DarkKelpieColors else LightKelpieColors
    // M3 组件（Sheet、Switch、Menu）也跟随 token；业务组件只读 LocalKelpieColors（规范 8.1）。
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            background = c.bg, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceSubtle, onSurfaceVariant = c.textSecondary,
            surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised,
            outline = c.outline, outlineVariant = c.hairline,
            error = c.error, scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            background = c.bg, onBackground = c.textPrimary,
            surface = c.surface, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceSubtle, onSurfaceVariant = c.textSecondary,
            surfaceContainerLow = c.surface, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceRaised,
            outline = c.outline, outlineVariant = c.hairline,
            error = c.error, scrim = c.scrim,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalKelpieColors provides c,
            LocalKelpieType provides DefaultKelpieType,
            LocalContentColor provides c.textPrimary,
            content = content,
        )
    }
}
