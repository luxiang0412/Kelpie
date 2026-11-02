package dev.herdr.feature.board

import androidx.compose.ui.graphics.Color
import dev.herdr.core.herdr.AgentStatus

/** 状态色：等你是橙、工作中是蓝、完成是绿、空闲是灰、未知是黄。 */
object StatusColors {
    fun of(status: AgentStatus): Color = when (status) {
        AgentStatus.BLOCKED -> Color(0xFFE8590C)
        AgentStatus.WORKING -> Color(0xFF1C7ED6)
        AgentStatus.DONE -> Color(0xFF2F9E44)
        AgentStatus.IDLE -> Color(0xFF868E96)
        AgentStatus.UNKNOWN -> Color(0xFFF08C00)
    }
}
