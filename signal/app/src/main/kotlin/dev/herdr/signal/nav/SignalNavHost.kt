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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
    @Serializable data object Settings
    @Serializable data class HostEdit(val hostId: String? = null)
    /** 配对确认页。链接里有私钥，所以不放进路由参数（会进 saved state），只放在内存里的 [AppContainer.pairLink]。 */
    @Serializable data object Pair
}

@Composable
fun SignalNavHost(
    pendingOpen: kotlinx.coroutines.flow.MutableStateFlow<dev.herdr.core.data.attention.AgentKey?>,
    pendingPair: kotlinx.coroutines.flow.MutableStateFlow<String?>,
    pairLink: kotlinx.coroutines.flow.MutableStateFlow<String?>,
) {
    val nav = rememberNavController()
    val pair by pendingPair.collectAsState()
    LaunchedEffect(pair) {
        val l = pair ?: return@LaunchedEffect
        pendingPair.value = null
        pairLink.value = l
        nav.navigate(Routes.Pair)
    }
    val open by pendingOpen.collectAsState()
    LaunchedEffect(open) {
        val k = open ?: return@LaunchedEffect
        pendingOpen.value = null
        nav.navigate(Routes.Conversation(k.hostId, k.paneId)) { popUpTo(Routes.Home) }
    }
    // 掉帧统计按界面分桶
    val entry by nav.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val r = entry?.destination?.route.orEmpty()
        dev.herdr.signal.JankMonitor.screen(
            when {
                "Conversation" in r -> "conversation"
                "HostEdit" in r || "Pair" in r -> "host-edit"
                "Settings" in r -> "settings"
                else -> "home"
            },
        )
    }
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
                    onSettings = { nav.navigate(Routes.Settings) },
                    onAddHost = { nav.navigate(Routes.HostEdit()) },
                )
            }
            composable<Routes.Conversation> { entry ->
                val r = entry.toRoute<Routes.Conversation>()
                ConversationRoute(r.hostId, r.paneId, r.prefill, onBack = { nav.popBackStack() })
            }
            composable<Routes.Settings> {
                SettingsRoute(
                    onBack = { nav.popBackStack() },
                    onEditHost = { id -> nav.navigate(Routes.HostEdit(id)) },
                )
            }
            composable<Routes.HostEdit> { entry ->
                val r = entry.toRoute<Routes.HostEdit>()
                dev.herdr.signal.settings.HostEditRoute(r.hostId, onDone = { nav.popBackStack() }, onPair = { pairLink.value = it; nav.navigate(Routes.Pair) })
            }
            composable<Routes.Pair> {
                // 进入时取一次；之后清空也不影响这一页（否则清空的瞬间会再触发一次返回，把首页也弹掉）
                val link = androidx.compose.runtime.remember { pairLink.value }
                val done = { pairLink.value = null }
                // 进程被杀后恢复到这一页时链接已经没了：直接回去
                if (link == null) LaunchedEffect(Unit) { nav.popBackStack() }
                link?.let { l ->
                    dev.herdr.signal.settings.PairConfirmRoute(
                        l,
                        onCancel = { done(); nav.popBackStack() },
                        onPaired = { done(); nav.navigate(Routes.Home) { popUpTo(Routes.Home) { inclusive = true } } },
                    )
                }
            }
        }
    }
}
