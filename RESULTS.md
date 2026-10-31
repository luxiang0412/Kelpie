# RESULTS — Phase 0 技术验证结果

> 更新：2026-10-29（离线 JVM 验证 + 对 `luxiang@192.168.5.8` 的只读在线验证）
> 对应文档：`herdr-android-client-research-v2.md`（v2.1）、`herdr-android-spike-plan.md`
> 环境报告：`spike/results/environment-report.md`
>
> 状态词（本轮起统一使用英文标记）：**VERIFIED** / **FAILED** / **BLOCKED** / **NOT TESTED**
> （分别对应之前的「已验证 / 验证失败 / 尚未验证」；`BLOCKED` 专指“被外部前置条件卡住”，`NOT TESTED` 指“本轮范围内但没开始”）

## 0. 结论摘要

| 项目 | 状态 | 一句话 |
| --- | --- | --- |
| 文档修订（7 项 P0 + 5 项事实错误） | VERIFIED | 已改完，见 §6 |
| 离线 JVM 验证（指纹/写账本/SFTP Tailer） | VERIFIED | 22/22 单测通过 |
| SSH 连接 + 主机指纹显式校验 | VERIFIED | 真机 fail-closed，指纹与 `ssh-keygen -lf` 完全一致 |
| SSH 冷连接 / 单次 exec 延迟 | VERIFIED | 一次性往返 P50 ≈ 1107 ms，TCP ≈ 108 ms |
| sshj 0.41.2 用户认证（RSA key） | **FAILED** | `Exhausted available authentication methods`，卡住所有通道级测量 |
| herdr 版本 / 协议 / schema / 会话清单 | VERIFIED | **0.9.0**，protocol 22，`test` 会话**不存在** |
| `ping` / `snapshot` / `subscribe` | **BLOCKED** | 需要 `test` 会话的 socket；创建会话需批准 |
| `remote-api-bridge`（文档方案 A） | **FAILED**（在 0.9.0 上） | `unknown command: remote-api-bridge`；需 `socat`/`nc`/streamlocal 替代 |
| API 语义静态事实（无锁/无幂等键/无协作原语/订阅粒度） | VERIFIED | 在 **0.9.0** 上复验，与 0.9.3 一致 |
| MaxSessions / 通道并发 / 延迟分解 | **BLOCKED** | 依赖 sshj 单连接多 channel |
| 写操作、Unknown、故障注入、Agent、Pane 行为 | NOT TESTED | 需批准 + 会话 + 可用客户端 |
| Spike 3–8 | NOT TESTED | 本轮范围外 |

## 1. 环境

完整报告见 `spike/results/environment-report.md`。要点：

| 项目 | 值 |
| --- | --- |
| 主机 | Ubuntu 24.04.5 LTS，x86_64，24 vCPU，31.8 GiB RAM，886 GB 可用，load ≈ 1.2 |
| herdr | **0.9.0**（`~/.local/bin/herdr`，非交互 PATH 里没有） |
| 现有会话 | `default`（running，其他项目）、`ljn`（stopped）、**`test` 不存在** |
| Claude Code | 2.1.312，`~/.claude/settings.json` 无 hook，未设 `CLAUDE_CONFIG_DIR` |
| sshd | OpenSSH 9.6p1；仅允许 `rsa-sha2-512/256`（无 `ssh-rsa`） |
| 本机执行环境 | Windows 11 / JDK 17.0.3 / Gradle 9.4.1 / sshj 0.41.2 / JUnit 5.11.4 |
| Android 设备 | 仅 `emulator-5554`，**无真机** |

## 2. S1-OFFLINE：离线 JVM 验证（VERIFIED）

证据：`spike/results/s1-local-unit-tests.txt`（22 PASSED / 0 FAILED，可重跑：`spike/jvm` → `gradle test`）。

| ID | 项 | 状态 | 证据 |
| --- | --- | --- | --- |
| OFF-1 | 未知主机不被自动信任（禁止 TOFU），信任库保持为空 | VERIFIED | `HostKeyTrustTest > F1` |
| OFF-2 | 显式确认后 pin，再次连接不再询问 | VERIFIED | `F2`、`F3` |
| OFF-3 | 篡改已 pin 的指纹 → 拒绝且不覆写 | VERIFIED | `F4` |
| OFF-4 | 算法变化视为不同身份，绝不自动信任 | VERIFIED | `F5` |
| OFF-5 | 写操作超时 → `Unknown`；永不自动重试；只有正向证据可升级；重发换新 id | VERIFIED | `WriteLedgerTest` ×6 |
| OFF-6 | SFTP Tailer：UTF-8 跨块、非法字节、截断、更大文件替换、偏移恢复、恢复校验失败、半行、短读、大文件读预算 | VERIFIED | `SftpTailTest` ×10 |
| OFF-7 | NDJSON 编解码：忽略未知字段、未知错误码原样透传 | VERIFIED | `Ndjson` + 自检 |

## 3. S1-ONLINE：对真实主机的只读验证

### 3.1 已通过（VERIFIED）

| ID | 项 | 实测 | 证据 |
| --- | --- | --- | --- |
| ON-1 | 主机指纹带外核对 | `ssh-keyscan` + `ssh-keygen -lf` 得到 ED25519 `SHA256:lfFYDnJNKAPT1jtyrA6bU89XDcKtHQiy7PPdiX0G0T8`，与 OpenSSH 实际握手用的 host key 一致 | `results/environment-report.md` §4 |
| ON-2 | F1 未知主机 fail-closed（真机） | 抛 `HostKeyUnknownException`，**未发送任何凭据**，信任库**未写入**，耗时 338 ms（仅 KEX） | `RESULT,F1,...store_written=false` |
| ON-3 | F4 篡改指纹（真机） | 抛 `HostKeyMismatchException`，pin 记录**未被改写** | `RESULT,F4,...pin_unchanged=true` |
| ON-4 | F2/F3 显式确认后静默通过（真机） | 提示次数 = 1；写入 `192.168.5.8:22:ssh-ed25519 SHA256:lfFY...` | `RESULT,F2/F3,prompts=1` |
| ON-5 | 指纹实现与 OpenSSH 一致 | 我们算出的指纹与 `ssh-keygen -lf` **逐字符相同**（踩坑：sshj 的 `getEncoded()` 是 X.509，`SecurityUtils.getFingerprint()` 是 MD5） | `spike/jvm/.../trust/SshjVerifier.kt` |
| ON-6 | TCP 22 建连延迟 | 10 次平均 **108.5 ms** | `results/s1-tcp-latency.csv` |
| ON-7 | 一次性 SSH 往返（TCP+KEX+auth+exec+exit） | 10 次 **1040–1139 ms**，P50 ≈ **1107 ms** | `results/s1-ssh-cli-latency.csv` |
| ON-8 | herdr 版本/协议/schema | 0.9.0 / protocol 22 / schema_version 1 / 102 方法 | `results/herdr-api.remote-0.9.0.schema.json` |
| ON-9 | `test` 会话是否存在 | `herdr session list` 只有 `default`、`ljn`；`sessions/test/` 不存在 | 同上 §2 |
| ON-10 | `remote-api-bridge` 可用性 | **不存在**（`unknown command`），rc=2 | `results/environment-report.md` §2 |
| ON-11 | 0.9.0 的 API 语义不变量 | `agent.wait` 无锁参数、写操作无幂等键、`pane.agent_status_changed` 必带 `pane_id`、协作原语 0 命中 | `results/herdr-schema-summary.md` + 差异对比 |

### 3.2 失败（FAILED）

| ID | 项 | 现象 | 已排除的可能 |
| --- | --- | --- | --- |
| ON-12 | sshj 0.41.2 公钥认证（RSA 3072/4096） | 三次 `publickey` 尝试后 `UserAuthException: Exhausted available authentication methods` | ① 不是密钥未授权：同一私钥用 OpenSSH 客户端（`IdentitiesOnly=yes`、`IdentityAgent=none`）可登录；② 不是密钥解析错：sshj 导出的公钥指纹 `SHA256:LiY1T1l9W1yMOCam/EPG1SlrjIDlmQp9zhzP27CcXpE` 与 `ssh-keygen -lf id_rsa.pub` **完全一致**；③ 不是 host key 校验导致：F1/F2/F4 均正常 |

证据：`results/s1-sshj-auth-debug.log`（含 `Server supports signature algorithms: [... rsa-sha2-512, rsa-sha2-256]`、三次 `Attempting authentication using OpenSSHKeyV1KeyFile`、最终 `publickey auth failed`）。

已尝试且**无效**的修法：`SecurityUtils.registerSecurityProvider(null)` 注册 BC；`DefaultConfig.keyAlgorithms` 把 `rsa-sha2-512/256` 提前；`OpenSSHKeyV1KeyFile().init(privateKey, publicKey)` 双文件初始化；改用 PKCS#1 的 `luxiang_rsa`（该 key sshj 连解析都失败，报 `No provider available for Unknown key file`）。

**影响**：所有需要“单连接多 channel”的测量（MaxSessions、exec 并发、每请求延迟、NDJSON 往返）都被卡住。**产品技术选型风险**：如果 Phase 1 前解决不了，sshj 这个选择本身要重新评估（备选 Apache MINA SSHD / JSch）。

## 4. S2-SCHEMA：API 静态事实（VERIFIED，已在 0.9.0 目标主机复验）

证据：`results/herdr-api.remote-0.9.0.schema.json`（远端导出，275 KB）+ `results/herdr-schema-summary.md`（本地 0.9.3 摘要）。

| ID | 事实 | 状态 |
| --- | --- | --- |
| SCH-1 | `agent.wait` 参数 = `{target, timeout_ms, until}`，**无锁、无 ownership 字段** | VERIFIED（0.9.0 + 0.9.3 一致） |
| SCH-2 | `pane.send_keys` = `{pane_id, keys}`；`pane.send_text` = `{pane_id, text}`；`agent.prompt` = `{target, text, wait}` → **无前置条件、无请求 id、无幂等键** | VERIFIED（两版本一致） |
| SCH-3 | `pane.agent_status_changed` **必带 `pane_id`**；`pane.created/closed/updated/exited/agent_detected/focused/moved`、`tab.*`、`workspace.*`、`layout.updated` **只接受 `type`** | VERIFIED（两版本一致） |
| SCH-4 | 102 个方法中 `presence`/`lease`/`owner`/`lock` 出现 **0 次** | VERIFIED（两版本一致） |
| SCH-5 | `ping.capabilities` 是 5–6 个 boolean 特性开关，**不是方法清单**（0.9.0 少 `ssh_agent_registration`） | VERIFIED |
| SCH-6 | `ErrorBody.code` 是自由字符串；`events_lost` / `agent_blocked` 不在 schema 枚举中 | VERIFIED |
| SCH-7 | 版本漂移：0.9.0 有 `pane.graphics.*`，0.9.3 有 `pane.clear` / `pane.link.resolve` / `server.ssh_agent.register` | VERIFIED |

## 5. 被阻塞 / 未测试

| ID | 项 | 状态 | 阻塞原因 |
| --- | --- | --- | --- |
| S2-ON-1 | `ping` / `session.snapshot` / `events.subscribe` | BLOCKED | `test` 会话未创建 → socket 不存在；创建需批准 |
| S2-ON-2 | 订阅与快照初始一致性 | BLOCKED | 同上 |
| S2-ON-3 | `events_lost` 复现与恢复 | NOT TESTED | 需要 pane 事件源（故障注入需批准） |
| S2-ON-4 | 写操作语义在线验证（`agent.prompt` / `send_keys` / `send_text`） | NOT TESTED | 需 Agent pane + 输入，需批准 |
| S2-ON-5 | 断线注入 / `Unknown` 判定 | NOT TESTED | 同上 |
| S2-ON-6 | pane id 移动/关闭/复用 | NOT TESTED | 需创建/关闭 pane，需批准 |
| S2-ON-7 | MaxSessions 实际值、并发上限、超限报错 | BLOCKED | 需要 sshj 单连接多 channel（ON-12 失败） |
| S2-ON-8 | `terminal session observe` 只读帧 | NOT TESTED | 需 pane |
| S3 / S4 / S5 / S6 / S7 / S8 | Claude JSONL 定位、增量解析渲染、审批安全、后台、性能、ACP | NOT TESTED | 本轮范围外；S3 需先创建隔离 `CLAUDE_CONFIG_DIR` |

## 6. 第一阶段（文档修订）结果

| P0 | 修订位置 | 状态 |
| --- | --- | --- |
| P0-1 指纹显式校验、禁止 TOFU | 调研 v2「安全」；任务书 Spike 1 代码 + F1–F4；本轮 ON-2/3/4 真机验证 | VERIFIED（规则 + 实现 + 真机行为） |
| P0-2 `agent.wait` 不是锁 | 调研 v2「写操作语义」专节；Spike 2 并发实验 | VERIFIED（静态事实，0.9.0/0.9.3 一致） |
| P0-3 审批 TOCTOU 不得宣称绝对安全 | 调研 v2「审批安全子系统」+ 时序图 + 三项验收；Spike 5 重写 | VERIFIED（口径），错题率数值 NOT TESTED |
| P0-4 PermissionRequest Hook 契约 | 调研 v2 新增契约小节；Spike 5 步骤 5 | VERIFIED（契约文本），hook 行为 NOT TESTED |
| P0-5 写操作 Unknown 语义 | 调研 v2「写操作语义」；Spike 2 步骤 6；`WriteLedger` 单测 | VERIFIED（实现 + 单测），在线注入 NOT TESTED |
| P0-6 SFTP Tailer 契约 | 调研 v2「SFTP Tailer 契约」8 条；Spike 4 重写；`SftpTail` 10 单测 | VERIFIED（离线），真实 SFTP NOT TESTED |
| P0-7 团队 Presence/Lease/Ownership | 调研 v2 新增章节；差异化/roadmap 降级 | VERIFIED（0.9.0/0.9.3 均无原语） |
| F-1…F-5 事实修正 | capabilities 非方法清单、订阅粒度、`recent_unwrapped`、错误码、平台路径 | VERIFIED |

## 7. 待验证项状态

| 编号 | 事项 | 状态 | 说明 |
| --- | --- | --- | --- |
| 1 | sshj streamlocal 支持 | BLOCKED | 需 sshj 可用 + 会话 socket |
| 2 | bridge 延迟 / MaxSessions | BLOCKED | sshj 认证失败；且 0.9.0 无 `remote-api-bridge` |
| 3 | `remote-api-bridge` 兼容性 | **FAILED（0.9.0）** | 子命令不存在；需 ≥ 某版本或改走 socat |
| 4 | pane id 复用/移动 | NOT TESTED | 需创建 pane |
| 5 | `CLAUDE_CONFIG_DIR` 发现 | NOT TESTED | 需 Agent pane |
| 6 | 子 agent transcript 路径 | NOT TESTED | — |
| 7 | Claude JSONL 行结构 | NOT TESTED | — |
| 8 | SFTP vs tail 延迟/耗电 | NOT TESTED | 离线契约已验证，在线未测 |
| 9 | 读屏审批竞态 | NOT TESTED | 需 pane |
| 10 | 前台服务 / 耗电 | NOT TESTED | 无真机 |
| 11 | 性能基线 | NOT TESTED | 无真机 |
| 12 | herdr-acp / ACP SDK | NOT TESTED | 需装 Node.js |
| 20 | bridge 单请求语义 | BLOCKED | 同 #2 |
| 21 | 超 MaxSessions 报错 | BLOCKED | 同 #2 |
| 22 | `events_lost` / `agent_blocked` 真实码 | BLOCKED | 需会话 |
| 23 | 新 pane 补订阅必要性 | 静态 VERIFIED | 代价未测 |
| 24 | `agent.wait` 不互斥 | 静态 VERIFIED | 并发实证未测 |
| 25 | L2 错题率 / T2→T3 延迟 | NOT TESTED | — |
| 26 | hook 超时默认行为 | NOT TESTED | — |
| 27 | SFTP 身份字段（inode） | 部分 VERIFIED | 前缀判定已实现；在线待测 |
| 28 | 未公开协作原语 | 部分 VERIFIED | 0.9.0 无 |

## 8. 证据索引

| 文件 | 内容 |
| --- | --- |
| `spike/results/environment-report.md` | 本轮只读环境报告（主机、herdr、Claude、SSH） |
| `spike/results/herdr-api.remote-0.9.0.schema.json` | 目标主机 herdr 0.9.0 的 schema 导出 |
| `spike/results/herdr-api.schema.json` | 本机 herdr 0.9.3 的 schema 导出 |
| `spike/results/herdr-schema-summary.md` | 从 schema 自动生成的摘要 |
| `spike/results/s1-local-unit-tests.txt` | 22 个单测输出 |
| `spike/results/s1-ssh-cli-latency.csv` | 一次性 SSH 往返 10 次 |
| `spike/results/s1-tcp-latency.csv` | TCP 22 建连 10 次 |
| `spike/results/s1-sshj-auth-debug.log` | sshj 认证失败的 debug 日志（ON-12） |
| `spike/results/s1-local-env.txt` | 本机执行环境 |
| `spike/jvm/` | 可重跑工程（`gradle test` / `gradle installDist` + `--mode hostkey|connect|channels|bridge`） |

---

# 追加：ONLINE-2（2026-10-31 15:16 远端时间 / 同步于本机 MPST）

> 本轮在获批后执行：A-6（创建 `test` 会话）、A-7（ed25519 专用密钥）、A-8（沙箱目录）。
> **未**创建 workspace/pane、**未**运行 Agent、**未**发送任何输入。

## 9.1 环境变化（重要，非本次操作造成）

| 项目 | 之前（10-29 只读检查） | 现在（10-31） |
| --- | --- | --- |
| herdr 版本 | 0.9.0 | **0.9.5**（二进制 `2026-10-30 08:03:28`，30,285,672 B） |
| `remote-api-bridge` | 无（`unknown command`） | **有**：`--check` → `herdr-api-bridge-v1` |
| `test` 会话 | 不存在 | **running**（本次创建，见 9.2） |
| 0.9.5 schema | — | 与 0.9.3 **完全一致**（102 方法、参数、订阅必填字段）；capabilities 6 项（含 `ssh_agent_registration`） |

升级不是本会话所为（本会话没有执行任何 `herdr update`；`herdr-client.log` 最后修改仍是 10-26）。原因待你确认，见 `ISSUES.md` I-29。

## 9.2 A-6：创建 `test` 会话（VERIFIED）

```bash
[ -e ~/.config/herdr/sessions/test ] && exit 1        # 预检：不存在
nohup ~/.local/bin/herdr --session test server > ~/spike-test-boot.log 2>&1 &
# socket 200 ms 后就绪
```

- `herdr session list` → `default`(running)、`ljn`(stopped)、**`test`(running)**
- `status`：client/server 均 0.9.5，`endpoint_compatible: yes`，`private_protocol: 22`
- **`session.snapshot` 是空的**（0 workspace / 0 tab / 0 pane）→ 该会话**不会**自动创建默认 pane。`ISSUES.md` I-32

## 9.3 协议行为（VERIFIED）

证据：`spike/results/s2-snapshot-bridge.json`、`s2-snapshot-cli.json`、`s2-subscribe-*.txt`、`s2-ping-latency.csv`。

| ID | 项 | 结果 |
| --- | --- | --- |
| ON2-1 | `ping` | `{"type":"pong","version":"0.9.5","protocol":22,"capabilities":{live_handoff:true, detached_server_daemon:false, endpoint_protocol_generation:1, surface_interest:true, health_check:true, ssh_agent_registration:true}}` |
| ON2-2 | **一个连接只处理一个请求** | 同一连接发 2 个请求：第 1 个正常返回，第 2 个得到 `Error: Os { code: 104, ConnectionReset }` → 待验证 #20 **关闭** |
| ON2-3 | `session.snapshot` bridge vs CLI | 两者字段与内容完全一致（当前都是空快照，`type=session_snapshot`, version 0.9.5, protocol 22） |
| ON2-4 | 全局 `events.subscribe` | 立即返回 `{"result":{"type":"subscription_started"}}`；随后空闲 12 s **没有任何事件**（不重放、不心跳） |
| ON2-5 | 订阅不存在的 pane | `{"error":{"code":"pane_not_found","message":"pane w9:p9 not found"}}` → **整条请求被拒**（不是静默忽略） |
| ON2-6 | `pane.agent_status_changed` 缺 `pane_id` | `{"error":{"code":"invalid_request","message":"invalid request: missing field \`pane_id\` at line 1 column 107"}}` → **F-2 在真机确认** |

## 9.4 延迟与通道预算（VERIFIED）

| ID | 测量 | 结果 | 证据 |
| --- | --- | --- | --- |
| ON2-7 | `ping`（每次新建 SSH 连接） | n=20，avg **1181 ms**，P50 **1185**，P95 **1222**，max 1224 | `s2-ping-latency.csv` |
| ON2-8 | sshj 连接+认证（Ed25519） | connect P50 ≈ **239 ms**，auth P50 ≈ **265 ms**，合计 P50 ≈ **502 ms**（首次 777 ms 含预热） | `s1-connect-ed25519.csv` |
| ON2-9 | 复用连接上单个 exec channel（`true`） | n=10，P50 **201.4 ms**（min 197.9，max 280.5） | `s1-channels.csv` |
| ON2-10 | **复用连接上 `ping`（方案 A 真实成本）** | n=20，P50 **247.7 ms**，P95 **255.1**，max 382.7 | `s1-pingloop.csv` |
| ON2-11 | 并发 exec channel 上限 | **9 个并发成功**；第 10 个起 `OpenFailException: open failed`；**连接保持可用**（`connection_alive_after=true`），fresh 连接复现 2/2 | `s1-maxsessions.csv` |

推论（写进 `DECISIONS.md`）：
- 不重用连接 = 1.2 s/请求，**不可接受**；必须长连接复用。
- 复用后每请求 ≈ 250 ms，其中 **200 ms 是 exec channel 本身**，herdr 只占约 45 ms。
- 有效通道上限 **9**（不是默认 10）；客户端并发建议限 4，并给订阅/SFTP/终端观察各留 1。

## 9.5 sshj：RSA 失败，Ed25519 成功（VERIFIED）

| 项 | 结果 |
| --- | --- |
| RSA（3072/4096，两把现有 key） | **FAILED**（三次 `USERAUTH_FAILURE` → `Exhausted available authentication methods`），原因仍未定位 |
| Ed25519（`~/.ssh/id_ed25519_spike`，`SHA256:3xa+zIxtftJjvwduu2b3Sq5EERg2LI3EA5rofUGTD5o`） | **VERIFIED**：5/5 认证成功，可正常 exec |
| 结论 | 产品只用 Ed25519 → 选型风险**降级**，但 RSA 路径仍不可用，且**必须**在 Android 上复测一次 |

授权变更：`~/.ssh/authorized_keys` 从 2 行（RSA×2）变为 **3 行**（+1 ed25519），备份 `authorized_keys.bak.<ts>`。
回滚：删除 `herdr-spike` 那一行。

## 9.6 A-8：沙箱目录（VERIFIED）

`~/spike-sandbox/{pi-sessions,logs}` 已创建（创建前确认不存在）。

## 9.7 仍然 BLOCKED / NOT TESTED

| 项 | 状态 | 原因 |
| --- | --- | --- |
| 带 pane 的订阅（`pane.agent_status_changed` 实事件） | BLOCKED | 会话里 0 pane；需批准创建 workspace/pane |
| `events_lost` 复现与恢复 | BLOCKED | 需产生事件（创建/关闭 pane） |
| 写操作（`pane.send_text` / `agent.prompt` / `send_keys`） | BLOCKED | 需 pane + 批准 |
| 断线注入 / `Unknown` 判定 | BLOCKED | 需先有可观测的写操作 |
| pane id 移动/关闭/复用 | BLOCKED | 同上 |
| pi 作为 Agent（Spike 3） | BLOCKED | 需在 pane 里启动 `pi`，会消耗额度，需批准 |
| MaxSessions 的*真实*配置值 | 部分 | 无 sudo，`sshd -T` 不可用；只能给“有效上限 9”的观测值 |

## 9.8 会话里已有 pane 之后的只读观察（2026-10-31 15:30）

用户手动升级 herdr 并在 `test` 会话里创建了 3 个 workspace / 4 个 pane，其中 2 个 pane 在跑 `pi`（`agent_status=idle`）。
本轮只做**只读**观察，未向任何 pane 发送输入。

| ID | 项 | 结果 | 证据 |
| --- | --- | --- | --- |
| ON2-12 | 有内容时的 snapshot 一致性 | bridge 与 CLI **完全一致**（4 panes / 2 agents / 3 workspaces / 4 tabs，逐项比对 `IDENTICAL: True`） | `s2-snapshot-bridge-with-panes.json`、`s2-snapshot-cli-with-panes.json` |
| ON2-13 | `pane.read` 返回结构 | `{read:{pane_id, workspace_id, tab_id, source, format, text, revision, truncated}}`；shell pane 的 `revision = 0` | `s2-pane-read-visible.json` |
| ON2-14 | 订阅**已存在**的 pane | 接受，返回 `subscription_started`（对比 `pane_not_found`） | `s2-subscribe-existing-pane.txt` |
| ON2-15 | pi pane 的 `agent_session` | **null**（herdr 能靠标题识别 agent 是 pi，但没有 session path） | `pane get w1:p1` / snapshot agents |
| ON2-16 | pi 进程信息 | `process-info` 给出 `pid=938662, argv=["pi"], cwd=/home/luxiang/project/private/herdr-gui-research` | 同次命令输出 |
| ON2-17 | pi 会话文件 | `~/.pi/agent/sessions/--home-luxiang-project-private-herdr-gui-research--/` **存在但 0 个 `.jsonl`**（未发生对话 / 未落盘） | `ls`/`find` 结果 |
| ON2-18 | CLI 与 API 的 `source` 命名 | CLI 接受 `recent-unwrapped`（短横线），JSON API 枚举是 `recent_unwrapped`（下划线） | `pane read --help` vs schema |

**推论（写进 Spike 3 计划）**：
- 不装 `herdr integration install pi` 时，`agent_session` 拿不到路径 → 必须自己定位：按 `foreground_cwd` 推导目录 `--<path 中非字母数字→->--`，再取该目录下最新 `.jsonl`。
- 该目录当前为空，说明 **`pi` 可能只在首次对话后才写 session 文件** → “首次提问前没有日志”的窗口需要用时间序列测（Spike 3 步骤 7）。

## 9.9 专用 pane 里的写操作 / 事件 / pi（2026-10-31 15:30–15:40）

已获批：A（专用 workspace + 写操作与事件测试）、P-2（在专用 pane 跑 pi）。
专用 workspace：**`w4`**（label `spike`，cwd `~/spike-sandbox`，`--no-focus`），使用 pane **`w4:p1`**。
用户原有 4 个 pane（`w1:p1`、`w1:p2`、`w2:p1`、`w3:p1`）**未收到任何输入**。

| ID | 项 | 结果 | 证据 |
| --- | --- | --- | --- |
| ON2-19 | `pane.send_text` + `pane.send_keys ["enter"]` | 返回 `{"type":"ok"}`；shell 执行；`pane.read --source visible` 看到输出 | `s2-write-basic.json` |
| ON2-20 | **`revision` 的真实语义** | `pane.read.revision` **恒为 0**（内容变化后仍是 0）；`pane.get.revision` 0→1 一次后不再变。→ **`revision` 不是内容版本号** | 见下 |
| ON2-21 | `pane.split` 的参数名 | schema 是 **`target_pane_id`**（不是 `pane_id`）。传 `pane_id` 会被忽略，**split 到当前焦点 pane** | schema + 误操作记录 |
| ON2-22 | pane id 复用 | 关闭后新建得到 `w4:p3`（前一个是 `w4:p2`）→ **递增不复用** | `s2-events-pane-lifecycle.ndjson` |
| ON2-23 | 事件载荷形态 | `{event, data:{type,...}}`；`pane_created`/`pane_updated` 带完整 pane，`pane_closed` 只有 `pane_id`+`workspace_id`，`layout_updated` 带 layout；**没有任何时间戳字段** | 同文件 |
| ON2-24 | `terminal session observe` | 可用，返回 `{"bytes":"<base64 ANSI>","encoding":"ansi","full":true,"seq":1,"width":60,"height":20,"type":"terminal.frame"}`；不影响 pane 本身 | 命令输出 |
| ON2-25 | pi 1.4.0 + `--session-dir` | 生效，会话文件落在**该目录直属**：`~/spike-sandbox/pi-sessions/2026-10-31T07-29-19-047Z_01a190dc-….jsonl`（**没有** `--<cwd>--` 子目录） | `ls` 结果 |
| ON2-26 | pi JSONL 行结构（小样本） | 6 行：`session`×1、`model_change`×1、`thinking_level_change`×1、`message`×3 | 计数脚本 |
| ON2-27 | pi 运行时的 herdr 识别 | 运行中能靠标题识别为 `pi`；**`agent_session` 始终 `null`**；退出后 `agent=null`、`agent_status=unknown` | `pane get w4:p1` |
| ON2-28 | pi 非交互耗时 | `pi --session-dir … -p "reply with exactly: SPIKE-OK"` 在 **< 5 s** 内返回 | 轮询输出 |

### 一次误操作（已恢复，如实记录）

第一次 split 用了 `pane_id` 字段（文档/任务书里的写法），服务端忽略该字段、按**当前焦点 pane**（`w1:p2`）执行，结果在**用户自己的 workspace `w1`** 里建了 `w1:p3`。
- 影响：约 40 秒内 `w1:t2` 多了一个空 shell pane（未输入任何内容）。
- 处置：立即 `pane.close w1:p3`，`pane list` 确认回到原状（`w1:p1`、`w1:p2` 未变）。
- 结论：**`pane.split` 必须用 `target_pane_id`**；写进 `ISSUES.md` I-34 与任务书。

### 对设计的影响

- 审批 L2 里“`revision` 变了但问题哈希没变就继续”的规则**不成立**：`revision` 不反映屏幕内容（ON2-20）。L2 的判据只能是 `promptHash`（对 `pane.read` 文本归一化）+ `agent_status`。
- pi 的会话定位：`--session-dir` 时是**扁平目录**，`<ISO8601>_<uuid>.jsonl`；默认（不加 `--session-dir`）是否走 `--<cwd>--` 子目录 **未测**（不想往你的 `~/.pi` 里写样本）。
