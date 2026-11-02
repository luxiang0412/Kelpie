# Kelpie 设计规范 v1（组件规范）

> 状态：**定稿草案**（2026-11-02）。确认后取代旧版 `docs/design/ux-d0/` 下的 Graphite × Editorial 规格（旧规格已于 2026-11-03 删除，见 git 历史）。
> 视觉稿：Design 画布「Herdr 移动端重设计」第一至三排（A1–A7 日间、夜间）。画布上 B / C 两排未采用，仅供对照。
> 用途：Compose 实现的唯一依据。数值以本文为准；画布和本文不一致时，以本文为准，并回头修正画布。

---

## 0. 设计原则（判断取舍时用）

1. **首屏只回答一个问题：谁在等我。** 等你的 agent 用大卡片放在最上面，其余一律降级。
2. **一个颜色只表达一件事。** 橙色只表示"在等你"，不用于品牌装饰、链接或普通按钮。
3. **不说假话。** 连接状态、数据新鲜度、发送回执三件事分开表达；不知道就写"未知 / 最后看到"。
4. **快捷操作只填输入框，不直接发出。** 唯一的例外是结构化审批，而且必须按住确认。
5. **少用容器。** 层级靠字号、字重和留白，不靠边框和阴影。卡片只给"等你"的事项用。

---

## 1. 颜色 Token

两套主题：**日间**和**夜间**。默认跟随系统，设置里可以固定为其中一套。
不使用 Material You 动态取色：状态色必须稳定。

### 1.1 基础色

| Token | 日间 | 夜间 | 用途 |
| --- | --- | --- | --- |
| `bg` | `#FFFFFF` | `#0E1013` | 页面背景（首页、对话、配对） |
| `bgGrouped` | `#F6F7F9` | `#0E1013` | 分组列表页背景（设置） |
| `surface` | `#FFFFFF` | `#171A1F` | 分组卡片、弹层、主机按钮 |
| `surfaceSubtle` | `#F4F5F7` | `#171A1F` | 折叠组按钮、输入框底、提示块 |
| `surfaceRaised` | `#F1F2F4` | `#20242B` | 用户消息气泡、夜间弹层里的次级块 |
| `hairline` | `#F0F1F3` | `#1C2026` | 列表分隔线（装饰性，不承担识别） |
| `outline` | `#E3E5E9` | `#2A2F37` | 胶囊按钮、芯片描边 |
| `outlineCard` | `#ECEDF0` | `#252A31` | 工具卡、diff 卡、提示行描边 |
| `textPrimary` | `#101217` | `#ECEEF1` | 标题、正文 |
| `textSecondary` | `#5A606B` | `#9AA1AC` | 副标题、元信息 |
| `textTertiary` | `#6B7280` | `#868D98` | 时间戳、回执 |
| `iconMuted` | `#7D838D` | `#7B828D` | 行尾箭头等辅助图标 |
| `primary` | `#101217` | `#ECEEF1` | 主按钮底色（发送、回复、按住允许） |
| `onPrimary` | `#FFFFFF` | `#0E1013` | 主按钮上的文字 / 图标 |
| `scrim` | `#101217` @ 42% | `#000000` @ 60% | 弹层遮罩 |

> 主按钮在夜间反色（浅底深字），不另设品牌色。品牌感来自橙色"等你"信号和 logo，不来自按钮。

### 1.2 状态色

| Token | 日间 | 夜间 | 用途 |
| --- | --- | --- | --- |
| `attention` | `#C2410C` | `#FF8A3D` | "等你"的圆点、logo 主点、按住确认的进度 |
| `attentionText` | `#9A3412` | `#FFA36B` | 等待时长、"在等你回答"标签、"查看并处理" |
| `attentionContainer` | `#FFF1E8` | `#2A1A10` | 等你卡片底色 |
| `attentionContainerInner` | `#FFFFFF` | `#3A2618` | 卡片内的图标底、芯片、行内代码 |
| `attentionOnContainerBody` | `#101217` | `#F3E9E1` | 卡片内正文（夜间偏暖，避免冷白刺眼） |
| `attentionOnContainerMeta` | `#5A606B` | `#B9A797` | 卡片内副标题 |
| `working` | `#1E5BD6` | `#7CB7FF` | 工作中进度环 |
| `workingTrack` | `#E4ECFB` | `#1B2A40` | 进度环轨道 |
| `workingContainer` | `#F4F7FD` | `#14202F` | 空态下"工作中"分组底色 |
| `done` | `#157347` | `#6EDBA8` | 完成勾、在线圆点 |
| `doneContainer` | `#EAF6EF` | `#12271D` | 完成勾的圆底 |
| `idle` | `#9AA0A9` | `#6B7280` | 空闲圆点（必须配文字，不单独承载含义） |
| `warning` | `#8A4A0B` | `#F5B76B` | 重连中文字 |
| `error` | `#B42318` | `#FF8A80` | 连接失败、diff 删除 |
| `diffAddBg` / `diffAddFg` | `#EAF7EF` / `#0E5A37` | `#12271D` / `#8EE6BB` | diff 新增行 |
| `diffDelBg` / `diffDelFg` | `#FDEDEC` / `#B42318` | `#2A1513` / `#FF8A80` | diff 删除行 |
| `codeBlockBg` / `codeBlockFg` | `#101217` / `#F4F5F7` | `#08090B` / `#ECEEF1` | 审批弹层里的命令框（两种主题都用深底） |

### 1.3 对比度（已用 WCAG 公式计算）

| 组合 | 日间 | 夜间 | 门槛 |
| --- | --- | --- | --- |
| `textPrimary` / `bg` | 18.73 | 16.39 | 4.5 |
| `textSecondary` / `bg` | 6.32 | 7.32 | 4.5 |
| `textTertiary` / `bg` | 4.83 | 5.70 | 4.5 |
| `textSecondary` / `surfaceSubtle` | 5.80 | 6.70 | 4.5 |
| `attentionText` / `attentionContainer` | 6.61 | 8.54 | 4.5 |
| `attentionOnContainerMeta` / `attentionContainer` | 5.72 | 7.21 | 4.5 |
| `attention` / `bg` | 5.18 | 8.12 | 3（图形） |
| `working` / `bg` | 5.96 | 9.14 | 3（图形） |
| `done` / `bg` | 5.87 | 11.21 | 3（图形） |
| `onPrimary` / `primary` | 18.73 | 16.39 | 4.5 |
| `iconMuted` / `bg`（日间）/ `surface`（夜间） | 3.82 | 4.50 | 3（图形） |
| `diffAddFg` / `diffAddBg` | 7.51 | 10.64 | 4.5 |

**规则**：
- 状态永远是 **颜色 + 形状 + 文字** 三者同时出现（例如"等你"= 橙点 + 卡片 + "等了 4 分钟"），色盲用户只看形状和文字也能分清。
- `hairline` 只做装饰，不能是唯一能看出"这里可以点"的线索。
- 实现时加一个 `ContrastTest`，对上表所有组合做断言（现有代码已有同名测试，可沿用）。

---

## 2. 字体与字阶

- **界面字体**：系统默认无衬线（中文落到系统黑体）。画布上用的 Inter 只是为了网页预览，App 内**不打包字体**，减少 APK 体积并保留系统字体缩放。
- **等宽字体**：系统 monospace；以后如果要统一观感，再考虑打包 JetBrains Mono（OFL 许可）。
- 字重只用三档：400 / 500 / 600，标题用 700。

| Token | 字号 / 行高（sp） | 字重 | 用途 |
| --- | --- | --- | --- |
| `display` | 28 / 34，字距 -0.4 | 700 | 首页大标题（"2 个 agent 在等你"） |
| `title` | 20 / 27 | 700 | 弹层标题 |
| `headline` | 18 / 24 | 600 | 二级页标题（"设置"） |
| `appName` | 17 / 22，字距 -0.2 | 700 | 顶栏 "Herdr" |
| `itemTitle` | 16 / 21 | 600 | 卡片里的项目名、对话页标题 |
| `rowTitle` | 15 / 20 | 600 | 列表行的项目名 |
| `bodyReading` | 15 / 24 | 400 | agent 消息正文 |
| `bodyCard` | 15 / 23 | 400 | 卡片里引用的 agent 原话 |
| `body` | 15 / 22 | 400 | 用户气泡、设置项 |
| `label` | 14 / 20 | 500 | 芯片、按钮 |
| `meta` | 13 / 18 | 400 | 副标题、状态行 |
| `metaStrong` | 13 / 18 | 600 | 等待时长、分组标题 |
| `caption` | 12 / 17 | 400 | 卡片副标题、时间 |
| `micro` | 11 / 14 | 400 | 消息回执（"已送达 · 22:27"） |
| `code` | 13 / 19 | 400 mono | 命令、路径、行内代码 |
| `codeSmall` | 12 / 20 | 400 mono | diff 行、工具卡命令 |

**规则**：
- 全部用 `sp`，跟随系统字体缩放。1.3× 和 2.0× 下允许卡片变高、芯片换行，**不允许截断等待时长和审批命令**。
- `micro`（11sp）只用于可有可无的回执，不承载必要信息。
- 中文不加字距；只有 `display` / `appName` 用负字距。

---

## 3. 间距、圆角、尺寸

### 3.1 间距（4 的倍数）

| Token | 值 | 典型用途 |
| --- | --- | --- |
| `space1` | 4dp | 图标和文字的最小间距 |
| `space2` | 6dp | 标题和状态行之间 |
| `space3` | 8dp | 芯片之间 |
| `space4` | 10dp | 卡片内行间 |
| `space5` | 12dp | 首页区块之间、行内元素 |
| `space6` | 16dp | 页面左右边距、卡片内边距 |
| `space7` | 20dp | 顶栏左边距、对话页正文左右边距、设置分组之间 |
| `space8` | 24dp | 配对页边距 |
| `space9` | 28dp | 配对页区块之间 |

页面左右边距：首页 / 设置 16dp；对话 20dp；配对 24dp。首页卡片内容和分组标题再缩进 4dp，与卡片内文字对齐。

### 3.2 圆角

| Token | 值 | 用途 |
| --- | --- | --- |
| `radiusXs` | 5dp | 行内代码 |
| `radiusS` | 10dp | 卡片内图标底、小按钮 |
| `radiusM` | 12dp | 卡片内命令框、提示块 |
| `radiusL` | 14dp | 工具卡、diff 卡、折叠组按钮、提示行 |
| `radiusXl` | 18dp | 设置分组、主按钮 |
| `radiusCard` | 20dp | 等你卡片 |
| `radiusSheet` | 28dp | 底部弹层上沿 |
| `radiusPill` | 高度 / 2 | 芯片、主机胶囊、输入框 |

### 3.3 尺寸

| 项 | 值 |
| --- | --- |
| 最小触控区 | 48×48dp（视觉可以更小，用 `minimumInteractiveComponentSize` 补足） |
| 顶栏高度 | 首页 56dp；对话 60dp（两行标题） |
| 图标按钮 | 44dp 视觉 / 48dp 触控 |
| 图标尺寸 | 顶栏 22dp；行内 16–18dp；行尾箭头 16dp |
| 首页列表行 | 最小 60dp |
| 设置列表行 | 单行 56dp；两行 72dp |
| 芯片 | 高 36dp（卡片里）/ 34dp（输入框上方），左右内边距 14dp |
| 输入框 | 最小高 52dp，最多 5 行后内部滚动 |
| 发送按钮 | 44dp 圆形 |
| 弹层主按钮 | 高 54dp |
| 配对页主按钮 | 高 56dp |
| 进度环 / 状态图标 | 32dp |
| 用户气泡最大宽度 | 内容区的 82% |

### 3.4 层级

- **不用阴影**。层级靠底色区分：`bg` → `surfaceSubtle` → `attentionContainer`。
- 唯一的"浮起"是底部弹层：靠 `scrim` 遮罩，不加阴影。
- 不用模糊、渐变和玻璃效果（长列表下成本高，也不符合这套风格）。

---

## 4. 图标

- 线性图标，24dp 网格，描边 1.8dp（行内小图标 2–2.4dp），圆角端点。
- 推荐直接用 Material Symbols Rounded（Apache-2.0）里的对应图标，统一调成 weight 300 左右，**不要再用文字字符当图标**（旧版底部导航的 `▤` `⚙` 要废掉）。
- 画布用到的图标及含义：

| 含义 | 图标 | 用在 |
| --- | --- | --- |
| 等你回答 | 放射状（spark） | 问题卡 |
| 权限请求 | 盾牌 | 审批卡、审批弹层 |
| 发送结果未知 | 圆圈 + 感叹号 | 未确认行 |
| 工作中 | 进度环（非图标，见 5.6） | 工作中行 |
| 完成 | 对勾 | 完成组 |
| 终端 | 方框 + 提示符 | 对话顶栏 |
| 设置 | 两条滑杆 | 首页顶栏 |
| 离线 | 斜杠 Wi-Fi | 离线条 |
| 扫码 | 四角框 | 配对按钮 |

**Logo**：三个圆点，中间的大且为 `attention` 色，两侧为灰色（日间 `#C9CDD4`，夜间 `#3A3F48`）。没有 agent 等你时三个点都是灰色（A7）。这个 logo 同时是 launcher icon 的基础（单色版用中间点）。

---

## 5. 组件

每个组件给出：结构、尺寸、状态、数据来源。名字就是建议的 Composable 名。

### 5.1 `HomeTopBar` — 首页顶栏

- 结构：Logo（22dp）· "Herdr"（`appName`）· 弹性空白 · `HostFilterPill` · 设置按钮（44dp）。
- 左内边距 20dp，右 12dp，高 56dp。
- 没有底部导航；设置是唯一的二级入口。

### 5.2 `HostFilterPill` — 主机筛选

- 高 36dp，`radiusPill`，1dp `outline` 描边，`label` 13sp/500，右侧 14dp 下箭头。
- 文案："全部主机" / 主机名。点开是下拉菜单，列出主机及其连接状态。
- 只有 1 台主机时**隐藏**。

### 5.3 `HomeHeader` — 大标题 + 新鲜度

- 标题（`display`）按情况变化：

| 情况 | 标题 |
| --- | --- |
| 有 N 个等你 | "N 个 agent 在等你" |
| 没有等你 | "没有 agent 在等你" |
| 离线 | 文案不变，颜色降为 `textSecondary`，见 5.15 |
| 还没配置主机 | 不显示首页，直接进配对页 |

- 状态行（`meta`，`textSecondary`）：7dp 圆点 + 文案。

| 连接情况 | 圆点 | 文案 |
| --- | --- | --- |
| 全部在线 | `done` | "2 台主机在线 · 实时" |
| 部分重连中 | `warning` 空心 | "1 台在线 · office 重连中" |
| 全部断开 | 无，换成 `OfflineBanner` | — |

> "实时"只在订阅正常、看门狗最近一次 ping 成功时显示。否则显示"N 秒前同步"。

### 5.4 `AttentionCard` — 等你卡片

首页最重要的组件，有三种变体。

**通用结构**（`attentionContainer` 底，`radiusCard`，内边距 16dp，行间 10–12dp）：

```
[图标 32dp] 项目名（itemTitle）              等待时长（metaStrong, attentionText）
            agent · 主机（caption, meta 色）
正文区（随变体）
操作区（随变体）
```

整张卡片可点，进入对应对话（A2）。卡片内的按钮单独响应点击。

**变体 A：问题（agent 在等回答）**
- 正文：agent 最后一条消息，`bodyCard`，**最多 4 行**，超出省略。行内代码用 `attentionContainerInner` 底。
- 操作区：快捷回复芯片（见 5.5）+ 右侧"回复 >"主按钮（`primary` 底，高 36dp）。
- 数据：状态为 `blocked` 的 pane；正文取 transcript 最后一条 assistant 文本；没有 transcript 时取 `pane.read` 最后几行，并在副标题加"来自终端画面"。

**变体 B：权限请求（结构化审批）**
- 正文：命令框（`attentionContainerInner` 底，`radiusM`，`code`，单行省略）。
- 底部一行：左侧说明（`meta`，例如"运行命令"）· 右侧"查看并处理 >"（`metaStrong`，`attentionText`）。
- 点击整卡打开 `ApprovalSheet`（5.12），**不在卡片上直接批准**。
- 数据：只有带请求身份的结构化审批才显示成这个变体（目前是 Claude hook 审批）。读屏判断出的 blocked 一律用变体 A。

**变体 C：离线时的缓存卡**（A6）
- 底色换成中性的 `#F6F3F1`（夜间 `#1E1915`），图标改成灰褐色，不显示等待时长。
- 问题卡：回复按钮禁用，左侧说明"联网后才能回复 · 草稿会保留"。
- 权限卡：副标题改为"可能已过期，联网后重新确认"。

**等待时长**：
- 从**第一次观察到**状态变成 blocked 开始计时。
- 如果是重连后才看到它已经 blocked，开始时间不可靠，显示"至少 N 分钟"。
- 格式：< 1 分钟显示"刚刚"；< 60 分钟显示"N 分钟"；之后显示"N 小时"。

**排序**（首页卡片之间）：
1. 权限请求（会过期），按剩余有效期从短到长；
2. 问题，按等待时长从长到短。
相同优先级时按主机、项目名稳定排序，避免列表跳动。

### 5.5 `QuickReplyChip` — 快捷回复

- 高 36dp（卡片里，`attentionContainerInner` 底，无描边）或 34dp（输入框上方，`surface` 底 + 1dp `outline`）。
- 点击后**把文字填进输入框并跳到对话页**，光标放在末尾，由用户点发送。
- 文案来源：
  - **v1**：固定预设，可在设置里编辑。默认"继续""好的""先解释一下"。
  - **以后**：从 agent 问题里提取出明确选项（画布上的"拆成两步 / 一次跑完"就是这种效果）。提取靠不住时退回预设，不能编造选项。
- 最多显示 3 个，放不下就横向滚动，不换行挤压"回复"按钮。

### 5.6 `AgentRow` — 工作中 / 完成的行

- 最小高 60dp，左右内边距 4dp，元素间距 12dp。
- 结构：状态图标（32dp）· 项目名（`rowTitle`）+ agent 类型（12sp，`textSecondary`）· 第二行当前活动（`meta`，单行省略）· 右侧时长（`caption`）。
- 状态图标：
  - 工作中：32dp 进度环，轨道 `workingTrack`，弧线 `working`，3dp 粗。弧线**匀速旋转**（不是真实进度，见第 6 节）。
  - 完成：32dp 圆，`doneContainer` 底 + `done` 对勾。
  - 离线时：3dp 虚线灰色圆环（A6），活动文字前加"最后看到："。
- 活动文字来源：transcript 最后一个工具调用（例如"正在运行 pnpm test"）或最后一条消息的前 40 字；都没有时显示"工作中"。
- 行之间用 `hairline` 分隔，最后一行不画。

### 5.7 `CollapsedGroupButton` — 折叠组

- 首页底部并排两个："今天完成 N""空闲 N"。高 52dp，`radiusL`，`surfaceSubtle` 底，无描边。
- 点击在原地展开成 `AgentRow` 列表（不是跳转新页）。
- **没有 agent 在等你时，"今天完成"默认展开**（A7），因为这时用户最可能想看结果。
- "今天"按本机时区 0 点重置。

### 5.8 `PendingWriteRow` — 发送结果未确认

- 高 48dp，`radiusL`，1dp `outlineCard` 描边，`surface` 底。
- 文案："N 条发送结果无法确认 · 项目名"，右侧箭头。
- 点击打开列表弹层，每条显示：发送内容摘要、目标、时间，以及两个操作——"去对话里看看"和"再发一次…"。后者要二次确认，并明确提示**可能会重复执行**。
- 位置：在所有等你卡片之后、工作中列表之前。

### 5.9 `ConversationTopBar` — 对话顶栏

- 高 60dp，底部 1dp `hairline`。
- 结构：返回（44dp）· 两行标题（项目名 `itemTitle`；状态行 `caption` = 6dp 状态点 + "在等你 · Claude Code · devbox"）· 终端按钮 · 更多按钮。
- 状态点颜色跟随 agent 状态；状态文字必须和首页一致。
- "更多"里放：技术信息（原来的 TechnicalInfoSheet）、在对话中搜索、复制会话路径。

### 5.10 消息组件

| 组件 | 规格 |
| --- | --- |
| `UserMessage` | 右对齐气泡，`surfaceRaised` 底，圆角 18/18/6/18（右下小角），内边距 10×14dp，`body`，最大宽 82%。下方一行回执（`micro`，`textTertiary`，见 5.14） |
| `AgentMessage` | **不用气泡**，全宽正文，`bodyReading`。行内代码 `radiusXs` + `surfaceSubtle` 底。Markdown 沿用现有自研渲染器 |
| `ToolCallCard` | 折叠态一行：图标 · 命令（`codeSmall`，单行省略）· 结果（✓ + 耗时，`done` 色；失败为 `error` 色 ✗）。`radiusL`，1dp `outlineCard`，内边距 10×12dp。点击展开输出；超过 12 行进入全文页（沿用现有逻辑） |
| `DiffCard` | 头部：文件图标 · 路径（`codeSmall`）· `+N`（`done`）· `−N`（`error`），底色 `#FAFAFB` / 夜间 `#14171B`。正文 `codeSmall` 20sp 行高，新增 / 删除行整行着色。默认最多显示 8 行，"展开全部" |
| `ThinkingBlock` | 一行"思考了 N 秒"，`meta` + `textTertiary`，点击展开，展开后正文用 `textSecondary` |
| `AwaitingAnswerBlock` | 对话末尾，agent 在等你时出现：标签"在等你回答 · 4 分钟"（`micro` 粗体，`attentionText`，带 6dp 圆点），下面是问题正文（`bodyReading`）。**不加卡片底色**，只靠标签区分 |

消息之间垂直间距 14dp；回执紧贴气泡，间距 -8dp（即 6dp）。

### 5.11 `Composer` — 输入区

- 上方：快捷回复芯片行（34dp，见 5.5）。agent 不在等你时隐藏。
- 输入框：`surfaceSubtle` 底，`radiusPill`，最小高 52dp，内边距 4dp。
  - 左：添加图片按钮（44dp，`+` 图标）。**v1 没有图片能力时隐藏**，不放灰按钮。
  - 中：文本框，`body` 15sp，占位符"回复 {项目名}…"，1–5 行自适应。
  - 右：语音按钮（44dp）。识别结果只进输入框。**v1 没有语音时隐藏**。
  - 最右：发送按钮，44dp 圆形，`primary` 底 + 向上箭头。输入为空或离线时禁用（底色 `outline`）。
- 离线时：输入框保持可编辑（草稿照常保存），发送按钮禁用，输入框上方出现一行 `meta`："联网后才能发送"。
- 长 prompt 全屏编辑器沿用现有实现，只换成新的颜色和字体 token。

### 5.12 `ApprovalSheet` — 审批弹层

- `ModalBottomSheet`，`surface` 底，上沿 `radiusSheet`，内边距 10/20/20dp，区块间距 16dp。拖动把手 36×4dp。
- 结构从上到下：
  1. 图标（36dp，`attentionContainer` 底 + 盾牌）· 来源（`meta`："herdr-android · Claude Code · office"）· 标题（`title`："允许运行这条命令？"）。
  2. **命令框**：`codeBlockBg` 底，`radiusL`，内边距 14dp；第一行工作目录 + `$`（11.5sp，灰）；第二行起是**完整命令**（`code` 14sp，自动换行，不截断，超过 8 行内部滚动）。
  3. 事实表：工具 / 请求来源 / 有效期，每行 `body` 14sp，左灰右黑，`hairline` 分隔。有效期用 `attentionText`，每秒倒数。
  4. 说明块（`surfaceSubtle` 底，`radiusM`，`meta`）："只对这一次请求生效。如果电脑上已经处理过，这里会自动失效，不会再执行一次。"
  5. 按钮行：左"拒绝"（flex 1，`surfaceSubtle` 底）；右"按住允许"（flex 1.4，`primary` 底，见 5.13）。高 54dp，`radiusXl`。
  6. "查看完整对话 >"（`label`，`textSecondary`）。
- 状态：
  - **已过期**：按钮行换成一行"请求已过期"，只留"关闭"。
  - **电脑上已处理**：同上，文案"已在其他地方处理"。
  - **提交中**：两个按钮都禁用，显示进度。
  - **结果未知**：弹层不自动关闭，显示"结果无法确认，请到终端或对话里核实"，**不提供重试**。
- 拒绝不需要按住；允许必须按住。

### 5.13 `HoldToConfirmButton` — 按住确认

- 按下后从左到右填充 `attention` 色进度条，**600ms** 填满后触发，同时给一次触觉反馈（`HapticFeedbackType.LongPress`）。
- 中途松手：进度条 150ms 退回，不触发。
- TalkBack 下：改为双击后弹出确认对话框（按住手势对读屏用户不可用）。
- 系统"移除动画"开启时：保留按住时长，进度条改为直接分段显示。

### 5.14 `SendReceipt` — 发送回执

用户消息下方一行 `micro`，四种状态：

| 状态 | 文案 | 样式 |
| --- | --- | --- |
| 发送中 | "发送中…" | `textTertiary` |
| 已送达 | "已送达 · 22:27" | `textTertiary` |
| 被拒绝 | "没发出去：agent 正在等确认" 等具体原因 + "重新编辑" | `error` |
| 结果未知 | "无法确认是否送达 · 查看" | `warning`，**不提供"重试"按钮**，只提供"查看"（进入 5.8 的弹层） |

"已送达"指 herdr 接受了请求，不代表 agent 已经处理。agent 开始回复后回执自动隐藏。

### 5.15 `OfflineBanner` — 离线条

- 位置：首页顶栏下方，左右 16dp 边距。`primary` 底（日间深色，夜间浅色），`radiusL`，内边距 10/8/10/14dp。
- 结构：离线图标 · 两行文字（"手机没有网络" `label` 600；"下面是 N 分钟前的内容 · 联网后自动更新" `caption`）· "重试"小按钮（36dp）。
- 区分两种离线：
  - **手机没网**（系统网络回调）："手机没有网络"。
  - **手机有网但连不上主机**："连不上 devbox"，副文案给出最近一次错误类别（认证失败 / 超时 / 指纹变化）。指纹变化必须用 `error` 色，并引导到设置里处理。
- 离线时整页内容色降一级（标题用 `textSecondary`），卡片换成变体 C。

### 5.16 设置页组件（A4）

| 组件 | 规格 |
| --- | --- |
| `SettingsGroup` | 分组标题（`metaStrong`，`textSecondary`，左缩进 4dp）+ `surface` 底卡片（`radiusXl`），组之间 20dp |
| `HostRow` | 72dp。左 10dp 状态点（在线实心 `done`；重连空心 `warning`；失败实心 `error`）· 主机名（`rowTitle`）+ 备注 · 第二行状态（"已连接 · 62 ms · herdr 0.9.5 · 7 个 agent" / "重连中 · 第 2 次 · 3 秒后重试"）· 右侧箭头或"立即重试"胶囊 |
| `SwitchRow` | 72dp，标题 + 说明 + 开关（52×32dp）。"后台保持连接"的说明里必须写出今天已用的配额 |
| `ValueRow` | 56dp，标题 · 当前值（`textSecondary`）· 箭头 |
| `ThemeSegmented` | 三段：跟随系统 / 日间 / 夜间，高 44dp，选中段 `primary` 底 |
| "添加主机" | 48dp，`surface` 底，`radiusL`，居中 `+` 图标和文字 |

### 5.17 配对页（A5）

- 大 logo（40dp）· 标题（`display`）· 说明（15/23，`textSecondary`）。
- 三步：编号圆（28dp；当前和已完成步骤为 `primary` 底，未到的为 `surfaceSubtle` 底）+ 步骤标题（15sp/600）。第一步带可复制的命令框。
- 底部：主按钮"扫描二维码"（56dp）+ 文字按钮"手动填写 SSH 信息"（48dp）。
- **依赖**：扫码需要主机端组件（Companion）生成二维码，目前还没有。v1 只实现"手动填写"，主按钮先隐藏；手动表单沿用现有 `HostEditorScreen` 的字段，换成新 token。

### 5.18 空态（A7）

- 标题："没有 agent 在等你"；状态行："2 台主机在线 · 有新情况会通知你"。
- logo 三个点全灰。
- 工作中分组改用 `workingContainer` 底卡片，放在原来等你卡片的位置。
- "今天完成"默认展开。
- 完全没有 agent 时：标题"还没有 agent"，下面一段说明怎么在电脑上用 herdr 启动 agent，不放插画。

---

## 6. 动效

原则：动效只用来说明"发生了什么变化"，不做装饰。120Hz 下每帧 8.3ms，长列表里不放持续动画。

| 场景 | 时长 | 曲线 | 说明 |
| --- | --- | --- | --- |
| 按压反馈 | 90ms | 标准 | 用 Compose 默认 ripple，颜色用 `textPrimary` @ 8% |
| 新卡片出现 / 消失 | 200ms | 减速（emphasized decelerate）/ 加速 | 高度展开 + 淡入；**用 `animateItem()`，不手写** |
| 状态切换（工作中 → 等你） | 220ms | 标准 | 行移动到卡片区，颜色交叉淡入 |
| 折叠组展开 | 180ms | 标准 | 只动高度 |
| 页面进入对话 | 220ms | emphasized | 从右滑入 + 淡入；支持预测性返回 |
| 底部弹层 | 进 280ms / 出 200ms | emphasized | 用 Material3 默认 |
| 进度环旋转 | 1200ms 一圈，匀速 | 线性 | **只在屏幕上可见的行运行**；超过 3 个工作中时只转第一个 |
| 按住确认 | 600ms | 线性 | 见 5.13 |
| 数字变化（等待时长） | 无动画 | — | 每分钟直接更新文字，不做翻牌 |

**降级**：
- 系统"移除动画"开启：全部改为瞬间切换，进度环改为静态 3/4 圆弧。
- 省电模式：进度环停止旋转。
- 对话列表里**不加任何入场动画**（避免流式输出时整表抖动）。

---

## 7. 可访问性

- 每张等你卡片合并成一个语义节点，读作："parsedock，Claude Code，devbox，等了 4 分钟。迁移脚本写好了……。按钮：拆成两步、一次跑完、回复。"
- 状态点、进度环都 `contentDescription = null`，状态由文字读出。
- 首页标题变化（例如从 1 个变 2 个）用 `liveRegion = Polite` 播报。
- 发送回执、审批结果用 `liveRegion = Polite`；连接失败用 `Assertive`。
- 触控区全部 ≥ 48dp；芯片之间至少 8dp。
- 字体 2.0× 下：卡片标题行允许换行，等待时长移到第二行；按钮行变成竖排。
- 深浅色都要跑一遍 TalkBack 和 2.0× 字体截图。

---

## 8. 落到代码

### 8.1 Token 怎么组织

建议新建模块 `:core:designsystem`，把 `feature/board/.../design/Tokens.kt` 迁过去并按本文重写：

```kotlin
@Immutable data class KelpieColors(/* 1.1 + 1.2 里的全部 token */)
val LightKelpieColors = KelpieColors(bg = Color(0xFFFFFFFF), /* … */)
val DarkKelpieColors  = KelpieColors(bg = Color(0xFF0E1013), /* … */)

@Immutable data class KelpieType(/* 第 2 节字阶，TextStyle */)
object KelpieSpace { val s1 = 4.dp; /* … */ }
object KelpieRadius { val card = 20.dp; /* … */ }

val LocalKelpieColors = staticCompositionLocalOf { LightKelpieColors }

@Composable fun KelpieTheme(
    mode: ThemeMode = ThemeMode.System,   // System / Light / Dark，来自设置
    content: @Composable () -> Unit,
)
```

- 同时把 token 映射进 `MaterialTheme.colorScheme`，让 `ModalBottomSheet`、`Switch` 这类 M3 组件自动跟随；但**业务组件只读 `LocalKelpieColors`**，不直接用 M3 的 `primary` / `tertiary`，避免语义混乱。
- 状态色通过一个函数取：`KelpieColors.forStatus(AgentStatus): StatusStyle(fg, container, icon)`，取代现在的 `statusFg` / `statusBg` lambda。

### 8.2 和现有实现的对应

| 现有 | 新规范 |
| --- | --- |
| `BoardScreen`（按 5 种状态分组 + 底部导航） | `HomeScreen`：`HomeHeader` + `AttentionCard` 列表 + `PendingWriteRow` + `AgentRow` + `CollapsedGroupButton`；删除底部导航 |
| 主机选择胶囊（标题下方） | `HostFilterPill`（顶栏右侧，单主机时隐藏） |
| 看板顶部审批卡 | `AttentionCard` 变体 B + `ApprovalSheet` |
| `ConversationScreen` 顶栏 | `ConversationTopBar`（加终端按钮，技术信息移进"更多"） |
| `ToolCard` | `ToolCallCard`（折叠态单行化）+ 新增 `DiffCard` |
| `ConversationComposer` | `Composer`（芯片行 + 药丸输入框，未实现的能力隐藏） |
| Unknown 横幅 / 待确认发送弹窗 | `SendReceipt` + `PendingWriteRow` + 列表弹层 |
| `HostsScreen` | 设置页（`SettingsGroup` / `HostRow` / `SwitchRow` / `ThemeSegmented`） |
| 指纹确认 AlertDialog | 保留逻辑，改用 `ModalBottomSheet` + 本文 token（命令框样式显示指纹） |
| 系统 `AlertDialog` | 一律换成弹层或页内状态 |

### 8.3 v1 范围外（画布上画了，但先不做）

- 扫码配对（依赖 Companion）。
- 从 agent 问题里提取快捷回复选项（v1 用预设）。
- 图片和语音输入（按钮隐藏）。
- 终端视图（顶栏按钮先打开现有的"最近输出"文本，C3 那种只读终端以后再做）。

---

## 9. 验收清单

- [ ] 第 1.3 节所有对比度组合写进 `ContrastTest`，日间夜间都通过。
- [ ] 首页在 360dp / 393dp / 412dp 宽，1.0× / 1.3× / 2.0× 字体下截图，等待时长和审批命令没有被截断。
- [ ] 日间 / 夜间 / 跟随系统切换即时生效，不重启 Activity。
- [ ] 读屏判断的 blocked 永远不显示成权限卡（变体 B）。
- [ ] 快捷回复芯片点击后只填输入框，没有任何路径能不经确认直接发送。
- [ ] 结果未知的发送在任何界面都没有"重试"按钮。
- [ ] 按住允许：松手不触发；TalkBack 下走确认对话框。
- [ ] 离线时：所有"实时"字样消失，时间戳显示为"N 分钟前"。
- [ ] 进度环只在可见时转动，"移除动画"开启时静止。
- [ ] 不再出现文字字符当图标、系统 `AlertDialog`。
