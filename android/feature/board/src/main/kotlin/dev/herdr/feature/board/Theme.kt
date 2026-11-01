package dev.herdr.feature.board

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dev.herdr.feature.board.design.HerdrThemeCompat

/** 兼容入口：具体 tokens 在 design/Tokens.kt（Quiet Command）。 */
@Composable
fun HerdrTheme(content: @Composable () -> Unit) {
    HerdrThemeCompat(isSystemInDarkTheme(), content)
}
