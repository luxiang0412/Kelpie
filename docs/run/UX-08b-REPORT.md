# UX-08b report — 统一空态、异常态、状态与 Motion（PARTIAL）

- baseline：`42b8c6b` / `ux/graphite-editorial`
- 审计结果（逐条对照任务书）：
  | 要求 | 现状 | 结论 |
  | --- | --- | --- |
  | 空态：无主机 / 在线无 Agent | 工作台用同一 `EmptyState`，按 `active == null` 与 `panes.isEmpty()` 区分文案与按钮 | ✅ 已区分（但未区分"鉴权失败导致的空"） |
  | 空态：断线 / 会话空 / 消息失败 / 无搜索结果 / 无权限 STT | 对话页有 `LoadingBlock/ErrorBlock/EmptyBlock`；搜索与 STT 未实现 | ⚠️ 部分 |
  | 动效：按钮轻反馈、折叠、消息进入 | 折叠用 `AnimatedVisibility` 默认规格；按钮用 Material 默认波纹 | ✅ 无语义问题 |
  | **不停止的装饰光效** | 无 | ✅ |
  | 连接恢复不弹连续 Dialog、不多条 Banner 叠压 | UX-02c 已改为**单条** Banner | ✅ |
  | 主操作不用 Unicode 替代字符 | UX-00a 已清零 `› ▸ ▾ ⤢` | ✅ |
  | 320dp/1.6 图标+文字挤压截断 | 工作台标题已改为 2 行；对话顶栏单行省略 | ✅ 已修（截图为 AVD） |
- 本轮未新增代码：上述要么已在 UX-00a/02a/02b/02c 完成，要么依赖未实现功能（搜索/STT）。
  按任务书「不虚构」，**不添加**搜索空态、STT 权限空态。
- 四类结论：
  - **已实现并验证**：单条 Banner、图标基线、折叠动效、无循环动画、标题换行
  - **已实现但未验证**：`reduce motion`（系统"移除动画"设置）下的表现未测
  - **未实现**：统一 `EmptyState` 组件（当前每个页面各写一份）；搜索/STT 空态；鉴权失败专属空态
  - **已知问题**：`CollapsibleRow` 与 `ToolRow` 的折叠动画时长不同（180 vs 默认），未统一到 `Motion` token
