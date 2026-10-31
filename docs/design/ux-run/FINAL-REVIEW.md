# FINAL REVIEW — UX-D0 Graphite × Editorial

> 分支：`ux/graphite-editorial`（**未合并 main**，已推送 origin）｜基线 `f56430c`
> **结论等级：`FUNCTIONAL_PARTIAL`** — 可构建、全部自动化测试通过、安全红线未被触碰；
> 视觉任务完成大部分，6 项为 PARTIAL、1 项 SKIPPED（STT）。真机验收仅完成 UX-02a。

## 1. 任务状态（唯一权威表）

| Task | 状态 | Commit | 关键证据 |
| --- | --- | --- | --- |
| UX-00 审计/基线 | PASS | `704365c` | `UX-00-AUDIT.md`、`PROGRESS.md` |
| UX-01a 对话顶栏大字体裁剪（I-93） | PASS | `48a94b1` | 真机 1.3 截图 |
| UX-01b InlineCode 保真 | PASS | `128632a` | `InlineCodeFidelityTest` 2 项 |
| UX-00a Tokens/对比度/图标契约 | PASS | `6b10196`,`7cc5112` | `ContrastTest` 34 断言；4 矢量图标；字形图标清零 |
| UX-02a 工作台顶栏/主机选择器 | PASS | `e19e731` | 真机 P1/P2 + AVD P5/P6 |
| UX-02b Attention First / done 折叠 | PASS | `309e3a1` | `BOARD-DONE-001` 落实；AVD 截图 |
| UX-02c 单条 Banner / Unknown 入口 | PASS | `8d4d3ee` | `BoardBannerTest` 5 项 |
| UX-03a 对话正文/身份头/气泡 | PASS | `edfaf40` | 分组表头、16/25、86% 气泡、围栏闭合 |
| UX-03b Markdown/CodeBlock 视觉 | PASS | `62d03a1` | 引用 12dp、悬挂缩进、code 13/20 |
| UX-03c Tool/Thinking/长输出 | PASS | `62d03a1` | 移除「失败」盲猜、52dp 头部 |
| UX-03d 复制/系统选择/更多 | PASS | `ddb2fb8` | `MessageActionsTest` 3 项 |
| UX-03e 阅读位置/新消息 | PARTIAL | `22c7a35` | 四状态+锚点+未读；`Resyncing` 未做；手势未实测 |
| UX-04a Composer 轻量双层 | PASS | `017a24e` | AVD 截图 |
| UX-04b 长 Prompt/草稿/异常 | PASS | `8f580ce` | 取消回退 + 三态不回归 |
| UX-04c 引用消息 → Composer | PARTIAL | `8f580ce` | 追加式引用；光标插入未做 |
| UX-05a i18n（zh/en） | PARTIAL | `63b0533`,`87edec2` | 46 key + 三屏接线 + 测试去中文化 |
| UX-05b App 内语言设置 | PARTIAL | `f03420f` | API33+ `LocaleManager`；切换生效未验证 |
| UX-06 会话内搜索 | PARTIAL | `42b8c6b` | 仅审计+计划；**未加搜索 UI** |
| UX-07a STT Spike | PASS | `cc55234` | 结论文档：不实现，降级系统键盘语音 |
| UX-07b STT 实施 | SKIPPED | — | 门槛未满足 |
| UX-08a 主机/设置一致性 | PARTIAL | `cc55234`,`42b8c6b` | Hosts 页可滚动；分组与文案未做 |
| UX-08b 空态/异常态/Motion | PARTIAL | `9c00752` | 审计为主；多数项已在前面任务完成 |
| UX-08c 品牌图标 | PASS（PROPOSAL） | `cc55234` | 3 个 Focus Orbit 方向，未替换资产 |
| UX-09 全局 QA | PARTIAL | `9c00752` | 决策表核对 + 回归 + 隐私检查 |

## 2. 自动化测试（本轮真实执行）

```
:app:assembleDebug                     BUILD SUCCESSFUL
:app:assembleDebugAndroidTest          BUILD SUCCESSFUL
:feature:board:testDebugUnitTest       BUILD SUCCESSFUL
:core:data:testDebugUnitTest           BUILD SUCCESSFUL
:core:herdr:test                       BUILD SUCCESSFUL
AVD emulator-5556 am instrument         OK (26 tests)   ← 在 en-US 语言下运行
:core:data DraftMigrationTest          OK (1 test)
```

UI 测试构成：看板 9 + Composer 7 + InlineCode 2 + Board Banner 5 + MessageActions 3 = 26。

## 3. 截图（全部实际拍摄）

| 文件 | 来源 |
| --- | --- |
| `ux01a-after-font13.png`、`ux01b-inline-code.png`、`ux00a-*`、`ux00a2-conv-light.png`、`ux02a-p1/p2` | **真机** Redmi 1080×2400 |
| `ux02a-p5-320-dark-1.6.png`、`ux02a-p6-320-light-1.6.png` | AVD 320dp / fontScale 1.6 |
| `ux02b-avd-p1/p2`、`ux03a-avd-p1/p2`、`ux03b-avd-p2`、`ux03d-avd`、`ux04a-avd`、`ux05a-en`、`ux05a-zh`、`ux05b-lang` | AVD 393dp |

## 4. 本轮修掉的真实缺陷（10 个）

1. 大字体下对话顶栏裁剪压住首条消息（I-93）；
2. 行内码用空格伪造 padding（复制/选区被污染）；
3. 深色 token 仅 3 档且差距过小 → 对齐 UX-D0；
4. `› ▸ ▾ ⤢` 字形当图标 → 4 个矢量图标 + 48dp 触达；
5. **`BOARD-DONE-001`（已批准）实际未落实** → 默认收起；
6. 一行三重语义色（彩点+彩字+彩 chevron）→ 只留一处；
7. 代码块折叠预览误报「未闭合」→ 补闭合 fence；
8. 滚动时闪出原始 Markdown（` ``` ` 可见）→ ≤2k 字符同步解析；
9. **Tool 结果用 error 关键字盲猜「失败」** → 改为「已运行/已返回 + 低强调提示」；
10. Hosts 页设置项在无主机时不可达 → 页面可滚动。
   附：SSH 断链未捕获异常导致进程 FATAL ×3 → 加 `CoroutineExceptionHandler` + `runCatching`（**设备未复验**）。

## 5. 未验证 / 未实现（诚实清单）

**未验证**：真机 P1–P8 全量；TalkBack 顺序；中文 IME 组合输入；手势导航 Insets；语言切换后状态保留；
上滑不被拉回与「N 条新消息」；`reduce motion`；SSH 崩溃修复的设备侧复现；en + 320dp 换行。

**未实现**：`UX-06` 搜索 UI（只交付审计+计划）；`UX-07b` 语音；`StatusText`/`HostsScreen`/`TechnicalInfoSheet`/
通知的 i18n；统一 `EmptyState` 组件；`Resyncing` 状态；引用到光标处；Android 12 及以下的 App 内语言切换；
品牌图标资产；真机 FrameTimingMetric（I-74/I-78 限制未变）。

## 6. 用户醒后请重点看这 5 处

1. **深色 token**（`#10151D / #181F2A / #222B39`）是否符合你的审美 —— 这是 `PROVISIONAL`；
2. **主机选择器在标题下方**（`BOARD-HOST-001`）是否符合预期；
3. **对话正文 16/25 + 分组表头**：连续 Agent 回复只出现一次灰色 agent 名，是否够清晰；
4. **Composer 轻量双层**（surface + 20dp 圆角 + 16dp 边距）是否比旧的大盒子"轻"；
5. **行内码变紧凑**（去掉伪 padding 后的真实间距）是否可接受 —— 这是保真的代价。

## 7. 下一步（按依赖顺序）

`UX-03e Resyncing → UX-04c 光标插入 → UX-05a 剩余 i18n → UX-05b 切换验证 → UX-06 搜索实现 →
UX-08a 设置分组 → UX-08b 统一空态 → 真机 P1–P8 全量截图 + TalkBack/IME 走查 → UX-09 最终验收`
