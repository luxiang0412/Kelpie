package dev.herdr.core.herdr

/** herdr 的 agent 状态。注意：完成是 `done`，不是 `idle`（RESULTS §9.10）。 */
enum class AgentStatus(val wire: String) {
    WORKING("working"), BLOCKED("blocked"), DONE("done"), IDLE("idle"), UNKNOWN("unknown");

    companion object {
        fun fromWire(v: String?): AgentStatus = when (v?.lowercase()) {
            "working" -> WORKING
            "blocked" -> BLOCKED
            "done" -> DONE
            "idle" -> IDLE
            else -> UNKNOWN
        }
    }
}

data class Pane(
    val paneId: String,
    val terminalId: String?,
    val workspaceId: String,
    val tabId: String,
    val agent: String?,
    val status: AgentStatus,
    val title: String?,
    val cwd: String?,
    val revision: Long,
    /** herdr 上报的 agent 会话文件路径（`agent_session.kind == "path"` 时），用于只读 transcript。 */
    val agentSessionPath: String? = null,
    /**
     * `agent_session.kind == "id"` 时上报的会话 id（Claude 走这条路径，没有文件路径）。
     * 用于按 cwd 定位会话文件后做 sessionId 对账（方案 C）。
     */
    val agentSessionId: String? = null,
    /** herdr 的单调序号：完成一次 turn 就 +1（用于“完成”提醒，避免采样漏掉 done→working→done）。 */
    val completionSeq: Long? = null,
    val stateChangeSeq: Long? = null,
    /**
     * 审批 hook（Herdr Signal 的 signal_approve.py）挂在 pane 上的待批准请求 id：
     * pane `tokens` 里以 `sa_` 开头的键的值。用来立刻发现新请求，并判断目录里的请求是否已经失效。
     */
    val pendingApprovals: Set<String> = emptySet(),
)

data class SessionSnapshot(
    val version: String,
    val protocol: Int,
    val panes: List<Pane>,
    val fetchedAtMs: Long,
)

data class PingInfo(
    val version: String,
    val protocol: Int,
    val capabilities: Map<String, String>,
)

data class HerdrEvent(
    val type: String,
    val paneId: String?,
    val rawJson: String,
)
