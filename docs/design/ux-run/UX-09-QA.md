# UX-09 — 全局综合 QA（在会话预算内能完成的部分）

## 1. 决策表逐项核对

| ID | 决策 | 实现状态 |
| --- | --- | --- |
| `BOARD-DONE-001` 已完成默认收起 | ✅ 已落实（UX-02b，`showDone = false`） |
| `CONV-ACTIONS-001` 消息末尾「复制+更多」、正文长按系统选择 | ✅ 已落实（UX-03d，`SelectionContainer` + `MessageActions`） |
| `CMP-VOICE-001` Mic 可用时常显 | ⏭️ 未实现（UX-07a 结论：不做，不放假入口） |
| `SEC-WRITE-001` Accepted/Rejected/Unknown，Unknown 不自动重发 | ✅ 保持（未改语义；`autoRetryAllowed` 恒 false） |
| `SEC-APPROVAL-001` 不做读屏一键审批 | ✅ 未引入 |
| `CONV-TOOL-001` Tool 只用可靠 ID 关联 | ✅ 保持（`ConversationResolve`，未匹配→独立条目） |
| `BOARD-HOST-001` 主机选择器在标题下方 | ✅ 已实现（**PROVISIONAL，待视觉终审**） |
| `CMP-STYLE-001` 轻量双层 Surface，Send 唯一强调 | ✅ 已实现（**PROVISIONAL**） |
| `CMP-QUOTE-001` 引用优先光标，否则追加 | ✅ 采用**追加到末尾**方案并记录（UX-04c） |
| `CONV-TOOL-002` Tool 有限预览 + 完整输出 | ⚠️ 只做预览；数据源无完整输出时**不提供**假入口 |
| `CONV-ANCHOR-001` 稳定锚点 + 像素偏移 | ✅ 已实现（UX-03e，`paneId-index + offset`）；`Resyncing` 状态未实现 |
| `BRAND-ICON-001` Focus Orbit | ⚠️ 仅方案（UX-08c），未替换资产 |

## 2. 回归结果（本轮实际执行）

| 命令 | 结果 |
| --- | --- |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `:feature:board:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:data:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:herdr:test` | BUILD SUCCESSFUL |
| AVD `emulator-5556` 全量 UI | **OK (26 tests)**（在 **en-US** 语言下运行） |
| `:core:data` `DraftMigrationTest` | OK (1 test) |

## 3. 隐私 / Secrets 检查

- 未提交任何 SSH 私钥、known_hosts、真实对话、草稿内容；截图只含 Fixture 假数据与 `herdr-gui-research` 项目名。
- `spike/results/ux-crash-evidence.txt` 已脱敏（只保留异常类型与线程名）。
- release 变体不含 `FixtureActivity`（debug 源集）；`benchmark` 变体按 `sourceSets` 显式引入。
- 崩溃兜底日志只输出异常**类名**，不输出主机/路径/内容。

## 4. 性能限制（不夸大的部分）

- 真机 MIUI：`instrumentation` 被 BAL 拒绝（I-74）、`dropShaderCache` 失败（I-78）→ **真机 FrameTimingMetric 仍为 0**。
- 本轮所有性能相关结论**只有** JVM 微基准（Mapper 21.9×、增量 mapped=1）与 `am start -W` 首帧，
  且本轮未重测首帧，**不宣称**任何滚动性能改善。

## 5. 未验证清单（必须如实）

1. 真机：P1–P8 全量截图（本轮全部来自 AVD；真机仅在 UX-02a 拍到 P1/P2）；
2. TalkBack 朗读顺序与数量朗读；
3. 中文 IME 组合输入、光标/选区、粘贴长堆栈；
4. 手势导航下 Composer Insets 双倍留白；
5. 语言切换后的状态保留（草稿/Unknown/锚点）；
6. 上滑不被拉回、点「N 条新消息」回到底部；
7. `reduce motion` 行为。

## 6. 结论等级

**`FUNCTIONAL_PARTIAL`**：分支可构建、测试全绿、安全红线未破；视觉任务完成 8/12，
`UX-03e`/`UX-04c`/`UX-05a`/`UX-05b`/`UX-06`/`UX-08a`/`UX-08b` 为 PARTIAL，`UX-07b` SKIPPED。
**不得合并 main**，需用户视觉终审后再决定。
