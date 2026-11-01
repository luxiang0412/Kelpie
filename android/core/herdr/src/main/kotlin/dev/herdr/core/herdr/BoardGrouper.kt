package dev.herdr.core.herdr

/** 看板分组（纯函数，可单测）。顺序即 UI 语义优先级。 */
object BoardGrouper {

    data class Group(val status: AgentStatus, val panes: List<Pane>)

    fun group(panes: List<Pane>): List<Group> {
        val order = listOf(
            AgentStatus.BLOCKED,
            AgentStatus.WORKING,
            AgentStatus.DONE,
            AgentStatus.IDLE,
            AgentStatus.UNKNOWN,
        )
        return order.map { st ->
            Group(st, panes.filter { it.status == st }.sortedBy { it.paneId })
        }.filter { it.panes.isNotEmpty() }
    }

    fun counts(panes: List<Pane>): Map<AgentStatus, Int> =
        AgentStatus.entries.associateWith { st -> panes.count { it.status == st } }
            .filterValues { it > 0 }

    fun title(status: AgentStatus): String = when (status) {
        AgentStatus.BLOCKED -> "等你处理"
        AgentStatus.WORKING -> "工作中"
        AgentStatus.DONE -> "已完成"
        AgentStatus.IDLE -> "空闲"
        AgentStatus.UNKNOWN -> "未知"
    }
}
