# UX-05a（重做·第 1 步）report — 术语与导航文案对齐 DESIGN-SYSTEM §3.1/§7.3

- baseline：`77ff077` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §3.1「术语统一」、§7.3「关键文案字典」；BOARD-SPEC §2.1 标题
- 改动（仅资源，不动逻辑）：
  | 位置 | 原 | 现（zh / en） |
  | --- | --- | --- |
  | `board_status_blocked` | 需要你 / Needs you | **需要处理 / Needs attention** |
  | `board_section_blocked` | 需要处理 / Needs you | 需要处理 / **Needs attention** |
  | `nav_board` | 看板 / Board | **工作台 / Agents** |
  | `composer_footnote` | 已接受（Accepted）…结果未知（Unknown）… | **「指令已接收」…「发送结果无法确认」…** |
- 测试：`assembleDebug` + `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD UI **OK (26 tests)**（测试断言走 `R.string`，与文案语言解耦）
- 截图：文案变更属文字级，未单独截图；下一次任务会与 Composer 状态一起出图
- 四类结论：
  - **已实现并验证**：4 条关键术语与导航名对齐规格；UI 26/26 无回归
  - **已实现但未验证**：真机中文下的视觉长度（"需要处理" 比 "需要你" 长 1 字，需确认行内不换行）
  - **未实现**：`Accepted/Rejected/Unknown` 三态**气泡文案**仍是旧措辞（属 UX-04b：`指令已接收` / `发送被拒绝` / `发送结果无法确认` + `查看发送记录`）；`composer_*` 其余 Key、通知文案、Hosts/Sheet 文案
  - **已知问题**：`values/strings.xml` 的 `board_section_blocked` 之前是 "Needs you"，与状态词同义，现已区分
- 回滚：`git revert <this commit>`
