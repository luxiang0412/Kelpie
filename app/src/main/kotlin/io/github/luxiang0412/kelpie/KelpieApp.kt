package io.github.luxiang0412.kelpie

import android.app.Application
import android.content.Context
import io.github.luxiang0412.kelpie.core.data.BoardDatabase
import io.github.luxiang0412.kelpie.core.data.BoardRepository
import io.github.luxiang0412.kelpie.core.data.HostRepository
import io.github.luxiang0412.kelpie.core.data.TranscriptStore
import io.github.luxiang0412.kelpie.core.data.drafts.DraftDatabase
import io.github.luxiang0412.kelpie.core.data.drafts.DraftStore
import io.github.luxiang0412.kelpie.core.data.drafts.RoomPendingWriteSink
import io.github.luxiang0412.kelpie.core.data.runtime.ApprovalCenter
import io.github.luxiang0412.kelpie.core.data.runtime.Fleet
import io.github.luxiang0412.kelpie.core.data.runtime.Previews
import io.github.luxiang0412.kelpie.core.data.runtime.Sender
import io.github.luxiang0412.kelpie.core.ssh.SshConnector
import io.github.luxiang0412.kelpie.core.designsystem.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 手写的依赖容器：进程级单例都在这里，ViewModel 通过 [Context.container] 取。 */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = KelpiePrefs(app)
    val hosts = HostRepository(app)
    private val boardDb = BoardDatabase.build(app)
    private val draftDb = DraftDatabase.build(app)
    val boards = BoardRepository(boardDb.boardDao())
    val transcripts = TranscriptStore(boardDb.transcriptDao())
    val drafts = DraftStore(draftDb.dao())
    private val pendingSink = RoomPendingWriteSink(draftDb.pendingWrites(), scope)
    private val trustDir = File(app.filesDir, "trust")

    fun trustFile(hostId: String) = File(trustDir, "$hostId.known_hosts")
    fun connector(hostId: String) = SshConnector(trustFile(hostId))

    /** 深链 / 扫码拿到、等待确认的配对链接。 */
    val pendingPair = MutableStateFlow<String?>(null)
    /** 正在确认的配对链接（只在内存里）。 */
    val pairLink = MutableStateFlow<String?>(null)

    val fleet = Fleet(hosts, boards, ::connector, scope)
    val sender = Sender(fleet, scope, pendingSink)
    val previews = Previews(fleet, scope)
    val approvals = ApprovalCenter(fleet, scope)
    val notifier = io.github.luxiang0412.kelpie.platform.Notifier(app, prefs)
    /** 点通知要打开的对话（MainActivity 写，导航层读一次后清空）。 */
    val pendingOpen = MutableStateFlow<io.github.luxiang0412.kelpie.core.data.attention.AgentKey?>(null)

    init {
        scope.launch { runCatching { sender.restore(pendingSink.load()) } }
        // 阻塞预览只跟着首页的“等你处理”走
        scope.launch { fleet.home.collect { previews.sync(it.needsYou) } }
        notifier.start(fleet, scope)
    }
}

class KelpieApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(newConfig.locales[0])
    }

    override fun onCreate() {
        super.onCreate()
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(resources.configuration.locales[0])
        container = AppContainer(this)
        DebugHooks.onAppStart(this, container)
        container.fleet.start()
        val c = container
        // 前台：开审批轮询并立刻对账一次；后台：停审批轮询（连接与状态对账继续，供通知使用）
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                c.approvals.setActive(true); c.fleet.refreshAll()
                // 前台时启动（后台不允许启动前台服务）；两个通知开关都关时不需要
                if (c.prefs.notifyBlocked.value || c.prefs.notifyDone.value) io.github.luxiang0412.kelpie.platform.MonitorService.start(this@KelpieApp)
                else io.github.luxiang0412.kelpie.platform.MonitorService.stop(this@KelpieApp)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = c.approvals.setActive(false)
        })
    }
}

val Context.container: AppContainer get() = (applicationContext as KelpieApp).container

/** 用户偏好（主题、通知）。只存非敏感项。 */
class KelpiePrefs(context: Context) {
    private val sp = context.getSharedPreferences("kelpie-prefs", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(sp.getString("theme", ThemeMode.System.name)!!) }.getOrDefault(ThemeMode.System),
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    fun setTheme(m: ThemeMode) {
        sp.edit().putString("theme", m.name).apply()
        _theme.value = m
    }

    private val _notifyBlocked = MutableStateFlow(sp.getBoolean("notify_blocked", true))
    val notifyBlocked: StateFlow<Boolean> = _notifyBlocked.asStateFlow()
    fun setNotifyBlocked(v: Boolean) { sp.edit().putBoolean("notify_blocked", v).apply(); _notifyBlocked.value = v }

    private val _notifyDone = MutableStateFlow(sp.getBoolean("notify_done", false))
    val notifyDone: StateFlow<Boolean> = _notifyDone.asStateFlow()
    fun setNotifyDone(v: Boolean) { sp.edit().putBoolean("notify_done", v).apply(); _notifyDone.value = v }
}
