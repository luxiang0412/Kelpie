package io.github.luxiang0412.kelpie

import android.app.Application

/** release 包里什么都不做：没有任何调试导入入口。 */
object DebugHooks {
    @Suppress("UNUSED_PARAMETER")
    fun onAppStart(app: Application, container: AppContainer) = Unit
}
