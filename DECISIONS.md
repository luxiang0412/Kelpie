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

## D17. 屏读审批只用 `promptHash`，不依赖 `revision`

**决策**

- `pane.read.revision` 实测恒为 0（内容变化也不变），`pane.get.revision` 只在元数据变化时变一次 → **`revision` 不能用来判断屏幕内容是否变化**。
- L2 的判据固定为：`promptHash = hash(归一化问题文本 + 选项 + 每项命令正文)`，外加 `agent_status == blocked` 与 `(pane_id, terminal_id, agent)` 三元组未变。
- 任何“revision 变了但哈希没变可以继续”的写法从文档中删除。

**依据**：`RESULTS.md` ON2-20；`ISSUES.md` I-35。

**状态**：`已定`。

## D18. 写操作前必须按 schema 校验参数名

**决策**

- 客户端发请求前用内置 schema 校验参数（字段名、类型、枚举），**未知字段直接报错不发**。
- 起因：`pane.split` 的正确字段是 `target_pane_id`，传 `pane_id` 被静默忽略并 split 到焦点 pane，误改了用户 workspace（已恢复）。
- 所有写操作日志记录“请求的 pane”与“服务端响应里实际生效的 pane”，不一致就告警。

**依据**：`RESULTS.md` ON2-21 与误操作记录；`ISSUES.md` I-34。

**状态**：`已定`。

## D19. pi 作为本阶段的结构化数据源（替代 Claude）

**决策**

- Spike 3/4 主数据源改为 **pi 1.4.0**：`pi --session-dir ~/spike-sandbox/pi-sessions`（扁平目录，可整体清理），不碰 `~/.pi` 配置、不装 `herdr integration install pi`。
- 已知限制：不装集成时 herdr 的 `agent_session` 为 `null`，只能按 `foreground_cwd` + 目录/文件 mtime 定位；pi 退出后 herdr 立即丢失 agent 身份（`agent=null`）。
- Phase 3 若要 `agent_session`，再评估装集成（会写 `~/.pi/agent/extensions/`，需单独批准）。

**依据**：`RESULTS.md` ON2-25…ON2-28。

**状态**：`已定`（本阶段）。

## D20. 方案 B（streamlocal）暂不投入；先做“减少请求数”

- 服务端支持 streamlocal，但 Windows OpenSSH 客户端与 sshj 0.41.2 都不支持 unix-socket 直连。
- 要落地需要给 sshj 写自定义 channel（估 1–2 天）或主机端复用脚本（方案 C，需装脚本/批准）。
- 决策：**Phase 1 不做方案 B**；先把“每请求 248 ms”压下去的手段放在：① 事件驱动（少轮询）② SFTP 复用 ③ 批量读取（一次 `snapshot` 覆盖多个 pane）。
- 若 Phase 2 仍有 >200 ms 的体感问题，再评估方案 B/C。

**状态**：`已定`。

## D21. `agent.wait` 只当“带超时的阻塞查询”，不当锁、也不当完成通知的唯一来源

- 实测：条件满足时 2 ms 返回 `agent_info`；不满足时精确阻塞 `timeout_ms` 后返回 `error.code=timeout`（直连）；**条件不满足时不会排斥其他写者**（working 期间另写被 1 ms 接受）。
- 用法约束：① `until` 必须包含 `done`（pi 完成后是 `done`，不是 `idle`）；② 超时一律视为“没等到”，不得推断成败；③ 完成检测以 `done`/`idle` 事件 + `agent.get` 为准，`agent.wait` 只做辅助。
- 经 `remote-api-bridge` 时超时无响应（I-39），客户端要按 `Unknown` 处理。

**状态**：`已定`。

## D22. L2 读屏审批不做「手机一键批准」

- 实测（合成 TUI，50 ms RTT 环境，窗口 B 用 250 ms 模拟）：二次校验把错题率从 24% 降到 3.6%，但**窗口 B 一变成 250 ms，错题率反弹到 34%**。
- 决策：
  1. **手机端禁止「一键批准」**。L2 只允许两种形态：① 只读提示（“这台 agent 在等你”，引导到电脑）；② 展示完整命令 + 二次校验 + 明确的“我确认批准”二次动作，并在 UI 上标注残余风险与实测错题率。
  2. 真正的审批能力放在 **L1 结构化审批**（Claude `PermissionRequest` hook / ACP / app-server）。没有 L1 的 agent，Phase 2 **不提供审批**，只提供只读与“跳转到电脑”。
  3. 若将来做 L2，前置条件是 **窗口 B ≤ 100 ms**（需要方案 B/C 或更少往返），否则不做。
- **状态**：`已定`。这是本轮最重要的产品级结论。

## D23. 网络基线按 RTT 50 ms 记账，不按「局域网 1 ms」

- `ping` 实测 50 ms；所有延迟目标改用这个基线：
  - 单 exec channel ≈ 200 ms（≈ 4 个往返 + 进程启动）
  - 复用连接上单请求 ≈ 248 ms
  - SFTP 单次操作 ≈ 50 ms；一次 poll ≈ 350–720 ms（往返太多）
- 因此 Phase 1 的“状态变化 → 手机更新 P95 < 1 s”仍然可行（事件驱动 + snapshot），但“每请求 < 300 ms”只是临界达标，**L2 审批的窗口无法靠现有 Transport 降到 100 ms 以内**。

**状态**：`已定`。

## D24. SFTP 读取策略：先修往返次数，再谈轮询频率

- 实测一次 poll 355–718 ms。在修掉多余往返（复用句柄、缓存 stat、减少 headBytes）之前，**不要把轮询调到 250 ms**——那只会制造排队。
- 目标：单次 poll ≤ 2 个往返（stat + read），即 ≈ 100 ms；达到后再谈 250 ms/1 s 的自适应轮询。

**状态**：`已实现`（2026-10-31）。稳态 poll 718 ms → **195 ms**；并新增“续读重叠校验”保证替换不被误当追加（`RESULTS.md` §9.12）。

## D25. SFTP 读取的三条硬约束（实现层）

1. **句柄必须缓存**（`open/close` 各占 1 个往返）；
2. **前缀只做“可疑时 + 周期”复检**，不得每次 poll 都读；
3. **每次读取必须带 64 字节重叠校验**，否则“文件被替换”会被误判成追加，输出错误内容。

**依据**：`RESULTS.md` §9.12；回归见 `ISSUES.md` I-46。

**状态**：`已定`（已实现 + 单测）。

## D26. 连通性诊断用 TCP + SSH banner，不用 ICMP

- 实测：手机 `ping 192.168.5.8` 100% 丢包，但同一时刻 `TCP 22` 55 ms 可达、banner 正常。
- 决策：连接向导与错误提示按“DNS → TCP connect（带端口）→ SSH 握手/banner → 主机指纹 → 认证”分层报告；**不把 ping 结果当结论**。
- 备用路径：当直连不可达时，支持用户配置跳板/隧道（测试期用过 `adb reverse` + `ssh -L`）。

**状态**：`已定`。

## D27. Android 上的 sshj 初始化顺序（硬性）

1. `Security.removeProvider("BC")` → `Security.addProvider(BouncyCastleProvider())`（APK 内置 `bcprov-jdk18on`，版本需与 sshj 传递依赖一致）；
2. `SecurityUtils.setRegisterBouncyCastle(true)` → `SecurityUtils.registerSecurityProvider("BC")`；
3. `DefaultConfig.keyExchangeFactories` 只保留 `curve25519-sha256`、`curve25519-sha256@libssh.org`、`ecdh-sha2-nistp256/384/521`、`dh-group14-sha256`、`dh-group16-sha512`、`dh-group-exchange-sha256`；
4. 私钥用 `OpenSSHKeyV1KeyFile` 读取（OpenSSH 格式），RSA/PKCS#1 直接拒绝并提示重新生成。

**依据**：`RESULTS.md` §9.15；`ISSUES.md` I-48。

**状态**：`已定`（真机验证通过）。

## D28. 手机端延迟预算修正

- 真机实测（直连、RTT 55 ms）：exec 331 ms、`ping` ≈416 ms（P50 ~390）。
- Phase 1 指标改为：**单请求 P95 < 500 ms**（原 <300 ms 只在 PC 上成立）；状态更新 P95 < 1.5 s。
- UI 必须事件驱动 + 批量读取（一次 snapshot 覆盖多 pane），禁止逐 pane 轮询。

**状态**：`已定`。

## D29. 移动端刷新策略：事件驱动 + snapshot 批量，轮询 ≥ 1 s

- 实测（真机）：snapshot 384 ms、事件随请求同步到达（≈RTT）、SFTP 单次 poll 353 ms。
- 决策：
  1. 看板/会话列表**只靠事件触发刷新**（`dirty` 标记 + 串行 snapshot），不做定时全量轮询；
  2. 确实需要兜底轮询时，间隔 **≥ 1 s**（活跃 1 s / 空闲 5 s），且一次刷新覆盖所有 pane；
  3. 任何“每 pane 一个轮询”的设计一律不做。

**依据**：`RESULTS.md` §9.16；`ISSUES.md` I-51。

**状态**：`已定`。

## D30. `pane_id` / `tab_id` / `workspace_id` 当作不透明字符串

- 实测 pane 序号是 **base36**（`w4:pA`），且 herdr 明确不保证可解析。
- 决策：禁止 `toInt()`、禁止按数字排序、禁止用 `\d+` 正则提取；需要稳定排序时用事件到达顺序或 `terminal_id`。
- 客户端存储/比较/日志全用字符串等值。

**依据**：`RESULTS.md` §9.16（I-50）。

**状态**：`已定`。

## D31. Milestone 1 的模块与数据流边界

- `:core:ssh` 只负责“连接 + 指纹 + 通道限流”，不认识 herdr 语义；
- `:core:herdr` 负责 NDJSON、订阅、对账（Reconciler）与连接代次（Supervisor）；
- `:core:data` 只做缓存与设置（Room + SharedPreferences），不发起网络请求；
- `:feature:board` 只依赖 `:core:*`，通过单一 `BoardViewModel` 暴露 `StateFlow<UiState>`；UI 只发 Action。
- 事件流：SSH → `HerdrApi.subscribe` → `SessionReconciler.onEvent`（仅置脏）→ 串行 `snapshot` → Room → UI。
- 连接代次：每次成功连接 `generation+1`；旧代次的回调一律丢弃（避免重连期间的状态串台）。

**状态**：`已定`（M1 实现已按此落地）。

## D32. Android 上的 BouncyCastle：优先“委托 provider”，不删系统 BC

- 方案：把 APK 内的 `BouncyCastleProvider` 包成名为 `BCAPP` 的委托 Provider（`getService` 转发），
  `SecurityUtils.registerSecurityProvider("BCAPP")`；**不动系统 `BC`**，避免影响同进程 TLS。
- 备选：`removeProvider("BC")` 后注册新版（已证明可行，但有全局副作用）。
- 采用哪个，取决于 `CryptoSpikeActivity` 的 A1/A2 结果（设备解锁后跑）。

**状态**：`待实测确认`（A1 通过则采用委托方案）。

## D33. SSH 库选型：M1 继续 sshj，Spike A 完成后重新评估

- 事实：JSch 的 exec 通道约快一倍；JSch 支持已授权 RSA，sshj 不支持；Android 上 JSch 还可能省掉 BC 处理。
- 决策：**M1 不换库**（已实现并测试），但在 Phase 2 之前用 Android 侧数据做一次正式对比；若差距稳定且 JSch 在 Android 14 上 Ed25519/SFTP 均通过，则考虑切换。

**状态**：`待实测确认`。

## D32（定稿）：Android 上用“委托 provider”，不删系统 BC

- 实测 A1：把 APK 内 BC 包成 `BCAPP`（`getService` 转发），`sshj_provider=BCAPP`、`bcapp_x25519=true`，认证与 exec 均正常；
  系统 `BC` 保持不变（`providers_after` 里 BC 与 BCAPP 并存）。
- A2（removeProvider）也能用，但有全局副作用。
- **决策：采用 A1**；`SshCrypto.ensureInitialized()` 的注册顺序固定为：
  `addProvider(BCAPP 委托)` → `setRegisterBouncyCastle(true)` → `setSecurityProvider("BCAPP")`（失败再 `registerSecurityProvider`）。

**状态**：`已实现 + 真机验证`。

## D33（定稿）：M1 保持 sshj；JSch 作为 Phase 2 性能备选

- Android 实测：JSch exec P50 **120 ms**，sshj **237 ms**；JSch 无需 Provider 操作、SFTP 正常、指纹可自算。
- 但 M1 的 sshj 路径已端到端验证通过（连接/快照/订阅/看板），换库会带来回归成本。
- **决策**：M1 不换；Phase 2 若“每请求延迟”成为瓶颈，优先评估 JSch（并复测 SFTP/主机指纹/并发）。

**状态**：`已定`。

## D34. Stale 语义：按“连接存活”判定，而不是“多久没刷新”

- 空闲会话本来就没有事件（D29 事件驱动），用“N 秒没刷新”判过期会永远误报（M1 首版实测到）。
- 修正：每 25 s 发一次 `ping` 探活；失败 → Stale（并触发重连）；成功且当前是 Stale → 恢复 Connected。
- 数据新鲜度另外用 `fetchedAtMs` 展示（“缓存数据 · 更新于 …”）。

**状态**：`已实现`。

## D35. pi 集成可在隔离 HOME 下安装（Spike B 结论）

- `herdr integration install pi` 会遵循 `$HOME`；在 `HOME=~/spike-sandbox/pihome` 下安装只会写沙箱，不动全局。
- 装上后 `agent_session` 返回 `{source:"herdr:pi", kind:"path", value:"…jsonl"}`，可作为 transcript 的权威定位。
- 决策：Phase 2 优先用 `agent_session.path` 定位 JSONL；`--session-dir` 扁平目录作为可控补充。

**状态**：`已定`（本阶段）。

## D36. transcript 视图：先做“尾部快照”，M3 再做增量

- M2 只读视图用 **SFTP 读文件末尾 256 KiB + 丢弃半行 + pi JSONL 解析**，不做增量 tail、不写 Room。
- 理由：先用最小成本验证“`agent_session.path` → 可读对话”这条链路（已真机验证），
  避免在性能基线没做完时引入增量缓冲、缓存一致性和 Markdown 渲染的复杂度。
- M3 计划：移植 `SftpTail`（D25 的三条硬约束）做增量 + Room 缓存 + 虚拟列表 + 代码高亮。
- `agent_session` 必须从 snapshot 的 **agents[]** 合并（I-62）。

**状态**：`已定`（M2 范围）。

## D37. 多主机先做“并行连接 + 单主机视图”

- 每台配置了私钥的主机都会建立独立连接（各自 Supervisor）；看板一次只显示一台（chips 切换）。
- 不做的：跨主机聚合看板、按“等你处理”总数排序、连接池上限的总量控制（主机数多了会同时占通道）。
- 若 M3 要支持 >3 台主机，需要引入“只连活动主机 + 其余按需唤醒”的策略（否则通道/电量都要重新算）。

**状态**：`已定`（M2 范围）。

## D38. transcript 的 M3 形态：2 s 轮询尾部快照，不做 offset 增量

- 原因：offset 增量需要移植 `SftpTail`（含文件身份/截断/替换/偏移恢复，D25），成本明显高于收益；
  而 256 KiB 尾部快照在真机上一次约 200–400 ms，2 s 一次对电量/流量都可接受。
- M4 再升级：`SftpTail` 增量 + Room 缓存 + 只读已缓存历史（离线可看）。
- 轮询只在**对话页可见时**运行，退出即取消（不后台耗电）。

**状态**：`已定`。

## D39. transcript 增量：以“完整行”为消费单位，cursor 落 Room

- 只在换行处推进 offset（`JsonlChunker`），因此 **UTF-8 不会被截断**、半行下次重读；
- cursor = `(offset, headHash)`，`headHash` 是文件头 64 字节 SHA-256：变化即视为“文件被替换”，清空重读；
- 首次打开只回填尾部 256 KiB；之后单次读取上限 1 MiB；
- SFTP 句柄在 tailer 生命周期内复用，退出对话页即 close；
- 消息落 Room（`hostId, path, seq`），UI 只订阅 Room → 离线可看、重进秒开。

**状态**：`已实现 + 真机验证`。

## D40. M3-2 结论：性能工作推迟到 M4，先建可控测量

- 看板已达标（jank 0.9%）；长对话滚动是瓶颈（30–44%），但当前“`input swipe` + `gfxinfo`”的测法不可信（I-63），继续盲调没有意义。
- 决策：M4 第一步建 `:benchmark`（Macrobenchmark + Baseline Profile + R8），用可控负载建立基线，再决定优化手段（文本 measure 缓存 / 分页 / 渲染降级）。
- 在此之前不改动 transcript 的渲染结构（保持简单、可读）。

**状态**：`已定`。
