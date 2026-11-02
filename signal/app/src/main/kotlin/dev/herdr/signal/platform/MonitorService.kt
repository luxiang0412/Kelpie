package dev.herdr.signal.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.herdr.signal.MainActivity
import dev.herdr.signal.R

/**
 * 前台服务：只为让进程在后台不被冻结，好让 [Notifier] 继续收到状态变化。
 * 连接、对账、通知逻辑都在进程级的 AppContainer 里，这里不重复。
 *
 * - 必须在 App 前台时启动（Android 12+ 不允许后台启动前台服务）；
 * - Android 15 的 dataSync 每天有 6 小时配额，到点系统回调 onTimeout，我们直接停掉。
 */
class MonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "后台关注", NotificationManager.IMPORTANCE_MIN).apply {
            description = "让 App 在后台也能发现有 agent 在等你"
            setShowBadge(false)
        })
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在关注你的 agent")
            .setContentText("有 agent 在等你时会通知你")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val CH = "monitor"
        private const val ID = 7001
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, MonitorService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MonitorService::class.java)) }
        }
    }
}
