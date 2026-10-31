# Herdr Android — UI/UX 工程化交付契约 v0.1

> 性质：**后续交给本地 Pi Coding Agent 的通用约束文件（草案）**，并非立即执行全部重构的任务书。
>
> 产品视觉方向：**Graphite × Editorial**（Graphite 的精密秩序 + 阅读型 AI 应用的高质量排版 + Android 原生交互）。
>
> 适用范围：看板、对话、Composer，以及后续 i18n、复制/引用、语音转文字、通知、主机管理等产品体验工作。
>
> 使用前提：执行者必须先核验当前仓库结构、依赖、Git 状态、已有自动化测试和最新决策；下文列出的类名只是已知参考，不能臆造不存在的源码路径。

## 0. 设计与实现之间的契约

1. **设计探索、设计规格冻结、编码、验证是四个独立阶段。** 未经确认的概念示意不能被 Pi 视为最终设计；不允许边写代码边随意决定颜色、布局和交互行为。
2. **一次只做一个可验证的小任务。** 默认变更范围为一个界面或至多一个组件族；如果需要动到核心 SSH、Herdr、Room、消息模型，必须先提交影响分析并停止。
3. **任务中的数字是约束，截图是验收证据。** “精致”“参考 Linear”“优化布局”等形容词不能单独作为验收标准。
4. **优先复用现有 Material 3/Compose 和项目 Design Tokens。** 严禁为了视觉效果新增大型 UI 框架，或跨页面散布新的颜色、字号及 Magic Number。
5. **没有跑过的测试就标为未验证。** 构建成功不等于 UI 正确；模拟器指标不等于真机性能；静态截图不等于交互正确。
6. **真实功能必须保真。** 不改变 Herdr/Pi 已验证的状态语义、会话身份、发送 Accepted/Rejected/Unknown、不自动重试 Unknown、L1 结构化审批限制等。
7. **不以一篇大任务书要求一次重写整个 App。** 本契约作为每张小任务书的公共约束，每个任务还必须给出独立的输入、文件范围、验收清单与回滚点。

## 1. 当前已知基线与禁止回归

以当前 `main` 的实际代码为最终事实来源；已知现状如下（可能随最新提交变化）：

- Kotlin + Jetpack Compose + Material 3，Android `minSdk 26`；模块涵盖 `:app`、`:core:ssh`、`:core:herdr`、`:core:data`、`:feature:board`。
- Android 实现已经包括多主机连接、Herdr 状态看板、Pi JSONL 对话、Markdown 子集、增量映射、Composer 和 Room 草稿。
- Agent 状态只有五种：`blocked`、`working`、`done`、`idle`、`unknown`；`done` 不等于 `idle`。
- 传输连接状态、数据新鲜度和后台监控状态必须分别表达。
- 真实网络操作约 300–500ms；不能假定写入在界面点击时立即成功。
- `pane_id` 是不透明字符串，不得做整数解析或依赖数字顺序。
- `Accepted` 表示远端接受，**不等于** Agent 已执行完；`Unknown` 不能自动重试，不得被绘制为普通发送失败。
- 消息工具结果仅能按可靠 ID 关联，不允许位置猜测；现有 Pi 分支解析必须保持正确。
- 真机 MIUI instrumentation 受限，新 AVD 已经跑通 Compose UI 16/16；不允许为了让测试通过改动真实业务逻辑。
- 现有 `fontScale=1.3` 顶栏裁剪是明确 Bug；Unknown 待确认操作入口不显眼也是待处理项。

### 功能和权限红线

- 不修改远端生产 Herdr Session、SSH/Host Key 配置、全局 Pi/Claude Hook。
- 不引入读屏猜测的一键审批。
- 不制造伪实时状态、伪事件历史、伪进度摘要、伪测试数据。Fixture 必须标明仅用于 Preview/测试。
- 不自动联网发送语音识别结果，也不自动将语音识别结果发送给 Agent。
- 不在 UI 或日志输出 SSH 私钥、敏感命令、敏感草稿、Token 或完整认证信息。
- 不直接新增云端服务、后台推送或上传能力，除非专项批准。

## 2. 设计决策冻结前必须交付的 8 件东西

设计方案应先覆盖 **浅色/深色 × 中文/英文 × 三个关键页面**，至少交付：

1. **IA / Navigation Spec**：主导航、详情层级、Sheet、返回栈、预测性返回、重连导航、通知落地页。
2. **Page Blueprints**：393dp 主设计稿，附 320dp 窄屏与 600dp 宽屏适配规则；低密度/高密度/空态/离线态。
3. **Design Tokens**：颜色、字阶、间距、圆角、边框、语义状态、阴影与动效，注明具体使用条件。
4. **Component State Matrix**：每个组件的 Normal/Loading/Empty/Error/Stale/Disabled/Unknown/Expanded 等状态。
5. **Interaction Contract**：点击、长按、滑动、回退、IME、复制、引用、语音转写，以及必要的二次确认。
6. **i18n Contract**：资源文件、翻译口径、术语、日期/复数、系统/应用语言切换、不翻译 Agent 原始内容。
7. **Accessibility Contract**：文本缩放、TalkBack 朗读、焦点顺序、触控、对比度、减少动效。
8. **Visual QA Matrix**：设备窗口、主题、语言、字体缩放、键盘、在线/离线、Agent 各状态的截图及检查项。

**设计评审通过之前，不要求 Pi 擅自实施全面视觉重构。**

## 3. 视觉 Token 初稿（不是已通过的最终版）

### 3.1 颜色

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| `canvas` | `#F7F8FA` | `#10151D` | 页面背景 |
| `surface` | `#FFFFFF` | `#181F2A` | 页面内主体 Surface |
| `surfaceRaised` | `#EFF2F6` | `#222B39` | Composer、代码/工具区域 |
| `borderSubtle` | `#DFE5ED` | `#344052` | 1dp 细分隔 |
| `textPrimary` | `#172033` | `#EEF2F8` | 任务/消息正文 |
| `textSecondary` | `#4F5D70` | `#AEB9C8` | 项目/主机/时间等 |
| `accent` | `#3556CE` | `#9BB0FF` | 品牌操作与链接 |

说明：候选色值须经组件级对比度测试（普通文字≥4.5:1，大号字≥3:1），不通过就调整；**禁止将色值“已设计”理解为“已完成无障碍验证”。**

语义色使用现有项目已验证的 Status Token 或设计后通过对比度检查的一组新 Token；不能在 Agent 行内同时放状态色圆点 + 状态色文字 + 品牌蓝 Chevron。**每行最多一个主要的彩色状态信号，状态含义始终要有文字。**

### 3.2 排版

| Token | 建议 | 使用位置 |
| --- | --- | --- |
| Display | 26sp / 34sp · SemiBold | 看板标题 |
| Title | 18sp / 26sp · SemiBold | 会话标题 |
| Subtitle | 16sp / 24sp · Medium/SemiBold | Agent 任务名 |
| Body | 16sp / 25sp · Regular | Agent 消息长文 |
| Body Small | 14sp / 21sp · Regular | 列表摘要、工具描述 |
| Label | 13sp / 19sp · Medium | 状态、按钮、元数据 |
| Code | 12.5–13.5sp / 20sp · Mono | 代码/命令/路径 |

注意：不要在关键状态使用 11sp 小字；不用固定 AppBar 高度容纳两行标题而不允许扩展；中英混排和动态字体必须实际查看。

### 3.3 空间与形态

- 基础栅格 4dp；常用间距为 8 / 12 / 16 / 24 / 32dp。
- 页面左右内边距优先为 18–20dp，但应根据窗口宽度与安全区调整，避免 320dp 设备拥挤。
- Agent 普通列表行目标 64–72dp；所有操作性触达区域至少 48×48dp。
- 卡片圆角约 14–16dp；Composer 约 20dp；代码块约 10–12dp。
- 列表优先用低对比度分隔线与排版建立秩序；不要每条内容都放进大卡片。
- 阴影仅用于真正浮层，不能用多层重阴影制造伪精致。

### 3.4 动效 Token

| 交互 | 初版时长 | 原则 |
| --- | --- | --- |
| Tap Feedback | 80–120ms | 轻量点击反馈 |
| Screen Transition | 220–280ms | 轻微位移 + 淡入，支持返回 |
| State Transition | 140–200ms | 状态文字/颜色变化 |
| Expand/Collapse | 180–240ms | 可打断，不强制重排全部列表 |
| New Message Hint | 160–200ms | 低打扰提示 |
| Composer Expansion | 200–280ms | 不影响 IME / 输入焦点 |

必须支持用户的减少动画设置；性能不足时可降级，**不得为了 120Hz 体验把每条消息做持续动画**。使用系统 Material 3 动效约定优先于额外自研动画系统。

## 4. 页面蓝图：A / 工作台（Board）

### 用户目标

打开后 3 秒内看明白：是否需要介入、对应任务、Agent/项目/主机身份、能否继续处理。

### 信息优先级

1. 需要介入的真实状态/行动摘要（不能虚构）。
2. 任务标题：从已有可靠标题来源取得，缺少时有清晰降级文案。
3. Agent 与项目、主机标识。
4. 正在运行/已完成/空闲与未知的其他 Agent。
5. 主机连接状态与缓存新鲜度，按实际需要提高强调等级。

### 推荐布局

- 顶部：产品/页面标题、搜索或设置入口；连接正常时用低强调文案。
- 首段：`需要处理` Section。存在相关事项时以一到两张轻度强调的 Card 展示；没有时不显示巨大空卡。
- 次段：`运行中` 紧凑列表，完整可点击区域；状态词不能只靠颜色。
- `已完成` 和 `其他` 低强调折叠区域。
- 主机选择器默认 `全部` 的前提是数据层真的支持聚合；尚未支持时不能伪显示。
- 底部导航：已实施的功能方可显示活跃入口。不要加入未实现的 `活动` 空壳页。

### 状态矩阵

- Online/live：正常列表，无常驻连接成功 Banner。
- Connecting：已缓存内容可显示，但与“实时”明确区分。
- Stale/offline：明确缓存标记，带刷新/重连操作。
- No Host：有清楚的新建主机入口。
- No Agent：显示“当前未发现 Agent”，区分“主机离线”。
- Blocked：仅描述真实等待；未有结构化审批时不得放“一键批准”。
- Unknown Agent：用文字解释不可确定，不能染成假成功绿。
- Pending Unknown Write：必须提供可发现入口。

### 工作台验收截图

- 0 / 1 / 2 / 12 个 Agent。
- 有 Blocked / 全部 Working / Done / Idle / Unknown。
- 一台主机离线、一台在线；全局数据过期。
- 中文/英语；浅色/深色；窄屏/标准；字体 1.0/1.3/1.6。

## 5. 页面蓝图：B / 对话（Conversation）

### 用户目标

快速回忆任务背景，读懂 Agent 做了什么、工具执行结果、代码和错误，并能引用/复制/继续提问。

### 推荐布局

- Top Bar：返回、任务标题、二级行（Agent / 主机 / 实时状态）、搜索与更多；允许字体放大时自适应高度。
- User：右侧气泡，最大宽度约 82–86%，按实际阅读宽度自适应。
- Assistant：全宽正文阅读型布局，不再用大气泡包住长 Markdown。
- Agent 身份：同一组连续回复头部显示一次，不要每条正文前重复 11sp 绿色 `Pi`。
- Tool/Thinking：独立可折叠记录，摘要先行；大文本按需展示。
- 代码：等宽、长行横向滚动、复制操作、超长输出初始折叠；复制内容不得加入 UI 填充空格。
- 消息操作：复制正文、复制原始 Markdown、选中部分文本、引用到输入框；代码块/命令有局部复制。
- 对话滚动：用户查看历史时新消息不得强制滚到底；低打扰的“新消息”入口可到达最新。
- Stale、断线和 Unknown 写操作应有可解释的入口。

### 交互精度要求

- 无可靠 ToolCall ID 时，不得把结果凭顺序绑定到调用。
- Agent 任务标题、摘要、测试通过数只能来自可靠内容源；不准凭 UI 猜测。
- 选中文本、引用消息只能改变 Composer 草稿，不能直接发送。
- 消息复制默认保留原文，不能把 ANSI/Markdown 渲染方式写回真实文本；复制选项要明确区分正文、原始 Markdown 与代码。
- 处理 URL 时只允许可控协议、由明确点击打开。

### 对话验收截图

- 2 条 / 500 条混合消息。
- 用户与 Agent 长回复、代码块、Markdown、ToolCall/ToolResult（成功/失败/无 ID）、Thinking。
- 长路径、中英混合、超长单行代码、折叠/展开、离线与恢复、新消息提示。
- 文字放大、TalkBack、深浅色、键盘弹出。

## 6. 页面蓝图：C / Composer

### 用户目标

自然输入短指令或长 Prompt，清楚感知发送回执，在弱网、切换会话、应用进程重建时不丢重要内容、不误发送。

### 推荐布局

- 轻量双层输入 Surface：文本区域 + 低强调工具行，不采用巨大悬浮大盒子。
- 输入默认 1–2 行，可扩展约 5 行；保留现有全屏长 Prompt 模式。
- 工具：按需要出现 `+`、语音、展开；发送按钮是唯一高强调操作。
- Enter 默认换行；独立发送按钮。IME、光标、中文输入组合态必须保真。
- i18n 占位、字符数、错误信息和状态文案不应通过硬编码处理。
- Draft 与 WriteOutcome 独立建模：编辑、Sending、Accepted、Rejected、Unknown。
- Unknown 有持久可找回的入口，并且没有自动重试。

### 语音转文字的未来契约（后续独立任务）

- 只做 Dictation：用户启动识别 → 看见转写文字 → 可编辑确认 → 手动发送。
- 麦克风权限拒绝、无本地识别引擎、网络服务不可用时，普通输入照常工作。
- 若使用 Android SpeechRecognizer，需要准确说明隐私和联网边界；不能暗示任何设备都支持纯离线。
- 识别错误的文件名、命令、代码符号允许用户纠正；不得自动执行。
- 不允许录音敏感内容无期限缓存、默认上传到额外服务。

## 7. 国际化与文本规范

- 第一阶段：`en`、`zh-CN`，并支持“跟随系统”；所有 UI、状态、错误、通知、权限引导都必须经 Android 资源本地化。
- 运行时语言切换应保留导航状态、会话身份、输入草稿；不应重连 SSH 或重发消息。
- Pi/Claude 生成的内容、日志、命令、代码、路径不做自动翻译。
- 使用 `plurals` / 格式化资源，不拼接易出错的单词；日期时间使用 Locale/时区本地化。
- 建立术语表（Agent、Session、Host、Blocked、Done、Unknown、Draft、ToolCall）。
- 缺失翻译有静态或测试检查；不得依靠 Pi 自行猜测关键安全文案。

## 8. 设计到代码的映射规则

建议的代码结构方向（**执行前核对仓库现状**）：

- 全局 Tokens：当前已有 `design/Tokens.kt`，允许增补 Token，但禁止让不同页面各自定义新的私有色值。
- 可重用组件：`AgentStatusLabel`、`AgentRow`、`AttentionCard`、`ConnectionBanner`、`AgentMessageHeader`、`ToolResultCard`、`MessageActions`、`ComposerSurface`、`UnknownWriteBanner`。
- 屏幕层：`BoardScreen`、`ConversationScreen`、`HostsScreen`、技术信息 Sheet；屏幕负责组合，不承载底层网络通信和昂贵 JSON/Markdown 解析。
- 依赖方向：`UI → ViewModel → Repository → Transport/Provider`，禁止 Composable 直接发 SSH 网络命令。
- UiState 必须是可预测、可测试的不可变快照；不用随意的 MutableList 原地更新欺骗 Compose。
- 动画优先可中断，必须在系统减少动画设定下正确降级。
- Compose 屏幕不能为“排版方便”重新解释 Agent 状态语义。

## 9. 单任务书（Task Packet）必需模板

**每一张任务书必须填写以下字段，不允许空缺：**

1. **Task ID / 名称 / PR 目标**：如 `UX-01 Board hierarchy`。
2. **单一用户收益**：一句话描述用户行为改善，不写“整体优化”。
3. **源代码基线**：提交哈希、当前分支、必须阅读的文件、复用组件。
4. **前置依赖**：上一任务是否完成、是否有通过的 Design Spec。
5. **输入数据模型**：真实 UiState 字段、Fixture 场景、无法可靠提供的数据。
6. **允许改动文件**：明确包/类/资源；如果实际路径不同先报告。
7. **禁止改动范围**：网络、数据库、Herdr、远端 Session、其他页面、协议、未知写操作等。
8. **像素/布局规范**：适配窗口、Token、信息顺序、间距/字阶/圆角、响应式规则。
9. **交互状态表**：每一状态的文案、视觉、按钮、禁用逻辑和后续动作。
10. **实现顺序**：检查现状 → 最小组件 → 状态 → 自动化测试 → 截图 → 视觉复核 → 修复。
11. **测试计划**：单测、Compose UI、无障碍、i18n、截图、真机/AVD、性能回归。
12. **必须交付的证据**：测试命令+结果、截图路径、diff 概述、已知限制、回滚方法。
13. **验收标准**：每个标准是二值且可核实，禁止“看起来好”。
14. **停止条件**：设计不清、API 与现实不符、修改超出范围、测试有无法定位的失败、涉及敏感操作时停止并说明。

### Task Packet 的标准输出结构

```
Task: UX-XX
Baseline: <git rev-parse HEAD>
Scope: <allowed files/classes>
Design spec: <approved artifact/version>
Plan: <5-10 numbered steps>
Implementation: <changed components>
Verification:
  unit: <command, result>
  compose-ui: <command, result>
  screenshots: <paths and scenarios>
  manual-device: <result and device>
  accessibility: <checks>
Risks: <specific unresolved items>
Unexpected changes: <yes/no + justification>
Decision: PASS / BLOCKED / FAIL
Next: <next task only; do not start>
```

## 10. PR 与质量门槛

- 一次 PR 默认只包含一个目标和一套对应 UI 组件，不混杂主机、协议、Room schema、Markdown parser 的大型改动。
- 变更前要保留参考截图；变更后需覆盖相同 Fixture、相同主题/语言/字体，并留下并排对比。
- 新增显示文案必须进入本地化资源；新增状态不允许只通过颜色表达。
- 每张可交互组件保留 Semantics 和不低于 48dp 的点击区域。
- 原有已通过的 Compose UI 与 JVM 测试不能回归；新增测试必须执行，不得只编译。
- 实机 MIUI 不能运行 Instrumentation 时用隔离 AVD 跑自动化，并单独做真机人工视觉验收。
- 高风险行为（审批/重复发送/权限）除了 UI 测试，还必须有领域层单元测试。
- 存在未解释的 Crash、覆盖输入、错误目标发送、草稿丢失、状态虚报时禁止合并。
- 不宣称长列表性能改善，除非有可比条件的测量；不要为了视觉而无约束增加布局、阴影与动画。

## 11. 推荐分批策略（请勿一次全部执行）

| 里程碑 | 仅涵盖的内容 | 出口条件 |
| --- | --- | --- |
| `UX-D0` | 补齐 IA、3 页 Blueprint、Token、States、Visual QA，**不改业务代码** | 人工评审通过设计规范 |
| `UX-01` | 修复字体缩放裁剪、行内代码空格等明确 Bug | 320/393dp + 字体 1.0/1.3/1.6 无裁剪；复制原文一致 |
| `UX-02` | 工作台信息层级与组件规范 | 小/大数据、Blocked/离线、深浅色正确；原测试不回归 |
| `UX-03` | 对话阅读排版与工具视觉 | 长文/代码/工具/未知配对皆正确，长列表不恶化 |
| `UX-04` | Composer 视觉与操作反馈 | IME、草稿、发送三态、Unknown 全部通过 |
| `UX-05` | i18n 中英双语基础 + 运行时语言切换 | 文案覆盖、状态不丢、无重连/重复发送 |
| `UX-06` | 复制/选择/引用与会话搜索 | 复制原文准确、明确行为、正常窄屏 |
| `UX-07` | Android 语音转文字（独立 Spike 后才开发） | 权限、隐私、识别错误、编辑确认、无自动发送 |
| `UX-08` | 品牌、图标、状态空页、动效细节 | 深浅色/无障碍/性能/真实设备视觉验收 |

说明：以上为分工建议，**不是一次性交给 Pi 并要求自动执行的执行计划**；每一阶段必须单独生成更细的 Task Packet，且逐批验收。

## 12. 最重要的失败防护

当 Pi 碰到以下情况，必须停止当前动作并给出可核实的证据：

- 找不到指定的类、文件或界面状态，不能凭空创建一套不兼容的新架构。
- Designer 规格互相矛盾或无法在 Compose 中按定义实现。
- 发现操作会影响 SSH、远端 Agent、敏感数据或现有生产 Session。
- 发现测试用例只有编译成功、无法运行，或截图无法复现。
- UI 因字体放大、语言变更、IME 出现裁剪、遮挡或误触发送。
- 对用户展示了不真实的状态、没有可靠数据的摘要、过时但未标记的审批入口。

**交付核心要求：Pi 不应该靠审美猜测、经验猜测或隐式假设完成任务；它应能逐条核对可执行的指令，并用真实证据验收。**
