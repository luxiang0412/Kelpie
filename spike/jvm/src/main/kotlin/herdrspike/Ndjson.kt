package herdrspike

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * herdr socket API 的 NDJSON 编解码。
 * - 请求：一行一个 JSON。
 * - 响应：忽略未知字段；错误码按自由字符串处理（schema 里的 ErrorBody.code 没有枚举）。
 */
object Ndjson {

    fun request(id: String, method: String, params: JsonObject = buildJsonObject { }): String =
        buildJsonObject {
            put("id", id)
            put("method", method)
            putJsonObject("params") { params.forEach { (k, v) -> put(k, v) } }
        }.toString() + "\n"

    sealed interface Response {
        data class Ok(val id: String?, val result: JsonObject) : Response
        data class Err(val id: String?, val code: String, val message: String) : Response
        data class Unparseable(val raw: String) : Response
    }

    fun parse(line: String): Response = runCatching {
        val obj = Json.parseToJsonElement(line).jsonObject
        val id = obj["id"]?.jsonPrimitive?.content
        val err = obj["error"]?.jsonObject
        if (err != null) {
            Response.Err(id, err["code"]?.jsonPrimitive?.content ?: "unknown", err["message"]?.jsonPrimitive?.content ?: "")
        } else {
            Response.Ok(id, obj["result"]?.jsonObject ?: buildJsonObject { })
        }
    }.getOrElse { Response.Unparseable(line) }
}
