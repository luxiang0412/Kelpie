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
        handleIntent(intent)
        maybeAskNotificationPermission()
        setContent {
            val mode by c.prefs.theme.collectAsStateWithLifecycle()
            SignalTheme(mode) {
                val colors = Signal.colors
                LaunchedEffect(colors.isDark) {
                    val style = if (colors.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, colors.scrim.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                SignalNavHost(c.pendingOpen)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val host = intent?.getStringExtra(dev.herdr.signal.platform.Notifier.EXTRA_HOST) ?: return
        val pane = intent.getStringExtra(dev.herdr.signal.platform.Notifier.EXTRA_PANE) ?: return
        container.pendingOpen.value = dev.herdr.core.data.attention.AgentKey(host, pane)
        intent.removeExtra(dev.herdr.signal.platform.Notifier.EXTRA_HOST)
    }

    /** Android 13+：通知开关打开、还没授权、也没问过时，问一次。 */
    private fun maybeAskNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val c = container
        if (!c.prefs.notifyBlocked.value && !c.prefs.notifyDone.value) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sp = getSharedPreferences("signal-prefs", MODE_PRIVATE)
        if (sp.getBoolean("asked_notif", false)) return
        sp.edit().putBoolean("asked_notif", true).apply()
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
