# UX-04b（补完）report — Composer 内「查看发送记录」持久入口

- baseline：`f428601` / `ux/graphite-editorial`
- 依据：COMPOSER-SPEC v0.2 §5.3「Unknown → `发送结果无法确认` + **查看记录**」、§5.4 线框
- 改动：
  - `ConversationScreen` 新增 `onOpenSendRecord` 参数，透传给 `ComposerBar`
  - Unknown 待确认行由「`重新发送…`」单按钮变为 **`查看发送记录` + `重新发送…`**（前者只读查看，后者仍需二次确认对话框）
  - 资源新增 `composer_review_action`（zh 查看发送记录 / en Review send record）
  - `AppRoot` 接线到已有的 `pendingSheet`（列出 method/target、action 前缀、尝试次数、Unknown 原因）
- 未改动：WriteLedger、二次确认、新 Action ID、SendTargetCheck
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：Unknown 态需要构造一次超时写入（会真实写远端），**未构造**，本轮未截图（记为未验证）
- 四类结论：
  - **已实现并验证**：入口存在且与 `pendingSheet` 连通；UI 26/26 无回归
  - **已实现但未验证**：真机 Unknown 场景下的按钮可见性与点击链路
  - **未实现**：`IdentityUncertain`（身份不可靠时的草稿归属选择）；≥600dp 720dp 居中；Composer 内「已恢复草稿」一次性提示
  - **已知问题**：Unknown 行仅在 `pendingText != null` 时出现，重启 App 后需等下一次发送失败或从工作台 Banner 进入
- 回滚：`git revert <this commit>`
