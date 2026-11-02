# herdr 远程端点审计（0.9.5 / protocol 22）

日期：2026-11-03。只读：`herdr --help`、`herdr api schema --json`（272 KB，schema_version 1）、`herdr integration status`、`~/.claude/hooks/herdr-agent-state.sh` 内容。没有改 herdr 配置。

## 结论（先说）

1. **不需要再造一个 Companion 传输层。** App 现在用的 `remote-api-bridge`（NDJSON over SSH）已经覆盖读状态、读屏、发文本、订阅事件。缺的只有"把结构化信息挂到某个 pane 上"，而 herdr 本身就有：`pane.report_metadata` 的 `tokens`。
2. **审批身份问题有解。** herdr 给每个 pane 里的进程注入 `HERDR_PANE_ID` 和 `HERDR_SOCKET_PATH`；Claude Code 的 PreToolUse 输入里有 `tool_use_id`。两者合起来，一个请求能唯一对应到"哪台机器 / 哪个 pane / 哪一次工具调用"。
3. **推送仍然缺。** herdr 没有任何向手机推送的能力（`notification.show` 只是 herdr 自己界面里的 toast）。手机在后台时只能靠常驻连接；真正可靠的后台提醒需要额外的推送源，见 `docs/signal/PUSH-OPTIONS.md`。

## 可用能力

| 能力 | 方法 / 机制 | App 现状 |
|---|---|---|
| 全量状态 | `session.snapshot` | 在用（10 s 对账） |
| 状态变化 | `events.subscribe`：`pane_updated`、`pane_agent_status_changed`、`pane_output_changed` 等 26 种 | 在用（只当失效信号） |
| 读屏 | `pane.read` / `agent.read` | 在用（终端回退、阻塞预览） |
| 发文本 | `agent.prompt`、`pane.send_text`、`pane.send_keys` | 在用（`agent.prompt`） |
| **pane 元数据** | `pane.report_metadata`：`tokens`（≤16 个，键 `[A-Za-z0-9_-]{1,32}`，带 `ttl_ms` ≤ 24 h）、`state_labels`、`title`、`display_agent` | **本次接入**：snapshot 的 `PaneInfo.tokens` / `AgentInfo.tokens` 能读到 |
| agent 状态上报 | `pane.report_agent`（state: idle / working / blocked / unknown）、`pane.report_agent_session` | herdr 自带的集成在用；我们不碰（会和 `herdr:claude` 的上报权限冲突） |
| 环境注入 | pane 内进程有 `HERDR_ENV=1`、`HERDR_PANE_ID`、`HERDR_SOCKET_PATH` | **本次接入**：审批 hook 用它确定自己属于哪个 pane |
| 新建 tab / pane | `tab.create`（可带 cwd、env、label）、`pane.split` | 只用于本次授权的 1 个沙箱 pane |
| 启动 agent | `agent.start`（name、kind、pane_id、args） | 未用 |
| 插件 | `plugin.*`（list / link / action.invoke / pane.open） | 未用；将来可以考虑把"审批"做成 herdr 插件 |

## 集成现状（.2）

`herdr integration status`：pi v9、claude v11、codex v10 已安装。Claude 的集成只挂了 `SessionStart`，作用是上报 `agent_session_id`（App 靠它定位 transcript）。**没有任何 PreToolUse 相关的东西**，所以 Claude 的权限请求 herdr 只能靠读屏判断成 `blocked`。

## 风险 / 注意

- `tokens` 是"display-only metadata"，herdr 不保证语义；键名、数量、TTL 有上限。审批 hook 每个请求占一个键（`sa_<id 尾部>`），并发上限 16 远大于实际需要。
- token 的值是 `tool_use_id`，不含命令内容；命令只写在主机上的请求文件里，经 SSH/SFTP 读取。
- 手机时钟和主机时钟可能有偏差（实测手机慢约 4 s）。请求过期判断用的是"手机现在 vs 主机写的 expires_at"，偏差到分钟级会出错；后续可以改用"收到时刻 + 剩余秒数"。
