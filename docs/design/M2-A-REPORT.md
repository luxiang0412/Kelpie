# M2-A 报告 — 性能基线与交互正确性

> 日期：2026-10-31｜设备：Redmi 22021211RC / Android 14 / 1080×2400 / 120 Hz
> 基线提交：`f657f1a`（M0+M1）→ 本轮提交见 git log

## 0. 结论摘要

| 交付项 | 状态 |
| --- | --- |
| Benchmark 模块（Macrobenchmark） | ✅ 已建、可编译/安装/启动；❌ **设备层被 MIUI 拦，未产出 FrameTimingMetric** |
| 100/500/2000 条 Fixture | ✅ 已实现（debug-only，确定性生成，不写 Room） |
| 真实设备性能基线 | ✅ **用 `am start -W` + 固定 `gfxinfo` 协议**得到（非 Macrobenchmark） |
| 长列表瓶颈分析 | ✅ 代码级 + fixture 数据（首帧随消息量几乎不变，长文本折叠后首帧减半） |
| 看板状态文字 / 无障碍 | ✅ 已实现并截图验证 |
| 核心 UI 状态测试 | ✅ 已写 9 个；❌ **本机无法执行**（MIUI BAL 阻断），模拟器 package 服务损坏 |
| 深色/浅色截图 | ✅ `m2a-board-light.png` / `m2a-board-dark.png` |
| 文档更新 | ✅ 本文件 + RESULTS/DECISIONS/ISSUES |

**一句话**：产品代码与测试代码都写好了，但**这台 MIUI 设备禁止 instrumentation 启动任何 Activity**（`MIULOG Permission Denied Activity`），同时禁用 `dropShaderCache`，导致 Macrobenchmark 与 Compose UI 测试都无法执行；改用 `adb shell am start` 的 Fixture 页面完成了可比的**产品级**基线。

---

## 1. 已实现并验证

### 1.1 看板可访问性（状态文字）

- `CompactAgentRow` 现在 **状态词 + 标题同行**（需要你/运行中/已完成/空闲/状态未知），不再只靠色点；
- 行最小高度 **56 dp**（≥48 dp 触达），`semantics(mergeDescendants = true)` 合并朗读「状态 + agent + 标题 + 项目」；
- 对比度：状态前景色取 Quiet Command 色板（报告实测最低 5.41:1），浅/深色均验证。

证据：`spike/results/m2a-board-light.png`、`m2a-board-dark.png`。

### 1.2 Fixture 数据集（确定性）

`app/src/debug/.../FixtureActivity.kt`：

```bash
adb shell am start -n dev.herdr.app/.FixtureActivity --ei count 500
```

- 消息按 `i % 8` 轮转：user / assistant(短) / thinking / toolCall / toolResult / 长文本 / 长代码块 / 长路径；
- 中英混排、固定 ID（index 派生）、无随机数；**不连接远端、不写 Room**；
- 仅存在于 `debug` 源集，release 包不包含。

### 1.3 产品级基线（`am start -W` + 固定脚本）

| 指标 | 100 条 | 500 条 | 2000 条 |
| --- | --- | --- | --- |
| 首帧 `TotalTime`（优化前） | 1577 ms | 1529 ms | 1583 ms |
| 首帧 `TotalTime`（优化后） | **787 ms** | **786 ms** | **834 ms** |
| 滚动 jank（优化前，10×swipe） | 28.5% | 25.5% | 36.0% |
| 滚动 jank（优化后） | *不可比*（内容折叠导致滚动距离变化） | 同左 | 同左 |

- 冷启动主界面（release，M1 记录）：中位 **379 ms**；本轮未回归（`manual reconnect` 复测 = 0）。
- 证据：`m2a-fixture-start.txt`、`m2a-fixture-start-after.txt`、`m2a-scroll-baseline.csv`、`m2a-scroll-optimized.csv`。

### 1.4 本轮性能修复（范围可控）

**assistant 长文本/长代码默认折叠 + 「展开全文」**（`ConversationScreen.AssistantMessage`）：

- 阈值：`text.length > 600` 或行数 > 14；
- 效果：首帧 **1577→787 ms（-50%）**、500 条 **1529→786 ms**、2000 条 **1583→834 ms**（同设备、同 Fixture、同 `am start -W` 协议）；
- **滚动 jank 不能用于前后对比**：折叠后列表总高度变小，同样 10 次 swipe 覆盖的内容不同（I-63 再次印证）——因此**不宣称**滚动优化成功。

---

## 2. 已实现但未验证

### 2.1 `:benchmark`（Macrobenchmark）

- 新模块 `benchmark/`（`com.android.test`，`targetProjectPath = ":app"`，`benchmark-macro-junit4:1.3.4`）；
- 4 个用例：`scroll_100/500/2000`（`FrameTimingMetric` + `StartupTimingMetric`，5 iterations，`StartupMode.COLD`）、`startup_500`；
- 可直接跑：`am instrument -w -e androidx.benchmark.suppressErrors DEBUGGABLE -e class dev.herdr.benchmark.ConversationBenchmark#startup_500 dev.herdr.benchmark/androidx.test.runner.AndroidJUnitRunner`。

**未能产出数据的三层原因（逐层实测）**：

1. `CompilationMode.Partial()` 需要目标 APK 集成 `profileinstaller` → 报 `baseline profile install broadcast was not received` → 改为 `CompilationMode.None()`；
2. 目标 App 只有 debug 变体（`FixtureActivity` 在 debug 源集）→ `Debuggable` 配置错误 → 用 `suppressErrors=DEBUGGABLE` 显式抑制（**精度降级，已在结论中标注**）；
3. **MIUI 设备层拦截**：`dropShaderCache` 失败（`MacrobenchmarkScope.dropShaderCache`），且 UiAutomator 启动 Fixture 时被 `MIULOG Permission Denied Activity` 拒绝。

### 2.2 Compose UI 状态测试（9 个）

`app/src/androidTest/.../BoardScreenTest.kt`，覆盖：

| 用例 | 覆盖点 |
| --- | --- |
| `blocked_isProminentAndFirst` | blocked 优先展示 + 「需要你」文案 |
| `doneAndIdle_areDistinctLabels` | 已完成 vs 空闲不混淆 |
| `allIdle_stillShowsRows` | 全空闲时列表不空 |
| `offlineHost_showsCachedBannerAndRetry` | 断线 + 重试回调 |
| `reconnecting_bannerIsShown` | 自动重连中状态 |
| `awaitingHostKey_bannerIsShown` | 首次连接待确认指纹 |
| `staleData_isLabelledAsCache` | 缓存不得伪装成实时 |
| `tappingAgent_opensConversation` | 点击直达对话（回调） |
| `approvalCard_allowsDecision` | 审批卡片决策回调（不自动批准） |

**无法执行的原因（已定位到具体位置，不是产品 bug）**：

- 测试宿主改为 app 包内 `TestHostActivity`（debug）后仍然失败：MIUI 日志
  `MIULOG- Permission Denied Activity : cmp=dev.herdr.app/.TestHostActivity ... result code=102`
  → 系统拒绝**后台启动 Activity**（BAL），`ActivityScenario` 永远等不到 RESUMED，随后
  `BLASTSyncEngine: ... never received commit callback. Application ANR likely to follow.`
- 模拟器（`emulator-5554`）尝试运行：`cmd: Can't find service: package`（系统服务损坏，重启无效）。

### 2.3 长文本折叠对“阅读体验”的影响

折叠对首帧有效，但**未做真实用户可读性验证**；展开交互只在 Fixture 上点过，未在真实会话中验证。

---

## 3. 未实现（本轮范围外）

| 项 | 原因 | 下一步 |
| --- | --- | --- |
| 500/2000 条 `FrameTimingMetric` | 设备禁止 instrumentation 启动 Activity | 非 MIUI 设备 / CI 上跑 `:benchmark` |
| IME 弹出/收起、增量追加、展开折叠的帧指标 | 同上 | 同上（Fixtures 已支持 count，需加 IME/append 用例） |
| Perfetto trace 原始文件 | 同上 | 同上（`benchmark` 默认输出 trace） |
| Markdown / 代码高亮 / Diff | 明确留给 M2-B | M2-B |
| 跨主机聚合 | 明确留到后续 | — |

---

## 4. 已知问题（本轮新增）

| # | 问题 | 证据 | 影响 |
| --- | --- | --- | --- |
| I-74 | MIUI 禁止 instrumentation 启动 Activity（BAL） | logcat `MIULOG- Permission Denied Activity` + ANR 提示 | UI 测试、Macrobenchmark 在本机全部无法执行 |
| I-75 | 模拟器 `package` 服务缺失 | `cmd: Can't find service: package`（重启无效） | 无法用模拟器替代 |
| I-76 | `CompilationMode.Partial()` 需 app 集成 `profileinstaller` | benchmark 报 `baseline profile install broadcast was not received` | 需在 app 加 `androidx.profileinstaller` 或用 `None()` |
| I-77 | 内容变化后 `gfxinfo` 滚动数据不可比 | 折叠后帧数 344→252（同样 swipe 次数） | 前后对比必须固定内容；否则只能比较“首帧” |

---

## 5. 下一步（M2-B 建议）

1. **先把 benchmark/UI 测试跑到能出数**：在非 MIUI 设备或 CI（GitHub Actions 上的 emulator）跑 `:benchmark`，并给 app 加 `profileinstaller` 以便用 `CompilationMode.Partial()`；
2. 补 benchmark 用例：IME 弹出、增量追加、thinking/tool 展开折叠、看板→对话→返回；
3. 用同一套 Fixture 做 M2-B Markdown 渲染的 before/after（**固定内容**，避免本轮的可比性问题）；
4. 保持本轮“长文本折叠”作为默认体验，并按 M2-B 的渲染分级决定展开策略。
