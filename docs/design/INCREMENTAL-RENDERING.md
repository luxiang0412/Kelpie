# 增量渲染设计（M2-C1）

## 1. 数据流

```
pi JSONL ──PiTranscriptParser──> List<ChatMessage>   (id / parentId / toolCallId)
                                      │
                     ConversationBranch.effective()   ← 树 → 当前有效分支
                                      │
                   IncrementalConversationMapper      ← 增量：APPEND / PATCH / FULL
                                      │
                             List<ConversationItem>   ← 渲染模型（不可变 data class）
                                      │
                        LazyColumn(key = paneId-index, contentType = 类型)
```

三处派生状态全部**不在主线程做重解析**：Parser 在 IO 线程，Branch/Mapper 在 ViewModel 侧，
Markdown AST 在 `Dispatchers.Default` + `MarkdownAstCache`。

## 2. 增量算法

`IncrementalConversationMapper.update(messages, sessionKey)`：

| 条件 | 模式 | 成本 |
| --- | --- | --- |
| `sessionKey` 变化 | FULL | O(n)，受控重建（会话切换/分支切换） |
| 新旧序列完全相同 | NONE | O(n) 比较，O(1) 分配（返回原列表） |
| 旧序列是新序列的**前缀** | APPEND | O(k)，k = 新增条数 |
| 等长且只有少量**非工具**消息变化 | PATCH | O(changed) |
| 工具消息变化、乱序、id 冲突、早期结果合并 | FULL | O(n) |

APPEND 的两条快速路径：
1. 新增普通消息 → 直接 `prevItems + single(msg)`，不触碰历史；
2. 新增 `TOOL_RESULT` 且 `toolCallId` 命中已有工具条目 → **只原位替换那一个条目**
   （`toolItemByCallId` 增量维护，O(1) 查找 + 一次浅拷贝），不重解析任何历史消息。

重复事件（同消息 `id`）在 APPEND 路径直接丢弃，与参考实现的去重语义一致。

## 3. ToolCall / ToolResult 关联（唯一真相：`ConversationResolve`）

- 只有 `toolCallId` 相等才算配对（`pairedById = true`）；
- 结果先于调用到达 → 仍按 id 配对，条目落在**调用的位置**；
- 无 id 或 id 不匹配的结果 → **独立条目**，绝不按相邻位置猜测；
- 重复 id 事件 → 保留首次；同 id 多个结果 → 取最后一个（结果更新）；
- 分支切换后旧分支的结果自然不在有效序列里，不会串到新分支（`ConversationBranchTest`）。

## 4. 缓存与内存预算

| 缓存 | Key | 上限 | 失效 |
| --- | --- | --- | --- |
| Markdown AST (`MarkdownAstCache`) | `(FNV-1a(content), length, PARSER_VERSION)` | 256 条 LRU | 内容变 → key 变；`PARSER_VERSION` 升 → 全失效 |
| 映射条目 (`IncrementalConversationMapper`) | 会话前缀比较 | 与当前会话等长 | 会话/分支切换清空；`reset()` |

- 按内容寻址即可满足「Host/Session/分支/消息 id」的正确性要求：内容不同 → key 不同 →
  不可能读到旧结果；内容相同 → 跨会话复用是安全的。
- 超过 120k 字符的单条内容不入缓存（长代码块不长期占内存）。
- `remember(text)` 仅作为**帧内**快速路径，不作为持久缓存（Compose 会随时丢弃 remember）。

## 5. 线程安全与取消

- `IncrementalConversationMapper` 明确**非线程安全**，只在单一调度器（ViewModel）内使用；
- 会话切换通过 `sessionKey` 或 `reset()` 清空派生状态，旧会话结果不会覆盖新会话
  （`sessionSwitch_discardsPreviousState`）；
- `MarkdownAstCache` 用 `@Synchronized` + LRU，可被多线程安全访问。

## 6. 已知限制

- `ConversationBranch.effective()` 与 `ConversationItemMapper.map()` 仍是 O(n)，每次消息变化都会跑；
  但它们**不解析 Markdown、不做字符串拼接**，实测 2000 条 ≈ 0.9 ms（见 M2-C1 报告）。
- PATCH 只处理非工具消息；工具消息更新走 FULL（工具数量少，代价可接受，避免错误配对）。
