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
