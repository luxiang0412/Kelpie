# Herdr Android — Graphite × Editorial 自主实施总任务书

> **版本**：1.0 · 2026-11-01  
> **适用执行者**：本地 Pi Coding Agent（默认能力有限，需要低歧义、小步实施）  
> **执行授权说明**：用户本轮授权基于 UX-D0 资料，在**本地 Android 工程中连续执行**下面列出的、可回滚的开发与测试任务；不需要每个任务完成后等待用户回复。**这不是**远端 Herdr/Pi 会话写入、生产部署、删除用户数据、自动合并主分支、改变安全协议或上传秘密的授权。  
> **执行目标**：将现有功能已跑通的 Kotlin / Compose App 按 Graphite × Editorial 规范做成可用、精致、可国际化且可测试的原生移动 Coding Agent 客户端。  
> **优先级**：功能/数据安全 > 正确性 > 可访问性 > 不回归 > 设计一致性 > 性能 > 动效/装饰。

---

## 0. 执行者必须先阅读

本任务书是**执行编排和分任务验收文件**，不替代现有详细设计规格。

1. `docs/design/ux-d0/HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md`
2. `docs/design/ux-d0/HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md`
3. `docs/design/ux-d0/HERDR-UX-D0-BOARD-SPEC-v0.2.md`
4. `docs/design/ux-d0/HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md`
5. `docs/design/ux-d0/HERDR-UX-D0-COMPOSER-SPEC-v0.2.md`
6. `docs/design/ux-d0/HERDR-UX-D0-VISUAL-REVIEW-v0.1.md`
7. `docs/design/ux-d0/STATUS-BRIEF-FOR-EXTERNAL-REVIEW.md`（是历史快照，不代表当前代码）
8. `docs/design/tasks/HERDR-UX-01a-PI-TASK-PACKET-DRAFT.md`（其草案标注由本文件明确授权范围替代）
9. 仓库最新 `RESULTS.md`、`DECISIONS.md`、`ISSUES.md`、`NEXT-STEPS.md` 及 README/AGENTS.md（若存在）
10. 实际 Android 源码、Gradle 构建文件、测试及已验证的运行脚本。

**文档与代码不符时**，先标记差异并检查最近提交；安全/协议事实以当前证据为准，设计变动以已确认用户决策为准。绝不能按猜测创造不存在的文件路径、模块名、接口或 App 行为。

### 0.1 决策等级（务必区分）

| ID | 决策 | 执行属性 |
|---|---|---|
| `BOARD-DONE-001` | 工作台已完成默认收起 | **已批准，必须遵守** |
| `CONV-ACTIONS-001` | Agent 消息末尾常显低强调「复制 + 更多」，正文长按系统文本选择 | **已批准，必须遵守** |
| `CMP-VOICE-001` | Mic 在**实际可用**时正常宽度常显，窄屏/大字体收入「更多」，转写不自动发送 | **已批准，必须遵守** |
| `SEC-WRITE-001` | Accepted / Rejected / Unknown；Unknown 不自动重发 | **安全红线** |
| `SEC-APPROVAL-001` | 不做读屏推断式一键审批 | **安全红线** |
| `CONV-TOOL-001` | ToolCall/ToolResult 只用可靠 ID 关联 | **正确性红线** |
| `BOARD-HOST-001` | 主机选择器放在工作台主标题**下方** | **本轮实施默认值，尚待用户视觉终审** |
| `CMP-STYLE-001` | 轻量双层 Surface，Send 唯一显著强调按钮 | **本轮实施默认值，尚待用户视觉终审** |
| `CMP-QUOTE-001` | 引用优先插入光标；若 API 不可靠，使用安全的“追加到草稿末尾并留分隔” | **本轮受控默认值，必须记录实际选择** |
| `CONV-TOOL-002` | Tool 卡有限预览 + 查看完整输出 | **以数据源真实支持为前提；不具备完整数据则不出现假入口** |
| `CONV-ANCHOR-001` | 稳定消息锚点 + 像素偏移保存阅读位置 | **尽力实现；涉及旧数据结构时可降级，记录限制** |
| `BRAND-ICON-001` | Focus Orbit 品牌方向 | **仅做可审阅方案；没有通过视觉审核的资产不得谎称最终 App 图标** |

用户要求自主连续执行，故上述两个 UI Proposed 可以**作为本轮可回滚的实现默认值**，**不等同于正式设计冻结 v1.0**。必须在报告中保留“待用户审美验收”标签。

### 0.2 绝对禁止

- 不改远端 `192.168.5.8`、不写 Herdr `test` Session、不安装 Hook、不重启/升级远端服务、不改 SSH Host Key 信任策略。
- 不清空、不破坏或迁移真实用户草稿、Action Journal、Room 数据；不随意添加 `fallbackToDestructiveMigration`。如果确有必要迁移，先写方案并标 **BLOCKED**，不能自行破坏数据。
- 不在 Unknown 结果后自动重试，不把 Accepted 显示为 Agent 已执行，不把 `done` 和 `idle` 混淆，不把 `blocked` 视为 L1 审批。
- 不更改已验证的 Pi JSONL 解析、可靠 Tool ID 配对、增量 Mapper、跨会话隔离逻辑，除非单任务发现可复现的缺陷且有对应回归测试。
- 不擅自引入重型 UI 框架、WebView 聊天替代、远端推送服务、云端识别、图片上传、绕过系统安全的方案。
- 不自动合并 `main`、不 `git push --force`、不删除/覆盖已有未提交改动或文件、不无条件自动修复 Gradle 大版本。
- 不将 SSH 私钥、Host Key、JWT/令牌、真实日志中的隐私内容或真实用户对话提交 Git、上传第三方或放入截图。
- 不调用尚未完成的语音/附件/活动历史功能制作假可点击控件；不能靠硬编码固定英文/中文实现临时 UI。
- 不为了视觉截图绕过发送目标校验、安全边界、TalkBack、输入法或焦点规则。

---

## 1. 自主运行协议（睡眠期间无需向用户逐项请示）

### 1.1 运行前保护

1. 执行 `git status --porcelain=v1`、`git branch --show-current`、`git rev-parse HEAD`、`git remote -v`。保存到 `docs/design/ux-run/00-baseline.md`。
2. 工作树里如果**只有这次导入的 UX-D0 文档、原型和任务包**为未追踪文件，可在新工作分支中先单独提交这些资料，作为受本轮授权的文档基线；须逐一确认它们来自本 ZIP 的文件清单。若还有任何其它未提交/未追踪用户改动：**禁止 stash/reset/clean**，执行只读审计并标记 `BLOCKED-WORKTREE`，不得写入代码；留下报告后停止实施。
3. 若干净：新建隔离分支 `ux/graphite-editorial`；如分支已存在，不强制覆盖，使用唯一的递增后缀，并记录。禁止直接在 `main` 连续改动。
4. 识别 Windows PowerShell / Linux / macOS 环境、Java 版本、Gradle wrapper、可用 AVD；**只用实际存在的构建脚本**，不盲套 `./gradlew` 或 bash。
5. 读取现有 `AGENTS.md` 和项目测试指南，先运行最小可用的现有检查作为 baseline：失败必须记录，不得描述为自己的回归。
6. 新增 `docs/design/ux-run/PROGRESS.md`：每个 Task ID 一行，状态只能是 `TODO / IN_PROGRESS / PASS / PARTIAL / BLOCKED / SKIPPED`；不得把编译成功标记为视觉 PASS。
7. 对于与运行环境无关的文档和代码修改可继续；真机被 MIUI instrumentation 阻断时，优先使用已验证的新 AVD，**不再无限排查 MIUI**。

### 1.2 连续执行逻辑

按第 3 节顺序逐任务执行，每个 Task 严格采用此循环：

`检查真实源码 → 记录改动计划 → 运行针对性基线 → 最小实现 → 自动测试 → 截图/人工布局检查 → 记录四类结论 → git commit → 下一个 Task`。

- **小任务验收通过**：可自动继续下一任务，无需等用户醒来。
- **该任务不具备前提**（例如完整 Tool 输出根本无法获取、设备无 SpeechRecognizer）：不得虚构功能；标为 `BLOCKED` 或 `PARTIAL`，写清被阻断的条件；如果与后续任务无强依赖，安全地跳过继续其他任务。
- **测试环境失败**：先用 AVD 复现；若为可重复平台阻断，记录证据，不要花大量时间兜圈子；需要真实设备的验收标为未验证。
- **连续同一失败两次，且没有新增证据或变更方案**：停止对这条路径的重复尝试，进入诊断或安全跳过，不得无限循环。
- **安全、数据损坏、真实用户内容覆盖、Gradle 大版本迁移、SSH 连接/证书变化**：立即暂停受影响任务；不在无人值守情况下擅自突破边界。可以执行独立、低风险任务。
- **一个任务不能完成**不意味着整套任务一定停掉；保留可独立构建的检查点，并在末尾列明未完成事项。若 App 无法构建且修复需要扩大范围，则停止后续改代码。

### 1.3 Git 与日志

- 每个任务至多涉及一个清晰用户能力/视觉子问题；复杂任务应拆成两个子提交，不混安全修复和外观改动。
- 提交主题：`ux(UX-01a): fix conversation top bar at large font` 等；禁止虚假的 `fully verified` 字样。
- 每个 Task 附 `docs/design/ux-run/UX-<ID>-REPORT.md`，四类必须明确：**已实现并验证 / 已实现但未验证 / 未实现 / 已知问题**。
- 成功完成一个任务才可 commit；`PARTIAL` 的实验代码若不影响主线构建，可单独提交 `wip` 清晰标记，原则上优先回滚不完整 UI，只保留报告。
- 可推送**新分支**至现有配置好的 `origin`（不需要合并 main），失败时保留本地提交，不自行修改远端仓库设置或凭证。
- 不向外部提交真实对话截图。Fixture 统一采用无机密的假数据。

### 1.4 执行预算与停止策略

用户睡觉时不要反复向用户索要确认；遇到需要确认的敏感操作就**跳过/标阻断**。为了避免执行者陷入长时间循环：

- 单任务以“小提交可验证”为准，不允许把 3+ 独立领域混到一次改动。
- 一条可疑依赖/设备错误最多尝试 2 种有依据的修正策略，随后提交阻断记录。
- 每个 Task 的单测/UI 测试按仓库现有规则执行；最后做一次全量可执行回归。
- 若遇到 API 版本与设计文档不一致，先查本地依赖源码或官方文档/现有实现，不能杜撰 API；联网不可用时记录并实现可安全验证的部分。
- 禁止任务结束后自动运行远端测试或提交任何真实 Agent 命令。

---

## 2. 工程事实与统一验收矩阵

### 2.1 已知事实（来自之前项目总结；执行前需重新验证）

- Kotlin / Jetpack Compose / Material 3，Android minSdk 26；项目可能采用 AGP 9.2.1 内置 Kotlin，**不可无脑重新 apply `kotlin-android` 插件**。
- `:core:ssh`、`:core:herdr`、`:core:data`、`:feature:board`、`:app` 已存在或有对应实现，需以当前仓库为准。
- Herdr/Pi 对话、发送 Accepted、草稿 Room、Unknown 账本、增量 Mapper 已在既往阶段实现；它们是**保留和回归**对象，不是本任务重写目标。
- Compose UI 测试曾在隔离 AVD `herdr_m2c1` 跑通 16/16；MIUI 真机可能拒绝 instrumentation；Benchmark 的模拟器数据不代表真机性能。
- 历史缺陷：I-93 顶栏字体放大裁剪；InlineCode 靠空格制造 padding；工具卡和旧 UI 无足够层级；Unknown 入口可能过深。

### 2.2 全局视觉设计数值（唯一规范）

严格引用 `HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md`，实际实现以同一 Token 系统承载，禁止业务页面散落色值。摘要如下：

| Token | Light | Dark |
|---|---|---|
| canvas | `#F7F8FA` | `#10151D` |
| surface | `#FFFFFF` | `#181F2A` |
| raised | `#EFF2F6` | `#222B39` |
| borderSubtle | `#DFE5ED` | `#344052` |
| textPrimary | `#172033` | `#EEF2F8` |
| textSecondary | `#4F5D70` | `#AEB9C8` |
| accent | `#3556CE` | `#9BB0FF` |
| onAccent | `#FFFFFF` | `#10151D` |

状态色见设计系统 §2.1.2。**borderSubtle 不是高对比焦点线，不能单独表示可点击边界。**

| Typography | sp / 行高 sp | 用途 |
|---|---|---|
| displayPage | 26 / 34 SemiBold | 工作台标题 |
| titlePage | 18 / 26 SemiBold | 对话标题 |
| titleTask | 16 / 24 SemiBold | Agent 标题 |
| titleSection | 14 / 20 SemiBold | 分组、工具小节 |
| bodyReading | 16 / 25 Regular | Markdown 正文 |
| bodyInput | 16 / 24 Regular | 输入 |
| bodySecondary | 14 / 21 Regular | 描述 |
| meta | 13 / 19 Regular | 主机、Agent、状态 |
| labelAction | 13 / 20 Medium | 控件动作 |
| code | 13 / 20 Mono | 代码、命令、路径 |

- Spacing 4/8/12/16/20/24/32dp；常规页面侧边距 20dp（<360dp 为16dp），Composer 侧边距16dp（<360dp 为12dp）。
- Shape：InlineCode 约 4–6dp、Code/Tool 12dp、Card 16dp、Composer 20dp；**全屏正文不包大卡**。
- Icons 视觉20–24dp，触达目标至少48×48dp；不再用 `›`、`▸`、`⤢` 等字体字形做主要点击图标。
- 动效：点击80–120ms、状态140–200ms、折叠180–240ms、转场220–280ms；低性能/减少动画下可直接切换，不能循环装饰性光效。

### 2.3 状态真相矩阵

必须分别保留 `AgentState`、`TransportState`、`DataFreshness`、`WriteOutcome`，不要合并为 `isOnline`。主页面不能长期显示已连接成功横幅；数据过期必须标识缓存；Unknown 写操作必须有可找回入口。重要问题优先级：安全异常 > Unknown write > 鉴权失败 > 离线/缓存 > 需处理 Agent > 普通同步。

### 2.4 截图/测试组合（逐任务选相关项；末轮完整跑）

| Profile | 宽度 | 字体 | Locale | 主题 | 用途 |
|---|---|---|---|---|---|
| P1 | 393dp | 1.0 | zh-CN | Light | 主视觉 |
| P2 | 393dp | 1.0 | zh-CN | Dark | 主视觉 |
| P3 | 393dp | 1.0 | en | Light | 英文排版 |
| P4 | 393dp | 1.0 | en | Dark | 英文排版 |
| P5 | 320dp | 1.6 | en | Dark | 极限约束 |
| P6 | 320dp | 1.6 | zh-CN | Light | 极限约束 |
| P7 | 393dp | 1.3 | zh-CN | Dark | 中等放大 |
| P8 | 600dp | 1.0 | en | Light | 大屏布局回退 |

- 每个视觉 Task 至少 P1/P2/P5；改变文案/导航/输入的任务再加 P3/P6；最终集成跑 P1–P8 在 Board/Conversation/Composer 各一次。
- Fixture 类型：no host、host online/no agents、1/2/8/20 agents、blocked/working/done/idle/unknown、connected+stale、offline+cached、auth/key failure、Unknown write、500/2000 messages、multi-tool、unmatched ToolResult、长英文、代码/中文混排、IME visible。
- 截图必须来自 **Compose 可运行 App/测试 AVD/真实手机** 并且注明来源；HTML 视觉原型只能作参考，不能混充 Android 运行证据。
- 自动化：每任务尽可能跑针对性 JVM + Compose UI；全局回归沿用已验证 16 项 UI 测试；长列表仅在相同 Fixture、相同设备、同构建模式下比较性能，不能用历史不等价数字宣布优化。
- 使用实际 Gradle Wrapper（Windows 通常 `gradlew.bat`，PowerShell `./gradlew.bat`；Unix `./gradlew`），先 `tasks` / `projects` 判断真实 Task 名，不得假定 `:app:connectedDebugAndroidTest` 一定存在。
- 不能对用户的真实草稿数据库、实际用户对话或 SSH Host Key 仓库做清理测试。测试专用包/Fixture 不污染生产数据。

### 2.5 每个任务的最小交付格式

```markdown
# UX-XX report
- baseline commit / branch:
- inspected real files and declarations:
- exact goal and allowed scope:
- changed files:
- why the implementation matches UX-D0:
- tests (commands + exit codes + device/build variant):
- screenshots (device/width/font/locale/theme + path):
- safety regressions checked:
- verified / implemented-not-verified / not-implemented / known-issues:
- commit ID:
- rollback command / relevant limits:
```

---

## 3. 可连续执行的 Task Packets（按顺序，每个任务单独 commit）

> 执行者：**逐项执行，不要一次生成全体代码后才编译**。每个 Task 的“最小目标”是唯一交付目标。能复用现有代码就不新建重复抽象。无法自动验收的项目必须诚实标记。

### UX-00｜仓库审计与基线保护（不可跳过）

**目标**：给之后所有任务建立准确的代码坐标与回归证据。

1. 按 §1.1 创建隔离分支与 `PROGRESS.md`；读取所有设计资料，生成设计与源码映射表：页面→Composable→ViewModel→Repository→当前 UI 测试，记录准确路径和符号名。
2. 查找 `Tokens.kt`、Theme、Board、Conversation、Composer、InlineCode、Tool、Hosts、Settings、Room Draft、WriteLedger、Navigation。用 `rg` 搜索、IDE/本地代码阅读确认真实定义，**不要因为 task 内写了建议名称就重建相同类**。
3. 列当前已经实现的能力与差距，尤其已接通/未接通的菜单与控件。为 Design System 的 Open 决策建一份 `DECISION-IMPLEMENTATION-DEFAULTS.md`，只记录本任务书授权的暂定默认值。
4. 运行已有测试 baseline；抓取当前 Board/Conversation/Composer 各一张 Android 截图或清楚标为 `NOT_CAPTURED` 并解释。
5. 保存 `docs/design/ux-run/UX-00-AUDIT.md`、初始 `PROGRESS.md`；commit 文档基线。

**禁止**：此步修改 UI 业务实现。**验收**：审计中的所有路径都存在，状态与原始证据对应。

### UX-01a｜Conversation 顶栏 320dp / 大字体裁剪修复

**唯一目标**：修复 I-93，且不影响消息滚动首项和操作按钮。

1. 完整遵守 `HERDR-UX-01a-PI-TASK-PACKET-DRAFT.md` A–H，先按当前源码确认顶部栏真正结构。
2. 复现固定高度+双行标题问题；保留 before 截图；如果历史代码已修正，记录 `PASS-NO-CHANGE`，不再制造改动。
3. 使用内容自适应高度、行列布局与安全截断；320dp/fontScale1.6 时返回按钮必显，非核心操作优先进入更多；状态文案仍准确。
4. 禁止全局改 Navigation、Markdown、Composer、连接或发送逻辑。
5. 执行任务草案 A01–A06 的 UI Fixture，至少 P1/P2/P5/P6 截图；断言顶栏不盖住第一条消息、返回和更多≥48dp、TalkBack 顺序正确。

**验收**：原裁剪不能复现，正常 393dp 没有无意义大留白，原对话 UI 测试不回归。**提交**：`ux(UX-01a): ...`。

### UX-01b｜InlineCode 的原始文本与复制保真修复

**唯一目标**：移除将代码字符串包围空格当 padding 的实现。

1. 搜索真实 InlineCode 代码和 `buildAnnotatedString`，记录输入→AST→渲染→复制链路；构造 `grep`、`spike/`、`HANDOFF.md`、中文标点紧贴代码、前后空格真实存在、Tab/CRLF、emoji Fixture。
2. 优先保持源文本原样：使用背景 Span 或经验证可保持选区与基线的 InlineContent；**若真实 padding 与系统选区冲突，退回保真背景 Span**，不得为了视觉增加数据空格。
3. 保证 Markdown `复制原文`、`复制代码` 和系统选区行为独立正确；该 Task 只涉及 InlineCode 与相关测试。
4. 自动测试断言“复制所得等于源文本所要求的 exact string”；Code Block fence 不进入“复制代码”；截图对照中英混排。

**验收**：视觉不会无故产生半个字符空白，复制数据没有增删改，长按选择仍可用。

### UX-00a｜统一基础 Tokens / Theme / 图标契约

**唯一目标**：在不改变业务/网络行为的前提下，将 Graphite × Editorial 的基础视觉形成一个唯一实现。

1. 审计现有 `design/Tokens.kt`、Theme/Material 3 色表，建立迁移映射，优先扩充而不是创建第二套 `TokensV2`。
2. 实现 §2.2 颜色/字阶/间距/Shape 的**语义 Token**，Light/Dark 独立值；Theme 支持显式系统、浅色、深色方案（如果已有选择设置，不破坏存储）。
3. 旧 UI 逐步改为引用 Token；本步骤仅替换基础样式，不做页面布局重构，避免截图差异无法归因。
4. 固定图标 API/Vector 资源，清理主要点击控件中的 Unicode 假图标，但仅限共享组件，不全局加大体积图标库。新增依赖时记录大小、许可、具体所需图标；没有必要则复用现有资源。
5. 组件至少覆盖 `AppTextStyles`、`SemanticStatusLabel`、`TouchTargetIconButton`、`SurfaceTokens`、`PageGutter`（名称可根据项目现有结构调整，不得重复封装）。
6. 加入纯 JVM 色值对比度检查（文本 ≥4.5:1，必要非文本 UI ≥3:1）；注意装饰性 border 不可当作焦点。记录实际检查组合。

**验收**：构建、两主题基准、文本/状态无明显对比度倒退，UI 没引入业务代码变化。

### UX-02a｜工作台顶栏、主机选择器与底部导航

**唯一目标**：采用暂定 `BOARD-HOST-001` 将主机选择器置于主标题下方。

1. 阅读 Board Spec §1–3；保存现状截图。实现标题/刷新/设置操作的响应式排列；320dp/fontScale1.6 可换行，高度由内容决定。
2. 主机选择器必须显示真实主机名，切换时保留 Agent 目标来源；若只有单主机也不伪装“全部主机”。在跨主机聚合未实现前**只列已有单主机选项**。
3. 仅保留已实现的底部入口（工作台、主机）；没有持久 Activity Inbox 时不创建假 Tab。
4. 连接正常不常驻成功横幅；失败/离线/缓存状态在后续 UX-02c 完善，现有安全提示不可被视觉重构删除。
5. 320/393、浅/深、中/英文、长 Host 名；操作≥48dp，系统状态栏 Insets 不叠加。

**验收**：不会因为标题与下拉并排被挤压；切换主机跳转目标正确；无新假入口。标注此方案为“Provisional UI Default”。

### UX-02b｜工作台 Attention First、列表与已完成折叠

**唯一目标**：重构 Board 信息密度和层级。

1. 基于真实 `AgentState` 按 blocked → working → done → idle → unknown 展示；不要自行编造当前任务摘要、进度百分比、等待时长；仅显示源数据真实提供的内容。
2. blocked 1–2 条使用轻强调 Attention Card；超过 2 条其余用紧凑行；working 用 64–72dp 紧凑行；done 默认收起（`BOARD-DONE-001`），用户主动展开在页面重组后保持状态；idle/unknown 不混作一个语义。
3. 一条 Agent 行最多一处明显语义色，Chevron/项目/主机始终中性；不要同时彩色点、彩色状态字和彩色 Chevron。
4. 0/1/2/8/20 Agent Fixture；0 Agent 区分“主机在线但没有 Agent”和“主机无法连接”。LazyColumn 稳定 Key，不对每个 Working 项添加循环动画。
5. 全行点击直达真实对话，返回保持筛选和滚动位置；每行 ≥48dp 触达；标题最多2行而有查看完整内容路径。

**验收**：Blocked 能 3 秒内看出；Done 默认收起；`done != idle`；空态不显示虚假统计；P1–P6 样例视觉通过。

### UX-02c｜工作台异常、缓存、Unknown 入口与系统提示

**唯一目标**：建立准确、节制的异常状态 UI。

1. 复用现有 `TransportState/DataFreshness/WriteOutcome`，分别处理 connecting、reconnecting、offline、auth failure、host key mismatch、online+stale、Unknown write、空主机。
2. 同屏最多一条主 Banner 按 §2.3 优先级出现，其他问题可从“查看全部问题/详情”恢复；**Unknown 已持久化但只在技术 Sheet 才看得到**的情况必须从 Board 进入并看到原待确认记录。
3. 清楚写“发送结果无法确认，远端可能已执行”，不能表示失败；重新发送仅走已有二次确认和新 Action ID，绝不在重连/启动时自动触发。
4. 保留缓存内容并标明“显示缓存内容”；不把事件长时间没变当作数据必定过期；不因在线自动声称消息最新。
5. UI 测试覆盖安全异常和 Unknown 同时出现、offline+blocked cached、auth failure、reconnect success、no host；通知和前台 Banner 不应对同一事件双重轰炸。

**验收**：高优先级操作可见，安全状态真实，未删除原先主机密钥确认逻辑。

### UX-03a｜对话正文视觉、身份头与用户气泡

**唯一目标**：实现阅读优先排版，不改消息协议和增量 Mapper。

1. 依 Conversation Spec §3–5，用 Agent 内容全宽、用户右侧气泡（一般最大84–86%，窄屏可88%）；不要给每条 Agent 正文加大卡片。
2. 一组连续 Agent 回复只显示一个中性 Agent 标识（已知 Pi/Claude 则用可靠名称，未知则中性 Agent），禁止反复出现 11sp 绿色“Pi”调试标签。
3. Markdown 段落采用 `type.bodyReading 16/25`；对话标题 `type.titlePage`；可读宽度和屏幕边距以 Tokens 为准。
4. 保留原 Markdown、Tool、Thinking 的数据对象与 stable keys；布局更改不得导致全量 Mapper O(n) 回归。
5. 对话 0/5/500 条、长中文/英文、用户长内容、离线缓存，P1/P2/P5/P6；TalkBack 先读消息内容后读操作。

**验收**：文本不挤压代码，Agent/用户角色区别明显，滚动/重连/发送不回归。

### UX-03b｜Markdown 正文与 Code Block 视觉

**唯一目标**：用已有轻量 Parser 做高质量排版，不做新语法引擎。

1. 对已支持的 heading、bold/italic、list、quote、link、inline code、fenced code 应用 Markdown 字阶和段落间距；未支持表格/嵌套列表/图片保真降级，不吞文字。
2. 引用中性左线 + 12dp 内边距；列表悬挂缩进保持编号和正文基线；链接仅 http/https 可点击，其它显示不可点且不能自行执行。
3. Code Block：13/20sp Mono，语种标签、可复制源码、局部水平滚动；长于约12行默认缩略，展开操作明确；复制不含 fence/语言/行号和视觉填充。
4. 大代码或不受信任 HTML 不执行、不作为 WebView 主动内容；文字排版在性能允许的前提下实现。
5. 使用真实源原文作为复制来源，绝不能从渲染后文本反推。

**验收**：中英标点贴 InlineCode 无多余空格，正常/长代码在320dp不拖动整页，正文支持文字选择，长对话性能未出现明显证据性回退。

### UX-03c｜Tool/Thinking/长输出

**唯一目标**：Agent 工作记录可读、可信、按需展开。

1. 依据 Conversation Spec §7，Thinking 默认折叠（非 Tool）；Tool Header ≥52dp、Tool 名 + 真实状态 + 展开；Summary 不超过两行，Tool 列表遵守原 Transcript 顺序。
2. 仅可靠 Tool ID 匹配；不匹配结果独立渲染，不依顺序猜测；结果收到不等于执行成功，不能从输出含 error 字样盲猜失败。
3. 默认折叠；展开后显示参数、命令与有限输出（约12行）；长输出若真实完整源可获取，提供完整阅读页，并支持选择/复制与按需加载；**不可访问全量数据时不提供假的“查看全文”按钮**，改成“仅有部分输出”并说明。
4. 展开状态依据 Host/Session/Branch/ToolCall ID 管理，切换会话不串；内部 Copy 不误触卡片 Expand；500/2000 条 LazyColumn 不应用全局尺寸动画。
5. 测试：并行工具、结果先到、无匹配 ID、结果更新、旧分支、无工具输出、超长单行、错误结果、仅部分结果；截图浅/深与320dp。

**验收**：工具名/状态真实，交互独立，展开不丢失阅读位置或源文本，长输出不会造成明显滚动锁死。

### UX-03d｜消息复制、系统选择、更多菜单

**唯一目标**：实现**已批准**的 `CONV-ACTIONS-001`。

1. Agent 消息组末尾常显低强调「复制 + 更多」（两个点击区都≥48dp）；小屏/大字允许操作条换行，不能把 Copy 全部藏起来。
2. 长按消息正文由 Android 系统文本选择处理（Compose `SelectionContainer` 或兼容方案）；不能用父级长按拦截；工具栏应排除在选区外。
3. Copy Body 只复制可阅读的 Assistant 正文，不含 Agent 名、时间、Thinking、ToolResult、UI 按钮字样；Copy Raw Markdown 使用源原文；Code Copy 仅代码内容；Tool 命令/参数/结果分别从源字段复制。
4. 不能将“已加载部分”假称“完整输出”。对敏感剪贴板内容按 Android 平台能力设置敏感标志，避免额外的系统预览泄漏；Android13+ 系统已有反馈时不要重复 Toast。
5. More 菜单只展示**真正可实现**的 Copy Raw/Quote/Share 等；Quote 尚未完成时可先不显示。点击菜单后不会使阅读位置跳变。
6. Copy 单测覆盖中文、英文、CRLF、Tab、零宽字符、emoji、代码 fence、真实源空白。UI 测试覆盖菜单、长按选择、不影响滚动。

**验收**：用户复制和精确选择无需进入技术 Sheet；复制语义字节级保真；离线缓存下仍可用。

### UX-03e｜阅读位置、滚动与新消息入口

**唯一目标**：稳定阅读历史和新消息提示。

1. `FollowingLatest/BrowsingHistory/RestoringAnchor/Resyncing/Empty` 五状态，依据真实用户手势与列表位置转换；不是仅用 `listState.firstVisibleItemIndex` 判定永远自动滚动。
2. 用户主动浏览、选择文字或打开旧 Tool 时不自动跳底；在底部新消息可跟随；离底部提示「回到最新」或可靠新消息数。
3. 锚点由 Host/Herdr Session/Agent Session(或占用身份)/Branch/Stable Item ID + offset 组成；不可仅记 index。跨会话切换不串，分支消息不存在时保守回退并有说明。
4. 流式 PATCH 不按字符计新消息；tool result 更新也不反复增加未读数；用户点新消息返回最新后清除当前分支未读。
5. IME 和 Tool 卡高度变化不导致历史锚点乱跳；断线恢复时先保留缓存，再显示新内容。
6. 使用已有 Fixture 构造 500条+多次 append/patch/branch/sync，UI 测试覆盖自动滚动边界、可达性及消息数。

**验收**：历史阅读不会被打断、返回锚点尽可能稳定；对于不可靠身份的数据不能恢复到别的 Agent。

### UX-04a｜Composer 视觉布局：轻量双层 Surface

**唯一目标**：按暂定 `CMP-STYLE-001` 设计外观，而不改发送、Draft、Room、WriteLedger 实现。

1. 保留已经验证的 State-based TextField、1–5 行、自适应高度、长 Prompt 编辑器；仅改容器层次、Padding、图标、聚焦与 Send 视觉。
2. 主输入 Surface 使用 `surface`，约20dp 圆角（窄屏18dp），外左右16dp（窄屏12dp）；Send 是唯一明显 Accent 操作，永远有≥48dp 可点击区（可见圆面可略小）。
3. 正常态显示真实可用的 Expand、Send；Mic/附件**尚未接通时绝不显示无效入口**；语音实现后采用 `CMP-VOICE-001`：`width <360dp` 或 `fontScale >=1.3` 放 More。
4. 文本字阶16/24，Placeholder 为低强调；输入4–5行仍能看到发送按钮；系统 IME/导航栏 Insets 只由一条路径消费，无双倍留白。
5. 回归：长英文、中文 IME、横竖屏、输入500字、键盘打开、Soft Navigation 与 Gesture Navigation，至少 AVD + 可用真机手工截图。

**验收**：Composer 比旧大盒子更轻、不会遮挡正文、与键盘融合；任何视觉改动都不破坏现有发送三态。

### UX-04b｜Composer 长 Prompt、草稿与发送异常入口

**唯一目标**：统一已存在的可靠输入行为与状态视觉。

1. 复用 `DraftStore` 和 `WriteLedger`，不要重新建库；`DraftKey` 仍隔离 host/session/agentSession，缺失 agentSession 时延续 NeedsConfirm，不能偷偷减少校验。
2. 长 Prompt 编辑页：取消/完成、可编辑正文、字数/上限（已有8000字符限制）、复制/粘贴、Tab/CRLF 与光标选区保留。**完成只写回本地草稿，不触发 Send**。
3. Sending：锁定不可变发送快照、同目标单飞；Accepted 只清除未变化的已提交草稿；Rejected 保留；Unknown 保留 pending + ledger 且不自动重试。
4. Unknown 在 Composer 附近清晰显示谨慎提示，并能前往待确认操作；发送目标过期/agent 身份变化时 NeedsConfirm 优先，禁止悄悄重投。
5. 不用自动聚焦/布局切换导致编辑中的文本被先前异步 load 覆盖。
6. 16条既有 Compose UI 测试不回归；新增针对输入中回执、Unknown跨进程、长编辑器取消、目标切换、Offline Draft 的 Fixture。

**验收**：安全状态真实、草稿不串、键盘和编辑体验可靠，没有自动发送队列。

### UX-04c｜引用消息 → Composer，草稿冲突保护

**唯一目标**：实现真实有用的“引用到输入框”，不抢现有草稿。

1. 入口从 UX-03d 的 More 开始；读取该消息的**不可变文本快照**与真实会话身份。优先使用原始来源而非截断渲染结果。
2. 当目标与当前 Composer 身份相同时：能准确取得光标/选区时，在光标处插入带边界的引用；如果当前文本输入 API 无法可靠插入，则用空行分隔安全**追加到末尾**，并明确记录回退。
3. 草稿已存在时不得覆盖；超过8000字符上限要在修改前拒绝，显示“内容太长，请复制后粘贴”之类可本地化说明。
4. 引用来源 Agent/Host 和当前发送目标不一致时，只允许明确用户确认后将引用写入当前草稿，不自动跨 Pane 发送；引用与 Unknown pending 不混淆。
5. 测试：无草稿、有草稿、选区、越界、跨 Agent、已过期 Session、离线、长代码多行、Emoji。

**验收**：用户可轻松引用过去的分析回复，且原有草稿与发送目标不会被覆盖或改变。

### UX-05a｜i18n 资源梳理与中英文覆盖

**唯一目标**：移除 UI 硬编码文案，完成 zh-CN/en 第一版资源覆盖。

1. 扫描 Kotlin Compose/通知/错误/权限引导/设置中的用户可见文案，列清单并给稳定 resource key；**保留原始 host 名、项目名、代码、命令、Agent transcript，不翻译它们**。
2. 默认 `res/values/strings.xml` 提供英语基准，`res/values-zh-rCN/strings.xml` 提供简体中文；必要语言变体按已用 Android 构建规范处理，不在 XML 里写不可解析符号。
3. 使用 `stringResource` / plurals / format arguments 避免字符串拼接；数量/时间/相对时间使用 Locale 合适格式（不要拿“刚刚”硬编码所有语言）。
4. 枚举、错误、连接状态、写操作三态、通知 Channel 与审批提示都本地化，语义必须准确；“Result received”不翻译成“执行成功”。
5. `contentDescription`、`stateDescription`、TalkBack 的数量与动作也本地化；更新测试不依赖硬编码中文语句（尽可能匹配资源/语义）。
6. CI 或本地加入未翻译 key/多语言占位符数量一致性检查；真实未支持的语言统一回退到英语，不能出现空标签。

**验收**：系统设中文时没有大量英文 UI 残留（技术名除外），英文模式无中文字句残留，User transcript 保持原文。

### UX-05b｜App 内语言设置与动态切换

**唯一目标**：提供“跟随系统 / 简体中文 / English”且不丢草稿、会话、滚动与连接状态。

1. 调研当前 `MainActivity`、Compose Theme、Android target/minSdk 与 AndroidX 依赖，选择与现有工程兼容的 Locale API。Android13+ 优先系统级单应用语言设置；旧版本使用已验证的兼容方案。**不要在没有 AppCompat 依赖的工程里盲用 AppCompatDelegate 而引入不必要架构迁移**。
2. 设置页真实展示三种选项；明确语言切换仅作用 UI，而不翻译代码、Pi 对话、日志。
3. 语言变化导致 Activity recreation 时，Composer Draft、Unknown write、主机筛选、阅读锚点不能丢；发送中不可因重建再次提交。
4. 系统/设置语言来回切换测试 `zh-CN→en→system`，深浅色各一次，320dp/1.6 验证英文文案布局。
5. 本地化通知文案与标题；系统权限弹窗由 Android 本身决定，不能承诺随 App 语言完全同步。

**验收**：三种语言模式可用；状态语义正确，动态切换不重复发送或清空草稿。

### UX-06｜会话内搜索与阅读导航（独立可降级）

**唯一目标**：对已缓存、已解析的当前有效会话内容提供搜索和定位，不触碰远端工作流。

1. 先审计现有 Room Transcript 与当前分支可访问的文字；评估当前可支持的搜索边界，若不可从当前数据中稳定检索完整内容，先提供**明确“仅搜索已加载消息”**的局部搜索，不能声称搜索全部远端历史。
2. 搜索入口在 Conversation 顶栏或 More；搜索框、本地匹配条数与上/下一处按钮；以稳定消息 ID 定位，避免只用序号。
3. 支持中文/英文/代码片段/文件路径；最小实现采用 Unicode 归一化合理方式与大小写选择，不对代码内容无谓删空格或修改大小写展示。
4. 当前分支以外的消息默认不参与；ToolResult 是否搜索、是否包含折叠输出要明确标注边界。断线时可搜本地缓存，不连接服务器。
5. 将搜索解析放后台，输入防抖，500/2000 条性能由同数据集单测验证；不因每字输入全量重组界面。
6. 无结果、结果超限、会话切换、分支切换、IME 与返回、浅深主题、TalkBack 都需要覆盖。

**验收**：可以定位真实匹配消息，不误把旧分支结果当当前结果；不可检索范围明确标注。若源数据不足，保留审计报告后标 `PARTIAL`，不做假搜索按钮。

### UX-07a｜语音转文字可行性 Spike（不改主流程）

**唯一目标**：确认当前 Android 真机/AVD 上可用且隐私清晰的 STT 方案；本 Task 不添加假可用入口。

1. 阅读 Composer Spec §7；调研当前 Android minSdk/target、`SpeechRecognizer`、API31+ on-device API、识别语言可用性、`RECORD_AUDIO` 权限、RecognizerListener 生命周期。
2. 做一份兼容性矩阵：Android 26–30、31–34、Android15+；Redmi/MIUI 实测能力（若实际可用）；离线/网络服务可用性必须分别标注，`EXTRA_PREFER_OFFLINE` **不等于保证离线**。
3. 生成隔离的小 Spike 或 Android 测试 Activity，仅运行在 debug/实验变体，**不得后台偷录**，不得将音频发送至自建服务或在日志保留真实语音。
4. 处理 no recognizer、没权限、权限拒绝、无语音、中文/英文、取消/电话中断、服务被系统回收；至少单测覆盖 STT 状态机的纯 Kotlin 部分。
5. 如果设备无可用语音服务，给出允许的降级方案（用户仍可使用系统键盘语音输入），标阻断后继续其他任务。

**验收**：产出 `UX-07a-STT-RESEARCH.md`，注明所依据的 Android API 与真实设备证据，绝不假称设备端离线识别已验证。

### UX-07b｜语音识别 → 文本复核 → 草稿（条件性实施）

**前置门槛**：UX-07a 找到当前项目可使用的本地系统语音识别路径；若没有，则**SKIPPED/NOT AVAILABLE**，不能用网络服务替代而不告知用户。

1. 使用实际可用的系统 `SpeechRecognizer`，请求必要权限且只在用户明确点 Mic 时开始；录音/识别不能在后台自动启动，Activity 离开时 stop/cancel + `destroy()`。
2. 实现 `Unavailable/Idle/PermissionNeeded/Preparing/Listening/Processing/Review/Error/Interrupted` 状态；识别中间结果先放临时区域，不持续覆盖已有 TextFieldState 和中文输入法组合态。
3. 正常宽度（建议≥360dp且fontScale<1.3）显示 Mic，窄屏或大字体收到 More。没有可用 recognizer 时不显示无用 Mic；可在设置说明不支持的原因。
4. 最终文本进入可编辑复核界面，用户明确“插入草稿”后才加入 Composer；“插入草稿”仍不 Send；取消不更改原草稿。
5. 与已有草稿并发更新必须有版本/快照保护，不能覆盖用户在识别期间新写的内容；文本超过8000字符时提示并拒绝超限写入，不静默截断。
6. 识别语言可跟随系统或显式 zh-CN/en-US，不从 App UI 语言推断只能识别一种语言。若语音服务会联网，首次启用时明确提示音频可能交由系统语音服务处理。
7. 测试：拒权/重复拒权、识别失败、取消、被系统中断、服务空结果、中文英文、500字符、输入中继续编辑、Process recreation、TalkBack，至少 AVD + 可用真机用户操作；不记原音频。

**验收**：从点 Mic 到“转写→编辑→插入草稿→手动发送”的完整本地路径可用；没有任何代码路径从识别回调直接调用 `sendPrompt`。

### UX-08a｜主机管理/设置基础视觉一致性

**唯一目标**：使非三屏页面不再显得像 Debug 表单，不添加新 SSH 能力。

1. HostsScreen：显示主机名称、连接状态、必要的认证失败/主机密钥异常，Action 层级清楚；新增/编辑/删除仍使用真实功能流程与二次确认。
2. Settings：按外观/语言/通知与后台监控/连接与安全/隐私与数据/关于分组；只展示真实已实现的选项，未接通功能放设计文档而不加无效开关。
3. 技术信息 Sheet：技术字段按诊断层级分组，Pane ID/Revision/路径保留可复制，Unknown 写入待确认入口不能只隐藏在这里。
4. 所有文案使用 UX-05 的 strings，控件触达≥48dp，浅深模式，窄屏/放大字号。

**验收**：主机管理原功能不回归，取消/删除操作不会误删草稿；新增 UI 既不是空壳也不损失诊断能力。

### UX-08b｜统一空态、异常态、状态与 Motion 完成度

**唯一目标**：消除零散的视觉状态及无意义动画。

1. 空态分别覆盖无主机/在线无 Agent/断线/会话空/消息失败/无搜索结果/无权限 STT；使用图标+简短真实解释+真实可用动作，禁止每个都塞大插画。
2. 动效按 Tokens 实施：按钮轻反馈、分区折叠、消息进入与 Tool 展开；高频/长列表/省电/减少动画时可直接 Snap；不为 Working 添加永不停止的光效。
3. 连接恢复不弹连续 Dialog，不多条 Banner 叠压重要内容；通知与 App 内提示避免同一事件重复打扰。
4. 最终统一图标基线与触摸面积，禁用主操作 Unicode 替代字符，修复 320dp/1.6 下因图标+文字挤压导致的截断。

**验收**：多种异常不会互相覆盖，交互反馈一致，不能因视觉精修破坏真实异常信息。

### UX-08c｜App 品牌图标候选与启动页（不得自动视为最终审核）

**唯一目标**：提供可对比的品牌设计候选，并让启动体验符合原生 Android。

1. 给出 Focus Orbit 的最多3个抽象方向（不是终端提示符或通用机器人脸），附相应色彩/单色轮廓逻辑和轻量 Vector 设计说明。
2. 不擅自下载或提交第三方受版权保护的图标；若缺乏合法可用资源，输出 `BRAND-ICON-REVIEW.md` 而不替换最终正式图标。
3. Android Adaptive Icon 的 foreground/background/monochrome 需使用实际合法资源与不同 Launcher 测试；SplashScreen 使用系统标准机制，不造等待动画或虚假连接状态。
4. 若只有占位方案，明确标注 `PROPOSAL`，不能报告“品牌图标已完成/设计已冻结”。

**验收**：原型/样例与最终资产严格分开，启动不多等待、不泄露敏感状态。

### UX-09｜全局综合 QA、性能回归与交付收尾

**唯一目标**：将分任务实现拼成可用的一致产品，在不夸大证据的情况下形成最终报告。

1. 对照本文件决策表逐项核对；所有 `PROPOSED` 仍保留“待用户最终视觉批准”状态；没有支持的数据不能出现假 UI。
2. 完整回归原先 JVM、Room、Compose UI 用例；检查 16/16 原有 UI 测试及新增测试均通过（若测试数量已变化，以实际项目数据为准）。
3. 在隔离 AVD 用 P1–P8 完整截取工作台/对话/Composer；至少在一台真实 Android 手机上做 393dp附近深/浅、输入法与可访问性手动验收，未做则标未验证。
4. 使用 0/1/2/8/20 Agent、500/2000 条会话与网络失败 Fixture；重点检查发送、Draft、Unknown、缓存/重新连接、工具关联、复制选区、搜索、i18n、STT、返回栈。
5. 记录正式性能测量限制：MIUI 上 instrumentation 与 dropShaderCache 问题仍可能存在；模拟器 Benchmark 仅证明流程，不宣称真机滚动性能改善；对相同 Fixture 的算法 microbench 可以另行记录。
6. 目视核对：文本不裁剪、对齐、拼写/翻译、空白、层级、状态语义色单信号、低对比边界、Send/Copy ≥48dp、不靠颜色单独传状态。
7. 跑一次 Secrets/日志/截图检查；确认未将真实主机密钥、私钥、真实对话或用户草稿引入仓库；确认发行变体不包含 debug-only Fixture 或实验语音入口。
8. `docs/design/ux-run/FINAL-REVIEW.md` 用表格列任务状态、测试证据、截图目录、Commit IDs、仍然未验证事项和建议用户醒后重点视觉检查的3–5点。
9. 只将已通过和可构建的代码提交至工作分支并推送该分支；**不自动合并 main**。

**最终验收等级**：
- `READY_FOR_USER_VISUAL_REVIEW`：可构建，核心功能安全回归通过，已提交真 Android 截图，待用户审美批准。
- `FUNCTIONAL_PARTIAL`：部分功能完成且构建通过，但局部设备/能力未验证。
- `BLOCKED`：无法构建或存在高风险回归，停止，不将分支当作可合并产品。

---

## 4. 任务依赖与跳过规则

| Task | 最小前置 | 缺失前置时怎么办 |
|---|---|---|
| UX-00 | 可读代码 | 无工作树安全条件 → 只读审计并停止写入 |
| UX-01a/01b | UX-00 | 若旧问题已修复，标 `PASS-NO-CHANGE` |
| UX-00a | UX-00 | UI 基线编译失败且非本轮造成：可做只读 Token 审计，暂不改源码 |
| UX-02a–c | UX-00a | Token 缺失先修基础层，不自行硬编码多个页面 |
| UX-03a–e | UX-00a、真实对话源码 | 如果缺可靠原文，只实现本地可保证的复制能力，不造虚假复制全文 |
| UX-04a–c | UX-03a、现有 Draft/WriteLedger | 引用入口不能正确回写草稿时隐藏入口并标 Partial |
| UX-05a | UX-00a | 可早于视觉模块开展扫描，但最终要复测全部新增文案 |
| UX-05b | UX-05a | 不引入超范围 Activity 架构迁移；记录替代方案 |
| UX-06 | UX-03a/03e | 只能搜索已缓存/可访问数据，不造全量远端索引 |
| UX-07a | 真实 Android API 与用户触发授权 | 无设备/语音服务仍可提交调研，UX-07b 跳过 |
| UX-07b | UX-07a 验证可行 | 失败则不加 Mic，保留键盘功能 |
| UX-08a/b/c | 对应主题/页面基础完成 | 图标缺合法素材则仅产候选文档 |
| UX-09 | 所有已完成任务 | 执行所有能跑的验收，诚实标未完成/阻断 |

**重要**：并非每个任务都必须 PASS 才能继续无依赖任务。用户不在时以**最小安全增量**为核心策略。

---

## 5. 最终用户醒后交付格式（严格要求）

只输出一个汇总，不做冗长自我表扬：

1. **Git**：工作分支、baseline SHA、当前 SHA、是否推送、是否合并（必须为否）。
2. **任务进度**：UX-00 … UX-09，各自 `PASS/PARTIAL/BLOCKED/SKIPPED`，commit ID。
3. **功能已完成**：实际可运行的页面/复制/语言/语音/搜索；不要把草稿 Proposal 写成 Done。
4. **验证证据**：Gradle 命令、JVM/Room/UI 测试数量、使用 AVD 与真机型号、截图路径；性能数据须声明 debug/release/emulator。
5. **未完成**：失败根因、是否影响主路径、具体下一步；不向用户隐瞒。
6. **风险检查**：真实 Agent/远端/用户草稿未修改；Unknown 仍不重试；未提交秘密。
7. **用户需要评审的 3–5 个 UI 点**：如工作台主机选择器、Composer 轻量 Surface、Tool 长输出、图标方向、语音授权说明。
8. **最小回滚路径**：基于分任务 commit，用 `git revert <sha>` 而不是破坏性重置。

**可以在无人值守下连续完成所有具备安全前提的 Task，不能因“等用户醒来确认视觉”阻断其它独立任务；但也不能在未经授权的范围扩大到远端、生产、危险写操作或强行合并 main。**
