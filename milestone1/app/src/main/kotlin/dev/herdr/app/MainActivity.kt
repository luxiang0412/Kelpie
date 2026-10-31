package dev.herdr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.herdr.feature.board.AppRoot
import dev.herdr.feature.board.HostsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HerdrApplication).container
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                HostsViewModel(container.manager, container.hosts, container.drafts) as T
        }
        setContent {
                val vm: HostsViewModel = viewModel(factory = factory)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    // API 33+ 需要显式申请通知权限，否则“出门被提醒”不会显示
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted && container.prefs.monitoringEnabled) {
                            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
                        }
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    dev.herdr.feature.board.LocalMonitorController provides object : dev.herdr.feature.board.MonitorController {
                        override fun start() = MonitorService.start(this@MainActivity)
                        override fun stop() = MonitorService.stop(this@MainActivity)
                    },
                ) {
                    AppRoot(vm)
                }
        }
    }
}
