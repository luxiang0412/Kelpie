package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.herdr.t

import io.github.luxiang0412.kelpie.core.data.HostRepository
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.core.herdr.ConnectionSupervisor
import io.github.luxiang0412.kelpie.core.herdr.HerdrApi
import io.github.luxiang0412.kelpie.core.herdr.SessionReconciler
import io.github.luxiang0412.kelpie.core.ssh.HostKeyUnknownException
import io.github.luxiang0412.kelpie.core.ssh.SshConnection
import io.github.luxiang0412.kelpie.core.ssh.SshConnector
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
    /** 失败类别（决定横幅文案与可操作项）。 */
    val failure: ConnectionSupervisor.Kind? = null,
    val version: String = "",
    private val state: ConnectionSupervisor.State? = null,
    private val hasKey: Boolean = true,
) {
    /**
     * 一句话状态：“已连接 · herdr 0.9.5”“重连中（第 3 次）”。
     * 每次读取时按当前语言生成：设置里切换语言后不用等连接状态变化。
     */
    val text: String get() = state?.let { HostRuntime.describe(it, hasKey) } ?: ""
}

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
                if (hasKey) HostLink(HostReach.Connecting, state = st, hasKey = true) else HostLink(HostReach.Unreachable, ConnectionSupervisor.Kind.KEY, state = st, hasKey = false)
            is ConnectionSupervisor.State.Connecting -> HostLink(HostReach.Connecting, state = st, hasKey = hasKey)
            is ConnectionSupervisor.State.AwaitingHostKey -> HostLink(HostReach.Connecting, ConnectionSupervisor.Kind.HOST_KEY_UNKNOWN, state = st, hasKey = hasKey)
            is ConnectionSupervisor.State.Connected -> HostLink(HostReach.Online, version = st.version, state = st, hasKey = hasKey)
            // Stale：连接还在，但超过 25 秒没刷新成功。对用户来说数据已不可信。
            is ConnectionSupervisor.State.Stale -> HostLink(HostReach.Unreachable, ConnectionSupervisor.Kind.NETWORK, state = st, hasKey = hasKey)
            is ConnectionSupervisor.State.Reconnecting -> HostLink(HostReach.Unreachable, ConnectionSupervisor.Kind.NETWORK, state = st, hasKey = hasKey)
            is ConnectionSupervisor.State.Failed -> HostLink(HostReach.Unreachable, st.kind, state = st, hasKey = hasKey)
        }

        /** [link] 的一句话状态，按当前语言。 */
        fun describe(st: ConnectionSupervisor.State, hasKey: Boolean): String = when (st) {
            is ConnectionSupervisor.State.Idle -> if (hasKey) t("未连接", "Not connected") else t("未导入私钥", "No private key imported")
            is ConnectionSupervisor.State.Connecting -> if (st.attempt <= 1) t("连接中…", "Connecting…") else t("连接中（第 ${st.attempt} 次）", "Connecting (attempt ${st.attempt})")
            is ConnectionSupervisor.State.AwaitingHostKey -> t("等待确认主机指纹", "Waiting for fingerprint confirmation")
            is ConnectionSupervisor.State.Connected -> t("已连接 · herdr ${st.version}", "Connected · herdr ${st.version}")
            is ConnectionSupervisor.State.Stale -> t("数据可能过期：${st.reason}", "Data may be stale: ${st.reason}")
            is ConnectionSupervisor.State.Reconnecting ->
                listOfNotNull(reason(st.lastError), t("${st.delayMs / 1000} 秒后第 ${st.attempt} 次重连", "retry ${st.attempt} in ${st.delayMs / 1000}s")).joinToString(" · ")
            is ConnectionSupervisor.State.Failed -> reason(st.message) ?: st.message
        }

        /** 把底层异常文字翻成一句人话；认不出来就截短原文。 */
        fun reason(raw: String?): String? {
            val r = raw?.lowercase() ?: return null
            return when {
                "refused" in r -> t("连接被拒绝", "Connection refused")
                "timed out" in r || "timeout" in r -> t("连接超时", "Connection timed out")
                "unreachable" in r || "no route" in r -> t("网络不通", "Network unreachable")
                "auth" in r -> t("认证失败（私钥不被接受）", "Authentication failed (key rejected)")
                "unknownhost" in r || "unable to resolve" in r -> t("找不到这个地址", "Address not found")
                "host key" in r || "mismatch" in r -> t("主机指纹不一致", "Host fingerprint mismatch")
                else -> raw.take(40)
            }
        }
    }
}
