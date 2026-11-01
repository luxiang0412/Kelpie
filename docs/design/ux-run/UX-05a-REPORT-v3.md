# UX-05a（补完）report — Hosts 页文案本地化

- baseline：UX-04b 提交 / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §7.2「输入 placeholder、按钮、菜单、状态、TalkBack 全部本地化」
- 改动：`HostsScreen.kt` 的 35 处中文文案改为 `stringResource`，新增 30 个 key（zh/en 各一份）：
  主机页标题/返回/添加、空态（还没有主机 / 说明 / 添加主机）、编辑主机、主机/用户/端口/herdr 会话/私钥字段、
  私钥已导入/缺少私钥/删除私钥/粘贴说明、连接/断开/删除/保存/取消、语言与后台监控说明等
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD UI **OK (26 tests)**
- 截图：`spike/results/ux05b-lang.png` 之前是中文；本次未重拍（en 环境下的 Hosts 页待补，记为未验证）
- 四类结论：
  - **已实现并验证**：30 个 key 双语、Hosts 页全部可见文案走资源、编译与 UI 测试通过
  - **已实现但未验证**：en 环境下 Hosts 页截图；两处带参数文案（私钥 `私钥 · {label}`）的英文语序
  - **未实现**：`TechnicalInfoSheet` 文案、通知 channel 与正文、TalkBack `contentDescription` 剩余中文
  - **已知问题**：`HostsScreen` 仍用 Material 默认 `TopAppBar`，未按 DESIGN-SYSTEM §2.3「高度可增长」改造（下一包）
- 回滚：`git revert <this commit>`
