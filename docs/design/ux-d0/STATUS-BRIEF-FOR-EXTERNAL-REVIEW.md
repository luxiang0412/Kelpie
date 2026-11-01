# Herdr Android 客户端 — 现状说明（供外部设计评审）

> 生成时间：2026-10-31｜代码基线：`aa45b3f`（分支 `main`）
> 用途：把当前产品、代码、验证状态**如实**描述清楚，供外部（ChatGPT 或其他设计评审）判断
> UI/UX 问题出在哪、该怎么改。本文不含美化，也未宣称未经验证的东西。

---

## 1. 产品是什么

**不是**移动 SSH 终端。定位是 **Coding Agent 的移动监督台**：在手机上看到多台机器上的 AI coding agent
（`pi` / Claude 等）现在在干什么、卡在哪、有没有在等你，并能用一条短指令推进它。

核心使用场景（真实）：
1. 通勤/离开电脑时，扫一眼「哪个 agent 在等我」；
2. 点进去读它刚刚做了什么（结构化对话，不是终端字符流）；
3. 回一句「继续」「用方案 B」；
4. 长任务时让它在后台跑，完成/出错时收到通知。

**明确不做**：读屏猜审批并一键批准（实测 250ms 窗口错题率 34%）、离线发送队列、跨主机聚合（尚未做）。

---

## 2. 技术架构（已实现）

```
Android App (Kotlin, Compose, Material3, minSdk 26 / target 34)
 ├─ :core:ssh      SSH 传输（SshJ）：连接、exec 通道（客户端并发上限 4）、指纹校验
 ├─ :core:herdr    Herdr API 客户端（JSON 协议 22）+ Pi JSONL transcript 解析 + 写操作账本
 │                 · HerdrApi.snapshot / pane.read / agent.prompt
 │                 · PiTranscriptParser → ChatMessage(kind, text, toolName, toolCallId, id, parentId)
 │                 · ConversationBranch：按 id/parentId 还原「当前有效分支」
 │                 · WriteLedger：Accepted / Rejected / Unknown 三态 + 未确认记录持久化
 ├─ :core:data     进程级连接管理 + Room 缓存 + 草稿库
 │                 · HostConnectionManager：多主机连接、自动重连（1.67s 恢复）、状态三态分离
 │                 · BoardRepository / TranscriptStore（Room，缓存看板与对话）
 │                 · DraftDatabase（独立库 herdr-drafts.db）+ DraftStore + pending_writes
 ├─ :feature:board Compose UI（全部界面）
 │                 · BoardScreen（看板）、ConversationScreen（对话 + Composer）
 │                 · TechnicalInfoSheet、HostsScreen（主机管理）
 │                 · conversation/：IncrementalConversationMapper（增量渲染）
 │                 · markdown/：MarkdownParser（自研子集）+ MarkdownText + MarkdownAstCache
 └─ :app           组装：MainActivity、前台服务 MonitorService、通知、debug FixtureActivity
```

规模：Kotlin 约 **8.7k 行**（含测试与 fixture）。

数据流（发送）：`Composer → HostsViewModel → HostConnectionManager.sendPrompt → HerdrApi.agent.prompt → WriteLedger`

数据流（阅读）：`Pi JSONL → TranscriptTailer（增量拉取）→ PiTranscriptParser → Room → ViewModel → IncrementalConversationMapper → LazyColumn`
Markdown 解析在 `Dispatchers.Default`，AST 走 LRU 缓存（256 条，key 含内容哈希+长度+首尾 48 字符抽样+解析器版本）。

---

## 3. 界面清单（现状事实）

| 页面 | 内容 | 现有视觉 |
| --- | --- | --- |
| **看板 BoardScreen** | 顶部「Agent 工作台」+ 刷新 + 主机下拉；按紧急度分区：**需要处理 → 运行中 → 已完成 → 其他**；每行：状态词 + agent 名 + 标题 + 项目行 + 右侧 chevron；底部导航「看板 / 主机」 | 无卡片、无分隔线，行间仅靠 24dp 空白；状态既用**彩色圆点**又用**彩色文字**；已完成/空闲分区标题与正文同重量 |
| **对话 ConversationScreen** | 顶栏（返回「看板」+ 标题 + 项目 + 状态胶囊 + 「技术信息」）；正文消息流；底部 Composer | 用户消息右对齐浅色气泡（max 300dp）；assistant 全宽、左对齐、前面一行 **11sp 绿色「Pi」**；thinking/tool 折叠行（▸ + 名称 + 状态 + 字符数）；Composer 是一个大圆角 Surface + 胶囊「发送」按钮 |
| **技术信息 Sheet** | pane_id/terminal/workspace、agent_session 路径、revision（诊断）、待确认写操作列表 | Material 默认 ModalBottomSheet |
| **主机管理 HostsScreen** | 主机列表、连接状态、新增/删除 | 同上，基本是表单 |
| **Markdown 渲染** | 标题、粗/斜、列表、引用、行内码、链接、围栏代码块（语言标签 + 复制 + 横向滚动 + 12 行折叠） | 行内码用空格 hack 做 padding（见 §6 缺陷 1）；引用用 3px 左边线；代码块是 `surfaceRaised` 圆角块 |

设计 token（`design/Tokens.kt`，实际值）：

- 浅色：bg `#F7F8FA`、surface `#FFFFFF`、surfaceRaised `#F1F3F8`、textPrimary `#172033`、textSecondary `#526074`、accent `#3556CE`、outline `#D6DDE7`
- 深色：bg `#0B111C`、surface `#141B29`、surfaceRaised `#1C2636`、textPrimary `#F1F5FA`
- 状态色（前景/背景成对）：BLOCKED `#98470B`/`#FFF3EA`、WORKING `#12558A`/`#EAF4FF`、DONE `#17683E`/`#EAF8EF`、IDLE `#596374`/`#F0F2F5`、UNKNOWN `#74530A`/`#FFF7E0`
- 字号：titleLarge 24/30 SemiBold、titleMedium 17/24 SemiBold、titleSmall 16/24 Medium、bodyLarge 16/24、bodyMedium 14/22、bodySmall 13/20、labelLarge 13/18、labelMedium 12/18、labelSmall 11/16
- 间距：4/8/12/16/20/24/32；圆角：card 18、code 12、chip 全圆
- 字体：系统默认（中文 = MIUI 默认），等宽 = Monospace

---

## 4. 功能实现状态

### 4.1 已实现并验证（有真机/自动化证据）

| 能力 | 证据 |
| --- | --- |
| 多主机连接、看板真实数据、状态分区 | 真机截图（真实 herdr `test` 会话，2 个 pi pane） |
| 断线自动重连 | 实测 1.67s 恢复；启动不再自触发重连（复测 `manual reconnect = 0`） |
| 对话阅读（真实 pi 会话 Markdown 渲染） | 真机截图（标题/列表/行内码正确） |
| **真实发送链路** | 真机发送 `Reply-with-one-line-M2C2V-ACK-0120` → Herdr **Accepted** → 远端 JSONL 14→16 行（user `985860c5` → assistant `e546b12a`，1.85s）→ App 增量显示回复 |
| Composer 基础交互 | 1–5 行、Enter 换行、48dp 发送键、IME 不遮挡（真机截图）、长 Prompt 全屏编辑 |
| 草稿持久化 | 独立 Room 库；**杀进程（force-stop）后草稿完整恢复**（真机验证） |
| 发送三态 | Accepted / Rejected / Unknown；Unknown 不自动重发、需二次确认才重发（新 Action ID） |
| Unknown 跨进程持久化 | 只落 Pending/Unknown；启动 restore 不触发发送 |
| 增量渲染 | 追加 1 条只映射 1 条；2000 条历史 + 100 次追加：全量 55.1ms vs 增量 2.5ms（JVM 微基准） |
| 自动化测试 | Compose UI **16/16**（AVD Android 34）；JVM 60+ 用例；Room 迁移测试 1/1 |

### 4.2 已实现但**未经真机验证**

- 上滑阅读时不被自动拉回底部、「↓ 新消息」提示（代码有，未构造场景实测）
- 断线重连后不重复消息 / 不误发
- Pi 分支切换（`ConversationBranch` 只有单测与人造数据）
- ToolCall/ToolResult 在真实工具调用下的关联（本轮 prompt 未触发工具）
- APPEND/PATCH/FULL 命中计数（**应用内没有埋点**，只能间接推断）
- 中文拼音组合输入、光标/选区、粘贴大段堆栈、TalkBack 朗读顺序
- 旋转时的「二次加载是否覆盖用户输入」竞争
- 草稿身份在 `agent_session` 缺失时的串稿风险（有 NeedsConfirm 兜底）

### 4.3 未实现

- 结构化审批（L1）触发源：Claude `PermissionRequest` hook 变更清单已备好但未获批；pi 侧扩展 API 未调研完
- 跨主机聚合（「等你处理」总数）
- 正式图标集、App 图标（当前是字形占位）、后台推送（Cloud/FCM）
- Markdown 表格 / 图片 / 嵌套列表 / Diff 视图
- 平板/折叠屏布局

### 4.4 已知问题（节选，按影响排序）

| ID | 问题 |
| --- | --- |
| I-64 | 长对话（500 条）历史 gfxinfo 显示 30–44% jank，但**测法不可信**，真机可信 FrameTimingMetric 至今为 0 |
| I-74 | MIUI 禁止 instrumentation 启动 Activity → 真机跑不了 Compose UI 测试 / Macrobenchmark（已用隔离 AVD 绕过） |
| I-78 | MIUI `dropShaderCache` 失败 → Macrobenchmark 无法产出 FrameTimingMetric |
| I-93 | **最大系统字号（1.3）下对话页标题栏被裁剪**，压住列表首项 |
| I-84 | 无 `agent_session` 时草稿身份靠 pane 占用指纹，理论上可串 |
| I-91 | 真机中文 IME / TalkBack / 旋转 / 最大字号的人工验收未完成 |
| I-92 | 恢复后的 Unknown 只在技术信息 Sheet 里可见，缺显眼入口 |

---

## 5. 设计现状自评（外部评审的起点）

**目标风格**：「Quiet Command」——信息优先、克制、状态色只表意；单手可用；弱网可读；深色为主。

**实际观感**（本地自评，也是用户反馈「丑」的来源）：

1. **层级扁平**：全站只有 24sp 粗标题 + 16sp 正文，缺中间层；分区标题（「已完成 1」）与正文同重量，看板读起来像文档而不是仪表盘。
2. **颜色过载且语义混乱**：一行里同时出现彩色圆点（状态）、彩色文字（状态）、蓝色 chevron（可点）、灰色副标题 —— 颜色既表状态又做装饰，违反自身规范。
3. **密度失衡**：看板上 2 条数据占据上部 1/3，下方 2/3 全空；行与行没有分隔线，扫描性差。
4. **无视觉锚点**：accent 只出现在「发送」和链接；真正该强调的（等你处理的数量、正在运行的那个）反而弱。
5. **Composer 与系统脱节**：大圆角盒子 + 胶囊按钮，placeholder 与正文同字号，像"塞进来的"。
6. **深色模式糊**：只有 bg/surface/surfaceRaised 三档且差距小，缺 1px 分隔与 tonal 层次。

---

## 6. 具体代码级缺陷（与审美无关，可直接修）

1. **行内代码空隙是 bug**：`MarkdownText.inline()` 用 `" ${text} "`（前后加空格）模拟 padding，导致 ` grep `、` spike/ `、` HANDOFF.md ` 两侧各有半个字宽空白；应改为真实背景 + 内边距（或 `Box` 包裹）。
2. **「Pi」标签是 11sp 绿色字**，视觉上像调试标记，不像发言人。
3. **TopAppBar 固定高度 + 双行标题**，在 font_scale 1.3 时裁剪（I-93）。
4. 看板行右侧 chevron 用文本字符 `›`，与文字基线不一致。
5. 状态色点与状态词重复表达同一信息（冗余 + 噪音）。

---

## 7. 测试与测量基础设施现状

| 项 | 状态 |
| --- | --- |
| Compose UI 测试 | ✅ 16/16（隔离 AVD `herdr_m2c1`，Android 34；真机 MIUI 跑不了，I-74） |
| JVM 单测 | ✅ Markdown 解析 12、配对 9、分支 5、增量一致性 7、Mapper 成本 2、缓存 5、草稿/目标校验 15、账本持久化 5 |
| 增量 vs 参考实现一致性 | ✅ 固定种子随机序列逐条比对 |
| Macrobenchmark | ⚠️ 流程可跑（模拟器 `startup_500` 有数），**真机 FrameTimingMetric 为 0** |
| 真机性能数据 | ❌ 无（MIUI BAL + dropShaderCache） |
| 调试用 Fixture | ✅ debug 专用 `FixtureActivity`（100/500/2000 条确定性消息，不写 Room、不连远端） |

---

## 8. 外部评审最需要回答的问题

1. 在"移动端 agent 监督台"这一品类下，看板的信息密度、分区方式、状态表达是否方向正确？
2. 目标风格「Quiet Command」是继续做（需要一套真正的排版/密度规范），还是应该换方向（如卡片化、时间线化）？
3. 对话页的发言人标识（现在一行 11sp「Pi」）应该怎么处理最不打扰阅读？
4. 状态色：一个 agent 行内到底应该用几种颜色？
5. Composer 应该多"重"（贴着键盘的一行工具条 vs 现在的独立大盒子）？
6. 代码块/行内码在窄屏（1080px / 5.2 英寸）上的最小可用排版规则。
7. 深色模式需要几档层次才够（现在 3 档且差距过小）。
