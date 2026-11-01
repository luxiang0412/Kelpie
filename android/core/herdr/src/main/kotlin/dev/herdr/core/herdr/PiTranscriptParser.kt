package dev.herdr.core.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一条只读对话消息（从 agent 的 JSONL 里解析出来）。 */
data class ChatMessage(
    val kind: Kind,
    val text: String,
    val toolName: String? = null,
    val timestamp: Long? = null,
    /** toolCall 的稳定 id（用于 ToolCall↔ToolResult 配对；拿不到时为 null）。 */
    val toolCallId: String? = null,
    /** JSONL 事件行的稳定 id（pi 的 id 字段；拿不到时为 null）。 */
    val id: String? = null,
    /** 父事件 id（pi 的 parentId；用于分支重建）。 */
    val parentId: String? = null,
) {
    enum class Kind { USER, ASSISTANT, THINKING, TOOL_CALL, TOOL_RESULT, SYSTEM }
}

/**
 * pi 的会话 JSONL 解析（只读，尽力而为）。
 *
 * 结构（实测 2026-10-31）：
 *   顶层 { id, parentId, timestamp, type, message }
 *   type ∈ session / model_change / thinking_level_change / message / ...
 *   message.role ∈ system / user / assistant / toolResult
 *   message.content 是分片数组：[{type:"text"|"thinking"|"toolCall"|...}]
 *
 * 解析原则：不认识的行/分片一律忽略，绝不抛异常（格式会变，D19）。
 */
object PiTranscriptParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(lines: List<String>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        lines.forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val obj = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: return@forEach
            val type = obj.str("type") ?: return@forEach
            if (type != "message") return@forEach
            val msg = obj["message"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return@forEach
            val role = msg.str("role") ?: return@forEach
            val ts = obj.str("timestamp")?.toLongOrNull()
            val eventId = obj.str("id")
            val parentEventId = obj.str("parentId")
            when (role) {
                "system" -> Unit // 系统提示不展示
                "user" -> {
                    val text = textOf(msg)
                    if (text.isNotBlank()) out += ChatMessage(ChatMessage.Kind.USER, text, timestamp = ts, id = eventId, parentId = parentEventId)
                }
                "assistant" -> {
                    val parts = partsOf(msg)
                    if (parts.isEmpty()) {
                        val text = textOf(msg)
                        if (text.isNotBlank()) out += ChatMessage(ChatMessage.Kind.ASSISTANT, text, timestamp = ts)
                    }
                    parts.forEach { part ->
                        when (part.str("type")) {
                            "text" -> part.str("text")?.takeIf { it.isNotBlank() }?.let {
                                out += ChatMessage(ChatMessage.Kind.ASSISTANT, it, timestamp = ts, id = eventId, parentId = parentEventId)
                            }
                            "thinking" -> (part.str("thinking") ?: part.str("text"))?.takeIf { it.isNotBlank() }?.let {
                                out += ChatMessage(ChatMessage.Kind.THINKING, it, timestamp = ts, id = eventId, parentId = parentEventId)
                            }
                            "toolCall", "tool_use", "tool_call" -> {
                                val name = part.str("name") ?: part.str("toolName") ?: "tool"
                                val args = part["arguments"] ?: part["input"]
                                out += ChatMessage(
                                    ChatMessage.Kind.TOOL_CALL,
                                    args?.let { preview(it.toString()) } ?: "",
                                    toolName = name,
                                    timestamp = ts,
                                    toolCallId = part.str("id") ?: part.str("toolCallId") ?: part.str("tool_use_id"),
                                    id = eventId,
                                    parentId = parentEventId,
                                )
                            }
                            else -> Unit
                        }
                    }
                }
                "toolResult", "tool_result" -> {
                    val text = textOf(msg)
                    if (text.isNotBlank()) {
                        val id = msg.str("toolCallId")
                            ?: msg.str("tool_use_id")
                            ?: partsOf(msg).firstNotNullOfOrNull { it.str("toolCallId") ?: it.str("tool_use_id") }
                        out += ChatMessage(
                            ChatMessage.Kind.TOOL_RESULT,
                            text,
                            timestamp = ts,
                            toolCallId = id,
                            toolName = msg.str("toolName"),
                            id = eventId,
                            parentId = parentEventId,
                        )
                    }
                }
                else -> Unit
            }
        }
        return out
    }

    private fun textOf(msg: JsonObject): String {
        val c = msg["content"] ?: return ""
        return when (c) {
            is JsonPrimitive -> c.content
            is JsonArray -> c.filterIsInstance<JsonObject>()
                .filter { it.str("type") == "text" }
                .mapNotNull { it.str("text") }
                .joinToString("\n")
            else -> ""
        }
    }

    private fun partsOf(msg: JsonObject): List<JsonObject> {
        val c = msg["content"] as? JsonArray ?: return emptyList()
        return c.filterIsInstance<JsonObject>()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    private fun preview(s: String, max: Int = 300): String =
        if (s.length <= max) s else s.take(max) + "…"
}
