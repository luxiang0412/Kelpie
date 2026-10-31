# Herdr Android · UX-D0 / 对话（Conversation）正式设计规格 v0.2

> **文档性质**：设计评审稿；不授权 Pi 编码。
> **本轮冻结决策**：`CONV-ACTIONS-001` —— Agent 回复末尾常显轻量「复制 + 更多」；正文长按保留系统文字选择（2026-11-01 用户确认）。  
> **视觉方向**：Graphite × Editorial（D 方向）。  
> **日期**：2026-11-01。  
> **关联规范**：`HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md`、`HERDR-UX-D0-BOARD-SPEC-v0.2.md`、`STATUS-BRIEF-FOR-EXTERNAL-REVIEW.md`。  
> **已确认上游决定**：工作台 `done` /「已完成」分区 **默认收起（A）**。  
> **数据/实现边界**：下文区分“目前已有”“建议新增”“设计规则”。不得把虚构的任务摘要、工具结果、错误、时间与审批权限当作真实 Herdr 数据。

## 0. 一句话产品目标

在狭窄的 Android 屏幕上，让用户在 **3 秒内恢复当前 Agent 任务上下文**，随后能够**清楚阅读 → 准确复制/引用 → 理解工具结果 → 安全继续输入**。对话不是终端字符流，也不是通用社交聊天。

**成功条件（待实现、待验收）**：

1. Agent 的 Markdown 正文有阅读节奏；不把每一段封装成大气泡。
2. 工具执行默认是可扫描的工作记录；没有可靠数据时不虚构工具结论。
3. 全部可见文本、代码、路径都能准确复制；原始 Markdown 与渲染后文本明确区分。
4. 新消息不打断用户阅读历史，回到底部的控制由用户掌握。
5. Pending/Unknown 写入、离线与缓存数据有可发现的、互不混淆的提示。
6. 320dp 窄屏、1.6 字体缩放、中文/英文都不裁剪核心操作。
7. 500/2000 条混合消息维持增量处理和稳定列表身份；性能主张必须有可比测试数据。

## 1. 源事实、禁止推断、实现现状

### 1.1 从最新项目状态得知

- Android：Kotlin + Jetpack Compose + Material 3；当前存在 `ConversationScreen`、`TechnicalInfoSheet`、`ConversationItem`/增量 Mapper、自研 Markdown Parser、`MarkdownAstCache`、Room 缓存与草稿库。
- 已真实验证 Pi Session 的消息发送、JSONL 追加及手机展示；Composer 具有 1–5 行和全屏长 Prompt；三态 Accepted/Rejected/Unknown。
- Markdown 当前支持标题、粗体/斜体、列表、引用、行内码、`http(s)` 链接和 fenced code；表格、图片、嵌套列表、Diff 尚未完整支持。
- 工具结果只在可靠 `toolCallId` 相等时配对；没有可靠 ID 的结果须独立显示。`id/parentId` 表示 Pi 分支关系。
- 已有 `新消息` 控件和跟随底部逻辑，但真实长会话、上滑阅读的验证仍待完成。
- 已知问题：字体放大裁剪顶部栏；行内代码靠插空格模拟 Padding；对话仍缺成熟的消息复制/局部选择/引用操作。
- 当前 MIUI 真机 Instrumentation/Benchmark 受系统限制，独立 AVD 可运行 Compose UI 测试；不能以模拟器帧时冒充真实 120 Hz 真机性能。

### 1.2 不允许 UI 自行推断

- `blocked` 不等于存在可批准的结构化审批对象。
- `done` 不等于 `idle`；`Accepted` 不等于 Agent 已执行完成。
- 缺少结构化工具结果时，不得编造「16 passed」「用时 2s」「工具运行中」等显示。
- 无可靠 Agent Session Identity，不得把旧消息/旧草稿归属伪装成当前 Agent。
- 不能通过相邻位置“猜测” ToolCall↔ToolResult；分支切换不应混进旧分支结果。
- 不能因为 TCP 连接在线，就假定最新 transcript 已同步。
- 不做读屏一键批准；Unknown 写操作绝不自动重发。

## 2. 页面职责与导航边界

**对话页为完整二级目的地**：从工作台一击进入，返回时恢复工作台筛选及滚动；对话不出现全局底部导航。

页面拥有：
- 可读顶栏：返回、任务标题、Agent/主机/状态摘要、搜索（后续）、更多操作。
- 连接/数据有效性提醒：仅需要时出现。
- 消息阅读区：用户、Assistant、Thinking、工具、系统/未知内容。
- 新消息/回到最新入口：位于列表之上、Composer 之上。
- 发送结果未知提示：始终具有可找回入口。
- Composer：独立子规范 UX-D0 Composer 决定其内部视觉，此文只规定与消息区的布局关系。

**Sheet/次级页面**：技术信息（Pane、Session 路径等）、完整工具输出、文件/Diff（后续）、会话搜索（后续）。不能把技术字段重新放回顶部主体。

## 3. 主要布局（Compact 393dp 蓝图）

```text
┌─────────────────────────────────────────┐
│ ‹  优化数据库查询性能           搜索  ⋯  │ ← 顶栏主行，不固定高度
│    Pi · Dev-01 · 运行中                   │ ← 二级信息；窄屏可换行
├─────────────────────────────────────────┤
│ [显示缓存 / 正在恢复]                 ×   │ ← 仅异常时出现
│ [发送结果未知]                  [查看]   │ ← 仅存在 Pending/Unknown 时
│                                         │
│                           ┌───────────┐ │
│                           │ 用户指令…│ │ ← 用户气泡靠右
│                           └───────────┘ │
│                                         │
│ ◈ Pi                                    │ ← 一组连续回复仅标一次
│ 已检查项目配置，发现两处需要修改。       │ ← Agent 正文全宽排版
│                                         │
│ 检查结果                                │ ← Markdown 标题
│ 1. 构建设置需要更新                     │
│ 2. 需要补齐断线测试                     │
│                                         │
│ ┌───────────────────────────────────┐   │
│ │ ✓ 运行测试               [展开 ▾] │   │ ← 工具摘要
│ │ ./gradlew test… · 结果已收到       │   │
│ └───────────────────────────────────┘   │
│                                         │
│ 接下来建议验证实际设备上的滚动性能。     │
│ [复制] [更多 ⋯]                         │ ← 消息操作，非一排小按钮
│                                         │
│             [↓ 3 条新消息]              │ ← 用户离开底部时才出现
├─────────────────────────────────────────┤
│ [Composer 输入区……                 ↑]   │ ← 同一键盘/Insets 体系
└─────────────────────────────────────────┘
```

**线框内容仅说明布局，不代表已有可靠的 Agent 任务摘要或工具执行统计。**

### 3.1 栏位与排列顺序

1. 顶栏（不跟随消息滚动）：返回、任务标题、Agent/主机/Agent 状态、更多；搜索在实现前不显示假按钮。
2. 可选状态条：离线/同步/过期；Pending Unknown 写入独立且优先可见。
3. 消息列表（唯一主垂直滚动容器）。
4. 悬浮新消息/回底部入口：不能覆盖正文操作或 Composer。
5. Composer：屏幕底部，与键盘/导航栏安全区联动，内容显示区随之调整。

### 3.2 顶栏规则

- 顶栏主标题按真实任务标题显示；缺失则回退为可靠的会话名称或 Agent 类型，不凭空概括任务。
- 标题建议 `18sp/26sp Medium/SemiBold`，最多 2 行；长路径、UUID 不放主标题。
- 二级信息 `12–13sp/18sp`，包含 Agent、Host 和真实 `working/blocked/done/idle/unknown` 状态。网络/数据有效性不混进 Agent 状态色。
- 大字号/窄屏下，按钮保持 ≥48dp 点击区域；文字允许增加高度。**禁止固定高度双行 TopAppBar**（已发现裁剪）。
- 没有可靠的“最后活跃于 X 分钟前”，就不显示相对时间。
- 多操作空间不足时只保留返回与更多；未实现的搜索不显示无效入口。

### 3.3 Compact / Medium / Expanded

| 内容 | 320–359dp | 360–599dp | ≥600dp（预留） |
|---|---|---|---|
| 外侧水平安全边距 | 16dp | 18–20dp | 内容最大宽度 720dp 居中；按窗口自适应 |
| Agent 正文 | 占满内容栏 | 占满内容栏 | 阅读宽度上限约 680–720dp |
| User 气泡 | 最大 88% 且不超可用宽 | 最大 84–86% | 最大 520dp |
| 顶栏 | 操作折叠到菜单、标题可换行 | 常规两行 | 可扩展为宽屏标题与工具栏 |
| Tool/Code | 铺满正文宽，局部横向滚动 | 同左 | 更宽显示，不强制拉伸正文 |
| 字体 1.3/1.6 | 允许容器增高 | 同左 | 同左 |

依据的是 **Android window dp**，不是 1080px 等物理分辨率。

## 4. 字体与文本内容层级（Typography）

仅使用现有统一 Token/Material 3 Type Scale 派生的样式，不允许在消息 Composable 中散落字号字面量。

| Token（候选） | 字号 / 行高 | 字重 | 用途 |
|---|---|---|---|
| `chatTitle` | 18sp / 26sp | Semibold | 顶栏主标题 |
| `chatMeta` | 12.5–13sp / 18sp | Regular | Agent/主机/状态元信息 |
| `chatBody` | **16sp / 25sp** | Regular | 用户与 Agent 正文（阅读核心） |
| `chatH1` | 21sp / 29sp | SemiBold | Agent Markdown 一级标题，不可压过页面标题 |
| `chatH2` | 18sp / 26sp | SemiBold | Markdown 二级标题 |
| `chatH3` | 16sp / 24sp | SemiBold | Markdown 三级标题 |
| `chatCaption` | 13sp / 19sp | Regular | 工具摘要、来源信息、时间 |
| `chatAction` | 13–14sp / 20sp | Medium | 复制/展开操作 |
| `chatCode` | 13sp / 20sp | Mono | 代码、命令、路径；可保留字体缩放 |

- 字体家族优先使用系统 Sans/Monospace，不打包额外字体作为默认条件；保持中英混排和系统字体一致性。
- `11sp` Pi 绿色标签不再使用。Agent 身份为一组 Assistant 输出最前面的 13sp 中性标识（可配 20–24dp 图标）；同一组连续输出不重复。
- 正文支持自然换行、选择与大字号；文本超长不依赖 `softWrap=false` 全局硬限制。
- 建议段落间距 10–14dp；Markdown 标题上距 18–22dp、下距 8–10dp；连续列表项间距约 4–6dp，列表与下一段 10–12dp。
- 不要为了“看起来整齐”在行内代码源码前后拼接空格。

## 5. 消息排版规则

### 5.1 User（用户）消息

- 靠右，最大宽度按第 3 节；气泡采用 `SurfaceRaised` 的微蓝/中性色调，不用高饱和品牌蓝作为整段背景。
- 圆角 16dp，靠尾部一角可用 6dp 作为轻微角色识别；内边距水平 14dp、垂直 10–12dp。
- `chatBody`，保留输入中的换行/代码格式；过长消息可以按需折叠，但必须有完整查看和复制路径。
- 用户消息之间可使用 12–16dp 间隔；用户与 Agent 段落之间 20–24dp。
- 仅当存在可靠回执时显示“已接受”；没有交付到 Agent 的证据不显示“已执行”。

### 5.2 Assistant（Agent）正文

- **无大气泡、接近全宽**：内容与页面阅读网格左对齐，背景为 Canvas。
- 连续的一个 Assistant 回答作为内容组呈现；Agent 署名在组首出现一次，不在每个 paragraph / tool 上重复。
- 单条长回答之间通过空间而非重边框区分；不使用角色颜色来装饰每段文字。
- 每条已完成消息末端：一处低强调「复制」入口 + 「更多」菜单（具体操作见第 8 节）；持续流式更新时操作可延后至内容稳定，不影响文字选择。
- 纯英文、纯中文、中英混合均以阅读节奏与自然换行为优先。

### 5.3 Thinking

- 默认折叠显示一行「思考内容」或实际来源允许显示的摘要，不自行创造“推理了 X 秒”。
- 只显示数据源中实际提供的内容；如果 Agent 不提供 thinking，不显示空项。
- 展开后使用次级 Surface、较低字重/颜色，但仍满足对比度；Thinking 内文本可选择与复制。
- 关闭 App 或重组不能导致同一消息的折叠状态无故跳变；切换分支后按新分支身份管理状态。

### 5.4 系统 / Unknown / 不受支持内容

- 使用中性说明「此内容暂不支持预览」，提供「查看原文 / 复制原文」；禁止丢数据或将未知数据渲染成成功工具结果。
- 错误与失败使用文字+图标；颜色是辅助，不是唯一信号。

## 6. Markdown 的渲染与降级（不要求本轮扩展 Parser）

| 元素 | 视觉与行为 | 目前/未来 |
|---|---|---|
| 段落 | `16/25sp`、自然换行、10–14dp 段距 | 当前增强样式 |
| H1/H2/H3 | 21/29、18/26、16/24；分别与段落留间距 | 当前 Markdown 已有标题语义，需映射层级 |
| 强调（粗/斜） | 字重/斜体，不增加角色色 | 已有 |
| 有序/无序列表 | 悬挂缩进，序号/项目符与文本基线对齐 | 已有，细化排版 |
| 引用 | 左侧 2dp 中性线，内边距 12dp；正文仍可选择 | 已有，视觉完善 |
| 行内代码 | 等宽 + 背景色 + 适当 padding；**复制不含任何人为补空格** | 已有 Bug，先修 |
| 链接 | 仅 `https`/`http` 可点击；下划线或可辨识风格、TalkBack 可读 | 已有，安全保持 |
| Fenced code | 语言标签、复制、水平滚动、展开 | 已有，规格在第 7 节 |
| 表格 | 不支持时按原文保真展示；不能渲染为错乱列 | 后续独立任务 |
| 嵌套列表 | 不支持时降级为可阅读的纯文本，禁止丢内容 | 后续 |
| 图片 | 明确占位/可打开来源，不能自动加载不可信远程 URL | 后续 |
| HTML / 脚本 | 不执行、不渲染主动 HTML，纯文本或安全降级 | 安全硬约束 |
| LaTeX / Diff | 先保留原始内容；后续专项 | 后续 |

**渲染正确性优先于花哨语法**：Markdown AST 来自已有 Parser；完整重写 Markdown Parser 不属于 UX-D0。仅当真实样例表明核心语法损失严重时，再开独立技术选型任务。

### 6.1 行内代码细节

- 单段行内代码背景为 `Raised`，圆角约 4dp；理想可见左右内边距 4–6dp、垂直 1–2dp。
- **不能通过在原始文本两端增加空格模拟 Padding**。Compose 的 Text Span 不能直接拥有 Box padding；需要评估符合文本选择与基线的 InlineTextContent/占位实现，或退化成不插空格的背景 Span。若方案引入基线/选择错误，以正确复制和布局为先。
- 中英相邻、标点相邻时没有不可见“人为字宽”。

## 7. Code、Tool 卡片与长输出（精细化设计 v0.2）

> **决策状态**：Tool 卡的视觉/行为属于设计提案，已确定的唯一强制前置事实是**可靠 ID 关联**和**不得虚构执行结果**；本节提供默认实施建议，仍需在真机截图确认视觉细节。与 v0.1 第 7 节冲突时以本节为准。

### 7.1 Tool 卡片的角色与优先级

Tool 卡是 Agent 工作记录，不是另外一个聊天气泡、不是模拟终端，也不是审批卡。用户应在 1 秒内看懂「调用了什么工具、结果有没有回来、是否有可核实的错误」，需要时再展开参数、命令与输出。

组件分为四层：

1. **Header（始终可见）**：中性图标 20dp + 可读工具名 + 状态文字/图标 + 折叠箭头。头栏最小 52dp；整个头栏可点，但独立操作区不触发展开。
2. **Summary（最多 1–2 行）**：仅使用可验证的 `toolName` / 命令前缀 / 文件路径 / 真实退出码或明确错误字段。未知时宁可显示「结果已收到」而非自动猜测执行成功。
3. **Detail（按需展开）**：明确标记「参数」「命令」「输出」，代码/路径采用等宽字体，输出默认摘要最大 12 行；详情随卡片展开但不在消息流形成固定 260dp 的内部竖向滚动框。
4. **Actions**：复制命令、复制参数、复制结果、查看完整输出；只显示数据实际存在、当前可执行的操作。若卡片无法提供可靠源值则不展示相应复制操作。

### 7.2 393dp 结构线框

```text
┌──────────────────────────────────────┐
│ ›  ⌘  bash              结果已收到   │ ← 52dp Header，状态文案不是推断成功
│    ./gradlew testDebugUnitTest      │ ← 真实命令前缀；一行省略
└──────────────────────────────────────┘

点击展开：
┌──────────────────────────────────────┐
│ ⌄  ⌘  bash              结果已收到   │
│──────────────────────────────────────│
│ 命令                            [复制]│
│ ./gradlew testDebugUnitTest          │
│──────────────────────────────────────│
│ 输出（预览）                         │
│ > Task :app:testDebugUnitTest …     │
│ …（最多 12 行）                      │
│ [查看完整输出]           [复制结果]   │
└──────────────────────────────────────┘
```

视觉建议（候选 Token，统一来自 Design System，不允许私自硬编码）：

| 部位 | 规格 | 说明 |
|---|---|---|
| 容器 | `surface`/`surfaceRaised`；1dp `borderSubtle` | 深色不叠重阴影 |
| 圆角 | 12dp | 与代码块统一 |
| 横向内边距 | 12dp，窄屏最小 10dp | 不挤压操作触达区 |
| Header | ≥52dp；图标 20dp；展开箭头 18–20dp | 文字可换行、容器自然增高 |
| Tool Name | 14sp/20sp Medium | 无需单独品牌色 |
| Summary | 13sp/19sp Secondary | 最多 2 行，截断能查看全文 |
| 状态 | 12–13sp/18sp + 可辨图标 | 不仅靠颜色 |
| Detail Label | 12–13sp/18sp Medium | 参数/命令/输出 |
| Detail Code | 13sp/20sp Mono | 保留缩进与换行 |
| Detail 垂直间距 | 8–12dp | 相邻 Tool 卡间距 8dp |
| Copy 操作 | 图标 20dp，实际点击 ≥48×48dp | 不得挤压到不可点击 |

### 7.3 状态语义矩阵（禁止误报）

| 真实证据 | 用户可见文案（zh / en） | 视觉 | 禁止 |
|---|---|---|---|
| ToolCall 存在，无对应 ToolResult | `尚未收到结果` / `Result not received` | 中性时钟/信息图标 | 声称「执行中」或「失败」 |
| 可靠 ID 对应 ToolResult 到达 | `结果已收到` / `Result received` | 中性勾/收件图标 | 自动标记「成功」 |
| 源数据明确标记错误 | `执行失败` / `Execution failed` | 小红色语义图标及文字 | 根据输出包含 `error` 猜测 |
| 源数据明确给出退出码 | `退出码 {n}` / `Exit code {n}` | 中性色或明确错误色，按协议字段 | 由 0 推断测试通过条数 |
| ToolResult 无可靠关联 ID | `未关联的工具结果` / `Unmatched tool result` | 中性独立卡 | 按顺序绑定相邻 ToolCall |
| ToolCall/Result 属于旧分支 | 当前分支不展示 | 无 | 跨分支显示旧结果 |
| 内容暂无法解码或不支持 | `暂不支持预览` / `Preview unavailable` | 中性说明 + 原文入口 | 丢弃源数据 |
| 正在离线阅读旧缓存 | 卡片保持历史原状态，整页标识缓存 | 不额外给历史卡片染色 | 把历史调用当实时任务 |

UI 只能呈现源事实。使用 `result received` 与 `completed successfully` 的语义严格不同。不能显示伪造的“运行 XX 秒”“XX 个测试通过”。

### 7.4 Tool 群组、排序与折叠

- 同一 Agent 回答内 Tool 卡跟随结构化 transcript 的实际顺序；不能跨 Agent 分支合并。
- 连续超过 3 个 Tool 卡，允许增加弱强调的「工具操作 · 5 项」分组标签，但不允许把其中单条数据消失；是否合并为可折叠组作为后续 UX 专项。
- 所有 Tool 卡**默认收起**。用户展开后，切换视图或屏幕重组不应无故折叠；折叠状态 key 必须含 Host、Herdr Session、Agent Session/Occupancy、Branch、ToolCall ID。
- 输出先于调用到达时，在可靠 ID 出现后回归调用的位置，不额外生成第二张重复卡。
- 对于没有关联 ID 的结果，使用独立卡保留其来源和完整文本入口。
- 展开卡片引起高度变化时尽量保留可见阅读锚点；不要为整个 500/2000 条列表应用 `animateContentSize`。

### 7.5 长代码/长输出行为

- 代码块 ≤12 行原样可读，超过默认展示约 12 行并提供「展开/查看完整」。
- 长输出**优先在原卡内展开有限预览**，超过大段高度后使用「查看完整输出」的次级全屏阅读页；不在 LazyColumn 内制造嵌套竖向滚动。
- 完整输出页支持：选择文本、复制全文、关闭返回、按需加载；大文本必须有容量/分页策略，不能静默截断后伪称“完整”。
- 超长单行代码保持局部水平滚动，不改变整个对话页横向布局。
- ANSI 序列不执行；复制原始输出时应从原始数据来源提取，而不是复制被格式化/截断的 UI 字符串。
- 没有可取回的完整文本时应直说「仅保存了部分输出」，并只提供「复制可用内容」，不得承诺查看或复制全文。

### 7.6 Thinking 与 Tool 的视觉区分

- Thinking：极轻的弱强调折叠行，可选中性思考符号，无工具名、无执行结果态。
- Tool：紧凑边框卡，工具名称为第一阅读入口，存在可查证的关联/结果状态。
- Assistant Body：不包卡，16sp/25sp 阅读正文；这三者不能全部做成等权卡片。

### 7.7 Tool 子组件边界（供后续拆 Task Packet）

建议复用的逻辑组件：`ToolRecordHeader`、`ToolRecordSummary`、`ToolRecordDetail`、`ToolRecordActions`、`UnmatchedToolResult`。**仅是设计接口建议，Pi 执行前必须核对仓库实际类名和文件位置**；不得因建议名称而创建第二套重复数据层。

---

## 8. 消息操作规范（决策已冻结：A）

> **冻结决策 CONV-ACTIONS-001 / Approved（2026-11-01，用户明确选择 A）**：**已完成的 Agent 回复末尾常显低强调「复制」+「更多」；正文长按使用 Android 系统文本选择。** 后续工程实现不得擅自换成“全部放进更多”或“长按只开自定义菜单”。

### 8.1 操作信息架构与优先级

| 触发位置 | 一级操作 | 二级操作 | 规则 |
|---|---|---|---|
| Agent 正文组末尾 | `复制` + `更多 ⋯` 常显 | 复制原始 Markdown、引用到 Composer、分享（若已支持） | 字体/图标次级色，无重型卡片背景 |
| User 气泡 | 长按可选文字；低强调 `复制` 可在尾部/菜单 | 复制原文、引用（如产品实际需要） | 消息操作可读可达，不能因为气泡窄造成按钮挤压 |
| Markdown code block | 标题栏 `复制代码` 始终可见 | 复制原始块（仅有真实原文时） | 复制不包含 ``` fence 标记或 UI 空格 |
| Tool record | 展开后显示存在的复制动作 | 复制命令/参数/结果/查看完整输出 | 无真实字段时不出现不可用假按钮 |
| Thinking | 展开后允许选取内容；更多中复制原文 | 无 | 不与正文合并复制，除非用户明确选择 |
| System / Unknown item | 原文可复制或说明不可用 | 查看原始数据 | 不能把错误信息伪装成 Assistant 回复 |

**条目末尾动作的建议布局**：

```text
Agent 正文结束。

[ ▢ 复制 ]  [ ⋯ 更多 ]                       刚刚（仅有真实时间时）
```

- 视觉图标 18–20dp、标签 13sp/19sp；两个动作均有 ≥48×48dp 点击区域，横向间距建议 4–8dp。
- 小宽度和大字体时允许折为两行或将可选的时间信息隐藏，但**不能把「复制」完全隐藏**。
- 不使用一直悬浮在每段文字上的复制图标；只在可识别的 Agent 消息/消息组末尾保留一个操作栏。
- 流式更新期间如复制没有稳定的消息边界，可展示操作但需明确复制的是**当前可用内容**；绝不能标记完整或已完成。若实现无法保证内容快照正确，可以在消息稳定后启用复制。
- 若复制的正文由多个子块拼接，必须事先定义包括/排除规则并用单测锁定。

### 8.2 长按选择 vs 菜单：手势优先级

1. 对 Agent 正文文本进行长按 → 系统选中词语/拖动选区；不得被父级 `combinedClickable(onLongClick=...)` 吞掉。
2. 点击 `复制` → 将当前消息组纯文本正文一次性复制；不影响文本选区或会话滚动。
3. 点击 `更多` → 打开锚定菜单或小型菜单 Sheet；关闭后恢复原阅读位置。
4. 长按代码文本 → 系统选择；点击代码块专属复制 → 复制完整代码正文。
5. Tool Header 点击展开/收起，不能和内部 Copy 按钮触控区域重叠。
6. TalkBack 阅读中，各动作具有独立可辨名称、焦点顺序和 ≥48dp 可操作区域。

实现技术提示：`SelectionContainer` 对只读文本选区；按需要用 `DisableSelection` 排除工具栏/控件；对自定义 Markdown 多 Composable 组合，选择是否跨段落连续可选属于需真机验证的实现约束。官方参考见文末。

### 8.3 复制内容“字节级语义”

| 操作 | 应复制 | 不应复制 |
|---|---|---|
| `复制正文` | 可阅读的纯文本、段落和列表分隔，源代码内容（若正文包含） | Agent 署名、时间、UI 文案、独立 Thinking/ToolResult |
| `复制原始 Markdown` | 原始 Markdown 字符串 | 经过布局转换后丢掉标记的文本 |
| `复制代码` | Fence 内实际代码内容及其真实换行 | fence、语言名、虚假左右空格、行号 |
| `复制命令` | 真正的命令字段 | 描述文字、凭空加的引号 |
| `复制 ToolResult` | 明确区分「复制当前预览」与「复制全部原文」 | 将折叠省略后片段标作全文 |
| `选择文字` | 用户实际选区 | 自动扩成整条消息 |
| `引用到输入框` | 带清晰边界的可编辑文本 | 自动发送、覆盖已有草稿 |

文本语义通过确定性 Fixture 单测固定，包括中文标点贴着行内代码、Tab、CRLF、emoji、多语言文本、超长行、UTF-8 与不完整 JSONL 的降级行为。不能用渲染后 `Text` 文案反推原始 Markdown。

### 8.4 复制反馈与敏感内容

- Android 13+ 已有系统剪贴板反馈，通常不再弹重复“复制成功” Toast。Android 12L 及以下可用简短 Snackbar。
- 需要保护敏感内容时使用官方 `ClipDescription.EXTRA_IS_SENSITIVE`；默认谨慎对待原始工具输出、路径与代码，避免在锁屏或剪贴板预览泄露 Token。
- 对于尚未读取/被截断的远端大输出，不做默默复制部分内容；优先提示用户先查看完整内容或使用明确的“复制已加载部分”。
- 剪贴板操作属于本地复制，不自动向远端 Agent 发送任何消息。

### 8.5 引用消息与草稿冲突

- 引用仅追加到 Composer 草稿或插入光标处（两种策略需在 Composer 文档选定），不能直接触发 Send。
- 如果草稿已存在，不能覆盖；引用长度超出现有 8000 字符上限时，应在修改前阻止并给出“先复制”替代操作。
- Unknown Pending 记录与普通草稿分离，引用不能覆盖 Unknown 操作原文或其账本。
- 切换 Host/Session/Agent 后，必须再次核对目标身份，不能把历史消息引用直接发送给新 Pane。

### 8.6 无障碍/i18n 语义

- `复制` / `Copy`、`更多选项` / `More options`、`复制原始 Markdown` / `Copy raw Markdown`、`引用到输入框` / `Quote in composer`、`复制代码` / `Copy code` 各自有清楚的 Content Description。
- 正文选区仍支持系统选择和放大控制；TalkBack 焦点先正文再操作栏，不要对屏幕阅读器重复朗读复制的整条正文。
- 语义、资源字符串使用 Android `strings.xml`，不能将中英文文案写死在 Compose 中。

### 8.7 状态下的动作可用性

| 场景 | 复制 | 更多/引用 | 原因 |
|---|---|---|---|
| 正常在线已完成消息 | 可用 | 可用 | 纯本地阅读操作 |
| SSH 断线、展示缓存 | 可用 | 引用可编辑草稿，但发送禁用 | 复制不需要联机 |
| 正在流式更新 | 可用但明确当前片段，或稳定后启用 | 引用必须基于不可变当前快照 | 不能复制尚未发生的内容 |
| ToolResult 未配对 | 可复制独立原文 | 没有“复制关联命令” | 避免伪关联 |
| 原文未加载 | 只能复制已加载部分并明示 | 可尝试“查看原文” | 不伪装完整 |
| 写操作 Unknown | 消息复制正常 | 可引用，但绝不自动重试旧操作 | 安全隔离 |

### 8.8 冻结状态与待评审事项

- **已冻结**：Agent 消息末尾常显 `复制 + 更多`；长按正文走系统选区；Tool/Code 复制有独立入口；不得吞长按。
- **建议默认，待真机验证**：Tool 长输出使用“有限原位预览 + 全屏完整输出”。
- **仍待 UX-D0 最终验收**：不同窗口/大字号下操作栏的实际基线对齐，消息组的边界规则，跨段落文字选择在当前 Markdown Renderer 中的具体效果。

---

## 9. 滚动和阅读位置状态机（必须能测试）

**目标**：永不打断“正在看历史”的用户，除非用户明确执行“回到最新”。

### 9.1 视图状态

| 状态 | 判定建议 | 新消息到达 | UI |
|---|---|---|---|
| `FollowingLatest` | 初次进入无历史阅读锚点，或用户点了“回到底部”，且列表确实接近末尾 | 随新内容跟随底部；动画应克制 | 不显示新消息按钮 |
| `BrowsingHistory` | 用户主动上滑离开末尾 / 正在选文字 / 正在操作历史消息 | **保持锚点**，不自动滚动 | 显示「↓ 新消息」/「回到最新」 |
| `RestoringAnchor` | 返回已保存的消息 ID + offset | 延迟解析完成后定位锚点，勿先跳底再跳回 | 可提示“恢复阅读位置”但不弹窗 |
| `Resyncing` | 重连、Snapshot 重建或分支切换中 | 保留当前稳定内容和锚点，按可靠身份对账 | 显示数据可能不是最新 |
| `Empty` | 当前有效分支无可见消息 | 不显示悬浮按钮 | 友好空态 |

- **接近底部** 候选阈值：尾项可见且剩余滚动距离 ≤64dp（须结合 LazyList 的真实可见信息和 Composer Insets 判断）。只是 UX 目标参数，真实交互需验证。
- 一旦用户开始拖拽列表/长按选择，就先进入 `BrowsingHistory`，即使当前仍靠近底部，也不以“阈值”强拉。
- 点击“回到最新”是用户主动行为，滚动到最后一个**当前有效分支**消息，不跳入其他分支。
- 用户自己提交新 Prompt 时可视为明确回到最新的动作，但不能因为网络重发 Unknown 自动触发该行为。

### 9.2 未读/新消息入口

- 新增**独立消息/条目**才增加 `newCount`；同一 ToolResult 的内容 PATCH、流式字块更新不能按字符数无限增长未读计数。
- 没有可靠计数时显示「↓ 回到最新」，而非编造「3 条新消息」。
- 按钮位于 Composer 上方，淡入出现，不能遮挡复制菜单、文本选择柄、发送状态条。
- 用户点按钮后定位最新，成功后清除对应当前 Session/Branch 的未读提示；离线过期时表述为“查看当前已缓存的最新内容”。
- 在浏览旧消息时，流式增量只更新数据，不改变用户当前显示位置。

### 9.3 阅读位置持久化

- Anchor：`hostId + herdrSession + agentSession/occupancy + branchId + itemStableId + offsetPx`。
- 返回对话时优先恢复锚点；如果 item 不存在（分支/截断/Reset）回退最近有效前驱或顶部，并给轻量说明，不能随机跳到其他 Session。
- 不依赖纯 index 作为跨数据变更时的唯一锚点；LazyList 提供 `key` 和 `contentType`。
- 展开 Tool/Code 导致内容高度变化时，尽可能固定当前视口中的首个稳定条目位置；避免长列表中对整批元素使用 `animateContentSize` 导致跳变。
- 切换 Agent Session 时任何 anchor/draft/expanded state 不串。

### 9.4 IME/Insets 与返回

- Composer 打开 IME 时，消息区可用高度变化；用户浏览历史时不自动跳底。
- 返回键优先关闭系统输入法/弹层，再退出对话（遵循系统预测性返回）；不劫持文本选择状态。
- 使用单一 Insets 来源，避免重复 `imePadding`/`navigationBarsPadding` 造成双倍空白。

## 10. 连接 / 数据 / 写操作状态的 UI 表达

三维独立：`TransportConnection`、`TranscriptFreshness`、`WriteOutcome`，再加 Agent `working/blocked/done/idle/unknown`；不要合成一个布尔 `isOnline`。

| 场景 | 视觉与文案 | 可以做什么 | 禁止 |
|---|---|---|---|
| 在线 + 内容已同步 | 低强调状态，正文正常 | 阅读、复制、在通过目标校验后输入 | 常驻绿色成功横幅 |
| 在线 + Transcript 正在同步 | 小型状态提示；保留已有内容 | 阅读、复制 | 声称内容必然最新 |
| SSH 断线 + 有缓存 | 顶部「已断开 · 显示已缓存内容」 | 阅读、复制、编辑草稿、手动重连 | 离线自动排队发送 |
| SSH 恢复中 | 低干扰状态条 | 阅读、复制 | 多个 Dialog 打断阅读 |
| 数据过期 | 「显示缓存，等待同步」 | 阅读、刷新 | 把旧 Blocked 当可批准的实时请求 |
| Accepted | 会话中的已接受回执（须有确定记录） | 继续阅读 | 称作“Agent 已完成” |
| Rejected | 显示实际原因，保留草稿 | 修改后提交 | 清除文本 |
| Unknown / Pending | **Composer 上方持久易见提示 + 更多中的“待确认发送记录”** | 查看写入记录、检查最新消息、经二次确认新建发送 | 自动重试、直接判失败 |
| 目标身份变化 | 明确“当前 Agent 已改变” | 重新选择/确认目标 | 静默沿用旧 Pane occupancy |

**注意**：有 Agent `blocked` 但无结构化审批对象时，仅提供进入对话/终端的后续路径，不自动把「允许/拒绝」按钮放进对话。

## 11. Motion（只用于解释状态，不制造热闹）

| 动作 | 候选规范 | 禁止 |
|---|---|---|
| 进入对话 | 220–280ms，小幅横向位移 + 淡入 | 文本、代码单独逐字飞入 |
| Thinking/Tool 展开 | 180–240ms，可中断，内容淡入 | 巨型输出整体展开多屏并连续重排 |
| 新消息按钮 | 150–200ms 淡入/隐藏 | 持续跳动吸引注意 |
| 复制成功 | 系统剪贴板反馈/低干扰轻提示 | 大面积成功动画 |
| 断线/恢复 | 状态文字+小符号 | 永久旋转、频繁闪烁 |
| 新消息增量 | 不改变历史阅读位置 | 每段 Markdown 都触发列队动画 |

- 尊重系统减少动画/动画时长设置，低端机/省电模式可用无动效直接切换。
- 120Hz 下预算约 8.33ms/帧，但不把该数当成现有性能成绩。
- 保留增量 Mapper；主线程不执行全量 Markdown 解析；保持 `LazyColumn` 稳定 key、明确 `contentType`。

## 12. i18n（设计阶段准备中文 / English）

**所有用户可见文案必须来自 Android 资源，下面只是拟议 Key 与术语；不授权本轮代码修改。**

| 资源 Key（建议） | 简体中文 | English |
|---|---|---|
| `conversation_unknown_title` | 当前会话 | Current session |
| `conversation_search` | 搜索会话 | Search conversation |
| `conversation_more` | 更多选项 | More options |
| `conversation_thinking` | 思考内容 | Thinking |
| `conversation_tool_result_pending` | 尚未收到工具结果 | Tool result not received |
| `conversation_tool_unmatched` | 未关联的工具结果 | Unmatched tool result |
| `conversation_copy_text` | 复制正文 | Copy text |
| `conversation_copy_markdown` | 复制原始 Markdown | Copy raw Markdown |
| `conversation_select_text` | 选择文字 | Select text |
| `conversation_quote` | 引用到输入框 | Quote in composer |
| `conversation_copy_code` | 复制代码 | Copy code |
| `conversation_view_full_output` | 查看完整输出 | View full output |
| `conversation_new_messages` | 新消息 | New messages |
| `conversation_jump_latest` | 回到最新 | Jump to latest |
| `conversation_stale` | 显示缓存，等待同步 | Showing cached content; syncing pending |
| `conversation_disconnected` | 连接已断开，显示缓存 | Disconnected; showing cached content |
| `conversation_send_unknown` | 发送结果暂时无法确认 | Send outcome cannot be confirmed |
| `conversation_restore_position` | 已恢复阅读位置 | Reading position restored |

- 数量使用 `plurals` 或合适的本地化格式；拼装带语言依赖的字序不允许直接字符串拼接。
- Pi/Claude 原文、路径、命令与代码不翻译；UI 语言切换不能重连或重复发送。
- 关键安全文案需人工校对，不能让 Pi 自行生成模糊的“成功/失败”措辞。

## 13. Accessibility / TalkBack

- 交互触达尺寸≥48dp；图标可20–24dp，但点击区域必须足够。
- 顶栏返回示例朗读：「返回工作台」；Agent 头部朗读：「Pi，运行中，Dev-01」，注意正在离线时不可读成“实时运行中”。
- 工具折叠控件应有 `expanded/collapsed` 状态说明与操作提示，避免读取整段原始输出后才到按钮。
- 复制正文、复制代码、复制原始 Markdown 必须有不同的 contentDescription。
- Link 需能通过辅助功能辨认为链接；正文可选择；只有装饰用途的图标不重复朗读。
- 对 `Unknown` 写操作可使用适度的 `liveRegion=Polite`，但禁止每次流式字块更新都频繁朗读整段回复。
- 字体 `1.0 / 1.3 / 1.6` 实测，主标题、状态、操作不裁剪；对比度正文目标≥4.5:1，非文本关键边界≥3:1（正式 Token 实测验证）。
- 对话在放大字体时应允许高度增长，不压住第一条消息；最小宽度 320dp 验证。

## 14. 视觉 QA / 交互 Fixture 矩阵

每个状态都应有确定性、脱敏的 Fixture；设计阶段先列验证场景，不需要真实 SSH 操作。

| ID | 场景 | 主要检查 |
|---|---|---|
| CONV-01 | 2 条普通消息 | 头部/正文/Composer 正常比例 |
| CONV-02 | 500 条混合消息 | 稳定 key、快速滚动、不卡顿/不跳锚 |
| CONV-03 | 2000 条混合消息 | 大列表惰性渲染/内存受控 |
| CONV-04 | 长 Markdown（H1-H3、列表、引用、链接） | 字阶、段距、基线、复制原文 |
| CONV-05 | 中英混合行内代码、紧贴标点 | 不插人工空格、文本选择一致 |
| CONV-06 | 300 行代码、超长单行 | 默认折叠、复制、横向滚动、查看全文 |
| CONV-07 | Tool 成功/失败/未有结果/无 ID | 不伪造状态、不错误关联 |
| CONV-08 | Thinking + Tool 相邻 | 区分层级、默认折叠、不会无故展开 |
| CONV-09 | 用户在历史中时追加 5 条消息 | 保持锚点、出现新消息入口 |
| CONV-10 | 点击回到最新 | 定位当前分支最后一条，入口消失 |
| CONV-11 | 工具结果先到，后有 ID 对应 ToolCall | 正确配对，卡片状态/位置稳定 |
| CONV-12 | Pi 分支切换 | 不显示旧分支内容、锚点规则正确 |
| CONV-13 | SSH 离线但缓存可读 | 缓存提示，复制可用，发送禁用 |
| CONV-14 | Unknown 写操作待确认 | 持久入口、无自动重发、朗读准确 |
| CONV-15 | 输入法弹出、展开 Tool | 不遮挡文本选择/Composer，滚动不乱跳 |
| CONV-16 | 320dp + fontScale 1.6 | 顶栏增高/折行，无裁剪，48dp 操作 |
| CONV-17 | 浅色、深色、中文、英文 | 色彩对比度、文案换行和排版 |
| CONV-18 | 大段用户消息及进程恢复 | 文本与滚动/草稿身份不串 |
| CONV-19 | 复制四种内容 | 正文/Raw Markdown/代码/Tool 输出符合定义 |
| CONV-20 | TalkBack 探索 | 操作顺序与描述、无重复朗读 |

**最小截图矩阵**：`393dp × light/dark × zh/en` 的主阅读页面；`320dp × fontScale 1.6`；代码块（折叠/展开）；工具（成功/失败/未匹配）；历史浏览时新消息；离线缓存；Unknown 写操作；IME 打开；每个截图固定 Fixture 并标注真实测试构建。

## 15. 设计到代码的边界（为能力较弱的 Pi 预先约束）

UX-D0 阶段 **只产出规格，不修改仓库业务代码**。后续 UX-03 实施时必须拆为独立 Task Packet，不允许把全部内容当一个单一 PR：

1. **UX-03a / 基础阅读布局**：顶栏自适应高度、全宽 Agent、User Bubble、署名分组与排版 Token；与现有样本对照截图。
2. **UX-03b / Markdown 视觉组件**：字体层级、行内代码空格 Bug、引用/列表/代码布局；Parser 改动需要单独申请。
3. **UX-03c / 工具与 Thinking**：可靠 ID/未配对/结果缺失、折叠卡、长输出与完整查看入口。
4. **UX-06a / 消息操作**：复制正文、复制原始 Markdown、选择文字、复制代码、敏感剪贴板处理。
5. **UX-06b / 引用与会话搜索**：引用只入草稿，搜索真实文本数据；不混入 UX-03a。
6. **UX-03d / 阅读位置**：锚点、新消息计数、分支/Resync、Keyboard/Insets，且不触碰网络写入协议。

各任务必须包含 `Task ID`、允许修改文件/禁止区域、前置测试、Fixture、操作步骤、二值验收、截图差异、实测设备、异常停止条件。先核对仓库实际路径，再指定文件；不擅自发明源码类名。

## 16. 设计决策状态（v0.2：操作入口已冻结）

| 决策编号 | 内容 | 状态 |
|---|---|---|
| `CONV-ACTIONS-001` | Agent 完成消息末尾常显低强调复制 + 更多；正文长按系统选区 | **Approved / 冻结** |
| `CONV-CARD-001` | Agent 全宽阅读；用户气泡；Tool/Thinking 默认折叠 | Design baseline，等待三屏统一审查 |
| `CONV-TOOL-001` | 无可靠 ID 不配对；结果收到不等于执行成功 | Engineering invariant，禁止修改 |
| `CONV-TOOL-002` | 工具长输出有限原位预览 + 独立完整输出页，无嵌套纵向滚动 | 推荐方案，待实现可行性/真机确认 |
| `CONV-SCROLL-001` | 历史浏览不自动跳底，新消息浮动入口 | 已有实现方向，真实长会话验收待补 |
| `CONV-ANCHOR-001` | 阅读锚点导航/Activity 重建恢复，跨进程持久化单列任务 | 设计建议，未冻结 |

**本轮不得把此文标作整体 v1.0**：其余待验证细节不等于已经全部获用户批准。后续 UX-D0 三屏统一审查后再考虑全量冻结。

## 17. 官方技术参考（独立于项目实测）

- Compose text selection / LinkAnnotation: https://developer.android.com/develop/ui/compose/text/user-interactions
- Compose Lazy lists（stable key、contentType）: https://developer.android.com/develop/ui/compose/lists
- Android Compose accessibility semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Android 最小触达面积: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Compose Clipboard / Android 13 反馈与敏感标记: https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste
- Compose Motion / Animation performance: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Compose Material 3 Type Scale: https://developer.android.com/develop/ui/compose/designsystems/material3
- State-based text field: https://developer.android.com/develop/ui/compose/text/user-input

> **结束条件**：`CONV-ACTIONS-001` 已冻结；Tool 细节、阅读锚点与 Composer 仍需三屏统一评审。暂不授权 Pi 编码，完成总规范审查后再拆 Task Packet。
