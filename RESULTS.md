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

## 9.10 Q-4 / Q-2 / Q-1（2026-10-31 15:40–16:05）

### Q-4 streamlocal 对照（BLOCKED，两个独立原因）

| 项 | 结果 |
| --- | --- |
| Windows OpenSSH 客户端做 unix-socket `-L` | **不支持**：`Bad local forwarding specification 'C:/…/herdr-test.sock:/home/…/herdr.sock'` |
| sshj 0.41.2 的 `direct-streamlocal@openssh.com` | **没有实现**（jar 里只有 `DirectTCPIPChannel`，没有 streamlocal 相关类） |
| 服务端是否允许 streamlocal | **允许**：在主机本机 `ssh -N -L /tmp/herdr-loop.sock:<herdr.sock> localhost` + `socat` → `ping` 正常返回 |
| 顺带发现 | **同一个服务端的 capabilities 随连接方式变化**：直连 socket 时 `detached_server_daemon: true`，经 `remote-api-bridge` 时是 `false` |

结论：方案 B 在“我们的客户端栈”里 **BLOCKED**，不是服务端限制。要做只有两条路：给 sshj 写自定义 channel（估 1–2 天），或走方案 C（主机端复用脚本，需装脚本，需批准）。

### Q-2 events_lost（小规模尝试 → NOT TESTED，附量化估算）

做法：订阅后本地 25 s 不读取，同时在 `w4` 里做 6 次 split+close（30 个事件）。

| 结果 | 值 |
| --- | --- |
| 事件数 / 字节 | 30 个事件 / 10,477 B（≈1.7 KB/pane） |
| `events_lost` | **未出现**；恢复读取后 30 个事件**全部送达**（无丢失、无重放） |
| 结论 | 订阅能承受 25 s 级卡顿。要触发 `events_lost` 需要 >100 个 pane 量级（填满 socket 缓冲），属于压力测试，按你的约束**不做** → 恢复路径本轮 `NOT TESTED` |

证据：`spike/results/s2-events-lost-attempt.ndjson`。

### Q-1 交互式 pi：agent.prompt / agent.wait / 无锁（VERIFIED）

在 `w4:p1` 里启动 `pi --session-dir ~/spike-sandbox/pi-sessions`；用**直连 unix socket**（本地 loopback）测量，去掉 SSH 连接开销。

| ID | 项 | 结果 |
| --- | --- | --- |
| ON2-29 | herdr 对 pi 的识别 | `agent=pi`，`agent_session=null`（未装集成） |
| ON2-30 | `agent.prompt` | 返回 `{"type":"agent_prompted","agent":{…}}`，**301 ms** |
| ON2-31 | 状态轨迹 | `done`(残留) → **`working` @405 ms → `done` @4836 ms** |
| ON2-32 | `agent.wait` 条件已满足 | **2 ms** 返回 `agent_info` |
| ON2-33 | `agent.wait` 条件不满足 | **精确阻塞 3007 ms**（timeout_ms=3000）后返回 `{"error":{"code":"timeout","message":"timed out waiting for agent status"}}` |
| ON2-34 | **`agent.wait` 不提供互斥（P0-2 实证）** | pi 处于 `working` 时，另一个客户端的 `pane.send_text` + `send_keys(enter)` 在 **1 ms** 内被接受；同时第三个客户端的 `agent.wait` 持续 **13.3 s** 后才返回 `done`。→ 等待期间别人可以写，**没有锁** |
| ON2-35 | 经 `remote-api-bridge` 的 `agent.wait` 超时 | 3/3 次**没有任何输出**、连接被关；同一请求直连 socket 则明确返回 `timeout` 错误 → 传输层差异（`ISSUES.md` I-39） |
| ON2-36 | `agent_blocked` | **NOT TESTED**：需要诱导 pi 弹权限确认，未做（避免在共享机上产生需要审批的操作） |
| ON2-37 | 收尾 | `ctrl+c`×3 与 `/exit` 都没退出 TUI；最终 `kill` 掉自己的测试进程（pid 1012435），pane 回到 shell（`agent=None`） |

上一条的“状态轨迹”说明：**pi 完成后是 `done` 而不是 `idle`**；`agent.wait` 的 `until` 必须包含 `done`，否则会白等到超时。

## 9.11 R-1 / R-2 / R-3（2026-10-31 16:00–16:20）

### 关键前提更正：网络不是「局域网 1 ms」

`ping 192.168.5.8` = **RTT 50 ms（0% 丢包）**。之前写的“局域网”应改为「同网段 WiFi，RTT 50 ms」。
这解释了：单个 exec channel ≈ 200 ms、API 请求 ≈ 248 ms、SFTP 单次操作 ≈ 50 ms。

### R-1a 用 pi 诱导 `blocked`：**不可行**（NOT TESTED）

让 pi 执行 `touch ~/spike-sandbox/probe-r1.txt`：**pi 直接执行，没有确认**。
`agent_status` 时间线：`unknown` → `working`(2.7 s) → `done`(5.7 s)，**从未出现 `blocked`**。

→ 结论：pi 默认设置下不产生审批确认，无法用它验证 `blocked` 态与 L1。
要拿到 `blocked` 只有两条路：① 用 Claude（需登录/额度）；② 给 pi 写一个会调用 `ctx.ui.confirm` 的扩展（会写 pi 配置目录，需单独批准）。

### R-1b 合成 TUI 测 L2 读屏审批的竞态（VERIFIED，核心结论）

用沙箱里的 `~/spike-sandbox/scripts/fake-tui.sh`：循环显示 `=== Qn === / 1) Allow cmd-n / 2) Deny`，每轮 **0.05–1.5 s 随机换题**，记录“按键到达时屏幕上正显示哪一题”。
客户端直连 socket（loopback，`T2→T3` ≈ 1 ms）。

| 组 | 发送 | 二次校验拦截 | 送达时题号不符 | 错题率 |
| --- | --- | --- | --- | --- |
| CTRL（读屏后直接发，无二次校验） | 25 | 0 | **6** | **24.0%** |
| L2 本地（二次校验，窗口 B ≈ 1 ms） | 56 | 14 | **2** | **3.6%** |
| L2 模拟手机（二次校验 + 250 ms 模拟 RTT） | 50 | 20 | **17** | **34.0%** |

样本量小（每组 25–70 轮），不追求统计显著，但趋势明确：

1. 二次校验在本机把错题率从 **24% → 3.6%**；
2. **一旦窗口 B 变成手机的 250 ms，错题率反弹到 34%**，比不校验还糟——因为“校验通过”给了用户虚假信心；
3. 所以 **L2 读屏审批不能做成手机上的“一键批准”**。要么只做 L1（结构化审批），要么 L2 只保留“只读提示 + 引导到电脑”。
4. 合成 TUI 不产生 `agent_status=blocked`（herdr 不认识它），因此「状态前置条件」这一环本轮 **仍未验证**。

### R-2 pi JSONL 结构（VERIFIED，小样本）

一次带工具调用的会话（13 行，11 KB）：

| 行 `type` | 条数 | 说明 |
| --- | --- | --- |
| `session` | 1 | 会话头 |
| `model_change` | 1 | 模型切换 |
| `thinking_level_change` | 1 | 思考级别（文档未提，新增观察） |
| `message` | 5 | role = `system` / `user` / `assistant` / `toolResult` / `assistant` |

`message` 行的顶层字段：**`id`、`parentId`、`timestamp`、`type`、`message`** → 树结构 + 自带时间戳。
工具调用在 `message.content[]` 里以 `thinking` / `toolCall` / `toolResult` **分片**出现（不是独立行）。
`--session-dir` 下文件是**扁平**的 `<ISO8601>_<uuid>.jsonl`，没有 `--<cwd>--` 子目录。

### R-3 SFTP 实链（VERIFIED）

证据：`spike/results/s4-sftp-live.csv`。

| 步骤 | 结果 | 耗时 |
| --- | --- | --- |
| `stat` | `size=24, mtime=…, inode=null`；attrs 里**没有 inode** | — |
| 首次读取 3 行 | 3 行，末行 `{"n":3}` | 433 ms |
| 追加 2 行后 | 2 行，末行 `{"n":5}` | 718 ms |
| 截断到 10 字节 | `Reset(identity-changed)` ✅ | 359 ms |
| 截断后重读 | 1 行 `{"n":1}` | 423 ms |
| 替换成更大文件（200 行） | `Reset(identity-changed)` ✅ | 561 ms |
| 替换后重读 | 200 行，末行 `{"big":200}` | 506 ms |
| 尾部回填 50 行 | `{"big":151}` … `{"big":200}` | 225 ms |

发现：

1. **SFTP 真的可用**（sshj 的 `canonicalize`/`ls`/`stat` 都正常）。之前 stat 全失败是我这边的 **MSYS 路径转换**把 `/home/...` 变成了 `C:/Program Files/Git/home/...`（`MSYS_NO_PATHCONV=1` 后正常）——记进 `ISSUES.md` I-42。
2. **每次 poll 355–718 ms**：因为一次 poll 要做 `stat` + `headBytes` + `open` + `read` + `close`，在 50 ms RTT 下 ≈ 5–8 个往返。
   → 优化方向：**复用已打开的 RemoteFile、缓存 stat、去掉多余的 headBytes**（可用 (size, mtime) 先判同文件，只在可疑时读前缀）。
3. `FileAttributes` **没有 inode**，前缀判定是唯一可行路径（确认 I-15）。
4. 截断被判成 `identity-changed` 而不是 `truncated`（因为先查身份后查 size）；行为正确（Reset），只是原因字符串不同，记进 `ISSUES.md` I-43。

## 9.12 D-3① SFTP 往返次数优化（VERIFIED）

改动（`SftpTail` + `SftpByteSource`）：

1. **句柄缓存**：`readAt` 不再每次 open+close（省 2 个往返），`invalidate` 只在身份变化时丢弃。
2. **前缀只读一次**：稳态不再每次读前缀；只在「变短 / mtime 倒退 / 每 5 次 poll」时复检。
3. **续读重叠校验（关键安全项）**：每次读取向前多读 64 字节，与上次读到的末尾比对；不一致立即 `Reset`。
   → 修复了“更大文件替换”在优化后被当成正常追加、**输出了 199 行错误内容**的问题（0 额外往返）。
4. 截断不再丢弃句柄（同一文件）。

同一场景前后对比（RTT 50 ms，`s4-sftp-live.csv` vs `s4-sftp-live-optimized.csv`）：

| 步骤 | 优化前 | 优化后 |
| --- | --- | --- |
| 首次读 3 行 | 433 ms | 382 ms |
| 追加 2 行 | **718 ms** | **195 ms** |
| 截断（Reset） | 359 ms | **145 ms**（原因也从 `identity-changed` 修正为 `truncated`） |
| 截断后重读 | 423 ms | 318 ms |
| **替换成更大文件** | 561 ms（Reset） | **250 ms（Reset，且不再输出错误行）** |
| 替换后读 200 行 | 506 ms | 475 ms |
| 回填 50 行 | 225 ms | **123 ms** |

单元测试：**23/23 通过**（新增 1 个用例：稳态 poll 不得重复读前缀，用计数 `ByteSource` 断言）。

结论：稳态单次 poll 从 ~700 ms 降到 **~195 ms（≈2 个往返）**；对“轮询 250 ms”来说仍然偏紧，建议产品按 **500 ms–1 s** 自适应轮询，并把“身份复检”当作低频操作。

## 9.13 S-2 冷启动优化（VERIFIED）

首次 poll 不再主动读前缀：**从第一次读取的缓冲里顺手取前 512 字节**，作为后续身份比对的前缀。
省掉 `headBytes`（open+read+close ≈ 3 个往返）。

| 步骤 | S-2 前 | S-2 后 |
| --- | --- | --- |
| 首次读 3 行（冷） | 382 ms | **171 ms** |
| 截断后重读 | 318 ms | **106 ms** |
| 替换后读 200 行 | 475 ms | **294 ms** |
| 追加 / 截断 / 替换 / 回填 | 195 / 146 / 249 / 123 ms | 不变 |

单测 23/23 通过；「稳态与首轮都不得调用 `headBytes`」由计数 `ByteSource` 断言。
优化后一次 poll = `stat`(1) + `read`(1) = **2 个往返**。
