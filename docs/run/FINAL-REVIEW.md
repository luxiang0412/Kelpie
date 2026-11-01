# FINAL REVIEW — UX-D0 Graphite × Editorial

> 分支：`ux/graphite-editorial`（**未合并 main**，已推送 origin）
> 结论等级：**`FUNCTIONAL_PARTIAL`**

## 1. 任务状态

完整逐项状态见 `PROGRESS.md`（唯一权威）。汇总：**PASS 13 / PARTIAL 8 / SKIPPED 1 / PROPOSAL 1**。

## 2. 已实现并验证的核心能力

1. **设计 Token**：色板（含 `error`、`focusRing`）、字阶（display 26/34、titlePage 18/26、bodyReading 16/25、mdH1–3 21/29·18/26·16/24、code 13/20）、Shape（card 16、codeTool 12、composer 20/18）、对比度实测 5.86–16.30。
2. **工作台**：主机选择器在标题下方、Attention First（blocked 前 2 卡）、done 默认收起、idle/unknown 分区、单行单一语义色、1dp 分隔线、按优先级单条 Banner + 「全部问题」。
3. **对话**：Agent 正文全宽阅读排版、用户气泡 88/86/520dp、组首一次中性标识、Tool 卡（容器/图标/参数·命令·输出/复制/全量阅读页/无嵌套滚动）、Thinking 可选中、Unknown 降级为「暂不支持预览」、消息尾「复制 + 更多」+ 系统长按选择。
4. **Composer**：双层 Surface、2dp focus ring、40dp 圆形 Send（48dp 触达）、1–5 行、Enter 换行、长 Prompt 全屏编辑（取消回退）、离线禁用 Send、Unknown 二次确认 + 「查看发送记录」、IdentityUncertain 告知、≥600dp 720dp 居中。
5. **搜索**（本会话新增）：仅搜索已加载消息、防抖 250ms、n/N 计数、上/下一处定位、常驻边界说明。
6. **i18n**：zh/en 双语资源；Hosts 33 条、Sheet、搜索、工具卡、术语（需要处理/指令已接收/发送结果无法确认）。
7. **减少动效**：读系统 `ANIMATOR_DURATION_SCALE`，关闭时折叠直接 snap。

## 3. 证据

- 测试：AVD `emulator-5556` Compose UI **26/26**；JVM 全绿（`ContrastTest` 38 断言、`MarkdownParserTest` 12、`ConversationPairingTest` 9、`ConversationBranchTest` 5、`IncrementalMapperEquivalenceTest` 7、`MapperCostTest` 2、`MarkdownAstCacheTest` 6、`DraftTest` 15、`WriteLedgerPersistTest` 5）。
- 截图（`docs/evidence/`）：`ux09-p1-board-light`、`ux09-p2-board-dark`、`ux09-p5-320-dark-1.6`、`ux09-p8-600-light`、`ux08b-empty-p1`、`ux08a-hosts`、`ux06-search-open`、`ux04a-v2-composer`、`ux03ab-spacing`、`ux00a-v2-conv-light`、`real-board-spec`（真机，较早）。
- 真机（Redmi 22021211RC / A14）：本轮**离线**，仅保留更早的真机截图；本轮矩阵为 AVD，已在报告中标注来源。

## 4. 未完成（诚实清单）

| # | 项 | 影响 |
| --- | --- | --- |
| 1 | 搜索**命中高亮** | 用户体验：只知道跳到哪，看不到高亮 |
| 2 | **≥600dp 内容居中**（除 Composer） | P8 截图已暴露：看板仍是整宽左对齐 |
| 3 | 真机 P1–P8 全矩阵 + TalkBack + 中文 IME | 无法宣称真机通过 |
| 4 | 通知文案 / 部分 `contentDescription` 未本地化 | 英文模式下可能残留中文 |
| 5 | 草稿归属选择 UI（IdentityUncertain 的「恢复/放弃」） | 目前只有告知 + 发送前校验 |
| 6 | 对话/Hosts 空态未迁到共享 `EmptyState` | 一致性 |
| 7 | `UX-07b` 语音 | SKIPPED（无本地识别路径） |
| 8 | 真机 FrameTimingMetric | 仍为 0（MIUI 限制未变），不宣称性能 |

## 5. 需要你终审的 5 个点

1. **深色 Token**（`#10151D/#181F2A/#222B39`）与 `attention` 新色；
2. **主机选择器在标题下方**（`BOARD-HOST-001`，仍是 Proposed）；
3. **Composer 圆形 Send（↑）** 是否比你原来的「发送」文字按钮好；
4. **工具卡**的容器/边框/图标密度是否合适；
5. **320dp/1.6** 下看板标题与 Refresh 同行的排版是否可接受（P5 截图）。

## 6. 回滚

按任务粒度 `git revert <sha>`；分支未合并 main，最省事是直接丢弃分支。
