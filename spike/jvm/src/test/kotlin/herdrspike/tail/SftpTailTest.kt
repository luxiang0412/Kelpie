package herdrspike.tail

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SftpTailTest {

    private fun linesOf(r: TailResult): List<String> = when (r) {
        is TailResult.Lines -> r.lines
        else -> emptyList()
    }

    @Test
    fun `utf8 multibyte split across reads is not corrupted`(@TempDir dir: Path) {
        val f = dir.resolve("a.jsonl")
        val content = """{"t":"中文行一"}""" + "\n" + """{"t":"第二行"}""" + "\n"
        writeFile(f, content.toByteArray(Charsets.UTF_8))

        // 每次最多读 1 字节 → 强制多字节字符跨读取边界
        val tail = SftpTail(LocalFileByteSource(maxReadPerCall = 1), f.toString())
        val lines = linesOf(tail.poll())
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("中文行一"), "got=${lines[0]}")
        assertTrue(lines[1].contains("第二行"))
    }

    @Test
    fun `invalid utf8 bytes are replaced and do not kill the stream`(@TempDir dir: Path) {
        val f = dir.resolve("b.jsonl")
        val bad = byteArrayOf(0x41, 0xC3.toByte(), 0x28, 0x42, '\n'.code.toByte())  // "A" + invalid + "B"
        writeFile(f, bad + "ok\n".toByteArray())
        val tail = SftpTail(LocalFileByteSource(), f.toString())
        val r = tail.poll() as TailResult.Lines
        assertEquals(2, r.lines.size)
        assertTrue(r.lines[0].contains('\uFFFD'))
        assertTrue(r.invalidBytes > 0)
        assertEquals("ok", r.lines[1])
    }

    @Test
    fun `truncation yields Reset then re-reads from zero`(@TempDir dir: Path) {
        val f = dir.resolve("c.jsonl")
        writeFile(f, "one\ntwo\n".toByteArray())
        val tail = SftpTail(LocalFileByteSource(), f.toString())
        assertEquals(listOf("one", "two"), linesOf(tail.poll()))

        writeFile(f, "new\n".toByteArray())            // truncate + 更短
        val r = tail.poll()
        assertTrue(r is TailResult.Reset, "expected Reset, got $r")
        assertEquals(listOf("new"), linesOf(tail.poll()))
    }

    @Test
    fun `replacement by a larger file is detected within prefixRecheckPolls polls`(@TempDir dir: Path) {
        val f = dir.resolve("d.jsonl")
        writeFile(f, "aaa\n".toByteArray())
        val cfg = TailConfig(prefixRecheckPolls = 5)
        val tail = SftpTail(LocalFileByteSource(), f.toString(), cfg)
        assertEquals(listOf("aaa"), linesOf(tail.poll()))

        Files.delete(f)
        writeFile(f, "zzz-old-content\n".repeat(20).toByteArray())   // 更大、mtime 更新 → size/mtime 无法立刻发现

        // 设计取舍：更大文件的替换采用「有界延迟」检测（≤ prefixRecheckPolls 次 poll）
        var resetAt: Int? = null
        for (i in 1..6) { if (tail.poll() is TailResult.Reset) { resetAt = i; break } }
        assertTrue(resetAt != null && resetAt <= 5, "expected Reset within 5 polls, got $resetAt")
    }

    @Test
    fun `steady-state polls do not re-read the head prefix`(@TempDir dir: Path) {
        val f = dir.resolve("i.jsonl")
        writeFile(f, "l1\n".toByteArray())
        val base = LocalFileByteSource()
        var headCalls = 0
        val counting = object : ByteSource {
            override fun stat(path: String) = base.stat(path)
            override fun readAt(path: String, offset: Long, buf: ByteArray, off: Int, len: Int) =
                base.readAt(path, offset, buf, off, len)
            override fun headBytes(path: String, maxBytes: Int): ByteArray {
                headCalls++; return base.headBytes(path, maxBytes)
            }
        }
        val tail = SftpTail(counting, f.toString(), TailConfig(prefixRecheckPolls = 100))
        tail.poll()
        assertEquals(0, headCalls, "首次 poll 也不应额外读前缀（应从首次读取里顺手取）")
        for (i in 2..5) {
            writeFile(f, "l$i\n".toByteArray(), append = true)
            tail.poll()
        }
        assertEquals(0, headCalls, "稳态 poll 不应读前缀，实际 $headCalls 次")
    }

    @Test
    fun `offset recovery resumes and verifies`(@TempDir dir: Path) {
        val f = dir.resolve("e.jsonl")
        writeFile(f, "1\n2\n3\n".toByteArray())
        val t1 = SftpTail(LocalFileByteSource(), f.toString())
        assertEquals(3, linesOf(t1.poll()).size)
        val state = t1.saveState()

        writeFile(f, "4\n".toByteArray(), append = true)
        val t2 = SftpTail(LocalFileByteSource(), f.toString())
        t2.restoreState(state)
        assertTrue(t2.verifyResume(), "resume point should verify")
        assertEquals(listOf("4"), linesOf(t2.poll()))
    }

    @Test
    fun `resume verification fails when the last line changed`(@TempDir dir: Path) {
        val f = dir.resolve("f.jsonl")
        writeFile(f, "alpha\nbeta\n".toByteArray())
        val t1 = SftpTail(LocalFileByteSource(), f.toString())
        linesOf(t1.poll())
        val state = t1.saveState()

        // 原地改掉最后一行（同样长度），模拟“文件被另一个会话覆写”
        writeFile(f, "alpha\nBETA\n".toByteArray())
        val t2 = SftpTail(LocalFileByteSource(), f.toString())
        t2.restoreState(state)
        assertTrue(!t2.verifyResume(), "changed content must fail verification")
    }

    @Test
    fun `partial line is not emitted until newline arrives`(@TempDir dir: Path) {
        val f = dir.resolve("g.jsonl")
        writeFile(f, "{\"a\":1}".toByteArray())
        val tail = SftpTail(LocalFileByteSource(), f.toString())
        assertTrue(linesOf(tail.poll()).isEmpty())

        writeFile(f, "\n".toByteArray(), append = true)
        assertEquals(listOf("{\"a\":1}"), linesOf(tail.poll()))
    }

    @Test
    fun `short reads do not lose data`(@TempDir dir: Path) {
        val f = dir.resolve("h.jsonl")
        val expected = (1..200).map { "line-$it" }
        writeFile(f, (expected.joinToString("\n") + "\n").toByteArray())
        val tail = SftpTail(LocalFileByteSource(maxReadPerCall = 7), f.toString())
        assertEquals(expected, linesOf(tail.poll()))
    }

    @Test
    fun `large file backfill is bounded in bytes read`(@TempDir dir: Path) {
        val f = dir.resolve("big.jsonl")
        val sb = StringBuilder()
        var i = 0
        while (sb.length < 8 * 1024 * 1024) { sb.append("""{"i":${i++},"pad":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}""").append('\n') }
        writeFile(f, sb.toString().toByteArray())
        val totalSize = Files.size(f)
        assertTrue(totalSize > 4L * 1024 * 1024)

        val src = LocalFileByteSource()
        val tail = SftpTail(src, f.toString(), TailConfig(backfillMaxBytes = 256L * 1024))
        val lines = tail.backfillLastLines(maxLines = 100)
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.size <= 100, "lines=${lines.size}")
        assertTrue(src.bytesRead <= 256L * 1024, "read budget exceeded: ${src.bytesRead} of $totalSize")
        assertTrue(lines.last().contains("\"i\":${i - 1}"), "last line mismatch: ${lines.last()}")
    }
}
