package herdrspike

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 离线入口。真正的在线测量（Spike 1/2）需要一个已批准的隔离主机，
 * 见 ../README.md 与仓库根目录的 NEXT-STEPS.md。
 */
fun main() {
    println("herdr spike jvm harness")
    println("offline self-check:")
    println(Ndjson.request("p1", "ping"))
    val parsed = Ndjson.parse("""{"id":"p1","result":{"type":"pong","version":"0.9.3","protocol":22,"capabilities":{"live_handoff":true}},"unknown_field":1}""")
    println("parsed = $parsed")
    check(parsed is Ndjson.Response.Ok)
    val err = Ndjson.parse("""{"id":"x","error":{"code":"future_code","message":"m"}}""")
    check(err is Ndjson.Response.Err && err.code == "future_code")
    println("error code passthrough ok")
}
