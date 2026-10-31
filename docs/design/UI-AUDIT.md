# UI-AUDIT — 现状审计（M0 前置）

> 日期：2026-10-31｜设备：Redmi 22021211RC / Android 14 / 1080×2400 / 120 Hz
> 依据：`DESIGN-REVIEW-BRIEF.md`、`HERDR-ANDROID-UX-DESIGN-REVIEW-2026-10-31.md`（下称"设计报告"）、`RESULTS.md`、`DECISIONS.md`、`ISSUES.md`、现有源码与真机截图。

## 1. 现有结构与页面

| 页面 | 文件 | 能力 | 问题 |
| --- | --- | --- | --- |
| 看板 | `feature/board/BoardScreen.kt` | 主机 chips、连接条、按状态分组的卡片 | 卡片信息等权（agent/标题/cwd 同字号）；无紧急度分区；chips 横向滚动（主机多会挤爆）；无底部导航 |
| Pane 详情 | `feature/board/PaneDetailScreen.kt` | key-value 字段 + 最近输出 + 输入栏 | **调试式页面**，作为"进入对话"的中间页，与设计报告 W2"不得有中间页"冲突 |
| 对话 | `feature/board/TranscriptScreen.kt` | pi JSONL 结构化消息（纯文本）+ 输入栏 | 无角色视觉区分（仅彩色标签前缀）、无折叠、无 Markdown、无时间分组 |
| 主机管理 | `feature/board/HostsScreen.kt` | 列表 + 后台监控开关 + 连接/断开/私钥/编辑/删除 | 布局用 padding 手工拼、控件密度高、文案偏工程化 |
| 指纹确认 | `AppRoot.kt`（AlertDialog） | 显示 host:port + SHA256 | 可用；样式与设计系统未统一 |
| 审批卡片 | `BoardScreen.kt`（内联） | 请求问题 + request id + 允许/拒绝 | 只验证客户端；**触发源未定**，属于"测试可用"状态 |

导航：单 `Activity` + `rememberSaveable` 的字符串枚举（board/hosts/detail/transcript），无底部导航、无 Sheet、无转场。

## 2. UI State 与 ViewModel

| 现状 | 影响 |
| --- | --- |
| `HostsViewModel.UiState` 同时承载"主机列表 + 当前主机看板 + 选中 pane + 审批" | 关注点混杂；`hostKeyPrompt` 由 manager 透传 |
| `BoardView.stale` 一个布尔 | 无法区分"传输断了"、"数据旧了"、"正在同步"、"后台监控受限"（设计报告 D8 要求三者分开） |
| `TranscriptState.messages` 全量替换 | 流式更新会整表重组（M2 需按 item 缓存解析结果） |
| `paneOutput` 2 s 轮询只读终端尾部 | 作为"无 transcript 时的兜底"，与设计报告"对话为主体"一致，但当前放在详情页 |

## 3. 设计 Tokens / 组件复用

- **无 tokens 层**：`Theme.kt` 只覆盖 primary/secondary；`StatusColors` 5 个硬编码色；字号/间距/圆角全部散落在各屏幕。
- **组件复用差**：状态胶囊、卡片、字段行、输入栏在两个屏幕各写一遍。
- 颜色对比度未按设计报告校验（报告给出 quiet command 调色板与对比度，已抄入本轮 tokens）。
- **无 launcher icon**（系统默认机器人）；无 splash；无 monochrome icon。

## 4. 重复重连：真实代码路径（P0）

**证据**：logcat `HerdrConn` 中 **13 次 `manual reconnect`**；每次冷启动都是 `generation=1` 紧接 `generation=2`。

**路径**：

```text
HerdrApplication.onCreate()
  └─ container.manager.startAll()          → reload(startConnections = true)
       └─ ensureStarted(rt)                 → supervisor.start()

MonitorService.onStartCommand()
  └─ container.manager.startAll()          → reload(...)  // runtime 已存在（cfg 相同）
       └─ ensureStarted(rt)                 // supervisor != null
            └─ supervisor.reconnectNow()    // ← 这里无条件重连！
```

另外 `Application.onCreate` 在 `monitoringEnabled` 时会 `MonitorService.start()` → 又一次 `startAll()`。**同一进程启动可能触发 2–3 次重连。**

修复原则（设计报告 P0-1）：
- `startAll()` 幂等：已存在 supervisor 的主机**不做任何事**；
- 只有「用户点重连」才 `reconnectNow()`；
- 前后台切换不隐式重连（连接是进程级的，Service 只增加"保活"）。

## 5. 长对话掉帧的来源（M0.3 待测量）

已知事实：500 条消息滚动 jank **30–44%**（`m3-jank-*`），但测量方法（`input swipe` + `gfxinfo`）不可控（I-63）。

代码侧可疑点（待 Release/Profileable 基线确认）：
1. `itemsIndexed` 的 key 含 `text.length`（滚动时无变化，但每次重组重算字符串）；
2. 每条消息 `buildAnnotatedString` 在组合期重建（无缓存）；
3. 无 `contentType` 分化（thinking/tool/text 尺寸差异大）；
4. 长文本一次全量 layout，无 `maxLines` 预览与展开；
5. 输入栏与消息列表同屏，IME 变化触发整页重组。

本轮只做**基线测量**，不改渲染（M2 再改）。

## 6. 深浅色 / 动态字体 / TalkBack / Insets / 返回

| 项 | 现状 | 结论 |
| --- | --- | --- |
| 深色 | `HerdrTheme` 有 dark scheme，但只有 2 个主色；卡片用 `surface` 默认 | 需统一 tokens（本轮） |
| 动态字体 | 全部用 `MaterialTheme.typography`（sp） | 基本可用；技术字段用 `labelSmall` 偏小 |
| TalkBack | 卡片有 `semantics { contentDescription }`；状态点有描述 | 尚可；需补"状态 + 主机 + 项目"合并朗读、按钮 contentDescription |
| 键盘 Insets | 详情页/对话页输入栏已加 `imePadding + navigationBarsPadding` | 可用（I-65 已修） |
| 返回 | `BackHandler` 处理 board/hosts/detail/transcript | 需按新 IA 调整（conversation → board；sheet → conversation） |
| 预测性返回 | 未处理 | 本轮不拦截 root 返回 |

## 7. 依赖真实状态 vs 调试信息

| 组件 | 依赖 | 处理 |
| --- | --- | --- |
| 看板/对话/状态色 | herdr snapshot（5 状态 + agent_session） | 保留 |
| `pane_id / terminal_id / revision / workspace / tab` | 调试/诊断 | **移入技术信息 Bottom Sheet** |
| `agent_session` 路径 | 诊断 + 对话入口判断 | Sheet + 会话标题副文案 |
| `recent output`（pane.read） | 真实终端尾部 | 无 transcript 时作为会话主体兜底 |
| 审批卡片 | 沙箱文件 IPC | 保持"测试可用"，不宣称为正式功能 |

## 8. 模块划分判断

现状 `:app / :core:ssh / :core:herdr / :core:data / :feature:board` 足以支撑本轮重构：

- 不新建 Gradle 模块（编译时间与风险不值得）；
- 设计 tokens 放 `feature/board/design/`，等 M3 稳定后再抽 `:core:designsystem`；
- 页面拆分：`BoardScreen`（W1）／`ConversationScreen`（W2）／`TechnicalInfoSheet`（W3）／`HostsScreen`（W4，沿用）。

## 9. 本轮拟修改文件

| 文件 | 改动 |
| --- | --- |
| `core/data/HostConnectionManager.kt` | P0：`startAll` 幂等；区分 transport / data 状态；暴露 `monitoring`；`connect()` 才重连 |
| `feature/board/design/Tokens.kt` | 新增：颜色/字阶/间距/圆角/动效（Quiet Command） |
| `feature/board/BoardScreen.kt` | 重写为 W1：紧急度分区、blocked 大卡 + 其余紧凑行、主机筛选、连接/数据状态合并展示 |
| `feature/board/ConversationScreen.kt` | 新增 W2：角色区分、thinking/tool 折叠、无 transcript 时降级为终端尾部、Composer 1–5 行 |
| `feature/board/TechnicalInfoSheet.kt` | 新增 W3：技术字段 + 复制，替代独立 Pane 详情页 |
| `feature/board/PaneDetailScreen.kt` | 删除（能力并入 W2 + W3） |
| `feature/board/AppRoot.kt` | 新导航：Board ↔ Conversation（+ Sheet），Hosts 作为一级目的地 |
| `feature/board/HostsScreen.kt` | 套用 tokens（细节调整，不重写功能） |
| `app/MonitorService.kt` | 不再触发重连（只 `startAll` 幂等 + 通知） |

## 10. 优先级

| P | 项 | 本轮 |
| --- | --- | --- |
| P0 | 重复重连（13 次日志证据） | ✅ 修 |
| P0 | 传输/数据/监控状态分开建模 | ✅ 修 |
| P0 | 长对话性能基线（Release/Profileable 可复现测量） | ✅ 建立（Macrobenchmark 模块留 M2） |
| P1 | W1 看板重排（紧急度优先 + 紧凑密度） | ✅ 做 |
| P1 | 直达对话（删除中间详情页）+ 技术 Sheet | ✅ 做 |
| P2 | Composer 自适应 + 草稿 | ✅ 基础版 |
| P2 | Markdown / Diff / 图标 / 动效 | ⛔ 留 M2/M4 |
