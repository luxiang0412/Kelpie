# Claude 适配 — 真机端到端验证（方案 C 首次跑通）

日期：2026-11-01 · 设备：Redmi 22021211RC / MIUI / A14 · 分支 `ux/graphite-editorial`

## 1. 现场事实（远端只读取证）

```
pane w9:p1 | agent claude | status idle | cwd /home/luxiang/project/private/herdr-gui-research
agent_session = {"agent":"claude","kind":"id","source":"herdr:claude","value":"15367e80-d73d-45f0-9d85-f699bf7fbab8"}
```

```
~/.claude/projects/-home-luxiang-project-private-herdr-gui-research/15367e80-….jsonl   366,647 B
首行：{"type":"mode","mode":"normal","sessionId":"15367e80-d73d-45f0-9d85-f699bf7fbab8"}
```

**结论：herdr 的 `agent_session.value` 就是 Claude 的 `sessionId`（同为 UUID，且等于文件名）。** 方案 C 的三重校验成立。

## 2. 真机上暴露的 3 个真实缺陷（全部已修）

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| C-1 | 一直显示「未使用结构化对话：herdr 未提供会话 id」 | `Pane.agentSessionId` 在 **Room 缓存往返**时被丢掉（`PaneEntity` 没有这一列），界面拿到 null | `PaneEntity` 增列 + 显式 `MIGRATION_3_4`（只加列，不清表）+ 双向映射 |
| C-2 | 定位成功后**仍然**显示终端尾部 | `hasTranscript` 只看 `pane.agentSessionPath`（Claude 的路径是运行时定位的，pane 上永远为 null） | `TranscriptState.structured`，定位成功置 true；界面以它为准 |
| C-3 | 首条 user 消息（`hello`）消失 | 首帧回填窗口 256 KB 从文件中间起读，把 offset 671 的 user 行切掉 | 回填上限 1 MB；从中间起读时**跳到下一行行首**；超出上限时列表顶部如实提示「只加载了最近的消息」 |

另修：`homeDir` 为空时拼出根路径 `/.claude/…` → 改用 `sftp.canonicalize(".")` 取登录 home。

诊断手法：定位失败时把失败码透传到列表顶部（`claude_lookup_failed: <原因>`），并用 `Log.i("HerdrConv", …)` 打出 `agent/path/sid/cwd` 与定位结果 —— 不静默降级。

## 3. 验证结果

| 项 | 结果 |
| --- | --- |
| 结构化对话渲染（Claude） | ✅ 真机截图 `docs/evidence/claude-conv7.png`：user 气泡 + `↔ Claude` 标识 + Markdown 标题/列表/行内码 |
| 定位链路 | ✅ logcat `claude lookup -> Found(path=…15367e80….jsonl, reason=cwd→-home-luxiang-…，取最新且 sessionId 匹配)` |
| 单元测试 | ✅ `:core:herdr:test`、`:core:data:testDebugUnitTest`、`:feature:board:testDebugUnitTest`（含新增 `TranscriptTailerStartTest` 3 项） |
| Compose UI（AVD `herdr_m2c1`） | ✅ **OK (28 tests)** |
| 工具调用卡（Claude 格式） | ⚠️ 未验证 —— 该会话无 tool_use/tool_result |

## 4. 仍未验证 / 已知限制

- 该 Claude 会话没有工具调用，`ClaudeTranscriptParser` 的 tool_use↔tool_result 配对**只有单测覆盖**，无真机样本。
- 已存在的会话缓存保留旧游标（256 KB 时代的 offset），所以老用户升级后**仍会缺最早几条**，直到该会话缓存被重置；新会话首次打开即 1 MB 全量（单测覆盖边界）。
- `isSidechain`（子代理）跳过逻辑仅在单测验证。

## 5. 第二轮：工具调用（真机）

用户在该 Claude 会话里跑了 10 次 `Bash`（含 5 组 tool_use/tool_result）。真机暴露并修复两个新缺陷：

| # | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| C-4 | 工具卡全部显示「尚未收到结果」+ 一堆「未关联的工具结果」 | `transcript_messages` 表只存 `kind/text/toolName/timestamp`，**没存 `toolCallId`**；Room 往返后配对信息归零 | 表加 `msgId/parentId/toolCallId` 三列 + `MIGRATION_4_5`（并清空派生缓存重解析） |
| C-5 | 整个工具调用段落**整段消失**（列表从后半段开始） | `ConversationBranch` 从最后一条沿 `parentId` 回溯，Claude 的 `parentUuid` 指向被跳过的非消息事件（`api-request-blob`/`attachment`/`mode`），链在中间断掉 → 断点之前全部丢弃 | Claude 解析器不再输出 `parentId`；`ConversationBranch` 在「最后一条没有 parentId」时如实退化为线性渲染 |
| C-6 | 同一次 Bash 出现两张卡 | Claude 会把「增长中的」assistant 消息重写一遍（uuid 不同、`tool_use.id` 相同） | `ConversationResolve` 按 `toolCallId` 去重并取最后一次（内容最全） |

验证（真机 Redmi，19:49）：
- 折叠态：4 张 Bash 卡全部「结果已收到」，无重复、无「未关联」→ `docs/evidence/claude-tools15.png`
- 展开态：`参数` = 真实 `{"command":"git log --stat …"}`，`输出` = 真实 git log → `docs/evidence/claude-tool-open2.png`
- 单测：`:core:herdr`、`:core:data`、`:feature:board` 全绿（新增 `ClaudeLinearRenderTest`、`TranscriptTailerStartTest`、配对去重用例）
- Compose UI：AVD **OK (28 tests)**

## 6. 仍未解决（如实登记）

- ~~**Markdown 表格不渲染**~~ → 已实现（见 §7）。
- 反复 markdown 长消息滚动时，顶部内容会被顶栏遮住（截图可见），属视觉细节，待与 DESIGN-SYSTEM 行高复核。

## 7. GFM 表格（I-99 已修）

`MarkdownParser` 新增 `MdBlock.Table(header, rows)`：
- 识别条件：当前行含 `|`，且下一行匹配 `|---|---|` 分隔行（允许 `:---:` 对齐标记、允许省略首尾竖线）；
- 单元格逐个走既有 `parseInline`（行内码/加粗/链接照常生效）；
- 列数不齐按表头裁剪/补空，绝不抛异常；只有竖线没有分隔行 → 仍是普通段落；
- `toPlainText` 投影为 `a | b` 行文本（无障碍朗读不丢内容）；
- `MarkdownAstCache.PARSER_VERSION` 2 → 3（语法子集变更，旧 AST 整体失效）。

渲染（`TableBlock`）：`surfaceRaised` 圆角容器 + 表头 SemiBold + 表头下 1dp 分隔线 + 行间 1dp 分隔线；按列 `weight` 等分，不做横向滚动（手机宽度下换行更可读）。

验证：
- 单测 `MarkdownTableTest` 6 项（基本两列、对齐标记/无首尾竖线、单元格行内语法、列数不齐、非表格、纯文本投影）全绿；
- 真机浅色 `docs/evidence/table1.png`、深色 `docs/evidence/table-dark.png`：表头/分隔线/换行均正确；
- 回归：`core:herdr` + `core:data` + `feature:board` 单测全绿，Compose UI **28/28**（AVD）。
