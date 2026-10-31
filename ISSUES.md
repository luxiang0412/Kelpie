# ISSUES — 发现的问题、复现方式与处理建议

> 分类：`环境` / `文档·已修` / `API 限制` / `待实测`。
> 每条都写清：现象、复现、影响、建议、当前状态。

## 环境（阻断在线 Spike）

### I-1 没有可用的隔离远端主机与专用密钥

- **现象**：Spike 1/2 的在线部分全部无法开始。
- **复现**：本轮检查环境时，`~/.ssh/config` 只有生产/团队机器（`shudao119`、`hwy79`、`hdsj*`、`sxlf`、`hyx*`、`cekj*` 等，多数 `User root` + `IdentityFile ~/.ssh/luxiang_rsa`），HerDR 的 `herdr machine list` 只有一台 `ddj ai dev`（`cekj67ddj`，团队开发机）。两者都不符合“隔离 + 可归零 + 专用密钥”的要求。
- **影响**：S1-L1…L6、S2-L1…L9 全部 `尚未验证`。
- **建议**：由用户提供一台隔离主机（或明确批准在 `ddj ai dev` 上仅使用 `--session spike`）并生成专用 Ed25519 密钥 + 带外核对指纹。**不要**复用生产主机的 root 账号。
- **状态**：等待批准（见 `NEXT-STEPS.md`）。

### I-2 只有模拟器，没有 Android 真机

- **现象**：`adb devices` 只有 `emulator-5554`。
- **影响**：所有与真机相关的结论（请求延迟、耗电、后台保活、键盘动画、120 Hz 掉帧）不能给出；本轮一律 `尚未验证`。
- **建议**：接一台 Android 14+ 真机（最好 120 Hz）后再做 S1-L6 / Spike 6 / Spike 7；模拟器数据只能用于“代码能跑”。
- **状态**：等待环境。

### I-3 本机 herdr 0.9.3，与文档基线 0.9.5 存在版本漂移

- **现象**：`herdr --version` = 0.9.3；文档基线是 0.9.5（master `bc8c289`）。
- **影响**：本轮所有 schema 事实，严格说只对 **0.9.3** 成立；0.9.5 可能已增加方法或字段。
- **复现**：`herdr --version`；`herdr api schema --json | jq .protocol`。
- **建议**：Spike 1 第一步先导出目标主机的 schema，和 `spike/results/herdr-api.schema.json` 逐字段 diff；把 diff 写进结果。上线前固定“支持的最低协议号”。
- **状态**：已记录，待在线复核。

### I-4 系统 SSH 配置存在 `StrictHostKeyChecking no`

- **现象**：`~/.ssh/config` 的 `Host *` 段有 `StrictHostKeyChecking no`。
- **影响**：如果 App 复用系统配置/已知主机，会静默接受未知主机指纹，直接违反 P0-1。
- **建议**：App 自带独立信任库，不读也不写系统 `known_hosts`/`config`；代码评审加一条检查项。
- **状态**：已在 D1 与调研 v2「安全」中定为硬性要求。

### I-5 无法运行 Gradle `--offline`

- **现象**：`gradle --offline compileKotlin` 报 `Plugin [id: 'org.jetbrains.kotlin.jvm', version: '2.2.10'] was not found`；去掉 `--offline` 后正常。
- **影响**：无网络环境下无法冷启动构建。
- **建议**：CI/本地首次构建联网；或改用 Gradle 依赖缓存镜像。不影响结论。
- **状态**：已绕开（联网构建成功）。

## 文档 / 任务书（本轮已修）

### I-6 任务书的指纹校验是 TOFU（P0-1）

- **现象**：`PinnedVerifier` 在未知 host 时 `store.appendText(...)` + `return true`。
- **复现**：把该代码用于任何新主机 → 自动信任，用户完全没看到指纹。
- **影响**：MITM 可长期驻留；与调研 v2「安全」章节自相矛盾。
- **建议/状态**：**已修**。改为 `StrictHostKeyTrust` + `SshjHostKeyVerifier`（fail-closed），并补 F1–F5 单测。

### I-7 `agent.wait` 被描述成锁（P0-2）

- **复现**：`herdr api schema --json` → `AgentWaitParams` 只有 `target`/`timeout_ms`/`until`。
- **影响**：会诱导实现“发送前先 wait 拿锁”，实际完全没有互斥，审批与输入仍可被打断。
- **建议/状态**：**已修**（文档删错误说法 + 新增专节 + 任务书加并发实验 S2-L7）。

### I-8 审批读屏被描述成“每一步都不能省”因而隐含“安全”（P0-3）

- **复现**：T0 读屏 → 用户思考（窗口 A）→ T2 二次读取 → T3 送达（窗口 B）→ T4 消费（窗口 C）。二次读取只能证明 T2 时刻的状态。
- **影响**：验收标准“0 次答错”不可实现，也无法证明；对外宣称会构成虚假安全承诺。
- **建议/状态**：**已修**。改为时序图 + 三项可度量验收（拦截率 / 窗口上限 / 无静默批准）。

### I-9 `PermissionRequest` hook 只写了“写文件 + 轮询”（P0-4）

- **缺什么**：请求身份、过期、取消、并发、信任边界。
- **具体后果**：多请求互相覆盖（固定文件名）、过期后静默批准、回答可被重放、`/tmp` 可注入、App 被杀死后 hook 阻塞 10 分钟。
- **建议/状态**：**已修**。契约见调研 v2「L1 结构化审批的 Hook 契约」；任务书 Spike 5 步骤 5 与验收表。

### I-10 SFTP 示例代码有 6 个缺陷（P0-6）

- **现象**：`String(buf, 0, n, UTF_8)` 跨块切坏多字节字符；只比 `size` 判定截断；`open(path, READ)` 后从 `offset` 读但每次重新分配 `size-offset` 大小缓冲；无偏移恢复；整文件读取；`partial` 用 `StringBuilder` 混用字节/字符语义。
- **复现**：把中文字符的 3 个字节拆到两次 `read`（本工程 `LocalFileByteSource(maxReadPerCall=1)` 即复现）→ 原写法产生乱码。
- **建议/状态**：**已修**。`SftpTail` 重写 + 10 个单测覆盖。

### I-11 团队场景被当成差异化，但 API 没有任何协作原语（P0-7）

- **复现**：对 schema 全文搜 `presence`/`lease`/`owner`/`lock` → 0 次；102 个方法里无相关内容。
- **影响**：任何“谁在处理这个 agent/避免两人同时回答”的功能都必须自建协调层（≈写服务端）。
- **建议/状态**：**已修**（文档新增章节 + 差异化降级 + roadmap 不做承诺）。

### I-12 订阅粒度写错（F-2）

- **复现**：按 v2 的示例发 `{"type":"pane.agent_status_changed"}`（无 `pane_id`）→ 不符合 schema 的 required。
- **影响**：订阅请求可能被整条拒绝；或者（若服务端宽松）漏掉状态事件。
- **建议/状态**：**已修**（双层订阅 + `pane.created` 补订阅）。

### I-13 能力协商写错（F-1）

- **复现**：`ping` 返回的 `capabilities` 只有 6 个 boolean 开关，没有任何方法列表。
- **影响**：按 v2 实现会拿到空方法集，导致所有按钮被误判为“不支持”。
- **建议/状态**：**已修**（D8）。

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

## API / 平台限制（产品设计要接受的现实）

### I-15 `stat` 通常拿不到 inode → 文件替换检测有局限

- **现象**：SFTP 的 `stat`（sshj `FileAttributes`）一般不返回 inode/device；本工程 `LocalFileByteSource` 在 Windows 上也返回 `null`。
- **影响**：若新文件的前 N 字节与原文件完全相同，且长度更大，仅靠前缀无法识别“替换”。
- **建议**：优先用 inode（`echo` 一个 `ls -i` 探测脚本，属于主机端只读操作）；否则把 headBytes 提高到 4 KiB 并记录该局限；受影响时宁可 Reset（多读一次）而不是漏读。
- **状态**：已实现前缀判定，局限已记录（`DECISIONS.md` D6）。

### I-16 错误码没有枚举，`events_lost` / `agent_blocked` 无法静态校验

- **现象**：schema 的 `ErrorBody.code` 是自由字符串；全文搜索 `events_lost` / `agent_blocked` 均为 0 次。
- **影响**：客户端不能依赖固定错误码做分支，也不能假设这两个码一定存在。
- **建议**：`code` 一律按字符串透传 + 未知码统一走“未知错误”路径（已实现于 `Ndjson`）；Spike 2 记录实际观察到的码并与文档对照。
- **状态**：已实现，实测待补。

### I-17 写操作没有幂等键 → “已送达但显示未知”无法根除

- **现象**：`agent.prompt` / `pane.send_keys` / `pane.send_text` 无请求 id。
- **影响**：`Unknown` 是永久状态之一；极端情况下用户重发会导致重复输入。
- **建议**：UI 必须把“未知”作为一等状态展示；对 `agent.prompt` 提供基于 transcript 的自动核对（拿到正向证据才升级）；对 `pane.send_keys` 直接提示“无法确认，请到电脑上确认”。
- **状态**：已实现账本语义，UX 文案待 Spike 2 后定稿。

### I-18 `pane.send_keys` 无 revision 前置条件 → L2 永远有残余风险

- **现象**：schema 里 `PaneSendKeysParams` 只有 `pane_id` + `keys`。
- **影响**：审批 L2 无法做到原子“比较后发送”。
- **建议**：向上游提 feature request（`if_revision` / `if_status`）；在此之前 L2 必须按 D4 的口径对外表述。
- **状态**：已记录，建议已写入调研 v2。

### I-19 逐 pane 订阅带来订阅管理复杂度

- **现象**：`pane.agent_status_changed` 必须逐 pane 订阅，`pane.created` 是全局事件。
- **影响**：pane 数量多时订阅条目线性增长；`pane.created` 与状态订阅之间存在短暂窗口（新 pane 的状态变化可能漏掉，需要靠快照兜底）。
- **建议**：订阅表以 pane 集合为唯一真源，`pane.created` 后立即补订阅并触发一次定向读取（`pane.get`）；在 Spike 2 测这个窗口的实际漏报率。
- **状态**：结构已定，漏报率待实测。

## 待实测（本轮无法给结论）

### I-20 文档中若干“已核实”结论本轮无法独立复核

- **清单**：`handle_connection_with_stop` 单请求语义、`events_lost` 时连接被关闭、订阅不存在 pane 导致整条拒绝、hook 超时 10 分钟、Claude JSONL 行结构、Codex `.zst` 触发条件等。
- **影响**：这些结论目前只有文档/源码声明，没有本机证据。
- **建议**：Spike 1/2 逐条对照记录；凡是实测与文档不符的，在 `RESULTS.md` 标 `验证失败` 并更新文档。
- **状态**：`尚未验证`。

---

# 本轮（在线只读）新增问题

## I-21 `test` 会话不存在（BLOCKED 的直接原因）

- **现象**：`herdr session list` 只有 `default`（running）和 `ljn`（stopped）；`~/.config/herdr/sessions/test/` 不存在；`herdr --session test status` 显示 `server: not running`。
- **复现**：
  ```bash
  ssh luxiang@192.168.5.8 '~/.local/bin/herdr session list'
  ssh luxiang@192.168.5.8 'ls -l ~/.config/herdr/sessions'
  ```
- **影响**：没有 socket → `ping` / `session.snapshot` / `events.subscribe` 全部无法执行；用户描述的 `herdr --remote ... --session test` 目前实际指向一个**尚未创建**的会话。
- **建议**：批准创建（命令、副作用、回滚见 `NEXT-STEPS.md` A-6）。这是恢复 Phase 0 的唯一前置动作。

## I-22 herdr 0.9.0 没有 `remote-api-bridge`（文档方案 A 失效）

- **现象**：`~/.local/bin/herdr --session test remote-api-bridge --check` → `unknown command: remote-api-bridge`（rc=2）。
- **对比**：Windows 本机 0.9.3 有该子命令（`usage: herdr remote-api-bridge [--check]`）。说明该子命令是 0.9.1–0.9.3 之间新增的**隐藏**命令。
- **影响**：文档/任务书里“每请求 exec `remote-api-bridge`”的做法在目标主机不可用。
- **建议**：按 `DECISIONS.md` D11 抽象 Transport；0.9.0 用 `socat - UNIX-CONNECT:<sock>` 或 `nc -U`；或 ssh `-L` streamlocal 转发。**不升级 herdr**（用户禁止）。

## I-23 sshj 0.41.2 对 OpenSSH 9.6 + RSA 公钥认证失败（FAILED）

- **现象**：`UserAuthException: Exhausted available authentication methods`；debug 日志显示连续 3 次 `Attempting authentication using OpenSSHKeyV1KeyFile`，每次收到 `USERAUTH_FAILURE`。
- **复现**：
  ```bash
  cd spike/jvm && gradle installDist
  JAVA_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug" \
    build/install/herdr-spike-jvm/bin/herdr-spike-jvm --mode connect \
    --host 192.168.5.8 --port 22 --user luxiang --key 'C:/Users/luxia/.ssh/id_rsa' --runs 1
  ```
  日志：`spike/results/s1-sshj-auth-debug.log`。
- **已排除**：密钥未授权（OpenSSH 可登录，`IdentityAgent=none` 也一样）；公钥不一致（sshj 导出的指纹与 `ssh-keygen -lf` 相同）；host key 校验（F1/F2/F4 正常）。
- **已尝试无效**：注册 BouncyCastle、把 `rsa-sha2-512/256` 提前、`init(private, public)` 双文件、改用 PKCS#1 密钥（那个直接解析失败）。
- **影响**：MaxSessions/并发/每请求延迟/NDJSON 往返全部无法测量；Phase 1 的 SSH 层选型存在真实风险。
- **建议**：先用 Ed25519 复测（产品本来只用 Ed25519，需授权写入 `authorized_keys`）；仍失败则做 MINA SSHD / JSch 最小对照；同时向 sshj 上游报告（附日志 + sshd 版本 + `server-sig-algs`）。

## I-24 sshj 无法解析 PKCS#1 私钥，且 `loadKeys(path)` 报 "Unknown key file"

- **现象**：`~/.ssh/luxiang_rsa`（`-----BEGIN RSA PRIVATE KEY-----`）→ `SSHException: No provider available for Unknown key file`。
- **影响**：用户如果沿用旧格式密钥，App 需要给出明确提示（引导生成新密钥），而不是抛内部异常。
- **建议**：App 只支持/只生成 OpenSSH 格式密钥；错误信息要可读（“此私钥格式不受支持，请在 App 内重新生成”）。

## I-25 一次性 SSH 往返 ≈ 1.1 s，远高于文档目标

- **现象**：`ssh host true` 10 次：1040–1139 ms（P50 ≈ 1107 ms）；其中 TCP 建连仅 108 ms。
- **含义**：如果客户端“每次请求都新建 SSH 连接”，单请求成本 ≈ 1.1 s，**必然不达标**（目标 P95 < 300 ms）。约 900 ms 花在 KEX + 认证 + 进程启动（Windows 侧 `ssh.exe` 冷启动也计入）。
- **建议**：Phase 1 必须复用单条连接（长连接 + 多 channel），把 1.1 s 只付一次；每请求成本要单独测（依赖 I-23 修复）。

## I-26 目标主机是共享桌面环境

- **现象**：桌面会话（`:1`）在线、`herdr default` 会话 running、9 个登录会话、swap 已用 8.7 GiB。
- **风险**：误操作别的会话/别的项目；GUI 侧可能有 herdr 客户端在操作 pane。
- **建议**：见 `DECISIONS.md` D14 的实验纪律；所有实验限定 `--session test`；不做 pane 压力测试。

## I-27 版本三处不一致（0.9.0 / 0.9.3 / 0.9.5）

- **现象**：目标主机 0.9.0、本机 0.9.3、文档基线 0.9.5；方法集各有 3 个差异，capabilities 少 1 项。
- **影响**：任何“某方法可用”的判断都必须绑定版本；用 0.9.3/0.9.5 的结论指导 0.9.0 客户端会出错。
- **建议**：客户端内置“版本 → 方法集”表；启动时比对 `ping.version` 与 schema，未知版本进入“保守模式”（只读功能）。

## I-28 本机非交互 PATH 不含 `~/.local/bin`（已规避）

- **现象**：`ssh host 'herdr ...'` → `command not found`。
- **建议**：所有远端命令用绝对路径；App 端也应先 `command -v`/绝对路径探测，而不是假设 PATH。

---

# ONLINE-2 新增问题

## I-29 herdr 在两天内从 0.9.0 变成 0.9.5（非本会话操作）

- **现象**：10-29 只读检查时 `herdr --version` = 0.9.0；10-31 复查为 0.9.5，二进制 `2026-10-30 08:03:28`（30.3 MB）。`remote-api-bridge` 也随之出现。
- **复现**：`ls -l --time-style=+%F_%T ~/.local/bin/herdr; ~/.local/bin/herdr --version`。
- **排除**：本会话从未执行 `herdr update`；`~/.config/herdr/herdr-client.log` 最后修改仍是 10-26。
- **影响**：① 说明主机侧版本不受本项目控制，任何“某方法可用”的结论都必须带版本；② 之前的 0.9.0 结论作废（schema 已存档可回溯）。
- **建议**：确认是否你手动升级；若为自动更新，客户端必须按 D16 做严格版本探测。**不要再升级**（保持 0.9.5 与文档基线一致即可）。

## I-30 有效并发通道上限是 9，不是 sshd 默认的 10

- **现象**：同一连接上 9 个并发 exec 成功；第 10 个报 `OpenFailException: open failed`；fresh 连接、10 作为首测也复现 2/2。连接本身保持可用。
- **复现**：`spike/jvm` → `--mode channels --list 10`（见 `spike/results/s1-maxsessions.csv`）。
- **未知**：`/etc/ssh/sshd_config*` 没有显式 `MaxSessions`，`sshd -T` 需要 root（未用 sudo），所以无法确认是 9 还是“10 减 1 个槽位被占用”。
- **建议**：按 9 记账（D15），客户端上限 4；若要确认，需要你批准一次 `sudo sshd -T | grep -i maxsessions`。

## I-31 每请求 250 ms 里，200 ms 是 exec channel 本身

- **现象**：复用连接上 `exec true` P50 = 201 ms；同连接上 `ping` P50 = 248 ms → herdr 侧只占约 46 ms。
- **影响**：优化 JSON/解析没意义；要降延迟只能换 Transport（streamlocal 直连 socket 不启进程）或主机端复用脚本（方案 C）。
- **建议**：Spike 2 完成后做一次 streamlocal 对照（`--mode bridge` 换成 sshj 的 direct-streamlocal，或先手工用 `ssh -L` 量）。

## I-32 `herdr --session test server` 不会自动创建 pane

- **现象**：新建会话的 `session.snapshot` 是空的（0 workspace / 0 tab / 0 pane），`pane list` 为空。
- **影响**：所有带 pane 的验证（订阅状态事件、写操作、审批、pi）都需要先显式创建 workspace/pane。
- **建议**：用 `herdr workspace create --cwd ~/spike-sandbox --label spike --no-focus`（命令已确认存在，待批准执行）。

## I-33 订阅接口的行为已确认（补充 I-19）

- 全局订阅立即返回 `subscription_started`，空闲时**不发送心跳/重放**。
- 订阅不存在的 pane → `pane_not_found`（整条拒绝）。
- 缺 `pane_id` 的 `agent_status_changed` → `invalid_request: missing field pane_id`。
- **建议**：客户端把 `pane_not_found` 当作“pane 已消失”处理（触发一次快照对账），不要当致命错误。

## I-34 `pane.split` 的参数是 `target_pane_id`，用 `pane_id` 会 split 到焦点 pane（已发生的误操作）

- **现象**：`{"method":"pane.split","params":{"pane_id":"w4:p1","direction":"right"}}` → 在 `w1:t2` 里创建了 `w1:p3`（当前焦点 pane 所在 tab），`w4` 没有任何变化。
- **复现**：见上方；schema `PaneSplitParams` 只有 `target_pane_id` / `workspace_id` / `direction` / `cwd` / `ratio` / `focus`，**没有 `pane_id`**。
- **影响**：会误改**别人的 workspace**（本次已即时 `pane.close` 恢复）。
- **建议**：① 对未知字段零容忍（客户端发请求前用 schema 校验）；② 文档/任务书把所有 `pane.split` 示例改成 `target_pane_id`；③ 写操作日志记录“实际生效的 pane”。
- **状态**：已恢复；契约已修正。

## I-35 `pane.read.revision` 恒为 0，不能当内容版本号

- **现象**：同一 pane 内容变化 3 次后，`pane.read` 的 `revision` 仍是 0；`pane.get` 的 `revision` 0→1 后不再变化。
- **影响**：审批 L2 原设计“比较 revision 判断屏幕是否变化”失效；只能用 `promptHash`（文本归一化）与 `agent_status`。
- **建议**：文档改为“`revision` 仅作 pane 元数据版本参考，**不要**用它判断屏幕内容是否变化”；L2 的二次校验只依赖 `promptHash` + `pane.get(agent_status, agent, terminal_id)`。
- **状态**：已记录，文档待改（本轮已改本文件与 RESULTS）。

## I-36 `pi --session-dir` 是扁平目录，与默认布局不同

- **现象**：`pi --session-dir ~/spike-sandbox/pi-sessions -p "…"` 产生的文件是 `~/spike-sandbox/pi-sessions/2026-10-31T07-29-19-047Z_<uuid>.jsonl`，**没有** `--<cwd>--` 子目录。
- **影响**：定位逻辑要按“是否使用了 `--session-dir`”分两种：扁平 vs 按 cwd 分目录。默认布局本轮**未测**（避免往 `~/.pi` 写入样本）。
- **建议**：Spike 3 明确分两路验证；产品优先要求用户/我们传 `--session-dir`（可控且可清理）。

## I-37 事件没有时间戳字段

- **现象**：`pane_created` / `pane_updated` / `pane_closed` / `layout_updated` 的载荷里都没有时间戳。
- **影响**：端到端延迟只能靠“主机侧先记时间、客户端收到再回传”或时钟偏差估计；无法直接用事件自带时间。
- **建议**：延迟测量脚本按“主机侧写入标记 + 客户端记录接收时刻 + 估算偏差”三段式；别假设事件带 ts。

## I-38 streamlocal 在客户端栈不可用（服务端 OK）

- **现象 1**：Windows OpenSSH `-L <unix socket>:<remote socket>` 直接拒绝（`Bad local forwarding specification`）。
- **现象 2**：sshj 0.41.2 无 `direct-streamlocal@openssh.com` 实现（jar 内只有 `DirectTCPIPChannel`）。
- **现象 3**：服务端允许（主机本机自连 `ssh -L` 成功 ping）。
- **影响**：方案 B 在当前客户端栈上不可行；每请求 200 ms 的 exec 开销暂时无法绕过。
- **建议**：① 短期靠“减少请求数 + 连接复用”；② 若必须优化，给 sshj 写自定义 channel 或采用方案 C（主机端脚本，需批准）。
- **状态**：BLOCKED（客户端侧）。

## I-39 `agent.wait` 超时经 `remote-api-bridge` 时没有响应

- **现象**：`agent.wait` 条件不满足 + `timeout_ms=1500/2000/3000` 经 bridge 发出 → **stdout/stderr 都为空**、连接关闭；同一请求直连 socket → 收到 `{"error":{"code":"timeout",…}}`。
- **复现**：`printf '%s\n' '{...agent.wait until blocked timeout 3000...}' | ssh host 'herdr --session test remote-api-bridge'`（无输出）。
- **影响**：客户端会把它判成 `Unknown`（可接受但语义变差）；也说明“bridge ≠ 直连”的行为并不完全等价。
- **建议**：客户端把“无响应 + 连接关闭”统一映射为 `timeout/Unknown`；**不要**因为 bridge 有响应差异就假设请求没送达。

## I-40 capabilities 随连接方式变化

- **现象**：`ping` 的 `capabilities.detached_server_daemon`：直连 socket = `true`，经 `remote-api-bridge` = `false`。
- **影响**：能力协商必须在**实际使用的传输**上做，不能一次探测到处复用。
- **建议**：写进 D8 的实现细则：每个 Transport 建立后各做一次 `ping`，结果按 Transport 缓存。

## I-41 pi 默认不产生审批确认 → `blocked` 态与 L1 无法用 pi 验证

- **现象**：让 pi 执行 `touch …/probe-r1.txt`，直接执行并返回结果；`agent_status` 走 `unknown → working → done`，从未 `blocked`。
- **影响**：Spike 2 的 `agent_blocked`、Spike 5 的 L1、以及“审批是最高风险功能”的验证，都不能靠 pi 默认配置完成。
- **建议**：① 用 Claude（需登录/额度）；② 写一个调用 `ctx.ui.confirm` 的 pi 扩展（会写 pi 配置目录，需单独批准）；③ 合成的 TUI 只能验证哈希逻辑，验证不了 `blocked` 前置条件。

## I-42 MSYS 路径转换会让 SFTP 路径变成 `C:/Program Files/Git/...`

- **现象**：`--path /home/luxiang/...` 到达 Java 时变成 `C:/Program Files/Git/home/luxiang/...`，`stat` 报 `No such file`，看起来像 SFTP 坏了。
- **复现**：Git Bash 下运行 `--mode sftp --path /home/...`。
- **建议**：Windows 上跑脚本用 `MSYS_NO_PATHCONV=1`；客户端不涉及此问题（Android 无 MSYS），但文档里要给 Linux/Windows 两套命令。

## I-43 截断被归类为 `identity-changed` 而非 `truncated`

- **现象**：`truncate -s 10` 后 `poll()` 返回 `Reset(reason=identity-changed)`。
- **原因**：`poll()` 先做身份比对（前缀变了），再看 `size < offset`。
- **影响**：无功能影响（都 Reset），但审计日志里的原因不准确。
- **建议**：先判 `size < offset` 再判身份，或在 Reset 原因里同时带两个条件。

## I-44 SFTP 每次 poll 355–718 ms（50 ms RTT 下往返次数太多）

- **现象**：一次 poll 内部有 `stat` + `headBytes` + `open` + `read` + `close` 等 5–8 个往返。
- **影响**：250 ms 轮询在 50 ms RTT 下实际变成 ~500 ms 一次，且吃电量上升。
- **建议**：① 复用 RemoteFile 句柄；② 缓存 stat（只在 size 变化时读）；③ 身份判定用 (size, mtime) 先行，前缀只在可疑时读；④ 轮询间隔按实测调成 500 ms–1 s。

## I-45 合成 TUI 无法产生 `blocked` 状态

- **现象**：`fake-tui.sh` 显示选项时，`agent_status` 始终是 `unknown`（herdr 不认识这个假 agent）。
- **影响**：L2 的“`agent_status == blocked` 前置条件”本轮无法验证，只能验证哈希/拦截逻辑。
- **建议**：要么让假 TUI 模拟 herdr 已支持的 agent 的画面特征（脆弱、不推荐），要么等 Claude/pi 扩展把真实 `blocked` 造出来。

## I-46 更大文件替换的即时检测需要用“续读重叠校验”

- **现象（优化过程中的回归）**：为了省往返，前缀只做周期复检；结果“替换成更大文件”被当成正常追加，**先输出了 199 行错误内容**，第 5 次 poll 才 Reset。
- **修复**：每次读取向前多读 64 字节，与上次读到的末尾比对（0 额外往返）；不一致立即 Reset。
- **残留局限**：若新文件在被替换位置之后的 64 字节恰好与原文件相同，仍可能漏判（概率极低）；周期复检（5 次）作为兜底。
- **状态**：已修复并有单测。

## I-47 手机与 herdr 主机不在同一可路由网络（测试期需 adb reverse 中转）

- **现象**：手机（192.168.1.8，WiFi）→ `192.168.5.8:22` TCP 超时；手机路由只有 `192.168.1.0/24`、`10.10.14.0/30`(tun0)、`10.227.209.12/30`(cellular)。手机 → PC 的 ICMP 也 100% 丢包。
- **影响**：真机无法直连 herdr 主机，Phase 1 的“手机直连开发机”场景在这套网络下不成立。
- **当前对策（仅测试期）**：`adb reverse tcp:2222 tcp:2222` + PC 上 `ssh -N -L 2222:127.0.0.1:22 luxiang@192.168.5.8`，让 App 连 `127.0.0.1:2222`。
- **产品含义**：用户网络环境多样（不同网段 / 4G / VPN），连接失败必须给出可读诊断（DNS/路由/端口/指纹），并支持“通过跳板/隧道”的自定义目标。
- **状态**：测试链路已打通；产品侧方案待设计。

## I-47 补充：直连可用，但 ICMP 不可靠

- 复测（2026-10-31）：手机经 VPN `tun0` 获得 `192.168.5.0/24` 路由后，**TCP 22 可达**（55 ms），SSH banner 正常；但 `ping` 仍 100% 丢包。
- 修订结论：
  - “手机不可达主机”只在缺少路由时成立；现在的测试链路走**直连**，adb reverse 仅作备用。
  - **连通性诊断禁止用 ICMP**：必须用 TCP 建连（可指定端口）+ SSH banner 校验。
- 状态：直连已验证；诊断策略写入 Phase 1 要求。

## I-48 Android 自带 BouncyCastle 太旧，sshj 连不上

- **现象**：真机上首次运行报 `TransportException: no such algorithm: X25519 for provider BC`；换成 ECDH KEX 后又报 `no such algorithm: SHA-256 for provider BC`。
- **原因**：Android 的 `BC` provider 是裁剪版，没有 X25519，也没有给 `MessageDigest("SHA-256")` 注册 BC 实现；而 sshj 的 `SecurityUtils` 检测到有 BC 就优先用它。
- **解决**（已验证）：
  1. `Security.removeProvider("BC")`；
  2. `Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())`（APK 内 `bcprov-jdk18on:1.84`）；
  3. `SecurityUtils.setRegisterBouncyCastle(true)` + `registerSecurityProvider("BC")`；
  4. `keyExchangeFactories` 白名单：`curve25519-sha256` / `ecdh-sha2-*` / `dh-group14/16/gex`。
- **影响**：Phase 1 的 SSH 层必须内置 BC 并按此顺序初始化；否则真机直接失败（JVM 上不会暴露）。
- **状态**：已解决，真机验证通过。

## I-49 Android 上的每请求延迟比 PC 高 130–170 ms

- **现象**：同一路径，真机 exec 331 ms（PC 201 ms）、ping 416 ms（PC 248 ms）。
- **影响**：手机端每请求 300–500 ms；之前基于 PC 的 248 ms 预算需要上调。
- **建议**：Phase 1 一律事件驱动 + 批量读取；不要做轮询式 UI；把“每请求 <300 ms”从硬指标改为“P95 < 500 ms”。

## I-50 pane id 的序号是 base36，不是十进制

- **现象**：连续创建 pane 时，第 10 个之后的 id 是 `w4:pA`（而不是 `w4:p10`）。
- **影响**：任何 `toInt()`、`substringAfter(":p").toInt()`、按数字排序、正则 `\d+` 都会出错；日志/审计里把 id 当数字也会错。
- **建议**：`pane_id` 一律当**不透明字符串**（比较、去重、排序都用字符串）；需要“创建顺序”就用 `terminal_id` 或事件到达顺序。

## I-51 Android 上 SFTP 单次 poll 353–564 ms

- **现象**：同一实现，PC 稳态 poll 195 ms，真机 353 ms；替换后读 100 行 564 ms。
- **影响**：手机上 250 ms 轮询会持续排队。
- **建议**：移动端轮询间隔 ≥ 1 s；或在“活跃”时 500 ms、空闲 2–5 s；优先事件驱动 + snapshot 批量刷新（D29）。

## I-52 【纠正 I-23】第一轮 RSA 测试用的密钥并未授权

- **现象**：`~/.ssh/id_rsa`（`SHA256:LiY1T1l9...`）不在远端 `authorized_keys` 中；远端只有 GoeT...（`luxiang_rsa`）、qInW...（同事 Mac）、herdr-spike（Ed25519）。
- **影响**：`I-23` 把“认证失败”归因于 sshj，证据不成立。
- **修订**：用**已授权**的 `luxiang_rsa` 重测 —— JSch 成功，**sshj 仍失败**（见 I-53），所以“sshj 对 RSA/rsa-sha2 有问题”这个结论**依然成立**，只是第一次的证据无效。
- **教训**：任何“认证失败”的结论，必须先用 `ssh-keygen -lf authorized_keys` 确认密钥授权状态。

## I-53 sshj 用已授权 RSA 密钥仍无法认证（JSch 可以）

- **现象**：`luxiang_rsa`（4096, PKCS#1，已授权）：JSch 0.2.20 认证成功；sshj 0.41.2 仍 `Exhausted available authentication methods`。服务端只允许 `rsa-sha2-512/256`（无 `ssh-rsa`）。
- **影响**：如果将来要支持“用户已有 RSA 密钥”，sshj 是硬伤；Ed25519 不受影响。
- **建议**：M1 继续只支持 Ed25519（已在 UI 与代码里限制）；Spike A 的 Android 对比完成后决定是否换 JSch。

## I-54 JSch 的 exec 通道比 sshj 快约一倍（JVM 实测）

- **数据**：同一主机、同一密钥：JSch exec P50 **108 ms**（min 106 / max 208）；sshj **201 ms**。
- **影响**：若换成 JSch，单请求 248 ms 有望降到 ~150 ms，直接改善审批窗口与 UI 手感。
- **注意**：这是 JVM 数据；Android 上需要用 `CryptoSpikeActivity` 复测（BC 依赖更少是 JSch 的优势）。

## I-55 Android 上 `registerSecurityProvider("BCAPP")` 不生效，需直接 `setSecurityProvider`

- **现象**：只调 `registerSecurityProvider("BCAPP")` 时 `getSecurityProvider()` 仍返回 `BC`，连接报 `no such algorithm: X25519 for provider BC`。
- **修复**：显式 `SecurityUtils.setSecurityProvider("BCAPP")`（并在失败时再尝试 `registerSecurityProvider`），已真机验证。
- **状态**：已修复（D32）。

## I-56 「多久没刷新」不能判定过期（空闲会话误报）

- **现象**：M1 首版 25 s 没有事件就把看板标成“数据可能过期”，而空闲会话本来就无事件。
- **修复**：改为 ping 探活（D34）。
- **状态**：已修复。

## I-57 MIUI 锁屏时 `adb install` 被拒

- **现象**：`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，`dumpsys power` 显示 `mWakefulness=Asleep`。
- **结论**：这是设备/系统限制，不是应用问题；解锁后重试即成功。
- **建议**：自动化流水线里先 `input keyevent KEYCODE_WAKEUP` 并确认已解锁。

## I-58 Windows 上 Gradle 偶发“classes.jar 被占用”

- **现象**：`core/ssh/build/.../bundleLibCompileToJarDebug/classes.jar: The process cannot access the file`。
- **规避**：`gradle --stop` + 删除 `**/build` 后重建；或长期使用 `--no-daemon`（本轮采用）。
- **状态**：已规避，非代码问题。

## I-59 Compose UI 测试在真机（MIUI）上挂起，不返回结果

- **现象**：`adb shell am instrument -w -e class dev.herdr.feature.board.BoardScreenTest ...` 输出 `INSTRUMENTATION_STATUS_CODE: 1`（测试开始）后一直不结束；测试进程存活，`logcat` 无异常。
- **已排除**：屏幕休眠/锁屏（`mWakefulness=Awake`、`mDreamingLockscreen=false`）、测试 APK 未安装（已安装，包名可见）。
- **影响**：Compose 层的 UI 断言目前无法自动化；UI 逻辑（分组/计数/状态映射）已由 JVM 单测覆盖，UI 呈现靠真机截图验收。
- **后续**：换一台非 MIUI 设备或用 `androidx.compose.ui:ui-test-junit4` v2 API 再试；或改用 `UiAutomator`（跨进程，不依赖 Compose test rule）。

## I-60 `connectedDebugAndroidTest` 在设备名含空格时解析结果失败

- **现象**：`Could not load test results from '...TEST-22021211RC - 14-_core_data-.xml'`。
- **规避**：改用 `:module:assembleDebugAndroidTest` + `adb shell am instrument -w`，结果直接看 stdout（Room 4/4 通过）。
- **建议**：CI 里统一用 `am instrument` 或给设备起不带空格的 `adb -s` 别名。

## I-61 `ConnectionSupervisor.reconciler` 是 lateinit，取用顺序错会导致启动崩溃

- **现象**：M2 首版启动即 crash：`UninitializedPropertyAccessException` at `ConnectionSupervisor.getReconciler`，调用栈来自 `HostsViewModel.ensureStarted`。
- **原因**：`reconciler` 在 `start()` 里初始化，而 ViewModel 先读 `sup.reconciler` 再 `sup.start()`。
- **修复**：先 `start()`，再 `persistLoop(sup.reconciler)`。
- **建议**：把 `reconciler` 改成构造期创建（`val reconciler = SessionReconciler(...)`），彻底消除顺序依赖；已记入 M3 清理项。
- **状态**：已修复。

## I-62 `agent_session` 在 snapshot 的 `agents[]` 里，不在 `panes[]` 里

- **现象**：M2 首版从 `snapshot.panes[].agent_session` 取路径，永远是 null；Pane 详情不出现「查看对话」。
- **原因**：herdr `session.snapshot` 的 `panes[]` 不带 `agent_session`，它只在 `agents[]`（和 `agent.get`）里。
- **修复**：`HerdrCodec.parseSnapshot` 先解析 `agents[]` 建立 `paneId → path` 映射，再合并进 `Pane.agentSessionPath`；补了 2 个单测。
- **状态**：已修复（真机验证）。

## I-63 `input swipe` + `dumpsys gfxinfo` 不适合比较优化优劣

- **现象**：同一功能（长对话滚动）三次测量帧数 476 / 493 / 388，jank 37.2% / 34.9% / 44.1%，无法归因到代码改动。
- **原因**：swipe 的惯性距离、起始帧、系统负载都不受控；`gfxinfo` 统计包含启动/停止阶段的帧。
- **影响**：不能用它判断“优化有没有用”，只能给出“这个场景确实很慢”的量级结论。
- **建议**：M4 建 `:benchmark` 模块，用 Macrobenchmark 的 `FrameTimingMetric` + 固定 `CompilationMode` + 受控滚动（`UiAutomator` fling 或 `benchmarkRule` 的滚动动作）。

## I-64 长对话滚动 30–44% 掉帧（真实瓶颈）

- **现象**：500 条消息的对话页，滚动时 jank 30–44%，P50 17–20 ms（120 Hz 预算 8.3 ms）。
- **对比**：同样 swipe 在看板（5 个 pane）只有 0.9%。
- **原因假设**：每屏多条富文本的 measure/layout 成本 + 可能的 debug 构建开销。
- **已知无效**：去 Card/阴影、合并为单 Text（甚至更差）。
- **建议**：M4 用 Macrobenchmark 量化后，再考虑：文本 measure 缓存、消息分页/窗口化、release+R8+Baseline Profile。

## I-65 键盘弹出时对话页的输入栏被遮挡（已修）

- **现象**：点输入框后键盘弹出，发送按钮落在键盘下方，点不到。
- **修复**：底部输入栏加 `Modifier.imePadding().navigationBarsPadding()`；实测键盘弹出时输入框与发送按钮上移到 y≈1180–1335，可正常点击（`m4-03-ime.png` / `m4-04-ime-sent.png`）。
- **状态**：已修复。

## I-66 用“状态 diff”判断完成会漏（已修）

- **现象**：pi 一轮 `done→working→done` 在两次对账之间完成，前后采样都是 `done`，diff 认为没变化 → 不发通知。
- **修复**：改用 `agents[].completion_seq`（单调递增）；实测 `72→74` 正确触发。
- **状态**：已修复。

## I-67 `run-as` 的当前目录是 app data 根目录，不是 `files/`

- **现象**：`run-as pkg ls -l trust` 看到空目录，误以为信任库没写入（实际在 `files/trust/`）。
- **影响**：排查时容易得出“指纹没固定”的错误结论（本轮就误判过一次）。
- **建议**：调试命令统一写成 `run-as pkg ls -l files/trust`；`rm` 同样要带 `files/`。

## I-68 后台提醒的可用性依赖厂商策略

- **现象**：本机 MIUI 允许前台服务在后台刷新并发通知（实测通过）；但不同厂商/省电模式可能冻结进程。
- **影响**：“出门被提醒”不是 100% 保证；Android 15 还有 6h/24h 配额。
- **建议**：产品文案写“可选后台提醒（受系统限制）”；首次开启时引导用户关闭电池优化；M5 评估主机侧推送（可选）。

## I-69 用“自然语言让 pi 跑审批脚本”会产生多次请求（已确认）

- **现象**：提示 pi “运行 ask.sh 并等待结果”，pi 会自己加监控循环/重试，产生 3 个不同 request_id（64c8760a、551bc6ed、cc2d007d），需要多次点允许。
- **原因**：pi 把“等待类命令”当成需要兜底的任务，会重跑；这不是客户端 bug。
- **影响**：验证时体验混乱；真实产品里必须由**扩展/工具**保证“一次审批一次请求”。
- **建议**：改用 pi 扩展注册工具，或在 Claude 侧用 `PermissionRequest` hook（一次调用一次请求）。
- **状态**：客户端已验证，触发源待替换。

## I-70 “herdr integration status 显示未安装”其实是查了本机

- **现象**：用户在 Windows 本机执行 `herdr integration status`，看到 `pi: not installed (C:\Users\luxia\.pi\...)`，误以为远端没装。
- **事实**：远端两台都是 `pi: current (v9)`（`/home/luxiang/.pi/agent/extensions/...`）。
- **根因**：集成装在“跑 agent 的那台机器”的 HOME 下，每台主机独立；Windows 与 Linux 的 pi 配置互不相干。
- **建议**：排查时先看路径前缀（`/home/...` vs `C:\Users\...`）；文档里引用命令时标注在哪台机器执行。

## I-71 集成装完不会热加载，必须重启 pi

- **现象**：21:28 装好集成后，已在运行的 pi（w1:p1 等）`agent_session` 仍然是 `null`。
- **事实**：扩展在 pi 启动时加载；重启后的新 pane（w7:p1/w8:p1）立刻上报路径。
- **建议**：App 里 `agent_session=null` 的提示要写“pi 可能启动早于集成安装，重启 pi 再试”（D47）；文档把“安装后需重启 pi”写成步骤。

## I-72 App 的 `agent_session: null（未安装/未上报集成）` 文案有歧义

- **现象**：容易让人以为“集成没装”，实际可能只是“这个 pane 启动早于安装/未上报”。
- **建议**：改成“这个 pane 没有上报会话路径（重启 pi 或检查集成）”。
- **状态**：待改（D47）。

## I-73 启动时自触发一次重连（每次打开 App 白连一次）

- **现象**：日志里 **13 次 `manual reconnect`**，每次冷启动都是 `generation=1` 紧接着 `generation=2`。
- **原因**：`HerdrApplication.onCreate` 调 `manager.startAll()`；`MonitorService.onStartCommand` 又调一次 `startAll()` → `reload()` 保留 runtime 后执行 `ensureStarted()` → 因为 `supervisor != null` 直接 `reconnectNow()`。
- **影响**：多一次连接（≈0.5–1 s）、状态条闪一下"重连中"、通知的 completion 基线被重置（可能漏一次完成提醒）。
- **修复建议**：`startAll()` 幂等——已连接的主机不调用 `reconnectNow()`；只有显式"重连"按钮才重连。
- **状态**：待修（记录在 `DESIGN-REVIEW-BRIEF.md` 问题 1）。

## I-74 MIUI 禁止 instrumentation 启动 Activity（BAL），导致 UI 测试与 Macrobenchmark 无法执行

- **现象**：`am instrument` 启动后卡住；logcat：
  `MIULOG- Permission Denied Activity : Intent { ... cmp=dev.herdr.app/.TestHostActivity } ... result code=102`，
  随后 `BLASTSyncEngine: ... never received commit callback. Application ANR likely to follow.`
- **影响**：Compose UI 测试（9 个）与 Macrobenchmark 均无法在真机执行；换到 app 包内的 TestHostActivity 也不行。
- **建议**：使用非 MIUI 设备或 CI emulator；或在 MIUI 上手动授予“后台弹出界面”权限后再试（未验证）。

## I-75 模拟器 emulator-5554 的 package 服务缺失

- **现象**：`adb -s emulator-5554 shell pm list packages` → `cmd: Can't find service: package`；`sys.boot_completed=1` 但 `reboot` 后仍复现。
- **影响**：无法用模拟器替代 MIUI 设备跑 instrumented 测试。
- **建议**：重建 AVD / 换 system image。

## I-76 `CompilationMode.Partial()` 需要目标 APK 集成 profileinstaller

- **现象**：benchmark 报 `The baseline profile install broadcast was not received. This most likely means that the profileinstaller library is missing from the target apk.`
- **建议**：app 增加 `implementation("androidx.profileinstaller:profileinstaller")`（M2-B），或继续用 `CompilationMode.None()`（结果偏悲观）。

## I-77 内容变化后 `gfxinfo` 滚动数据不可比（再次印证 I-63）

- **现象**：长文本折叠后，同样 10 次 swipe 的渲染帧数从 344 → 252（总内容高度变小）。
- **影响**：不能把“jank 百分比上升/下降”归因于代码改动。
- **建议**：前后对比必须固定内容高度；否则只比较首帧/启动耗时。

## I-78 MIUI 上 `dropShaderCache` 失败，阻塞 Macrobenchmark

- **现象**：`MacrobenchmarkScope.dropShaderCache` 抛异常，基准在准备阶段终止。
- **影响**：FrameTimingMetric / Perfetto 无法获得。
- **建议**：非 MIUI 设备或 CI emulator。

## I-79 流式增量未做批处理，映射仍为 O(n)

- **现状**：`remember(messages) { ConversationItemMapper.map(messages) }`，每条新增消息都会重跑整表映射。
- **风险**：长会话（>500 条）高频增量时可能掉帧。
- **建议**：M2-C 改成增量 append + 历史条目缓存复用。

## I-80 Fixture 数据集版本变更导致与 M2-A 数据不可比

- **变更**：`i % 8` → `i % 10`，并加入 Markdown/不安全链接/未知内容。
- **影响**：M2-A 的 debug 首帧（787/786/834ms）与本轮 benchmark 首帧（375/344/298ms）同时包含构建模式与数据集两处变化，不可归因。
- **建议**：对比时必须固定数据集版本（本报告为 fixture-v2）。


## I-79 ✅ 已修复 — 全量映射 O(n)

- 修复：`IncrementalConversationMapper`（APPEND/PATCH/FULL），追加 1 条只映射 1 条，
  工具结果追加只原位替换单条目（`mapped == 0`）。
- 验证：`IncrementalMapperEquivalenceTest`（随机种子一致性）+ `MapperCostTest`（21.9×）。

## I-81 分支解析与条目映射仍是每次 O(n)

- **现状**：`ConversationBranch.effective()` 与 `ConversationItemMapper.map()` 每次消息变化都跑一遍，
  但只做线性扫描，不解析 Markdown（2000 条约 0.9 ms）。
- **风险**：真实流式高频增量下仍可能累积；需真机帧数据判断。
- **建议**：M2-C2 视真机数据决定是否做增量分支。

## I-82 UI 测试首轮 3 个失败均为测试代码问题（已修）

- `doneAndIdle_areDistinctLabels`：合并语义需 `useUnmergedTree`；「已完成」同时是分区标题 → 用节点集合断言。
- `staleData_isLabelledAsCache`：文案已改为动态 `timeAgo()`。
- `tappingAgent_opensConversation`：应点击行的合并 `contentDescription`。
- **结论**：产品代码未改动来迎合测试（语义合并与文案保持原样）。

## I-83 模拟器只有“流程可运行”结论，不能代表真机性能

- `startup_500` 在模拟器 median 2869.6 ms（debuggable + emulator，已显式 suppress）。
- 禁止据此宣称性能改善；真机 FrameTimingMetric 仍缺（I-74 / I-78）。

## I-84 草稿身份在无 agent_session 时依赖 pane 占用指纹

- **现状**：`DraftKey` 退化为 `paneId+agent+title+cwd` 的哈希；同一 pane 上换 agent 且 title/cwd 完全相同时可能复用旧草稿。
- **缓解**：`SendTargetCheck` 在占用指纹或 agent session 变化时返回 NeedsConfirm，用户必须重新确认。
- **建议**：如果 Pi 能提供 pane creation epoch，加入 DraftKey。

## I-85 ✅ 已修（key 增加首尾 48 字符抽样）— MarkdownAstCache 无文本等值校验

- 64 位 FNV-1a，碰撞概率 ~2⁻⁶⁴；不做全文等值校验是为了避免缓存持有全文的内存代价。
- **结论**：接受，记录在案。

## I-86 真实 Pi 会话端到端验收未执行（M2-C2 P0 §6）

- **原因**：需要在本轮对远端测试 Session 实际写入，未取得该轮明确授权（按任务书要求先申请）。
- **影响**：发送侧的语义/UI 变更只经 UI 测试与 JVM 测试验证，未经真实远端写操作确认。
- **下一步**：授权后执行，并记录 APPEND/PATCH/FULL 命中与耗时。

## I-87 草稿跨进程恢复未实测

- Room 写入与加载路径已实现，未做「杀进程 → 重开 → 恢复」实测。
- **建议**：与 I-86 一并验收。

## I-88 ✅ 已修复 — Unknown 写操作跨进程丢失

- **现象**：`WriteLedger` 记录只在内存；进程被杀后「结果未知」的操作消失，用户无法确认。
- **修复**：`PendingWriteSink` + Room `pending_writes`（仅 Pending/Unknown）+ 启动 `restore()`；
  重发生成新 Action ID，旧记录保留；`autoRetryAllowed` 恒 false。
- **验证**：`WriteLedgerPersistTest` 5 个用例。

## I-89 ✅ 已补（DraftMigrationTest，AVD 通过）— 草稿库缺少 Room Migration 的 instrumentation 测试

- **现状**：`MIGRATION_1_2` 已实现并可编译，但没有 `MigrationTestHelper` 用例证明旧库可升级。
- **建议**：`:core:data` androidTest 增加迁移测试（1 → 2 保留草稿行）。

## I-90 ✅ 已修复（ON_STOP 强制 flush）— 防抖窗口内进程被杀可能丢最后一次输入

- **现状**：草稿保存 400ms 防抖；此窗口内被杀 → 最后一次编辑可能未落盘。
- **建议**：`onStop` / `DisposableEffect` 强制 flush（当前仅在离开对话时 flush），或把防抖降到 150ms。

## I-91 真机中文 IME / TalkBack / 旋转 / 最大字号未人工验收

- 自动化与截图覆盖不到真实输入法组合输入、光标选区手感、TalkBack 朗读顺序。
- **建议**：人工走查并录屏（本轮无人工环节）。

## I-92 ✅ 已补（技术信息 Sheet「待确认写操作」）— 恢复后的 Unknown 在 UI 上缺少统一入口

- 持久化已具备，但 UI 只在 composer 显示最近一次 Unknown；进程重建后需重进对话才可见。
- **建议**：技术信息 Sheet 增加「待确认写操作」列表（含时间、目标、payload 指纹前 8 位）。
