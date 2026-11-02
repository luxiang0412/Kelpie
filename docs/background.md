# 背景：代码注释里引用的旧决策与旧问题

旧版客户端（原 `android/`，已删除）的 `DECISIONS.md`、`ISSUES.md`、`RESULTS.md` 已从仓库删除（2026-11-03）。Kelpie 代码注释里还引用着其中一些编号：`Dn` 指决策，`I-n` 指问题，`RESULTS §9.x` 指实测结果。这里原样摘录被引用的条目，方便查阅。

完整原文在 git 历史里，例如 `git show signal-v0.2.0:DECISIONS.md`。

摘录里提到的 `docs/evidence/…` 证据文件也已删除，在同一个 git 历史里。摘录**不再更新**。新的决策写进 Kelpie 自己的文档（本目录）。

## 决策（DECISIONS.md）

### D1. 主机指纹：显式确认 + fail-closed，禁止 TOFU

**决策**

- 首次连接**只做 KEX 拿 host key**，展示 `算法 + SHA256 指纹 + host:port`，要求用户带外核对后显式确认；确认前**不发送任何凭据**。
- 未知主机 → 抛 `HostKeyUnknownException` 并连接失败，**绝不**把指纹顺手写盘。
- 已固定主机指纹不匹配（含算法变化）→ `HostKeyMismatchException`，必须用户手动删除记录才能继续，不提供“忽略一次”。
- 信任库按 `(host, port, ssh 算法)` 存；算法名从 SSH wire blob 解析，不依赖 `getAlgorithm()`。
- 不读系统 `~/.ssh/known_hosts` / `~/.ssh/config`。

**依据**：P0-1 修订；`archive/spike/jvm/.../trust/HostKeyTrust.kt`；单测 F1–F5 + sshj 适配层测试全绿；sshj 0.41.2 的 `HostKeyVerifier` 签名已编译验证。

**状态**：`已定`（实现层）。真实 sshd 全流程为 `待实测确认`。

**被否决的方案**：TOFU（原任务书写法）、`StrictHostKeyChecking=no` 等价行为、只比对指纹不比对算法、把指纹存进日志。

### D2. 写操作：三态 + 永不自动重试 + 没有锁

**决策**

- 写操作结果只有 `Accepted` / `Rejected(code,message)` / `Unknown`；`Unknown` **不允许**自动重试，也**不允许**自动升级为成功。
- 只有权威读取拿到**正向证据**才升级为 `Accepted`；“确认没送达”仍然是 `Unknown`（保留证据串）。
- 人工重发必须用新的 `clientRequestId`，旧记录保留为 `Unknown`，审计里能看到两次独立尝试。
- 重连（sshj 指数退避）只重建通道 + 对账，**不重放**任何排队中的写请求。
- 客户端内部对同一 pane 的写操作做 per-pane Mutex 串行化，但这只是 **advisory**，不是跨客户端互斥。

**依据**：P0-2 / P0-5；0.9.3 schema 显示 `agent.prompt` / `pane.send_keys` / `pane.send_text` 无前置条件、无幂等键；`WriteLedger` 6 个单测。

**状态**：`已定`。

### D5. L1 PermissionRequest Hook 的文件 IPC 契约

**决策**（细节见调研 v2「L1 结构化审批的 Hook 契约」）

- 请求身份：`request_id`（ULID）+ `nonce` + `tool_input_sha256` + `session_id` + `tool_use_id` + `herdr_pane_id`；客户端展示前必须校验 `tool_input_sha256`。
- 过期：hook 侧 TTL 120 s（远小于官方 10 分钟 hook 超时）；过期 → 退出码 0、**不输出 decision**，交回终端提示；客户端同样在 TTL 后禁止提交。
- 取消：`.cancel` 文件（带 nonce），hook 立即退出；App 被杀只靠 TTL 兜底，所以 TTL 必须小。
- 并发：每请求一个目录 `approvals/<request_id>/{req,answer,cancel}`，hook 只等自己的 id，客户端只对可见的那个 id 提交，并发上限 8。
- 信任边界：`0700`/`0600`、原子写、回答读一次即删、nonce/哈希/TTL 任一不符即丢弃；明确“安全性 = SSH 账号安全性”，不宣称防主机被攻破。
- 安装：优先专用 `CLAUDE_CONFIG_DIR`，改前备份、改后提供一键卸载。

**依据**：P0-4；Claude Code hooks 文档（`PermissionRequest`、10 分钟超时）。

**状态**：`已定`（契约）。`hook 超时无输出时 Claude 的默认行为`（调研 v2 #26）为 `待实测确认`，这是契约里唯一可能在实测后需要调整的点。

### D8. 能力协商以 schema 为准，`ping.capabilities` 只当特性开关

**决策**：客户端内置“herdr 版本/协议号 → 方法集”的映射（由 `herdr api schema --json` 生成），运行时校验 `ping.protocol` 兼容性；`ping.capabilities` 只用于开关实验性特性（如 `live_handoff`、`ssh_agent_registration`）；未知方法/未知错误码一律优雅降级，不崩溃。

**依据**：F-1；`ServerCapabilities` 字段清单；`ErrorBody.code` 无枚举。

**状态**：`已定`。

### D9. 订阅模型：全局生命周期 + 逐 pane 状态，双层维护

**决策**：全局订阅只订阅 `pane.created/closed/updated/exited`、`tab.*`、`workspace.*`、`layout.updated`；`pane.agent_status_changed` **逐 pane** 订阅，收到 `pane.created` 后补订阅、`pane.closed` 后退订。事件仍然只当失效信号，由串行 Reconciler 全量对账。

**依据**：F-2；`Subscription` oneOf 的 required 字段。

**状态**：`已定`（结构）。补订阅的时序细节 `待实测确认`。

### D12. 指纹必须用 SSH wire blob 的 SHA256，不能用 sshj 的便捷方法

**决策**

- 指纹定义：`SHA256:` + Base64(无 padding) over `Buffer.PlainBuffer().putPublicKey(key).compactData`，与 `ssh-keygen -lf` 逐字符一致。
- 禁止使用 `java.security.PublicKey.getEncoded()`（sshj 的 Ed25519 返回 **X.509**，实测解析得到荒谬的 `bad key blob length 808071173`）。
- 禁止使用 `SecurityUtils.getFingerprint()`（它是 **MD5**）。
- SSH 算法名从 wire blob 的第一个 length-prefixed string 解析；解析失败才退回 `KeyType.fromKey(key).toString()`。

**依据**：`RESULTS.md` ON-5；真机 F1–F4 全通过。

**状态**：`已定`（已实现 + 真机验证）。

### D15. 连接必须复用；并发上限按 4 记账

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

### D19. pi 作为本阶段的结构化数据源（替代 Claude）

**决策**

- Spike 3/4 主数据源改为 **pi 1.4.0**：`pi --session-dir ~/spike-sandbox/pi-sessions`（扁平目录，可整体清理），不碰 `~/.pi` 配置、不装 `herdr integration install pi`。
- 已知限制：不装集成时 herdr 的 `agent_session` 为 `null`，只能按 `foreground_cwd` + 目录/文件 mtime 定位；pi 退出后 herdr 立即丢失 agent 身份（`agent=null`）。
- Phase 3 若要 `agent_session`，再评估装集成（会写 `~/.pi/agent/extensions/`，需单独批准）。

**依据**：`RESULTS.md` ON2-25…ON2-28。

**状态**：`已定`（本阶段）。

### D21. `agent.wait` 只当“带超时的阻塞查询”，不当锁、也不当完成通知的唯一来源

- 实测：条件满足时 2 ms 返回 `agent_info`；不满足时精确阻塞 `timeout_ms` 后返回 `error.code=timeout`（直连）；**条件不满足时不会排斥其他写者**（working 期间另写被 1 ms 接受）。
- 用法约束：① `until` 必须包含 `done`（pi 完成后是 `done`，不是 `idle`）；② 超时一律视为“没等到”，不得推断成败；③ 完成检测以 `done`/`idle` 事件 + `agent.get` 为准，`agent.wait` 只做辅助。
- 经 `remote-api-bridge` 时超时无响应（I-39），客户端要按 `Unknown` 处理。

**状态**：`已定`。

### D25. SFTP 读取的三条硬约束（实现层）

1. **句柄必须缓存**（`open/close` 各占 1 个往返）；
2. **前缀只做“可疑时 + 周期”复检**，不得每次 poll 都读；
3. **每次读取必须带 64 字节重叠校验**，否则“文件被替换”会被误判成追加，输出错误内容。

**依据**：`RESULTS.md` §9.12；回归见 `ISSUES.md` I-46。

**状态**：`已定`（已实现 + 单测）。

### D27. Android 上的 sshj 初始化顺序（硬性）

1. `Security.removeProvider("BC")` → `Security.addProvider(BouncyCastleProvider())`（APK 内置 `bcprov-jdk18on`，版本需与 sshj 传递依赖一致）；
2. `SecurityUtils.setRegisterBouncyCastle(true)` → `SecurityUtils.registerSecurityProvider("BC")`；
3. `DefaultConfig.keyExchangeFactories` 只保留 `curve25519-sha256`、`curve25519-sha256@libssh.org`、`ecdh-sha2-nistp256/384/521`、`dh-group14-sha256`、`dh-group16-sha512`、`dh-group-exchange-sha256`；
4. 私钥用 `OpenSSHKeyV1KeyFile` 读取（OpenSSH 格式），RSA/PKCS#1 直接拒绝并提示重新生成。

**依据**：`RESULTS.md` §9.15；`ISSUES.md` I-48。

**状态**：`已定`（真机验证通过）。

### D29. 移动端刷新策略：事件驱动 + snapshot 批量，轮询 ≥ 1 s

- 实测（真机）：snapshot 384 ms、事件随请求同步到达（≈RTT）、SFTP 单次 poll 353 ms。
- 决策：
  1. 看板/会话列表**只靠事件触发刷新**（`dirty` 标记 + 串行 snapshot），不做定时全量轮询；
  2. 确实需要兜底轮询时，间隔 **≥ 1 s**（活跃 1 s / 空闲 5 s），且一次刷新覆盖所有 pane；
  3. 任何“每 pane 一个轮询”的设计一律不做。

**依据**：`RESULTS.md` §9.16；`ISSUES.md` I-51。

**状态**：`已定`。

### D35. pi 集成可在隔离 HOME 下安装（Spike B 结论）

- `herdr integration install pi` 会遵循 `$HOME`；在 `HOME=~/spike-sandbox/pihome` 下安装只会写沙箱，不动全局。
- 装上后 `agent_session` 返回 `{source:"herdr:pi", kind:"path", value:"…jsonl"}`，可作为 transcript 的权威定位。
- 决策：Phase 2 优先用 `agent_session.path` 定位 JSONL；`--session-dir` 扁平目录作为可控补充。

**状态**：`已定`（本阶段）。

### D50. ToolCall↔ToolResult 只按稳定 id 配对，位置配对必须标注

- 双方都有 `toolCallId` 才标记为精确配对；
- id 不匹配时退化为最近未配对 call，UI 显示「按顺序配对」，不得对外声称精确；
- 找不到 call 的结果独立渲染，绝不合并进别的工具条目。

**状态**：`已定`。

### D53. ToolCall↔ToolResult 只认 `toolCallId`，彻底删除位置配对

- id 不匹配或无 id 的 ToolResult 一律独立渲染（`pairedById=false`），UI 不显示“已配对”；
- 结果先于调用到达：仍按 id 在**调用位置**合并；
- 这是对 M2-B 的 `按顺序配对` 的**回退**：那种显示会误导用户相信错误归属。

**状态**：`已定`（覆盖 D50 的降级分支）。

## 问题（ISSUES.md）

### I-14 其他测试脚本问题（已修）

| 问题 | 影响 | 修复 |
| --- | --- | --- |
| Spike 1 用 `sudo -n sshd -T` | 需要 sudo 或直接失败，且违反“不扩大权限” | 改为非 sudo + 记录不完整 |
| Spike 1 用 `herdr ...` 裸命令 | 非交互 shell 的 PATH 与登录 shell 不同 | 用 `command -v herdr` 绝对路径 + 白名单引用 |
| Spike 2 `events_lost` 只跑 1 次 | 无法给成功率 | 改为 20 次 + 失败原因 |
| Spike 3 hook 里 `cat` 被消费两次 | `sid=` 那行拿到空输入，脚本报错 | 改为一次性 `python3` 读取 + 原子写 |
| Spike 5 假 TUI 用均匀随机 0–1500 ms | 撞不到窗口，测试可能“假通过” | 改为三种对抗模式（0–50 ms / 300–1500 ms / 同文案不同命令） |
| 验收标准“0 次答错” | 不可实现 | 改为拦截率 / 窗口上限 / 无静默批准 |
| 结果模板没有状态列 | “没跑”和“跑了没问题”不可区分 | 总表加 `状态` 与 `证据文件` 列 |

### I-73 启动时自触发一次重连（每次打开 App 白连一次）

- **现象**：日志里 **13 次 `manual reconnect`**，每次冷启动都是 `generation=1` 紧接着 `generation=2`。
- **原因**：`HerdrApplication.onCreate` 调 `manager.startAll()`；`MonitorService.onStartCommand` 又调一次 `startAll()` → `reload()` 保留 runtime 后执行 `ensureStarted()` → 因为 `supervisor != null` 直接 `reconnectNow()`。
- **影响**：多一次连接（≈0.5–1 s）、状态条闪一下"重连中"、通知的 completion 基线被重置（可能漏一次完成提醒）。
- **修复建议**：`startAll()` 幂等——已连接的主机不调用 `reconnectNow()`；只有显式"重连"按钮才重连。
- **状态**：待修（记录在 `DESIGN-REVIEW-BRIEF.md` 问题 1）。

### I-79 流式增量未做批处理，映射仍为 O(n)

- **现状**：`remember(messages) { ConversationItemMapper.map(messages) }`，每条新增消息都会重跑整表映射。
- **风险**：长会话（>500 条）高频增量时可能掉帧。
- **建议**：M2-C 改成增量 append + 历史条目缓存复用。

### I-79 ✅ 已修复 — 全量映射 O(n)

- 修复：`IncrementalConversationMapper`（APPEND/PATCH/FULL），追加 1 条只映射 1 条，
  工具结果追加只原位替换单条目（`mapped == 0`）。
- 验证：`IncrementalMapperEquivalenceTest`（随机种子一致性）+ `MapperCostTest`（21.9×）。

### I-85 ✅ 已修（key 增加首尾 48 字符抽样）— MarkdownAstCache 无文本等值校验

- 64 位 FNV-1a，碰撞概率 ~2⁻⁶⁴；不做全文等值校验是为了避免缓存持有全文的内存代价。
- **结论**：接受，记录在案。

### I-88 ✅ 已修复 — Unknown 写操作跨进程丢失

- **现象**：`WriteLedger` 记录只在内存；进程被杀后「结果未知」的操作消失，用户无法确认。
- **修复**：`PendingWriteSink` + Room `pending_writes`（仅 Pending/Unknown）+ 启动 `restore()`；
  重发生成新 Action ID，旧记录保留；`autoRetryAllowed` 恒 false。
- **验证**：`WriteLedgerPersistTest` 5 个用例。

### I-99 Markdown 表格不渲染（已修）
`MarkdownParser` 增加 `MdBlock.Table`（表头 + 分隔行识别、单元格行内语法、列数容错、纯文本投影），`MarkdownText` 增加 `TableBlock` 渲染；`PARSER_VERSION` 2→3。单测 6 项 + 真机浅/深色截图验证。

## 实测结果（RESULTS.md）

### RESULTS §9.3 协议行为（VERIFIED）

证据：`docs/evidence/s2-snapshot-bridge.json`、`s2-snapshot-cli.json`、`s2-subscribe-*.txt`、`s2-ping-latency.csv`。

| ID | 项 | 结果 |
| --- | --- | --- |
| ON2-1 | `ping` | `{"type":"pong","version":"0.9.5","protocol":22,"capabilities":{live_handoff:true, detached_server_daemon:false, endpoint_protocol_generation:1, surface_interest:true, health_check:true, ssh_agent_registration:true}}` |
| ON2-2 | **一个连接只处理一个请求** | 同一连接发 2 个请求：第 1 个正常返回，第 2 个得到 `Error: Os { code: 104, ConnectionReset }` → 待验证 #20 **关闭** |
| ON2-3 | `session.snapshot` bridge vs CLI | 两者字段与内容完全一致（当前都是空快照，`type=session_snapshot`, version 0.9.5, protocol 22） |
| ON2-4 | 全局 `events.subscribe` | 立即返回 `{"result":{"type":"subscription_started"}}`；随后空闲 12 s **没有任何事件**（不重放、不心跳） |
| ON2-5 | 订阅不存在的 pane | `{"error":{"code":"pane_not_found","message":"pane w9:p9 not found"}}` → **整条请求被拒**（不是静默忽略） |
| ON2-6 | `pane.agent_status_changed` 缺 `pane_id` | `{"error":{"code":"invalid_request","message":"invalid request: missing field \`pane_id\` at line 1 column 107"}}` → **F-2 在真机确认** |

### RESULTS §9.10 Q-4 / Q-2 / Q-1（2026-10-31 15:40–16:05）

#### Q-4 streamlocal 对照（BLOCKED，两个独立原因）

| 项 | 结果 |
| --- | --- |
| Windows OpenSSH 客户端做 unix-socket `-L` | **不支持**：`Bad local forwarding specification 'C:/…/herdr-test.sock:/home/…/herdr.sock'` |
| sshj 0.41.2 的 `direct-streamlocal@openssh.com` | **没有实现**（jar 里只有 `DirectTCPIPChannel`，没有 streamlocal 相关类） |
| 服务端是否允许 streamlocal | **允许**：在主机本机 `ssh -N -L /tmp/herdr-loop.sock:<herdr.sock> localhost` + `socat` → `ping` 正常返回 |
| 顺带发现 | **同一个服务端的 capabilities 随连接方式变化**：直连 socket 时 `detached_server_daemon: true`，经 `remote-api-bridge` 时是 `false` |

结论：方案 B 在“我们的客户端栈”里 **BLOCKED**，不是服务端限制。要做只有两条路：给 sshj 写自定义 channel（估 1–2 天），或走方案 C（主机端复用脚本，需装脚本，需批准）。

#### Q-2 events_lost（小规模尝试 → NOT TESTED，附量化估算）

做法：订阅后本地 25 s 不读取，同时在 `w4` 里做 6 次 split+close（30 个事件）。

| 结果 | 值 |
| --- | --- |
| 事件数 / 字节 | 30 个事件 / 10,477 B（≈1.7 KB/pane） |
| `events_lost` | **未出现**；恢复读取后 30 个事件**全部送达**（无丢失、无重放） |
| 结论 | 订阅能承受 25 s 级卡顿。要触发 `events_lost` 需要 >100 个 pane 量级（填满 socket 缓冲），属于压力测试，按你的约束**不做** → 恢复路径本轮 `NOT TESTED` |

证据：`docs/evidence/s2-events-lost-attempt.ndjson`。

#### Q-1 交互式 pi：agent.prompt / agent.wait / 无锁（VERIFIED）

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
