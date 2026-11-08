package io.github.luxiang0412.kelpie.core.data.runtime

import android.util.Log
import io.github.luxiang0412.kelpie.core.data.BoardRepository
import io.github.luxiang0412.kelpie.core.data.HostRepository
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.attention.AttentionTracker
import io.github.luxiang0412.kelpie.core.data.attention.HomeDerivation
import io.github.luxiang0412.kelpie.core.data.attention.HomeState
import io.github.luxiang0412.kelpie.core.data.attention.HostInput
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.core.herdr.ConnectionSupervisor
import io.github.luxiang0412.kelpie.core.herdr.Pane
import io.github.luxiang0412.kelpie.core.ssh.SshConnection
import io.github.luxiang0412.kelpie.core.ssh.SshConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 进程级的“机群”：所有主机的连接 + 首页状态。替代旧版 545 行的 HostConnectionManager。
 *
 * 只做三件事：
 *  1. 按 [HostRepository.hosts] 维护每台主机的 [HostRuntime]；
 *  2. 把每台主机的权威快照喂给 [AttentionTracker]，派生出 [HomeState]；
 *  3. 快照落库（冷启动先显示“最后看到”），并发出状态转换供通知层使用。
 *
 * 写操作在 [Sender]，阻塞预览在 [Previews]，结构化审批在 [ApprovalCenter]——都只借用这里的连接。
 */
class Fleet(
    private val hosts: HostRepository,
    private val boards: BoardRepository,
    private val connectorFor: (hostId: String) -> SshConnector,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Transition(val key: AgentKey, val hostLabel: String, val pane: Pane, val toBlocked: Boolean, val completed: Boolean)

    /**
     * 主机表：只在 [lock] 里整体替换成新的只读 Map，读的一方（10 秒对账、界面线程、Sender 等）
     * 拿到的永远是完整的一版，不会撞上“改了一半”的 HashMap。
     */
    @Volatile private var runtimes: Map<String, HostRuntime> = emptyMap()
    private val watchers = HashMap<String, Job>() // 只在 lock 里 / stop() 里动
    private val inputs = MutableStateFlow<Map<String, HostInput>>(emptyMap())
    /** 各主机的收集协程会同时更新它：所有访问都在 synchronized(tracker) 里。 */
    private val tracker = AttentionTracker()
    private val lock = Mutex()

    private val _home = MutableStateFlow(HomeDerivation.derive(emptyList(), emptyMap(), clock()))
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _links = MutableStateFlow<Map<String, HostLink>>(emptyMap())
    val links: StateFlow<Map<String, HostLink>> = _links.asStateFlow()

    /** 多台主机可能同时等待指纹确认：排队，一次只问一个。 */
    private val hostKeyQueue = MutableStateFlow<Map<String, HostKeyPrompt>>(emptyMap())
    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = hostKeyQueue
        .map { it.values.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _transitions = MutableSharedFlow<Transition>(extraBufferCapacity = 64)
    val transitions: SharedFlow<Transition> = _transitions.asSharedFlow()

    private var started = false
    private var ticker: Job? = null

    /** 幂等：已经在跑的主机不碰（不触发重连，旧版 I-73 的教训）。 */
    fun start() {
        if (started) return
        started = true
        scope.launch { hosts.hosts.collect { list -> sync(list) } }
        // 低频兜底对账：事件只是失效信号，可能漏；10 秒一次快照对账保证“刚完成”不漏。
        ticker = scope.launch {
            while (true) {
                delay(RECONCILE_EVERY_MS)
                runtimes.values.forEach { it.refresh() }
                recompute() // 时间相关文案（“刚完成”窗口）也需要推进
            }
        }
    }

    fun stop() {
        ticker?.cancel(); ticker = null
        val rts = runtimes
        runtimes = emptyMap()
        rts.values.forEach { it.stop() }
        synchronized(watchers) { watchers.values.forEach { it.cancel() }; watchers.clear() }
        started = false
    }

    fun reconnect(hostId: String) = runtimes[hostId]?.reconnect() ?: Unit
    fun reconnectAll() = runtimes.values.forEach { it.reconnect() }
    fun refresh(hostId: String) = runtimes[hostId]?.refresh() ?: Unit
    fun refreshAll() = runtimes.values.forEach { it.refresh() }

    fun answerHostKey(accept: Boolean) {
        val p = hostKeyPrompt.value ?: return
        runtimes[p.hostId]?.answerHostKey(accept)
    }

    fun connection(hostId: String): SshConnection? = runtimes[hostId]?.connection()
    internal fun runtime(hostId: String): HostRuntime? = runtimes[hostId]
    fun config(hostId: String): HostRepository.HostConfig? = runtimes[hostId]?.cfg
    fun isOnline(hostId: String): Boolean = _links.value[hostId]?.reach == HostReach.Online

    /** 当前权威（或最后看到的）pane。 */
    fun pane(key: AgentKey): Pane? = inputs.value[key.hostId]?.panes?.firstOrNull { it.paneId == key.paneId }

    /** 某台主机当前（或最后看到的）全部 pane。 */
    fun panes(hostId: String): List<Pane> = inputs.value[hostId]?.panes.orEmpty()

    private suspend fun sync(all: List<HostRepository.HostConfig>) = lock.withLock {
        // 停用的主机当作不存在：不建连接；运行中的被停用 → 和删除一样停掉、从首页拿走
        val list = all.filter { it.enabled }
        val ids = list.map { it.id }.toSet()
        val next = LinkedHashMap(runtimes)
        // 删除 / 配置变化的主机：停掉
        next.entries.removeAll { (id, rt) ->
            val gone = id !in ids || list.first { it.id == id } != rt.cfg
            if (gone) {
                rt.stop(); synchronized(watchers) { watchers.remove(id)?.cancel() }
                synchronized(tracker) { tracker.forgetHost(id) }
                hostKeyQueue.update { it - id }
                inputs.update { it - id }
                _links.update { it - id }
            }
            gone
        }
        list.forEach { cfg ->
            if (cfg.id in next) return@forEach
            val rt = HostRuntime(cfg, hosts, connectorFor(cfg.id), scope) { prompt ->
                hostKeyQueue.update { q -> if (prompt != null) q + (cfg.id to prompt) else q - cfg.id }
            }
            next[cfg.id] = rt
            synchronized(watchers) { watchers[cfg.id] = scope.launch { watch(rt) } }
        }
        runtimes = next
        recompute()
    }

    private suspend fun watch(rt: HostRuntime) {
        val id = rt.cfg.id
        // 冷启动：先显示库里的“最后看到”
        val cached = runCatching { boards.observe(id, rt.cfg.session).first() }.getOrNull()
        inputs.update { it + (id to HostInput(id, rt.cfg.label, HostReach.Connecting, cached?.panes.orEmpty(), cached?.fetchedAtMs ?: 0)) }
        recompute()

        rt.start()
        // 两个收集都是本 watcher 的子协程：主机被删除 / 配置变化时 watcher 一取消，它们一起停，
        // 不会有旧连接的状态写进同一个主机 id
        coroutineScope {
        launch {
            rt.state.collect { st ->
                val link = HostRuntime.link(st, hosts.hasKey(id))
                _links.update { it + (id to link) }
                val prev = inputs.value[id]
                if (prev != null && prev.reach != link.reach) {
                    inputs.update { it + (id to prev.copy(reach = link.reach)) }
                    recompute()
                }
            }
        }
        // supervisor.start() 之后 reconciler 才可用
        val rec = rt.reconciler ?: return@coroutineScope
        rec.state.collect { rs ->
            if (rs.fetchedAtMs <= 0) return@collect
            val now = clock()
            val reach = _links.value[id]?.reach ?: HostReach.Online
            val trs = if (!rs.stale) synchronized(tracker) { tracker.update(id, rs.panes, now) } else emptyList()
            inputs.update { it + (id to HostInput(id, rt.cfg.label, if (rs.stale) HostReach.Unreachable else reach, rs.panes, rs.fetchedAtMs)) }
            recompute()
            trs.forEach { t ->
                val pane = rs.panes.firstOrNull { it.paneId == t.key.paneId } ?: return@forEach
                val toBlocked = t.to == io.github.luxiang0412.kelpie.core.herdr.AgentStatus.BLOCKED && t.from != t.to && t.from != null
                if (toBlocked || t.completed) _transitions.tryEmit(Transition(t.key, rt.cfg.label, pane, toBlocked, t.completed))
            }
            runCatching {
                boards.replace(id, rt.cfg.session, rs.panes, rs.version, rs.protocol, rs.fetchedAtMs, rs.stale, rs.lastError)
            }.onFailure { Log.w(TAG, "persist failed: ${it.message}") }
        }
        }
    }

    private fun recompute() {
        val order = runtimes.keys.toList()
        val list = order.mapNotNull { inputs.value[it] }
        val snap = synchronized(tracker) { tracker.snapshot() }
        _home.value = HomeDerivation.derive(list, snap, clock())
    }

    /** 单测 / 调试：直接读派生输入。 */
    internal fun inputsForTest(): Map<String, HostInput> = inputs.value

    companion object {
        private const val TAG = "Fleet"
        const val RECONCILE_EVERY_MS = 10_000L

        /** 横幅：区分“手机没网”与“连不上主机”（规范 5.15）。 */
        fun failureIsAuthOrKey(kind: ConnectionSupervisor.Kind?) = kind == ConnectionSupervisor.Kind.AUTH ||
            kind == ConnectionSupervisor.Kind.KEY || kind == ConnectionSupervisor.Kind.HOST_KEY_CHANGED ||
            kind == ConnectionSupervisor.Kind.HOST_KEY_UNKNOWN || kind == ConnectionSupervisor.Kind.CONFIG
    }
}
