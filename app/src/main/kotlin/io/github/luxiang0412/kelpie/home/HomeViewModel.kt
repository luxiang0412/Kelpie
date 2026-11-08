package io.github.luxiang0412.kelpie.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.attention.HomeState
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.core.data.runtime.ApprovalItem
import io.github.luxiang0412.kelpie.core.data.runtime.BlockedPreview
import io.github.luxiang0412.kelpie.core.data.runtime.HostKeyPrompt
import io.github.luxiang0412.kelpie.core.data.runtime.HostLink
import io.github.luxiang0412.kelpie.core.data.runtime.Receipt
import io.github.luxiang0412.kelpie.core.data.runtime.SendRecord
import io.github.luxiang0412.kelpie.container
import io.github.luxiang0412.kelpie.platform.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** 一张“等你处理”的卡：问题（读 transcript）或结构化权限请求（文件 IPC）。 */
sealed interface AttentionEntry {
    val stableKey: String

    data class Question(val item: AgentItem, val preview: BlockedPreview?) : AttentionEntry {
        override val stableKey get() = "q:${item.key.hostId}/${item.key.paneId}"
    }

    data class Permission(val approval: ApprovalItem, val item: AgentItem?) : AttentionEntry {
        override val stableKey get() = "p:${approval.hostId}/${approval.request.requestId}"
    }
}

/** 顶部横幅（规范 5.15）：手机没网 ≠ 连不上主机。 */
sealed interface Banner {
    data class PhoneOffline(val lastAtMs: Long) : Banner
    data class HostsUnreachable(val labels: List<String>, val detail: String, val lastAtMs: Long) : Banner
}

data class HomeUi(
    val state: HomeState,
    /** 等你处理的 agent 数（不是卡片数）。 */
    val waitingAgents: Int,
    val entries: List<AttentionEntry>,
    val unknownSends: List<Pair<SendRecord, String>>,
    val banner: Banner?,
    val onlineHosts: Int,
    val nowMs: Long,
    /** 停用的主机数（不在 [HomeState.hosts] 里）。全部停用时首页不能说“还没有主机”。 */
    val disabledHosts: Int = 0,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val network = NetworkMonitor.get(app)

    private val ticker = flow {
        while (true) { emit(System.currentTimeMillis()); delay(30_000) }
    }

    val hostKeyPrompt: StateFlow<HostKeyPrompt?> = c.fleet.hostKeyPrompt

    val ui: StateFlow<HomeUi> = combine(
        combine(c.fleet.home, c.previews.map, c.approvals.items, ::Triple),
        c.sender.records,
        combine(c.fleet.links, c.hosts.hosts, ::Pair),
        network.online,
        ticker,
    ) { (home, previews, approvals), sends, (links, hosts), phoneOnline, _ ->
        build(home, previews, approvals, sends, links, phoneOnline, System.currentTimeMillis())
            .copy(disabledHosts = hosts.count { !it.enabled })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(c.fleet.home.value, emptyMap(), emptyList(), emptyMap(), emptyMap(), true, System.currentTimeMillis()))

    /** 最近一次从手机新开 agent。 */
    val launch = c.sender.launch

    /** 新开 agent。返回拦下的原因（null = 已开始，看 [launch]）。 */
    fun startAgent(hostId: String, dir: String, kind: String): String? = c.sender.launch(hostId, dir, kind)?.message

    fun dismissLaunch() = c.sender.dismissLaunch()

    fun answerHostKey(accept: Boolean) = c.fleet.answerHostKey(accept)
    fun retry() = c.fleet.reconnectAll()
    fun dismissSend(key: AgentKey) = c.sender.dismiss(key)

    companion object {
        fun build(
            home: HomeState,
            previews: Map<AgentKey, BlockedPreview>,
            approvals: List<ApprovalItem>,
            sends: Map<AgentKey, SendRecord>,
            links: Map<String, HostLink>,
            phoneOnline: Boolean,
            now: Long,
        ): HomeUi {
            // 结构化请求能对上 pane 的：替换掉那张“问题卡”，并从“工作中 / 完成 / 空闲”里拿走这个 agent（它现在在等你批准）。
            // 一个 pane 可以同时有多个请求（并发工具调用），每个请求一张卡。对不上 pane 的单独成卡。
            val all = home.needsYou + home.working + home.recentlyDone + home.quiet
            val itemByKey = all.associateBy { it.key }
            val byPane = approvals.filter { it.request.paneId != null }.groupBy { AgentKey(it.hostId, it.request.paneId!!) }
            val entries = ArrayList<AttentionEntry>()
            home.needsYou.forEach { item ->
                val list = byPane[item.key]
                if (list.isNullOrEmpty()) entries += AttentionEntry.Question(item, previews[item.key])
                else list.forEach { entries += AttentionEntry.Permission(it, item) }
            }
            val needsKeys = home.needsYou.map { it.key }.toSet()
            byPane.filterKeys { it !in needsKeys }.forEach { (k, list) -> list.forEach { entries += AttentionEntry.Permission(it, itemByKey[k]) } }
            approvals.filter { it.request.paneId == null }.forEach { entries += AttentionEntry.Permission(it, null) }
            val approvalKeys = byPane.keys
            val shown = home.copy(
                working = home.working.filterNot { it.key in approvalKeys },
                recentlyDone = home.recentlyDone.filterNot { it.key in approvalKeys },
                quiet = home.quiet.filterNot { it.key in approvalKeys },
            )
            // 标题按 agent 计数：同一个 agent 的多个请求只算一次
            val waitingAgents = (needsKeys + approvalKeys).size + approvals.count { it.request.paneId == null }

            val titles = (home.needsYou + home.working + home.recentlyDone + home.quiet).associate { it.key to it.title }
            val unknown = sends.values.filter { it.receipt is Receipt.Unknown }
                .map { it to (titles[it.key] ?: it.key.paneId) }

            val lastAt = home.hosts.maxOfOrNull { it.fetchedAtMs } ?: 0
            val unreachable = home.hosts.filter { it.reach == HostReach.Unreachable }
            val banner = when {
                home.hosts.isEmpty() -> null
                !phoneOnline -> Banner.PhoneOffline(lastAt)
                unreachable.isNotEmpty() -> Banner.HostsUnreachable(
                    unreachable.map { it.label },
                    links[unreachable.first().hostId]?.text.orEmpty(),
                    unreachable.maxOf { it.fetchedAtMs },
                )
                else -> null
            }
            return HomeUi(
                state = shown,
                waitingAgents = waitingAgents,
                entries = entries,
                unknownSends = unknown,
                banner = banner,
                onlineHosts = home.hosts.count { it.reach == HostReach.Online },
                nowMs = now,
            )
        }
    }
}
