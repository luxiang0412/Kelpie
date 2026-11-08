package io.github.luxiang0412.kelpie.core.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Claude Code 会话 JSONL 解析（只读，尽力而为）。
 *
 * 结构（依据 2026-11-01 在 `~/.claude/projects/<slug>/<file>.jsonl` 实测，2050 行真实样本）：
 *   顶层 `type` ∈ user / assistant / system / mode / permission-mode / file-history-snapshot / api-request …
 *   事件行还有 `uuid` / `parentUuid` / `timestamp` / `isSidechain`（子代理）
 *   `message.role` ∈ user / assistant / system
 *   `message.content` 是分片数组，分片 type ∈ text / thinking / tool_use / tool_result / tool_addition
 *   - `tool_use`：{ id, name, input }
 *   - `tool_result`：{ tool_use_id, content, is_error? }（出现在 **user** 角色的行里）
 *
 * 与 pi 的差异（因此需要单独解析器，不能复用 [PiTranscriptParser]）：
 *   - 结果通过 `tool_use_id` 关联（pi 用 `toolCallId`）
 *   - 工具结果行挂在 user 角色下
 *   - 存在大量非消息事件（mode/api-request/…），必须忽略
 *   - user 角色里还有 Claude Code 自己写的东西，不是用户说的话（2026-11-08 实测）：
 *     - `origin.kind == "task-notification"`：后台任务 / Monitor 的事件，正文是 `<task-notification>` XML
 *       → 一行说明（[ChatMessage.Kind.NOTICE]），不再显示成用户气泡；
 *     - `isMeta: true`：图片尺寸说明、`<local-command-caveat>`、粘贴图片的路径等 → 跳过
 *   - tool_result 可能没有文字（ToolSearch 的 `tool_reference`、没有输出的命令）→ 照样给结果，
 *     否则工具卡片会一直显示“运行中”
 *
 * 解析原则：不认识的类型一律跳过，绝不抛异常。
 */
object ClaudeTranscriptParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(lines: List<String>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        lines.forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val obj = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: return@forEach
            val type = (obj["type"] as? JsonPrimitive)?.content ?: return@forEach
            // 只处理对话事件；mode / api-request / file-history-* 等一律跳过
            if (type != "user" && type != "assistant" && type != "system") return@forEach
            // 子代理（sidechain）不进入主对话
            if ((obj["isSidechain"] as? JsonPrimitive)?.content == "true") return@forEach

            val msg = obj["message"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return@forEach
            val role = (msg["role"] as? JsonPrimitive)?.content ?: type
            val ts = (obj["timestamp"] as? JsonPrimitive)?.content?.let { parseIsoMillis(it) }
            val uuid = (obj["uuid"] as? JsonPrimitive)?.content
            val parentUuid = (obj["parentUuid"] as? JsonPrimitive)?.content?.takeIf { it != "null" }

            val isMeta = (obj["isMeta"] as? JsonPrimitive)?.content == "true"
            val origin = (obj["origin"] as? JsonObject)?.get("kind")?.let { (it as? JsonPrimitive)?.content }
            val blocks = (msg["content"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

            /** user 角色的一段文字 → 用户气泡 / 后台任务说明 / 跳过。 */
            fun userText(text: String) {
                if (text.isBlank() || isMeta) return
                if (origin == "task-notification" || text.trimStart().startsWith("<task-notification>")) {
                    out += ChatMessage(ChatMessage.Kind.NOTICE, taskNotice(text), timestamp = ts, id = uuid, parentId = null)
                } else {
                    out += ChatMessage(ChatMessage.Kind.USER, unwrapPasted(text), timestamp = ts, id = uuid, parentId = null)
                }
            }

            if (blocks.isEmpty()) {
                // content 可能是纯字符串
                val text = (msg["content"] as? JsonPrimitive)?.content
                if (text != null && role == "user") userText(text)
                return@forEach
            }

            blocks.forEach { b ->
                val bt = (b["type"] as? JsonPrimitive)?.content ?: return@forEach
                when (bt) {
                    "text" -> {
                        val text = (b["text"] as? JsonPrimitive)?.content ?: return@forEach
                        if (text.isBlank()) return@forEach
                        if (role == "assistant") out += ChatMessage(ChatMessage.Kind.ASSISTANT, text, timestamp = ts, id = uuid, parentId = null)
                        else userText(text)
                    }

                    "thinking" -> {
                        val text = (b["thinking"] as? JsonPrimitive)?.content
                            ?: (b["text"] as? JsonPrimitive)?.content ?: return@forEach
                        if (text.isBlank()) return@forEach
                        out += ChatMessage(ChatMessage.Kind.THINKING, text, timestamp = ts, id = uuid, parentId = null)
                    }

                    "tool_use" -> {
                        val name = (b["name"] as? JsonPrimitive)?.content ?: "tool"
                        val id = (b["id"] as? JsonPrimitive)?.content
                        val input = b["input"]
                        out += ChatMessage(
                            ChatMessage.Kind.TOOL_CALL,
                            ToolCalls.summary(name, input),
                            toolName = name,
                            questions = ToolCalls.questions(name, input),
                            timestamp = ts,
                            toolCallId = id,
                            id = uuid,
                            // 不输出 parentId：Claude 的 parentUuid 会指向被跳过的非消息事件，
                            // 交给 ConversationBranch 会误删整段历史
                            parentId = null,
                        )
                    }

                    "tool_result" -> {
                        val id = (b["tool_use_id"] as? JsonPrimitive)?.content
                        val text = textOfResult(b).ifBlank { emptyResult(b) }
                        out += ChatMessage(
                            ChatMessage.Kind.TOOL_RESULT,
                            text,
                            // AskUserQuestion 的结构化回答在行级 toolUseResult 里（tool_result 正文只是拼好的一句话）
                            answers = ToolCalls.answers(obj["toolUseResult"]),
                            timestamp = ts,
                            toolCallId = id,
                            id = uuid,
                            // 不输出 parentId：Claude 的 parentUuid 会指向被跳过的非消息事件，
                            // 交给 ConversationBranch 会误删整段历史
                            parentId = null,
                        )
                    }

                    else -> Unit // tool_addition / 未知识别分片：忽略
                }
            }
        }
        return out
    }

    private val PASTED = Regex("""<pasted_content(?:\s+id="[^"]*")?>\s*([\s\S]*?)\s*</pasted_content(?:\s+id="[^"]*")?>""")

    /**
     * 用户粘贴的文本在 transcript 里被包成 `<pasted_content id="…">…</pasted_content id="…">`（2026-11 真机所见）。
     * 只显示里面的内容，去掉标签和标签两侧的空行。
     */
    fun unwrapPasted(text: String): String =
        if (!text.contains("<pasted_content")) text else PASTED.replace(text) { it.groupValues[1] }.trim()

    private val TAG = Regex("""<(summary|event)>([\s\S]*?)</\1>""")

    /**
     * `<task-notification>` → 一行说明：“后台任务 · {summary}”，有 `<event>` 时附在下一行（过长截断）。
     * 其他标签（task-id、output-file、usage、给模型看的提示语）都不显示。
     */
    fun taskNotice(xml: String): String {
        val tags = TAG.findAll(xml).associate { it.groupValues[1] to unescape(it.groupValues[2].trim()) }
        val summary = tags["summary"]?.takeIf { it.isNotBlank() } ?: t("后台任务有新消息", "Background task update")
        val event = tags["event"]?.takeIf { it.isNotBlank() }?.let { if (it.length > 400) it.take(400) + "…" else it }
        val head = t("后台任务 · ", "Background task · ") + summary
        return if (event == null) head else head + "\n" + event
    }

    private fun unescape(s: String): String =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    /** 没有文字的工具结果：ToolSearch 加载工具 → “已加载 X”；图片 → “[图片]”；其他 → “（无输出）”。 */
    private fun emptyResult(b: JsonObject): String {
        val parts = (b["content"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
        val tools = parts.filter { (it["type"] as? JsonPrimitive)?.content == "tool_reference" }
            .mapNotNull { (it["tool_name"] as? JsonPrimitive)?.content }
        return when {
            tools.isNotEmpty() -> t("已加载 ", "Loaded ") + tools.joinToString(", ")
            parts.any { (it["type"] as? JsonPrimitive)?.content == "image" } -> t("[图片]", "[image]")
            else -> t("（无输出）", "(no output)")
        }
    }

    /** tool_result.content 可能是字符串，也可能是 [{type:text,text:...}] 数组。 */
    private fun textOfResult(b: JsonObject): String {
        val c = b["content"] ?: return ""
        return when (c) {
            is JsonPrimitive -> c.content
            is JsonArray -> c.filterIsInstance<JsonObject>()
                .mapNotNull { (it["text"] as? JsonPrimitive)?.content }
                .joinToString("\n")
            else -> ""
        }
    }

    /** ISO-8601 → epoch millis（解析失败返回 null，不阻断展示）。 */
    private fun parseIsoMillis(s: String): Long? = runCatching {
        java.time.Instant.parse(s).toEpochMilli()
    }.getOrNull()
}
