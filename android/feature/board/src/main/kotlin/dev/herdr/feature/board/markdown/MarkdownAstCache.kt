package dev.herdr.feature.board.markdown

/**
 * Markdown AST 缓存（M2-C1）。
 *
 * **为什么按内容寻址**：Markdown 的渲染结果只取决于 (文本内容, 解析器版本)。
 * 用内容哈希做 key 天然满足「Host / Session / 分支 / 消息 id」的失效要求——
 * 不同作用域下内容相同即可安全复用，内容一变 key 就变，不会读到旧结果。
 * （若按 session+branch+msgId 建 key，还需要额外维护失效表，反而更容易出错。）
 *
 * 内存控制：
 *  - LRU，最多 [MAX_ENTRIES] 条，超出淘汰最久未用的；
 *  - 单条内容超过 [MAX_SOURCE_LENGTH] 不入缓存（避免长代码块长期占内存）；
 *  - [PARSER_VERSION] 变化（语法子集/渲染语义改动）时整体失效。
 */
object MarkdownAstCache {

    const val PARSER_VERSION = 3
    const val MAX_ENTRIES = 256
    const val MAX_SOURCE_LENGTH = 120_000
    private const val SAMPLE = 48

    /**
     * key 里额外带文本抽样（首尾各 48 字符）：即使 64 位哈希碰撞，
     * 抽样不同也会直接判定为未命中，避免把 A 的内容渲染给 B（I-85）。
     */
    private data class Key(
        val hash: Long,
        val length: Int,
        val parserVersion: Int,
        val head: String,
        val tail: String,
    )

    private val lru = object : LinkedHashMap<Key, List<MdBlock>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<MdBlock>>?): Boolean =
            size > MAX_ENTRIES
    }

    private var hits = 0L
    private var misses = 0L

    @Synchronized
    fun getOrParse(source: String): List<MdBlock> {
        if (source.length > MAX_SOURCE_LENGTH) return MarkdownParser.parse(source)
        val key = Key(
            hash = hash(source),
            length = source.length,
            parserVersion = PARSER_VERSION,
            head = source.take(SAMPLE),
            tail = source.takeLast(SAMPLE),
        )
        val cached = lru[key]
        if (cached != null) {
            hits++
            return cached
        }
        misses++
        val parsed = MarkdownParser.parse(source)
        lru[key] = parsed
        return parsed
    }

    @Synchronized
    fun stats(): Stats = Stats(hits, misses, lru.size)

    @Synchronized
    fun clear() {
        lru.clear()
        hits = 0
        misses = 0
    }

    data class Stats(val hits: Long, val misses: Long, val size: Int)

    /** 64 位 FNV-1a：足够区分消息文本，成本远低于解析。 */
    private fun hash(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        var i = 0
        while (i < s.length) {
            h = h xor s[i].code.toLong()
            h *= 0x100000001b3L
            i++
        }
        return h
    }
}
