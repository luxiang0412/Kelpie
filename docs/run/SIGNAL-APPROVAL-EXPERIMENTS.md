# Claude Code 手机审批：实验记录与结论

日期：2026-11-03 00:32–00:55。环境：.2，`test` 会话；隔离配置 `~/spike-sandbox/.claude-signal`（不碰 `~/.claude`）；Claude Code 2.1.316 经 OpenRouter（`anthropic/claude-haiku-5.5`，key 只从 .2 的环境变量读）；手机 MIUI 真机 + Signal debug 包。全部命令是 `pwd` / `ls`。

## 方案

`signal/companion/hooks/signal_approve.py`（PreToolUse hook，只用 Python 标准库）：

1. Claude 要调用 Bash → hook 写 `~/spike-sandbox/approvals/<tool_use_id>.req`（原子 rename），内容含 pane_id（来自 `HERDR_PANE_ID`）、命令、cwd、session_id、tool_use_id、一次性 nonce、过期时间；
2. 通过 herdr socket 给自己的 pane 挂 token `sa_<id 尾部>=<tool_use_id>`；
3. 手机看到 token 立刻读目录（不等 3 秒轮询），或者 3 秒轮询读到；显示权限卡；
4. 手机写 `<id>.answer`（request_id + nonce + allow/deny）；hook 校验后返回 `permissionDecision`；
5. 超时 / 校验失败 / hook 出错 → 返回 `ask`，退回 Claude 自己的终端确认。**任何异常都不会变成放行。**
6. 无论怎样结束（回答、超时、被 Esc 取消、进程被杀），都删除请求文件并清除 token。

## 实验结果

| # | 场景 | 结果 |
|---|---|---|
| 1 | 无头 `claude -p`，手机按住允许 | ✅ Claude 运行 `pwd`，输出 `/home/luxiang/spike-sandbox`。hook 输入确认有 `tool_use_id`、`session_id`、`transcript_path`、`prompt_id`；无头时没有 pane id |
| 2 | herdr pane（`w8:p2`）里的交互式 Claude，手机拒绝 | ✅ Claude 收到"在手机上拒绝（Herdr Signal）"，没有重试；snapshot 里能看到 token；卡片显示为该 agent（标题、"Claude Code"） |
| 3 | 一条消息里两个并发 Bash 调用 | ✅ 两个独立请求（不同 tool_use_id），手机两张卡；批准 `pwd`、拒绝 `ls`，Claude 分别执行 / 不执行 |
| 4 | 等待中在电脑上按 Esc | ✅ Claude 显示 Interrupted；请求文件和 token 都被清理，hook 进程退出（修复后；最初版本没处理信号） |
| 5 | 手机 120 秒不回答 | ✅ 退回终端确认，理由写着"手机 120 秒内没有回答，改在终端确认"；此时 herdr 读屏判为 `blocked`，手机按规范显示成"问题卡"而不是权限卡；在电脑上选 Yes 后正常执行 |
| 6 | 伪造回答（nonce 错误） | ✅ 拒收，退回终端确认（"手机回答校验失败"） |
| 7 | 延迟 | 稳态下请求出现后 0–3 秒手机读到（每次 SFTP 读目录 0.5–0.8 s）；token 出现时会额外立刻读一次 |

## 实验中发现并已修复

- hook：并发请求共用一个 token 键，先结束的会把后一个的 token 清掉 → 每个请求一个键。
- hook：被 Esc 取消时 `finally` 不执行，请求文件残留 → 处理 SIGTERM / SIGHUP / SIGINT。
- App：批准后卡片又冒出来一次（轮询与回答并发）→ 记住已回答的请求 id。
- App：一个 pane 有两个请求时，阻塞中的 agent 只保留第一个 → 按 pane 分组，每个请求一张卡。
- App：同一个 agent 同时出现在权限卡和"工作中" → 有请求的 agent 从其他列表移除。
- App：标题写"2 个 agent 在等你"（其实是同一个 agent 的两个请求）→ 按 agent 计数。
- App：请求带 pane_id 但 pane 上已经没有对应 token（hook 已不在等）→ 隐藏，给刚写出的请求 5 秒宽限。

## 结论

**可以在手机上真正批准 Claude Code 的工具调用**，身份可靠（tool_use_id + HERDR_PANE_ID），失败路径都退回终端，不会误放行。

还没解决 / 需要决定的：

1. **安装方式**：现在 hook 只装在隔离配置里。要在真实的 Claude 上用，需要在用户的 `~/.claude/settings.json` 里加一条 PreToolUse（这一步必须由你决定；可以做成 `herdr integration` 风格的安装脚本，或者 herdr 插件）。
2. **哪些工具需要手机批准**：现在只有 Bash，且对所有 Bash 都拦截。实际用时应该只拦截 Claude 本来就会问的那些（尊重 allowlist / Auto 模式的判断），否则会比现在更打扰。可行做法：hook 先调用 Claude 的权限判断不可行（hook 拿不到），只能在 hook 里维护一份与 settings 一致的 allow 规则，或者只在用户"离开电脑"时启用（例如手机 App 在线时挂一个标记文件）。
3. **电脑前的人想自己批**：hook 等待期间终端不出确认框。现在的做法是 Esc 取消再让 Claude 重来；更好的是在 herdr 侧给一个快捷键直接写 `.answer`，或者把等待时间缩短到 15–30 秒再退回终端。
4. **请求目录**：现在是 `~/spike-sandbox/approvals`（实验约定）。正式版应该放到 `~/.local/state/herdr-signal/approvals`，并在 App 主机设置里可配置。
5. **时钟偏差**：见审计文档最后一条。
