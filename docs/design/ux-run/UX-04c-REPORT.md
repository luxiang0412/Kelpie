# UX-04c report — 引用消息 → Composer（草稿冲突保护）

- baseline：UX-04b 提交 / `ux/graphite-editorial`
- inspected：`MessageActions`（UX-03d 建立的 More 菜单）、`HostsViewModel.updateComposerText`、`DraftStore.MAX_CHARS`
- goal：真实可用的"引用到输入框"，不抢现有草稿
- changed：
  - `MessageActions` 的「更多」菜单新增 **「引用到输入框」**
  - `HostsViewModel.quoteIntoDraft(text)`：
    - **不覆盖**已有草稿：空草稿时直接写入引用；已有草稿时 `原草稿 + 空行 + "> " 逐行引用 + 空行`
    - 超过 `DraftStore.MAX_CHARS`（8000）→ **返回 false 并拒绝写入**（不静默截断）
    - 只写本地草稿（`updateComposerText`），**绝不触发发送**
  - `AppRoot`：拒绝时弹「内容太长」说明；引用成功走既有草稿防抖保存
  - 回退说明：Compose `TextFieldValue` 光标插入需要把 Composer 从 `String` 改为 `TextFieldValue`（会牵动草稿/发送快照），本轮按任务书允许的**"追加到末尾并留分隔"**方案实现，并在此记录
- tests：`:app:assembleDebug` OK；AVD 全量 UI **26/26** 无回归
- 四类结论：
  - **已实现并验证（编译 + UI 无回归）**：追加式引用、上限拒绝、不覆盖原草稿
  - **已实现但未验证**：跨 Agent 引用（当前引用只写当前会话草稿，未做来源标注）、8000 边界用例的 UI 测试
  - **未实现**：光标处精确插入（需 TextFieldValue 改造）；引用来源（Host/Agent）标注
  - **已知问题**：引用内容以 `> ` 前缀写入纯文本草稿，发送时会原样发给 Agent（这是用户可见行为，非隐藏副作用）
- commit：见提交
