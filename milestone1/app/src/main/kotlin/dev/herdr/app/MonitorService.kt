package dev.herdr.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.herdr.core.herdr.AgentStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务（M4-2）：App 切到后台后继续保持 SSH 连接，并在 agent 进入
 * `blocked`（需要你处理）或 `done`（完成了）时发本地通知。
 *
 * 约束（D29 / 调研 v2 后台章节）：
 * - 不写服务端，只能靠前台服务 + 本地通知；
 * - Android 15 的 dataSync 每 24h 有 6h 配额，系统会回调 onTimeout；
 * - 通知需要 POST_NOTIFICATIONS（API 33+），由 UI 在开启时申请。
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_ONGOING = "herdr-monitor"
        const val CHANNEL_ALERT = "herdr-alerts"
        const val NOTIF_ONGOING = 1001
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e ->
                android.util.Log.w("HerdrScope", "monitor uncaught: ${e.javaClass.simpleName}")
            },
    )
    private var collectJob: Job? = null
    private val lastNotified = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        android.util.Log.i("Monitor", "service start action=${intent?.action}")
        startForeground(NOTIF_ONGOING, ongoingNotification("正在监控 agent 状态"))
        val container = (application as HerdrApplication).container
        container.manager.startAll()
        container.manager.setMonitoring(true)
        observeStatus(container)
        return START_STICKY
    }

    private fun observeStatus(container: AppContainer) {
        if (collectJob != null) return
        collectJob = scope.launch {
            container.manager.statusChanges.collect { change ->
                android.util.Log.i("Monitor", "status ${change.hostId}/${change.pane.paneId} ${change.from} -> ${change.to}")
                val interesting = change.to == AgentStatus.BLOCKED || change.to == AgentStatus.DONE
                if (!interesting) return@collect
                val key = "${change.hostId}/${change.pane.paneId}/${change.to}"
                val now = System.currentTimeMillis()
                val last = lastNotified[key] ?: 0L
                if (now - last < 20_000) return@collect // 防抖：同一 pane 同一状态 20s 内只提醒一次
                lastNotified[key] = now
                notifyAlert(change)
            }
        }
    }

    private fun notifyAlert(change: dev.herdr.core.data.HostConnectionManager.StatusChange) {
        val title = when (change.to) {
            AgentStatus.BLOCKED -> "等你处理 · ${change.hostLabel}"
            AgentStatus.DONE -> "已完成 · ${change.hostLabel}"
            else -> return
        }
        val body = listOfNotNull(
            change.pane.agent ?: "shell",
            change.pane.title ?: change.pane.paneId,
        ).joinToString(" · ")
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        val enabled = nm.areNotificationsEnabled()
        android.util.Log.i("Monitor", "notifyAlert title=$title enabled=$enabled")
        nm.notify((change.hostId + change.pane.paneId).hashCode(), n)
    }

    private fun ongoingNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Herdr 监控中")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "常驻监控", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Agent 提醒（需要你处理 / 已完成）", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun onTimeout(startId: Int) {
        // Android 15+：dataSync 配额用尽
        stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            (application as HerdrApplication).container.manager.setMonitoring(false)
        }
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
