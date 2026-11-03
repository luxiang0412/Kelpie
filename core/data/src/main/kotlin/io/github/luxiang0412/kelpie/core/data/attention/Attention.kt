package io.github.luxiang0412.kelpie.core.data.attention

import io.github.luxiang0412.kelpie.core.herdr.t

import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane

/** 某台主机的 pane 在列表里的唯一身份。 */
data class AgentKey(val hostId: String, val paneId: String)

/**
 * 我们自己观测到的状态时间线（herdr 快照里没有时间戳，只有序号）。
 *
 * - [sinceMs]：第一次看到当前状态的时刻；
 * - [sinceIsLowerBound]：true 表示“打开 App 时它已经是这个状态”，真实开始时间更早，
 *   界面要写“至少 N 分钟”，不能写成“N 分钟”（规范 5.4）；
 * - [finishedAtMs]：最近一次观测到 completion_seq 增长的时刻（“刚完成”分组用）。
 */
data class Observation(
    val status: AgentStatus,
    val sinceMs: Long,
    val sinceIsLowerBound: Boolean,
    val completionSeq: Long?,
    val finishedAtMs: Long?,
)

/**
 * 按快照更新观测时间线。纯函数式：输入旧表与新快照，返回新表与本轮发生的转换。
 * 不在快照里的 pane 会被移除（pane 已关闭）。
 */
class AttentionTracker {

    data class Transition(val key: AgentKey, val from: AgentStatus?, val to: AgentStatus, val completed: Boolean)

    private val table = HashMap<AgentKey, Observation>()

    @Synchronized fun observation(key: AgentKey): Observation? = table[key]

    @Synchronized fun snapshot(): Map<AgentKey, Observation> = HashMap(table)

    /**
     * 一台主机的一次权威快照。主机离线时不要调用（保留最后看到的状态）。
     */
    @Synchronized fun update(hostId: String, panes: List<Pane>, nowMs: Long): List<Transition> {
        val out = ArrayList<Transition>()
        val seen = HashSet<AgentKey>()
        for (p in panes) {
            val key = AgentKey(hostId, p.paneId)
            seen += key
            val old = table[key]
            val seqIncreased = old?.completionSeq != null && p.completionSeq != null && p.completionSeq!! > old.completionSeq!!
            val finishedAt = if (seqIncreased) nowMs else old?.finishedAtMs
            // herdr 在 agent 工作期间不给 completion_seq（null），干完才回来；沿用上次的值，否则“完成”永远比不出来
            val seq = p.completionSeq ?: old?.completionSeq
            val next = when {
                old == null -> Observation(p.status, nowMs, sinceIsLowerBound = true, seq, null)
                old.status != p.status -> Observation(p.status, nowMs, sinceIsLowerBound = false, seq, finishedAt)
                else -> old.copy(completionSeq = seq, finishedAtMs = finishedAt)
            }
            table[key] = next
            if (old == null || old.status != p.status || seqIncreased) {
                out += Transition(key, old?.status, p.status, completed = seqIncreased)
            }
        }
        table.keys.removeAll { it.hostId == hostId && it !in seen }
        return out
    }

    @Synchronized fun forgetHost(hostId: String) {
        table.keys.removeAll { it.hostId == hostId }
    }
}

/** 主机连接的“对用户可见”的三态（规范 5.15 OfflineBanner）。 */
enum class HostReach { Online, Connecting, Unreachable }

/** 首页需要的单台主机输入。 */
data class HostInput(
    val hostId: String,
    val label: String,
    val reach: HostReach,
    /** 最近一次权威快照（离线时就是“最后看到”）。 */
    val panes: List<Pane>,
    /** 快照获取时刻；0 = 从未成功。 */
    val fetchedAtMs: Long,
)

/** 首页一行 / 一张卡。 */
data class AgentItem(
    val key: AgentKey,
    val hostLabel: String,
    val pane: Pane,
    val status: AgentStatus,
    val sinceMs: Long?,
    val sinceIsLowerBound: Boolean,
    val finishedAtMs: Long?,
    /** true = 主机离不可达，这是“最后看到”的状态。 */
    val lastSeen: Boolean,
) {
    /** 列表标题（规范 5.5）：终端标题去掉 agent 前缀；标题只是 agent 名时改用 cwd 末段。 */
    val title: String get() = Titles.of(pane)

    val agentName: String get() = AgentNames.display(pane.agent)
}

object Titles {
    private val ATTENTION_PREFIX = Regex("""^\[[^\]]{1,2}]\s*Action Required\s*\|\s*""", RegexOption.IGNORE_CASE) // 标记在 [!] / [.] 间闪烁
    private val prefixes = listOf("π - ", "pi - ", "✳ ", "* ", "⠂ ", "⠐ ")

    fun of(p: Pane): String {
        var t = p.title?.trim().orEmpty()
        // Codex 等你时把终端标题改成 “[!] Action Required | <原标题>”（[!] 和 [.] 交替）：状态已经由卡片表达，去掉
        t = ATTENTION_PREFIX.replace(t, "")
        prefixes.firstOrNull { t.startsWith(it) }?.let { t = t.removePrefix(it).trim() }
        val generic = t.isBlank() || t.equals(p.agent, ignoreCase = true) ||
            t.equals(AgentNames.display(p.agent), ignoreCase = true)
        if (!generic) return t
        return p.cwd?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: AgentNames.display(p.agent)
    }
}

object AgentNames {
    fun display(agent: String?): String = when (agent?.lowercase()) {
        "claude", "claude-code", "claude_code" -> "Claude Code"
        "pi" -> "Pi"
        "codex" -> "Codex"
        null, "" -> t("终端", "Terminal")
        else -> agent.replaceFirstChar { it.uppercase() }
    }
}

data class HomeState(
    val needsYou: List<AgentItem>,
    val working: List<AgentItem>,
    val recentlyDone: List<AgentItem>,
    val quiet: List<AgentItem>,
    val hosts: List<HostInput>,
    /** 还没有任何主机配置。 */
    val noHosts: Boolean,
    /** 至少一台主机从未拿到过快照，且没有任何数据可显示。 */
    val loading: Boolean,
) {
    val unreachableHosts: List<HostInput> get() = hosts.filter { it.reach == HostReach.Unreachable }
    val allClear: Boolean get() = !noHosts && !loading && needsYou.isEmpty()
    val total: Int get() = needsYou.size + working.size + recentlyDone.size + quiet.size
}

/**
 * 首页派生（P1 的核心规则，规范 5.1–5.6）：
 *
 * 1. **等你处理**：blocked，按等待时间从长到短（等最久的在最上）；
 * 2. **工作中**：working，按开始时间从早到晚；
 * 3. **刚完成**：done，或 [recentWindowMs] 内 completion_seq 增长过的 idle，按完成时间从新到旧；
 * 4. **其他**：剩下的 idle / unknown，默认折叠。
 *
 * 不可达主机的 pane 仍然显示（标记为 lastSeen），不会凭空消失——但不再算作“等你处理”的确定项：
 * 它们排在可达主机的 blocked 之后。
 */
object HomeDerivation {

    const val DEFAULT_RECENT_WINDOW_MS = 30 * 60_000L

    fun derive(
        hosts: List<HostInput>,
        observations: Map<AgentKey, Observation>,
        nowMs: Long,
        recentWindowMs: Long = DEFAULT_RECENT_WINDOW_MS,
    ): HomeState {
        val items = hosts.flatMap { h ->
            h.panes.filter { it.agent != null || it.status != AgentStatus.UNKNOWN }.map { p ->
                val key = AgentKey(h.hostId, p.paneId)
                val o = observations[key]?.takeIf { it.status == p.status }
                AgentItem(
                    key = key,
                    hostLabel = h.label,
                    pane = p,
                    status = p.status,
                    sinceMs = o?.sinceMs,
                    sinceIsLowerBound = o?.sinceIsLowerBound ?: true,
                    finishedAtMs = observations[key]?.finishedAtMs,
                    lastSeen = h.reach != HostReach.Online,
                )
            }
        }
        fun recentlyFinished(i: AgentItem) = i.finishedAtMs != null && nowMs - i.finishedAtMs <= recentWindowMs

        val needs = items.filter { it.status == AgentStatus.BLOCKED }
            .sortedWith(compareBy<AgentItem>({ it.lastSeen }, { it.sinceMs ?: Long.MAX_VALUE }, { it.key.paneId }))
        val working = items.filter { it.status == AgentStatus.WORKING }
            .sortedWith(compareBy<AgentItem>({ it.lastSeen }, { it.sinceMs ?: Long.MAX_VALUE }, { it.key.paneId }))
        val done = items.filter {
            it.status == AgentStatus.DONE || (it.status == AgentStatus.IDLE && recentlyFinished(it))
        }.sortedWith(compareByDescending<AgentItem> { it.finishedAtMs ?: it.sinceMs ?: 0 }.thenBy { it.key.paneId })
        val taken = (needs + working + done).map { it.key }.toSet()
        val quiet = items.filter { it.key !in taken }.sortedWith(compareBy({ it.hostLabel }, { it.title }))

        return HomeState(
            needsYou = needs,
            working = working,
            recentlyDone = done,
            quiet = quiet,
            hosts = hosts,
            noHosts = hosts.isEmpty(),
            loading = hosts.isNotEmpty() && items.isEmpty() && hosts.any { it.fetchedAtMs == 0L && it.reach != HostReach.Unreachable },
        )
    }
}

/** “等了多久”的文案（规范 5.4）。 */
object Durations {
    val JUST_NOTICED = { t("刚发现", "just noticed") }

    fun waited(sinceMs: Long?, lowerBound: Boolean, nowMs: Long): String {
        if (sinceMs == null) return t("刚刚", "just now")
        val m = ((nowMs - sinceMs) / 60_000).coerceAtLeast(0)
        val core = when {
            // 只知道下限且不到 1 分钟：其实是“刚发现”，不知道已经等了多久
            m < 1 -> return if (lowerBound) JUST_NOTICED() else t("刚刚", "just now")
            m < 60 -> t("$m 分钟", "$m min")
            m < 24 * 60 -> t("${m / 60} 小时", "${m / 60} h") + if (m % 60 >= 10) t(" ${m % 60} 分", " ${m % 60} min") else ""
            else -> t("${m / (24 * 60)} 天", "${m / (24 * 60)} d")
        }
        return if (lowerBound) t("至少 $core", "at least $core") else core
    }

    fun ago(atMs: Long?, nowMs: Long): String {
        if (atMs == null) return ""
        val m = ((nowMs - atMs) / 60_000).coerceAtLeast(0)
        return when {
            m < 1 -> t("刚刚", "just now")
            m < 60 -> t("$m 分钟前", "$m min ago")
            m < 24 * 60 -> t("${m / 60} 小时前", "${m / 60} h ago")
            else -> t("${m / (24 * 60)} 天前", "${m / (24 * 60)} d ago")
        }
    }
}
