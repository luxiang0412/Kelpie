# Herdr Android · UX-D0 / Composer（输入与发送）正式设计规格 v0.1

> **阶段**：UX-D0，供设计评审，**不授权 Pi 编码**。  
> **日期**：2026-11-01；**视觉方向**：Graphite × Editorial（D）。  
> **关联文档**：`HERDR-UX-D0-BOARD-SPEC-v0.2.md`、`HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md`、`HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md`、`STATUS-BRIEF-FOR-EXTERNAL-REVIEW.md`。  
> **边界**：区分当前已实现（TextField、Room 草稿、三态发送、全屏长 Prompt）与推荐设计（视觉/语音/引用/附件）。本文包含建议尺寸与文案，尚未经过真机视觉评审；不把尚未实现的按钮展示为可用功能。

## 0. 体验目标与不能妥协的原则

**核心用户任务**：用户在地铁、碎片时间、单手握机的情况下，看到 Agent 的上下文后，以尽量少的动作发送一句准确的指令；即使网络不稳、中文 IME 正在组合输入、切换 Agent 或 App 被系统杀死，也不应误发送、丢失可恢复的内容或错误宣称已执行。

成功判据：

1. 393dp 标准宽度、320dp 窄屏与 fontScale 1.6 不裁剪文字、发送动作或取消操作。
2. 1–2 行日常输入轻量；长指令 5 行以内自然展开；更长内容使用全屏编辑器。
3. **Enter 换行，点击发送才真正提交**；中文拼音组合输入与光标/选区不受损。
4. 草稿不会因切换 Host/Session/Agent、页面旋转、输入法显示或发送回执覆盖而串写。
5. Accepted、Rejected、Unknown 文案与动作精准；Unknown 不自动重试，不伪装为失败。
6. 语音仅作**语音转文字草稿**：识别后可编辑、**绝不自动发送**；未实施时不暴露假功能。
7. 控件是同一套 Design Tokens 的一部分，不长成独立大型聊天盒子；TalkBack/触达面积可验收。
8. 拥有可执行的自动化测试与手工测试场景，不让 Pi 自行判断“应该够漂亮了”。

---

## 1. 现状与设计范围

### 1.1 当前项目已有（据 2026-10-31 状态说明，非重新审计代码）

- Compose Material3 状态式 `OutlinedTextField`，`minLines=1/maxLines=5`，长文本内部滚动；Enter 换行。
- 独立全屏长 Prompt 编辑器，≥48dp 发送控件、IME Insets 处理。
- Room 草稿库 `herdr-drafts.db`，400ms 防抖、8000 字符限制、Host/Session/Agent 身份隔离及 NeedsConfirm 回退。
- `Accepted / Rejected / Unknown` 写操作语义、单次提交文本快照、Unknown 不自动重发，Pending/Unknown 记录持久化。
- 真实 Pi 消息收发已验收；但中文 IME 组合态、TalkBack、极限字号和部分窗口竞争路径尚需专项复核。

### 1.2 本轮 UX-D0 设计覆盖

- Composer 默认/聚焦/多行/全屏/离线/未知发送结果等状态的视觉规范。
- 与对话列表、键盘、底部导航栏和 Android Insets 的关系。
- 操作分级：发送、粘贴、引用、语音转写、未来附件、更多指令。
- 草稿/目标身份/发送状态提示的可发现性。
- i18n 中英双语、TalkBack、减少动效、窄屏适配。
- 可拆解为小的 Pi Task Packet 的验收矩阵。

### 1.3 明确不属于本次交付

- 真实语音识别代码与录音权限实现（后续 `UX-07` 独立 Spike/任务）。
- 图片或文件传输协议、多模态附件的远端上传、云端识别服务。
- Slash Command 协议、Agent Approvals、离线发送队列、终端键盘完整模拟。
- SSH/Herdr API、WriteLedger、Room 数据库 schema 的重构。

---

## 2. 视觉与交互方向：轻量双层 Composer

**推荐方案 C1 / Selected for Design Baseline**：同一个低对比 Surface 内“文本区 + 一行工具/发送动作”；不要大面积悬浮重卡片。非交互提示弱于正文；发送是唯一高强调控件。

候选方案比较：

| 方案 | 优势 | 代价 | 结论 |
|---|---|---|---|
| A 单行输入 + 右发送 | 极致节省高度 | 粘贴代码、长 Prompt 与附件很别扭 | 不采用 |
| **B 轻量双层 Surface** | 输入与操作分层；弱网状态和语音入口可扩展 | 需要仔细处理 IME/多行高度 | **本稿推荐** |
| C 常驻半屏编辑抽屉 | 长 Prompt 更强 | 占据阅读空间、单手操作负担重 | 仅长编辑模式采用 |

### 2.1 393dp 主屏线框（默认态）

```text
┌─────────────────────────────────────────┐
│                                         │
│            当前 Agent 对话正文           │
│                                         │
│               ↓ 新消息（有需要才出现）    │
├─────────────────────────────────────────┤
│ [发送结果未知 ▸]                        │ ← 仅存在 Unknown 才显示、可找回
│ ┌─────────────────────────────────────┐ │
│ │ 给 Pi 发送下一步指令…               │ │ ← 正文输入，与操作行在同一 Surface
│ │                                     │ │
│ │ [＋] [麦克风*] [展开]      [发送 ↑] │ │ ← 只有真实可用时才显示麦克风
│ └─────────────────────────────────────┘ │
└───────── Android 导航/IME 安全区 ────────┘

* 麦克风是设计预留，不代表已实现；初版可省略。
```

默认 Composer 不单独显示常驻大标题「消息输入」或永久的“连接正常”文案；这些是高频无效信息。只有真正有意义的草稿恢复、离线、Unknown、身份切换才增加提示。

### 2.2 输入 4 行后的形态

```text
┌─────────────────────────────────────────┐
│ ┌─────────────────────────────────────┐ │
│ │ 帮我继续完成测试。                  │ │
│ │ 检查是否还有错误日志，并…           │ │
│ │ 优先修复会导致消息重复的问题。       │ │
│ │ （内部可滚动，最多约 5 行）          │ │
│ │─────────────────────────────────────│ │
│ │ ＋  麦克风*  展开             发送 ↑ │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

`*` 表示该功能完成时才可显示。超过多行后仍然是一个整体的 Composer，工具行不跟随文本内部滚动。

### 2.3 焦点与发送层级

- 点击文本区域：获得焦点、显示 IME；不自动滚动正文到末尾（若用户在阅读历史，保持锚点）。
- 空白输入状态：发送按钮禁用，图标/背景可降低强调，但维持 48dp 点击区域。
- 有可发送文字：品牌 Accent 发送按钮可用；禁用与可用必须同时可由 TalkBack 识别。
- 正在发送：按钮呈低强调 Processing 状态，阻止同一目标重复提交；**不应因一条慢请求让整个文本框完全无法编辑**（已有单次快照语义）。
- Unknown：出现独立可见状态条和持久 Action Journal 入口；不能使用类似普通发送失败的重试图标。

---

## 3. 尺寸、字体、网格与色彩

### 3.1 基础布局 Token（初版候选值）

| 元素 | 360–599dp | ≤359dp | ≥600dp（预留） |
|---|---|---|---|
| 内容区水平外边距 | 16dp（对话正文 18–20dp 时可微调） | 12dp | Composer 最大宽 720dp，居中 |
| Composer 表面内边距 | 水平 14dp，上下 10–12dp | 12dp | 16dp |
| 容器圆角 | 20dp | 18dp | 20dp |
| 文本输入正文字体 | 16sp / 24–25sp | 同 | 同 |
| Placeholder | 14–15sp / 21sp，Secondary | 同 | 同 |
| 工具图标 | 20–22dp | 20dp | 22dp |
| 工具点击区 | ≥48×48dp | 同；内容放不下时收进 `＋/更多` | 同 |
| 发送点击区 | ≥48×48dp，视觉圆面 40dp | 同 | 同 |
| 工具按钮间距 | 0–4dp 的外间距，点击区不重叠 | 同 | 4–8dp |
| 输入高度 | 1–5 行 + 内边距，自适应 | 同 | 同 |
| Composer 与消息区的分隔 | 1dp subtle 或背景层差 | 同 | 同 |
| Composer 底部距系统导航 | 由 WindowInsets 决定 | 同 | 同 |

不以 1080×2400 物理像素作为布局基准；依据窗口 dp 与系统 Font Scale。不可强制给多行输入框固定高度；必须验证 IME 输入法、导航手势和三键导航两种情形。

### 3.2 色彩

全部复用 `HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md` 的候选 Token，不发明 Composer 私有配色。

| 区域 | Light | Dark | 用法 |
|---|---|---|---|
| 页面底 | `#F7F8FA` | `#10151D` | Canvas |
| Composer Surface | `#FFFFFF` | `#181F2A` | 轻量主要表面 |
| Composer 细边框 | `#DFE5ED` | `#344052` | 1dp，聚焦时可用品牌色细强调 |
| 工具区文字/图标 | `#4F5D70` | `#AEB9C8` | Secondary |
| 输入正文 | `#172033` | `#EEF2F8` | Primary |
| 发送按钮 | `#3556CE` | `#9BB0FF` | 唯一高强调操作 |
| Pending/Unknown | 使用项目 caution 语义色 | 对比度单独校验 | 图标 + 文案，不整块铺亮色 |

本表是设计建议，不等于颜色对比度已验收；按钮前景、状态文案、焦点环与背景须逐组件计算并在真机核实。

### 3.3 视觉规范

- 非聚焦时使用低对比 1dp 描边；聚焦时 1–2dp Accent Focus Ring，不能因为聚焦整个卡片猛烈发光。
- 不在 Composer 外再套第二层大号卡片或投影；没有层层包裹。
- 发送按钮仅用一处高强调色；麦克风、加号、展开、字数均中性。
- 没有真实“草稿已保存”回执时不虚构该文字；正常保存可以不常驻提醒，只有恢复/冲突/保存失败时提示。
- 发送结果/权限问题比工具栏装饰优先展示，但警示不应无限挤压对话区。

---

## 4. 操作可用性与优先级

| 操作 | 默认位置 | 前提 | 行为 |
|---|---|---|---|
| **发送** | 右下圆形高强调 | 输入非空、目标可信、连接可写、无冲突 | 单次不可变文本快照、现有 Herdr WriteOutcome |
| 编辑文本 | 主输入区域 | 永远可用（离线也可编辑） | Enter 换行，支持选择/粘贴 |
| 长 Prompt | 左下 `展开` | 已有功能 | 进入全屏编辑，原草稿不丢 |
| 语音转文字 | 工具行 `麦克风`（可用后） | 系统有识别服务且用户允许 | 听取、转写、审核、插入草稿，**不自动发** |
| 附件/更多 | 工具行 `＋`（未来） | 至少一个实际可用的子动作 | 仅显示已实现能力；无内容时不展示假入口 |
| 引用消息 | 从对话消息的 `更多` | 有可复制正文，目标 Draft 可用 | 插入光标处/末尾，不覆盖旧草稿 |
| 终端辅助键 | 将来终端模式的独立栏 | 用户真的进入终端 | 不占据普通对话 Composer |

**建议规则**：在最窄 320dp 或 fontScale 1.6 时优先保留文本、展开与发送。语音和附件可收进已存在的 `＋/更多`；若加号没有真实动作，则直接隐藏，不要显示不可点装饰。

---

## 5. Composer 的交互状态机

### 5.1 本地编辑状态

| 状态 | 用户看到 | 允许行为 | 注意 |
|---|---|---|---|
| `Empty` | Placeholder、禁用 Send | 输入/粘贴/打开长编辑器 | 不允许发送纯空白 |
| `Editing` | 文本、多行输入、Send 可用（其他前提满足） | 继续编辑、选中、引用、发送 | 发送状态不改变编辑原文 |
| `Focused` | Focus Ring + IME | 中文输入组合、光标/选区 | 不自动触发 Send |
| `ExpandedEditor` | 全屏长 Prompt、确认/取消 | 大段文本编辑 | 取消不覆盖旧草稿；确认后更新草稿 |
| `OfflineDraft` | “离线 · 可继续编辑草稿” | 编辑、复制、恢复 | Send 禁用，无离线发送队列 |
| `RestoredDraft` | 已恢复草稿（适度一次性提示） | 编辑、删除、发送前确认目标 | 不覆盖用户新输入 |
| `IdentityUncertain` | “会话身份无法完全确认”及草稿恢复选择 | 查看旧草稿、确认恢复/放弃 | 不静默将旧 Agent 草稿附着到新 Agent |
| `SaveError` | “草稿保存失败，请复制备份” | 复制、重试存储、继续输入 | 绝不显示“已保存” |

**本地状态与远端写操作正交**：`Editing`/`Focused` 可能与 `Sending` 同时存在。不要设计成一个互相覆盖的巨大 enum。

### 5.2 远端写操作状态

```text
Editing (草稿、可修改)
   │ 用户明确点击 Send，且 SendTargetCheck 通过
   ▼
Sending (提交不可变快照 + Action ID)
   ├─ Accepted → Herdr 已接受（不代表 Agent 已执行）
   ├─ Rejected → 有明确拒绝回执，保留用户内容
   └─ Unknown  → 结果不可确认，保留 Pending 文本与 Action ID
                          ├─ 查看记录/核对会话
                          └─ 用户再次明确确认 → 新 Action ID 的全新发送
```

严格要求：

1. 单次发送只对**当时内容**形成不可变快照。若用户继续输入，收到旧结果时不能清掉新文本。
2. 用户连点 Send 不会产生两次网络提交；当前目标按现有单飞约束。
3. Accepted 在同一草稿版本且无后续编辑时可清空已提交内容；**不表示 Pi 处理完成**。
4. Rejected 展示真实原因，原文本保留；用户修改或重发前有可见目标信息。
5. Unknown 不自动重试、不自动清空、不自动称作失败；可检查当前 transcript，但“没看见”不等于“没有送达”。
6. Unknown 重发必须二次确认，明确“可能重复执行”，并生成新的 Action ID。
7. 断线后只允许保存草稿；不建立离线排队投递。
8. `agent_session` 缺失，目标回退身份不可靠时必须 NeedsConfirm；不能“还在同一个 Pane”就假设同一个 Agent。

### 5.3 写入状态在 UI 的表现

| 状态 | 主按钮 | 提示位置与文案（zh） | 用户动作 |
|---|---|---|---|
| Ready | Accent ↑ | 无常驻横幅 | 发送 |
| Sending | 禁止重复提交、低调进行中 | `正在发送…`，可以隐含在按钮无须巨幅横幅 | 等待/继续编辑 |
| Accepted | 恢复正常 | `已接收指令`（短暂/会话回执） | 可继续编辑；不得显示“Agent 已完成” |
| Rejected | 恢复正常，可再次提交 | `发送被拒绝` + 可靠原因 | 修改后重新发送 |
| Unknown | 普通 Send 禁止不经确认重复提交原操作 | `发送结果无法确认` + `查看记录` | 对账、二次确认新发送 |
| Offline | 灰化 Send | `已离线，可继续编辑草稿` | 编辑、手动重连 |
| ChangedTarget | 禁止立即发送 | `当前 Agent 已改变，请确认发送目标` | 重新核实目标 |

**多项问题并发时的排布**：优先展示 Unknown/身份风险，之后离线，再是草稿保存错误；显示一条最紧急短提示与“查看全部状态”入口，不堆叠三条大横幅。

### 5.4 Unknown 具体线框

```text
┌─────────────────────────────────────────┐
│ ? 发送结果无法确认             [查看记录]│ ← 有历史 Action ID 可找到
│   远端可能已收到；请勿重复提交。         │
├─────────────────────────────────────────┤
│ 输入草稿…                              │
│ +   展开                       发送 ↑  │ ← 若要再发旧内容须走二次确认
└─────────────────────────────────────────┘
```

“查看记录”展示 Action ID、目标 Host/Session/Agent、提交文本快照（在安全的本地 UI 中）、结果更新时间、明确的人工复核动作；不要在通知、错误日志或锁屏直接曝光原始 Prompt。

---

## 6. 草稿生命周期与跨会话策略

### 6.1 身份与存储（承接已实现机制）

`DraftKey = hostStableId | herdrSessionName | agentSession`；如果稳定 `agentSession` 不可用，使用 Pane/Agent/title/cwd 的临时占用指纹时，应按不可靠身份设计显式确认恢复，不应误认为强身份。

- 草稿与 `PendingWrite` 必须逻辑隔离；不能因为清空草稿而清空 Unknown 记录。
- 继续使用独立 Room 草稿存储；不在本设计阶段改 schema 或新建重复持久层。
- 400ms 防抖是正常时减少写盘的策略，**不是突然断电/进程中止前 400ms 不丢的承诺**；手动返回/生命周期变化尽力同步落盘。
- 不宣称“草稿已保存”直到数据库写操作实际成功；失败时保留内存内容并展示低干扰警示。
- 8000 字符限制沿用当前实现；后续任务必须先核对“字符”到底按 UTF-16 code units 还是 Unicode code points 计数，并避免截断代理对/组合字符。
- 不在普通 Logcat、错误报告、通知正文中输出草稿与未确认发送全文。

### 6.2 不覆盖规则

1. 进入 A 会话开始编辑 → 切换 B → 返回 A，恢复 A 草稿（如果身份可确认）。
2. 后台恢复或 Activity 旋转后，草稿异步载入**不得覆盖用户已经输入的新文本**。
3. 全屏编辑取消 → 恢复进入前的原文本；全屏编辑确认 → 替换为用户确认的新版本。
4. 发送旧快照时继续编辑 → Accepted 只能清除旧快照对应版本，不能清除新编辑。
5. 只有临时指纹时同 Pane 重启相同 Agent → 不静默恢复旧草稿到新输入框，而应给出“发现可能属于上次会话的草稿，是否恢复”选择。
6. 删除 Host 的草稿依现有策略级联删除；属于危险操作，主机删除 UI 应明确说明其附属草稿影响。

### 6.3 引用插入策略（本稿推荐，待评审）

推荐 **在当前光标位置插入清晰 Markdown 引用块**，当当前选择区域存在时替换该选择必须先显式确认；保守实现可在尾部追加而不覆盖已有草稿。引用示例：

```text
请继续排查这个问题。

> 编译失败：找不到符号 ExampleType

```

- 引用不自动发送，不自动将多个 ToolResult 重新拼成错误的 Assistant 结论。
- 引用超出草稿最大容量：**禁止静默裁剪**，提示“内容过长，请复制或先精简草稿”。
- 引用按钮不能在用户已经选中文字时强制丢掉选区而执行其他操作。

---

## 7. 语音转文字（设计完整，实施另立 UX-07）

### 7.1 体验边界

- 语音是文本输入的可选快捷方式，**不是语音聊天**。
- 原始录音不默认保存、不加入 Agent Context、不上传到任何自建后端。
- 使用 Android 系统语音识别时，具体识别服务**可能使用网络**；不能承诺本地离线。API 31+ 可在设备支持时优先使用 on-device recognizer，minSdk 26–30 或不可用时可提供系统默认服务/键盘语音的选择。
- UI 语言与语音识别语言是不同设置：系统英语 UI 不等于只能识别英语；优先允许识别语言跟随系统或用户明确选择 zh-CN/en-US。
- **任何最终转写只进入用户可编辑草稿**，不直接调用 `sendPrompt`。

### 7.2 状态机

| STT 状态 | UI 表现 | 允许动作 | 不允许 |
|---|---|---|---|
| `Unavailable` | 不展示 Mic 或给可读不可用原因（设置） | 使用键盘 | 空白无反应 |
| `Idle` | 中性麦克风按钮 | 点击请求权限/检查支持 | 后台偷录 |
| `PermissionNeeded` | Android 系统权限 + 适度解释 | 允许/拒绝 | 必须授权才能继续文本编辑 |
| `Preparing` | 文案“准备语音识别” | 取消 | 假称已在录音 |
| `Listening` | 麦克风状态 + 轻量波形/计时（若有真实数据） | 停止/取消 | 自动发给 Agent |
| `Processing` | 文案“正在转写…” | 等待/取消 | 长期锁死输入框 |
| `Review` | 转写文字在可编辑区域、显示“插入草稿” | 编辑、插入、放弃 | 不经确认覆盖已有草稿 |
| `Error/NoSpeech` | 简洁失败原因 | 重新尝试/键盘输入 | 伪造文本或重试录音 |
| `Interrupted` | 被电话/生命周期/权限变化打断 | 保留已确认文本、关闭识别 | 后台继续录音 |

### 7.3 语音 UI 推荐

```text
┌─────────────────────────────────────────┐
│ 语音输入                         [×]    │
│ [麦克风状态] 正在聆听…                   │
│ “继续刚才的测试，并检查是否有…”          │ ← 中间候选文本，不视为最终草稿
│                                         │
│ [取消]                      [完成转写]  │
└─────────────────────────────────────────┘

确认后：
┌─────────────────────────────────────────┐
│ 识别结果（可编辑）                       │
│ 继续刚才的测试，并检查是否有错误……       │
│ [放弃]                      [插入草稿]   │
└─────────────────────────────────────────┘
```

- 录音状态不覆盖原草稿；识别中间结果只在临时区域展示，不直接持续覆盖 TextFieldState（否则会破坏中文组合态、光标及已有草稿）。
- 插入草稿后用户仍需自行点击 Send；点击取消不改变草稿。
- 不在 Mic 图标上承诺“离线/私密”除非实际验证识别服务、设备模型和网络行为。
- 无权限/用户拒绝：关闭识别界面并返回普通 Composer，发送功能不受影响。
- 录音或识别可能有成本和隐私风险，首次使用前清楚说明由系统语音服务处理音频。

### 7.4 工程实施前置研究（只记录，不在本轮执行）

- Android `SpeechRecognizer` 初始化、RecognitionListener 的生命周期及 `destroy()` 释放；服务能力检测与权限、取消时的回调竞争。
- `createOnDeviceSpeechRecognizer` 为 API 31+；识别可用性因设备/语言/服务而异；`EXTRA_PREFER_OFFLINE` 不保证强制本地。
- 语音结果与正在变化的草稿之间按不可变快照/显式插入解决冲突。
- 当前 8000 字符限制、长内容识别失败、识别出命令但用户需要改错的 UX。
- 不在普通 Composer 可视化原始录音文件；禁止音频在错误报告中泄露。

---

## 8. 长 Prompt 编辑器

### 8.1 页面职责

长编辑器是 Composer 的扩展，不是另一个 Agent 会话，不拥有自己的发送状态或独立 Room 草稿库。

推荐顶部：`取消` / `编辑长指令` / `完成`；底部是必要操作提示（字数、超限状态），不放不安全的“Enter 即发送”。

### 8.2 内容规则

- 支持中文、英文、代码、Tab、换行、粘贴堆栈、长路径；原样保留空白，不自动格式化代码。
- 保留光标位置和选区；从普通 Composer 进入可将焦点/光标传递，但实现方案要真机检查。
- 取消后不覆盖原草稿；确认时校验大小与当前 Agent 身份，并处理编辑期间外部草稿版本变化。
- 自动保存策略和“完成”按钮的提交语义要明确：完成=写回本地草稿，不代表已发送。
- 对话页面恢复时不应出现旧编辑器将新输入覆盖的竞争。

### 8.3 全屏编辑线框

```text
┌─────────────────────────────────────────┐
│ 取消         编辑长指令           完成   │
├─────────────────────────────────────────┤
│ 请帮我分析以下报错：                    │
│                                         │
│ ```                                     │
│ [长代码/堆栈原文，完整可选可粘贴]        │
│ ```                                     │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│ 2,489 / 8,000 字符 · 已编辑，未发送     │
└─────────────────────────────────────────┘
```

---

## 9. Insets、滚动、导航与减少动效

### 9.1 与对话页滚动的关系

- Composer 始终锚定可用窗口底部，正文是独立的 Lazy 列表；其高度变化不能使阅读旧消息的用户跳到底部。
- 用户正在浏览历史时，IME 打开/关闭优先保持可见阅读锚点；若用户刚刚点击“回到最新”，才随 Composer 高度变化维持最新消息可见。
- 新消息浮动入口应始终位于 Composer 上方且不遮挡文本选择工具栏；更长提示不能盖住发送按钮。
- 切换 Input Focus、打开菜单、显示语音 Sheet、显示长编辑器时，返回键应首先关闭当前临时界面/IME，而不是直接离开对话。
- Compose Insets 使用单一可信消费路径，禁止 `imePadding + navigationBarsPadding` 在多个层级重复加出双倍空白。

### 9.2 动效提案

| 交互 | 候选时长 | 原则 |
|---|---|---|
| 获得焦点 | 120–180ms | 细边框/focus 强调，不扩大 Surface 投影 |
| 输入栏随文本变高 | 160–220ms 或原生自然 reflow | 高频输入时不得每字符触发布局动画 |
| 发送按钮状态 | 100–160ms | 色彩/图标切换，不做持续发光 |
| 编辑器展开/关闭 | 220–280ms | 依系统导航/返回运动，可中断 |
| 语音输入 Sheet | 200–280ms | 无权限/中断时可直接关闭 |
| Unknown 状态条 | 160–200ms | 初次出现一次，不持续跳动 |
| 发送接受反馈 | 120–160ms | 短暂、不过度重播 |

- 在省电/减少动效场景下可直接 snap，而不是无限重复 Loading 波形。
- 不用动画暗示已成功送达或 Agent 已执行；只有 WriteLedger 的真实状态可以驱动这些文案。

---

## 10. i18n：简体中文与 English（建议资源字典）

完整 i18n 由 `UX-05` 处理，本文件先冻结术语与文案意图，不要求本轮修改 Strings。

| 建议 Key | 中文 | English |
|---|---|---|
| `composer_placeholder` | 给 Agent 发送下一步指令… | Send your next instruction… |
| `composer_send` | 发送 | Send |
| `composer_sending` | 正在发送… | Sending… |
| `composer_send_accepted` | 指令已接收 | Instruction accepted |
| `composer_send_rejected` | 发送被拒绝 | Send rejected |
| `composer_send_unknown` | 发送结果无法确认 | Send outcome unknown |
| `composer_send_unknown_detail` | 远端可能已收到指令，重新发送可能重复执行 | The remote agent may have received this; sending again may repeat the action |
| `composer_review_action` | 查看发送记录 | Review send record |
| `composer_offline` | 已离线，可继续编辑草稿 | Offline — you can keep editing your draft |
| `composer_target_changed` | 当前 Agent 已改变，请确认发送目标 | Agent changed — confirm the destination |
| `composer_expand` | 展开编辑 | Expand editor |
| `composer_long_title` | 编辑长指令 | Edit long instruction |
| `composer_long_done` | 完成编辑 | Done editing |
| `composer_long_cancel` | 取消编辑 | Cancel editing |
| `composer_draft_restored` | 已恢复草稿 | Draft restored |
| `composer_draft_save_failed` | 草稿保存失败，请复制备份 | Draft couldn't be saved — copy a backup |
| `composer_restore_draft` | 发现旧草稿，是否恢复？ | A previous draft was found. Restore it? |
| `composer_quote_too_long` | 引用内容超出草稿长度限制 | Quoted text exceeds the draft limit |
| `composer_voice` | 语音输入 | Voice input |
| `composer_voice_listening` | 正在聆听… | Listening… |
| `composer_voice_processing` | 正在转写… | Transcribing… |
| `composer_voice_review` | 检查识别结果 | Review transcription |
| `composer_voice_insert` | 插入草稿 | Insert into draft |
| `composer_voice_cancel` | 放弃转写 | Discard transcription |
| `composer_voice_unavailable` | 当前设备无法使用语音识别 | Speech recognition isn't available on this device |

- 词组与数量使用资源格式化，不将字符串片段直接拼接；动态状态文案不能吞掉 TalkBack 的关键风险提示。
- App 语言切换不改变 Agent 原文、代码和终端路径，也不触发 SSH 重连或重发。
- Speech 输入语言不强制等同界面语言；识别功能上线时另设独立偏好或系统默认。

---

## 11. Accessibility / TalkBack / 隐私

### 11.1 基本要求

- 所有可触摸动作≥48×48dp；Icon 可20–22dp，但点击区不可小于推荐尺寸。
- 发送按钮有状态：`发送`、`发送中不可重复`、`离线不可发送`、`目标身份需要确认`、`发送结果未知请查看记录`。
- 正文 TextField 能正常让辅助技术选择内容、移动插入点、粘贴；不用上层手势抢焦点。
- `liveRegion=Polite` 只用于重要状态变化，如 Unknown/Rejected/草稿恢复；不可每个识别部分结果都连续朗读整段文本。
- 语音 Sheet 支持 TalkBack 清晰读出录音状态与取消，使用权限前说明录音目的。
- 触控、文案和按钮在 320dp + fontScale 1.6 时不能裁剪，可将辅助工具折叠进已存在的更多菜单，发送保留。
- 内容需要目标/确认时，视觉和 TalkBack 均明确提示；不能只用颜色。

### 11.2 隐私边界

- 不将草稿或录音识别中间内容写进普通日志、监控通知、截图上传或错误上报。
- 若后续语音依赖设备的系统语音服务，明确音频是否可能经网络处理；不假装绝对离线。
- 用户主动复制/引用后才进入剪贴板/草稿；敏感内容沿用对话规范的剪贴板敏感标记策略。
- Unknown Action 的发送原文/目标信息仅在本地安全页面可查看，不在锁屏通知中直接暴露。

---

## 12. Fixture/验收矩阵（设计定义，不宣称已经执行）

| ID | 场景 | 验收要点 |
|---|---|---|
| CMP-01 | 空草稿、在线 | 禁用 Send，Placeholder 清晰，无大面积空盒 |
| CMP-02 | 一行短中文 Prompt | 显示正常、按钮可触达、单手可发 |
| CMP-03 | 五行英文/中文混合 | 输入扩展但工具行仍固定可见 |
| CMP-04 | 贴入 300 行堆栈 | 内容内滚/长编辑器入口可用、不会卡住 |
| CMP-05 | 中文拼音尚未上屏 | 拼音组合与光标准确，Enter 不触发发送 |
| CMP-06 | 复制粘贴带 Tab/CRLF/Emoji | 内容不被意外格式化 |
| CMP-07 | 全屏编辑取消 | 返回原草稿，不覆盖 |
| CMP-08 | 全屏编辑确认 | 更新本地草稿，不自动发送 |
| CMP-09 | 旋转/Activity 重建 | 草稿、光标与身份不串 |
| CMP-10 | Force-stop 后恢复 | 已落盘部分恢复，未写盘风险如实标注 |
| CMP-11 | Host/Session/Agent 交替切换 | 不串稿，身份不可靠时 NeedsConfirm |
| CMP-12 | 发送中继续输入 | 旧 Accepted 不清掉新内容 |
| CMP-13 | 连点两次发送 | 单一目标单飞，网络提交只发生一次 |
| CMP-14 | 明确 Rejected | 展示真实原因，保留草稿 |
| CMP-15 | 超时 Unknown | 记录持久、无自动重试、看得见入口 |
| CMP-16 | Unknown 二次确认 | 新 Action ID，不假设第一次未送达 |
| CMP-17 | 离线但缓存可读 | 可编辑复制，禁止发送/离线队列 |
| CMP-18 | 同 Pane 新 Agent | 旧草稿待确认，不直接套入 |
| CMP-19 | 引用超过 8000 字 | 不截断原草稿，不静默插入 |
| CMP-20 | 草稿保存失败 | 明确保存失败、可复制备份 |
| CMP-21 | IME 展示/关闭，手势与三键导航 | 输入栏不被盖住、无双层 Insets |
| CMP-22 | 用户在历史阅读中弹出 IME | 保持阅读锚点、不跳最新 |
| CMP-23 | 320dp + fontScale 1.6 | 发送/展开常在，按钮≥48dp、不裁剪 |
| CMP-24 | 393dp，浅色/深色，zh/en | 字阶、视觉层次、提示位置一致 |
| CMP-25 | TalkBack 全流程 | 输入、编辑、状态、确认顺序合理 |
| CMP-26 | 语音服务不可用（未来 Fixture） | 普通文字输入不受影响 |
| CMP-27 | 语音权限拒绝（未来 Fixture） | 不自动再请求、不关闭文本输入 |
| CMP-28 | 语音取消/识别完成（未来 Fixture） | 草稿原样保留/显式插入，绝不自动发送 |
| CMP-29 | 语音识别时用户切换 Agent（未来 Fixture） | 转写不得插入错误目标草稿 |
| CMP-30 | Unknown + 离线 + 保存失败并存 | 不堆三条 Banner，所有问题仍可找回 |

### 12.1 截图清单（正式实施时）

- `393dp × dark/light × zh/en`：空输入、一行输入、四行输入、输入法弹出。
- `320dp × fontScale 1.3/1.6`：工具行、发送按钮、Unknown 提示、文本超长。
- 全屏长 Prompt 编辑器：短文本/大段代码/超限提示/取消。
- 发送状态：Sending/Accepted/Rejected/Unknown/Offline/ChangedTarget。
- 草稿恢复与 NeedsConfirm、Voice Review（未来功能才需截图）。
- 确定性 Fixture 与真实 Redmi 手机分别检查；不以纯 Preview 代替设备运行。

### 12.2 性能与测试指标

- 沿用 JVM、Compose UI（AVD 可执行）与真机人工验收；不能把模拟器启动指标当真机帧时。
- 记录 500/2000 条历史 + IME 弹出、粘贴大段文本和全屏编辑切换的帧时/Trace（有可用真实基线时才做改善宣称）。
- UI 动画不允许每输入一字触发昂贵整页 remeasure；解析、保存、转写回调不能阻塞主线程。
- 既有发送/草稿和 16/16 UI 测试不回归；任何语音测试必须包含“绝无自动发送”的领域层测试。

---

## 13. 后续 Pi Task Packet 切分建议（本文件不是 Prompt）

1. **UX-04a：Composer Surface 与布局** — 只改布局、Token、Insets/IME 视觉，保留现有 TextFieldState/发送逻辑。
2. **UX-04b：长 Prompt 编辑器视觉** — 焦点/取消/确认/字号/窄屏，不改草稿数据库 schema。
3. **UX-04c：发送状态呈现与 Unknown 入口** — UI 状态矩阵、TalkBack、横幅与 Journal 入口；不改 Herdr 写入语义。
4. **UX-04d：草稿恢复/身份确认视觉** — NeedsConfirm、保存失败、跨进程状态；逻辑 Bug 另立工程任务。
5. **UX-06c：消息引用插入** — 依照对话 spec 定义文本来源/容量检查；不与语音混 PR。
6. **UX-07a：语音技术 Spike** — 权限、可用性、系统服务、语言与隐私，先在隔离 App 验证。
7. **UX-07b：语音转文字 UI** — 实现回调后的转写审阅与插入；真实语音数据验收、无自动发送。
8. **UX-05：i18n** — 国际化基础、动态切换、本地化资源、语言+大字体截图；不重新发明版面。

每个 Task Packet 必须含：准确现有源码路径与接口（Pi 在仓库核对后填写）、单一目标、允许/禁止改动范围、样式尺寸、状态矩阵、操作步骤、确定性 Fixture、测试命令、截图证据、未知风险与 Stop Condition。**没有设计审查批准不得让 Pi 一次执行这 8 项。**

---

## 14. 待 UX-D0 最终确认的决策

| ID | 内容 | 本稿建议 | 状态 |
|---|---|---|---|
| `CMP-STYLE-001` | Composer 视觉形态 | 轻量双层 Surface | 推荐，待确认 |
| `CMP-VOICE-001` | Mic 入口 | 已实现可用时常显；窄屏收进更多 | 推荐，待确认 |
| `CMP-QUOTE-001` | 引用插入 | 光标位置插入；保守可尾部追加 | 推荐，待确认 |
| `CMP-UNKNOWN-001` | Unknown 提示 | Composer 上方低高度、持久可找回 | 安全基线，待视觉确认 |
| `CMP-DRAFT-001` | 草稿身份不可靠时恢复 | 显式确认，不自动覆盖 | 安全基线，不得回归 |
| `CMP-STT-001` | 语音转写后自动发送 | **绝不自动发送** | 安全基线，不得回归 |

此稿不代表全部 Composer 决策均已冻结，仍需用户审美确认。三屏设计整合评审后再发布 UX-D0 总规范和后续 Pi 小任务书。

---

## 15. 官方技术参考（外部设计/实现依据，非项目实测）

- Android Compose state-based text fields: https://developer.android.com/develop/ui/compose/text/user-input
- Android Compose text selection: https://developer.android.com/develop/ui/compose/text/user-interactions
- Android speech recognizer API: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android RecognizerIntent（offline hint/partial results 并非保证）: https://developer.android.com/reference/android/speech/RecognizerIntent
- Android touch target guidelines: https://developer.android.com/develop/ui/compose/accessibility/api-defaults
- Android Compose copy/paste and sensitive clipboard: https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste
- Android Compose animation performance: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Android Compose Insets: https://developer.android.com/develop/ui/compose/layouts/insets

> **结束条件**：这是一份 UX-D0 Composer 正式设计评审稿。完成工作台/对话/Composer 三屏统一审查并由用户确认关键视觉和行为方案后，才允许进入实现任务拆解；目前不做 Pi 编码。
