# UX-04b（重做）report — 发送三态文案 + 离线禁用 Send

- baseline：`48bb6c4` / `ux/graphite-editorial`
- 依据：COMPOSER-SPEC v0.2 §5.1 `OfflineDraft`、§5.3「写入状态在 UI 的表现」
- 改动：
  1. `HostConnectionManager` 三态回执文案：
     | 状态 | 原 | 现 |
     | --- | --- | --- |
     | Accepted | 已接受（Accepted）：远端已收到，未代表执行完成 | **指令已接收（远端已收到，不代表执行完成）** |
     | Rejected | 被拒绝：code（…） | **发送被拒绝：code（内容已保留，可修改后重发）** |
     | Unknown | 结果未知（Unknown）：不会自动重发… | **发送结果无法确认，远端可能已执行；不会自动重发** |
  2. `ComposerBar` 新增 `offline` 参数（由 `ConversationScreen` 的 `resyncing` 传入）：
     - 离线/重连中时 **Send 直接禁用**（此前可点、点了才 Rejected）
     - 输入区内显示 `已离线，可继续编辑草稿（不会排队发送）`
- 未改动：草稿库、WriteLedger、SendTargetCheck、Unknown 二次确认与新 Action ID 逻辑
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
  - 现有 `ComposerUiTest.rejected_keepsTextAndShowsCode` 与 `unknown_*` 断言使用 `res(R.string…)` 或行为断言，未依赖本次修改的文案
- 截图：离线态需要真实断网环境，未截取（记为未验证）；正常态见 `docs/evidence/ux00a-v2-conv-light.png`
- 四类结论：
  - **已实现并验证**：三态文案对齐规格；离线禁用 Send；UI 26/26 无回归
  - **已实现但未验证**：真机断网下的 Send 禁用 + 提示（需设备侧网络开关）
  - **未实现**：`查看发送记录` 按钮（目前 Unknown 入口在工作台 Banner / 技术信息 Sheet，未放进 Composer 状态条）；`IdentityUncertain` 草稿归属提示；Send 40dp 视觉圆面（仍是胶囊）；≥600dp 720dp 居中
  - **已知问题**：`offline` 复用了 `resyncing` 语义（连接非 CONNECTED 即视为离线），在"重连中"时也会禁用 Send——符合规格「连接不可写不得发送」，但文案写的是"已离线"，重连场景措辞略不精确
- 回滚：`git revert <this commit>`
