package io.github.luxiang0412.kelpie.core.data.runtime

import io.github.luxiang0412.kelpie.core.herdr.t

/** 从手机新开一个 agent：可选的种类、目录校验、herdr 里的名字。纯 Kotlin，便于测试。 */
object NewAgent {
    /** herdr `agent.start --kind` 的值 → 界面上的名字。只放 App 能读对话的三种。 */
    val KINDS: List<Pair<String, String>> = listOf("claude" to "Claude Code", "codex" to "Codex", "pi" to "Pi")

    /**
     * “跳过权限确认”对应的启动参数；null = 这种 agent 没有这个开关（Pi 本来就不问）。
     * 只放这两个固定参数，不让用户随便拼命令行。
     */
    fun skipPermissionsArgs(kind: String): List<String>? = when (kind) {
        "claude" -> listOf("--dangerously-skip-permissions")
        "codex" -> listOf("--dangerously-bypass-approvals-and-sandbox")
        else -> null
    }

    fun args(kind: String, skipPermissions: Boolean): List<String> =
        if (skipPermissions) skipPermissionsArgs(kind).orEmpty() else emptyList()

    /** 用户填的目录：绝对路径或 `~/…`；去掉结尾的 `/`。 */
    fun normalizeDir(raw: String): Result<String> = runCatching {
        val s = raw.trim()
        require(s.isNotEmpty()) { t("先填一个目录", "Enter a directory first") }
        require(s == "~" || s.startsWith("/") || s.startsWith("~/")) {
            t("目录要写绝对路径，或者以 ~/ 开头", "Use an absolute path or one starting with ~/")
        }
        require(s.length <= 1024 && s.none { it.isISOControl() }) { t("目录不对", "Invalid directory") }
        s.trimEnd('/').ifEmpty { "/" }
    }

    /** 展开 `~`；[home] 只在需要时调用。 */
    fun resolve(dir: String, home: () -> String): String = when {
        dir == "~" -> home().trimEnd('/')
        dir.startsWith("~/") -> home().trimEnd('/') + dir.substring(1)
        else -> dir
    }

    /** 目录名，做 workspace 标签。 */
    fun label(dir: String): String = dir.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

    /**
     * herdr 里 agent 的名字，同一个会话里必须唯一：`<目录名>-<kind>-<4 位>`。
     * 只留 `[A-Za-z0-9._-]`，中文等其他字符换成 `-`。
     */
    fun name(dir: String, kind: String, nowMs: Long): String {
        val base = label(dir).replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').take(32).ifEmpty { "agent" }
        val suffix = (nowMs % 1_679_616).toString(36).padStart(4, '0') // 36^4
        return "$base-$kind-$suffix"
    }

    /** 主机上已有 pane 的工作目录（[cwds] 按新到旧排），去重；有 agent 的排前面。 */
    fun recentDirs(cwds: List<Pair<String, Boolean>>, limit: Int = 8): List<String> =
        cwds.filter { it.first.startsWith("/") && !it.first.endsWith("(deleted)") }
            .sortedByDescending { it.second }.map { it.first }.distinct().take(limit)
}
