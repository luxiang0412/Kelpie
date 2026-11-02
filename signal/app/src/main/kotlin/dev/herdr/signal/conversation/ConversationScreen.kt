package dev.herdr.signal.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.ui.BackTopBar

@Composable
fun ConversationRoute(hostId: String, paneId: String, prefill: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Signal.colors.bg).statusBarsPadding()) {
        BackTopBar(paneId, onBack, subtitle = hostId)
        Text("对话页（P3）" + (prefill?.let { " · 预填：$it" } ?: ""), style = Signal.type.body, color = Signal.colors.textSecondary, modifier = Modifier.padding(20.dp))
    }
}

@Composable
fun ApprovalRoute(hostId: String, requestId: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Signal.colors.bg).statusBarsPadding()) {
        BackTopBar("权限请求", onDone, subtitle = "$hostId · $requestId")
    }
}
