package dev.herdr.core.data.runtime

import android.util.Log
import dev.herdr.core.data.BoardRepository
import dev.herdr.core.data.HostRepository
import dev.herdr.core.data.attention.AgentKey
import dev.herdr.core.data.attention.AttentionTracker
import dev.herdr.core.data.attention.HomeDerivation
import dev.herdr.core.data.attention.HomeState
import dev.herdr.core.data.attention.HostInput
import dev.herdr.core.data.attention.HostReach
import dev.herdr.core.herdr.ConnectionSupervisor
import dev.herdr.core.herdr.Pane
import dev.herdr.core.ssh.SshConnection
import dev.herdr.core.ssh.SshConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val runtimes = LinkedHashMap<String, HostRuntime>()
    private val watchers = HashMap<String, Job>()
    private val inputs = MutableStateFlow<Map<String, HostInput>>(emptyMap())
    private val tracker = AttentionTracker()
    private val lock = Mutex()

    private val _home = MutableStateFlow(HomeDerivation.derive(emptyList(), emptyMap(), clock()))
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _links = MutableStateFlow<Map<String, HostLink>>(emptyMap())
    val links: StateFlow<Map<String, HostLink>> = _links.asStateFlow()

    private val _hostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = _hostKeyPrompt.asStateFlow()

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
        runtimes.values.forEach { it.stop() }
        watchers.values.forEach { it.cancel() }
        runtimes.clear(); watchers.clear()
        started = false
    }

    fun reconnect(hostId: String) = runtimes[hostId]?.reconnect() ?: Unit
    fun reconnectAll() = runtimes.values.forEach { it.reconnect() }
    fun refresh(hostId: String) = runtimes[hostId]?.refresh() ?: Unit
    fun refreshAll() = runtimes.values.forEach { it.refresh() }

    fun answerHostKey(accept: Boolean) {
        val p = _hostKeyPrompt.value ?: return
        runtimes[p.hostId]?.answerHostKey(accept)
    }

    fun connection(hostId: String): SshConnection? = runtimes[hostId]?.connection()
    internal fun runtime(hostId: String): HostRuntime? = runtimes[hostId]
    fun config(hostId: String): HostRepository.HostConfig? = runtimes[hostId]?.cfg
    fun isOnline(hostId: String): Boolean = _links.value[hostId]?.reach == HostReach.Online

    /** 当前权威（或最后看到的）pane。 */
    fun pane(key: AgentKey): Pane? = inputs.value[key.hostId]?.panes?.firstOrNull { it.paneId == key.paneId }

    private suspend fun sync(list: List<HostRepository.HostConfig>) = lock.withLock {
        val ids = list.map { it.id }.toSet()
        // 删除 / 配置变化的主机：停掉
        runtimes.entries.removeAll { (id, rt) ->
            val gone = id !in ids || list.first { it.id == id } != rt.cfg
            if (gone) {
                rt.stop(); watchers.remove(id)?.cancel(); tracker.forgetHost(id)
                inputs.update { it - id }
                _links.update { it - id }
            }
            gone
        }
        list.forEach { cfg ->
            if (cfg.id in runtimes) return@forEach
            val rt = HostRuntime(cfg, hosts, connectorFor(cfg.id), scope) { prompt ->
                _hostKeyPrompt.value = prompt ?: _hostKeyPrompt.value?.takeIf { it.hostId != cfg.id }
            }
            runtimes[cfg.id] = rt
            watchers[cfg.id] = scope.launch { watch(rt) }
        }
        recompute()
    }

    private suspend fun watch(rt: HostRuntime) {
        val id = rt.cfg.id
        // 冷启动：先显示库里的“最后看到”
        val cached = runCatching { boards.observe(id, rt.cfg.session).first() }.getOrNull()
        inputs.update { it + (id to HostInput(id, rt.cfg.label, HostReach.Connecting, cached?.panes.orEmpty(), cached?.fetchedAtMs ?: 0)) }
        recompute()

        rt.start()
        scope.launch {
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
        val rec = rt.reconciler ?: return
        rec.state.collect { rs ->
            if (rs.fetchedAtMs <= 0) return@collect
            val now = clock()
            val reach = _links.value[id]?.reach ?: HostReach.Online
            val trs = if (!rs.stale) tracker.update(id, rs.panes, now) else emptyList()
            inputs.update { it + (id to HostInput(id, rt.cfg.label, if (rs.stale) HostReach.Unreachable else reach, rs.panes, rs.fetchedAtMs)) }
            recompute()
            trs.forEach { t ->
                val pane = rs.panes.firstOrNull { it.paneId == t.key.paneId } ?: return@forEach
                val toBlocked = t.to == dev.herdr.core.herdr.AgentStatus.BLOCKED && t.from != t.to && t.from != null
                if (toBlocked || t.completed) _transitions.tryEmit(Transition(t.key, rt.cfg.label, pane, toBlocked, t.completed))
            }
            runCatching {
                boards.replace(id, rt.cfg.session, rs.panes, rs.version, rs.protocol, rs.fetchedAtMs, rs.stale, rs.lastError)
            }.onFailure { Log.w(TAG, "persist failed: ${it.message}") }
        }
    }

    private fun recompute() {
        val order = runtimes.keys.toList()
        val list = order.mapNotNull { inputs.value[it] }
        _home.value = HomeDerivation.derive(list, tracker.snapshot(), clock())
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
