package dev.herdr.signal.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.herdr.signal.conversation.ConversationRoute
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.home.HomeRoute
import dev.herdr.signal.settings.SettingsRoute
import kotlinx.serialization.Serializable

/** 类型安全路由（规范 8.3）：没有底部导航，首页是唯一的根。 */
object Routes {
    @Serializable data object Home
    @Serializable data class Conversation(val hostId: String, val paneId: String, val prefill: String? = null)
    @Serializable data class Approval(val hostId: String, val requestId: String)
    @Serializable data object Settings
    @Serializable data class HostEdit(val hostId: String? = null)
}

@Composable
fun SignalNavHost() {
    val nav = rememberNavController()
    val reduce = dev.herdr.signal.designsystem.rememberReduceMotion()
    val d = if (reduce) 0 else 260
    Box(Modifier.fillMaxSize().background(Signal.colors.bg)) {
        NavHost(
            navController = nav,
            startDestination = Routes.Home,
            enterTransition = { slideInHorizontally(tween(d)) { it / 4 } + fadeIn(tween(d)) },
            exitTransition = { fadeOut(tween(d / 2)) },
            popEnterTransition = { fadeIn(tween(d)) },
            popExitTransition = { slideOutHorizontally(tween(d)) { it / 4 } + fadeOut(tween(d)) },
        ) {
            composable<Routes.Home> {
                HomeRoute(
                    onOpen = { key, prefill -> nav.navigate(Routes.Conversation(key.hostId, key.paneId, prefill)) },
                    onOpenApproval = { a -> nav.navigate(Routes.Approval(a.hostId, a.request.requestId)) },
                    onSettings = { nav.navigate(Routes.Settings) },
                    onAddHost = { nav.navigate(Routes.HostEdit()) },
                )
            }
            composable<Routes.Conversation> { entry ->
                val r = entry.toRoute<Routes.Conversation>()
                ConversationRoute(r.hostId, r.paneId, r.prefill, onBack = { nav.popBackStack() })
            }
            composable<Routes.Approval> { entry ->
                val r = entry.toRoute<Routes.Approval>()
                dev.herdr.signal.conversation.ApprovalRoute(r.hostId, r.requestId, onDone = { nav.popBackStack() })
            }
            composable<Routes.Settings> {
                SettingsRoute(
                    onBack = { nav.popBackStack() },
                    onEditHost = { id -> nav.navigate(Routes.HostEdit(id)) },
                )
            }
            composable<Routes.HostEdit> { entry ->
                val r = entry.toRoute<Routes.HostEdit>()
                dev.herdr.signal.settings.HostEditRoute(r.hostId, onDone = { nav.popBackStack() })
            }
        }
    }
}
