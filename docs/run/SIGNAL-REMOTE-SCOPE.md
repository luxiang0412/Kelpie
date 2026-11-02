# Signal 重写期间的远端操作授权范围

> 2026-11-02 由 Claude 按用户授权（“授权范围你自己定”）制定，重写期间一律照此执行。

## 主机与会话

| 主机 | 会话 | 内容 | 允许的写操作 |
| --- | --- | --- | --- |
| `10.144.144.2`（**就是开发本机**） | `test` | pi ×2、claude ×2、codex ×1 | **只允许**发给 `w8:p1`（pi，cwd `~/spike-sandbox`） |
| `10.144.144.3` | `test` | pi ×1（cwd `~`） | 只允许发“只读”类提示 |

其他会话（`default`、`ljn`、`transfer-*`）**一律不连**。

## 规则

1. **只读不限**：`ping`、`session.snapshot`、`events.subscribe`、`pane.read`、SFTP 读 transcript。
2. **仓库内的 agent 只读**：`.2` 上 cwd 为本仓库的 `w7:p1`、`w9:p1`、`wA:p1`、`wB:p1` 不发任何写操作，避免它们在重写期间改动仓库。
3. **写操作内容**：只发短小、无副作用的提示（如“列出当前目录”“用一句话回答 1+1”），不让 agent 删除文件、git push、安装依赖或联网。
4. **审批**：只批准无副作用的请求（`ls`、`cat`、`pwd` 一类）；其他一律拒绝。
5. **不新建、不关闭 pane，不改 herdr 配置。**
6. **Claude hook**：只装在隔离的 `CLAUDE_CONFIG_DIR=~/spike-sandbox/.claude-signal`，附卸载步骤；不动 `~/.claude`。
7. 模型调用总量控制在少量（每个验证点 1–2 次）。
8. 每次写操作记录到对应阶段的报告里（目标 pane、内容、结果）。
