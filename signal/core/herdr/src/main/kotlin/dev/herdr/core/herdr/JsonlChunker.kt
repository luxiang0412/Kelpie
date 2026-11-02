package dev.herdr.core.herdr

/**
 * 增量读取 JSONL 的纯逻辑（可单测，不碰 IO）。
 *
 * 规则（对应 D25 的三条硬约束之一）：
 * - 只在 **换行字节** 处切分，所以 UTF-8 多字节字符永远不会被切断；
 * - 末尾不完整的行不消费：调用方把 offset 停在最后一个 `\n` 之后，下次从那里继续读；
 * - 返回的 offset 是“已消费到的字节位置”，保证重启后能续读。
 */
object JsonlChunker {

    data class Result(
        val lines: List<String>,
        /** 下一个应该读取的绝对偏移（= 最后一个完整行之后）。 */
        val nextOffset: Long,
        /** 末尾未消费的字节数（半行）。 */
        val pendingBytes: Int,
    )

    /**
     * @param startOffset 本次 [bytes] 对应的文件偏移
     * @param bytes 从 [startOffset] 开始读到的字节
     */
    fun consume(startOffset: Long, bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Result {
        var lastNl = -1
        for (i in from until to) {
            if (bytes[i] == '\n'.code.toByte()) lastNl = i
        }
        if (lastNl < 0) {
            return Result(emptyList(), startOffset, to - from)
        }
        val text = String(bytes, from, lastNl - from + 1, Charsets.UTF_8)
        val lines = text.split('\n').filter { it.isNotEmpty() }
        return Result(lines, startOffset + (lastNl - from) + 1, to - lastNl - 1)
    }
}
