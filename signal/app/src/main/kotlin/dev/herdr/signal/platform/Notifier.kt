package dev.herdr.signal.platform

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
import dev.herdr.core.data.attention.AgentNames
import dev.herdr.core.data.attention.Titles
import dev.herdr.core.data.runtime.Fleet
import dev.herdr.signal.MainActivity
import dev.herdr.signal.R
import dev.herdr.signal.SignalPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 状态转换 → 系统通知（只在 App 不在前台时发；前台时首页本身就是提醒）。
 * 锁屏上只显示项目名和 agent，不显示问题正文（规范 7：隐私）。
 */
class Notifier(private val context: Context, private val prefs: SignalPrefs) {
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
                    t.toBlocked && prefs.notifyBlocked.value -> post(t, CH_BLOCKED, "在等你", "${AgentNames.display(t.pane.agent)} · ${t.hostLabel} 需要你回复")
                    t.completed && prefs.notifyDone.value -> post(t, CH_DONE, "完成了", "${AgentNames.display(t.pane.agent)} · ${t.hostLabel}")
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
                    .setContentTitle(if (channel == CH_BLOCKED) "有 agent 在等你" else "有任务完成了")
                    .build(),
            )
            .build()
        nm.notify(id, n)
    }

    private fun ensureChannels() {
        nm.createNotificationChannel(NotificationChannel(CH_BLOCKED, "在等你", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "agent 停下来等你回复或批准"
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "agent 完成一轮工作"
        })
    }

    private fun idOf(hostId: String, paneId: String) = ("$hostId/$paneId").hashCode()

    companion object {
        const val CH_BLOCKED = "blocked"
        const val CH_DONE = "done"
        const val EXTRA_HOST = "host"
        const val EXTRA_PANE = "pane"
    }
}
