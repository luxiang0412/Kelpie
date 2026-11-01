# UX-09 修复 — ≥600dp 内容最大宽 720dp

- baseline：`c2caa34`
- 问题（P8 截图暴露）：看板/对话在 600dp 宽度下内容仍整宽左对齐，只有 Composer 有 720dp 约束。
- 改动：
  - `BoardScreen` 的 `LazyColumn`：`widthIn(max = 720.dp)`（仅 `screenWidthDp >= 600`）+ `align(CenterHorizontally)`
  - `ConversationScreen` 的消息 `LazyColumn`：同约束 + `align(TopCenter)`
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：`ux09-p8-600-light.png`（AVD `wm size 1600x2560` / `density 320` / en / Light）
  - 空态与按钮已居中；**注意**：本截图里标题行仍靠左（顶栏是页面级 header，未纳入 720dp 容器），属已知差异
- 四类结论：
  - **已实现并验证**：列表容器 720dp 约束 + 居中对齐；UI 26/26
  - **已实现但未验证**：有内容（非空态）时的 600dp 观感；顶栏是否也需要 720dp 约束
  - **未实现**：大屏两列/侧栏（NavigationRail）布局——规格为「预留」，本轮不做
  - **已知问题**：顶栏与列表的左右边缘在 600dp 下不对齐（顶栏整宽、列表居中）
- 回滚：`git revert <this commit>`
