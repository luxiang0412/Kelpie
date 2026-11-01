# Claude 适配 — 方案 C：按 cwd 定位会话文件（已实现，端到端待验证）

> 前置：`ADAPT-CLAUDE.md`（解析器 + 分发，方案 B，已提交 `5a53ae7`）

## 1. 为什么需要 C

herdr 对 Claude 上报 `agent_session = { kind: "id", value: "sess-…" }`，**不给文件路径**；
而结构化对话必须读 `~/.claude/projects/<slug>/<uuid>.jsonl`。没有路径 → 只能降级为终端尾部。

## 2. 实现（全部只读）

| 组件 | 作用 |
| --- | --- |
| `ClaudeSessionLocator`（core:herdr，纯逻辑） | `slugFor(cwd)`（`/`→`-`，实测对齐 `-home-luxiang-project-work-xn-ontology`）、`pickLatest(候选, now, window)`、`sessionIdOf(文件头)`、`sessionIdMatches(双向非空且相等，允许 `sess-` 前缀差异)` |
| `ClaudeSessionResolver`（core:data，IO） | 用 `sharedSftp()` 列 `~/.claude/projects/<slug>/`，筛 `*.jsonl`，取时间窗内最新，读头 8KB 对账 `sessionId`；任何一步失败返回 `Failure(code, message)` |
| `HerdrCodec` + `Pane.agentSessionId` | 记录 `kind != path` 时的会话 id |
| `HostConnectionManager.locateClaudeSessionTracked` | 供 UI 调用，并保留 `lastClaudeLookup` 供技术信息显示 |
| `HostsViewModel.openConversation` | Claude pane 无路径时先定位；`Found` → 打开该路径；`Failure` → 显示「未能定位 Claude 会话文件（原因），已降级为终端输出」 |

**三重校验（任一不满足即放弃，绝不错挂）**：
1. 目录名必须**完全等于** `slugFor(cwd)`；
2. 候选文件 `mtime` 必须落在最近 24h（`windowMs`，可调）；
3. 文件头 `sessionId` 必须与 herdr 上报的 id 相等（缺任一侧都判失败）。

## 3. 测试

- 新增 `ClaudeSessionLocatorTest`（5 项）：slug 规则、取最新、窗口外拒绝、sessionId 提取、双向非空匹配（含 `sess-` 前缀）
- 回归：`:core:herdr:test`、`:core:data:testDebugUnitTest` 通过；`:app:assembleDebugAndroidTest` + AVD → **OK (28 tests)**

## 4. 端到端未验证（如实）

- 当前 `test` session 里**没有 Claude pane**，所以 C 的真实链路（列目录 → mtime → sessionId 对账 → 渲染）**未在真机跑过**。
- 已知风险：若 herdr 的 `value` 不是 Claude 的 `sessionId`（例如 `sess-…` 是 herdr 自己的 id），第 3 条会**永远失败** → 一直降级。这一点必须等有 Claude pane 时用真机确认；届时我会先只读打印 `herdr api snapshot` 与目标文件首行做对账，不改任何配置。
- `windowMs=24h` 是启发值；如果 Claude 会话跨越超过 24h，需要放宽或改为「与 pane 活跃时间比对」。
