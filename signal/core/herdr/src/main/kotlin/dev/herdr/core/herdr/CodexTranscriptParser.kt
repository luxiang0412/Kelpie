package dev.herdr.core.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Codex CLI 会话（rollout）JSONL 解析（只读，尽力而为）。
 *
 * 位置：`~/.codex/sessions/YYYY/MM/DD/rollout-<本地时间>-<uuid>.jsonl`。
 * 依据：2026-11-03 在 .2 上统计 3746 个真实文件（codex-cli 0.1xx ~ 0.166）。
 *
 * 每行 `{timestamp, type, payload}`。只读 `type=response_item`（OpenAI Responses 的条目，各版本都稳定）：
 *  - `message`：role ∈ user / assistant / developer；content 分片 `input_text` / `output_text`。
 *    user 里混着 Codex 自己注入的上下文（`<environment_context>`、`# AGENTS.md instructions for …` 等），要过滤。
 *  - `reasoning`：`summary[]` 有文字才显示；正文是加密的。
 *  - `function_call {name, arguments(JSON 字符串), call_id}` / `function_call_output {call_id, output}`
 *    常见：exec_command {cmd}、shell_command {command}、shell {command:[…]}、write_stdin、update_plan
 *  - `custom_tool_call {name, input, call_id}` / `custom_tool_call_output {call_id, output}`
 *    0.16x 的 `exec`：input 是一段 JS（`tools.exec_command({cmd:"…"})`）；`apply_patch`：input 是补丁文本
 *  - `web_search_call {action:{query}}`
 * `event_msg` 里的 user_message / agent_message / item_completed 和上面重复，不读。
 *
 * 不认识的一律跳过，绝不抛异常。
 */
object CodexTranscriptParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 注入的上下文：整条是一个 `<tag …>…</tag>` 块，或 AGENTS.md 说明。 */
    private val INJECTED_TAG = Regex("""^<([A-Za-z_][\w-]*)(\s[^>]*)?>[\s\S]*</\1>\s*$""")
    private val INJECTED_PREFIX = listOf("# AGENTS.md instructions for", "<environment_context>", "<user_instructions>", "<permissions instructions>")

    fun parse(lines: List<String>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        val hidden = HashSet<String>() // 被隐藏的调用（轮询 stdin 等），它们的输出也不显示
        lines.forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val obj = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: return@forEach
            if (obj.str("type") != "response_item") return@forEach
            val p = obj["payload"] as? JsonObject ?: return@forEach
            val ts = obj.str("timestamp")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            val id = p.str("id")
            when (p.str("type")) {
                "message" -> {
                    val text = textOf(p["content"])
                    if (text.isBlank()) return@forEach
                    when (p.str("role")) {
                        "user" -> if (!isInjected(text)) out += ChatMessage(ChatMessage.Kind.USER, text, timestamp = ts, id = id)
                        "assistant" -> out += ChatMessage(ChatMessage.Kind.ASSISTANT, text, timestamp = ts, id = id)
                        else -> Unit
                    }
                }
                "reasoning" -> {
                    val text = (p["summary"] as? JsonArray)?.filterIsInstance<JsonObject>()?.mapNotNull { it.str("text") }?.joinToString("\n").orEmpty()
                    if (text.isNotBlank()) out += ChatMessage(ChatMessage.Kind.THINKING, text, timestamp = ts, id = id)
                }
                "function_call", "custom_tool_call" -> {
                    val callId = p.str("call_id")
                    val name = p.str("name") ?: "tool"
                    val raw = p.str("arguments") ?: p.str("input") ?: ""
                    val call = describe(name, raw)
                    if (call == null) { callId?.let { hidden += it }; return@forEach }
                    out += ChatMessage(ChatMessage.Kind.TOOL_CALL, call.second, toolName = call.first, timestamp = ts, toolCallId = callId, id = id)
                }
                "function_call_output", "custom_tool_call_output" -> {
                    val callId = p.str("call_id")
                    if (callId != null && callId in hidden) return@forEach
                    val text = unwrapOutput(textOf(p["output"]))
                    if (text.isNotBlank()) out += ChatMessage(ChatMessage.Kind.TOOL_RESULT, text, timestamp = ts, toolCallId = callId, id = id)
                }
                "web_search_call" -> {
                    val q = (p["action"] as? JsonObject)?.str("query").orEmpty()
                    out += ChatMessage(ChatMessage.Kind.TOOL_CALL, q, toolName = "web_search", timestamp = ts, id = id)
                }
                else -> Unit
            }
        }
        return out
    }

    fun isInjected(text: String): Boolean {
        val s = text.trim()
        return INJECTED_PREFIX.any { s.startsWith(it) } || INJECTED_TAG.matches(s)
    }

    /**
     * 工具调用 → (显示名, 一行摘要)。返回 null 表示不显示（例如空的 write_stdin 轮询）。
     * 命令类统一显示成命令本身，和 Claude 的 Bash 卡片一致。
     */
    fun describe(name: String, raw: String): Pair<String, String>? {
        val args = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        return when (name) {
            "exec_command" -> "exec_command" to (args?.str("cmd") ?: raw)
            "shell_command" -> "shell" to (args?.str("command") ?: raw)
            "shell", "local_shell" -> "shell" to (shellArray(args) ?: raw)
            "exec" -> "exec_command" to (jsCommand(raw) ?: raw.trim())
            "apply_patch" -> "apply_patch" to patchSummary(args?.str("input") ?: raw)
            "write_stdin" -> {
                val chars = args?.str("chars").orEmpty()
                if (chars.isEmpty()) null else "write_stdin" to chars
            }
            "update_plan" -> "update_plan" to (args?.str("explanation") ?: t("更新计划", "Update plan"))
            else -> name to raw
        }
    }

    /** `{"command":["bash","-lc","ls"]}` → `ls`。 */
    private fun shellArray(args: JsonObject?): String? {
        val parts = (args?.get("command") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: return null
        return if (parts.size >= 3 && parts[parts.size - 2].startsWith("-") && parts[parts.size - 2].contains('c')) parts.last()
        else parts.joinToString(" ")
    }

    private val JS_CMD = Regex("""exec_command\(\s*\{\s*"?cmd"?\s*:\s*("(?:\\.|[^"\\])*")""")

    /** 0.16x 的 exec 工具：`text(await tools.exec_command({cmd:"ls -la", …}))` → `ls -la`。 */
    fun jsCommand(js: String): String? {
        val lit = JS_CMD.find(js)?.groupValues?.get(1) ?: return null
        return runCatching { (json.parseToJsonElement(lit) as JsonPrimitive).content }.getOrNull()
    }

    private val PATCH_FILE = Regex("""^\*\*\* (Add|Update|Delete) File: (.+)$""", RegexOption.MULTILINE)

    /** 补丁 → `编辑 a.kt, b.kt`。 */
    fun patchSummary(patch: String): String {
        val files = PATCH_FILE.findAll(patch).map { it.groupValues[2].trim().substringAfterLast('/') }.distinct().toList()
        if (files.isEmpty()) return t("修改文件", "Edit files")
        val shown = files.take(3).joinToString(", ") + if (files.size > 3) t(" 等 ${files.size} 个", " +${files.size - 3}") else ""
        return t("编辑 $shown", "Edit $shown")
    }

    /** apply_patch 的输出是 `{"output":"…","metadata":{…}}`。 */
    private fun unwrapOutput(s: String): String {
        val t = s.trim()
        if (!t.startsWith("{\"output\"")) return s
        return runCatching { (json.parseToJsonElement(t) as JsonObject).str("output") }.getOrNull() ?: s
    }

    private fun textOf(e: kotlinx.serialization.json.JsonElement?): String = when (e) {
        is JsonPrimitive -> e.content
        is JsonArray -> e.filterIsInstance<JsonObject>().mapNotNull { it.str("text") }.joinToString("\n")
        else -> ""
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
}
