package dev.herdr.signal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalTheme
import dev.herdr.signal.nav.SignalNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                c.approvals.setActive(true)
                c.fleet.refreshAll()
            }
            override fun onStop(owner: LifecycleOwner) = c.approvals.setActive(false)
        })
        setContent {
            val mode by c.prefs.theme.collectAsStateWithLifecycle()
            SignalTheme(mode) {
                val colors = Signal.colors
                LaunchedEffect(colors.isDark) {
                    val style = if (colors.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, colors.scrim.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                SignalNavHost()
            }
        }
    }
}
