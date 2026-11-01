# 项目现状（给外部复核 / 调研用）

> 生成时间：2026-10-31｜仓库：`herdr-gui-research`｜分支 `main`
> 详见：`RESULTS.md`（逐项实测）、`DECISIONS.md`（D1–D30）、`ISSUES.md`（I-1–I-51）、`NEXT-STEPS.md`、`archive/docs/PHASE0-SUMMARY.md`

---

## 1. 项目是什么

一个**原生 Android（Kotlin + Compose）的 herdr 客户端**：手机上监督 / 交互跑在远程开发机上的 coding agent（Claude Code / pi / Codex）。
定位不是"手机终端"，而是"agent 监督与交互"：看板（谁在等）、审批、读对话、补指令、少数情况接管终端。

**核心架构假设**：控制面走 herdr 的 socket JSON API（NDJSON），内容面读 agent 自己的结构化数据（Claude/pi 的 JSONL），终端只做兜底。

---

## 2. 实测环境

| 项 | 值 |
| --- | --- |
| 客户端 | Windows 11，JDK 17.0.3，Gradle 9.4.1，Kotlin 2.2.10，**sshj 0.41.2**，JUnit 5.11.4 |
| 远端主机 | Ubuntu 24.04.5 LTS，24 vCPU，31.8 GiB，**herdr 0.9.5**（protocol 22 / schema_version 1 / **102 方法**），OpenSSH 9.6p1 |
| Agent | pi **1.4.0**（`/home/luxiang/.nvm/.../bin/pi`）；Claude Code 2.1.312（已装但未使用，`settings.json` 无 hook） |
| 手机 | **Redmi 22021211RC（Xiaomi）**，Android **14（SDK 34）**，1080×2400 @440dpi，60/90/120 Hz |
| 网络 | PC↔主机 RTT **50 ms**；手机↔主机 TCP **55 ms**（经 VPN `tun0`）；**ICMP 被丢（ping 全丢但 TCP 通）** |
| Android 构建 | AGP **9.2.1**（内置 Kotlin，不能再apply kotlin-android 插件），compileSdk 36，minSdk 26 |

---

## 3. 已验证（真机 + PC，均有原始数据）

### 3.1 SSH / 安全

| 项 | 结果 |
| --- | --- |
| 主机指纹 fail-closed（F1–F4） | **PC 与真机都通过**：F1 未知主机被拒且**信任库未创建**；F2 显式确认后 pin；F3 再次连接 0 次询问；F4 篡改被拒且不覆写 |
| 指纹计算 | 自己按 SSH wire blob 算 `SHA256:` + Base64(no padding)，与 `ssh-keygen -lf` **逐字符一致**（`SHA256:lfFYDnJNKAPT1jtyrA6bU89XDcKtHQiy7PPdiX0G0T8`） |
| sshj + Ed25519 | **可用**（JVM 5/5；Android 真机 OK） |
| sshj + RSA | **失败**（`Exhausted available authentication methods`），同一 key 用 OpenSSH 客户端可登录；根因未定位 |
| 连接+认证耗时 | PC ≈ 500 ms；真机首次含对话框等待 |

### 3.2 herdr API

| 项 | PC | 真机 |
| --- | --- | --- |
| `ping` | `0.9.5 / protocol 22`，capabilities 6 项 | 同 |
| 单请求（复用连接，经 `remote-api-bridge`） | **248 ms** | **≈416 ms**（300–513，5/5 成功） |
| 每请求新开 SSH 连接 | **1181 ms**（禁止） | — |
| `exec true`（通道开销） | 201 ms | 331 ms |
| `session.snapshot` | bridge 与 CLI 输出**逐项一致** | **384 ms**（P50） |
| `events.subscribe` | 立即 `subscription_started`；空闲无事件/无心跳 | 建立 159 ms |
| 事件端到端 | — | 自触发 split/close → 事件到达 **292/388 ms ≈ 请求 RTT** |
| 并发 exec channel | **上限 9**（第 10 个 `open failed`，连接不断） | — |
| 订阅不存在的 pane | `error.code = pane_not_found`（整条拒绝） | — |
| `agent_status_changed` 缺 `pane_id` | `invalid_request: missing field pane_id` | — |
| 一个连接只处理一个请求 | ✅（第 2 个请求被 reset） | — |

### 3.3 写操作与 agent 语义

| 项 | 结果 |
| --- | --- |
| `pane.send_text` / `pane.send_keys` | 可用；`send_text` + `["enter"]` 能在 shell/pane 里执行 |
| `agent.prompt` | 可用；ack **301 ms**（直连 socket）；返回 `agent_prompted` |
| pi 状态轨迹 | `unknown → working @405 ms →` **`done` @4836 ms**（注意：完成是 `done` 不是 `idle`） |
| `agent.wait` 条件已满足 | 2 ms 返回 `agent_info` |
| `agent.wait` 条件不满足 | 精确阻塞 `timeout_ms`（3007 ms）后返回 `error.code=timeout` |
| **`agent.wait` 是否是锁** | **不是**：pi `working` 时另一客户端 `send_text`+`enter` 在 **1 ms** 内被接受，同时第三个 `agent.wait` 干等 13.3 s |
| `pane.read.revision` | **恒为 0**（内容变化也不变）；`pane.get.revision` 只跳一次 → 不能当内容版本号 |
| pane id | **base36**（`w4:pA`），不是十进制 |
| `pane.split` 参数 | 是 **`target_pane_id`**；传 `pane_id` 会被**静默忽略**并 split 当前焦点 pane |

### 3.4 审批读屏（L2）竞态量化（合成 TUI，50 ms RTT）

| 组 | 发送 | 二次校验拦截 | 送达时题号不符 | 错题率 |
| --- | --- | --- | --- | --- |
| 对照组（读屏后直接发） | 25 | 0 | 6 | **24.0%** |
| L2 本地（窗口 B ≈ 1 ms） | 56 | 14 | 2 | **3.6%** |
| L2 + 模拟手机 250 ms | 50 | 20 | 17 | **34.0%** |

**结论：手机网络下"二次校验"不足以支撑一键批准。**

### 3.5 内容面（pi JSONL）与 SFTP

| 项 | 结果 |
| --- | --- |
| pi `--session-dir` | 生效；文件**扁平**落在该目录：`<ISO8601>_<uuid>.jsonl`（无 `--<cwd>--` 子目录） |
| pi JSONL 行类型 | `session` / `model_change` / `thinking_level_change` / `message`；`message` 带 **`id`/`parentId`/`timestamp`**；工具调用在 `content[]` 分片（`thinking`/`toolCall`/`toolResult`） |
| herdr 对 pi 的识别 | `agent=pi`，但 **`agent_session=null`**（未装 `herdr integration install pi`）；pi 退出后 herdr 立即丢失 agent 身份 |
| SFTP 可用性 | ✅；`FileAttributes` **无 inode**（只能靠“前缀 + 连续性”判定身份） |
| SFTP 单次 poll | PC 稳态 **195 ms**（优化前 718）；真机 **353 ms** |
| 截断 / 替换 | 都能 `Reset`；替换用“64 字节重叠校验”做到**即时**（0 额外往返） |
| 尾部回填 | PC 123 ms / 真机 143 ms（30–50 行） |

### 3.6 Android 专属坑（已解决，且 JVM 不会暴露）

1. Android 自带老 BouncyCastle 缺 `X25519`/`SHA-256` → 必须 `removeProvider("BC")` 后注册 APK 内 `bcprov-jdk18on`，并让 sshj 用它；
2. KEX 必须白名单：`curve25519-sha256` / `ecdh-sha2-*` / `dh-group14/16/gex`。

---

## 4. 已否决 / 明确不做

| 结论 | 依据 |
| --- | --- |
| 手机端「一键批准」读屏审批 | 250 ms 窗口下错题率 34%（D22） |
| 用 `agent.wait` 做互斥/加锁 | 无锁、不排斥写者（实测） |
| 首次连接自动信任主机（TOFU） | 安全底线（D1） |
| 写操作超时自动重试 | 无幂等键（D2） |
| 用 `ping.capabilities` 判断方法可用性 | 只是特性开关（D8） |
| 方案 B（`direct-streamlocal`） | Windows ssh 不支持 unix-socket `-L`；sshj 0.41.2 无该 channel 实现（D20） |
| 团队 Presence/Lease/Ownership | Herdr 无任何协作原语（D7） |
| 用 pi 默认配置验证审批 | pi 不弹确认，`blocked` 造不出来（I-41） |
| 每次 poll 读文件前缀 / 整文件读 | 50 ms RTT 下往返爆炸（D25） |
| 把 `pane_id` 当数字 | base36（D30） |

---

## 5. 尚未验证 / 开放风险

| 项 | 状态 | 说明 |
| --- | --- | --- |
| `events_lost` 复现与恢复 | NOT TESTED | 30 事件/25 s 卡顿不会触发；需要 >100 pane 量级（压力测试，未做） |
| 断线注入下的写操作三态 | NOT TESTED | 需要专门注入；账本语义已有单测 |
| `agent_blocked` 与 L1 审批 | NOT TESTED | pi 不产生确认；Claude 未登录；pi 扩展会改其配置 |
| Android 后台 / 前台服务 / 耗电 | NOT TESTED | 需要 Spike 6 真机跑 1 小时 |
| Compose 性能基线 / 键盘动画 / 120 Hz | NOT TESTED | 需要 Spike 7 |
| sshj RSA 认证失败根因 | 未定位 | 产品只用 Ed25519，但需知道为什么 |
| pi 会话"首次落盘时机"、多会话区分 | NOT TESTED | Spike 3 剩余项 |

---

## 6. 关键决策清单（节选）

| 编号 | 决策 |
| --- | --- |
| D1/D12 | 指纹：先 KEX 拿 key → 展示 → 带外核对 → 显式确认；fail-closed；指纹按 SSH wire blob 算 SHA256 |
| D2/D21 | 写操作三态（Accepted/Rejected/Unknown），**永不自动重试**；`Unknown` 只有正向证据才能升级 |
| D3 | `agent.wait` 只是阻塞查询，不是锁 |
| D8/D16 | 能力协商以 schema/版本为准；启动时 `ping` 探测版本，未知版本进保守模式 |
| D9 | 双层订阅：全局生命周期 + 逐 pane 状态；`pane.created` 后补订阅 |
| D11 | Transport 抽象（remote-api-bridge / socat / streamlocal），启动探测；capabilities 按 Transport 分别读 |
| D15 | 必须复用单连接；并发短请求上限 4（实测通道上限 9） |
| D17 | 审批 L2 只用 `promptHash` + `agent_status`，**不依赖 `revision`** |
| D22 | **手机端禁止一键批准**；审批只走 L1 结构化 |
| D23/D28 | 延迟基线：PC 248 ms / 手机 300–500 ms；手机单请求目标 P95 < 500 ms |
| D25 | SFTP 三条硬约束：句柄缓存、前缀按需复检、每次读带 64B 重叠校验 |
| D26 | 连通性诊断用 TCP + banner，**不用 ICMP** |
| D27 | Android sshj 初始化顺序（BC + KEX 白名单） |
| D29 | 移动端事件驱动 + snapshot 批量刷新，兜底轮询 ≥ 1 s |
| D30 | id 一律不透明字符串 |

---

## 7. 当前代码/产物

```text
herdr-android-client-research-v2.md   # 调研 v2.1（7 项 P0 已修）
herdr-android-spike-plan.md           # Spike 任务书（v2.2 实测修正）
RESULTS.md / DECISIONS.md / ISSUES.md / NEXT-STEPS.md / archive/docs/PHASE0-SUMMARY.md
spike/jvm/          # Kotlin/JVM：指纹/写账本/SFTP tailer + 23 个单测（全绿）+ 在线探针
spike/android/      # 最小 Android App：F1–F4 + exec/ping + snapshot + 订阅 + SFTP
spike/results/      # 所有原始证据（schema、CSV、日志、截图）
```

---

## 8. 想请外部帮忙判断的问题（重点）

1. **sshj 的 RSA 失败**：服务端只允许 `rsa-sha2-512/256`（无 `ssh-rsa`），sshj 三次 `USERAUTH_FAILURE`。这是 sshj 0.41.2 的已知问题吗？产品只用 Ed25519 是否足够（例如有些用户的 `authorized_keys` 只有 RSA）？
2. **Android 上该不该继续用 sshj**：为了 X25519/SHA-256 我们全局 `removeProvider("BC")`，这会影响同进程其他 TLS/crypto 吗？MINA SSHD / JSch 在 Android 14 上的现实成熟度如何？
3. **每请求 300–500 ms 能否接受**？如果不能，优先级应是：① 主机端复用脚本（方案 C，需装脚本）② 自研 `direct-streamlocal` channel（1–2 天）③ 其他？
4. **审批路线的取舍**：L2 在 250 ms 窗口下错题率 34%，是否应彻底放弃读屏审批，只做 L1（Claude `PermissionRequest` hook / herdr-acp / Codex app-server）？团队/个人场景下 L1 覆盖率大概是多少？
5. **`events_lost` 的恢复策略**：生产使用中触发频率如何？值得为它做全量对账压测吗，还是“重订阅 + snapshot”足够？
6. **pi vs Claude 作为第一优先数据源**：pi 的 JSONL 结构稳定性如何（我们现在用 `message.content[]` 的 `toolCall` 分片）；是否应该直接依赖 `herdr integration install pi` 拿 `agent_session`？
7. **移动网络下的内容读取**：SFTP 单次 353 ms，`tail -F` 会占一个常驻通道（上限 9）。哪个更省电/更稳？
8. **后台与推送**：Android 15 的 `dataSync` 6h/24h 限制下，有没有必要考虑极轻量的自建推送（主机侧 hook + ntfy/FCM）？
9. **网络拓扑**：实测手机与开发机可能不同网段且 ICMP 被丢。产品是否应该内置“跳板/隧道”支持（如 SSH ProxyJump、反向隧道），而不是要求用户改网络？
10. **架构复核**：四层（Transport / herdr 控制面 / Provider / UI+Room）是否合理？有没有我们没考虑到的并发/状态一致性问题？

---

## 9. 建议的下一步（待确认）

1. **Phase 1 骨架**：`:core:ssh`（连接池 + 指纹）、`:core:herdr`（NDJSON + 对账 + 三态账本）、`:core:data`（Room）、`:feature:board`（只读看板）。
2. Spike 6（后台/耗电，需真机常驻 1 小时）。
3. Spike 7（Compose 基线，需 Macrobenchmark）。
4. 审批路线拍板（L1 用什么）。
5. Spike 3 收尾（pi 会话定位的落盘时机/多会话）。


---

## 附：M4 之后的状态（2026-10-31）

> 上面第 1–9 节是 Phase 0 的调研与验证结论，仍然有效；本节补充 Milestone 1 + M4 的落地情况。

| 能力 | 状态 | 证据 |
| --- | --- | --- |
| 只读看板（多主机、断线恢复、Room 缓存） | ✅ 真机 | `m2-07` / `m1-05` |
| 发消息（`agent.prompt`，三态账本，不自动重试） | ✅ 真机 | `m5-03` |
| 看回复：详情页「最近输出」（终端尾部） | ✅ 真机 | `m5-05` |
| 看回复：对话页（pi JSONL 结构化 + 增量 + 缓存） | ✅ 真机 | `m5-07` |
| 后台提醒（前台服务 + 本地通知，`completion_seq` 判完成） | ✅ 前台/后台 | `m4-06` |
| L1 审批客户端（nonce/TTL/一次性/审计） | ✅ 客户端；触发源未定 | `m4-07` |
| Claude `PermissionRequest` hook | ⛔ 已给变更清单，用户选择暂缓 | — |
| 长对话滚动性能 | ⚠️ 30–44% jank，测法不可信 → 待 Macrobenchmark | `m3-jank-*` |
| Compose UI 自动化测试 | ⛔ MIUI 上挂起 | I-59 |

新增的关键事实（供复核）：

1. **`agent_session` 只在 snapshot 的 `agents[]` 里**，`panes[]` 没有（I-62）；
2. **完成检测必须用 `completion_seq`**，状态 diff 会漏 `done→working→done`（I-66）；
3. **pi 集成装在跑 agent 的主机 HOME 下，且装完必须重启 pi**（I-71）；
4. **`pane.read.revision` 恒为 0**，不能当内容版本号（I-35）；
5. 手机端每请求 300–500 ms；看板 jank 0.9%，长对话 30–44%。
