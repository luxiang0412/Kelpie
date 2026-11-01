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
| 结构化对话渲染（Claude） | ✅ 真机截图 `spike/results/claude-conv7.png`：user 气泡 + `↔ Claude` 标识 + Markdown 标题/列表/行内码 |
| 定位链路 | ✅ logcat `claude lookup -> Found(path=…15367e80….jsonl, reason=cwd→-home-luxiang-…，取最新且 sessionId 匹配)` |
| 单元测试 | ✅ `:core:herdr:test`、`:core:data:testDebugUnitTest`、`:feature:board:testDebugUnitTest`（含新增 `TranscriptTailerStartTest` 3 项） |
| Compose UI（AVD `herdr_m2c1`） | ✅ **OK (28 tests)** |
| 工具调用卡（Claude 格式） | ⚠️ 未验证 —— 该会话无 tool_use/tool_result |

## 4. 仍未验证 / 已知限制

- 该 Claude 会话没有工具调用，`ClaudeTranscriptParser` 的 tool_use↔tool_result 配对**只有单测覆盖**，无真机样本。
- 已存在的会话缓存保留旧游标（256 KB 时代的 offset），所以老用户升级后**仍会缺最早几条**，直到该会话缓存被重置；新会话首次打开即 1 MB 全量（单测覆盖边界）。
- `isSidechain`（子代理）跳过逻辑仅在单测验证。
