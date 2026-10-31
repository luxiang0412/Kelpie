# UX-04b report — Composer 长 Prompt / 草稿 / 发送异常入口

- baseline：`017a24e` / `ux/graphite-editorial`
- inspected：`ComposerBar`、`LongPromptEditor`、`HostConnectionManager.sendPrompt`、`DraftStore`、`WriteLedger`
- goal：复用既有可靠输入行为，只补视觉与缺失的分支；不改草稿/账本语义
- changed：
  - `LongPromptEditor` 新增**「取消」**：回到打开编辑前的快照（`snapshot`），不保留半成品、不发送；「完成」仍只写回本地草稿
  - 其余保持：`DraftStore.MIGRATION`、`DraftKey` 隔离、Unknown `pendingText/pendingActionId` + 二次确认、`SendTargetCheck` NeedsConfirm
  - Composer 附近仍显示 Unknown 提示行 + 「重新发送…」二次确认（既有实现，未改语义）
- tests：`:app:assembleDebug` OK；AVD 全量 UI **26/26**（ComposerUiTest 7 项含 Unknown 二次确认、Rejected 保留内容、发送中禁用）
- 四类结论：
  - **已实现并验证**：取消回退、完成不发送、Unknown/Rejected/Accepted 三态不回归
  - **已实现但未验证**：Unknown 跨进程恢复后 Composer 的呈现（数据层已有 `restore()`，UI 未在杀进程场景复测）
  - **未实现**：Composer 内直接跳转「待确认操作」的快捷入口（当前走工作台 Banner 或技术信息 Sheet）
  - **已知问题**：长编辑器的「取消/完成」在 320dp/1.6 下未截图验证
- commit：见提交
