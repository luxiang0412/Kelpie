# M2-C1 报告 — 增量渲染、消息正确性与测试基础设施

> 基线：`b0ece0a` / `f9d76f7`（M2-B）｜设备：Redmi 22021211RC（MIUI/A14）+ 新隔离 AVD `herdr_m2c1`（Android 34）
> 关联：[INCREMENTAL-RENDERING.md](INCREMENTAL-RENDERING.md)、[BENCHMARK-INFRA-REPORT.md](BENCHMARK-INFRA-REPORT.md)

## 0. 摘要

| 验收条件 | 状态 |
| --- | --- |
| 1. 普通追加不再全量解析/映射 | ✅ 已实现并验证（JVM 21.9× 更快，mapped=1） |
| 2. 已有消息更新只影响必要条目 | ✅ PATCH 模式 + 测试 |
| 3. 错误 ToolResult 不被静默配对 | ✅ **彻底移除位置猜测**，改为独立条目 |
| 4. Pi 分支切换/恢复正确 | ✅ `ConversationBranch` + 测试 |
| 5. 增量结果与 Reference Mapper 一致 | ✅ 12 个随机种子 × 120 步 + 9 个 upsert 种子 + 分支/会话切换 |
| 6. 长会话缓存有失效与内存上限 | ✅ `MarkdownAstCache` LRU 256 + 版本号 |
| 7. 既有 JVM 单测通过 | ✅ 全部通过（含新增） |
| 8. 能有真正执行 Compose UI 测试的环境 | ✅ **新建隔离 AVD，9/9 通过** |
| 9. 测试数据与正式数据隔离 | ✅ Fixture 仅 debug/benchmark 源集，不写 Room/不连 SSH |
| 10. 未破坏 SSH/Herdr/输入/重连 | ✅ 未改动协议层与连接层代码 |

---

## 1. 已实现并验证

### 1.1 增量映射（`IncrementalConversationMapper`）

- APPEND / PATCH / FULL 三模式，`stats.mode` 可断言；
- 追加 1 条 → `mapped == 1`；追加 `TOOL_RESULT` → `mapped == 0`（只原位替换条目）；
- 2000 条历史 + 100 次追加：**全量 55.1 ms vs 增量 2.5 ms（21.9×）**；
- 历史规模 200 → 2000（10×）：全量 2.7 → 26.6 ms（线性增长），增量 0.1 → 0.9 ms（基本不变）。
  > 口径：JVM 微基准（`MapperCostTest`），单机、无 GC 控制，**不是** UI 帧指标。

### 1.2 Tool 关联正确性（P0 §3）

`ConversationResolve` 成为唯一真相：
- 只有 `toolCallId` 相等才 `pairedById = true`；
- id 不匹配 / 无 id 的结果 → 独立条目（`unmatchedResultId_isStandalone_notSilentlyPaired`、
  `resultWithoutAnyId_neverAttachesToNeighbouringCall`）；
- 结果先于调用到达 → 在调用位置合并（`resultBeforeCall_pairsAtCallPosition`）；
- 并行调用互不串号（`parallelCalls_keepSeparateResults`）；
- 重复结果取最新、重复事件丢弃（`duplicateResult_usesLatest`、`duplicateEventId_isDropped`）。

### 1.3 分支与会话

- `ChatMessage` 增加 `id` / `parentId`，`PiTranscriptParser` 从 JSONL 提取；
- `ConversationBranch.effective()` 从最后事件回溯 `parentId`，环/悬空父节点安全终止；
- 分支切换后旧分支 ToolResult 不会挂到新分支调用上（`branchSwitch_doesNotLeakResultsAcrossBranches`）；
- `sessionKey` 变化 → 清空派生状态并 FULL 重建（`sessionSwitch_discardsPreviousState`）。

### 1.4 缓存与内存（§4）

- `MarkdownAstCache`：内容寻址（FNV-1a + 长度 + `PARSER_VERSION`），LRU 256 条，
  >120k 字符不入缓存；测试覆盖命中、内容变更不返回旧 AST、淘汰上限、版本号。
- `remember(text)` 仅作帧内快速路径；解析结果由缓存层持有。

### 1.5 测试基础设施（P1）

**新 AVD（隔离，不覆盖用户已有 AVD）**：`herdr_m2c1`（`system-images;android-34;google_apis;x86_64`），
headless 启动 50 s，`pm list packages` 正常，`adb install` 正常。

**Compose UI 状态测试：9/9 通过**（`emulator-5556`，26 s）：

```
OK (9 tests)
```

**Macrobenchmark 流程可执行**（模拟器）：

```
ConversationBenchmark_startup_500
  timeToInitialDisplayMs [min 2841.7, median 2869.6, max 3102.0]
  OK (1 test) + 5 份 Perfetto trace
```

> ⚠️ 这是**模拟器 + debuggable + `suppressErrors=DEBUGGABLE,EMULATOR`** 的结果，
> 仅证明流程可运行，**不得**作为性能结论。

### 1.6 UI 截图

`spike/results/m2c1-conversation-dark.png`（Markdown 标题/列表/引用/行内码/链接/代码块，深色）。

---

## 2. 已实现但未验证

| 项 | 缺什么 |
| --- | --- |
| 真实 pi 流式增量 | 未用真实会话验证「追加时只映射新条目」；Fixture 是静态列表 |
| 分支切换的真实样本 | 单测覆盖了人造分支；真实 pi 分支切换未跑 |
| 长会话真机内存曲线 | 只有 LRU 上限与单元测试，未做真机 heap 采样 |
| 「新消息」提示 | 需要真实增量触发，未验证 |
| IME / 滚动 | 未在真实会话中验收 |

---

## 3. 未实现

- Benchmark 的确定性增量场景（追加 100 条 / 更新 100 次 / 100 次 ToolResult 更新 / 分支切换）
  尚未写成 `measureRepeated` 用例（当前只有 `startup_500` + 3 个滚动用例在真机被 MIUI 拦截、模拟器未跑滚动）；
- 真机 `FrameTimingMetric`（MIUI：BAL + `dropShaderCache`）；
- Markdown 表格 / 图片 / 嵌套列表。

---

## 4. 已知问题

| # | 问题 | 状态 |
| --- | --- | --- |
| I-74 | MIUI 禁止 instrumentation 启动 Activity | **仍存在**，但已用新 AVD 绕过（真机不受影响） |
| I-75 | 旧 emulator-5554 package 服务损坏 | ✅ 已绕过：新建 `herdr_m2c1`，旧 AVD 未删除 |
| I-78 | MIUI `dropShaderCache` 失败 | 仍存在（真机 Macrobenchmark） |
| I-79 | 全量映射 O(n) | ✅ **已修复**（本报告 §1.1） |
| I-81（新） | `ConversationBranch` / `map` 仍每次 O(n) | 实测 2000 条约 0.9 ms；若后续成为瓶颈再做增量分支 |
| I-82（新） | 3 个 UI 测试曾因**测试代码**假设失败 | ✅ 已修（合并语义需 unmerged tree、文案变更、重复文本节点）；非产品 Bug |

### 关于测试失败的性质

首轮 9 个测试有 3 个失败，逐一判定后**均为测试代码问题**，不是产品 Bug：

1. `doneAndIdle_areDistinctLabels`：行是 `mergeDescendants` 语义，状态词需 `useUnmergedTree`；
   且「已完成」同时是分区标题 → 改用节点集合断言。
2. `staleData_isLabelledAsCache`：文案已从「上次同步 1 分钟前」改为动态 `timeAgo()`。
3. `tappingAgent_opensConversation`：点击目标应为行的合并 `contentDescription`。

产品侧无改动来迎合测试（未删除语义合并、未改文案）。

---

## 5. 下一阶段（M2-C2）建议

1. 把 `ConversationBranch` + `map` 的 O(n) 也做增量（若真机长会话验证确有帧损失）；
2. Benchmark 补齐增量场景（追加/更新/ToolResult 批量/分支切换/展开折叠/滚动+新消息）；
3. 在非 MIUI 真机或 CI emulator 上取 `FrameTimingMetric`，与本轮 JVM 微基准分开记录；
4. 真实 pi 会话录屏验收流式增量、新消息提示、分支切换；
5. （仍不做）Composer 重构、跨主机聚合。
