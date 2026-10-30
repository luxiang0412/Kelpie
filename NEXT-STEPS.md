# NEXT-STEPS — 下一步执行建议

> 当前状态：只读环境检查完成；SSH 层与主机指纹已在真机验证；API 层因两个前置条件被卡住。
> 依据：`RESULTS.md`、`ISSUES.md`、`spike/results/environment-report.md`。

## 0. 现在被什么卡住（按优先级）

| # | 卡点 | 影响 | 解除方式 |
| --- | --- | --- | --- |
| B-1 | `test` 会话不存在 | `ping` / `snapshot` / `subscribe` / 全部 Spike 2 在线项 | 批准 A-6（创建会话） |
| B-2 | sshj RSA 认证失败（I-23） | MaxSessions、通道并发、每请求延迟、NDJSON 往返 | 批准 A-7（Ed25519 专用密钥）后复测 |
| B-3 | 没有真机 | 延迟/耗电/后台/性能 | 接 Android 真机 |
| B-4 | `remote-api-bridge` 在 0.9.0 不存在 | 文档方案 A | 已定 D11：改用 socat / streamlocal，无需批准 |

## 1. 等待批准的变更（逐条列出影响与回滚）

### A-6 创建 `test` 会话（解除 B-1）

**将执行的命令**（远端）

```bash
# 1) 先确认不存在（幂等保护）
[ -e ~/.config/herdr/sessions/test ] && echo EXISTS && exit 1
# 2) 启动该会话的 headless server（后台，不 attach、不碰 default/ljn）
nohup ~/.local/bin/herdr --session test server >/tmp/herdr-test-boot.log 2>&1 &
# 3) 等 socket 就绪
for i in $(seq 1 40); do [ -S ~/.config/herdr/sessions/test/herdr.sock ] && break; sleep 0.25; done
# 4) 只读自检
~/.local/bin/herdr --session test status
```

**副作用**

- 新建目录 `~/.config/herdr/sessions/test/`（`session.json`、`herdr.sock`、日志）。
- 多一个 `herdr server` 进程（仅服务 `test` 会话）。
- herdr 可能在该会话里创建**一个默认 workspace + 一个 shell pane**（cwd = `$HOME`）；这正是后续实验要用的 pane。
- **不影响** `default`、`ljn` 会话；不改任何全局配置。

**回滚**

```bash
~/.local/bin/herdr --session test stop
rm -rf ~/.config/herdr/sessions/test
```

### A-7 生成 Spike 专用 Ed25519 密钥并授权（解除 B-2）

**将执行的命令**

```bash
# 本机（不覆盖已有文件）
[ -e ~/.ssh/id_ed25519_spike ] || ssh-keygen -t ed25519 -N '' -f ~/.ssh/id_ed25519_spike -C 'herdr-spike'
# 远端：只追加一行，先备份
ssh luxiang@192.168.5.8 'cp -a ~/.ssh/authorized_keys ~/.ssh/authorized_keys.bak.$(date +%s) && cat >> ~/.ssh/authorized_keys' < ~/.ssh/id_ed25519_spike.pub
```

**副作用**：`~/.ssh/authorized_keys` 追加 1 行；生成 1 对新密钥（本机 `~/.ssh/`）。
**回滚**：从 `authorized_keys` 删掉该行；删除本机密钥文件。
**理由**：① 产品只用 Ed25519，用 RSA 测 sshj 命题不严；② 避免继续复用与其他生产机共享的 `luxiang_rsa`。

### A-8 创建测试目录与隔离 Claude 配置（Spike 3/4 前置）

```bash
[ -e ~/spike-sandbox ] && echo EXISTS && exit 1     # 不覆盖
mkdir -p ~/spike-sandbox
# 隔离 Claude 配置（不碰 ~/.claude）
[ -e ~/.claude-spike ] && echo CONFIG_EXISTS && exit 1
mkdir -p ~/.claude-spike
```

**副作用**：新建两个目录。**回滚**：`rm -rf ~/spike-sandbox ~/.claude-spike`。
**注意**：`~/.claude-spike` 是**空**配置目录，Claude 会要求重新登录/重新信任目录——这会消耗交互，需要你确认是否接受。

### A-9（可选）只读探测 inode 与 streamlocal

- `ls -i ~/spike-sandbox/...`（只需一次，用于决定 SFTP 文件身份策略）。
- `ssh -L /tmp/herdr-test.sock:/home/luxiang/.config/herdr/sessions/test/herdr.sock -N` 手工验证 streamlocal（本地临时 socket，用完即删）。

## 2. 批准后立刻执行的顺序

```text
A-6 (创建 test 会话)
   └─→ Spike 2A：ping / snapshot / 初始一致性（只读）
          └─→ Spike 2B：events.subscribe + 一次受控事件（需批准 B 组）
A-7 (Ed25519)
   └─→ 复测 sshj 认证 → 若通过：
          ├─ Spike 1 剩余：每请求延迟 / 并发 / MaxSessions / 超限报错
          └─ Spike 2C：socat/streamlocal 两种 Transport 的延迟对比
A-8
   └─→ Spike 3（定位 JSONL）→ Spike 4（解析+渲染）
```

## 3. Spike 1 剩余工作（批准 A-7 后 0.5 天内）

| 步骤 | 命令/入口 | 通过标准 |
| --- | --- | --- |
| sshj + Ed25519 认证 | `--mode connect --key <ed25519> --runs 10` | 10/10 成功；记录 connect/auth 分解 |
| 单连接顺序请求 | `--mode channels`（先加 `--seq 100`） | P50/P95 有数；对比 ON-7 的 1.1 s |
| 并发 1/4/8/10/12 | `--mode channels` | 得到**实际** MaxSessions；超限报错原文；连接是否存活 |
| streamlocal | `--mode bridge --sock ...` 或 `ssh -L` | 明确可用/不可用；可用则记录延迟 |
| 超 MaxSessions 的客户端策略 | 代码层 | 排队上限（建议 4）+ 退避；不因超限断开整条连接 |

## 4. Spike 2A（只读，批准 A-6 后当天可做）

1. `ping` → 记录 `version` / `protocol` / `capabilities`（0.9.0 只有 5 项）。
2. `session.snapshot` → 保存原始 JSON；统计 pane/agent 数量（预期 1 个 shell pane）。
3. 与 CLI `herdr --session test api snapshot` 逐项比对（pane_id / 状态 / agent_session）。
4. `events.subscribe`：全局生命周期 + 逐 pane 状态（`pane_id` 必填）。
5. 只读观察 60 s：是否产生事件、事件载荷是否带时间戳、`subscription_started` 的实际形态。
6. 订阅不存在的 `pane_id`：记录是整条拒绝还是忽略、连接是否断（I-19 里的待验证项）。

**注意**：第 6 步会在服务端产生一次错误响应，属于只读探测，但仍会写日志；如果你要求零写入，请跳过并标 NOT TESTED。

## 5. Spike 2B（需要批准：会产生 pane 事件与输入）

这些动作会改变远端状态，执行前单独申请：

| 动作 | 目的 | 影响 |
| --- | --- | --- |
| `pane split` + `pane close`（≤ 20 次） | `events_lost` 复现、pane id 复用 | 在该会话内增删 pane；不影响其他会话 |
| `pane.send_text` 到一个 `cat` pane | 写操作语义 + Unknown | 向该 pane 输入文本 |
| `agent.prompt` | 写操作 + `agent_blocked` | 需要在该 pane 里跑 Claude（会消耗额度） |
| 断线注入（kill channel） | `Unknown` 判定 | 仅本地断开，不影响服务端状态 |

建议先做 `pane.send_text` + `cat`，成本最低、可观测性最好；`agent.prompt` 放到最后。

## 6. Spike 3–4（隔离配置）

- 只用 `CLAUDE_CONFIG_DIR=~/.claude-spike`；**不改** `~/.claude/settings.json`、不装全局 hook。
- Spike 3 先做方法②（`find ~/.claude/projects -name '<sid>.jsonl'`）与方法③（cwd 拼路径），方法①（SessionStart hook）最后做，且只在 `~/.claude-spike/settings.json` 里加：
  - 变更内容：新增一个 `SessionStart` hook → `~/spike-sandbox/hooks/session-hook.sh`；
  - 回滚：删除 `~/.claude-spike/settings.json` 中该条目（或整目录）；
  - 该 hook 写在 `~/spike-sandbox` 内，不污染其他项目。
- Spike 4 的 SFTP 读取直接用已验证的 `SftpTail`；先跑本地单测，再对 `~/.claude-spike/projects/...` 实测。
- 注意：`claude` 在隔离配置下需要重新登录 → 会用到账号；若你不希望在此主机登录 Claude，Spike 3/4 只能标 NOT TESTED。

## 7. Spike 5–8（维持原计划，补一条）

- Spike 5（审批安全）：先用假 TUI 三模式，再上真实 Claude；L1 hook 需要写 `~/.claude-spike`，按 D5 契约。
- Spike 6/7：需要真机。
- Spike 8：需要装 Node.js（单独批准）。

## 8. 三个“可能翻转的结论”

| 结论 | 现状 | 翻转条件 | 影响 |
| --- | --- | --- | --- |
| sshj 可作为 SSH 库 | FAILED（RSA） | Ed25519 也失败 | 换 MINA SSHD/JSch，Phase 1 延期 |
| 方案 A（exec + 桥）够快 | 未测（且 0.9.0 需 socat） | 每请求 P95 > 500 ms | 改 streamlocal（方案 B）或主机端复用脚本（方案 C） |
| 单连接复用能救延迟 | 未测 | 单连接多 channel 也慢 | 需要评估 herdr `remote` 协议或长连接池 |

## 9. 本轮没有做、也不打算在未批准时做的事

- 未创建/停止任何 herdr 会话（`default`、`ljn` 均未触碰）。
- 未创建 `~/spike-sandbox`、`~/.claude-spike`。
- 未改 `~/.ssh/authorized_keys`、`sshd_config`、防火墙、系统用户。
- 未升级 herdr / Claude / 任何全局软件。
- 未运行任何 pane 创建/关闭压力测试、未发送任何输入、未启动 Agent。
- 未用 sudo。
