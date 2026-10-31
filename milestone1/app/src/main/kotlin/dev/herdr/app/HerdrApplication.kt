package dev.herdr.app

import android.app.Application
import androidx.room.Room
import dev.herdr.core.data.BoardDatabase
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.AppPrefs
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.data.HostRepository
import dev.herdr.core.ssh.SshConnector
import java.io.File

import kotlinx.coroutines.launch

class AppContainer(app: Application) {

    /**
     * 兜底：SSH 传输层异常（如 `Broken transport; encountered EOF`）可能在 Default 调度器上
     * 以未捕获异常形式冒出；没有 handler 时会直接 FATAL 整个进程（2026-11-01 实测三次）。
     * 这里只记录标签与异常类型，**不打印**主机、路径或消息内容。
     */
    private val crashGuard = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.w("HerdrScope", "uncaught background exception: ${e.javaClass.simpleName}")
    }

    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default + crashGuard,
    )
    val prefs = AppPrefs(app)
    val hosts = HostRepository(app)
    val database: BoardDatabase = Room.databaseBuilder(app, BoardDatabase::class.java, "herdr.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    val boards = BoardRepository(database.boardDao())
    val transcripts = dev.herdr.core.data.TranscriptStore(database.transcriptDao())

    /** 草稿独立库：herdr.db 会在升级时被销毁重建，草稿不能跟着丢。 */
    private val draftDatabase = dev.herdr.core.data.drafts.DraftDatabase.build(app)
    val drafts = dev.herdr.core.data.drafts.DraftStore(draftDatabase.dao())
    val pendingWrites = dev.herdr.core.data.drafts.RoomPendingWriteSink(draftDatabase.pendingWrites(), appScope)

    /** 每台主机独立信任库（指纹互不影响）。 */
    fun connectorFor(hostId: String): SshConnector =
        SshConnector(File(app.filesDir, "trust/$hostId.known_hosts"))

    /** 进程级连接管理：前台服务与 UI 共用同一份连接。 */
    val manager = HostConnectionManager(
        hosts = hosts,
        boards = boards,
        transcripts = transcripts,
        connectorFor = ::connectorFor,
        scope = appScope,
        pendingWriteSink = pendingWrites,
    ).also { m ->
        // 恢复上次进程里未确认的写操作（Unknown 不会自动重发）
        appScope.launch {
            runCatching { m.restorePendingWrites(pendingWrites.load()) }
        }
    }

    private val app: Application = app
}

class HerdrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.manager.startAll()
        if (container.prefs.monitoringEnabled) {
            container.manager.setMonitoring(true)
            runCatching { MonitorService.start(this) }
        }
    }
}
