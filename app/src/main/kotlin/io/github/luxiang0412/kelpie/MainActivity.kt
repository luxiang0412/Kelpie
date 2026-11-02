package io.github.luxiang0412.kelpie

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
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieTheme
import io.github.luxiang0412.kelpie.nav.KelpieNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(resources.configuration.locales[0])
        enableEdgeToEdge()
        val c = container
        handleIntent(intent)
        JankMonitor.attach(this, "home")
        setContent {
            val mode by c.prefs.theme.collectAsStateWithLifecycle()
            KelpieTheme(mode) {
                val colors = Kelpie.colors
                LaunchedEffect(colors.isDark) {
                    val style = if (colors.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, colors.scrim.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                KelpieNavHost(c.pendingOpen, c.pendingPair, c.pairLink)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAskNotificationPermission()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        intent?.data?.takeIf { it.scheme == io.github.luxiang0412.kelpie.core.data.PairingPayload.SCHEME }?.let { uri ->
            container.pendingPair.value = uri.toString()
            intent.data = null // 旋转 / 重建时不要再触发一次
            return
        }
        val host = intent?.getStringExtra(io.github.luxiang0412.kelpie.platform.Notifier.EXTRA_HOST) ?: return
        val pane = intent.getStringExtra(io.github.luxiang0412.kelpie.platform.Notifier.EXTRA_PANE) ?: return
        container.pendingOpen.value = io.github.luxiang0412.kelpie.core.data.attention.AgentKey(host, pane)
        intent.removeExtra(io.github.luxiang0412.kelpie.platform.Notifier.EXTRA_HOST)
    }

    /** Android 13+：已经有主机、通知开关打开、还没授权、也没问过时，问一次（空 App 首次打开不问，没有上下文）。 */
    private fun maybeAskNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val c = container
        if (c.hosts.hosts.value.isEmpty()) return
        if (!c.prefs.notifyBlocked.value && !c.prefs.notifyDone.value) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sp = getSharedPreferences("kelpie-prefs", MODE_PRIVATE)
        if (sp.getBoolean("asked_notif", false)) return
        sp.edit().putBoolean("asked_notif", true).apply()
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
