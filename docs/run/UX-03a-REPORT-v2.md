# UX-03a（重做）report — 用户气泡分档 / Agent 标识 / Thinking 可选 / Unknown 降级

- baseline：UX-03b 提交 / `ux/graphite-editorial`
- 依据：CONVERSATION-SPEC v0.2 §3.3（气泡 88%/84–86%/520dp）、§4（Agent 标识 13sp 中性、一次/组）、§5.3（Thinking 可选择复制）、§5.4（Unknown 用「暂不支持预览」+ 查看/复制原文）
- 改动（`ConversationScreen.kt`）：
  1. `UserMessage`：固定 86% → **三档**（<360dp 88%、360–599dp 86%、≥600dp `widthIn(max = 520dp)` 居中右侧）
  2. `AgentGroupHeader`：12sp → **13sp（type.meta）中性色**，仍只在连续回复组首出现一次
  3. `CollapsibleRow`（Thinking / 系统）：内容包 `SelectionContainer` → **可长按选择与复制**
  4. 新增 `UnknownContent`：显示 **「暂不支持预览」+ 展开原文 / 复制原文**（此前直接倒原文，无说明）
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：`docs/evidence/ux03ab-spacing.png`（AVD 393dp / en / Light）——Agent 标识 13sp 灰、Thinking 行、工具卡；气泡三档需 320/600dp 另拍（记为未验证）
- 四类结论：
  - **已实现并验证**：标识字号、Thinking 可选、Unknown 降级、气泡分档逻辑；26 项 UI 无回归
  - **已实现但未验证**：320dp/600dp 下的气泡实际宽度（未截图）；TalkBack 对「暂不支持预览」的朗读
  - **未实现**：Agent 标识的可选 20–24dp 图标（规格写「可配」）；气泡尾部 6dp 圆角差异
  - **已知问题**：`UnknownContent` 的展开状态未按会话/消息 ID 记忆（重组会复位）
- 回滚：`git revert <this commit>`
