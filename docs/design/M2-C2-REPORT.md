# M2-C2 报告 — Composer 与真实 Pi 会话验收

> 基线：M2-C1 `2c213d8`｜设备：Redmi 22021211RC（MIUI/A14，真机）+ 隔离 AVD `herdr_m2c1`（Android 34）

## 0. 摘要

| 目标 | 状态 |
| --- | --- |
| P0 Composer 交互（1–5 行、内部滚动、长 Prompt 模式、Enter 换行、48dp 发送、IME、TalkBack 动态字体） | ✅ 已实现并验证（UI 测试 + 真机截图） |
| P0 草稿持久化（DraftKey / Room / 防抖 / 上限 / 删除策略 / 不覆盖新输入） | ✅ 已实现；跨进程恢复**未验证** |
| P0 发送状态机（Editing/Sending/Accepted/Rejected/Unknown，单飞、快照、不自动重发、新 Action ID） | ✅ 已实现并验证（UI 测试 + JVM 测试） |
| P0 发送目标身份校验（host/session/pane/agent session/可写性） | ✅ 已实现并验证（JVM 测试） |
| P0 真实 Pi 会话端到端验收 | ❌ **未执行**（见 §3） |
| P1 缓存与数据正确性复核 | ✅ 复核结论见 §2.4（发现 0 个真实错误） |
| P1 自动化测试 | ✅ Compose UI **16/16**，JVM 新增 15 个 |
| 视觉验收 | ✅ 深色真机截图（浅色/IME/长 Prompt 见 §4） |

---

## 1. 已实现并验证

### 1.1 Composer（P0 §2）

`ComposerBar` 重写要点：

- `OutlinedTextField`（Compose Material3 状态式 API，未引入实验性 `BasicTextField2`）：
  `minLines = 1`、`maxLines = 5`，超出内部滚动；`heightIn(min = 48.dp, max = 140.dp)`；
- `imeAction = ImeAction.Default` + `keyboardType = Text` → **Enter 插入换行，不自动发送**；
- 发送按钮 `heightIn(min = 48.dp)`、`widthIn(min = 64.dp)`，独立点击，`contentDescription = "发送" / "发送中，请稍候"`；
- `imePadding() + navigationBarsPadding()`：IME 弹出时输入栏上移（真机截图验证）；
- 语义：输入框 `contentDescription = "指令输入框"` + `stateDescription`（空 / N 个字符 / 发送中）；
  结果横幅 `liveRegion = Polite`（TalkBack 会朗读 Accepted/Rejected/Unknown）；
- 长 Prompt：`⤢` 按钮打开**全屏编辑器**（`Dialog` + `usePlatformDefaultWidth = false`），
  等宽大字号、可粘贴代码/堆栈、显示字符数；
- 动态字体：全部使用 `MaterialTheme.typography` + `min/maxLines`，未硬编码字号。

### 1.2 草稿持久化（P0 §3）

- **独立 Room 库** `herdr-drafts.db`（`DraftDatabase`/`DraftEntity`/`DraftDao`）——
  不复用 `herdr.db`：后者用 `fallbackToDestructiveMigration`，升级会清空缓存，草稿不能跟着丢。
- `DraftKey = hostId | session | (agentSessionPath ?: pane 占用指纹)`：
  - 不同主机 / 不同 Herdr session / 不同 agent session → 不同 key（JVM 测试覆盖）；
  - `agent_session` 缺失时用 `paneId + agent + title + cwd` 的指纹，**降低**串稿概率；
    发送前仍有目标校验兜底（不是绝对保证，见 §5 风险）。
- 防抖 400 ms（`DraftStore.DEBOUNCE_MS`），页面离开 `flushDraft()` 立即落盘；
- 单条上限 **8000 字符**，超出截断；空文本 → 删除记录；
- 删除主机 → `deleteByHost(hostId)` 一并清理（避免遗留敏感代码）；
- 加载草稿时**不覆盖**用户已开始输入的新内容（`if (composer.text.isEmpty())`）。

### 1.3 发送状态机（P0 §4）

| 状态 | 行为 |
| --- | --- |
| Editing | 可编辑、可发送；发送按钮仅在文本非空且未发送中可用 |
| Sending | 提交**不可变快照**；`sending = true` 期间重复点击直接 return（单飞） |
| Accepted | 显示「已接受（Accepted）：远端已收到，未代表执行完成」；**仅当草稿自提交后未变化**才清空；草稿记录同步删除 |
| Rejected | 显示「被拒绝：<code>（内容已保留，可修改后重发）」；**输入保留** |
| Unknown | 显示「结果未知（Unknown）：不会自动重发…」；保留 `pendingText` + `pendingActionId`；不自动清空、不自动重试 |

Unknown 的重新发送：必须点「重新发送…」→ 弹出确认对话框（明确提示**可能重复执行**）→ 确认后调用发送，
由 `WriteLedger.begin()` 生成**新的 Action ID**，旧 Unknown 记录原样保留，不会被静默重放。

### 1.4 发送目标校验（P0 §5）

`SendTargetCheck.check(pane, writable, editedAgentSession, editedOccupancy)`：

| 情况 | 结果 |
| --- | --- |
| pane 不存在 | Blocked(`pane_gone`) |
| pane 上没有 agent | Blocked(`no_agent`) |
| 连接不可写（离线/重连中） | Blocked(`not_writable`) —— **不排队**，只允许存草稿 |
| agent session 已变化 | NeedsConfirm → UI 提示「请确认后重新点击发送」 |
| pane 占用指纹变化（无 agent session 时） | NeedsConfirm |

不宣称消除 TOCTOU：读取目标与写入之间没有原子锁，只做可检测的一致性检查。

### 1.5 测试

**Compose UI（AVD `herdr_m2c1`）：16/16 通过**
- 看板 9 个（M2-C1 建立的基线，无回归）
- Composer 7 个（`ComposerUiTest`）：输入启用发送、多行换行保留、Accepted 文案、Rejected 保留内容、
  Unknown 需二次确认才能重发（断言 `sends == 0` 直到确认）、长 Prompt 编辑器、发送中禁用。

**JVM 新增 15 个**（`:core:data` `DraftTest`）：DraftKey 隔离 4、占用指纹 1、空 pane 1、
目标校验 6、草稿存取/截断/删除 4（含 `deleteByHost` 只影响该主机）。

### 1.6 视觉

`spike/results/m2c2-composer-dark.png`（真机深色）：Markdown 正文、折叠的思考/tool 行、
新 Composer（占位符、`⤢` 长编辑入口、48dp 发送按钮）、底部三态说明。

---

## 2. 已实现但未验证

| 项 | 缺什么 |
| --- | --- |
| 草稿跨进程恢复 | 未做「杀进程 → 重开」实测（Room 写入路径已实现） |
| 草稿跨导航/横竖屏 | 逻辑已实现，未在真机逐步验收 |
| IME 行为（中文组合输入、光标、选区） | 仅模拟器自动化 + 真机截图，未做人工中文输入法验收 |
| 动态字体放大 | 未在真机把字号调到最大验收 |
| TalkBack | 语义已加，未做完整 TalkBack 走查 |
| 长 Prompt 粘贴大段代码 | 未在真机粘贴真实堆栈 |

---

## 3. 未实现

- **真实 Pi 会话端到端验收（P0 §6）**：本轮**未执行**。
  原因：该步骤需要在已授权的远端测试 Session 内实际写入并观察 pi 产生新消息，
  当前轮次未取得针对「本轮真实写操作」的明确授权，且远端写入属于能影响真实 Agent 状态的操作。
  按任务书「超出当前授权先列出变更并取得批准」的要求，本轮**不做**，留待下一轮授权后执行。
  已知状态：`Accepted/Rejected/Unknown` 语义与 `agent.prompt` 写入链路在 M4 已真机验证过一次；
  本轮只做了发送侧的语义与 UI 变更（快照清空、目标校验、Unknown 二次确认），
  未改变远端协议调用方式。
- 真实 Pi 分支切换验收（需同一会话内产生分支）。
- 增量映射的真实输入命中统计（APPEND/PATCH/FULL 计数）——需真实会话才有意义。

---

## 4. 缓存与数据正确性复核（P1 §7）

| 检查项 | 结论 |
| --- | --- |
| FNV-1a 可能碰撞吗 | 可能（64 位），但 key 还含 `length` + `PARSER_VERSION`；碰撞只会导致「同长度不同内容」被复用，概率 ~2⁻⁶⁴ |
| 是否有文本等值验证 | ❌ 没有。**记录为已知限制**（不修：加等值校验要存全文，内存代价与收益不成比例；且碰撞概率可忽略） |
| LRU 是否按预期淘汰 | ✅ `accessOrder = true` + `removeEldestEntry(size > 256)`，测试覆盖（插入 296 条 → size ≤ 256） |
| 解析器升级是否正确失效 | ✅ key 含 `PARSER_VERSION`（当前 2） |
| 超长文本是否绕过缓存 | ✅ >120k 字符直接解析不入缓存（测试覆盖） |
| 不同 Session 是否错误复用 | ✅ 按内容寻址：内容相同则 AST 相同，复用安全；内容不同必然不同 key |
| ToolResult 更新是否产生新状态 | ✅ `items.toMutableList().also { it[at] = old.copy(...) }` → 新不可变列表，Compose 可观察 |
| 条目 Key 是否稳定 | ✅ `"${paneId}-${item.index}"`（消息序号，追加时不变） |
| 是否有可变集合原地修改 | ✅ 无：所有条目都是 `data class` copy，列表每次创建新实例 |
| 分支切换是否清除旧关联 | ✅ 有效序列由 `ConversationBranch` 重建；测试 `branchSwitch_doesNotLeakResultsAcrossBranches` |

**发现 0 个真实错误**，未做大改；仅修正一处**误导性文案**：
无 id 的 ToolResult 原先显示「按顺序配对」，在位置配对被移除后措辞已不准确 → 改为「未配对（无可靠 ID）」。

---

## 5. 已知问题与风险

| # | 问题 | 影响 |
| --- | --- | --- |
| I-74 | MIUI 禁止 instrumentation 启动 Activity | 真机跑不了 UI 测试；已用 AVD 覆盖 |
| I-84（新） | 草稿身份在 `agent_session` 缺失时只能靠占用指纹 | 同一 pane 换 agent 且 title/cwd 完全相同 → 可能复用旧草稿；发送前目标校验会 NeedsConfirm，但仍建议未来加 `pane.creation` epoch |
| I-85（新） | MarkdownAstCache 无文本等值校验 | 碰撞概率 ~2⁻⁶⁴，接受 |
| I-86（新） | 真实 Pi 会话端到端未验收 | 发送侧变更未经真实远端写操作确认（仅 UI/单测） |
| I-87（新） | 草稿未做跨进程恢复实测 | 有 Room 实现，缺少验收证据 |

---

## 6. 下一阶段（M2-C3）建议

1. **取得授权后**做真实 Pi 会话 E2E（含 APPEND/PATCH/FULL 计数、滚动位置、新消息提示、分支切换）；
2. 草稿跨进程/横竖屏/删除主机的人工验收；
3. 中文输入法（组合输入、光标、选区）与 TalkBack、最大字号的人工走查；
4. 若真实会话暴露增量命中问题，再回到 `IncrementalConversationMapper` 调优；
5. 仍未做：跨主机聚合、结构化审批、Markdown 表格/图片。
