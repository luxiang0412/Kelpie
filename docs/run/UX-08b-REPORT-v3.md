# UX-08b（空态统一）report — 共享 `EmptyState` 组件

- baseline：`5b10753` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM §5 组件契约 + 任务书 UX-08b「空态覆盖无主机 / 在线无 Agent / 断线 / 空会话；图标+简短真实解释+真实可用动作，禁止每个都塞大插画」
- 改动：
  1. `design/Components.kt` 新增 **`EmptyState(title, body, actionLabel, onAction, secondaryLabel, onSecondary)`**：居中、标题 `titleSmall`、说明 `bodyMedium`，主操作是 Material Button，次操作 TextButton（≥48dp）；**没有插画、没有假数据**
  2. `BoardScreen` 空态改为调用它，并**区分两种来源**：
     - 无主机 → `No agents / Add a host to start / Host settings` + 次操作 `Retry`
     - 有主机但无 Agent → `No agents` + **真实连接状态文案**（`active.connText`）+ `Retry`
  3. 保留旧 `EmptyState` 私函数（HomeScreen 内）暂未删除，避免一次改太多
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：`docs/evidence/ux08b-empty-p1.png`（AVD 393dp / en / Light）——统一空态：`No agents` / `Add a host to start` / `Host settings` / `Retry`
- 四类结论：
  - **已实现并验证**：共享组件、工作台两种空态分流、截图、26 项 UI 无回归
  - **已实现但未验证**：对话页空会话、断线空态是否迁到新组件（尚未迁移）
  - **未实现**：对话页 `EmptyBlock/LoadingBlock/ErrorBlock` 迁移；Hosts 页空态迁移；「无搜索结果」空态（搜索目前只在搜索栏里显示 `No match`）
  - **已知问题**：工作台内旧 `EmptyState` 私有函数已不再被调用（死代码），下次清理
- 回滚：`git revert <this commit>`
