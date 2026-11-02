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

    init {
        scope.launch { runCatching { sender.restore(pendingSink.load()) } }
        // 阻塞预览只跟着首页的“等你处理”走
        scope.launch { fleet.home.collect { previews.sync(it.needsYou) } }
    }
}

class SignalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DebugHooks.onAppStart(this, container)
        container.fleet.start()
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
