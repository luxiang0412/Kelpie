package io.github.luxiang0412.kelpie.settings

import io.github.luxiang0412.kelpie.core.herdr.plural
import io.github.luxiang0412.kelpie.core.herdr.t

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.BuildConfig
import io.github.luxiang0412.kelpie.container
import io.github.luxiang0412.kelpie.core.designsystem.Dot
import io.github.luxiang0412.kelpie.core.designsystem.Kelpie
import io.github.luxiang0412.kelpie.core.designsystem.KelpieChip
import io.github.luxiang0412.kelpie.core.designsystem.KelpieIcons
import io.github.luxiang0412.kelpie.core.designsystem.KelpieRadius
import io.github.luxiang0412.kelpie.core.designsystem.KelpieSize
import io.github.luxiang0412.kelpie.core.designsystem.ThemeMode
import io.github.luxiang0412.kelpie.ui.BackTopBar

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    val hosts = c.hosts.hosts
    val links = c.fleet.links
    val home = c.fleet.home
    val theme = c.prefs.theme
    val notifyBlocked = c.prefs.notifyBlocked
    val notifyDone = c.prefs.notifyDone
    fun setTheme(m: ThemeMode) = c.prefs.setTheme(m)
    fun setNotifyBlocked(v: Boolean) { c.prefs.setNotifyBlocked(v); syncService() }
    fun setNotifyDone(v: Boolean) { c.prefs.setNotifyDone(v); syncService() }
    private fun syncService() {
        val app = getApplication<Application>()
        if (c.prefs.notifyBlocked.value || c.prefs.notifyDone.value) io.github.luxiang0412.kelpie.platform.MonitorService.start(app)
        else io.github.luxiang0412.kelpie.platform.MonitorService.stop(app)
    }
    fun retry(id: String) = c.fleet.reconnect(id)
}

@Composable
fun SettingsRoute(onBack: () -> Unit, onEditHost: (String?) -> Unit, vm: SettingsViewModel = viewModel()) {
    val c = Kelpie.colors
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val nb by vm.notifyBlocked.collectAsStateWithLifecycle()
    val nd by vm.notifyDone.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.bgGrouped).statusBarsPadding()) {
        BackTopBar(t("设置", "Settings"), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupLabel(t("主机", "Hosts"))
            Group {
                hosts.forEachIndexed { i, h ->
                    val link = links[h.id]
                    val count = home.hosts.firstOrNull { it.hostId == h.id }?.panes?.count { it.agent != null } ?: 0
                    val dot = when (link?.reach) {
                        HostReach.Online -> c.done
                        HostReach.Unreachable -> c.warning
                        else -> c.idle
                    }
                    if (i > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onEditHost(h.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Dot(dot, 8.dp)
                        Column(Modifier.weight(1f)) {
                            Text(h.label, style = Kelpie.type.rowTitle, color = c.textPrimary)
                            val sub = (link?.text ?: t("未连接", "Not connected")) + if (link?.reach == HostReach.Online) t(" · $count 个 agent", " · " + plural(count, "agent")) else ""
                            Text(sub, style = Kelpie.type.meta, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (link?.reach == HostReach.Unreachable) {
                            KelpieChip(t("立即重试", "Retry now"), onClick = { vm.retry(h.id) }, container = c.surface, border = c.outline, height = KelpieSize.chipSmall)
                        } else {
                            Icon(KelpieIcons.ChevronRight, null, tint = c.iconMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (hosts.isNotEmpty()) Divider()
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { onEditHost(null) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KelpieIcons.Plus, null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                    Text(t("添加主机", "Add host"), style = Kelpie.type.label, color = c.textPrimary)
                }
            }

            GroupLabel(t("通知", "Notifications"))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val notifOk = remember(nb, nd) { ctx.container.notifier.allowed() }
            if ((nb || nd) && !notifOk) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.l)).background(c.offlineContainer)
                        .clickable {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(KelpieIcons.Info, null, tint = c.warning, modifier = Modifier.size(18.dp))
                    Text(t("系统关闭了本应用的通知，开关不会生效。点这里去开启。", "Notifications are turned off for this app in system settings, so these switches have no effect. Tap to turn them on."), style = Kelpie.type.meta, color = c.textPrimary, modifier = Modifier.weight(1f))
                }
            }
            Group {
                SwitchRow(t("有 agent 在等你时通知", "Notify when an agent is waiting"), t("App 在后台运行期间", "While the app runs in the background"), nb, vm::setNotifyBlocked)
                Divider()
                SwitchRow(t("任务完成时通知", "Notify when a task finishes"), t("默认关闭，避免打扰", "Off by default to avoid noise"), nd, vm::setNotifyDone)
            }

            GroupLabel(t("外观", "Appearance"))
            Group {
                Row(Modifier.fillMaxWidth().padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ThemeMode.System to t("跟随系统", "System"), ThemeMode.Light to t("日间", "Light"), ThemeMode.Dark to t("夜间", "Dark")).forEach { (m, label) ->
                        val sel = theme == m
                        Text(
                            label,
                            style = Kelpie.type.label,
                            color = if (sel) c.onPrimary else c.textPrimary,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(KelpieRadius.m))
                                .background(if (sel) c.primary else c.surface)
                                .selectable(sel, role = Role.RadioButton) { vm.setTheme(m) }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            GroupLabel(t("关于", "About"))
            Group {
                InfoRow(t("版本", "Version"), BuildConfig.VERSION_NAME)
                Divider()
                InfoRow("herdr", links.values.firstOrNull { it.version.isNotBlank() }?.version?.let { "$it · protocol 22" } ?: "—")
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(24.dp))
        }
    }
}

@Composable
internal fun GroupLabel(text: String) {
    Text(text, style = Kelpie.type.metaStrong, color = Kelpie.colors.textSecondary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 2.dp))
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(KelpieRadius.xl)).background(Kelpie.colors.surface)) { content() }
}

@Composable
internal fun Divider() {
    Spacer(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Kelpie.colors.hairline))
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Kelpie.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(checked, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Kelpie.type.rowTitle, color = c.textPrimary)
            Text(sub, style = Kelpie.type.meta, color = c.textSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
                uncheckedTrackColor = c.surfaceRaised, uncheckedThumbColor = c.iconMuted, uncheckedBorderColor = c.outline,
            ),
        )
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    val c = Kelpie.colors
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = Kelpie.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
        Text(v, style = Kelpie.type.meta, color = c.textSecondary)
    }
}
