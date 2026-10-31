# Herdr Android · UX-D0 / 三屏视觉验收包 v0.1

日期：2026-11-01  
状态：**Visual Review Candidate / 等待用户审美确认；不构成 Pi 编码授权**

## 0. 用途与依据

本包延续以下仓库外已完成的 UX-D0 设计文件：

- `HERDR-UX-D0-BOARD-SPEC-v0.2.md`
- `HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md`
- `HERDR-UX-D0-COMPOSER-SPEC-v0.2.md`
- `HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md`
- `HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md`
- `STATUS-BRIEF-FOR-EXTERNAL-REVIEW.md`（项目实际实现状况，不等于当前源码复审）

**重要边界**：展示的是一个纯 HTML/CSS/JS 的离线视觉原型，非 Jetpack Compose 代码、非真机截图、非 Herdr 实际数据。所有任务、工具、主机及成功状态均为人工设计 Fixture。操作控件用于布局预览，不连接 SSH、Pi 或服务器。

## 1. 决策登记（精确区分 Approved 与 Proposed）

| Decision ID | 内容 | 状态 | 使用规则 |
| --- | --- | --- | --- |
| `BOARD-DONE-001` | 已完成 Agent 分区默认收起 | **Approved** | 工作台原型遵守 |
| `CONV-ACTIONS-001` | Agent 消息末尾常显轻量「复制 + 更多」，正文长按保留 Android 系统文本选择 | **Approved** | 原型只呈现布局，正式交互须 Compose 真机验收 |
| `CMP-VOICE-001` | 功能真实接通后，常规宽度 Mic 常显；窄屏/大字体时进入「更多」；语音结果只插入草稿、绝不自动发送 | **Approved** | 原型演示自适应，不代表语音已实现 |
| `BOARD-HOST-001` | 主机选择器在工作台主标题下方 | **Proposed** | 本轮按推荐方案制样，用户尚未明确确认 |
| `CMP-STYLE-001` | Composer 使用轻量双层 Surface | **Proposed** | 本轮按推荐方案制样，用户尚未明确确认 |
| `CONV-TOOL-002` | 长 Tool 输出保留有限预览 + 独立完整输出阅读页 | **Proposed** | 原型提供“查看完整输出”入口，完整输出页未原型化 |

无其他决策被静默提升为 Approved。仅批准两个 Proposed 之后，才可以考虑整体 Design System 升级到 v1.0。

## 2. 交付的视觉原型

- 交互式离线 HTML：`HERDR-UX-D0-VISUAL-PROTOTYPE-v0.1.html`
- 三屏 × Light/Dark × zh-CN/en 的 **12 张 393dp 静态截图**。
- 三屏 × 320dp × 英文 × Dark × `fontScale 1.6` 的 **3 张额外截图**。
- 六宫格概览：`HERDR-UX-D0-visual-contact-sheet.png`（中文，393dp，三屏深浅色）。

在 HTML 上可以切换：Screen / Theme / Locale / Window width（320、393、600dp）/ Font scale（1.0、1.3、1.6）/ Scenario（视页面而定）。

### 工作台 Board

- 上：品牌微标签 → 页面标题 + 搜索/设置 → **标题下一行主机筛选 + 联机状况**。
- 内容区：`需要处理` 高关注卡片；`运行中` 紧凑行；`已完成`、`空闲` 默认收起。
- 不从 `blocked` 推断真实审批或操作内容。原型中的“等待下一步指令”与摘要仅是 Fixture；真实产品在缺乏结构化来源时必须改为通用状态说明。
- 跨主机聚合和「活动」信息目前未实现：原型呈现的是目标 IA，正式版本在具备数据层能力前不能有假入口。

### 对话 Conversation

- 顶部：返回、任务名、Pi/主机/状态；大字号允许变高。
- 用户右侧低饱和气泡；Agent 正文全宽；Tool 以低强调容器呈现。
- 消息尾部「复制 + 更多」常显；中文/英文文案已在概念层验证长度。
- 工具卡展示的是「工具结果已收到」而非「执行成功」，不推测 exit code 或测试统计；数据关联必须以可靠 ID 为准。
- 当前原型没有演示真实系统文本选择、Android 通知/剪贴板或真实 scroll anchor，这些必须在后续 Compose 验收。

### Composer

- 对话页底部为轻量 Surface，正文与操作区分层；Send 唯一强强调。
- 独立长 Prompt 编辑页保留编辑上下文和草稿状态。
- 语音功能在原型中是**未来启用状态的设计预览**；正式应用未完成 STT 时必须隐藏未接通的 Mic 按钮。
- 宽度 `<360dp` 或 `fontScale≥1.3` 时，Mic 从 Action Row 移到 More；Send 不得移动到菜单中。
- 大字体英文场景不假设真实 Android IME；原型键盘是假画面，需真机验收 Insets。

## 3. 初轮视觉 QA 结果

测试工具：Chromium headless + Playwright；来源为本地生成 HTML，截图已产出。

| 检查 | 本轮结果 | 不能证明什么 |
| --- | --- | --- |
| 393dp × 三屏 × 两主题 × 两语言 | 12/12 截图成功 | Compose 运行效果或产品功能可用性 |
| 320dp × 1.6 倍字体 × 三屏（英文深色） | 3/3 截图成功 | Android 字体缩放和系统 Insets 实际情况 |
| JS 运行时异常 | 0 | Android UI 测试是否通过 |
| 320dp 布局整体横向溢出 | 静态 DOM `scrollWidth == clientWidth` | 文本未裁剪、TalkBack 可达性或局部元素绝对无碰撞 |
| 标题/图标碰撞 | 首轮出现，已改为窄屏大字号标题与按钮分行 | 仍应在真机 320dp + 1.6 复核视觉节奏 |
| 语音自适应 | 原型依据 `width <360`、`fontScale ≥1.3` 切换 Mic / More | 语音识别 API、权限或回调是否实现 |

**未通过的验收仍待执行**：真实 Android `fontScale=1.6`、IME/三键导航、TalkBack 阅读顺序、颜色在透明混合后的截图对比度、系统剪贴板选择、长对话滚动与复合状态流。

## 4. 本轮设计评审要重点讨论的地方

1. **主机选择器**：位于主标题下方，是否比同一行更清楚？在 320dp 大字体时允许状态换到下一行。
2. **看板注意力密度**：高关注卡片不应该太多，超过 1–2 个后后续改为更紧凑条目。
3. **对话发言人**：仅在一组 Agent 消息开始处显示中性的 Pi/Claude 标识，不让绿色小字重复出现。
4. **工具卡片**：默认折叠、打开仅显示有限内容；打开完整输出进入阅读页；复制命令/结果分开。
5. **Composer 形态**：Surface 与键盘的间距是否轻盈；小字体/大字号下发送键是否始终易触达。
6. **语言与字体**：英文标题长文本允许换行或安全截断；必须有查看完整标题路径。
7. **状态准确性**：Unknown 写操作有持久入口；数据已过期不可伪装在线/可审批；运行中不是永远动画。

## 5. 进入工程前的“设计冻结”门槛

必须同时满足：

- 用户正式确认 `BOARD-HOST-001` 和 `CMP-STYLE-001`，或明确提出替代方案。
- 用户看过 393dp 的工作台/对话/Composer 的深浅色和中英文，认可整体气质、内容密度与主操作路径。
- 确认 320dp + 大字号的标题重排规则，允许真机微调 Token 但不能跳过验收。
- 确认 i18n 第一版 `zh-CN` + `en` + 跟随系统；界面语言不翻译 Agent 原始消息。
- 技术安全边界未改变：不自动重发 Unknown、无读屏一键批准、未接通功能不得出现假入口。
- Pi 的第一轮任务采用 **单一、低风险改动**，在仓库实际检查完成后指定文件与 API，不根据外部摘要猜源码。

完成上述条件后可进入 UX-01a（大字体标题栏修复），随后 UX-01b（InlineCode 原文一致性），再迁移共享 Tokens。**禁止把 UX-D0 全套文档作为一条“全部实现”任务。**

## 6. 下一阶段任务顺序（尚无编码授权）

1. `UX-01a` FontScale/TopAppBar — 单点修复裁剪，无业务重构。
2. `UX-01b` InlineCode — 删除靠添加空格实现 padding 的 hack；正确复制原文。
3. `UX-00a` Color + Typography — 不改行为，只迁移到统一 Token。
4. `UX-02a` Board Header/HostSelector — 按冻结方案改工作台顶栏。
5. `UX-02b/c` Board Attention/Row/Done — 分小包改信息密度和异常状态。
6. `UX-03a–d` Conversation — 正文、Tool、复制选择、滚动，分别验收。
7. `UX-04a–c` Composer — 外观、长文本、发送结果状态，分别验收。
8. `UX-05` i18n — 先资源治理，再语言设置与全屏适配。
9. `UX-07` 语音 — 独立可行性 Spike，设计与识别实现分开。

**本包为视觉评审资料，不是最终 Compose 截图，也不是“已完成产品重构”的声明。**
