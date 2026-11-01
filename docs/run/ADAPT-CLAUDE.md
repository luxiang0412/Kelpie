# Claude 适配 — transcript 解析（方案 B）

> 依据：2026-11-01 在远端 `~/.claude/projects/-home-luxiang-project-work-ParseDock/…jsonl`（**2050 行真实样本**）实测字段形状。
> **只读**：没有启动/修改任何 Claude 会话，没有改远端配置。

## 1. 实测到的真实结构

| 项 | 值 |
| --- | --- |
| 顶层 `type` 分布（2050 行） | `mode`101 / `permission-mode`101 / `atis-latch`101 / `file-history-snapshot`15 / **user 256** / `attachment`50 / `api-request`248 / `api-request-blob`333 / `last-prompt`100 / `ai-title`100 / **assistant 504** / system 24 / `tools-used`5 / `pr-link`99 … |
| 消息行字段 | `uuid` / `parentUuid` / `timestamp`(ISO) / `isSidechain` / `sessionId` / `cwd` / `message` |
| `message.content` 分片 | `text`114 / **`thinking`271** / **`tool_use`323** / **`tool_result`475** / `tool_addition`9 |
| `tool_use` | `{ id: "toolu_…", name: "Bash", input: {command…} }` |
| `tool_result` | `{ tool_use_id: "toolu_…", type: "tool_result", content: string \| [{type:text,text}] }`，**挂在 `user` 角色的行里** |

## 2. 实现

- 新增 `core/herdr/ClaudeTranscriptParser.kt`
  - 只处理 `type ∈ {user, assistant, system}`；`mode` / `api-request*` / `file-history-*` 等全部忽略
  - `isSidechain == true`（子代理）**不进主对话**
  - `text` → USER/ASSISTANT（按 role）；`thinking` → THINKING；`tool_use` → TOOL_CALL（带 `toolCallId = id`）；`tool_result` → TOOL_RESULT（带 `toolCallId = tool_use_id`）
  - `content` 可能是字符串或数组，两种都兼容；`timestamp` 解析为 epoch，失败不阻断
  - 未知识别分片（`tool_addition` 等）忽略；任何异常行跳过，不抛
- 新增 `TranscriptParser`（按路径分发）：`/.claude/projects/` → Claude，其余 → pi
- `TranscriptTailer`、`TranscriptLoader` 改用统一入口；顺带把 `TranscriptLoader` 自己打开的 SFTP 也改为 `sharedSftp()`（与上一个重连修复一致）

## 3. 测试（JVM）

`ClaudeTranscriptParserTest` 6 项全绿：
1. 解析出 USER/THINKING/ASSISTANT/TOOL_CALL/TOOL_RESULT 且跳过 api-request 与 sidechain
2. `tool_result` 用 `tool_use_id` 与 `tool_use` 的 `id` 正确关联
3. 保留 `uuid`/`parentUuid`/`timestamp`（分支还原可用）
4. `content` 为数组时文本拼接
5. 非法行/空对象不抛异常
6. 分发器按路径选择解析器

其余回归：`:core:herdr:test` 通过；AVD UI **28/28**。

## 4. 仍未验证（必须如实）

- **没有在 App 里跑通真实 Claude 会话**：herdr 对 Claude 上报的 `agent_session` 是 `kind: "id"`（`sess-…`），**不给文件路径**，所以 `ConversationScreen` 仍会降级为「终端尾部」。
  → 要真正看到 Claude 对话，需要额外一步：**按 pane 的 cwd 推断 `~/.claude/projects/<slug>/<最新>.jsonl`**（方案 C）。
- 因此：解析器与分发逻辑已就绪并有单测，但**端到端未验证**（不宣称可用）。

## 5. 建议的下一步（需你点头）

方案 C：在 `HerdrCodec`/ViewModel 里对 `agent == "claude"` 且 `agent_session.kind == "id"` 的 pane，按 `cwd` 映射 `~/.claude/projects/<slug(cwd)>/`，取**最新修改**的 jsonl 作为只读 transcript。
风险：slug 规则若变化会取错文件；需加「文件 mtime 时间窗 + 目录唯一匹配」双重校验，并在 UI 上显示来源路径以便核对。
