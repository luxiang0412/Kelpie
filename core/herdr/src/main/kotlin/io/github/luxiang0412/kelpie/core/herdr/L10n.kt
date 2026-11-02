package io.github.luxiang0412.kelpie.core.herdr

import java.util.Locale

/**
 * 中英双语文案。两种语言写在一起（`t("中文", "English")`），纯 Kotlin 模块和 UI 共用一套做法。
 * 语言跟随系统：App 在 Application / Activity 创建时用当前配置调用 [update]；
 * 系统语言变化会重建 Activity，界面随之重新取值。中文以外的语言一律显示英文。
 */
object L10n {
    @Volatile var zh: Boolean = Locale.getDefault().language == "zh"
        private set

    fun update(locale: Locale) { zh = locale.language == "zh" }
}

fun t(zh: String, en: String): String = if (L10n.zh) zh else en

/** 英文单复数：`plural(n, "agent")` → "1 agent" / "3 agents"。中文不需要。 */
fun plural(n: Int, one: String, other: String = one + "s"): String = "$n " + if (n == 1) one else other
