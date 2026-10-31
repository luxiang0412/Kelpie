# UX-D0 续做计划（RESUME-PLAN）

> 用途：本次会话预算耗尽，未完成全部任务。此文件保证下一次会话**不需要重读 535 行任务书**即可从断点继续。
> 当前分支：`ux/graphite-editorial`｜最后 commit：`6b10196`｜权威状态：`PROGRESS.md`

## 0. 断点位置

- 已完成：`UX-00`(PASS)、`UX-01a`(PASS)、`UX-01b`(PASS)、`UX-00a`(**PARTIAL**)
- 断点：**`UX-00a` 剩余项** → 然后 `UX-02a` 起按序推进

## 1. UX-00a 剩余项（先做这个，否则后续截图差异不可归因）

| 剩余项 | 落地位置 | 验收 |
| --- | --- | --- |
| 语义字阶命名（`displayPage/titlePage/titleTask/titleSection/bodyReading/bodyInput/bodySecondary/meta/labelAction/code`） | 在 `design/Tokens.kt` 增加 `object AppTextStyles`，值取 §2.2 表；页面逐步替换 `MaterialTheme.typography.*` | 编译 + 视觉零差异（截图对比）+ 至少 Conversation/Board 引用新命名 |
| `TouchTargetIconButton`（48dp 触达 + Vector 图标） | `design/Components.kt`（新建） | 替换 `›`（BoardScreen:CompactAgentRow）、`▸/▾`（ConversationScreen 折叠行）、`⤢`（Composer 长编辑） |
| `PageGutter`（20dp / <360dp 16dp；Composer 16dp/12dp） | 同上 | 页面引用；320dp 截图不溢出 |
| 图标资源 | `app/src/main/res/drawable/*.xml`（Vector，自绘 5 个：chevron、collapse、expand、fullscreen、copy） | 体积记录在报告；不引入 material-icons-extended（体积） |

**注意**：这一项改完必须重拍 P1/P2/P5（393 light、393 dark、320 dark fontScale 1.6），否则后续所有视觉任务失去基线。

## 2. 剩余任务一览（顺序不可乱）

| Task | 标题 | 唯一目标 | 验收 |
| --- | --- | --- | --- |
| UX-00 | 仓库审计与基线保护（不可跳过） | 给之后所有任务建立准确的代码坐标与回归证据。 | — |
| UX-01a | Conversation 顶栏 320dp / 大字体裁剪修复 | 修复 I-93，且不影响消息滚动首项和操作按钮。 | 原裁剪不能复现，正常 393dp 没有无意义大留白，原对话 UI 测试不回归。**提交**：`ux(UX-01a): ...`。 |
| UX-01b | InlineCode 的原始文本与复制保真修复 | 移除将代码字符串包围空格当 padding 的实现。 | 视觉不会无故产生半个字符空白，复制数据没有增删改，长按选择仍可用。 |
| UX-00a | 统一基础 Tokens / Theme / 图标契约 | 在不改变业务/网络行为的前提下，将 Graphite × Editorial 的基础视觉形成一个唯一实现。 | 构建、两主题基准、文本/状态无明显对比度倒退，UI 没引入业务代码变化。 |
| UX-02a | 工作台顶栏、主机选择器与底部导航 | 采用暂定 `BOARD-HOST-001` 将主机选择器置于主标题下方。 | 不会因为标题与下拉并排被挤压；切换主机跳转目标正确；无新假入口。标注此方案为“Provisional UI Default”。 |
| UX-02b | 工作台 Attention First、列表与已完成折叠 | 重构 Board 信息密度和层级。 | Blocked 能 3 秒内看出；Done 默认收起；`done != idle`；空态不显示虚假统计；P1–P6 样例视觉通过。 |
| UX-02c | 工作台异常、缓存、Unknown 入口与系统提示 | 建立准确、节制的异常状态 UI。 | 高优先级操作可见，安全状态真实，未删除原先主机密钥确认逻辑。 |
| UX-03a | 对话正文视觉、身份头与用户气泡 | 实现阅读优先排版，不改消息协议和增量 Mapper。 | 文本不挤压代码，Agent/用户角色区别明显，滚动/重连/发送不回归。 |
| UX-03b | Markdown 正文与 Code Block 视觉 | 用已有轻量 Parser 做高质量排版，不做新语法引擎。 | 中英标点贴 InlineCode 无多余空格，正常/长代码在320dp不拖动整页，正文支持文字选择，长对话性能未出现明显证据性回退。 |
| UX-03c | Tool/Thinking/长输出 | Agent 工作记录可读、可信、按需展开。 | 工具名/状态真实，交互独立，展开不丢失阅读位置或源文本，长输出不会造成明显滚动锁死。 |
| UX-03d | 消息复制、系统选择、更多菜单 | 实现**已批准**的 `CONV-ACTIONS-001`。 | 用户复制和精确选择无需进入技术 Sheet；复制语义字节级保真；离线缓存下仍可用。 |
| UX-03e | 阅读位置、滚动与新消息入口 | 稳定阅读历史和新消息提示。 | 历史阅读不会被打断、返回锚点尽可能稳定；对于不可靠身份的数据不能恢复到别的 Agent。 |
| UX-04a | Composer 视觉布局：轻量双层 Surface | 按暂定 `CMP-STYLE-001` 设计外观，而不改发送、Draft、Room、WriteLedger 实现。 | Composer 比旧大盒子更轻、不会遮挡正文、与键盘融合；任何视觉改动都不破坏现有发送三态。 |
| UX-04b | Composer 长 Prompt、草稿与发送异常入口 | 统一已存在的可靠输入行为与状态视觉。 | 安全状态真实、草稿不串、键盘和编辑体验可靠，没有自动发送队列。 |
| UX-04c | 引用消息 → Composer，草稿冲突保护 | 实现真实有用的“引用到输入框”，不抢现有草稿。 | 用户可轻松引用过去的分析回复，且原有草稿与发送目标不会被覆盖或改变。 |
| UX-05a | i18n 资源梳理与中英文覆盖 | 移除 UI 硬编码文案，完成 zh-CN/en 第一版资源覆盖。 | 系统设中文时没有大量英文 UI 残留（技术名除外），英文模式无中文字句残留，User transcript 保持原文。 |
| UX-05b | App 内语言设置与动态切换 | 提供“跟随系统 / 简体中文 / English”且不丢草稿、会话、滚动与连接状态。 | 三种语言模式可用；状态语义正确，动态切换不重复发送或清空草稿。 |
| UX-06 | 会话内搜索与阅读导航（独立可降级） | 对已缓存、已解析的当前有效会话内容提供搜索和定位，不触碰远端工作流。 | 可以定位真实匹配消息，不误把旧分支结果当当前结果；不可检索范围明确标注。若源数据不足，保留审计报告后标 `PARTIAL`，不做假搜索按钮。 |
| UX-07a | 语音转文字可行性 Spike（不改主流程） | 确认当前 Android 真机/AVD 上可用且隐私清晰的 STT 方案；本 Task 不添加假可用入口。 | 产出 `UX-07a-STT-RESEARCH.md`，注明所依据的 Android API 与真实设备证据，绝不假称设备端离线识别已验证。 |
| UX-07b | 语音识别 → 文本复核 → 草稿（条件性实施） | — | 从点 Mic 到“转写→编辑→插入草稿→手动发送”的完整本地路径可用；没有任何代码路径从识别回调直接调用 `sendPrompt`。 |
| UX-08a | 主机管理/设置基础视觉一致性 | 使非三屏页面不再显得像 Debug 表单，不添加新 SSH 能力。 | 主机管理原功能不回归，取消/删除操作不会误删草稿；新增 UI 既不是空壳也不损失诊断能力。 |
| UX-08b | 统一空态、异常态、状态与 Motion 完成度 | 消除零散的视觉状态及无意义动画。 | 多种异常不会互相覆盖，交互反馈一致，不能因视觉精修破坏真实异常信息。 |
| UX-08c | App 品牌图标候选与启动页（不得自动视为最终审核） | 提供可对比的品牌设计候选，并让启动体验符合原生 Android。 | 原型/样例与最终资产严格分开，启动不多等待、不泄露敏感状态。 |
| UX-09 | 全局综合 QA、性能回归与交付收尾 | 将分任务实现拼成可用的一致产品，在不夸大证据的情况下形成最终报告。 | — |

## 3. 每个任务的固定闭环（勿简化）

1. `rg` 确认真实符号（不得按任务书建议名新建重复类）；
2. 记录改动计划到报告；
3. 跑针对性基线（JVM / AVD UI）；
4. 最小实现；
5. 自动测试（`:feature:board:testDebugUnitTest`、`:app:assembleDebugAndroidTest` + AVD `am instrument`）；
6. 真机/AVD 截图（注明 P1–P8 profile + 路径）；
7. `docs/design/ux-run/UX-XX-REPORT.md` 四类结论；
8. 更新 `PROGRESS.md`（只允许 TODO/IN_PROGRESS/PASS/PARTIAL/BLOCKED/SKIPPED）；
9. 单任务单 commit：`ux(UX-XX): ...`。

## 4. 已知阻断（会持续存在，不要重复排查）

| 阻断 | 影响 | 处理 |
| --- | --- | --- |
| MIUI 拒绝 instrumentation 启动 Activity（I-74） | 真机跑不了 Compose UI 测试 | 用 AVD `herdr_m2c1`（`emulator-5556`）；真机只做 `am start` + 截图 |
| MIUI `dropShaderCache` 失败（I-78） | 真机无 FrameTimingMetric | 性能类任务标 PARTIAL，不得用模拟器数字替代 |
| 无设计图标资产（BRAND-ICON-001） | App 图标只能出方案不能定稿 | 标 PARTIAL，禁止谎称最终图标 |
| 无结构化审批触发源（SEC-APPROVAL-001） | `blocked` 卡无法用真实数据验收 | 用 Fixture 截图，报告注明 |

## 5. 安全红线（每次改动前自查）

- 不改远端 Herdr/Pi、不改 SSH 信任、不做读屏一键审批、Unknown 不自动重发、Accepted ≠ 执行完成；
- 不改 `ConversationResolve`/`IncrementalConversationMapper`/`DraftStore`/`WriteLedger` 语义（除非有可复现缺陷 + 回归测试）；
- 不做破坏性 Room 迁移；不提交真实对话/密钥/主机指纹。
