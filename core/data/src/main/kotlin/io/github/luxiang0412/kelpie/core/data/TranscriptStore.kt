package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.ChatMessage
import io.github.luxiang0412.kelpie.core.herdr.ToolCalls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * transcript 的本地缓存（Room）。
 *
 * 设计：
 * - 以 (hostId, path, seq) 为主键，seq 是“该会话第几条消息”；
 * - cursor 记录已消费到的文件偏移 + 文件前缀指纹，用于续读与替换检测；
 * - UI 只订阅 Room（离线也能看历史），网络层只负责“把新字节变成消息”。
 */
class TranscriptStore(private val dao: TranscriptDao) {

    fun observe(hostId: String, path: String): Flow<List<ChatMessage>> =
        dao.observe(hostId, path).map { rows ->
            rows.map { r ->
                val (questions, answers) = ToolCalls.decode(r.structured)
                ChatMessage(
                    kind = runCatching { ChatMessage.Kind.valueOf(r.kind) }
                        .getOrDefault(ChatMessage.Kind.SYSTEM),
                    text = r.text,
                    toolName = r.toolName,
                    id = r.msgId,
                    parentId = r.parentId,
                    toolCallId = r.toolCallId,
                    timestamp = r.timestamp,
                    questions = questions,
                    answers = answers,
                )
            }
        }

    suspend fun append(hostId: String, path: String, messages: List<ChatMessage>): Int {
        if (messages.isEmpty()) return 0
        val base = dao.maxSeq(hostId, path) + 1
        dao.append(
            messages.mapIndexed { i, m ->
                TranscriptMessageEntity(
                    hostId = hostId,
                    path = path,
                    seq = base + i,
                    kind = m.kind.name,
                    text = m.text,
                    toolName = m.toolName,
                    msgId = m.id,
                    parentId = m.parentId,
                    toolCallId = m.toolCallId,
                    timestamp = m.timestamp,
                    structured = ToolCalls.encode(m.questions, m.answers),
                )
            },
        )
        return messages.size
    }

    /**
     * 只保留最近 [keep] 条：超过 keep + [slack] 才删，一次删回 keep 条。
     * 不每条都删：删掉开头会让对话页整体重建（下标全变），攒一批再删，重建频率就低了。
     * 返回是否删过（删过 = 更早的消息不在本地了，UI 要如实提示）。
     */
    suspend fun trim(hostId: String, path: String, keep: Int = KEEP_MESSAGES, slack: Int = TRIM_SLACK): Boolean {
        if (dao.count(hostId, path) <= keep + slack) return false
        dao.deleteBefore(hostId, path, dao.maxSeq(hostId, path) + 1 - keep)
        return true
    }

    /** 清掉 [maxAgeMs] 内没有更新过的会话缓存（打开对话时会重新回填）。 */
    suspend fun pruneStale(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = STALE_AFTER_MS): Int =
        dao.pruneStale(nowMs - maxAgeMs)

    suspend fun reset(hostId: String, path: String) {
        dao.clearMessages(hostId, path)
        dao.clearCursor(hostId, path)
    }

    suspend fun cursor(hostId: String, path: String): TranscriptCursorEntity? = dao.cursor(hostId, path)

    suspend fun saveCursor(hostId: String, path: String, offset: Long, headHash: String?, truncated: Boolean) {
        dao.saveCursor(
            TranscriptCursorEntity(hostId, path, offset, headHash, System.currentTimeMillis(), truncated),
        )
    }

    suspend fun clearHost(hostId: String) {
        dao.clearHost(hostId)
    }

    companion object {
        const val KEEP_MESSAGES = 2000
        const val TRIM_SLACK = 500
        const val STALE_AFTER_MS = 14L * 24 * 60 * 60 * 1000
    }
}
