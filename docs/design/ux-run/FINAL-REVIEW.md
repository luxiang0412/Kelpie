# FINAL REVIEW — UX-D0 Graphite × Editorial（本轮实际达成）

> 分支：`ux/graphite-editorial`（**未合并 main**）｜基线：`f56430c`
> 结论等级：**`FUNCTIONAL_PARTIAL`** —— 构建通过、核心安全回归通过、部分任务完成并验证；`UX-03b…UX-09` 未实施。

## 1. 任务状态总表

| Task | 状态 | Commit | 证据 |
| --- | --- | --- | --- |
| UX-00 审计/基线 | PASS | `704365c` | `00-baseline.md`、`UX-00-AUDIT.md`、`PROGRESS.md` |
| UX-01a 对话顶栏大字体裁剪（I-93） | PASS | `48a94b1` | 真机 fontScale 1.3 截图 `ux01a-after-font13.png` |
| UX-01b InlineCode 保真 | PASS | `128632a` | `InlineCodeFidelityTest` 2/2 |
| UX-00a Tokens/对比度/图标契约 | PASS | `6b10196`、`7cc5112` | `ContrastTest` 34 断言；4 个矢量图标；字形图标清零 |
| FIX-01 未捕获 SSH 异常致崩溃 | PARTIAL | `7cc5112` | 证据 `ux-crash-evidence.txt`；**设备侧未复现验证** |
| UX-02a 工作台顶栏/主机选择器 | PASS | `e19e731` | 真机 P1/P2 + AVD P5/P6 截图 |
| UX-02b Attention First / done 折叠 | PASS | `309e3a1` | `BOARD-DONE-001` 已落实；AVD P1/P2 |
| UX-02c 单条 Banner / Unknown 入口 | PASS | `8d4d3ee` | `BoardBannerTest` 5 项；AVD UI 23/23 |
| UX-03a 对话正文/身份头/用户气泡 | PARTIAL | `edfaf40` | AVD P1/P2 截图；**UI 回归未重跑** |
| UX-03b Markdown/CodeBlock 视觉 | TODO | — | — |
| UX-03c Tool/Thinking/长输出 | TODO | — | — |
| UX-03d 复制/系统选择/更多（CONV-ACTIONS-001） | TODO | — | — |
| UX-03e 阅读位置/新消息入口 | TODO | — | — |
| UX-04a Composer 轻量双层 Surface | TODO | — | — |
| UX-04b 长 Prompt/草稿/发送异常 | TODO | — | — |
| UX-04c 引用消息 → Composer | TODO | — | — |
| UX-05a i18n 资源梳理（zh/en） | TODO | — | — |
| UX-05b App 内语言设置 | TODO | — | — |
| UX-06 会话内搜索 | TODO | — | — |
| UX-07a STT Spike | TODO | — | — |
| UX-07b STT 实施 | TODO | — | — |
| UX-08a 主机/设置视觉一致性 | TODO | — | — |
| UX-08b 空态/异常态/Motion | TODO | — | — |
| UX-08c 品牌图标候选 | TODO | — | — |
| UX-09 全局 QA / 性能回归 | TODO（部分由本文件承担） | — | — |

**未完成原因（如实）**：单次会话内可完成的实现+测试+截图+报告闭环数量有限（本会话完成 9 个）；剩余 15 个包需要下一轮继续。**不是**设备或权限阻断。

## 2. 测试证据（实际执行）

| 命令 | 结果 |
| --- | --- |
| `gradle --no-daemon :app:assembleDebug` | BUILD SUCCESSFUL（多轮） |
| `gradle --no-daemon :feature:board:testDebugUnitTest` | BUILD SUCCESSFUL（含 `ContrastTest` 34 断言、`MarkdownParserTest` 12、`IncrementalMapperEquivalenceTest` 7、`MapperCostTest` 2、`MarkdownAstCacheTest` 6、`ConversationPairingTest` 9、`ConversationBranchTest` 5） |
| `gradle --no-daemon :core:data:testDebugUnitTest` | BUILD SUCCESSFUL（`DraftTest` 15） |
| `gradle --no-daemon :core:herdr:test` | BUILD SUCCESSFUL（含 `WriteLedgerPersistTest` 5） |
| AVD `emulator-5556` `am instrument` 全量 | **OK (23 tests)**（看板 9 + Composer 7 + InlineCode 2 + BoardBanner 5） |
| `:core:data` androidTest `DraftMigrationTest` | OK (1 test) |

## 3. 截图目录（全部实际拍摄，来源已标注）

| 文件 | 来源 | 内容 |
| --- | --- | --- |
| `spike/results/ux01a-after-font13.png` | **真机** 1080×2400, fontScale 1.3, Light | 顶栏单行不裁剪 |
| `spike/results/ux01b-inline-code.png` | **真机** | 行内码无空格 padding |
| `spike/results/ux00a-board-dark.png` / `ux00a-conv-dark.png` | **真机** | 新深色 token |
| `spike/results/ux00a2-conv-light.png` | **真机** | 矢量图标（全屏）、顶栏 |
| `spike/results/ux02a-p1-board-light.png` / `ux02a-p2-board-dark.png` | **真机**（重连中状态） | 主机选择器下移 + 分隔线 |
| `spike/results/ux02a-p5-320-dark-1.6.png` / `ux02a-p6-320-light-1.6.png` | AVD 320dp/1.6 | 标题两行、刷新可见 |
| `spike/results/ux02b-avd-p1.png` / `ux02b-avd-p2.png` | AVD | done 收起、单一语义色 |
| `spike/results/ux03a-avd-p1.png` / `ux03a-avd-p2.png` | AVD | 分组表头、代码块（`kotlin` 不再「未闭合」、复制、横向滚动、展开 24 行） |

## 4. 本轮发现的真实缺陷（都已修 + 记录）

| # | 缺陷 | 处置 |
| --- | --- | --- |
| I-93 | 大字体下对话顶栏裁剪并压住首条消息 | ✅ UX-01a 修复（真机截图） |
| I-95 | SSH 断链未捕获异常 → 三次进程 FATAL | ⚠️ 已加 `CoroutineExceptionHandler` + `runCatching`；**设备侧未验证** |
| I-96 | 我关闭真机 WiFi 导致 adb 离线（操作事故） | 已由用户重开无线调试恢复（`192.168.1.8:38805`） |
| — | 代码块折叠预览显示「未闭合」 | ✅ UX-03a 修复（补闭合 fence） |
| — | 滚动时闪现原始 Markdown（``` 可见） | ✅ UX-03a 修复（≤2k 同步解析） |
| — | `BOARD-DONE-001`（已批准：已完成默认收起）实际未落实 | ✅ UX-02b 修复 |
| — | 行内彩色点 + 彩色状态字 + 彩色 chevron 三重语义色 | ✅ UX-02b 修复（只留一处） |

## 5. 仍需用户做的事

1. **视觉终审**（3 个 `PROVISIONAL` 默认值，我按任务书取的暂定值）：
   - 深色 token（`#10151D/#181F2A/#222B39`）；
   - 大字体时**隐藏对话副标题**（换行省空间）；
   - 主机选择器放在标题下方（`BOARD-HOST-001`）。
2. **真机验收补拍**：设备当时在锁屏（`mDreamingLockscreen=true`），UX-02b/03a 的真机 P1/P2 未拍到，只有 AVD 版本。
3. **继续实施**：说「按 RESUME-PLAN 继续」即可从 `UX-03b` 接上。

## 6. 下一步顺序（不变）

`UX-03b → UX-03c → UX-03d → UX-03e → UX-04a → UX-04b → UX-04c → UX-05a → UX-05b → UX-06 → UX-07a → UX-07b → UX-08a → UX-08b → UX-08c → UX-09`

每包固定闭环：`rg 确认符号 → 最小实现 → 针对性 JVM/AVD UI → 截图 → 四类报告 → 单包 commit`。
