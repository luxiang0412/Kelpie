# DECISIONS — Phase 0 已确认的技术决策

> 每条决策标注**依据**（文档 / schema 证据 / 单测）和**状态**：
> `已定`（有充分依据，可直接实现）/ `待实测确认`（方向已定，数值或细节在线验证后可能调整）。

## D1. 主机指纹：显式确认 + fail-closed，禁止 TOFU

**决策**

- 首次连接**只做 KEX 拿 host key**，展示 `算法 + SHA256 指纹 + host:port`，要求用户带外核对后显式确认；确认前**不发送任何凭据**。
- 未知主机 → 抛 `HostKeyUnknownException` 并连接失败，**绝不**把指纹顺手写盘。
- 已固定主机指纹不匹配（含算法变化）→ `HostKeyMismatchException`，必须用户手动删除记录才能继续，不提供“忽略一次”。
- 信任库按 `(host, port, ssh 算法)` 存；算法名从 SSH wire blob 解析，不依赖 `getAlgorithm()`。
- 不读系统 `~/.ssh/known_hosts` / `~/.ssh/config`。

**依据**：P0-1 修订；`spike/jvm/.../trust/HostKeyTrust.kt`；单测 F1–F5 + sshj 适配层测试全绿；sshj 0.41.2 的 `HostKeyVerifier` 签名已编译验证。

**状态**：`已定`（实现层）。真实 sshd 全流程为 `待实测确认`。

**被否决的方案**：TOFU（原任务书写法）、`StrictHostKeyChecking=no` 等价行为、只比对指纹不比对算法、把指纹存进日志。

## D2. 写操作：三态 + 永不自动重试 + 没有锁

**决策**

- 写操作结果只有 `Accepted` / `Rejected(code,message)` / `Unknown`；`Unknown` **不允许**自动重试，也**不允许**自动升级为成功。
- 只有权威读取拿到**正向证据**才升级为 `Accepted`；“确认没送达”仍然是 `Unknown`（保留证据串）。
- 人工重发必须用新的 `clientRequestId`，旧记录保留为 `Unknown`，审计里能看到两次独立尝试。
- 重连（sshj 指数退避）只重建通道 + 对账，**不重放**任何排队中的写请求。
- 客户端内部对同一 pane 的写操作做 per-pane Mutex 串行化，但这只是 **advisory**，不是跨客户端互斥。

**依据**：P0-2 / P0-5；0.9.3 schema 显示 `agent.prompt` / `pane.send_keys` / `pane.send_text` 无前置条件、无幂等键；`WriteLedger` 6 个单测。

**状态**：`已定`。

## D3. `agent.wait` 不是锁，任何“加锁”承诺一律取消

**决策**：删除 v2 中“`agent.wait` 锁定 pane 占用者”的说法；不基于 `agent.wait` 做互斥、不做“已锁定”UI；需要互斥时只能走 L1 结构化审批的请求 id 语义。

**依据**：`AgentWaitParams` = `{target, timeout_ms, until}`，无锁字段（schema 证据）；P0-2。

**状态**：`已定`（静态事实）。并发实证（S2-L7）为 `待实测确认`，但不影响决策方向。

## D4. 审批：L1 结构化优先，L2 只作兜底且禁止绝对安全宣称

**决策**

- 产品与文案层面：L2 是“缩短风险窗口”，不是“消除风险”。禁止“绝对安全 / 不会答错 / 0 次答错”。
- L2 必须保留：二次读取 + 哈希比对、新选项 500 ms 冷却、风险操作长按、默认不批准、完整审计。
- L2 的验收改为三项可度量指标：**拦截率**（哈希不一致时 100% 中止）、**窗口 B 上限**（T2→T3：局域网 P95 < 300 ms，4G < 1 s）、**无静默批准**（100%）。不再使用“0 次答错”。
- Phase 2 的完成标准同步修改；真实 Claude 上 L2 若错题率不可接受，则只做 L1 + L3。

**依据**：P0-3；`pane.send_keys` 无 `if_revision`（源码 + schema）；时序图分析见调研 v2。

**状态**：`已定`（口径与验收）。错题率具体数值 `待实测确认`。

## D5. L1 PermissionRequest Hook 的文件 IPC 契约

**决策**（细节见调研 v2「L1 结构化审批的 Hook 契约」）

- 请求身份：`request_id`（ULID）+ `nonce` + `tool_input_sha256` + `session_id` + `tool_use_id` + `herdr_pane_id`；客户端展示前必须校验 `tool_input_sha256`。
- 过期：hook 侧 TTL 120 s（远小于官方 10 分钟 hook 超时）；过期 → 退出码 0、**不输出 decision**，交回终端提示；客户端同样在 TTL 后禁止提交。
- 取消：`.cancel` 文件（带 nonce），hook 立即退出；App 被杀只靠 TTL 兜底，所以 TTL 必须小。
- 并发：每请求一个目录 `approvals/<request_id>/{req,answer,cancel}`，hook 只等自己的 id，客户端只对可见的那个 id 提交，并发上限 8。
- 信任边界：`0700`/`0600`、原子写、回答读一次即删、nonce/哈希/TTL 任一不符即丢弃；明确“安全性 = SSH 账号安全性”，不宣称防主机被攻破。
- 安装：优先专用 `CLAUDE_CONFIG_DIR`，改前备份、改后提供一键卸载。

**依据**：P0-4；Claude Code hooks 文档（`PermissionRequest`、10 分钟超时）。

**状态**：`已定`（契约）。`hook 超时无输出时 Claude 的默认行为`（调研 v2 #26）为 `待实测确认`，这是契约里唯一可能在实测后需要调整的点。

## D6. SFTP Tailer：按契约实现，禁止“整文件读 + 字符串解码”

**决策**

- 只按字节缓冲，遇到 `\n` 才增量解码（`CodingErrorAction.REPLACE`，统计非法字节）。
- 文件身份：inode/device 可得时用 inode；否则用**前缀保持不变 + 文件未变短**判定（**不能用“前 N 字节哈希”**，文件变大时它会变，从而把正常追加误判成替换）。
- 截断（size < offset）→ Reset；身份变化 → Reset；`stat` 失败（文件被删/会话断）→ 视为会话丢失并重连，**不**当成 Reset。
- 偏移恢复：持久化 `offset + lastLineStart/lastLineEnd + 行哈希`，恢复时回读校验，不一致才 Reset。
- 大文件：尾部按块回填，受 `backfillMaxBytes`（默认 32 MiB）约束；单次读 1 MiB，单行 1 MiB 上限。
- 短读必须循环；轮询单协程串行，禁止重叠 poll。

**依据**：P0-6；`SftpTail` + 10 个单测（UTF-8 跨块、非法字节、截断、更大文件替换、偏移恢复、恢复校验失败、半行、短读、大文件读预算、行边界）。

**状态**：`已定`。SFTP `stat` 是否返回 inode（调研 v2 #27）`待实测确认`；不返回时前缀判定的局限见 `ISSUES.md` I-5。

## D7. 团队协作：不做互斥承诺（P0-7）

**决策**：Phase 1–3 不提供 presence/lease/ownership 的互斥能力；最多做“advisory 提示”（基于事件 + 自愿上报），文案必须写明不是锁；主机端协同文件（A）与协调服务（B）不上 roadmap，等真实团队用户反馈后再评估。“团队场景”从差异化方向降级为假设。

**依据**：0.9.3 的 102 个方法中 `presence`/`lease`/`owner`/`lock` 出现次数为 0（schema 证据）；`agent.wait` / `pane.read.revision` 都不构成锁。

**状态**：`已定`。

## D8. 能力协商以 schema 为准，`ping.capabilities` 只当特性开关

**决策**：客户端内置“herdr 版本/协议号 → 方法集”的映射（由 `herdr api schema --json` 生成），运行时校验 `ping.protocol` 兼容性；`ping.capabilities` 只用于开关实验性特性（如 `live_handoff`、`ssh_agent_registration`）；未知方法/未知错误码一律优雅降级，不崩溃。

**依据**：F-1；`ServerCapabilities` 字段清单；`ErrorBody.code` 无枚举。

**状态**：`已定`。

## D9. 订阅模型：全局生命周期 + 逐 pane 状态，双层维护

**决策**：全局订阅只订阅 `pane.created/closed/updated/exited`、`tab.*`、`workspace.*`、`layout.updated`；`pane.agent_status_changed` **逐 pane** 订阅，收到 `pane.created` 后补订阅、`pane.closed` 后退订。事件仍然只当失效信号，由串行 Reconciler 全量对账。

**依据**：F-2；`Subscription` oneOf 的 required 字段。

**状态**：`已定`（结构）。补订阅的时序细节 `待实测确认`。

## D10. 本轮不做的决定（明确留空）

| 事项 | 为什么现在不定 | 什么时候定 |
| --- | --- | --- |
| 通道方案 A / B / C | 缺在线延迟与 MaxSessions 数据（S1-L2/L3/L4） | Spike 1 在线完成后 |
| SFTP 轮询间隔（250 ms / 2 s） | 缺端到端延迟与耗电数据 | Spike 4 |
| L2 是否进 Phase 2 | 缺错题率 | Spike 5 |
| Android 后台策略 A/B/C | 缺真机数据 | Spike 6 |
| 是否引入 herdr-acp / ACP | 需装 Node.js 且需评估稳定性 | Spike 8 |

---

# 本轮（在线只读验证）新增与更新

## D11. 0.9.0 上不能用 `remote-api-bridge`，传输层改为 socat/streamlocal

**决策**

- 目标主机 herdr 为 **0.9.0**，**没有** `remote-api-bridge` 子命令（`unknown command`）。文档里“每请求一个 exec channel 跑 `remote-api-bridge`”的方案 A 在该主机**不成立**。
- 客户端实现必须把“如何连到 `herdr.sock`”抽象成可替换的 Transport：
  1. `ExecBridgeTransport`：exec `remote-api-bridge`（≥ 支持该子命令的版本）；
  2. `ExecSocatTransport`：exec `socat - UNIX-CONNECT:<sock>`（0.9.0 可用，已确认 `socat`/`nc` 存在）；
  3. `DirectStreamLocalTransport`：`direct-streamlocal@openssh.com` 直接转发到 socket（不占 MaxSessions，待测）。
- 启动时先探测：`remote-api-bridge --check` → 成功用 1；失败则检测 `socat`/`nc` 用 2；再失败提示用户升级 herdr。
- **不升级主机 herdr**（用户明确禁止）；探测结果写入审计日志。

**依据**：`RESULTS.md` ON-10；`spike/results/environment-report.md` §2。

**状态**：`已定`。方案 3 的可行性仍 `BLOCKED`（需 sshj 可用 + 会话 socket）。

## D12. 指纹必须用 SSH wire blob 的 SHA256，不能用 sshj 的便捷方法

**决策**

- 指纹定义：`SHA256:` + Base64(无 padding) over `Buffer.PlainBuffer().putPublicKey(key).compactData`，与 `ssh-keygen -lf` 逐字符一致。
- 禁止使用 `java.security.PublicKey.getEncoded()`（sshj 的 Ed25519 返回 **X.509**，实测解析得到荒谬的 `bad key blob length 808071173`）。
- 禁止使用 `SecurityUtils.getFingerprint()`（它是 **MD5**）。
- SSH 算法名从 wire blob 的第一个 length-prefixed string 解析；解析失败才退回 `KeyType.fromKey(key).toString()`。

**依据**：`RESULTS.md` ON-5；真机 F1–F4 全通过。

**状态**：`已定`（已实现 + 真机验证）。

## D13. sshj 作为 SSH 库：暂不切换，但设为 Phase 1 的前置风险项

**决策**

- 本轮 sshj 0.41.2 对该主机的 **RSA** 公钥认证失败（`Exhausted available authentication methods`），而同一密钥用 OpenSSH 客户端可登录；已排除“密钥未授权”“密钥解析错”“host key 校验”三种可能。
- **暂不更换 SSH 库**（换库成本高，且失败原因未定位）。但把它列为 Phase 1 的**前置门槛**：在通过 Ed25519 复测之前，不开始 Phase 1 的 SSH 层实现。
- 复测顺序（见 `NEXT-STEPS.md`）：① Ed25519 专用密钥（需授权写入 `authorized_keys`，需批准）；② 若 Ed25519 成功 → 产品本来就只用 Ed25519，风险降级；③ 若仍失败 → 评估 Apache MINA SSHD / JSch 做一次最小对照。
- 在 Android 上额外测一次（JVM 通过不等于 Android 通过，Keystore/BouncyCastle 行为不同）。

**依据**：`RESULTS.md` ON-12；`spike/results/s1-sshj-auth-debug.log`。

**状态**：`待实测确认`（决策是“先修不换”）。

## D14. 共享主机的实验纪律（写进任务书）

**决策**

- 该主机是**共享桌面环境**（桌面登录中、`herdr default` 会话在跑其他项目、9 个登录会话）。因此：
  - 只允许 `herdr --session test`；**禁止** attach/操作 `default`、`ljn`；
  - 禁止创建/关闭大量 pane（不做压力测试）；
  - 禁止 `sudo`、禁止改 `sshd_config`、禁止动系统防火墙；
  - 测试目录只允许 `~/spike-sandbox`，创建前 `test -e` 检查；
  - Claude 相关实验只用 `CLAUDE_CONFIG_DIR=~/.claude-spike`，**不改 `~/.claude`**；
  - 任何需要写远端状态的动作先列清单、等批准。
- 所有远程命令使用**绝对路径** `/home/luxiang/.local/bin/herdr`（非交互 PATH 不含 `~/.local/bin`）。

**依据**：`spike/results/environment-report.md` §1、§2、§5。

**状态**：`已定`。

## 本轮之后既有决策的状态

| 决策 | 变化 |
| --- | --- |
| D1 指纹 fail-closed | 状态升级为 **真机 VERIFIED**（F1/F2/F3/F4 全通过） |
| D2 写操作三态/不重试 | 不变（离线单测 VERIFIED，在线 NOT TESTED） |
| D8 能力协商以 schema 为准 | 补充：0.9.0 的 capabilities 只有 5 项，进一步证明“不是方法清单” |
| D9 双层订阅 | 不变；0.9.0 的 `Subscription` required 与 0.9.3 完全一致 |
| D10 通道方案 A/B/C | 新增约束：A 在 0.9.0 需换成 socat 变体（D11） |

---

# ONLINE-2 之后的决策更新（2026-10-31）

## D11 更新：`remote-api-bridge` 现在可用，但保留 Transport 抽象

- 目标主机已升到 **0.9.5**，`remote-api-bridge --check` 返回 `herdr-api-bridge-v1`，方案 A 恢复可用。
- 仍然保留三种 Transport 与启动探测（D11 原文），因为：
  1. 版本会变（本次就在两天内从 0.9.0 变到 0.9.5）；
  2. `remote-api-bridge` 仍是隐藏子命令，不保证长期存在；
  3. 0.9.0 的 socat 回退路径已经验证可行（`socat`/`nc` 存在）。
- **状态**：`已定`（探测顺序不变）。

## D15. 连接必须复用；并发上限按 4 记账

**决策**

- 客户端**必须**维持单条长连接并复用 exec channel。实测：不复用 = **1181 ms/请求**（P50），复用 = **247.7 ms/请求**。
- 每请求成本拆解：exec channel ≈ **201 ms**，herdr 侧 ≈ **46 ms**。要再降只能换 Transport（streamlocal 或主机端复用脚本），不能靠优化 JSON。
- 有效并发上限观测为 **9**（第 10 个 channel `open failed`，连接不断）。因此：
  - 客户端**短请求并发上限 4**；
  - 订阅、SFTP、终端观察各占 1；
  - 预留 1 给偶发探测，永不把 9 用满。
- 超限策略：排队 + 指数退避（200 ms 起，上限 2 s），**不重连、不重发写请求**。

**依据**：`RESULTS.md` ON2-7…ON2-11。

**状态**：`已定`。

## D13 更新：sshj 选型风险从「高」降为「中」

- RSA 仍 **FAILED**；**Ed25519 VERIFIED**（5/5，connect+auth ≈ 500 ms）。
- 产品只用 Ed25519，因此 **继续用 sshj**，但增加两条硬性要求：
  1. App 只生成/接受 Ed25519（OpenSSH 格式），对 RSA/PKCS#1 直接给可读错误；
  2. **Android 真机必须复测**同一套 `--mode connect` 逻辑（JVM 通过 ≠ Android 通过）。
- 仍保留“若 Android 上 Ed25519 也失败 → 换 MINA SSHD/JSch”的备选。

**状态**：`已定`（选型），Android 侧 `待实测确认`。

## D16. 版本必须运行时探测，不能假设

- 目标主机在 48 小时内从 0.9.0 变成 0.9.5（非本会话所为），说明主机侧版本不受我们控制。
- 客户端启动流程固定为：`ping` → 读 `version`/`protocol`/`capabilities` → 与本机“版本→方法集”表比对 → 未知版本进入**保守模式**（只读功能，写操作隐藏）。
- 所有远端命令用绝对路径（`/home/luxiang/.local/bin/herdr`），不依赖 PATH。

**状态**：`已定`。
