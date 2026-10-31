# Herdr Android · UX-01a / Pi 任务包（草案，不是编码授权）

Task ID：`UX-01a`  
设计基线：UX-D0 Graphite × Editorial；`HERDR-UX-D0-VISUAL-REVIEW-v0.1.md`  
状态：**Draft / 等用户批准后执行**  
唯一目标：**修复 Conversation 顶栏在较大字体下文本裁剪/按钮重叠，并保证返回、标题、状态与菜单可使用。**

> 一次 PR 只做一个问题。不要趁机重做 Board、Composer、Markdown、导航或 SSH。最终是否合并以真实截图验收为准。

## A. 进入任务前检查（必须执行，不得猜路径）

1. `git status --short`；如果有未提交改动，停下报告，不得擅自覆盖。
2. `git rev-parse --short HEAD`；记录当前基线。查阅最近的 `RESULTS.md`、`DECISIONS.md`、`ISSUES.md`、`NEXT-STEPS.md` 和原有 `I-93` 描述。
3. `rg -n 'TopAppBar|CenterAlignedTopAppBar|LargeTopAppBar|ConversationScreen|fontScale|height\(' --glob '*.kt' .`，找出**真实文件路径、Composable 名、调用关系**。如果不是已知结构，更新任务实施计划，不得硬套外部示例。
4. 阅读 `HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md` §3.2、§4、§13–14 与 `HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md` §2.2、§4.2、§9–10。
5. 运行并保存 baseline 测试结果，使用仓库现有的 Gradle 命令，先 `./gradlew tasks` / `./gradlew projects` 确认模块名；不要将 Windows/PowerShell、Linux/macOS 命令硬套到当前系统。

**审计输出**：在任务开始前先写 10–20 行简明计划，包含将修改的真实文件、顶栏如何得到状态、为何裁剪、拟做方案、已有相关测试。除正常本地构建外无外部写入。

## B. 问题边界

既有事实：在系统 `fontScale≈1.3` 下，对话页固定高度 + 双行标题曾裁剪并压住消息列表首项（I-93）。需要修复的是真实布局；不修改标题来源或 Agent 状态语义。

本次允许改动：

- Conversation 顶栏及其专有的小型布局组件；
- 顶栏使用的共享 Layout/Type Token（如**确有必要**，但必须保持兼容）；
- 对应 Compose UI Fixture 和测试；
- 本任务验收截图与报告。

禁止改动：

- `core:ssh`、`core:herdr`、发送账本、草稿 Room、真实 Pi transcript 解析；
- Conversation 的消息映射/Markdown/Tool 绑定；
- Board 信息架构、主机筛选器、Composer 布局；
- 已确认的复制+更多、系统文字选择和语音自适应规则；
- 远端 Herdr/测试 Session，以及全局 Pi/Claude 配置。

## C. 组件布局契约

目标：

1. Compact 常规字体：左返回（点击目标≥48dp）+ 中间标题/状态 + 右侧必要动作（每个≥48dp）；标题与状态不是两个固定高度强行堆叠的 Text。
2. 393dp `fontScale=1.0`：标题清晰，一眼可读，元信息低强调，右侧操作可见。
3. 320dp / `fontScale=1.3` 或 `1.6`：允许 TopBar **增高、折行或标题安全截断**。严禁标题遮挡返回/更多，严禁盖住消息第一项。
4. 标题安全截断时，必须有可访问完整标题的入口，例如通过技术信息/内容描述或明确详情入口；不能把任务身份悄悄丢失。
5. Agent 状态要有文字（working/done/idle/blocked/unknown 均正确映射），不依赖单独色点。
6. 使用 `WindowInsets`，不得硬编码系统状态栏、Navigation Bar 高度，也不得额外叠加第二份 Insets。
7. 右侧操作超过可用宽度时，优先将非主要动作收进已有「更多」；返回操作必须始终保留。
8. 若标题与控件占空间导致消息区更小，允许正文滚动；禁止牺牲控件点击面积或把标题压到消息上。
9. 当前主题和字体 Token 优先使用现有值；不得新增不必要色值/阴影。
10. 所有动作具有可理解的 TalkBack label，阅读顺序为返回→标题/状态→操作；不重复朗读纯装饰图标。

## D. 必须复现/自动化的样例

采用测试 Fixture，不连接真实 SSH：

| Case | 宽度 | 字体 | 语言 | 必须覆盖 |
| --- | --- | --- | --- | --- |
| A01 | 393dp | 1.0 | zh | 基础布局与右侧动作 |
| A02 | 393dp | 1.0 | en | 英文布局与元信息 |
| A03 | 320dp | 1.3 | zh | 标题换行、点击区、消息首项 |
| A04 | 320dp | 1.6 | en | 长英文标题、安全换行或截断 |
| A05 | 393dp | 1.6 | zh | 状态标签、返回与右侧操作 |
| A06 | 320dp | 1.6 | en | 超长 Host 名、超长任务名、Offline/Stale |

每例在 Light/Dark 中抽查；重点病例 A03/A04/A06 两主题均截图。保留现有 Conversation UI Fixture 与测试的正常通过结果。

自动化断言建议：

- 顶栏核心 Semantics Node 存在且有有效标签；
- 返回/菜单点击可达，没有重叠或不可点；
- 对话列表第一条可以滚动看到，顶栏不遮挡；
- 屏幕旋转/字体变化后仍可导航返回；
- 没有将实际 Agent 状态替换为常量假状态。

静态截图只用于人工审美评价，不能替代 Android Compose 测试和真机截图。

## E. 实施纪律

1. **先**用最小 Fixture 复现旧布局在高字体下的裁剪，记录 before screenshot 和测试结论；如果原问题已经被其他提交修复，停下并报告，不要制造不必要改动。
2. 选择最小且稳定的 Compose 解决方案，例如自适应 Column/Row 和 `heightIn` / 由内容决定高度；不要为了修顶部栏引入新的架构库。
3. 使用一致的 Type Token，状态中英文临时 Fixture 可以复用已存在的 strings；不得在 UI 到处硬编码新的可见文案。
4. 每修改一个布局段落进行 Preview/编译/测试；不能一次把 Board/Conversation/Composer 都替换。
5. 对 320dp+1.6 的长任务名保留完整查看途径；同时验证 393dp 正常态没有被过度拉高。
6. 最后截图进行像素层级检查：对齐、间距、按钮基线、文字截断、首项遮挡、系统栏、轻/深主题。

## F. 硬性验收门槛（全部满足）

- [ ] I-93 场景在 Android 设备/AVD 上可重复测试，并已修复，保留 before/after 截图。
- [ ] A01–A06 全部通过功能/布局检查；未知情况明确标记未验证。
- [ ] 320dp + 1.6 下返回/更多按钮点击区≥48dp，无视觉重叠、遮挡。
- [ ] 393dp 正常态顶栏没有无谓变高和大面积留白。
- [ ] 原有 Compose UI 测试与相关 JVM 测试继续通过。
- [ ] MIUI 仍不能启动 instrumentation 时，使用已验证新 AVD 做自动化、在真机完成手工截图；不再次耗费大量时间绕过 MIUI。
- [ ] 不改网络、写操作账本、草稿、Tool/Markdown 行为。
- [ ] 产出 `docs/design/UX-01a-REPORT.md`，用「已实现并验证 / 已实现但未验证 / 未实现 / 已知问题」四类报告。
- [ ] 独立提交，变更范围小且可回滚；如 CI 无法运行，不宣称 CI 全绿。

## G. 停止条件（触发就暂停并报告）

- 实际工程结构和假定不符，必须改核心 Navigation 或消息数据模型才能修复。
- 改动会触及 SSH、远端写入、数据迁移或全局配置。
- 需要删除或覆盖用户文件、草稿、数据库。
- Android 系统级测试持续阻塞且无可用替代环境。
- 改动范围超过 Conversation 顶栏 + 相关 UI 测试，而没有清楚技术理由。

## H. 最终报告格式

提供：Commit SHA、改动文件列表、旧问题根因、最小修复解释、测试命令和结果、截图路径、适配矩阵、不足项、回滚方式。**未取得用户的“开始 UX-01a”明确许可前，本任务仅作预审草案。**
