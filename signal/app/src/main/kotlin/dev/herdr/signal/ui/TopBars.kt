package dev.herdr.signal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.herdr.signal.designsystem.Signal
import dev.herdr.signal.designsystem.SignalIconButton
import dev.herdr.signal.designsystem.SignalIcons
import dev.herdr.signal.designsystem.SignalSize

/** 二级页顶栏：返回 + 标题（+ 副标题）+ 右侧操作。 */
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Signal.colors
    Row(
        Modifier.fillMaxWidth().height(SignalSize.topBarConversation).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SignalIconButton(SignalIcons.Back, "返回", onBack)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(title, style = Signal.type.itemTitle, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Signal.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
