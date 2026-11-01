# UX-03c report — Tool / Thinking / 长输出

- baseline：UX-03b 提交 / `ux/graphite-editorial`
- inspected：`ConversationScreen.kt`（`ToolRow`、`CollapsibleRow`）、`conversation/ConversationResolve.kt`、`ConversationItemMapper.looksLikeError`
- goal：工作记录可读、可信、按需展开；**结果收到 ≠ 执行成功**；不从 error 字样盲猜失败
- changed（`ConversationScreen.kt`）：
  - **移除"失败"状态推断**：原 `resultError -> "失败" + 红色` 改为
    - 状态词只有两种真实状态：`运行中`（无结果）/ `已返回`（有结果），中性色
    - 输出里命中 error 关键字时，追加**低强调**提示「输出含 error 关键字」，不着色、不称为失败
  - Tool 头部高度 48 → **52dp**（Spec 要求 ≥52dp）
  - 结果正文取消红色着色（避免把 Unknown 当失败）
  - Thinking 仍默认折叠；长输出仍限高 260dp 内部滚动
- why：Spec §7「结果收到不等于执行成功，不能从输出含 error 字样盲猜失败」；这是**正确性**修正，不只是视觉
- tests：`:app:assembleDebug` OK；`ConversationItemMapperTest`/`ConversationPairingTest` 的 `resultError` 保留为**数据标记**（不再直接映射为 UI 失败态）
- 四类结论：
  - **已实现并验证**：状态词真实、error 关键字降级为提示、52dp 头部
  - **已实现但未验证**：并行工具/结果先到/无匹配 ID 的真实会话渲染（单测覆盖数据层，UI 未逐项截图）
  - **未实现**：`CONV-TOOL-002` 的「查看完整输出」页 —— 数据源不提供完整输出时**不放假入口**（当前仅有部分结果，因此不显示该按钮）
  - **已知问题**：`looksLikeError` 启发式仍存在，但只影响一句提示文字，不影响状态语义
- commit：见提交
