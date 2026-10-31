package dev.herdr.core.herdr

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HerdrCodecTest {

    @Test
    fun `request is single line ndjson with method and params`() {
        val line = HerdrCodec.request("p1", "ping", buildJsonObject { })
        assertTrue(line.endsWith("\n"))
        assertEquals(1, line.count { it == '\n' })
        assertTrue(line.contains("\"method\":\"ping\""))
    }

    @Test
    fun `ok response parses even with unknown fields`() {
        val r = HerdrCodec.parseResponse(
            """{"id":"p1","result":{"type":"pong","version":"0.9.5","protocol":22,"capabilities":{"live_handoff":true}},"extra":{"x":1}}""",
        )
        assertTrue(r is HerdrCodec.Response.Ok)
        assertTrue((r as HerdrCodec.Response.Ok).result.containsKey("version"))
    }

    @Test
    fun `error codes are freeform strings and preserved`() {
        val r = HerdrCodec.parseResponse("""{"id":"x","error":{"code":"pane_not_found","message":"pane w9:p9 not found"}}""")
        assertTrue(r is HerdrCodec.Response.Err)
        assertEquals("pane_not_found", (r as HerdrCodec.Response.Err).code)
    }

    @Test
    fun `garbage does not throw`() {
        assertTrue(HerdrCodec.parseResponse("not json") is HerdrCodec.Response.Garbage)
        assertTrue(HerdrCodec.parseResponse("") is HerdrCodec.Response.Garbage)
    }

    @Test
    fun `snapshot parses panes and statuses`() {
        val json = """
            {"snapshot":{"version":"0.9.5","protocol":22,"panes":[
              {"pane_id":"w1:p1","terminal_id":"term_1","workspace_id":"w1","tab_id":"w1:t1",
               "agent":"pi","agent_status":"done","terminal_title_stripped":"π - repo",
               "foreground_cwd":"/repo","revision":6},
              {"pane_id":"w4:pA","workspace_id":"w4","tab_id":"w4:t1","agent_status":"weird"}
            ],"agents":[]}}
        """.trimIndent()
        val r = HerdrCodec.parseResponse("""{"id":"1","result":$json}""") as HerdrCodec.Response.Ok
        val snap = HerdrCodec.parseSnapshot(r.result, 123L)
        assertEquals("0.9.5", snap.version)
        assertEquals(22, snap.protocol)
        assertEquals(2, snap.panes.size)
        assertEquals(AgentStatus.DONE, snap.panes[0].status)
        assertEquals("pi", snap.panes[0].agent)
        assertEquals("/repo", snap.panes[0].cwd)
        // 未知状态 → UNKNOWN，不崩
        assertEquals(AgentStatus.UNKNOWN, snap.panes[1].status)
        // base36 pane id 原样保留
        assertEquals("w4:pA", snap.panes[1].paneId)
        assertEquals(123L, snap.fetchedAtMs)
    }

    @Test
    fun `agent_session path is merged from agents array into pane`() {
        val json = """
            {"snapshot":{"version":"0.9.5","protocol":22,
              "panes":[{"pane_id":"w4:p1","terminal_id":"t1","workspace_id":"w4","tab_id":"w4:t1","agent":"pi","agent_status":"done","revision":1}],
              "agents":[{"pane_id":"w4:p1","agent":"pi","agent_status":"done","agent_session":{"source":"herdr:pi","agent":"pi","kind":"path","value":"/tmp/x.jsonl"}}]}}
        """.trimIndent()
        val r = HerdrCodec.parseResponse("""{"id":"1","result":$json}""") as HerdrCodec.Response.Ok
        val snap = HerdrCodec.parseSnapshot(r.result, 1L)
        assertEquals("/tmp/x.jsonl", snap.panes[0].agentSessionPath)
    }

    @Test
    fun `agent_session with kind id is not treated as path`() {
        val json = """
            {"snapshot":{"version":"0.9.5","protocol":22,
              "panes":[{"pane_id":"w1:p1","terminal_id":"t1","workspace_id":"w1","tab_id":"w1:t1","agent":"claude","agent_status":"idle","revision":1}],
              "agents":[{"pane_id":"w1:p1","agent":"claude","agent_status":"idle","agent_session":{"source":"herdr:claude","agent":"claude","kind":"id","value":"sess-123"}}]}}
        """.trimIndent()
        val r = HerdrCodec.parseResponse("""{"id":"1","result":$json}""") as HerdrCodec.Response.Ok
        assertNull(HerdrCodec.parseSnapshot(r.result, 1L).panes[0].agentSessionPath)
    }

    @Test
    fun `ping capabilities are string map and protocol parsed`() {
        val r = HerdrCodec.parseResponse(
            """{"id":"1","result":{"type":"pong","version":"0.9.5","protocol":22,"capabilities":{"live_handoff":true,"endpoint_protocol_generation":1}}}""",
        ) as HerdrCodec.Response.Ok
        val ping = HerdrCodec.parsePing(r.result)
        assertEquals(22, ping.protocol)
        assertEquals("true", ping.capabilities["live_handoff"])
        assertEquals("1", ping.capabilities["endpoint_protocol_generation"])
    }

    @Test
    fun `event parsing extracts type and pane id from both shapes`() {
        val created = HerdrCodec.parseEvent(
            """{"event":"pane_created","data":{"type":"pane_created","pane":{"pane_id":"w4:p2"}}}""",
        )
        assertNotNull(created)
        assertEquals("pane_created", created!!.type)
        assertEquals("w4:p2", created.paneId)

        val closed = HerdrCodec.parseEvent(
            """{"event":"pane_closed","data":{"type":"pane_closed","pane_id":"w4:p2","workspace_id":"w4"}}""",
        )
        assertEquals("w4:p2", closed!!.paneId)

        assertNull(HerdrCodec.parseEvent("""{"id":"x","result":{"type":"ok"}}"""))
    }
}
