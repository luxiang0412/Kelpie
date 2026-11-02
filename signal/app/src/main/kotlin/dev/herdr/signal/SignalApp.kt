package dev.herdr.signal

import android.app.Application
import android.content.Context
import dev.herdr.core.data.BoardDatabase
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.HostRepository
import dev.herdr.core.data.TranscriptStore
import dev.herdr.core.data.drafts.DraftDatabase
import dev.herdr.core.data.drafts.DraftStore
import dev.herdr.core.data.drafts.RoomPendingWriteSink
import dev.herdr.core.data.runtime.ApprovalCenter
import dev.herdr.core.data.runtime.Fleet
import dev.herdr.core.data.runtime.Previews
import dev.herdr.core.data.runtime.Sender
import dev.herdr.core.ssh.SshConnector
import dev.herdr.signal.designsystem.ThemeMode
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
    val prefs = SignalPrefs(app)
    val hosts = HostRepository(app)
    private val boardDb = BoardDatabase.build(app)
    private val draftDb = DraftDatabase.build(app)
    val boards = BoardRepository(boardDb.boardDao())
    val transcripts = TranscriptStore(boardDb.transcriptDao())
    val drafts = DraftStore(draftDb.dao())
    private val pendingSink = RoomPendingWriteSink(draftDb.pendingWrites(), scope)
    private val trustDir = File(app.filesDir, "trust")

    fun connector(hostId: String) = SshConnector(File(trustDir, "$hostId.known_hosts"))

    val fleet = Fleet(hosts, boards, ::connector, scope)
    val sender = Sender(fleet, scope, pendingSink)
    val previews = Previews(fleet, scope)
    val approvals = ApprovalCenter(fleet, scope)
    val notifier = dev.herdr.signal.platform.Notifier(app, prefs)
    /** 点通知要打开的对话（MainActivity 写，导航层读一次后清空）。 */
    val pendingOpen = MutableStateFlow<dev.herdr.core.data.attention.AgentKey?>(null)

    init {
        scope.launch { runCatching { sender.restore(pendingSink.load()) } }
        // 阻塞预览只跟着首页的“等你处理”走
        scope.launch { fleet.home.collect { previews.sync(it.needsYou) } }
        notifier.start(fleet, scope)
    }
}

class SignalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dev.herdr.core.herdr.L10n.update(newConfig.locales[0])
    }

    override fun onCreate() {
        super.onCreate()
        dev.herdr.core.herdr.L10n.update(resources.configuration.locales[0])
        container = AppContainer(this)
        DebugHooks.onAppStart(this, container)
        container.fleet.start()
        val c = container
        // 前台：开审批轮询并立刻对账一次；后台：停审批轮询（连接与状态对账继续，供通知使用）
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                c.approvals.setActive(true); c.fleet.refreshAll()
                // 前台时启动（后台不允许启动前台服务）；两个通知开关都关时不需要
                if (c.prefs.notifyBlocked.value || c.prefs.notifyDone.value) dev.herdr.signal.platform.MonitorService.start(this@SignalApp)
                else dev.herdr.signal.platform.MonitorService.stop(this@SignalApp)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = c.approvals.setActive(false)
        })
    }
}

val Context.container: AppContainer get() = (applicationContext as SignalApp).container

/** 用户偏好（主题、通知）。只存非敏感项。 */
class SignalPrefs(context: Context) {
    private val sp = context.getSharedPreferences("signal-prefs", Context.MODE_PRIVATE)

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
