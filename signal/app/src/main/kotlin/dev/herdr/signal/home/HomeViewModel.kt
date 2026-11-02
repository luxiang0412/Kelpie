package dev.herdr.signal.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.herdr.core.data.attention.AgentItem
import dev.herdr.core.data.attention.AgentKey
import dev.herdr.core.data.attention.HomeState
import dev.herdr.core.data.attention.HostReach
import dev.herdr.core.data.runtime.ApprovalItem
import dev.herdr.core.data.runtime.BlockedPreview
import dev.herdr.core.data.runtime.HostKeyPrompt
import dev.herdr.core.data.runtime.HostLink
import dev.herdr.core.data.runtime.Receipt
import dev.herdr.core.data.runtime.SendRecord
import dev.herdr.signal.container
import dev.herdr.signal.platform.NetworkMonitor
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
    val entries: List<AttentionEntry>,
    val unknownSends: List<Pair<SendRecord, String>>,
    val banner: Banner?,
    val onlineHosts: Int,
    val nowMs: Long,
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
        c.fleet.links,
        network.online,
        ticker,
    ) { (home, previews, approvals), sends, links, phoneOnline, _ ->
        build(home, previews, approvals, sends, links, phoneOnline, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), build(c.fleet.home.value, emptyMap(), emptyList(), emptyMap(), emptyMap(), true, System.currentTimeMillis()))

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
            // 结构化请求能对上 pane 的，替换掉那张“问题卡”；对不上的单独成卡
            val byPane = approvals.filter { it.request.paneId != null }
                .associateBy { AgentKey(it.hostId, it.request.paneId!!) }
            val entries = ArrayList<AttentionEntry>()
            home.needsYou.forEach { item ->
                val a = byPane[item.key]
                entries += if (a != null) AttentionEntry.Permission(a, item) else AttentionEntry.Question(item, previews[item.key])
            }
            approvals.filter { a -> a.request.paneId == null || home.needsYou.none { it.key == AgentKey(a.hostId, a.request.paneId!!) } }
                .forEach { entries += AttentionEntry.Permission(it, null) }

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
                state = home,
                entries = entries,
                unknownSends = unknown,
                banner = banner,
                onlineHosts = home.hosts.count { it.reach == HostReach.Online },
                nowMs = now,
            )
        }
    }
}
