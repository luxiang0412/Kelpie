# UX-05a（收尾）report — TechnicalInfoSheet 待确认区块本地化

- baseline：`a71669b` / `ux/graphite-editorial`
- 改动：`TechnicalInfoSheet.kt` 的「待确认写操作（N）」标题与说明改为 `stringResource`
  - `sheet_pending_writes`（`Pending writes (%1$d)` / `待确认写操作（%1$d）`）
  - `sheet_pending_hint`（英文/中文说明：结果未知、不会自动重发、先核对会话）
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：未单独拍（Sheet 需要存在 pending 写入才显示该区块）——记为未验证
- 四类结论：
  - **已实现并验证**：两处文案资源化、带参数格式化、编译与 UI 测试通过
  - **已实现但未验证**：en/zh 下 Sheet 实际渲染
  - **未实现**：Sheet 其余字段（Pane/Terminal/Workspace/revision 说明）、通知 channel 名与正文、部分 TalkBack `contentDescription`
  - **已知问题**：Sheet 仍是 Material 默认 `ModalBottomSheet` 样式，未按 DESIGN-SYSTEM §5 的「诊断分组」重构
- 回滚：`git revert <this commit>`
