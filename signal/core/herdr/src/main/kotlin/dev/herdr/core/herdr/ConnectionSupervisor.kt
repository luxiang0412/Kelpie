package dev.herdr.core.herdr

import dev.herdr.core.ssh.HostKeyId
import dev.herdr.core.ssh.HostKeyUnknownException
import dev.herdr.core.ssh.SshConfig
import dev.herdr.core.ssh.SshConnector
import dev.herdr.core.ssh.SshFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 连接监管：连接代次、断线重连、Stale 判定、订阅生命周期。
 *
 * 事件只用来通知 Reconciler 失效（D9）；这里负责：
 *  - 首次连接 → ping 校验协议 → snapshot → 订阅（全局 + 逐 pane）；
 *  - `pane.created` / `pane.closed` → 重新订阅（新的 pane 需要补状态订阅）；
 *  - 订阅中断 / 连接死亡 → 退避重连（1s→2s→4s→8s→15s 封顶）；
 *  - 超过 [staleAfterMs] 没有成功刷新 → Stale。
 */
class ConnectionSupervisor(
    private val scope: CoroutineScope,
    private val connector: SshConnector,
    private val configProvider: () -> SshConfig?,
    private val onUnknownHostKey: (HostKeyUnknownException) -> Boolean,
    private val staleAfterMs: Long = 25_000,
    private val requiredProtocol: Int = 22,
) {
    sealed interface State {
        data object Idle : State
        data class Connecting(val attempt: Int) : State
        data class AwaitingHostKey(val id: String, val fingerprint: String) : State
        data class Connected(val generation: Long, val version: String, val protocol: Int, val sinceMs: Long) : State
        data class Stale(val generation: Long, val reason: String) : State
        data class Reconnecting(val attempt: Int, val delayMs: Long, val lastError: String?) : State
        data class Failed(val message: String, val kind: Kind) : State
    }

    enum class Kind { CONFIG, HOST_KEY_UNKNOWN, HOST_KEY_CHANGED, AUTH, NETWORK, PROTOCOL, KEY, UNKNOWN }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    lateinit var reconciler: SessionReconciler
        private set

    private var loopJob: Job? = null
    private var resubscribe = Channel<Unit>(Channel.CONFLATED)

    /** 当前活动连接（供 transcript 读取等一次性操作使用）；不可用时返回 null。 */
    fun currentConnection(): dev.herdr.core.ssh.SshConnection? = currentConnection

    val isConnected: Boolean
        get() = _state.value is State.Connected || _state.value is State.Stale

    fun start() {
        if (loopJob?.isActive == true) return
        reconciler = SessionReconciler(
            scope = scope,
            fetch = {
                val conn = currentConnection ?: throw SshFailure.Network("not connected")
                val cfg = configProvider() ?: throw SshFailure.Network("no config")
                HerdrApi(conn, cfg.herdrBin, cfg.herdrSession).snapshot()
            },
        )
        loopJob = scope.launch { superviseLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        subscriptionJob?.cancel()
        currentConnection?.close()
        currentConnection = null
        _state.value = State.Idle
        runCatching { reconciler.clear() }
    }

    fun reconnectNow() {
        resubscribe.trySend(Unit)
        manualReconnect.trySend(Unit)
    }

    private val manualReconnect = Channel<Unit>(Channel.CONFLATED)
    private var currentConnection: dev.herdr.core.ssh.SshConnection? = null
    private var subscriptionJob: Job? = null

    private suspend fun superviseLoop() {
        var attempt = 0
        while (scope.isActive) {
            val cfg = configProvider()
            if (cfg == null) {
                _state.value = State.Failed("还没有配置主机", Kind.CONFIG)
                delay(1_000)
                continue
            }
            _state.value = State.Connecting(attempt + 1)
            val conn = try {
                connector.connect(cfg) { ex ->
                    _state.value = State.AwaitingHostKey(ex.id.toString(), ex.fingerprint)
                    val accepted = onUnknownHostKey(ex)
                    if (!accepted) _state.value = State.Failed("用户拒绝信任该主机指纹", Kind.HOST_KEY_UNKNOWN)
                    accepted
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val (kind, msg) = classify(e)
                log("connect failed kind=$kind msg=$msg cause=${e.cause?.javaClass?.simpleName}")
                if (kind == Kind.HOST_KEY_UNKNOWN || kind == Kind.HOST_KEY_CHANGED) {
                    _state.value = State.Failed(msg, kind)
                    delay(2_000)
                    continue
                }
                attempt++
                val backoff = backoffMs(attempt)
                _state.value = State.Reconnecting(attempt, backoff, msg)
                delay(backoff)
                continue
            }

            currentConnection = conn
            try {
                val api = HerdrApi(conn, cfg.herdrBin, cfg.herdrSession)
                log("connected generation=${conn.generation}; ping…")
                val ping = api.ping()
                log("ping ok version=${ping.version} protocol=${ping.protocol}")
                if (ping.protocol != requiredProtocol) {
                    _state.value = State.Failed(
                        "协议版本不匹配：server=${ping.protocol}, app 支持 $requiredProtocol",
                        Kind.PROTOCOL,
                    )
                    conn.close(); currentConnection = null
                    delay(5_000)
                    continue
                }
                attempt = 0
                val since = System.currentTimeMillis()
                _state.value = State.Connected(conn.generation, ping.version, ping.protocol, since)
                reconciler.resetForNewConnection()
                startWatchdog(conn.generation, api)
                runSession(api, conn)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val (kind, msg) = classify(e)
                log("session failed kind=$kind msg=$msg")
                attempt++
                val backoff = backoffMs(attempt)
                _state.value = State.Reconnecting(attempt, backoff, msg)
                delay(backoff)
            } finally {
                subscriptionJob?.cancel()
                conn.close()
                if (currentConnection === conn) currentConnection = null
            }
        }
    }

    /** 连接存活期间：维持订阅，直到订阅结束 / 连接死亡 / 手动重连。 */
    private suspend fun runSession(api: HerdrApi, conn: dev.herdr.core.ssh.SshConnection) {
        while (scope.isActive && conn.isAlive) {
            val snap = api.snapshot()
            val subs = buildSubscriptions(snap)
            val job = scope.launch {
                api.subscribe(subs).collect { ev ->
                    reconciler.onEvent(ev)
                    if (ev.type == "pane_created" || ev.type == "pane_closed") {
                        resubscribe.trySend(Unit)
                    }
                }
            }
            subscriptionJob = job
            // 等：订阅结束、需要重订阅、连接死亡、或手动重连
            var manual = false
            withTimeoutOrNull(staleAfterMs + 5_000) {
                wait@ while (scope.isActive) {
                    if (!conn.isAlive) break@wait
                    if (job.isCompleted) break@wait
                    if (resubscribe.tryReceive().isSuccess) break@wait
                    if (manualReconnect.tryReceive().isSuccess) { manual = true; break@wait }
                    delay(250)
                }
            }
            job.cancel()
            if (manual) throw SshFailure.Network("manual reconnect")
        }
        if (!conn.isAlive) throw SshFailure.Network("connection lost")
    }

    private fun buildSubscriptions(snap: SessionSnapshot): List<JsonObject> {
        val globals = listOf(
            "pane.created", "pane.closed", "pane.updated", "pane.exited",
            "tab.created", "tab.closed", "layout.updated",
        ).map { buildJsonObject { put("type", it) } }
        val perPane = snap.panes.map { pane ->
            buildJsonObject {
                put("type", "pane.agent_status_changed")
                put("pane_id", pane.paneId)
            }
        }
        return globals + perPane
    }

    /**
     * 存活探测：**空闲会话没有事件是正常的**，所以不能用“多久没刷新”判过期（D29 事件驱动）。
     * 改为每 [staleAfterMs] 发一次 `ping`：失败才进入 Stale；成功则把 Stale 恢复成 Connected。
     */
    private var watchdogJob: Job? = null
    private fun startWatchdog(generation: Long, api: HerdrApi) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (scope.isActive) {
                delay(staleAfterMs)
                val st = _state.value
                if (st !is State.Connected && st !is State.Stale) continue
                if ((st as? State.Connected)?.generation != generation &&
                    (st as? State.Stale)?.generation != generation
                ) continue
                val ok = runCatching { api.ping(8_000) }.isSuccess
                if (!ok) {
                    _state.value = State.Stale(generation, "连接探测失败，数据可能不是最新")
                } else if (_state.value is State.Stale) {
                    _state.value = State.Connected(
                        generation,
                        (_state.value as State.Stale).let { "0.9.5" },
                        requiredProtocol,
                        System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    private fun backoffMs(attempt: Int): Long = when (attempt) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        4 -> 8_000L
        else -> 15_000L
    }

    private fun log(msg: String) {
        android.util.Log.i("HerdrConn", msg)
    }

    private fun classify(e: Throwable): Pair<Kind, String> = when (e) {
        is SshFailure.UnknownHostKey -> Kind.HOST_KEY_UNKNOWN to "未知主机指纹（未确认）"
        is SshFailure.HostKeyChanged -> Kind.HOST_KEY_CHANGED to "主机指纹变化，已拒绝连接"
        is SshFailure.Auth -> Kind.AUTH to "认证失败：${e.message}"
        is SshFailure.KeyUnusable -> Kind.KEY to "私钥不可用：${e.message}"
        is SshFailure.Protocol -> Kind.PROTOCOL to "协议错误：${e.message}"
        is SshFailure.Timeout -> Kind.NETWORK to "超时：${e.message}"
        is SshFailure.Network -> Kind.NETWORK to "网络错误：${e.message}"
        else -> Kind.UNKNOWN to (e.message ?: e.javaClass.simpleName)
    }
}
