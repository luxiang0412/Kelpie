# Herdr Android · UX-D0 / Unified Design System v0.1

> 日期：2026-11-01 · 状态：**Design Review Baseline / 待视觉验收，不是代码实施授权**  
> 风格：**Graphite × Editorial**（Graphite 的秩序和精密 + Editorial 的阅读感）  
> 覆盖：工作台 Board、对话 Conversation、Composer；同时约束技术信息 Sheet、主机管理与未来能力的基础样式  
> 基线文件：`HERDR-UX-D0-BOARD-SPEC-v0.2.md`、`HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md`、`HERDR-UX-D0-COMPOSER-SPEC-v0.2.md`、`HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md`、`STATUS-BRIEF-FOR-EXTERNAL-REVIEW.md`  
> **项目事实**：Herdr/Pi 数据、SSH、Room 和 WriteLedger 已有实现；跨主机聚合、结构化审批、语音转写、附件与会话搜索尚不能当作已实现功能。

---

## 0. 使用方式、权威性、冻结状态

### 0.1 文档的职责

- **本文件**：三屏共享的视觉 Token、组件契约、语义状态、适配、动效、i18n、可访问性、视觉验收和工程交付规则。
- **Board Spec v0.2**：工作台独有信息层级、排序、状态和 Fixture。
- **Conversation Spec v0.2**：消息排版、Tool 关联、复制、选择、滚动和阅读锚点。
- **Composer Spec v0.2**：输入、草稿、发送三态、语音转写交互、身份与安全。
- **Engineering Contract**：给本地 Pi 的任务拆分、PR、测试及停止条件。
- 产品领域状态与权限以仓库的 `DECISIONS.md` 和真实实现为准。设计方案不能覆盖协议事实和安全决策。

### 0.2 规则优先顺序

1. **用户已明确批准的决策**与已验证的安全/协议约束。
2. 本 Design System 的共享 Token 与通用组件规范。
3. 对应页面 Spec 的页面特例（需明确标出理由）。
4. Android 官方组件行为/无障碍要求。
5. 尚待评审的概念稿：只作候选，不自动进入正式实现。

若某个任务需要更改优先级 1–2 的约束：暂停该项，提交冲突分析，不能由 Pi 自行选择。

### 0.3 正式已冻结决策（Approved）

| 决策 ID | 内容 | 不可回退约束 |
|---|---|---|
| `BOARD-DONE-001` | 工作台「已完成」分区**默认收起** | 展开后可保留当前页面的展开状态；不得默认显示最近一条替代原选择 |
| `CONV-ACTIONS-001` | Agent 消息尾部常显低强调 **「复制 + 更多」**；长按正文使用系统文字选择 | 不把“复制”全部塞进菜单；不以自定义长按劫持文本选择 |
| `CMP-VOICE-001` | **自适应语音入口 B**：功能真实可用后，常规宽度显示麦克风，窄屏或大字体进入「更多」 | 未接通不出现无响应 Mic；语音转写不自动发送 |
| `SEC-WRITE-001` | `Accepted / Rejected / Unknown` 三态；Unknown 绝不自动重发 | Accepted 不等于 Agent 已执行 |
| `SEC-APPROVAL-001` | 不从终端读屏推断后实现一键批准 | 未来审批须采用可信结构化请求来源 |
| `CONV-TOOL-001` | ToolCall 与 ToolResult 仅按可靠 ID 关联 | 不使用“按出现顺序猜测配对” |

### 0.4 仍未获用户确认的选择（Proposed / Open）

| ID | 设计提案（本文件给出默认画法） | 仍需确认/验证 |
|---|---|---|
| `BOARD-HOST-001` | 主机选择器位于工作台主标题**下方** | 实际截图与最大字号验证后确认 |
| `CMP-STYLE-001` | 轻量双层 Composer Surface | 真机输入法展开与视觉验收 |
| `CMP-QUOTE-001` | 引用插入光标处；有选择区时保守处理 | 插入规则与现有草稿冲突体验 |
| `CONV-TOOL-002` | 长输出有限卡内预览 + 独立完整输出页 | 对大文件分页、焦点和返回体验验证 |
| `CONV-ANCHOR-001` | 稳定消息 ID + offset 保存阅读锚点 | 进程恢复 / 分支切换实测 |
| `BRAND-ICON-001` | 抽象 Focus Orbit 图标方向 | 另设图标视觉评审，不以占位图标为最终版 |

**注意**：v0.1 是可以据此开展视觉原型的基线，**不是**对所有组件尺寸、图标、交互的最终冻结。需要用户批准后才升级 v1.0。

---

## 1. 产品体验准则

1. **3 秒可扫描**：谁需要我、是什么任务、在哪台主机；工作台以任务为主语而非 Pane。
2. **阅读优先**：Assistant 正文像文章，User 像简洁聊天；Tool 像工作记录。
3. **只用一个高强调动作**：屏幕内品牌色通常只强调当前主动作（例如 Send）；语义色只表示状态。
4. **信息必须可信**：`done ≠ idle`；`blocked ≠ 可信审批`；`tool result received ≠ execution succeeded`；`Accepted ≠ Agent 已执行`。
5. **弱网不白屏**：先显示标记为缓存的内容；连接状态、数据新鲜度、Agent 状态、写操作结果分别呈现。
6. **底部安全可达**：单手高频输入与主导航在可达区域，但标题、阅读空间不牺牲。
7. **没有假功能**：尚未实现语音、附件、活动历史、跨主机聚合、搜索，不展示能点击却无结果的入口。
8. **禁用装饰性复杂动效**：不用持续发光、循环进度条、多层玻璃或完整列表的布局动画制造“AI 感”。

---

## 2. Foundations / 设计基础

### 2.1 Color Scheme：语义角色，而非散落 Hex

#### 2.1.1 基础色板（正式候选值）

| Token 名 | Light | Dark | 用法 |
|---|---|---|---|
| `color.canvas` | `#F7F8FA` | `#10151D` | 页面底色 |
| `color.surface` | `#FFFFFF` | `#181F2A` | 常规容器/Sheet/Composer |
| `color.surfaceRaised` | `#EFF2F6` | `#222B39` | User 气泡、嵌套只读代码区域 |
| `color.borderSubtle` | `#DFE5ED` | `#344052` | 装饰性细分割线/轻边界 |
| `color.textPrimary` | `#172033` | `#EEF2F8` | 主要正文、标题 |
| `color.textSecondary` | `#4F5D70` | `#AEB9C8` | 元信息、说明、次要操作 |
| `color.accent` | `#3556CE` | `#9BB0FF` | 当前主操作、链接、焦点 |
| `color.onAccent` | `#FFFFFF` | `#10151D` | 品牌按钮文字/图标 |
| `color.focusIndicator` | `#3556CE` | `#9BB0FF` | 明确的输入焦点，可与背景形成显著对比 |

> `borderSubtle` 在背景上的对比度约 1.2–1.7:1，因此**只能**作装饰性分割线；不能单独依靠它表明输入框边界、选中态、焦点或错误状态。必须在需要识别控件的地方添加强对比焦点线、形状差异或有标签的 Surface 层次。

#### 2.1.2 语义颜色（前景文字 / 柔和容器背景）

| 语义 | Light `on / container` | Dark `on / container` | 主要用途 |
|---|---|---|---|
| `attention` | `#8B3E0A` / `#FFF2E6` | `#FFC28A` / `#3A2B23` | 需要用户关注/Blocked |
| `working` | `#12558A` / `#EAF4FF` | `#9FCEFF` / `#1E344A` | Working（仅一处色彩信号） |
| `done` | `#17683E` / `#EAF8EF` | `#8ED6B0` / `#1C352A` | 完成（不等于已阅读） |
| `idle` | `#596374` / `#F0F2F5` | `#BEC9D8` / `#2A323E` | 空闲 |
| `unknown` | `#74530A` / `#FFF7E0` | `#E9CF87` / `#38321D` | Agent 状态未知 / 数据需核实 |
| `error` | `#B42318` / `#FFF0EF` | `#FFB4AB` / `#402524` | 明确失败/认证错误/Rejected |

- **两个不同的 Unknown 必须使用不同文案**：`Agent 状态未知` 和 `发送结果无法确认`；颜色不是区分两者的唯一手段。
- 工作台一条 Agent 行最多出现一处明显的语义色（标签或图标，二选一）；项目名、Chevron 和元信息保持中性。
- 不用绿色给已接收但未经确认成功的 ToolResult 着色。
- 设计中状态色配对仅用于柔和容器或语义文字，若用于另外背景必须重新计算对比度。

#### 2.1.3 对比度校验（计算结果，尚非真机截图验收）

使用 WCAG 相对亮度算法对上述不透明色值计算，结果保留两位小数：

| 组合 | Light | Dark | 结论 |
|---|---:|---:|---|
| 主文字 / Canvas | 15.31:1 | 16.30:1 | 满足普通文本 4.5:1 |
| 次文字 / Canvas | 6.31:1 | 9.22:1 | 满足普通文本 4.5:1 |
| Accent / Surface | 6.22:1 | 7.92:1 | 满足普通文本 4.5:1 |
| OnAccent / Accent 按钮 | 6.22:1 | 8.76:1 | 满足普通文本 4.5:1 |
| Attention 前景 / Attention 背景 | 6.84:1 | 8.61:1 | 满足普通文本 4.5:1 |
| Working 前景 / Working 背景 | 7.01:1 | 7.75:1 | 满足普通文本 4.5:1 |
| Done 前景 / Done 背景 | 6.21:1 | 7.79:1 | 满足普通文本 4.5:1 |
| Unknown 前景 / Unknown 背景 | 6.58:1 | 8.37:1 | 满足普通文本 4.5:1 |
| Error 前景 / Error 背景 | 5.94:1 | 8.20:1 | 满足普通文本 4.5:1 |
| **Subtle Border / Canvas** | **1.19:1** | **1.74:1** | **不满足关键控件边界 3:1；仅装饰** |

WCAG AA：普通文本至少 4.5:1、大字号文本至少 3:1；必要的非文本控件/状态边界至少 3:1。禁用态或纯装饰内容的例外不能滥用到重要操作。半透明叠加、截图后的真实合成颜色、图标与背景需另测。

### 2.2 Typography（可映射到 Material 3 TextStyles）

| Token | Size / Line height | Weight | 用途 |
|---|---|---|---|
| `type.displayPage` | 26sp / 34sp | SemiBold | 工作台「工作台」 |
| `type.titlePage` | 18sp / 26sp | SemiBold | 对话页标题、长编辑器标题 |
| `type.titleTask` | 16sp / 24sp | SemiBold | 被关注的任务标题 |
| `type.titleSection` | 14sp / 20sp | SemiBold | 需要处理、运行中、工具小节 |
| `type.bodyReading` | 16sp / 25sp | Regular | Agent 与用户消息、Markdown 正文 |
| `type.bodyInput` | 16sp / 24sp | Regular | Composer 输入 |
| `type.bodySecondary` | 14sp / 21sp | Regular | 卡片描述、预览 |
| `type.meta` | 13sp / 19sp | Regular | Host、项目、真实状态及工具摘要 |
| `type.labelAction` | 13sp / 20sp | Medium | 复制、更多、展开、按钮文案 |
| `type.code` | 13sp / 20sp | Mono | 命令、代码、路径 |
| `type.mdH1` | 21sp / 29sp | SemiBold | Markdown H1 |
| `type.mdH2` | 18sp / 26sp | SemiBold | Markdown H2 |
| `type.mdH3` | 16sp / 24sp | SemiBold | Markdown H3 |

约束：

- 使用系统 Sans 和 Monospace；不打包任意字体来掩盖中英混排问题。
- 不在业务 Composable 内手写散落的 `11.sp`、`14.sp`；统一通过语义 Type Tokens。
- `fontScale` 至少覆盖 `1.0 / 1.3 / 1.6`。增大字体时**容器长高、内容换行**，不能固定 TopAppBar 高度裁剪。
- 状态不以 11sp 纯彩色文字承载；重要状态至少使用 `type.meta`，并有正确 TalkBack 语义。
- 标题不跨多行强制截断后失去完整查看路径；多行文本不通过插入空白字符模拟布局。

### 2.3 Spacing、Shape、Stroke、Elevation

| Token | 值 | 用途 |
|---|---|---|
| `space.1 / 2 / 3 / 4` | 4 / 8 / 12 / 16dp | 内部间距 |
| `space.5 / 6 / 8` | 20 / 24 / 32dp | 页面/分区间距 |
| `layout.pageGutter` | 20dp；320–359dp 时 16dp | 工作台及阅读内容网格 |
| `layout.composerGutter` | 16dp；320–359dp 时 12dp | Composer 外边距；与正文不同属有意设计 |
| `shape.small` | 6dp | Inline Code（圆角建议可 4dp）、小标签 |
| `shape.codeTool` | 12dp | Code Block、Tool 卡 |
| `shape.card` | 16dp | Blocked 注意事项卡、User 气泡 |
| `shape.composer` | 20dp；窄屏可18dp | Composer Surface |
| `shape.pill` | full | 状态 Chip、Send 视觉圆面 |
| `stroke.decorative` | 1dp | 非必要分隔线，低对比 |
| `stroke.focus` | 2dp 建议 | 聚焦边界，需可靠 3:1 可见性 |
| `elevation.default` | 0dp | 列表/普通容器没有阴影 |
| `elevation.overlay` | 采用 Material3 组件默认语义 | Sheet/菜单/浮层，可有轻微阴影 |

- 图标标准可见尺寸：20–24dp；**交互点击区 ≥48×48dp**，Header/行的整体最小点击高度合理延长。
- 状态量多时不将每行都包成大卡片。靠行分隔、字重和章节间距建立层次。
- 行内代码需要在渲染层解决背景与内边距，**不能修改原始文本两侧增加空格**。

### 2.4 Iconography

- 图标风格：统一 20–24dp、轻量轮廓、尽量 1.75–2dp 视觉描边；最终用 Android Vector Drawable/Material Symbols 中实际可用的图标，并核对许可与图标版本。
- 不使用 `›`、`▸`、`⤢` 等纯 Unicode 字形替代主要可交互图标，避免不同系统字体基线不一致。
- 每个图标只有一种明确语义；Decorative Icon 不参与重复朗读。图标+文案传递状态，不单用颜色。
- **可点击性不能只靠 Chevron**；整行点击的状态和内容要明确，Chevron 保持中性。
- 不把未实现的图标排进生产 UI。Logo/Launcher Icon 另开独立设计评审，不能自动随机生成。

### 2.5 Content density

- 工作台：0–2 Agent 自然留白；3–8 Agent 紧凑列表；12+ Agent 重要任务前置、其他分组折叠、稳定 Key/惰性列表。
- 对话：Agent 文字全宽；User 气泡最大宽度 84–86%（窄屏 88%）；Tool 默认折叠；代码块横向独立滚动。
- Composer：正常 1–2 行；最多 5 行可视，长文本内部滚动/进入全屏编辑器；文本和操作行保持一体。
- 无可靠业务数据时不放统计图、假等待时长、虚构总结来填空白。

---

## 3. 统一语义状态模型

**同一屏上可能同时存在四个不同维度，禁止折成 `isOnline`：**

| 维度 | 状态示例 | 何处显示 | 禁止表达 |
|---|---|---|---|
| `AgentState` | blocked / working / done / idle / unknown | 工作台任务项、对话头部 | 将 done 当 idle；将 blocked 当自动可批准 |
| `TransportState` | connecting / connected / reconnecting / offline / auth error / host key changed | 工作台次级状态、对话必要时的提示 | 已连接时永久绿色横幅 |
| `DataFreshness` | live / syncing / cached-stale / unavailable | 缓存条、必要时独立时间 | 在线等于数据一定最新 |
| `WriteOutcome` | sending / accepted / rejected / unknown | Composer 与持久待确认操作入口 | Unknown 显示 Failed 或自动重试 |

优先级：**安全阻断/主机身份变化 > Unknown 写入 > 明确认证失败 > 连接/数据已过期 > Blocked 注意事项 > 普通同步状态**。

多个问题并存不叠三条 Banner：展示一条最重要状态以及「查看全部问题」入口，且所有低优先级问题仍能从详情恢复。

### 3.1 术语统一

| 领域状态 | 中文 | English |
|---|---|---|
| `blocked` | 需要处理 | Needs attention |
| `working` | 运行中 | Running |
| `done` | 已完成 | Completed |
| `idle` | 空闲 | Idle |
| `unknown` | 状态未知 | Status unknown |
| 连接丢失 | 已断开 | Disconnected |
| 缓存内容 | 显示缓存内容 | Showing cached content |
| `Accepted` | 指令已接收 | Instruction accepted |
| `Rejected` | 发送被拒绝 | Send rejected |
| `UnknownWrite` | 发送结果无法确认 | Send outcome unknown |
| 未收到 ToolResult | 尚未收到结果 | Result not received |
| 结果到达 | 结果已收到 | Result received |
| 明确工具错误 | 执行失败 | Execution failed |
| 无可靠关联 ID | 未关联的工具结果 | Unmatched tool result |

这些文案应由资源 Key 维护，不能由 Pi 根据上下文改写安全关键句子。

---

## 4. 三屏布局契约

### 4.1 Board / 工作台

```text
Screen window
 ├─ AppBar：工作台标题 + 当前已实现的刷新/设置
 ├─ Host Selector：具体已选主机（位置在标题下方，Proposed）
 ├─ Critical Status Strip：Unknown/安全异常（仅存在才显示）
 ├─ Content：注意项卡片 → Working 紧凑行 → Done 折叠 → Idle/Unknown
 └─ Bottom Navigation：当前只显示「工作台」「主机」
```

- 原则：**用户选 A：Done 默认折叠**。用户点击展开后在当前页面生命周期保持展开状态。
- 尚未做跨主机聚合：选择器必须是具体主机，不能默认为「全部主机」。
- 注意项前 1–2 条适度强调（容器 16dp 内边距/圆角 16dp）；普通条目最小 64dp。点击任务直达对话。
- 单个 Agent 行里**只允许一处强语义色**，其余保持中性；不重复状态色点+状态字+蓝 Chevron。
- 无任务时不放巨大的空白注意项区块；离线且有缓存时展示缓存标识与安全的只读内容。

### 4.2 Conversation / 对话

```text
AppBar（高度随 fontScale 增长）
 ├─ 返回 + 可信任务标题 + Agent/Host/Status + 更多
 ├─ Status (离线/缓存/Unknown 条，仅有必要时)
 ├─ LazyColumn（唯一主垂直滚动）
 │   ├─ User bubble (右侧 ≤86%)
 │   ├─ Agent Name 一次/组
 │   ├─ Agent 正文全宽，Markdown 阅读布局
 │   ├─ Thinking 弱强调折叠行
 │   ├─ Tool 卡默认折叠 + 可靠 ID 状态
 │   └─ Agent 消息尾 `复制 + 更多`（**冻结**）
 ├─ 新消息浮动入口（仅离开底部、有新消息时）
 └─ Composer（底部，受 Insets 约束）
```

- Agent 正文 16sp/25sp；User 气泡微蓝中性背景，避免饱和品牌蓝整段填充。
- Agent `Pi` 标识只在组首出现一次（13sp 中性字/简图），不再是每条独立 11sp 绿色文字。
- 不能用父级长按处理器覆盖系统文本选择；消息的「复制 + 更多」至少 48dp 点击区。
- Tool：只有 ID 对应才关联；“结果收到”不等于“成功”；超过 12 行输出按需查看完整原文。
- 长会话保留阅读锚点；用户在看历史时新消息不强制跳底；新消息提示只统计独立条目而非 token 数。
- 原始 Markdown、代码、ToolResult 的复制语义不同，不能通过屏幕渲染后 Text 反推原始数据。

### 4.3 Composer / 输入

```text
Conversation body
 ├─ 可选状态：Unknown/身份变化/离线（优先单条高风险提示）
 └─ Composer (轻量两层 Surface)
     ├─ TextField (1–5 行；Enter 换行)
     └─ Actions (可用功能图标 + Expand + Send)
         ├─ Voice Mic (已实现且常规宽度)
         ├─ More → Voice Input (窄屏或大字体)
         └─ Send（始终可见 ≥48dp）
```

- Composer 外边距 16dp（窄屏 12dp），圆角 20dp、Surface 1dp 低强调描边，发送按钮为区域唯一明显 Accent。
- TextField `16sp/24sp`，Placeholder 14–15sp，Compose TextField 状态式 API 保留；不要无理由重写草稿层。
- 本地编辑状态与 WriteOutcome 分离；用户发送时提交不可变快照；发送中仍可继续编辑。
- Unknown 的状态条必须在 Composer 周边可持续找到；不自动重发，不把 `Accepted` 说成执行完成。

### 4.4 Composer 麦克风响应式规则（**用户已确认 B**）

优先级：`Send` > `TextInput` > `Expand` > 可用 `More` > `Mic` > 其他功能。

1. **功能门控**：仅当真实 STT 功能接通、可启动时展示语音入口；未实现时不展示 Mic 或仅供设计 Fixture 预览。
2. **常规宽度**：建议窗口宽度 **≥360dp、字体缩放 <1.3** 且实际布局测量容得下 48dp 操作触达区时，Mic 在 Action Row 常显。
3. **紧凑宽度/放大字体**：窗口宽度 **<360dp**，或 fontScale **≥1.3**，或动态测量出现碰撞时，Mic **进入 More 菜单**；主操作 Send、编辑文本与 Expand 保持常显。
4. **More 的存在条件**：若 Mic 是唯一被折叠功能，More 仍必须显示并明确包含“语音输入”；不能因“无附件”而把语音入口一起隐藏。
5. **无其他操作时**：未接通 Voice/附件/其他功能，就不显示空 `+` 菜单或失效占位图标。
6. **权限拒绝 / 识别服务暂不可用**：普通文本输入不受影响；已展示菜单必须提供准确的禁用说明或收起不可用选项，不能静默点击无反应。
7. **语言分离**：转写语言选择独立于 App UI 语言；任何识别结果进入可编辑审阅再插入草稿；**永不自动 Send**。
8. **旋转/调整字体**：当布局在显隐 Mic 与 More 间切换，草稿、光标、STT 状态不能重置或错误插入另一 Agent 会话。

**测试 Fixture：** 320/359/360/393/600dp × fontScale 1.0/1.3/1.6 × STT Enabled/Unavailable/NotImplemented，验证 Mic/More 出现规则、动作可发现性、TalkBack、48dp 无重叠、发送绝不误触发。

---

## 5. Component Contract / 组件规范

组件名称为**设计建议**，Pi 执行前必须核对真实文件与 API，不得因名称不一致而新增重复数据架构。

| 组件 | 必须显示 | 状态/行为 | 禁止 |
|---|---|---|---|
| `AppTopBar` | 标题、返回/现有实际操作 | 高度可增长、次级元信息可换行 | 固定两行高度裁剪 |
| `HostSelector` | 具体主机和清晰目标 | 已连接/断线信息不等于 Agent 状态 | 未实现时出现“全部主机” |
| `AgentAttentionCard` | 可信标题、状态、Agent/项目/Host | 低对比高关注卡，可点击进入会话 | 推断审批按钮/等待时长 |
| `AgentCompactRow` | 标题、状态文字、Agent/Host | 最小64dp、单个语义色、整行点击 | 彩色点+字+Chevron 同时强调 |
| `StatusStrip` | 必要异常与动作 | 统一优先级、可查看全部 | 多条堆叠遮正文 |
| `AssistantGroup` | Agent 身份一次/组、正文、操作尾栏 | Agent 全宽阅读、尾部复制+更多 | 每段加品牌色大卡片 |
| `UserBubble` | 用户输入原文、可选内容 | 右侧 ≤86%、低饱和底色 | 全宽饱和蓝气泡 |
| `MarkdownBody` | 标题/列表/代码/引用等 | 纯文本降级无丢失、可复制原文 | 编造空格/执行 HTML |
| `CodeBlock` | 语言（如真实）、代码、Copy | 超 12 行折叠；横向滚动；全文入口 | 把截断内容称全文 |
| `ThinkingRow` | 「思考内容」/真实摘要 | 默认收起；文本可选 | 虚构思考时长 |
| `ToolRecord` | 工具名、可靠结果状态、展开 | 默认折叠；只按 toolCallId 配对 | 猜测成功/顺序配对 |
| `MessageActions` | **复制 + 更多** | 低强调常显、≥48dp、长按仍可选文 | 把复制藏入更多 |
| `NewMessagePill` | 新内容入口 | 仅 BrowsingHistory、有新条目时出现 | 打断历史阅读位置 |
| `ComposerSurface` | TextField / 有效操作 / Send | 输入与 Action Row 两层；IME Insets | 嵌套巨型悬浮卡 |
| `VoiceEntry` | 可启动的 Mic 或 More 菜单项 | 宽度/字号自适应 + STT 状态 | 假按钮、自动发送 |
| `UnknownWriteStrip` | “发送结果无法确认”与记录入口 | 会话可恢复；重发二次确认 | “发送失败，再试一次” |
| `FullOutputReader` | 已加载范围 / 搜索可选 / 复制 | 大文本惰性、仅在可取全文时称全文 | 列表内固定长竖向滚动 |

### 5.1 组件的 6 种通用状态

每个涉及交互的组件至少定义：`Default / Pressed / Focused / Disabled / Loading / Error`（不适用者写 N/A）。重点屏幕再加 `Offline / Stale / Unknown` 业务正交状态。

- 只有状态确实可用时才显示 Loading；不能无限 spinner 代替“没有事件”。
- 焦点、按压、选择不改变业务事实；按钮禁用必须通过语义说明原因。
- 组件单测/截图使用确定性 Fixture，保证光/暗中文/英文一致。

---

## 6. Motion System

| Token | 建议时长 | 动画意图 | 低功耗/减少动画 |
|---|---|---|---|
| `motion.feedback` | 80–120ms | 按钮按压/轻反馈 | 立即更新 |
| `motion.colorState` | 140–200ms | Agent 状态标签颜色切换 | 直接替换 |
| `motion.appear` | 150–200ms | 新消息入口/低风险提示 | 直接出现 |
| `motion.expand` | 180–240ms | Tool/Thinking/分区折叠 | 无过渡 |
| `motion.screen` | 220–280ms | Board → Conversation / 返回 | 系统减少动画时关闭 |
| `motion.sheet` | 240–320ms | 辅助 Sheet 与长编辑器 | 优先使用系统组件行为 |
| `motion.focus` | 120–180ms | Composer 聚焦边界 | 直接设焦点样式 |
| `motion.sendAck` | 120–160ms | 已接收状态的轻反馈 | 直接显示文案 |

**实施注意事项**：

- Duration 是视觉建议，不是性能验收结果。120Hz 目标帧时间约 8.33ms；高负载对话页优先减少 Layout 阶段每帧变化。
- 禁止工作状态的持续脉冲/流光；真实等待使用静态“运行中”文案或按系统惯例的小型进度组件。
- 长列表新增/展开不对整个列表统一 `animateContentSize`；用户正在阅读历史时保持锚点，不用花哨位移动画。
- 使用系统/Android 动画设置并支持禁用非必要动效；完整测试需要真机观察与自动化 Trace，而不是通过 Pi 口述“流畅”。

---

## 7. Internationalization / Localization

### 7.1 语言模型

- 第一阶段 UI：`System default / 简体中文 / English`，中文资源建议 `values-zh-rCN`，英语为默认 `values`（最终主语言以仓库构建策略决定）；确保开发版语言切换不触发 SSH 重连/重复写入。
- Android 13+ 使用官方 Per-app Language 支持。API 26–32 的兼容实现需要核对 Activity 基类和 AppCompat 依赖。**不能**简单建议 `AppCompatDelegate.setApplicationLocales` 而忽略当前 MainActivity 是否是 `AppCompatActivity`。
- 语言改变可能造成 Activity 重建，草稿/Unknown Ledger/阅读位置要正确恢复。
- Agent 原始内容、Host 名、路径、命令、代码、工具原始 JSON **不本地化**。
- STT 语言与 UI Locale 分离：优先系统识别设置或用户显式选择；不要切 UI 英语就把说中文的识别强制改为英文。

### 7.2 资源治理

- `strings.xml` + plurals + 本地化格式参数；不得硬拼译文单词顺序；占位符位置必须允许英中调整。
- 每个文案 Key 只表达一种领域语义。不同页面复用同一含义的通用 Key，禁止 `Board/Conversation/Composer` 三套相互冲突的同义词。
- 输入 Placeholder、按钮标签、菜单项、Status Strip、通知、权限对话框、TalkBack 语义、错误提示都必须本地化。
- 动态数量、日期/时间采用 Locale/时区敏感格式；不要硬编码“3 分钟前”；没有可靠 timestamp 就不展示相对时间。
- 源串 ID、Pane ID、Action ID 保持字节原样；不本地化代码块内容。

### 7.3 关键文案字典（建议一次统一）

| Key（建议） | 中文 | English |
|---|---|---|
| `nav_board` | 工作台 | Agents |
| `nav_hosts` | 主机 | Hosts |
| `state_attention` | 需要处理 | Needs attention |
| `state_running` | 运行中 | Running |
| `state_completed` | 已完成 | Completed |
| `state_idle` | 空闲 | Idle |
| `state_unknown` | 状态未知 | Status unknown |
| `connection_reconnecting` | 正在重新连接… | Reconnecting… |
| `data_cached` | 显示缓存内容 | Showing cached content |
| `write_outcome_unknown` | 发送结果无法确认 | Send outcome unknown |
| `write_review` | 查看发送记录 | Review send record |
| `message_copy` | 复制 | Copy |
| `message_copy_raw` | 复制原始 Markdown | Copy raw Markdown |
| `message_more` | 更多 | More |
| `message_quote` | 引用到输入框 | Quote in composer |
| `tool_result_received` | 结果已收到 | Result received |
| `tool_result_missing` | 尚未收到结果 | Result not received |
| `composer_placeholder` | 给 Agent 发送下一步指令… | Send your next instruction… |
| `composer_send` | 发送 | Send |
| `composer_expand` | 展开编辑 | Expand editor |
| `voice_input` | 语音输入 | Voice input |
| `voice_insert_draft` | 插入草稿 | Insert into draft |

> 表中 Key 是提议资源键，Pi 必须先清点现有 Key，优先复用，不得为了此文新建完全重名的字符串常量。**统一推荐**：英文工作台主标题与底部导航都用 `Agents`，而不再在同屏混用旧稿的 `Workspace`；但资源 Key/实际修改须在 UX-05 中依据现有代码核查，不是本轮已经修改。

---

## 8. Accessibility / Inclusive Design

1. 所有交互目标≥48×48dp，具备明确的标签和选中/展开状态；装饰图标不重复朗读。
2. 文本对比度按组件最终混色检验；普通文本≥4.5:1，大字≥3:1；必要图形/焦点≥3:1。
3. `fontScale 1.0 / 1.3 / 1.6` + 320dp + 横屏：按钮不相互遮挡，TopAppBar 可增高，Tool 卡详情和 Action Row 可换行。
4. 正文长按系统文本选择；复制/更多仍独立可操作；TalkBack 焦点从正文 → 操作区。
5. `liveRegion=Polite` 仅用于发送异常、认证失效、草稿保存失败等重要变化；不对每一段流式字符进行自动播报。
6. Tool Header 的展开状态与声音提示同时明确，不依赖 Chevron 指向。
7. 跨语言语音识别出错不替用户直接提交；文字转写必须让用户核对。
8. 大文本、超长代码在缩放时保留复制、完整查看和返回路径。
9. 触控反馈和 Compose 的无障碍默认语义优先，只有有充分理由才覆盖原生行为。

---

## 9. Visual QA、可重复 Fixture 与性能

### 9.1 每个核心屏的最小截图矩阵

| 维度 | 必须覆盖 |
|---|---|
| 主题 | Light / Dark |
| 语言 | zh-CN / en |
| 窗口宽度 | 320dp / 393dp / ≥600dp 设计预览（未实现大屏时只验设计） |
| 字体缩放 | 1.0 / 1.3 / 1.6 |
| 系统导航 | 手势导航 / 三键导航（至少真机抽查） |
| 网络 | 在线 / 断开有缓存 / 同步中 / 认证问题 |
| Agent | blocked / working / done / idle / unknown |
| 写入 | Ready / Sending / Accepted / Rejected / Unknown |
| 对话 | 短文 / Markdown / 300 行代码 / Tool 4 种状态 / 500+ 条 / 正在浏览历史 |
| 输入 | 空 / 1 行 / 5 行 / 8000 字符临界 / IME / 长编辑器 / 草稿恢复 / 身份不确定 |
| 语音（上线前设计 Fixture） | 可用常规宽度 / 窄屏 More / 权限拒绝 / 识别结果审阅 / 不自动 Send |

不需要全量笛卡尔积截图；每个任务选覆盖其改动的高风险组合，并提交一份“组合覆盖说明”。颜色测试需要独立自动化对比度检查，截图对照用于肉眼审美和裁剪检查。

### 9.2 关键视觉验收项

- 屏幕顶端第一项不被 TopAppBar 遮挡；中文/英文最长文案不截断关键含义。
- 工作台卡片/列表水平对齐，行分隔不产生多余噪音；任务标题比状态、元信息更突出。
- 对话字阶和段距稳定；行内代码无额外人为空格；Tool/Thinking 和正文层级正确。
- 复制/更多可见且不挡长按选区；用户浏览历史时输入法弹出不强行跳底。
- Composer 不被 IME/导航栏遮挡；Mic 自适应隐藏后仍可从 More 发现语音功能。
- 120Hz 目标不被持续动画拖累；正式帧指标只从可比的真实设备测试获得。
- 敏感文本/私钥/真实日志在公共截图、CI Artifact 中脱敏；必要时使用确定性 Fixture。

### 9.3 工程验证门槛

- 现有 JVM、Room、Compose UI 回归测试全部通过；若 MIUI instrumentation 仍失败则用已可运行的隔离 AVD，**不能宣称真机 UI 自动化通过**。
- 不擅自改 SSH/Herdr/Pi 协议语义；真实发送测试只在授权的 `test` Session 内进行。
- `Accepted`、`Rejected`、`Unknown` 逻辑通过领域层测试；Unknown 重发必须二次确认和新 Action ID。
- 一项设计改动必须有对照截图：**Before/After 同一 Fixture、同一主题、同一窗口、同一字体配置**。
- 不把构建成功当作视觉通过；必须记录仍存在的审美与交互问题。

---

## 10. Engineering Governance：专为本地 Pi Agent 设计

### 10.1 不允许一次性大重构

每个 Pi Task Packet 都要有：

1. `Task ID / 目标 / 不在范围内`。
2. 仓库当前 SHA、**实际核查过的**文件路径和 Compose 函数签名。
3. 设计引用精确至章节、Token、状态矩阵、验收 Case ID。
4. 需要修改/不得修改的模块名单；跨数据协议和安全逻辑必须单独申请。
5. 先 Fixture、再组件、再页面集成，避免“看起来可用但测试不可重复”。
6. 操作步骤分为 3–7 个明确 Checkpoint，每一步完成后有验证命令。
7. JVM 单测 / Compose UI / 真机抽查 / 中英文深浅色截图 / TalkBack 检查。
8. 失败时停止条件（例如：无法确认真实发送目标、需要改写安全状态机、键盘覆盖发送按钮、变更破坏已有 16 个 UI 测试）。
9. 回滚方案（单独提交、禁止混合跨任务重构）；变更日志与遗留事项。
10. 交付四类：**已实现并验证 / 已实现未验证 / 未实现 / 已知问题**。

### 10.2 任务包排序建议（尚未批准开始）

| 阶段 | Task Packet | 一次只解决的目标 |
|---|---|---|
| `UX-01a` | FontScale / TopAppBar 修复 | fontScale 1.3+ 裁剪与自适应高度 |
| `UX-01b` | InlineCode 原文正确性 | 删除虚假空格，复制完全一致 |
| `UX-00a` | 共享 Color / Typography Tokens | 不改布局的 Token 收敛 |
| `UX-00b` | Shape / Spacing / Icons | 统一图标尺寸与间距，但不做业务重构 |
| `UX-02a` | Board Header + HostSelector | 仅工作台顶部布局 |
| `UX-02b` | Attention + CompactRow | 待处理卡与普通行 |
| `UX-02c` | Done Fold + Empty/Offline | 已完成默认折叠和异常态 |
| `UX-03a` | Conversation typography | Agent 全宽/用户气泡/署名 |
| `UX-03b` | Markdown / CodeBlock | 行距、代码块、链接、复制代码 |
| `UX-03c` | Tool / Thinking | 可靠结果态、折叠、长输出 |
| `UX-06a` | Message Actions | 常显复制+更多、系统文本选择 |
| `UX-03d` | Scroll / Anchor | 新消息与恢复位置 |
| `UX-04a` | Composer visual | 轻量 Surface 与 Insets |
| `UX-04b` | Long Editor | 长文本编辑器视觉和文字溢出 |
| `UX-04c` | Write state UI | Unknown 入口、真实文案和警示 |
| `UX-05a/b` | i18n | 先基础 strings 和术语，后语言切换/回归 |
| `UX-07a/b` | STT | 独立能力 Spike，然后语音 UI；遵守 B 自适应规则 |
| `UX-08` | Icon / animation polish | 在功能和可读性通过后进行 |

> 这些是**设计至工程的切分建议**。仍未取得“开始编码”的授权；不能把整份 v0.1 总规范直接喂给 Pi 让其实施所有任务。

### 10.3 Pi 自检检查表

- [ ] 没有触碰 SSH 指纹认证、Herdr API 写入语义、Action Ledger 安全规则。
- [ ] 没有出现未实现的假按钮或不可信数字/摘要。
- [ ] 没有新增屏幕级硬编码色值/字号/工具 ID。
- [ ] 任意 Agent 状态只由真实可靠源推导，Done/Idle 未混淆。
- [ ] Agent 消息尾部仍有常显复制+更多；正文仍能系统选区。
- [ ] 语音 B 规则在正常/窄屏/大字号表现一致，不自动发送。
- [ ] 本次修改的组件实现了中英文资源，且大字号无裁剪。
- [ ] 无与正文长列表重叠的操作面板/横幅，IME/Insets 无双重 padding。
- [ ] 自动测试与 Fixture 截图真实产出；未测试项诚实记录。
- [ ] 一次 PR 只解决一个限定目标，可回滚。

---

## 11. 官方资料与验证注记

本规范综合使用以下 Android/WCAG 官方材料；**它们是外部设计/实现指导，不是 Herdr 项目的实测结果**。

- Material 3 in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Per-app languages / AndroidX 兼容路径: https://developer.android.com/guide/topics/resources/app-languages
- Minimum touch targets: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Compose accessibility / semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Compose animation quick guide: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Adaptive navigation: https://developer.android.com/codelabs/add-adaptive-layouts
- WCAG 2.2 Contrast (Minimum): https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum
- WCAG 2.2 Non-text Contrast: https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast

### UX-D0 Design Review Exit Criteria

进入 UX-01 编码包**之前**必须完成：

1. 用户确认 `BOARD-HOST-001` 的主机选择器位置与 `CMP-STYLE-001` 的轻量双层 Composer 视觉。
2. 冻结 Color/Typography/Spacing/Shape/Motion Tokens（可有少量特别标注的实验值），确认颜色与主要组件对比度。
3. 针对三屏给出 393dp light/dark + zh/en 的静态/Fixture 视觉原型供审美验收；320dp + 1.6x 字体检查不裁剪。
4. 形成明确 UX Task Packets，首轮只做一个小包，不能全量重构。
5. 所有未完成业务功能明确 Gate 与责任：不借设计名义制造假按钮或假的成功语义。

**结论**：本文件完成跨三屏设计系统的统一基线；后续先进行视觉审查与两项开放决策确认，再允许分任务实施。
