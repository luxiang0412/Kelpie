package dev.herdr.core.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * herdr NDJSON 编解码。
 * - 请求一行一个 JSON；
 * - 响应忽略未知字段；错误码是自由字符串（D8），未知码不崩。
 */
object HerdrCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun request(id: String, method: String, params: JsonObject = buildJsonObject { }) : String =
        buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString() + "\n"

    sealed interface Response {
        data class Ok(val id: String?, val result: JsonObject) : Response
        data class Err(val id: String?, val code: String, val message: String) : Response
        data class Garbage(val raw: String) : Response
    }

    fun parseResponse(line: String): Response = runCatching {
        val obj = json.parseToJsonElement(line).jsonObject
        val id = obj["id"]?.jsonPrimitive?.content
        val err = obj["error"]?.jsonObject
        if (err != null) {
            Response.Err(
                id,
                err["code"]?.jsonPrimitive?.content ?: "unknown",
                err["message"]?.jsonPrimitive?.content ?: "",
            )
        } else {
            Response.Ok(id, obj["result"]?.jsonObject ?: buildJsonObject { })
        }
    }.getOrElse { Response.Garbage(line) }

    fun parseEvent(line: String): HerdrEvent? = runCatching {
        val obj = json.parseToJsonElement(line).jsonObject
        val type = obj["event"]?.jsonPrimitive?.content ?: return null
        val data = obj["data"]?.jsonObject
        val paneId = data?.get("pane_id")?.jsonPrimitive?.content
            ?: data?.get("pane")?.jsonObject?.get("pane_id")?.jsonPrimitive?.content
        HerdrEvent(type, paneId, line)
    }.getOrNull()

    fun parseSnapshot(result: JsonObject, nowMs: Long): SessionSnapshot {
        val snap = result["snapshot"]?.jsonObject ?: result

        // agent_session 在 agents[] 里（不是 panes[]）——按 pane_id 合并
        data class AgentMeta(val sessionPath: String?, val sessionId: String?, val completionSeq: Long?, val stateSeq: Long?)

        val agentMeta: Map<String, AgentMeta> = (snap["agents"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { el -> runCatching { el.jsonObject }.getOrNull() }
            ?.mapNotNull { a ->
                val pid = a["pane_id"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
                val sess = a["agent_session"]?.jsonObject
                val kind = sess?.get("kind")?.jsonPrimitive?.contentOrNullSafe()
                val value = sess?.get("value")?.jsonPrimitive?.contentOrNullSafe()
                pid to AgentMeta(
                    sessionPath = if (kind == "path") value else null,
                    sessionId = if (kind != null && kind != "path") value else null,
                    completionSeq = a["completion_seq"]?.jsonPrimitive?.longOrNull,
                    stateSeq = a["state_change_seq"]?.jsonPrimitive?.longOrNull,
                )
            }?.toMap() ?: emptyMap()

        val sessionByPane: Map<String, String> = (snap["agents"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { el -> runCatching { el.jsonObject }.getOrNull() }
            ?.mapNotNull { a ->
                val pid = a["pane_id"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
                val sess = a["agent_session"]?.jsonObject ?: return@mapNotNull null
                val kind = sess["kind"]?.jsonPrimitive?.contentOrNullSafe()
                val value = sess["value"]?.jsonPrimitive?.contentOrNullSafe()
                if (kind == "path" && value != null) pid to value else null
            }?.toMap() ?: emptyMap()
        val panes = snap["panes"]?.let { it as? kotlinx.serialization.json.JsonArray }?.mapNotNull { el ->
            val p = el.jsonObject
            val paneId = p["pane_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Pane(
                paneId = paneId,
                terminalId = p["terminal_id"]?.jsonPrimitive?.content,
                workspaceId = p["workspace_id"]?.jsonPrimitive?.content ?: "",
                tabId = p["tab_id"]?.jsonPrimitive?.content ?: "",
                agent = p["agent"]?.jsonPrimitive?.contentOrNullSafe(),
                status = AgentStatus.fromWire(p["agent_status"]?.jsonPrimitive?.content),
                title = p["terminal_title_stripped"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: p["terminal_title"]?.jsonPrimitive?.contentOrNullSafe(),
                cwd = p["foreground_cwd"]?.jsonPrimitive?.contentOrNullSafe()
                    ?: p["cwd"]?.jsonPrimitive?.contentOrNullSafe(),
                revision = p["revision"]?.jsonPrimitive?.longOrNull ?: 0L,
                completionSeq = agentMeta[paneId]?.completionSeq,
                stateChangeSeq = agentMeta[paneId]?.stateSeq,
                pendingApprovals = (p["tokens"] as? JsonObject)
                    ?.filterKeys { it.startsWith("sa_") }
                    ?.values?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNullSafe() }.getOrNull() }
                    ?.toSet() ?: emptySet(),
                agentSessionPath = sessionByPane[paneId]
                    ?: p["agent_session"]?.jsonObject?.let { sess ->
                        val kind = sess["kind"]?.jsonPrimitive?.contentOrNullSafe()
                        val value = sess["value"]?.jsonPrimitive?.contentOrNullSafe()
                        if (kind == "path") value else null
                    },
                agentSessionId = agentMeta[paneId]?.sessionId,
            )
        } ?: emptyList()

        return SessionSnapshot(
            version = snap["version"]?.jsonPrimitive?.content ?: "?",
            protocol = snap["protocol"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            panes = panes,
            fetchedAtMs = nowMs,
        )
    }

    fun parsePing(result: JsonObject): PingInfo {
        val caps = result["capabilities"]?.jsonObject
        return PingInfo(
            version = result["version"]?.jsonPrimitive?.content ?: "?",
            protocol = result["protocol"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            capabilities = caps?.entries?.associate { (k, v) ->
                k to (runCatching { v.jsonPrimitive.content }.getOrDefault("?"))
            } ?: emptyMap(),
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}
