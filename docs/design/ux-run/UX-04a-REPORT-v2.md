# UX-04a（重做）report — Composer 焦点环 / Send 视觉圆面 / 发送中文案

- baseline：`9acdc38` / `ux/graphite-editorial`
- 依据：COMPOSER-SPEC v0.2 §3.1「Send 点击区 ≥48dp、**视觉圆面 40dp**」、§3.3「聚焦 1–2dp Accent Focus Ring」、§5.3「Sending：`正在发送…`」
- 改动（`ConversationScreen.kt` + `HostConnectionManager.kt`）：
  1. 输入框聚焦时叠加 **2dp `focusRing`（Accent）描边**（`Modifier.onFocusChanged` + `border`），不再只靠 `borderSubtle`
  2. Send 改为**圆形**：48dp 点击区 + 内层 **40dp 视觉圆面**，图标 `↑`；禁用态用 `outline` 色
  3. Sending 文案 `发送中…` → **`正在发送…`**（规格 §5.3）
  4. 保留：null(Light) 双层 Surface、20/18dp 圆角、16/12dp 边距、1–5 行、Enter 换行、无假 Mic/附件
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
  - `sendButton_isNotClickableWhileSending` 改为断言语义「发送中，请稍候」+ 文案「正在发送…」
- 截图：`spike/results/ux04a-v2-composer.png`（AVD 393dp / en / Light）——圆形 Send、双层 Surface
- 四类结论：
  - **已实现并验证**：focus ring、40dp 圆面 + 48dp 触达、Sending 文案；UI 26/26
  - **已实现但未验证**：真机 IME 下 focus ring 与键盘的视觉关系；聚焦/失焦切换的真实手感
  - **未实现**：≥600dp 时 Composer 最大宽 720dp 居中；`IdentityUncertain` 草稿归属提示；`查看发送记录` 按钮置于 Composer 状态条
  - **已知问题**：Send 只显示 `↑`，无文字标签——依赖 `contentDescription`，TalkBack 可读但视觉上弱于原「发送」二字（规格未要求文字，保留）
- 回滚：`git revert <this commit>`
