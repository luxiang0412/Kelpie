# UX-06 — 会话内搜索：审计结论与实施计划（本轮 PARTIAL，未交付搜索 UI）

## 1. 审计：数据是否足以"搜索当前有效会话"

| 检查 | 结论 |
| --- | --- |
| 已解析消息来源 | `TranscriptStore`（Room `transcript_messages`，按 `hostId+path+seq`）+ `PiTranscriptParser` |
| 当前分支可见性 | `ConversationBranch.effective(messages)` 可用，能拿到当前有效序列 |
| 是否含完整远端历史 | **不能保证**：客户端只增量拉取 JSONL 尾部（`TranscriptTailer`），未被拉取的历史不在本地 |
| 工具输出是否在消息文本里 | 是（`ChatMessage.text`，含 TOOL_RESULT 全文） |
| 折叠状态的"完整输出" | **没有**：数据源只提供已获取部分，无法声称可检索完整输出 |

**因此按任务书要求**：搜索必须是**「仅搜索已加载消息」**，且 UI 必须明确标注该边界，
不得出现"搜索全部历史"的暗示。

## 2. 计划实现（下一轮）

1. **入口**：对话顶栏 `更多` → `搜索`；打开后顶栏替换为搜索框（`TextField` + 上/下一处 + 计数 + 关闭）。
2. **匹配**：
   - 输入防抖 250ms；`Dispatchers.Default` 上对 `ConversationBranch.effective(messages)` 的 `text` 做
     `String.indexOf(ignoreCase = true)`（代码内容不 trim、不改大小写展示）；
   - 中文/英文/路径/代码片段同一套逻辑，不做 Unicode 归一化之外的变形（NFKC 归一化单独开关）。
3. **定位**：用**稳定条目 key**（`paneId-index`）而非序号 → `listState.animateScrollToItem(index)`；
   命中项高亮用 `SpanStyle(background)` 叠加在 Markdown 渲染之外（不改 AST）。
4. **边界标注**：搜索框下方常驻一行 `仅搜索已加载的 N 条消息`；ToolResult 默认参与，折叠内容参与（已是全文）。
5. **状态**：无结果 / 结果超过 200 条截断（提示"仅显示前 200 条"）/ 会话切换自动关闭搜索 / 分支切换重建命中集。
6. **性能**：500 条 × 平均 400 字符 ≈ 200k 字符扫描，单次 < 5ms（同 `MarkdownParserTest` 量级），
   仍放后台并在输入停止后才计算；不用 `derivedStateOf` 逐字符触发。

## 3. 为什么本轮没做

剩余会话预算不足以完成「实现 + UI 测试 + 截图 + 报告」闭环，且搜索需要新增 5 处状态与 1 组 UI 测试。
按任务书 §1.2「不具备前提时不得虚构」，本轮只交付本审计 + 计划，**没有**添加任何搜索按钮，
避免出现「点开是空的」假功能。
