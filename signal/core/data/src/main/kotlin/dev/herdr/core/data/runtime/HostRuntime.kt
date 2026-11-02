package dev.herdr.core.data.runtime

import dev.herdr.core.data.HostRepository
import dev.herdr.core.data.attention.HostReach
import dev.herdr.core.herdr.ConnectionSupervisor
import dev.herdr.core.herdr.HerdrApi
import dev.herdr.core.herdr.SessionReconciler
import dev.herdr.core.ssh.HostKeyUnknownException
import dev.herdr.core.ssh.SshConnection
import dev.herdr.core.ssh.SshConnector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** 连接的用户可见描述（设置页 / 横幅）。 */
data class HostLink(
    val reach: HostReach,
    /** 一句话状态：“已连接 · herdr 0.9.5”“重连中（第 3 次）”。 */
    val text: String,
    /** 失败类别（决定横幅文案与可操作项）。 */
    val failure: ConnectionSupervisor.Kind? = null,
    val version: String = "",
)

data class HostKeyPrompt(val hostId: String, val label: String, val host: String, val fingerprint: String, val algorithm: String = "")

/**
 * 一台主机的运行时：一个 [ConnectionSupervisor]（连接代次 / 退避 / 订阅 / Stale，沿用已实测的实现），
 * 加上主机指纹确认。只管“连接”，不管首页怎么排。
 */
internal class HostRuntime(
    val cfg: HostRepository.HostConfig,
    private val hosts: HostRepository,
    private val connector: SshConnector,
    private val scope: CoroutineScope,
    private val onHostKey: (HostKeyPrompt?) -> Unit,
) {
    private val supervisor = ConnectionSupervisor(
        scope = scope,
        connector = connector,
        configProvider = { hosts.sshConfig(cfg.id) },
        onUnknownHostKey = ::awaitHostKey,
    )
    private var pendingKey: CompletableDeferred<Boolean>? = null
    private var started = false

    val state: StateFlow<ConnectionSupervisor.State> get() = supervisor.state

    val reconciler: SessionReconciler? get() = if (started) supervisor.reconciler else null

    fun start() {
        if (started) return
        started = true
        supervisor.start()
    }

    fun stop() {
        if (!started) return
        supervisor.stop()
        started = false
    }

    fun reconnect() = if (started) supervisor.reconnectNow() else start()

    fun refresh() { reconciler?.markDirty() }

    fun connection(): SshConnection? = supervisor.currentConnection()

    fun api(): HerdrApi? = connection()?.let { HerdrApi(it, cfg.herdrBin, cfg.session) }

    fun answerHostKey(accept: Boolean) {
        pendingKey?.complete(accept)
        pendingKey = null
        onHostKey(null)
    }

    /** 未知指纹：阻塞连接线程，等用户在界面上明确确认（绝不 TOFU）。90 秒无回应视为拒绝。 */
    private fun awaitHostKey(ex: HostKeyUnknownException): Boolean {
        val d = CompletableDeferred<Boolean>()
        pendingKey = d
        onHostKey(HostKeyPrompt(cfg.id, cfg.label, cfg.host, ex.fingerprint, ex.id.algorithm))
        return runBlocking { withTimeoutOrNull(90_000) { d.await() } ?: false }.also {
            if (pendingKey === d) { pendingKey = null; onHostKey(null) }
        }
    }

    companion object {
        fun link(st: ConnectionSupervisor.State, hasKey: Boolean): HostLink = when (st) {
            is ConnectionSupervisor.State.Idle ->
                if (hasKey) HostLink(HostReach.Connecting, "未连接") else HostLink(HostReach.Unreachable, "未导入私钥", ConnectionSupervisor.Kind.KEY)
            is ConnectionSupervisor.State.Connecting -> HostLink(HostReach.Connecting, if (st.attempt <= 1) "连接中…" else "连接中（第 ${st.attempt} 次）")
            is ConnectionSupervisor.State.AwaitingHostKey -> HostLink(HostReach.Connecting, "等待确认主机指纹", ConnectionSupervisor.Kind.HOST_KEY_UNKNOWN)
            is ConnectionSupervisor.State.Connected -> HostLink(HostReach.Online, "已连接 · herdr ${st.version}", version = st.version)
            // Stale：连接还在，但超过 25 秒没刷新成功。对用户来说数据已不可信。
            is ConnectionSupervisor.State.Stale -> HostLink(HostReach.Unreachable, "数据可能过期：${st.reason}", ConnectionSupervisor.Kind.NETWORK)
            is ConnectionSupervisor.State.Reconnecting -> HostLink(
                HostReach.Unreachable,
                listOfNotNull(reason(st.lastError), "${st.delayMs / 1000} 秒后第 ${st.attempt} 次重连").joinToString(" · "),
                ConnectionSupervisor.Kind.NETWORK,
            )
            is ConnectionSupervisor.State.Failed -> HostLink(HostReach.Unreachable, reason(st.message) ?: st.message, st.kind)
        }

        /** 把底层异常文字翻成一句人话；认不出来就截短原文。 */
        fun reason(raw: String?): String? {
            val r = raw?.lowercase() ?: return null
            return when {
                "refused" in r -> "连接被拒绝"
                "timed out" in r || "timeout" in r -> "连接超时"
                "unreachable" in r || "no route" in r -> "网络不通"
                "auth" in r -> "认证失败（私钥不被接受）"
                "unknownhost" in r || "unable to resolve" in r -> "找不到这个地址"
                "host key" in r || "mismatch" in r -> "主机指纹不一致"
                else -> raw.take(40)
            }
        }
    }
}
