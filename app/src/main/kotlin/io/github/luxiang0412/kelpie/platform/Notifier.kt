package io.github.luxiang0412.kelpie.platform

import io.github.luxiang0412.kelpie.core.herdr.t

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.luxiang0412.kelpie.core.data.attention.AgentNames
import io.github.luxiang0412.kelpie.core.data.attention.Titles
import io.github.luxiang0412.kelpie.core.data.runtime.Fleet
import io.github.luxiang0412.kelpie.MainActivity
import io.github.luxiang0412.kelpie.R
import io.github.luxiang0412.kelpie.KelpiePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 状态转换 → 系统通知（只在 App 不在前台时发；前台时首页本身就是提醒）。
 * 锁屏上只显示项目名和 agent，不显示问题正文（规范 7：隐私）。
 */
class Notifier(private val context: Context, private val prefs: KelpiePrefs) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun start(fleet: Fleet, scope: CoroutineScope) {
        ensureChannels()
        scope.launch {
            fleet.transitions.collect { t ->
                val foreground = withContext(Dispatchers.Main) {
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }
                android.util.Log.i("Notifier", "transition ${t.key} blocked=${t.toBlocked} completed=${t.completed} fg=$foreground allowed=${allowed()}")
                if (foreground || !allowed()) return@collect
                when {
                    t.toBlocked && prefs.notifyBlocked.value -> post(t, CH_BLOCKED, t("在等你", "Waiting for you"), t("${AgentNames.display(t.pane.agent)} · ${t.hostLabel} 需要你回复", "${AgentNames.display(t.pane.agent)} · ${t.hostLabel} needs your reply"))
                    t.completed && prefs.notifyDone.value -> post(t, CH_DONE, t("完成了", "is done"), "${AgentNames.display(t.pane.agent)} · ${t.hostLabel}")
                }
            }
        }
    }

    fun clear(hostId: String, paneId: String) = nm.cancel(idOf(hostId, paneId))

    /** 运行时权限 + 系统 / 厂商开关都要通过（MIUI 会在授权后仍把应用通知设为关闭）。 */
    fun allowed(): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    private fun post(t: Fleet.Transition, channel: String, verb: String, body: String) {
        val title = "${Titles.of(t.pane)} $verb"
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_HOST, t.key.hostId)
            .putExtra(EXTRA_PANE, t.key.paneId)
        val id = idOf(t.key.hostId, t.key.paneId)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFC2410C.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_MESSAGE)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                android.app.Notification.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(if (channel == CH_BLOCKED) t("有 agent 在等你", "An agent is waiting for you") else t("有任务完成了", "A task finished"))
                    .build(),
            )
            .build()
        nm.notify(id, n)
    }

    /**
     * 两类通知各一个类别，声音用系统默认，用户可以在系统设置里给每类单独换声音（设置页有入口）。
     * 振动节奏不同，静音时也能分辨：在等你 = 短振两下，完成 = 振一下。
     * 类别建好后 App 就改不了它的声音和振动了，所以加振动时换了新 id（v2），并删掉旧类别。
     */
    private fun ensureChannels() {
        LEGACY_CHANNELS.forEach { runCatching { nm.deleteNotificationChannel(it) } }
        nm.createNotificationChannel(NotificationChannel(CH_BLOCKED, t("在等你", "Waiting for you"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = t("agent 停下来等你回复或批准", "An agent stopped to wait for your reply or approval")
            enableVibration(true)
            vibrationPattern = VIBRATE_BLOCKED
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, t("完成", "Done"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("agent 完成一轮工作", "An agent finished a round of work")
            enableVibration(true)
            vibrationPattern = VIBRATE_DONE
        })
    }

    private fun idOf(hostId: String, paneId: String) = ("$hostId/$paneId").hashCode()

    companion object {
        const val CH_BLOCKED = "blocked_v2"
        const val CH_DONE = "done_v2"
        private val LEGACY_CHANNELS = listOf("blocked", "done")
        /** 停 0 → 振 180 ms → 停 120 → 振 180（两下短振）。 */
        private val VIBRATE_BLOCKED = longArrayOf(0, 180, 120, 180)
        /** 一下 300 ms。 */
        private val VIBRATE_DONE = longArrayOf(0, 300)

        /** 打开系统里某一类通知的设置页（声音、振动）；个别 ROM 不支持时退回本应用的通知设置。 */
        fun openChannelSettings(context: Context, channel: String) {
            val pkg = context.packageName
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg)
                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel),
                )
            }.recoverCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg),
                )
            }
        }
        const val EXTRA_HOST = "host"
        const val EXTRA_PANE = "pane"
    }
}
