# 环境报告：herdr test 会话（只读检查）

- 检查时间：2026-10-29 17:54（主机本地时间）～18:05
- 连接方式：`ssh luxiang@192.168.5.8`（现有密钥，BatchMode，未做任何写入）
- 会话约束：只读；未创建 pane、未发送输入、未启动/停止任何 herdr server、未改任何配置

## 1. 主机

| 项目 | 值 |
| --- | --- |
| OS | Ubuntu 24.04.5 LTS (Noble) |
| 内核 | 7.0.0-38-generic（x86_64，SMP PREEMPT_DYNAMIC） |
| CPU | 24 vCPU |
| 内存 | 31.8 GiB 总 / 11.7 GiB 已用 / 10.4 GiB free / **20.1 GiB available** |
| Swap | 16 GiB（已用 8.7 GiB，偏高但可用） |
| 磁盘 | `/dev/nvme0n1p2` 1.9T，已用 896G，**可用 886G（51%）** |
| 负载 | load average 1.29 / 1.15 / 1.06（24 核 → 约 5%） |
| 运行时长 | 4 天 1 小时 |
| 登录用户 | `luxiang` 两个会话（seat0 登录界面 + `:1` 桌面），即**桌面正在运行** |
| 家目录权限 | `drwxr-x--- luxiang luxiang` |

资源与权限冲突风险评估：**低**（CPU/内存/磁盘都充裕）。但这是**共享桌面环境**（桌面会话 + 其他项目 + 正在运行的 herdr `default` 会话），因此所有实验必须限定在独立命名会话内，且避免创建大量 pane。

## 2. herdr

| 项目 | 值 |
| --- | --- |
| 二进制 | `/home/luxiang/.local/bin/herdr`（24.6 MB，2026-09-10） |
| 版本 | **herdr 0.9.0**（本地 Windows 侧是 0.9.3；文档基线 0.9.5） |
| 协议 | client `protocol 22`；schema `protocol = 22`，`schema_version = 1` |
| 非交互 PATH | **不含** `~/.local/bin`（必须用绝对路径调用） |
| 会话清单 | `default`（running，属其他项目）、`ljn`（stopped）、**`test` 不存在** |
| `test` 的 socket | `/home/luxiang/.config/herdr/sessions/test/herdr.sock` → **不存在** |
| `remote-api-bridge` | **0.9.0 没有这个子命令**：`unknown command: remote-api-bridge` |
| 远程连接用的进程 | 存在 `herdr remote-client-bridge`（`herdr --remote` 的传输端，**不是** JSON API 网桥） |
| 可用替代工具 | `socat`、`nc`、`python3` 3.12.3、`jq` 均存在 |

### 2.1 API schema 差异（0.9.0 远端 vs 0.9.3 本地）

| 维度 | 远端 0.9.0 | 本地 0.9.3 |
| --- | --- | --- |
| protocol / schema_version | 22 / 1 | 22 / 1 |
| 方法数 | 102 | 102 |
| 仅远端有 | `pane.graphics.clear` / `pane.graphics.info` / `pane.graphics.set` | — |
| 仅本地有 | — | `pane.clear` / `pane.link.resolve` / `server.ssh_agent.register` |
| `ping.capabilities` | 5 项（无 `ssh_agent_registration`） | 6 项 |
| `pane.agent_status_changed` 必填 | `type` + `pane_id` | 同 |
| `agent.wait` 参数 | `target` / `timeout_ms` / `until` | 同 |
| `pane.send_keys` 参数 | `pane_id` / `keys` | 同 |
| `agent.prompt` 参数 | `target` / `text` / `wait` | 同 |
| `presence`/`lease`/`owner`/`lock` | 0 处 | 0 处 |

**结论**：P0-2（无锁）、P0-5（无幂等键）、P0-7（无协作原语）、F-1（capabilities 非方法清单）、F-2（逐 pane 订阅）在**目标主机的 0.9.0 上同样成立**，不是 0.9.3 的巧合。

## 3. Claude Code

| 项目 | 值 |
| --- | --- |
| 版本 | **2.1.312**（`~/.local/bin/claude` → `~/.local/share/claude/versions/2.1.312`） |
| 配置目录 | `~/.claude`（存在）+ `~/.claude.json` |
| `settings.json` 顶层键 | `agentPushNotifEnabled, enabledPlugins, extraKnownMarketplaces, inputNeededNotifEnabled, modelSettings, skipDangerousModePermissionPrompt, statusLine, theme` |
| `hooks` | **空（`{}`）**，当前没有任何 hook |
| `CLAUDE_CONFIG_DIR` | 未在 `~/.bashrc` / `~/.profile` / `~/.bash_profile` 中设置 |
| 隔离测试配置 | **不存在**（可新建 `~/.claude-spike`，属远端写入，需批准） |

## 4. SSH / 网络

| 项目 | 值 |
| --- | --- |
| sshd | OpenSSH 9.6p1 Ubuntu-3ubuntu13.20（OpenSSL 3.0.13） |
| KEX | `sntrup761x25519-sha512@openssh.com`，cipher `chacha20-poly1305` |
| 服务端 host key | `ssh-ed25519 SHA256:lfFYDnJNKAPT1jtyrA6bU89XDcKtHQiy7PPdiX0G0T8`<br>`ecdsa-sha2-nistp256 SHA256:SsGysuwEsTf93ufA255RFCrkcRpsLVi0/RsuNiQ1t8w` |
| 服务端允许的签名算法 | `ssh-ed25519`、`ecdsa-sha2-nistp256/384/521`、`sk-*`、**`rsa-sha2-512`、`rsa-sha2-256`（没有 `ssh-rsa`）** |
| `MaxSessions` / `AllowStreamLocalForwarding` | 配置文件里**未显式设置**（`/etc/ssh/sshd_config` 与 `sshd_config.d/` 均无匹配项）→ 走默认（10 / yes），但**未能用 `sshd -T` 验证**（需要 root，未使用 sudo） |
| TCP 22 建连 | 10 次平均 **108 ms** |
| `ssh host true` 一次性往返 | 10 次 **1040–1139 ms**（P50 ≈ 1107 ms） |

## 5. 测试目录与配置隔离现状

| 项目 | 状态 |
| --- | --- |
| `~/spike-sandbox` | **不存在**（不会覆盖任何内容；需批准后创建） |
| 隔离 Claude 配置 | 不存在（需批准后创建 `~/.claude-spike`） |
| 已存在的 herdr 命名会话 | `ljn`（stopped），不触碰 |
| 正在运行的 herdr 会话 | `default`（其他项目），**不触碰** |

## 6. 结论：能否进入 Spike 1–2？

| 能力 | 结论 |
| --- | --- |
| SSH 连接 + 主机指纹校验 | ✅ 可做，且已完成（见 `RESULTS.md` S1-ON-1…4） |
| 冷连接/认证/单次 exec 延迟 | ✅ 可做（用 OpenSSH 客户端，已完成基本测量） |
| `ping` / `snapshot` / `subscribe` | ⛔ **BLOCKED**：`test` 会话不存在 → 没有 socket；创建会话需要批准 |
| `remote-api-bridge`（文档方案 A） | ❌ **在本主机不可用**（0.9.0 无此子命令）→ 需改用 `socat`/`nc` 或 streamlocal |
| MaxSessions / 通道并发 | ⛔ **BLOCKED**：需要能复用单连接多 channel 的客户端（sshj），而 sshj 认证当前失败 |
| Pane / Agent / 写操作 / 故障注入 | ⛔ **BLOCKED**：需先创建会话 + 批准 |

一句话：**只读检查通过**（资源与环境充足），但到 API 层还差两个前置条件——创建 `test` 会话（远端写入），以及修好 sshj 的 RSA 认证。
