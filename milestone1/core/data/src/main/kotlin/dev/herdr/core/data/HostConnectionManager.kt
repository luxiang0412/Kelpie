package dev.herdr.core.data

import dev.herdr.core.herdr.AgentStatus
import dev.herdr.core.herdr.ConnectionSupervisor
import dev.herdr.core.herdr.HerdrApi
import dev.herdr.core.herdr.Pane
import dev.herdr.core.herdr.WriteLedger
import dev.herdr.core.herdr.WriteOutcome
import dev.herdr.core.ssh.HostKeyUnknownException
import dev.herdr.core.ssh.SshConnection
import dev.herdr.core.ssh.SshConnector
import dev.herdr.core.ssh.SshFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 进程级的主机连接管理（M4-2）。
 *
 * 为什么放在这里、而不是 ViewModel：
 * - 前台服务要在 App 切后台后继续持有 SSH 连接 → 连接不能绑在 Activity/ViewModel 上；
 * - 仍是**每台主机一个 [ConnectionSupervisor]**（连接代次/退避/订阅/Stale 隔离，D37）；
 * - UI 只订阅它的 StateFlow，并通过它发指令（单向数据流不变）。
 */
class HostConnectionManager(
    private val hosts: HostRepository,
    private val boards: BoardRepository,
    private val transcripts: TranscriptStore,
    private val connectorFor: (String) -> SshConnector,
    private val scope: CoroutineScope,
    pendingWriteSink: dev.herdr.core.herdr.PendingWriteSink? = null,
) {

    enum class ConnKind { IDLE, CONNECTING, AWAITING_HOST_KEY, CONNECTED, STALE, RECONNECTING, FAILED }

    data class HostState(
        val cfg: HostRepository.HostConfig,
        val hasKey: Boolean,
        /** 传输状态（SSH 连接本身） */
        val connKind: ConnKind = ConnKind.IDLE,
        val connText: String = "未连接",
        val lastError: String? = null,
        val paneCount: Int = 0,
        /** 数据有效性（最近一次权威快照）：与传输状态分开，避免“空闲=过期”的误判（D34/D8） */
        val dataStale: Boolean = true,
        val dataAtMs: Long = 0,
        val dataVersion: String = "",
    )

    data class HostKeyPrompt(val hostId: String, val label: String, val fingerprint: String)

    /** 状态变化事件，供通知层使用。 */
    data class StatusChange(
        val hostId: String,
        val hostLabel: String,
        val pane: Pane,
        val from: AgentStatus,
        val to: AgentStatus,
    )

    private val _hosts = MutableStateFlow<List<HostState>>(emptyList())
    val hostsState: StateFlow<List<HostState>> = _hosts.asStateFlow()

    private val _hostKeyPrompt = MutableStateFlow<HostKeyPrompt?>(null)
    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = _hostKeyPrompt.asStateFlow()

    data class ApprovalItem(
        val hostId: String,
        val hostLabel: String,
        val request: Approvals.Request,
    )

    private val _approvals = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val approvals: StateFlow<List<ApprovalItem>> = _approvals.asStateFlow()

    private val audit = ArrayList<String>()

    private val _statusChanges = MutableSharedFlow<StatusChange>(extraBufferCapacity = 64)
    val statusChanges: SharedFlow<StatusChange> = _statusChanges.asSharedFlow()

    private val _composer = MutableStateFlow(ComposerState())
    val composer: StateFlow<ComposerState> = _composer.asStateFlow()

    data class ComposerState(
        val hostId: String? = null,
        val text: String = "",
        val sending: Boolean = false,
        val outcome: WriteOutcome? = null,
        val message: String? = null,
        /** 本次提交对应的 Action Journal id（Unknown 时用于重新检查/确认）。 */
        val pendingActionId: String? = null,
        /** 已提交但结果未知的文本快照（不会被自动重发）。 */
        val pendingText: String? = null,
        /** 编辑时的目标身份（用于发送前校验）。 */
        val editedAgentSession: String? = null,
        val editedOccupancy: String? = null,
    )

    private class Runtime(
        val cfg: HostRepository.HostConfig,
        val connector: SshConnector,
        var supervisor: ConnectionSupervisor? = null,
        var persistJob: Job? = null,
        var pendingKey: CompletableDeferred<Boolean>? = null,
        var lastStatuses: Map<String, AgentStatus> = emptyMap(),
        var lastCompletion: Map<String, Long> = emptyMap(),
    )

    private val runtimes = LinkedHashMap<String, Runtime>()
    private val writeLedger = WriteLedger(sink = pendingWriteSink)
    private var started = false
    private var ticker: Job? = null

    /**
     * 进程启动 / 服务启动时调用：**幂等**。
     * 已在运行的主机不做任何事（尤其不能再触发 reconnectNow，见 I-73）。
     */
    fun startAll() {
        reload(startConnections = true)
        started = true
        startTickerIfNeeded()
    }

    /** 后台监控开关（决定是否跑低频对账与审批轮询）。 */
    fun setMonitoring(on: Boolean) {
        monitoring = on
        if (on) startTickerIfNeeded() else stopTicker()
    }

    var monitoring: Boolean = false
        private set

    fun reload(startConnections: Boolean = false) {
        val list = hosts.list()
        val next = LinkedHashMap<String, Runtime>()
        list.forEach { cfg ->
            val old = runtimes[cfg.id]
            next[cfg.id] = if (old != null && old.cfg == cfg) old
            else Runtime(cfg, connectorFor(cfg.id))
        }
        runtimes.filterKeys { it !in next.keys }.values.forEach { stopRuntime(it) }
        runtimes.clear()
        runtimes.putAll(next)
        _hosts.value = list.map { cfg ->
            HostState(
                cfg = cfg,
                hasKey = hosts.hasKey(cfg.id),
                connKind = runtimes[cfg.id]?.supervisor?.let { kindOf(it) } ?: ConnKind.IDLE,
            )
        }
        if (startConnections) runtimes.values.forEach { ensureStarted(it) }
        started = startConnections
        startTickerIfNeeded()
    }

    fun isRunning(): Boolean = started

    /**
     * 后台通知的兜底对账：每 5 s 让每台主机重新拉一次 snapshot。
     *
     * 为什么需要（与 D29 不矛盾）：D29 说的是“UI 不要靠轮询”，而通知必须**不漏**——
     * 事件驱动在对账间隙可能漏掉 `done→working→done`，所以监控模式额外做低频对账。
     * 1 请求/5 s/主机，代价可接受；停止监控即停。
     */
    private fun startTickerIfNeeded() {
        if (ticker?.isActive == true) return
        if (!started || !monitoring) return
        var tick = 0
        ticker = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                tick++
                runtimes.values.forEach { rt ->
                    if (tick % 3 == 0) rt.supervisor?.reconciler?.markDirty()   // 6s 一次状态对账
                    pollApprovals(rt)                                            // 2s 一次审批检查
                }
            }
        }
    }

    /** 轮询沙箱审批目录（L1 文件 IPC）。 */
    private suspend fun pollApprovals(rt: Runtime) {
        val conn = rt.supervisor?.currentConnection() ?: return
        val dir = "/home/${rt.cfg.user}/spike-sandbox/approvals"
        val list = runCatching { Approvals(conn, dir).poll() }.getOrNull() ?: return
        val now = list.filter { !it.expired }.map {
            ApprovalItem(rt.cfg.id, rt.cfg.label, it)
        }
        // 合并多主机结果（按 hostId 替换本主机的部分）
        val others = _approvals.value.filter { it.hostId != rt.cfg.id }
        _approvals.value = others + now
    }

    fun answerApproval(item: ApprovalItem, allow: Boolean) {
        val conn = currentConnection(item.hostId) ?: return
        val dir = "/home/${hosts.get(item.hostId)?.user ?: "luxiang"}/spike-sandbox/approvals"
        scope.launch {
            val ok = runCatching { Approvals(conn, dir).answer(item.request, allow) }.getOrDefault(false)
            audit += "${System.currentTimeMillis()} ${item.hostId}/${item.request.requestId} " +
                "decision=${if (allow) "allow" else "deny"} written=$ok nonce=${item.request.nonce.take(6)}"
            _approvals.value = _approvals.value.filterNot { it.request.requestId == item.request.requestId }
        }
    }

    fun approvalAudit(): List<String> = audit.toList()

    fun stopTicker() {
        ticker?.cancel(); ticker = null
    }

    /** 用户主动连接：未连接则建立；已连接则强制重连（唯一允许触发新 generation 的入口）。 */
    fun connect(id: String) = runtimes[id]?.let {
        val sup = it.supervisor
        if (sup == null) ensureStarted(it) else sup.reconnectNow()
    } ?: Unit

    fun disconnect(id: String) = runtimes[id]?.let {
        stopRuntime(it)
        started = false
    } ?: Unit

    fun reconnect(id: String) = runtimes[id]?.supervisor?.reconnectNow() ?: Unit

    fun refresh(id: String) = runtimes[id]?.supervisor?.reconciler?.markDirty() ?: Unit

    fun currentConnection(id: String): SshConnection? = runtimes[id]?.supervisor?.currentConnection()

    fun answerHostKey(accept: Boolean) {
        val prompt = _hostKeyPrompt.value ?: return
        runtimes[prompt.hostId]?.pendingKey?.complete(accept)
        _hostKeyPrompt.value = null
    }

    fun board(hostId: String, session: String) = boards.observe(hostId, session)

    /** 只读：读 pane 的终端输出（用于详情页“最近输出”）。 */
    suspend fun readPane(hostId: String, paneId: String, lines: Int = 80): String? {
        val conn = currentConnection(hostId) ?: return null
        val cfg = hosts.get(hostId) ?: return null
        return runCatching {
            HerdrApi(conn, cfg.herdrBin, cfg.session).readPane(paneId, "recent", lines)
        }.getOrNull()
    }

    /** 暴露只读缓存给 UI 订阅（对话页）。 */
    fun transcripts(): TranscriptStore = transcripts

    fun openTranscript(hostId: String, path: String): TranscriptTailer? {
        val conn = currentConnection(hostId) ?: return null
        return TranscriptTailer(conn, transcripts, hostId, path)
    }

    // ---------------- 写操作 ----------------

    fun updateComposer(hostId: String?, text: String) {
        _composer.value = _composer.value.copy(
            hostId = hostId, text = text, outcome = null, message = null,
        )
    }

    /** 打开某个 pane 的对话时记录目标身份（发送前校验用）。 */
    fun setComposerTarget(hostId: String?, pane: dev.herdr.core.herdr.Pane?) {
        _composer.value = _composer.value.copy(
            hostId = hostId,
            editedAgentSession = pane?.agentSessionPath?.takeIf { it.isNotBlank() },
            editedOccupancy = pane?.let { dev.herdr.core.data.drafts.DraftKey.occupancyFingerprint(it) },
        )
    }

    fun clearComposerOutcome() {
        _composer.value = _composer.value.copy(outcome = null, message = null)
    }

    fun sendPrompt(hostId: String, pane: Pane, writable: Boolean = true) {
        val raw = _composer.value.text
        val text = raw.trim()
        if (text.isEmpty() || _composer.value.sending) return

        // 发送前目标校验（离线只允许存草稿，不排队）
        when (val check = dev.herdr.core.data.drafts.SendTargetCheck.check(
            pane = pane,
            writable = writable,
            editedAgentSession = _composer.value.editedAgentSession,
            editedOccupancy = _composer.value.editedOccupancy,
        )) {
            is dev.herdr.core.data.drafts.SendTargetCheck.Result.Ok -> Unit
            is dev.herdr.core.data.drafts.SendTargetCheck.Result.Blocked -> {
                _composer.value = _composer.value.copy(
                    sending = false,
                    outcome = WriteOutcome.Rejected(check.code, check.reason),
                    message = check.reason,
                )
                return
            }
            is dev.herdr.core.data.drafts.SendTargetCheck.Result.NeedsConfirm -> {
                _composer.value = _composer.value.copy(
                    sending = false,
                    outcome = WriteOutcome.Rejected("target_changed", check.reason),
                    message = "${check.reason}；请确认后重新点击发送",
                )
                return
            }
        }

        val conn = currentConnection(hostId)
        val cfg = hosts.get(hostId)
        if (conn == null || cfg == null) {
            _composer.value = _composer.value.copy(
                outcome = WriteOutcome.Rejected("not_connected", "主机未连接"),
                message = "主机未连接",
            )
            return
        }
        val snapshot = raw
        val rec = writeLedger.begin("agent.prompt", pane.paneId, text.toByteArray())
        _composer.value = _composer.value.copy(
            sending = true, outcome = null, message = "发送中…", pendingActionId = rec.clientRequestId,
        )
        scope.launch {
            val api = HerdrApi(conn, cfg.herdrBin, cfg.session)
            val result = runCatching { api.prompt(pane.paneId, text) }
            val outcome = result.fold(
                onSuccess = {
                    writeLedger.accept(rec.clientRequestId, "agent_prompted")
                    WriteOutcome.Accepted
                },
                onFailure = { e ->
                    when (e) {
                        is SshFailure.Timeout, is SshFailure.Network -> {
                            writeLedger.markUnknown(rec.clientRequestId, e.message ?: "timeout/network")
                            WriteOutcome.Unknown(e.message ?: "timeout/network")
                        }
                        is SshFailure.Protocol -> {
                            val msg = e.message ?: "protocol error"
                            val code = msg.substringBefore(":").ifBlank { "error" }
                            writeLedger.reject(rec.clientRequestId, code, msg)
                            WriteOutcome.Rejected(code, msg)
                        }
                        else -> {
                            writeLedger.markUnknown(rec.clientRequestId, e.message ?: "unknown")
                            WriteOutcome.Unknown(e.message ?: "unknown")
                        }
                    }
                },
            )
            _composer.value = _composer.value.copy(
                sending = false,
                outcome = outcome,
                // Accepted 只在「草稿自提交后没变」时清空；用户已经继续输入则保留新内容
                text = if (outcome is WriteOutcome.Accepted && _composer.value.text == snapshot) "" else _composer.value.text,
                pendingActionId = if (outcome is WriteOutcome.Unknown) rec.clientRequestId else null,
                pendingText = if (outcome is WriteOutcome.Unknown) snapshot else null,
                message = when (outcome) {
                    is WriteOutcome.Accepted -> "已接受（Accepted）：远端已收到，未代表执行完成"
                    is WriteOutcome.Rejected -> "被拒绝：${outcome.code}（内容已保留，可修改后重发）"
                    is WriteOutcome.Unknown -> "结果未知（Unknown）：不会自动重发，请稍后在会话里确认"
                    else -> null
                },
            )
        }
    }

    fun recentWrites() = writeLedger.recent()

    /** 进程重建后恢复未确认（Pending/Unknown）写操作；不会自动重放。 */
    fun restorePendingWrites(records: List<dev.herdr.core.herdr.WriteRecord>) = writeLedger.restore(records)

    // ---------------- 内部 ----------------

    private fun ensureStarted(rt: Runtime) {
        // 幂等：已经在运行（或正在重连）的主机不再触碰，避免启动时白连一次（I-73）
        if (rt.supervisor != null) return
        if (!hosts.hasKey(rt.cfg.id)) {
            update(rt.cfg.id, ConnKind.IDLE, "未导入私钥", null)
            return
        }
        val sup = ConnectionSupervisor(
            scope = scope,
            connector = rt.connector,
            configProvider = { hosts.sshConfig(rt.cfg.id) },
            onUnknownHostKey = { ex -> awaitHostKey(rt, ex) },
        )
        rt.supervisor = sup
        sup.start()
        rt.persistJob = scope.launch { persistLoop(rt, sup) }
        scope.launch { collectState(rt, sup) }
        started = true
    }

    private fun stopRuntime(rt: Runtime) {
        rt.persistJob?.cancel(); rt.persistJob = null
        rt.supervisor?.stop(); rt.supervisor = null
        update(rt.cfg.id, ConnKind.IDLE, "已断开", null)
    }

    private fun awaitHostKey(rt: Runtime, ex: HostKeyUnknownException): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        rt.pendingKey = deferred
        _hostKeyPrompt.value = HostKeyPrompt(rt.cfg.id, rt.cfg.label, ex.fingerprint)
        update(rt.cfg.id, ConnKind.AWAITING_HOST_KEY, "等待确认主机指纹", null)
        return runBlocking { withTimeoutOrNull(90_000) { deferred.await() } ?: false }
    }

    private suspend fun collectState(rt: Runtime, sup: ConnectionSupervisor) {
        sup.state.collect { st ->
            val (kind, text, err) = when (st) {
                is ConnectionSupervisor.State.Idle -> Triple(ConnKind.IDLE, "未连接", null)
                is ConnectionSupervisor.State.Connecting -> Triple(ConnKind.CONNECTING, "连接中…（第 ${st.attempt} 次）", null)
                is ConnectionSupervisor.State.AwaitingHostKey -> Triple(ConnKind.AWAITING_HOST_KEY, "等待确认主机指纹", null)
                is ConnectionSupervisor.State.Connected -> Triple(
                    ConnKind.CONNECTED, "已连接 · herdr ${st.version} · protocol ${st.protocol}", null,
                )
                is ConnectionSupervisor.State.Stale -> Triple(ConnKind.STALE, "数据可能过期：${st.reason}", st.reason)
                is ConnectionSupervisor.State.Reconnecting -> Triple(
                    ConnKind.RECONNECTING, "重连中（第 ${st.attempt} 次，${st.delayMs / 1000}s 后）", st.lastError,
                )
                is ConnectionSupervisor.State.Failed -> Triple(ConnKind.FAILED, st.message, st.message)
            }
            update(rt.cfg.id, kind, text, err)
        }
    }

    private suspend fun persistLoop(rt: Runtime, sup: ConnectionSupervisor) {
        sup.reconciler.state.collect { rs ->
            if (rs.fetchedAtMs <= 0 && rs.panes.isEmpty()) return@collect
            runCatching {
                boards.replace(
                    hostId = rt.cfg.id,
                    session = rt.cfg.session,
                    panes = rs.panes,
                    version = rs.version,
                    protocol = rs.protocol,
                    fetchedAtMs = rs.fetchedAtMs,
                    stale = rs.stale,
                    lastError = rs.lastError,
                )
            }
            android.util.Log.i(
                "Monitor",
                "refresh host=${rt.cfg.id} panes=${rs.panes.size} stale=${rs.stale} " +
                    rs.panes.joinToString(",") { "${it.paneId}:${it.status.wire}:${it.completionSeq}" },
            )
            // 状态变化 → 通知层
            val prev = rt.lastStatuses
            val now = rs.panes.associate { it.paneId to it.status }
            rs.panes.forEach { p ->
                // 1) 进入 blocked：采样能捕捉到（blocked 会持续等待）
                val old = prev[p.paneId]
                if (p.status == AgentStatus.BLOCKED && old != AgentStatus.BLOCKED) {
                    _statusChanges.tryEmit(StatusChange(rt.cfg.id, rt.cfg.label, p, old ?: AgentStatus.UNKNOWN, p.status))
                }
                // 2) 完成：用 completion_seq（单调）判断，避免 done→working→done 被采样漏掉
                val done = p.completionSeq
                if (done != null) {
                    val last = rt.lastCompletion[p.paneId]
                    android.util.Log.i("Monitor", "completion pane=${p.paneId} seq=$done last=${rt.lastCompletion[p.paneId]}")
                    if (last != null && done > last) {
                        _statusChanges.tryEmit(StatusChange(rt.cfg.id, rt.cfg.label, p, AgentStatus.WORKING, AgentStatus.DONE))
                    }
                }
            }
            rt.lastStatuses = now
            rt.lastCompletion = rs.panes.mapNotNull { p -> p.completionSeq?.let { p.paneId to it } }.toMap()
            update(
                rt.cfg.id,
                panes = rs.panes.size,
                dataStale = rs.stale,
                dataAtMs = rs.fetchedAtMs,
                dataVersion = rs.version,
            )
        }
    }

    private fun update(
        id: String,
        kind: ConnKind? = null,
        text: String? = null,
        error: String? = null,
        panes: Int? = null,
        dataStale: Boolean? = null,
        dataAtMs: Long? = null,
        dataVersion: String? = null,
    ) {
        _hosts.value = _hosts.value.map { hs ->
            if (hs.cfg.id != id) hs else hs.copy(
                connKind = kind ?: hs.connKind,
                connText = text ?: hs.connText,
                lastError = if (error != null) error else hs.lastError,
                paneCount = panes ?: hs.paneCount,
                dataStale = dataStale ?: hs.dataStale,
                dataAtMs = dataAtMs ?: hs.dataAtMs,
                dataVersion = dataVersion ?: hs.dataVersion,
            )
        }
    }

    private fun kindOf(sup: ConnectionSupervisor): ConnKind = when (sup.state.value) {
        is ConnectionSupervisor.State.Idle -> ConnKind.IDLE
        is ConnectionSupervisor.State.Connecting -> ConnKind.CONNECTING
        is ConnectionSupervisor.State.AwaitingHostKey -> ConnKind.AWAITING_HOST_KEY
        is ConnectionSupervisor.State.Connected -> ConnKind.CONNECTED
        is ConnectionSupervisor.State.Stale -> ConnKind.STALE
        is ConnectionSupervisor.State.Reconnecting -> ConnKind.RECONNECTING
        is ConnectionSupervisor.State.Failed -> ConnKind.FAILED
    }

    fun shutdown() {
        stopTicker()
        runtimes.values.forEach { rt ->
            rt.persistJob?.cancel()
            rt.supervisor?.stop()
        }
        runtimes.clear()
        started = false
    }
}
