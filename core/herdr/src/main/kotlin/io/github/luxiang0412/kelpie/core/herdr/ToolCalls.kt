package io.github.luxiang0412.kelpie.core.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Claude 的 AskUserQuestion：一道选择题（header 是终端里那个小标签）。 */
data class AskQuestion(
    val question: String,
    val header: String? = null,
    val multiSelect: Boolean = false,
    val options: List<Option> = emptyList(),
) {
    data class Option(val label: String, val description: String? = null) {
        /** Claude 习惯把推荐项写成 `xxx (Recommended)` / `xxx（推荐）`。 */
        val recommended: Boolean get() = RECOMMENDED.containsMatchIn(label)
        /** 去掉推荐后缀的标签（推荐另外用样式表示）。 */
        val plainLabel: String get() = RECOMMENDED.replace(label, "").trim().ifEmpty { label }
    }

    private companion object {
        val RECOMMENDED = Regex("""\s*[（(]\s*(Recommended|推荐)\s*[)）]\s*$""", RegexOption.IGNORE_CASE)
    }
}

/**
 * 工具调用参数 → 能看懂的一行摘要，以及 AskUserQuestion 的结构化内容。
 *
 * 解析器**在截断之前**调用这里：以前是先把 input 转成字符串截到 300 字符，
 * 截完已经不是合法 JSON，下游再解析只能退回显示原始 JSON。
 */
object ToolCalls {

    const val ASK_USER_QUESTION = "AskUserQuestion"
    private const val MAX = 300

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 一行摘要；拿不到任何可读字段时退回（截断的）原始 JSON。 */
    fun summary(name: String, input: JsonElement?): String {
        val o = input as? JsonObject ?: return cap(input?.let(::raw).orEmpty())
        val s = when (name) {
            ASK_USER_QUESTION -> questions(o).let { qs ->
                val first = qs.firstOrNull()?.question
                when {
                    first == null -> null
                    qs.size > 1 -> t("$first（共 ${qs.size} 个问题）", "$first (${qs.size} questions)")
                    else -> first
                }
            }
            "TodoWrite" -> (o["todos"] as? JsonArray)?.let { t("更新待办（${it.size} 项）", "Update todos (${it.size})") }
            "Task", "Agent" -> o.str("description")
            else -> null
        } ?: KEYS.firstNotNullOfOrNull { o.str(it) }
            ?: o.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf(String::isNotBlank) }
            ?: raw(o)
        return cap(s)
    }

    /**
     * 卡片标题：Claude 的 Bash / PowerShell 调用自带一句 `description`（例如 “Check PR CI status”），
     * 比命令本身好认得多（命令常常以 `cd /很长的路径 &&` 开头，截断后一排卡片看起来都一样）。
     */
    fun title(name: String, input: JsonElement?): String? {
        val o = input as? JsonObject ?: return null
        return when (name) {
            "Bash", "PowerShell", "Monitor" -> o.str("description")?.trim()?.takeIf { it.isNotEmpty() }?.let(::cap)
            else -> null
        }
    }

    private val CD_PREFIX = Regex("""^\s*cd\s+("[^"]+"|'[^']+'|\S+)\s*(&&|;)\s*""")

    /**
     * 去掉开头的 `cd <会话工作目录> &&`：它对看的人没有信息量，只会把真正的命令挤到截断之外。
     * 只去掉指向 [cwd] 本身的（`~` 按 [home] 展开）；cd 到别处的保留。
     */
    fun stripCwd(command: String, cwd: String?, home: String? = null): String {
        if (cwd.isNullOrBlank()) return command
        val m = CD_PREFIX.find(command) ?: return command
        var dir = m.groupValues[1].trim('"', '\'')
        if (home != null && (dir == "~" || dir.startsWith("~/"))) dir = home + dir.removePrefix("~")
        val norm = { p: String -> p.trimEnd('/').ifEmpty { "/" } }
        return if (norm(dir) == norm(cwd)) command.substring(m.range.last + 1).ifBlank { command } else command
    }

    /** 优先级：命令 > 文件 > 搜索词（Grep/Glob 也带 path，要排在它前面） > 路径 > 地址 > 描述。 */
    private val KEYS = listOf("command", "cmd", "file_path", "notebook_path", "pattern", "path", "url", "query", "description", "prompt")

    /** AskUserQuestion 的 `input.questions`；不是这个工具或格式不对时为空。 */
    fun questions(name: String, input: JsonElement?): List<AskQuestion> =
        if (name == ASK_USER_QUESTION) (input as? JsonObject)?.let(::questions).orEmpty() else emptyList()

    private fun questions(o: JsonObject): List<AskQuestion> =
        (o["questions"] as? JsonArray).orEmpty().mapNotNull { q ->
            val qo = q as? JsonObject ?: return@mapNotNull null
            val text = qo.str("question") ?: return@mapNotNull null
            AskQuestion(
                question = text,
                header = qo.str("header"),
                multiSelect = qo.str("multiSelect") == "true",
                options = (qo["options"] as? JsonArray).orEmpty().mapNotNull { op ->
                    val oo = op as? JsonObject ?: return@mapNotNull null
                    AskQuestion.Option(oo.str("label") ?: return@mapNotNull null, oo.str("description"))
                },
            )
        }

    /** 工具结果行的 `toolUseResult.answers`（问题原文 → 选中的标签 / 输入的文字）。 */
    fun answers(toolUseResult: JsonElement?): Map<String, String>? {
        val a = (toolUseResult as? JsonObject)?.get("answers") as? JsonObject ?: return null
        return a.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }.toMap()
    }

    // ---- 本地缓存（Room 一列 TEXT）里的编码：只存展示要用的结构，不存原始 input ----

    fun encode(questions: List<AskQuestion>, answers: Map<String, String>?): String? {
        if (questions.isEmpty() && answers == null) return null
        return buildJsonObject {
            if (questions.isNotEmpty()) put("q", buildJsonArray {
                questions.forEach { q ->
                    add(buildJsonObject {
                        put("question", q.question)
                        q.header?.let { put("header", it) }
                        if (q.multiSelect) put("multiSelect", true)
                        put("options", buildJsonArray {
                            q.options.forEach { op -> add(buildJsonObject { put("label", op.label); op.description?.let { put("description", it) } }) }
                        })
                    })
                }
            })
            if (answers != null) put("a", buildJsonObject { answers.forEach { (k, v) -> put(k, v) } })
        }.toString()
    }

    fun decode(s: String?): Pair<List<AskQuestion>, Map<String, String>?> {
        if (s.isNullOrBlank()) return emptyList<AskQuestion>() to null
        val o = runCatching { json.parseToJsonElement(s) as? JsonObject }.getOrNull() ?: return emptyList<AskQuestion>() to null
        val qs = o["q"]?.let { questions(buildJsonObject { put("questions", it) }) }.orEmpty()
        return qs to answers(buildJsonObject { o["a"]?.let { put("answers", it) } })
    }

    private fun raw(e: JsonElement): String = e.toString()

    private fun cap(s: String): String = if (s.length <= MAX) s else s.take(MAX) + "…"

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
}
