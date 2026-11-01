# UX-03d report — 消息复制、系统选择、更多菜单（CONV-ACTIONS-001）

- baseline：`62d03a1` / `ux/graphite-editorial`
- inspected：`ConversationScreen.kt`（`AssistantMessage`、`MessageList`）
- goal：落实**已批准**的 `CONV-ACTIONS-001`：正文末尾常显低强调「复制 + 更多」，长按走系统选择
- changed（`ConversationScreen.kt`）：
  - `AssistantMessage` 正文包进 `SelectionContainer` → 长按由 Android 系统文本选择处理，父级不拦截
  - 新增 `MessageActions(text)`：`复制`（48dp）+ `更多`（48dp）；`更多` 菜单只有**已实现**的「复制原文（Markdown）」，未放 Quote/Share 占位
  - 复制内容 = 该条消息的**源文本**（非渲染后反推），不含 Agent 名/时间/按钮字样
  - 两个按钮都带 `contentDescription`（复制这条回复 / 更多操作），避免与顶栏「更多」混淆
  - 代码块自身的「复制」仍只复制 `block.code`（不含 fence/语言标签），与本操作条互不影响
- tests：新增 `MessageActionsTest`（3 项）：操作条常显、菜单只含已实现项、点击复制后状态变「已复制」
  - AVD `emulator-5556` 全量：**OK (26 tests)**
- screenshots：`docs/evidence/ux03d-avd.png`（AVD 393dp / Dark）— 两条 Agent 回复末尾都有「复制 更多」，Thinking 折叠、Tool 状态「运行中/已返回 + 未配对（无可靠 ID）」
- 四类结论：
  - **已实现并验证**：操作条常显且 ≥48dp；菜单无假入口；复制取源文；26 项 UI 测试通过
  - **已实现但未验证**：真机长按选区手感与剪贴板敏感标记（`ClipDescription.EXTRA_IS_SENSITIVE` 未设置，如需要再补）
  - **未实现**：Quote / Share（按任务书：未完成就不显示）；剪贴板敏感标志
  - **已知问题**：不安全链接旁的文字「（链接已停用）」与链接之间多一个空格（下一轮统一修）
- commit：见提交
