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

            val blocks = (msg["content"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
            if (blocks.isEmpty()) {
                // content 可能是纯字符串
                val text = (msg["content"] as? JsonPrimitive)?.content
                if (!text.isNullOrBlank() && role == "user") {
                    out += ChatMessage(ChatMessage.Kind.USER, text, timestamp = ts, id = uuid, parentId = null)
                }
                return@forEach
            }

            blocks.forEach { b ->
                val bt = (b["type"] as? JsonPrimitive)?.content ?: return@forEach
                when (bt) {
                    "text" -> {
                        val text = (b["text"] as? JsonPrimitive)?.content ?: return@forEach
                        if (text.isBlank()) return@forEach
                        val kind = if (role == "assistant") ChatMessage.Kind.ASSISTANT else ChatMessage.Kind.USER
                        out += ChatMessage(kind, text, timestamp = ts, id = uuid, parentId = null)
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
                        val text = textOfResult(b)
                        if (text.isBlank()) return@forEach
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
