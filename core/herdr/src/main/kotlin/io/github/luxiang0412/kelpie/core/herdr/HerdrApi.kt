package io.github.luxiang0412.kelpie.core.herdr

import io.github.luxiang0412.kelpie.core.ssh.SshConnection
import io.github.luxiang0412.kelpie.core.ssh.SshFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * herdr socket API 客户端。
 *
 * 说明：
 * - 每个请求一个 exec channel（`remote-api-bridge` 一个连接只处理一个请求，RESULTS §9.3）；
 * - 订阅是长连接，占 1 个通道；
 * - `ping.capabilities` 只是特性开关，不用于判断方法可用性（D8）。
 */
class HerdrApi(
    private val conn: SshConnection,
    private val herdrBin: String,
    private val session: String,
) {
    private val bridge: String = "${shellQuote(herdrBin)} --session ${shellQuote(session)} remote-api-bridge"

    suspend fun ping(timeoutMs: Long = 15_000): PingInfo {
        val line = request("ping", buildJsonObject { }, timeoutMs)
        return HerdrCodec.parsePing(requireResult(line))
    }

    suspend fun snapshot(timeoutMs: Long = 20_000): SessionSnapshot {
        val line = request("session.snapshot", buildJsonObject { }, timeoutMs)
        return HerdrCodec.parseSnapshot(requireResult(line), System.currentTimeMillis())
    }

    /** 给 agent 发一条消息（写操作，无幂等键）。 */
    suspend fun prompt(target: String, text: String, timeoutMs: Long = 20_000) {
        val params = buildJsonObject {
            put("target", target)
            put("text", text)
        }
        requireResult(request("agent.prompt", params, timeoutMs))
    }

    /** 往 pane 发文本（写操作）。 */
    suspend fun sendText(paneId: String, text: String, timeoutMs: Long = 20_000) {
        val params = buildJsonObject {
            put("pane_id", paneId)
            put("text", text)
        }
        requireResult(request("pane.send_text", params, timeoutMs))
    }

    /** 往 pane 发按键（写操作）。 */
    suspend fun sendKeys(paneId: String, keys: List<String>, timeoutMs: Long = 20_000) {
        val params = buildJsonObject {
            put("pane_id", paneId)
            put("keys", JsonArray(keys.map { JsonPrimitive(it) }))
        }
        requireResult(request("pane.send_keys", params, timeoutMs))
    }

    /** 新建的 workspace 和它的第一个 pane。 */
    data class NewWorkspace(val workspaceId: String, val paneId: String)

    /** 在 [cwd] 新建一个 workspace（写操作），不抢电脑上的焦点。 */
    suspend fun createWorkspace(cwd: String, label: String?, timeoutMs: Long = 20_000): NewWorkspace {
        val params = buildJsonObject {
            put("cwd", cwd)
            label?.let { put("label", it) }
            put("focus", false)
        }
        val r = requireResult(request("workspace.create", params, timeoutMs))
        val ws = r["workspace"]?.jsonObject?.get("workspace_id")?.jsonPrimitive?.content
        val pane = r["root_pane"]?.jsonObject?.get("pane_id")?.jsonPrimitive?.content
        if (ws == null || pane == null) throw SshFailure.Protocol("unexpected: workspace.create returned no root pane")
        return NewWorkspace(ws, pane)
    }

    /**
     * 在一个停在 shell 提示符的 pane 里启动 agent（写操作）。
     * herdr 等到 agent 可以输入才返回；[name] 在一个会话里必须唯一（否则 `agent_name_taken`）。
     */
    suspend fun startAgent(paneId: String, kind: String, name: String, readyTimeoutMs: Long = 45_000) {
        val params = buildJsonObject {
            put("name", name)
            put("kind", kind)
            put("pane_id", paneId)
            put("timeout_ms", JsonPrimitive(readyTimeoutMs))
        }
        requireResult(request("agent.start", params, readyTimeoutMs + 15_000))
    }

    /** 关闭 workspace（写操作）。只用来收拾自己刚建、agent 没启动起来的空 workspace。 */
    suspend fun closeWorkspace(workspaceId: String, timeoutMs: Long = 15_000) {
        requireResult(request("workspace.close", buildJsonObject { put("workspace_id", workspaceId) }, timeoutMs))
    }

    /** 读终端输出（只读）。source: visible / recent / recent_unwrapped / detection */
    suspend fun readPane(paneId: String, source: String = "recent", lines: Int = 80, timeoutMs: Long = 15_000): String {
        val params = buildJsonObject {
            put("pane_id", paneId)
            put("source", source)
            put("lines", JsonPrimitive(lines))
        }
        val result = requireResult(request("pane.read", params, timeoutMs))
        return result["read"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
    }

    /** 订阅事件。流被取消或连接断开时关闭通道。 */
    fun subscribe(subscriptions: List<JsonObject>, id: String = "sub"): Flow<HerdrEvent> = callbackFlow {
        val body = buildJsonObject { put("subscriptions", JsonArray(subscriptions)) }
        val payload = HerdrCodec.request(id, "events.subscribe", body).trim()
        val handle = try {
            conn.openStream(bridge, payload)
        } catch (e: Exception) {
            close(SshFailure.Protocol("subscribe failed: ${e.message}", e))
            return@callbackFlow
        }
        val job = launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val line = handle.readLine() ?: break
                    if (line.isBlank()) continue
                    when (val resp = HerdrCodec.parseResponse(line)) {
                        is HerdrCodec.Response.Err ->
                            trySend(HerdrEvent("__error:${resp.code}", null, resp.message))
                        is HerdrCodec.Response.Ok -> {
                            if (line.contains("\"subscription_started\"")) {
                                trySend(HerdrEvent("__started", null, line))
                            } else {
                                HerdrCodec.parseEvent(line)?.let { trySend(it) }
                            }
                        }
                        else -> Unit
                    }
                }
            } finally {
                runCatching { handle.close() }
                close()
            }
        }
        awaitClose {
            runCatching { handle.close() }
            job.cancel()
        }
    }

    private suspend fun request(method: String, params: JsonObject, timeoutMs: Long): String {
        val id = "m${System.nanoTime()}"
        val payload = HerdrCodec.request(id, method, params).trim()
        val res = conn.execOnce(bridge, payload, timeoutMs)
        if (res.line.isBlank()) throw SshFailure.Protocol("empty response for $method")
        return res.line
    }

    private fun requireResult(line: String): JsonObject = when (val r = HerdrCodec.parseResponse(line)) {
        is HerdrCodec.Response.Ok -> r.result
        is HerdrCodec.Response.Err -> throw SshFailure.Protocol("${r.code}: ${r.message}")
        is HerdrCodec.Response.Garbage -> throw SshFailure.Protocol("unparseable: ${r.raw.take(80)}")
    }

    private fun shellQuote(s: String): String {
        require(s.matches(Regex("[A-Za-z0-9._/\\-]{1,200}"))) { "refusing to quote: $s" }
        return "'" + s.replace("'", "'\\''") + "'"
    }
}
