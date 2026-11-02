package dev.herdr.signal.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.ui.BackTopBar

@Composable
fun SettingsRoute(onBack: () -> Unit, onEditHost: (String?) -> Unit) {
    Column(Modifier.fillMaxSize().background(Signal.colors.bg).statusBarsPadding()) {
        BackTopBar("设置", onBack)
    }
}

@Composable
fun HostEditRoute(hostId: String?, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Signal.colors.bg).statusBarsPadding()) {
        BackTopBar(if (hostId == null) "添加主机" else "编辑主机", onDone)
    }
}
