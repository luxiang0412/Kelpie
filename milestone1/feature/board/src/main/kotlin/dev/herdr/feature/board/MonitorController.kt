package dev.herdr.feature.board

import androidx.compose.runtime.staticCompositionLocalOf

/** 由 :app 注入：控制前台监控服务（feature 模块不依赖 app 模块）。 */
interface MonitorController {
    fun start()
    fun stop()
}

val LocalMonitorController = staticCompositionLocalOf<MonitorController?> { null }
