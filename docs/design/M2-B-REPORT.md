# M2-B 报告 — 对话阅读体验与测试基础设施

> 基线提交：`3487539`（M2-A）｜设备：Redmi 22021211RC / Android 14 / 120 Hz
> 相关文档：[MARKDOWN-RENDERING-DECISION.md](MARKDOWN-RENDERING-DECISION.md)、[BENCHMARK-INFRA-REPORT.md](BENCHMARK-INFRA-REPORT.md)

## 0. 摘要

| 目标 | 状态 |
| --- | --- |
| A1 benchmark 构建（非 debuggable / R8 / profileinstaller / fixture 隔离） | ✅ **已实现并验证** |
| A2 instrumentation 阻断诊断 | ✅ **已定位**（MIUI BAL，与 profileinstaller/shader 无关）；未解除 |
| A3 模拟器恢复 | ❌ 未做（package 服务损坏，需新 AVD） |
| A4 FrameTimingMetric / Perfetto | ❌ 未获得（被 A2 阻断） |
| B1 Markdown 选型 | ✅ 决策记录已写（自研轻量 Parser + 自定义渲染） |
| B2 渲染模型 + 后台解析/缓存 | ✅ 已实现（`ConversationItem` + `produceState`）；增量更新未验证 |
| B3 Markdown 子集 + 代码块 | ✅ 已实现并截图验证 |
| B4 Thinking/Tool 配对 | ✅ 按稳定 id 配对 + 降级标注；单元测试覆盖 |
| B5 阅读体验 | ✅ 新消息提示、不强制回底、48dp、TalkBack 语义；未做真实长会话人工验收 |
| 单元测试 | ✅ **19 个新增**（Markdown 12 + 配对 7），全绿 |

---

## 1. 已实现并验证

### 1.1 benchmark 构建类型

见 [BENCHMARK-INFRA-REPORT.md](BENCHMARK-INFRA-REPORT.md) A1。
设备实测 `run-as: package not debuggable`，说明 R8/非调试变体真正生效。
Fixture 首帧：**375 / 344 / 298 ms**（100/500/2000，benchmark 变体）。

### 1.2 Markdown 渲染（子集）

- 标题、加粗、斜体、无序/有序列表、引用、行内代码、`http(s)` 链接、围栏代码块；
- 代码块：语言标识、复制按钮、横向滚动、等宽字体、默认 12 行 + 「展开全部 N 行」；
- 不安全 URL（`javascript:` / `file:` / `intent:`）：删除线 + 「（链接已停用）」，**不可点击**；
- 未知语法（表格、图片）按纯文本渲染，不崩溃；
- 截图：`docs/evidence/m2b-markdown-light.png`、`m2b-markdown-dark.png`（深色见图：标题/列表/引用/行内码/链接/代码块可读）。

### 1.3 渲染模型与配对

- `ConversationItem`（User/Assistant/Thinking/Tool/Unknown），与传输层 `ChatMessage` 解耦；
- `ChatMessage.toolCallId` 新增，`PiTranscriptParser` 从 `id` / `toolCallId` / `tool_use_id` 提取；
- 配对：id 命中 → `pairedById = true`；id 对不上 → 退化为最近未配对 call，并标 **「按顺序配对」**，不谎称精确；找不到 → 独立渲染；
- 结果错误启发式 → 「失败」+ 红色，仅视觉提示，不改真实状态；
- 长输出：展开区固定最大高度 260dp 内部滚动，按需渲染。

### 1.4 单元测试（19 个新增，全部通过）

| 文件 | 用例数 | 覆盖 |
| --- | --- | --- |
| `MarkdownParserTest` | 12 | 各类块/行内、未闭合围栏、安全/不安全链接、CJK 混排、纯文本投影、400 段性能上限 |
| `ConversationItemMapperTest` | 7 | 非相邻 id 配对、id 不匹配降级、错误识别、多结果不合并、孤儿结果、顺序保持、2000 组线性耗时 |

### 1.5 阅读体验（B5）

- 首次进入定位最新；之后**仅在底部附近跟随**，否则显示「↓ 新消息」而不把用户拉回底部；
- Thinking 默认折叠；tool 行 48dp 触达 + 语义「工具 X，状态，已折叠/已展开，点击切换」；
- 用户消息右对齐、assistant 全宽、消息间距 `Space.m`（比 M2-A 更松，提升可读性）。

---

## 2. 已实现但未验证

| 项 | 缺什么 |
| --- | --- |
| 后台解析缓存 | 只在 Fixture 上跑过；未在真实 pi 会话上验证缓存命中与失效 |
| 流式增量渲染 | 未实现批处理/防抖（当前按 `remember(messages)` 整体映射）；真实流式会话未测 |
| 「新消息」提示 | 需要真实增量到达才能触发；Fixture 是静态列表 |
| 分支切换 | Fixture 无分支数据；`agent_session` 分支路径未覆盖 |
| IME 弹出行为 | 未在真机验证 Composer 与列表的联动 |
| ToolCall 错误态 | Fixture 里有 `Error:` 文本，但真实 pi 工具失败样本未验证 |

---

## 3. 未实现（本轮范围外）

- FrameTimingMetric / StartupTimingMetric / Perfetto（A4，被 BAL 阻断）；
- 嵌套列表、表格、图片、HTML（决策记录里明确不支持）；
- Markdown 表格与任务列表；
- 长会话的增量解析与 Diff（M2-C）。

---

## 4. 已知问题

| # | 问题 | 状态 |
| --- | --- | --- |
| I-74 | MIUI 禁止 instrumentation 启动 Activity | **未解除**，本轮进一步确认与 Manifest/permission 无关 |
| I-75 | 模拟器 package 服务损坏 | 未处理（需新 AVD） |
| I-76 | `CompilationMode.Partial()` 需 profileinstaller | ✅ 已修（1.4.1） |
| I-77 | 内容变化后 gfxinfo 不可比 | 仍然有效 |
| I-78（新） | `dropShaderCache` 在 MIUI 失败 | 阻塞 FrameTimingMetric |
| I-79（新） | 流式增量未批处理 | 每条增量都会重跑 `ConversationItemMapper.map`（O(n)），长会话下有卡顿风险 |
| I-80（新） | fixture 数据集与 M2-A 不兼容（`i%8` → `i%10`） | M2-A 的 debug 首帧数据不可与本轮数据直接比较（已注明） |

---

## 5. 下一阶段（M2-C）建议

1. **先解决增量**（I-79）：只对最后一条消息做重解析，历史条目缓存复用；映射从 O(n) 改为增量 append。
2. **流式验收**：起一个真实 pi 会话录屏，验证「新消息」提示、缓存失效、折叠状态不跳变。
3. **测试基础设施**：创建隔离 AVD（package 服务损坏），跑 Compose UI 测试；`dropShaderCache` 用非 MIUI 设备或 CI。
4. **Markdown 扩展**：表格/嵌套列表需要重新评估是否引入库（Parser 层可替换）。
5. **性能对比**：固定数据集 + 固定交互轨迹，用 FrameTimingMetric 比较 M2-B 前后（当前不具备条件）。
