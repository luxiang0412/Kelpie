# herdr 原生 Android 客户端：Spike 任务书

> 配套文档：`herdr-android-client-research-v2.md`（下面简称“调研 v2”）。本任务书可以直接交给 Claude Code 或 Pi 执行。执行 AI 请先读完“执行须知”，再按顺序做 Spike 1 → 8。

## 什么是 Spike，本任务书要达成什么

**Spike** 是正式开发前的短时间技术实验：针对最没把握的点写最少的代码，尽快得到“走得通 / 走不通 / 换方案”的结论。Spike 的代码是**一次性的**，不追求架构和美观，结论和数据才是产出。

**本任务书的目标：**

1. 用实测数据确认调研 v2 的关键路线：SSH → herdr API → Claude 会话 JSONL → 手机上的聊天视图；
2. 关掉调研 v2 里的【待验证】项（每个 Spike 末尾都写了对应的编号）；
3. 为 Phase 1 定下技术选型：通道方案 A/B/C、日志读取方式、审批级别、后台策略；
4. 测出“可量化目标”的第一版基线。

**优先级**：Spike 1–4 是**关键路径**，任何一个走不通都要先找到备选方案，再继续往下做。Spike 5–8 用来降低后续风险，可以并行做，或者推迟。

| # | Spike | 关键 | 建议用时 |
| --- | --- | --- | --- |
| 1 | SSH 连接 + 运行 herdr | ★ | 0.5–1 天 |
| 2 | herdr socket API：订阅 / 快照 / 写操作 | ★ | 1–1.5 天 |
| 3 | 定位 Claude Code 会话 JSONL | ★ | 0.5–1 天 |
| 4 | 增量解析 transcript 并渲染 | ★ | 1.5–2 天 |
| 5 | 审批安全：读屏校验 vs 结构化 hook | | 1 天 |
| 6 | Android 后台、通知与网络切换 | | 1 天 |
| 7 | Compose 渲染性能基线 | | 1 天 |
| 8 | ACP / herdr-acp 结构化通道评估 | | 0.5–1 天 |

## 执行须知（给执行 AI）

- **环境变量**：下面的命令都使用这些变量，开始前先让用户填写，并写进 `spike/.env`（这个文件**不要提交**）：

  ```bash
  export SPIKE_HOST=dev.example.com      # 远程开发机（必须是隔离的、可归零的机器）
  export SPIKE_USER=me
  export SPIKE_PORT=22
  export SPIKE_KEY=~/.ssh/id_ed25519_spike   # 专门给 Spike 用的密钥，不用个人主密钥
  export SPIKE_HOST_KEY_SHA256=SHA256:...   # 用户带外核对后提供的指纹，用于首次信任
  export HERDR_SESSION=spike             # 专用的 herdr 会话，不要用团队正在用的会话
  export SPIKE_REPO=~/spike-sandbox      # 主机上的一次性测试仓库
  export SPIKE_HOST_ID=spike-1           # App 侧主机标识（本地信任库的 key）
  ```

- **硬性禁止（任何情况下都不做）**：
  1. 修改生产 / 团队机器的 `sshd_config`、`~/.ssh/config`、全局 `known_hosts`，或用它们做实验；
  2. 修改或 patch herdr 源码、在主机上替换 herdr 版本（除非用户单独批准并记录回滚方式）；
  3. 向团队正在使用的 herdr 会话（含 attach 中的会话）发任何输入；
  4. 自动信任未知主机指纹（TOFU）、或为了“先把链路跑通”临时关闭指纹校验；
  5. 对写操作（`agent.prompt` / `pane.send_keys` / `pane.send_text`）做任何自动重试；
  6. 为了拿到结果而扩大权限（sudo、root、关防火墙、放开 sshd 限制）。
- **需要先取得批准的变更**（列出命令、影响面、回滚方式，然后停下来等用户回复）：装任何软件（herdr 升级、Node.js、socat、python 包）、写/改主机上的配置文件、往 `authorized_keys` 添加密钥、开启她的机器的防火墙/端口、启用本机 sshd 做回环实验。
- **结论状态词（三选一，不得混用）**：`已验证`（有可重放的命令 + 原始证据）、`验证失败`（跑了但结果不符合预期，附报错）、`尚未验证`（没跑或环境不具备）。**没跑过的步骤绝不能写成已验证，也不能用“应该/估讦/大体上”代替数据。**
- **隔离**：只在 `HERDR_SESSION=spike` 和 `SPIKE_REPO` 里做实验，**不要碰团队正在使用的 herdr 会话**。需要 Claude 执行命令时，只允许无害命令（如 `touch /tmp/spike-*`、`ls`），绝对不要批准 `rm -rf`、`git push`、网络请求这类操作。
- **敏感信息**：日志和样本里不能有密钥、token、私有代码。收集的 JSONL 样本要先脱敏（见 Spike 4），再放进仓库。主机指纹可以记录，私钥不行。
- **工程结构**（建议）：

  ```text
  spike/
    .env                 # 不提交
    host/                # 主机端脚本（hook、造数据、竞态测试）
    jvm/                 # Kotlin JVM 命令行工具：先在电脑上跑通 SSH 和协议
    android/             # 最小 Android App（Compose），复用 jvm 里的代码
    samples/             # 脱敏后的 JSONL 样本
    results/             # 每个 Spike 的日志、CSV、截图
    RESULTS.md           # 按“结果记录模板”一节填写的结果
  ```

- **先 JVM 后 Android**：Spike 1–4 的协议和解析逻辑先写成 JVM 版 Kotlin（sshj 在 JVM 和 Android 上 API 一样），跑通后再搬到 Android 上测真机延迟和性能。
- **以 schema 为准**：任务书里的 JSON 参数名来自 herdr 0.9.5 的文档和源码。和 `herdr api schema --json` 不一致时，**以 schema 为准**，并在结果里记录差异。
- **不确定就停**：遇到需要用户决定的事（例如修改 sshd 配置、在主机上装 Node.js），先暂停并询问用户。
- **真机与模拟器**：延迟、耗电、后台、键盘动画这类结论必须来自 Android **真机**；模拟器只能用于验证“代码能跑”，结论里要写清是哪个设备。环境不具备真机时，相关项一律标 `尚未验证`，不得用模拟器数据代替。
- **每项实验都要可重放**：命令、参数、版本号写进 `results/`；数据文件与 `RESULTS.md` 里的行一一对应（`证据` 列）。
- **时间盒**：每个 Spike 超过建议用时的 1.5 倍还没结论，就停下来，记录卡在哪里、试过什么，然后进入下一个。

---

## Spike 1：SSH 连接 + 运行 herdr ★

**目的**：确认手机（先用 JVM 模拟）能通过 sshj 稳定连上主机、校验主机指纹、通过 `remote-api-bridge` 和 herdr 通信；测出连接和单次请求的延迟；摸清 MaxSessions 限制和 streamlocal 转发的可行性。

**前置条件**
- 主机：herdr ≥ 0.9（建议 0.9.5），sshd 可达，`SPIKE_KEY` 的公钥已加入 `authorized_keys`
- 本机：JDK 17+、Gradle；一台 Android 真机（Android 14+，最好支持 120 Hz）或模拟器
- 主机上已启动 spike 会话：`herdr --session $HERDR_SESSION server`（或者用户手动 attach 一次）

**步骤**

1. 主机侧检查（在本机执行，**全部只读**）：

   ```bash
   source spike/.env
   SSH="ssh -i $SPIKE_KEY -p $SPIKE_PORT $SPIKE_USER@$SPIKE_HOST"
   $SSH 'command -v herdr; herdr --version'          # 非交互 shell 的 PATH 可能不同，先确认二进制路径
   $SSH 'herdr --session '"$HERDR_SESSION"' status'
   $SSH 'herdr --session '"$HERDR_SESSION"' remote-api-bridge --check'          # 期望输出 herdr-api-bridge-v1
   $SSH 'printf "%s\n" "{\"id\":\"p1\",\"method\":\"ping\",\"params\":{}}" | herdr --session '"$HERDR_SESSION"' remote-api-bridge'
   # 期望：{"id":"p1","result":{"type":"pong","version":...,"protocol":22,"capabilities":{...}}}
   # 注意：capabilities 只是特性开关，不是方法列表（调研 v2.1 已修正）
   $SSH 'sshd -T 2>/dev/null | grep -iE "maxsessions|allowstreamlocalforwarding" || grep -rhiE "^\s*(MaxSessions|AllowStreamLocalForwarding)" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/ 2>/dev/null'  # 不加 sudo；拿不完整就以注释形式记下
   $SSH 'ls -l ~/.config/herdr/sessions/'"$HERDR_SESSION"'/herdr.sock'
   ```

   注意 macOS 主机的 socket 路径与 sshd 配置命令不同；先 `herdr status` 看实际路径，不要硬编码。

2. 用 OpenSSH 验证服务端是否允许 streamlocal 转发（方案 B 的前提）：

   ```bash
   SOCK_REMOTE="/home/$SPIKE_USER/.config/herdr/sessions/$HERDR_SESSION/herdr.sock"   # 按实际 home 修改
   ssh -i $SPIKE_KEY -p $SPIKE_PORT -N -L /tmp/herdr-spike.sock:$SOCK_REMOTE $SPIKE_USER@$SPIKE_HOST &
   sleep 1
   printf '%s\n' '{"id":"p2","method":"ping","params":{}}' | socat - UNIX-CONNECT:/tmp/herdr-spike.sock
   kill %1; rm -f /tmp/herdr-spike.sock
   ```

3. JVM 骨架（`spike/jvm`，依赖 `com.hierynomus:sshj`，版本用当前最新稳定版；`kotlinx-serialization-json`）。

   > **v2.1 重写（P0-1）**：原代码是 TOFU：未知 host 直接写盘并 `return true`。这是**禁止**的。下面改成 fail-closed：未知 host 必须拒绝，并把指纹交给上层 UI 让用户带外核对后**显式确认**，确认后才写入信任库。

   ```kotlin
   class HostKeyUnknownException(val host: String, val port: Int, val algorithm: String, val sha256: String) :
       IOException("unknown host key $host:$port $algorithm $sha256")
   class HostKeyMismatchException(val host: String, val port: Int,
                                  val pinned: String, val presented: String) :
       IOException("host key mismatch $host:$port pinned=$pinned presented=$presented")

   // 信任库：(host, port, algorithm) -> SHA256 指纹。首次不写入任何东西。
   class PinnedStore(private val file: File) {
       fun get(id: String): String? =
           file.takeIf { it.exists() }?.readLines()
               ?.firstOrNull { it.startsWith("$id ") }?.substringAfter(' ')
       fun pin(id: String, fp: String) = file.appendText("$id $fp\n")   // 只在用户显式确认后调用
   }

   class StrictPinnedVerifier(
       private val store: PinnedStore,
       private val onUnknown: (HostKeyUnknownException) -> Boolean,  // UI 阻塞式确认；用户点“信任”才 true
   ) : HostKeyVerifier {
       override fun verify(host: String, port: Int, key: PublicKey): Boolean {
           val alg = key.type.toString()
           val fp = SecurityUtils.getFingerprint(key)      // "SHA256:..."
           val id = "$host:$port:$alg"                     // 算法也算身份的一部分
           return when (val saved = store.get(id)) {
               null -> {
                   val ex = HostKeyUnknownException(host, port, alg, fp)
                   if (!onUnknown(ex)) throw ex              // 默认/用户拒绝 → 尽对失败
                   store.pin(id, fp)                          // 只有确认后才落盘
                   true
               }
               saved -> if (saved == fp) true else throw HostKeyMismatchException(host, port, saved, fp)
           }
       }
       // 不要返回空集合去“尝试所有算法”；显式声明可接受算法，避免降级到弱算法
       override fun findExistingAlgorithms(host: String, port: Int): List<String> =
           listOf("ssh-ed25519", "ecdsa-sha2-nistp256")
   }

   fun connect(ui: (HostKeyUnknownException) -> Boolean): SSHClient = SSHClient().apply {
       addHostKeyVerifier(StrictPinnedVerifier(PinnedStore(File("known_hosts.spike")), ui))
       connection.keepAlive.keepAliveInterval = 15
       val t0 = System.nanoTime()
       // 关键：首次连接在用户确认前不能 auth；connect() 抛 HostKeyUnknownException 后等用户确认再重试
       connect(env("SPIKE_HOST"), env("SPIKE_PORT").toInt())
       authPublickey(env("SPIKE_USER"), loadKeys(env("SPIKE_KEY")))
       log("connect+auth ms=${(System.nanoTime() - t0) / 1e6}")
   }

   // 每个请求一个 exec channel；JSON 走 stdin，不拼命令行
   fun request(ssh: SSHClient, json: String, timeoutMs: Long = 5000): String {
       val session = ssh.startSession()
       try {
           val cmd = session.exec("${shQuote(env("SPIKE_HERDR_BIN"))} --session ${shQuote(env("HERDR_SESSION"))} remote-api-bridge")
           cmd.outputStream.apply { write((json + "\n").toByteArray()); flush() }
           return cmd.inputStream.bufferedReader().readLineWithTimeout(timeoutMs)
               ?: throw TimeoutException("no response")
       } finally { session.close() }
   }

   fun shQuote(s: String): String {
       require(s.matches(Regex("[A-Za-z0-9._/-]{1,200}"))) { "refusing to quote: $s" }   // 白名单，路径也走这里
       return "'" + s.replace("'", "'\\''") + "'"
   }
   ```

   代码里 `SPIKE_HERDR_BIN` 取第 1 步 `command -v herdr` 的绝对路径（如 `/home/me/.local/bin/herdr`），不要依赖非交互 shell 的 PATH。

4. 测量：
   - 连接 + 认证：冷连接 20 次，记录每次耗时（含指纹校验路径）；
   - 单次请求：同一条连接上顺序发 100 次 `ping`，记录往返耗时；
   - 并发：同时开 1、4、8、10、12 个 exec channel 发 `ping`，记录**成功数、失败数、报错原文、连接是否仍然可用**（超过 MaxSessions 不应把整条连接弄死）；
   - 重连：Android 真机上切换 Wi-Fi ↔ 4G（或开关飞行模式）10 次，记录从断开到重新 `ping` 成功的耗时，并确认重连时**又跑了一次指纹校验**；
   - 指纹四组用例：
     | 用例 | 操作 | 期望 |
     | --- | --- | --- |
     | F1 未知 host | 删除信任库后连接 | 抛 `HostKeyUnknownException`，**且没发生 auth**，信任库仍为空 |
     | F2 显式确认 | F1 后用户确认 | 连接成功，信任库写入 `(host,port,alg,fp)` |
     | F3 指纹被篡改 | 手改信任库里的指纹 | 抛 `HostKeyMismatchException`，不连接、不改信任库 |
     | F4 算法变化 | 信任库只存 ed25519，服务端只提供 rsa（或反之） | 拒绝，不因算法不同就当成“新主机”自动信任 |
5. streamlocal（方案 B）：查 sshj 是否有 `direct-streamlocal@openssh.com` 的现成支持；没有的话，评估自己实现一个 channel 类型的工作量（不超过 2 小时的调研，不要求做完）。
6. 把第 3 步的代码搬到 Android 最小 App（一个按钮“Ping”，显示耗时；首次连接弹出指纹确认对话框，**不提供默认勾选“总是信任”**），在真机上重复第 4 步里“单次请求”的测量。

**通过标准**
- `ping` 能返回 `version`、`protocol`、`capabilities`（并记录 `capabilities` 的实际字段，验证“不是方法列表”）
- 局域网冷连接 + 认证 P50 < 1.5 s；真机单次请求 P50 < 150 ms、P95 < 300 ms（若 exec 启进程的开销占主导导致不达标，**先记录基线再重新论证目标**，不要闷头优化）
- 网络切换后 P95 < 3 s 恢复（这一项不达标只记录，不阻塞后续 Spike）
- 指纹：F1–F4 四组用例 **4/4 符合预期**，其中未知 host 自动信任 **0 次**
- 明确知道 MaxSessions 的实际值，以及超过时的报错表现，并给出客户端的排队/退避策略

**失败时的备选方案**
- 单次请求太慢（P95 > 500 ms）：评估方案 B（streamlocal）或方案 C（主机端一个小脚本，在一个 channel 上复用所有请求）
- sshj 在 Android 上有兼容性问题：换 Apache MINA SSHD 或 mwiede/jsch 重做第 3 步
- `remote-api-bridge` 不可用：用 `socat - UNIX-CONNECT:<sock>` 或 `python3` 一行脚本代替

**需要记录的日志和数据**
- herdr 版本、`ping` 的完整响应（保存到 `results/s1-ping.json`）
- sshd 的 `MaxSessions`、`AllowStreamLocalForwarding` 实际值
- `results/s1-latency.csv`：`run, kind(connect|ping|reconnect), network(lan|4g), device, ms`
- `results/s1-hostkey.csv`：`case(F1..F4), expected, actual, trusted_after(bool), evidence`
- 并发测试的报错原文
- sshj 版本、Android 版本、机型（真机 / 模拟器写清）

**建议用时**：0.5–1 天。**关闭的待验证项**：调研 v2 第 1、2 项。

---

## Spike 2：herdr socket API——订阅 / 快照 / 写操作 ★

**目的**：跑通“先订阅、再快照、事件只当失效信号”的对账流程；验证 `events_lost` 恢复；确认写操作（`agent.prompt`、`pane.send_keys`、`pane.send_text`）的语义和错误码；搞清 pane id 在移动、关闭后的变化。

**前置条件**：Spike 1 通过；spike 会话里至少有一个 shell pane 和一个运行 Claude Code 的 pane（`cd $SPIKE_REPO && claude`）。

> **实测提示（0.9.0，2026-10-29）**：herdr **0.9.0 没有 `remote-api-bridge` 子命令**（`unknown command`）。在那类主机上改用 `socat - UNIX-CONNECT:<sock>` 或 `nc -U <sock>` 传输 NDJSON，或直接用 SSH streamlocal 转发；本任务书里所有 `remote-api-bridge` 的地方都要先探测再降级（见 `DECISIONS.md` D11）。

**步骤**

1. 导出 schema，作为后续数据类和参数名的依据：

   ```bash
   $SSH 'herdr api schema --json' > spike/results/herdr-api.schema.json
   $SSH 'herdr --session '"$HERDR_SESSION"' api snapshot' > spike/results/s2-snapshot-cli.json
   ```

2. 订阅（一个长连接 channel，一直读）。

   > **v2.1 修正（schema 已核实）**：`pane.agent_status_changed` **必须带 `pane_id`**（逐 pane 订阅），而 `pane.created/closed/updated/exited` 这类全局事件只能写 `{"type":...}`，**多带字段不合法**。原来的示例把两者混在一起，是错的。

   先拉一次快照拿到现有 pane，然后两层订阅：

   ```json
   {"id":"sub_globals","method":"events.subscribe","params":{"subscriptions":[
     {"type":"pane.created"},{"type":"pane.closed"},{"type":"pane.updated"},{"type":"pane.exited"},
     {"type":"workspace.created"},{"type":"workspace.closed"},{"type":"tab.created"},{"type":"tab.closed"},
     {"type":"layout.updated"}
   ]}}
   ```

   ```json
   {"id":"sub_panes","method":"events.subscribe","params":{"subscriptions":[
     {"type":"pane.agent_status_changed","pane_id":"w1:p1"},
     {"type":"pane.agent_status_changed","pane_id":"w1:p2"}
   ]}}
   ```

   - 先确认首个响应是 `subscription_started`，再在另一个 channel 发 `{"id":"snap_1","method":"session.snapshot","params":{}}`；
   - 收到 `pane.created` 后**给新 pane 补一条** `agent_status_changed` 订阅；`pane.closed` 后退订（或重建订阅）；
   - 测一个**不存在的 pane_id**：记录是整条请求被拒还是只有那一条被忽略，以及连接是否被断开（调研 v2 的说法【待验证】）。

3. 实现 Reconciler（照调研 v2 “状态同步”一节的伪代码）：事件只设置 `dirty`；单协程串行刷新；刷新期间又来事件就再刷一次；每次刷新都用 `session.snapshot` 整体替换缓存。打印每次“事件到达 → 缓存更新完成”的耗时。

4. 端到端状态延迟：在主机上跑一个脚本，让 Claude 在 working → idle → blocked 之间切换（例如每轮让它执行一个需要权限的 `touch /tmp/spike-$i`），脚本在主机侧用 `herdr agent list` 或事件订阅打时间戳；手机侧记录收到并更新完的时间，算差值。
   - 事件载荷里有没有主机时间戳以 schema 为准（`EventEnvelope` 未包时间字段的话，就必须先估算时钟偏差）；
   - 优先用“**同一台主机打两个时间戳**”的做法（主机侧脚本写一行本地时间 + 触发事件，App 把收到的时刻回传），避免跨设备时钟；
   - 记录 `P50/P95/样本数`，不要只写平均值。

5. 制造 `events_lost`：订阅后**故意停止读取**订阅 channel 30–60 秒，同时在主机上用脚本快速创建、关闭 pane（先 `herdr pane --help` 确认参数，别凭猜）：

   ```bash
   for i in $(seq 1 200); do
     herdr --session "$HERDR_SESSION" pane split --direction right >/dev/null 2>&1
     herdr --session "$HERDR_SESSION" pane close >/dev/null 2>&1
   done
   herdr --session "$HERDR_SESSION" api snapshot >/dev/null   # 清理后核对
   ```

   恢复读取后，确认收到 `error.code = "events_lost"` 并且连接被关闭；验证自动恢复：重订阅 → 等确认 → 拉快照 → 缓存和 `herdr api snapshot` 一致。**跑 20 次**（不是 1 次），报成功率。

6. 写操作：
   - `pane.send_text` 发到 shell pane（运行 `cat`），确认文本出现；
   - `pane.send_keys`：`["esc"]`、`["up"]`、`["shift+tab"]`、`["ctrl+c"]`，以 schema 为准记录真实写法；
   - `agent.prompt`：`{"target":"<claude pane 或 agent id>","text":"say hi in one word"}`，以及带 `wait` 的版本（`until` / `timeout_ms`，schema：`AgentPromptWaitOptions`）；
   - 在 Claude 处于 `blocked` 时调用 `agent.prompt`，确认返回 `agent_blocked`，并且**没有**输入被发送（用 `pane.read` 前后对比）；
   - **超时 / 断线（P0-5）**：发出 `agent.prompt` 后立刻强制关闭 channel（或断网），App 必须标 `Unknown` 且**不重试**；然后做权威读取（快照 + transcript / `pane.read`）判定送达。重复 10 次，记录送达比例，并断言 `attempts == 1`。
   - **`agent.wait` 不是锁（P0-2，新增必测）**：两个客户端（两个 exec channel）同时对同一个 pane 做“`agent.wait` 等 idle → 立即 `pane.send_text`”，重复 10 轮，记录是否出现交错/覆盖。预期：**能交错**，证明它不提供互斥；这个结果要写进 `RESULTS.md`，以后不要再提“用 wait 加锁”。

7. 身份：
   - 记录 Claude pane 的 `pane_id`、`terminal_id`、`agent_session`、`foreground_cwd`；
   - 在电脑上把 pane 移到另一个 tab（`herdr pane move` 或 UI 操作），确认 id 是否变化，以及会收到哪些事件；
   - 关闭一个 pane 再新建，观察新 pane 会不会复用旧 id；
   - 在 Claude 里执行 `/clear`，确认 `agent_session.value` 是否更新。

8. 能力协商（v2.1 修正）：**不要**拿 `ping.capabilities` 当方法清单（它只是 6 个特性开关，schema 已核实）。正确做法：把 `herdr api schema --json` 的方法列表 + 协议号存成客户端内置的“版本→能力”表；运行时校验 `ping.protocol` 兼容性；对不支持的方法按普通错误降级（按钮变灰，不崩溃）。人为把某个方法从表里去掉，验证降级路径。

9. 只读终端（附加，可选）：`herdr --session $HERDR_SESSION terminal session observe <pane> --cols 60 --rows 30`，确认能收到 `terminal.frame`，并且**不会影响**电脑端正在 attach 的 herdr 界面。

**通过标准**
- 冷启动对账后，缓存和 CLI 快照**完全一致**（按 pane id、状态、agent_session 逐项比较）
- 局域网下状态变化 → 手机缓存更新 P95 < 1 s
- `events_lost` 能稳定复现（≥ 18/20），自动恢复成功（快照一致）率 ≥ 19/20，失败的用例要写原因
- `agent_blocked` 行为符合文档（含“确实没发送”的证据）
- 断线注入 10 次：App **0 次**自动重发，每次都能给出“已送达 / 未送达 / 仍未确认”三态之一
- 新 pane 的状态订阅不漏（`pane.created` 后 1 s 内收得到它的 `agent_status_changed`）
- 写清楚 pane id 在移动 / 关闭 / 新建时的行为
- `agent.wait` 并发实验已经有结论（预期：**不互斥**），并写进 `DECISIONS.md`

**失败时的备选方案**
- 全局订阅不支持：按 pane 订阅，并在 `pane.created` 后重建订阅
- 全量快照太慢（> 300 ms）：高频事件改成定向读取 `pane.get`
- `events_lost` 太频繁：减少订阅类型，只留状态和生命周期事件
- `agent.prompt` 不适合 Claude 的某些输入：退回 `pane.send_text` + `send_keys ["enter"]`

**需要记录的日志和数据**
- `results/s2-events.ndjson`：订阅收到的原始事件（带本地接收时间戳）
- `results/s2-latency.csv`：`event_type, host_ts, app_ts, delta_ms`
- `events_lost` 的复现参数、次数、每次恢复耗时
- 每种写操作的请求 / 响应原文、错误码、`attempts`
- `results/s2-write-unknown.csv`：`trial, method, delivered(bool|unknown), evidence, attempts`
- `results/s2-wait-not-lock.csv`：`trial, interleaved(bool), observed`
- schema 和本任务书参数名不一致的地方
- pane id / agent_session 变化的观察记录

**建议用时**：1–1.5 天。**关闭的待验证项**：调研 v2 第 3、4 项。

---

## Spike 3：定位 Claude Code 会话 JSONL ★

**目的**：在各种情况下都能准确找到 Claude 当前会话的 transcript 文件，对比三种方法的准确率：① 自装 `SessionStart` hook 记录 `transcript_path`；② 按 sessionId 用 find 搜索；③ 按 cwd 规则拼路径。

**前置条件**：Spike 2 通过；主机上已运行 `herdr integration install claude`，用 `herdr integration status` 确认版本；Claude Code 已登录。

**步骤**

1. 安装 hook（`spike/host/session-hook.sh`）。只做记录，不影响 Claude 的行为：

   ```bash
   #!/bin/sh
   # SessionStart hook：把官方给的 transcript_path 和 herdr pane 对应关系写到固定位置
   umask 077
   dir="$HOME/.local/state/herdr-android-spike/claude-sessions"
   mkdir -p "$dir"
   input=$(cat)
   sid=$(printf '%s' "$input" | python3 -c 'import sys,json;print(json.load(sys.stdin)["session_id"])')
   printf '%s' "$input" | python3 -c '
   import sys, json, os, time
   d = json.load(sys.stdin)
   out = {k: d.get(k) for k in ("session_id","transcript_path","cwd","source","hook_event_name")}
   out["herdr_pane_id"] = os.environ.get("HERDR_PANE_ID")
   out["claude_config_dir"] = os.environ.get("CLAUDE_CONFIG_DIR")
   out["ts"] = time.time()
   print(json.dumps(out))' > "$dir/$sid.json"
   exit 0
   ```

   安装方式（v2.1 收紧）：

   - **默认只改测试用的 `CLAUDE_CONFIG_DIR/settings.json`**（例如 `$HOME/.claude-spike`），不碰 `~/.claude`；
   - 无论改哪个文件，先 `cp settings.json settings.json.bak-$(date +%s)` 并记录 diff；脚本自身也用 `umask 077`、写入时先写 `.tmp` 再 `mv`（原子）；
   - 删除 `sid=...` 那行（`input` 会被 `cat` 消费两次，原来的脚本是错的）：直接一次性 `python3 -c` 读完并写文件；
   - 如果用户不允许改任何 Claude 配置，则本 Spike 的方法①只能标 `尚未验证`，不能假装跑过。

2. 准备 6 个场景（每个都在 herdr spike 会话的 pane 里启动 `claude`，并发一句话，让日志文件生成出来）：

   | 场景 | 做法 |
   | --- | --- |
   | S1 普通路径 | `cd $SPIKE_REPO && claude` |
   | S2 超长路径 | 建一个转换后超过 200 字符的目录，例如 `mkdir -p $SPIKE_REPO/$(printf 'a%.0s' {1..120})/$(printf 'b%.0s' {1..100})` |
   | S3 特殊字符 | 目录名含空格、中文、点号，如 `$SPIKE_REPO/中文 dir.v2` |
   | S4 自定义配置目录 | `CLAUDE_CONFIG_DIR=$HOME/.claude-spike claude`（目录要事先存在，路径用绝对路径） |
   | S5 换会话 | 在 S1 里执行 `/clear`，然后再用 `claude --resume` 恢复一个旧会话 |
   | S6 子 agent | 让 Claude 用子 agent（Task 工具）做一件小事，例如“用一个子 agent 列出当前目录的文件” |

3. 对每个场景，用三种方法分别定位：

   ```bash
   # 从 herdr 拿 sessionId 和 cwd（字段名以实际输出为准）
   herdr --session $HERDR_SESSION api snapshot | jq '.. | objects | select(has("agent_session")) | {pane_id, agent_session, cwd, foreground_cwd}'

   # 方法①：读 hook 记录
   cat ~/.local/state/herdr-android-spike/claude-sessions/<sid>.json

   # 方法②：find 搜索（候选目录：~/.claude，以及已知的 CLAUDE_CONFIG_DIR）
   for d in ~/.claude ~/.claude-spike; do find "$d/projects" -maxdepth 2 -name '<sid>.jsonl' 2>/dev/null; done

   # 方法③：按规则拼路径（只在转换后 <= 200 字符时有效）
   python3 - <<'PY'
   import re, sys
   cwd = sys.argv[1] if len(sys.argv) > 1 else "/path/from/foreground_cwd"
   name = re.sub(r'[^A-Za-z0-9]', '-', cwd)
   print(len(name), name if len(name) <= 200 else "(>200: needs hash, do not compute)")
   PY
   ```

4. 尝试读取 agent 进程的环境变量，判断手机端能否发现 `CLAUDE_CONFIG_DIR`：
   - 调 `pane.process_info`，看是否返回 pid；
   - 如果有 pid：`tr '\0' '\n' < /proc/<pid>/environ | grep CLAUDE_CONFIG_DIR`（仅 Linux 主机；macOS 主机记录“不支持”）。
5. 记录 S5 中 sessionId 的变化顺序：herdr 的 `agent_session` 什么时候更新？hook 记录什么时候出现？旧文件是否还在被写入？
6. 记录 S6 中子 agent transcript 的实际路径，和 `<session-id>/subagents/agent-<id>.jsonl` 的说法对比。
7. 测“首次提问前没有文件”的窗口：启动 Claude 后不提问，每 200 ms 检查一次文件是否存在，记录文件出现的时间点（相对于启动和第一次提问）。

**通过标准**
- 方法①在 6 个场景下 **6/6 正确**（S5 里能跟上会话切换）
- 方法②至少 5/6 正确（S4 允许失败，前提是方法①能覆盖）
- 从第一次提问到 App 拿到正确路径 < 2 s
- 明确子 agent transcript 的路径规则
- 明确手机端能否发现 `CLAUDE_CONFIG_DIR`

**失败时的备选方案**
- 用户不接受装 hook：方法② + 在主机设置页里让用户填额外的配置目录
- hook 不触发或字段缺失：改用 herdr-acp 的全局 hook（Spike 8），或者读 Claude 状态栏命令的输入（也带 `transcript_path`）
- 文件太晚出现：UI 在拿到日志前先显示终端只读预览

**需要记录的日志和数据**
- `results/s3-matrix.csv`：`scenario, method, found_path, correct(bool), latency_ms, note`
- 每个场景的 hook 记录文件（去掉无关字段）
- `CLAUDE_CONFIG_DIR` 能否发现的结论（Linux / macOS 分开）
- Claude Code 版本（`claude --version`）、herdr Claude 集成版本

**建议用时**：0.5–1 天。**关闭的待验证项**：调研 v2 第 5、6 项。

---

## Spike 4：增量解析 transcript 并渲染 ★

**目的**：通过 SFTP 增量读取 Claude JSONL，解析成 `TranscriptEvent`（支持更新、分支、重置），在 Android 上用 Compose 流畅地渲染出聊天视图；测出端到端延迟和解析性能。

**前置条件**：Spike 3 至少有一种方法能稳定定位文件；Android 最小 App 已经能连 SSH。

**步骤**

1. **收集样本**：从 Spike 3 的场景里复制 5–10 个 JSONL（覆盖普通对话、Bash 工具、文件编辑、图片、thinking、子 agent、`/compact`）。用脚本脱敏，把文本内容替换成占位符，但保留结构：

   ```bash
   python3 spike/host/sanitize.py in.jsonl > spike/samples/claude-<scenario>.jsonl
   # sanitize.py：递归遍历 JSON，把长度 > 0 的字符串值替换为 "<redacted:N>"，
   # 但保留 type / role / name / id / uuid / parentUuid / tool 名这些结构字段
   ```

2. **统计行类型**：写一个小脚本统计每个样本里出现的顶层 `type` 和 `message.content[].type`，生成 `results/s4-line-types.md`。这就是解析器要覆盖的清单，也用来关闭待验证第 7 项。

3. **解析器**（JVM 单元测试先行，`kotlinx.serialization` 的 `JsonElement` 手动解析，`ignoreUnknownKeys`）：

   ```kotlin
   class ClaudeJsonlParser(private val session: AgentSessionRef) {
       private val seen = HashMap<String, Long>()      // itemId -> revision

       fun parseLine(line: String): List<TranscriptEvent> {
           val obj = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrElse {
               return listOf(unknown("bad-json", line))
           }
           val uuid = obj["uuid"]?.jsonPrimitive?.contentOrNull ?: return emptyList() // 元数据行可以跳过并计数
           val parent = obj["parentUuid"]?.jsonPrimitive?.contentOrNull
           return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
               "user" -> mapUser(uuid, parent, obj)            // 文本 / tool_result / 图片
               "assistant" -> mapAssistant(uuid, parent, obj)  // text / thinking / tool_use；同一条消息可能分多行
               else -> listOf(unknown(uuid, line))             // 未知类型：折叠显示原始 JSON
           }
       }
       private fun upsert(id: String, parent: String?, item: TranscriptItem, final: Boolean) =
           TranscriptEvent.Upsert(session, id, parent, revision = (seen.merge(id, 1L, Long::plus)!!), item, final)
   }
   ```

   必须测到的情况：一条 assistant 消息被拆成多行（按 `message.id` 合并成同一个 item）；`tool_use` 和对应的 `tool_result` 配对（按 `tool_use_id`）；未知类型不抛异常；坏行（半行 / 非法 JSON）不抛异常。

4. **SFTP 增量读取**

   > **v2.1 重写（P0-6）**：原来的 `String(buf, UTF_8)` 会把跨块的多字节字符切坏，而且只比 `size`、无法区分截断与替换、没有偏移恢复、整文件读内存。按调研 v2“SFTP Tailer 契约”的 8 条实现，下面是最小骨架。

   ```kotlin
   data class FileIdentity(val size: Long, val mtime: Long, val headSha256: String, val inode: Long?)

   class SftpTail(
       private val fs: ByteSource,          // 接口：stat/readAt；生产实现包 SFTPClient，单测用本地文件
       private val path: String,
       private val cfg: TailConfig = TailConfig(),
   ) {
       private var offset = 0L
       private var identity: FileIdentity? = null
       private val pending = ByteArrayOutputStream()   // 只按字节缓冲
       private val decoder = Charsets.UTF_8.newDecoder()
           .onMalformedInput(CodingErrorAction.REPLACE)
           .onUnmappableCharacter(CodingErrorAction.REPLACE)

       fun poll(): TailResult {
           val st = fs.stat(path) ?: return TailResult.SessionLost      // 文件被删 / 会话断 → 重连，不当 Reset
           val id = fs.identity(path, st, cfg.headBytes)
           if (identity != null && id != identity) {
               reset("identity-changed old=$identity new=$id")
               return TailResult.Reset(reason = "identity-changed")
           }
           identity = id
           if (st.size < offset) { reset("truncated size=${st.size} offset=$offset"); return TailResult.Reset("truncated") }
           if (st.size == offset) return TailResult.Empty

           val lines = ArrayList<String>()
           var pos = offset
           while (pos < st.size) {
               val want = minOf(cfg.chunkBytes, st.size - pos)          // 1 MiB 上限
               val buf = ByteArray(want.toInt())
               var read = 0
               while (read < buf.size) {                                // read 可能短读，必须循环
                   val n = fs.readAt(path, pos + read, buf, read, buf.size - read)
                   if (n <= 0) break
                   read += n
               }
               if (read == 0) break
               pending.write(buf, 0, read); pos += read
               offset = pos
               drainLines(pending, lines)                               // 只在看到 \n 时解码整行
           }
           return TailResult.Lines(lines)
       }

       private fun drainLines(buf: ByteArrayOutputStream, out: MutableList<String>) {
           val bytes = buf.toByteArray()
           var start = 0
           for (i in bytes.indices) if (bytes[i] == '\n'.code.toByte()) {
               val line = bytes.copyOfRange(start, i).toString(Charsets.UTF_8).trimEnd('\r')
               if (line.isNotBlank()) out += line
               start = i + 1
           }
           buf.reset(); if (start < bytes.size) buf.write(bytes, start, bytes.size - start)
       }

       private fun reset(reason: String) { offset = 0; pending.reset(); identity = null; audit("reset", reason) }
   }
   ```

   实现时必须补齐（每条一个单测，证据写进 `results/s4-tailer-tests.txt`）：

   | 用例 | 造法 | 期望 |
   | --- | --- | --- |
   | UTF-8 跨块 | 让 1 个中文字符的 3 个字节分在两次 `read` 里 | 输出完整字符，行内容不变 |
   | 非法字节 | 写入 `0xC3 0x28`（非法序列） | 替换为 U+FFFD，不抛异常、不丢后续行 |
   | 截断 | `truncate(0)` 后写入新内容 | `Reset`，从 0 重读 |
   | 替换（更大） | 删文件 → 写一个**更大**的新文件（同路径） | `Reset`（靠 identity / 首字节指纹，不靠 size） |
   | 偏移恢复 | 重启进程，用持久化的 offset + 最后一行哈希 | 不重复不丢失；哈希不符则 `Reset` |
   | 大文件 | 100 MiB 文件，只取最后 2000 条 | 内存 < 200 MiB，首屏 < 2 s（尾部回填） |
   | 短读 | `ByteSource` 每次最多返回 7 字节 | 不丢数据、不卡死 |
   | 半行 | 写一半不带 `\n`，再补完 | 只在完整行才输出 |

   轮询间隔：活跃时 250 ms，空闲 10 秒后退到 2 s；**禁止重叠 poll**（前一未结束不发下一）。另外实现一个 `tail -c +<offset> -F` 的 exec 版本做对照（注意它会占一个 MaxSessions 名额）。

5. **渲染**（Android 最小 App）：一个 `LazyColumn`，`key = itemId`、`contentType = item 类型`；最后一条 assistant 文本用流式 Markdown 渲染；Bash 块可折叠；未知类型显示“原始 JSON”。网络增量先进 Channel，用 `withFrameNanos` 每帧最多提交一次状态。

6. **测量**：
   - 端到端：主机脚本每秒往一个测试 JSONL 追加一行（从样本回放，每行带主机时间戳），手机记录渲染出这一项的时间，算延迟；SFTP 和 `tail -F` 两种各跑 5 分钟；
   - 解析性能：JVM 上对全部样本跑 1000 次，统计每行平均和 P99 耗时；
   - 渲染：回放一个 2000 条消息的会话，在真机上快速滚动 30 秒，用 JankStats（或 Macrobenchmark，如果已经搭好）统计掉帧率；
   - 截断：运行中把测试文件截断为 0，确认收到 `Reset`，UI 正确重载。

**通过标准**
- 所有样本解析 0 崩溃；未知类型都有兜底显示
- 半行、截断、多行合并、tool_use / tool_result 配对全部有单元测试并通过
- 局域网端到端延迟 P95 < 1 s（SFTP，250 ms 轮询）
- 单行解析平均 < 1 ms、P99 < 5 ms（JVM）
- 2000 条消息快速滚动，掉帧率 < 3%（初步基线；正式目标 < 1% 留给 Spike 7 优化）

**失败时的备选方案**
- SFTP 延迟高或耗电大：改用 exec `tail -F`（注意它占 MaxSessions）
- 格式太不稳定：改走 ACP（Spike 8）作为主数据源
- 渲染掉帧：先把 Markdown 换成纯文本，确认瓶颈在哪，再交给 Spike 7

**需要记录的日志和数据**
- `results/s4-line-types.md`：行类型清单
- `results/s4-e2e.csv`：`method(sftp|tail), line_no, host_ts, render_ts, delta_ms`
- `results/s4-parse-bench.txt`：解析基准
- 渲染掉帧率、机型、刷新率
- 发现的格式特点（例如一条消息拆成几行、字段名）

**建议用时**：1.5–2 天。**关闭的待验证项**：调研 v2 第 7、8 项。

---

## Spike 5：审批安全——读屏校验 vs 结构化 hook

**目的**：量化 L2（读屏 + 校验）的**残余竞态风险**（不要求也不宣称 0），并验证 L1（Claude `PermissionRequest` hook）可以把审批搬到手机上。

> **v2.1 修正（P0-3 / P0-4）**：审批存在**不可消除**的 TOCTOU 窗口，本 Spike 的目标是“**测量窗口 + 证明拦截机制有效**”，而不是“证明不会答错”。

**前置条件**：Spike 2 通过。

**步骤**
1. **假 TUI 竞态脚本**（`spike/host/fake-prompt.sh`），必须**对抗性**而不是均匀随机：
   - 模式 A（坏运气）：问题在 0–50 ms 内换题（撞击窗口 B）；
   - 模式 B（跨窗口）：显示后 300–1500 ms 换题（撞击窗口 A）；
   - 模式 C（诱饵）：换题后保留**相同的选项文案**、但命令不同（专测“只比选项文案”的哈希设计）；
   - 脚本把 `(shown_prompt_id, received_key, key_at_prompt_id)` 写进日志；每种模式至少 100 轮。
2. App 端实现 L2：读屏（`revision₁`、`promptHash₁`）→ 展示 → 随机延迟（50–3000 ms）后点击 → 再读（`revision₂`、`promptHash₂`）→ 哈希一致才发数字键；同时记录 `T2→T3` 耗时。
3. 对照组：不做二次读取，直接发送。参数与轮数完全相同。
4. 真实 Claude：在 `$SPIKE_REPO` 里让 Claude 执行需要权限的无害命令（`touch /tmp/spike-perm-$i`），用 L2 流程回答 20 次；确认数字键能否直接选中选项，还是必须方向键 + Enter。**不批准任何网络访问、`rm`、`git push`**。
5. **L1 原型（必须满足 Hook 契约）**：按调研 v2“L1 结构化审批的 Hook 契约”实现：
   - 目录：`~/.local/state/herdr-android-spike/approvals/<request_id>/{req,answer,cancel}`，目录 `0700`、文件 `0600`、原子写；
   - `req` 必带 `request_id`(ULID)、`created_at`、`expires_at`（TTL 120 s）、`session_id`、`tool_use_id`、`tool_name`、`tool_input_sha256`、`nonce`；
   - `answer` 必带 `request_id`、`nonce`、`decision`，**读一次后立即删除**；nonce 不符 / 过期 / 哈希不符 → 丢弃并记日志；
   - 超时或收到 `cancel` → hook **退出码 0、不输出 decision**，把控制权交回 Claude 终端；
   - 并发：同时发起 3 个待批请求，确认互不干扰、不会串单；
   - 安装 hook：**先备份** `<配置目录>/settings.json`（优先用专用 `CLAUDE_CONFIG_DIR`，不动全局），matcher 只匹配 `Bash`，测完删除并恢复备份；
   - App 通过 SFTP 读 `req`、写 `answer`/`cancel`，并在展示前用 `tool_input_sha256` 校验自己看到的内容。

**通过标准**

| 项 | 要求 |
| --- | --- |
| 测试有效性 | 对照组在模式 A/B 下能观察到错题（否则测试没撞到窗口，要调参数重做） |
| L2 错题率 | **显著低于**对照组，并把两个数都报出来；**不要求 0** |
| L2 拦截率 | 哈希不一致时 100% 中止发送（这条必须是 100%） |
| 窗口 B | 局域网 `T2→T3` P95 < 300 ms；4G P95 < 1 s（超了就写入风险提示） |
| 无静默批准 | 全部路径（超时 / 过期 / 取消 / nonce 不符）0 次自动 allow |
| 真实 Claude | 20 次 2 次以上答错就说明 L2 不可用于生产，只能 L1/L3 |
| L1 | 批准 / 拒绝 10 次全部生效；过期 2 次、取消 2 次、并发 3 次、nonce 伪造 2 次全部符合契约；往返 P95 有数据 |

**失败时的备选方案**：L2 错题率不可接受 → Phase 2 只做 L1 + L3（不做读屏一键批准）；L1 的 hook 太脆弱 → 评估 herdr-acp（Spike 8）。

**需要记录**：`results/s5-race.csv`（`trial, mode, group, shown_prompt, answered_prompt, correct, aborted, t2_t3_ms`）、`results/s5-l1.csv`（`case, expected, actual, evidence`）、真实 Claude 的选项按键映射、hook 超时无输出时 Claude 的实际行为。

**建议用时**：1 天。**关闭的待验证项**：第 9、25、26 项。

---

## Spike 6：Android 后台、通知与网络切换

**目的**：确定 Phase 1 的后台策略，测出耗电和可靠性。

**前置条件**：Spike 1、2 的 Android 代码可用。

**步骤**
1. 实现三种模式：A 只在前台；B 离开后保活 90 秒；C 用户手动开启的前台服务（先用 `dataSync` 类型，同时查清 `specialUse` 等类型的 Play 政策要求）。
2. 每种模式下：锁屏 → 在主机上让 agent 进入 `blocked` → 记录本地通知是否送达、延迟多少。
3. 模式 C 连续跑 1 小时，记录耗电（`adb shell dumpsys batterystats`），并验证 `onTimeout` 回调（可以用 adb 命令缩短超时来测，具体命令查 Android 文档）。
4. 网络切换：Wi-Fi ↔ 4G 各 20 次，记录恢复时间和是否丢失状态变化（对账后能否补回当前状态）。
5. Doze：`adb shell dumpsys deviceidle force-idle`，观察连接和通知的表现。

**通过标准**：结论清楚（选哪种模式、文案怎么写）；模式 C 每小时耗电在可接受范围（目标 < 3%，以实测为准）；网络切换恢复 P95 < 3 s。

**失败时的备选方案**：前台服务不可行 → Phase 1 只做 A + B，文案上明确“不保证后台提醒”；真推送留到 Phase 3（主机 hook + 自建 ntfy / FCM）。

**需要记录**：每种模式的通知送达率和延迟、耗电数据、`onTimeout` 行为、机型和系统版本、厂商后台限制的现象。

**建议用时**：1 天。**关闭的待验证项**：第 10 项。

---

## Spike 7：Compose 渲染性能基线

**目的**：测出调研 v2“可量化目标”表的第一版基线，找到最大的性能瓶颈。

**前置条件**：Spike 4 的渲染原型。

**步骤**
1. 建 `:benchmark` 模块（Macrobenchmark）和 `:baselineprofile`。
2. 场景：冷启动到看板首帧（有缓存）；回放流式输出（每秒 50 个 token 级别的增量，持续 60 秒）同时滚动；5000 条消息的会话打开和快速滚动；键盘弹出 / 收起 20 次；图片全屏查看。
3. 每个场景分别在“有 / 无 Baseline Profile”下跑，至少 5 次迭代。
4. 附加：用 termlib 渲染 `terminal session observe` 的帧，测终端视图的掉帧率。

**通过标准**：每个指标都有数据；至少列出前 3 个瓶颈和改进方向；键盘动画 0 帧超过 16.7 ms 能达到，或者知道差在哪里。

**失败时的备选方案**：Markdown 渲染太慢 → 已完成的消息预渲染成 `AnnotatedString` 缓存；列表太重 → 拆分 item、减少嵌套布局。

**需要记录**：Macrobenchmark 的 JSON 输出、机型、刷新率、Compose BOM 和 Material3 版本。

**建议用时**：1 天。**关闭的待验证项**：第 11 项。

---

## Spike 8：ACP / herdr-acp 结构化通道评估

**目的**：判断 herdr-acp 能否作为 Phase 3 的结构化数据源（对话 + 审批），以及 ACP Kotlin SDK 能否在 Android 上使用。

**前置条件**：主机上可以安装 Node.js 22.18+（**先征得用户同意**）；Spike 1 通过。

**步骤**
1. 主机：`npm install -g herdr-acp`；`herdr-acp --help`；记录版本。
2. 在本机通过 SSH 跑 stdio：`ssh ... 'herdr-acp --herdr-session spike'`，手动发 ACP 的 `initialize`，记录 `_meta.herdr` 里的扩展列表。
3. `session/new`（在 `$SPIKE_REPO`）→ `session/prompt` 发一句话 → 观察 `session/update` 流。
4. 触发一次权限请求（无害命令），用 `session/request_permission` 回答。
5. 在电脑上手动启动的 Claude 会话：执行 `herdr-acp install-hooks`（**先备份 `~/.claude/settings.json`，并征得用户同意**），再用 `session/list` / `session/load` 接管，测试 `_session/tail`。
6. Android：新建模块引入 `com.agentclientprotocol:acp`，编译并跑通 `initialize`（通过 sshj exec 的 stdin/stdout）。
7. 测试结束后：`herdr-acp uninstall-hooks`，恢复设置。

**通过标准**：3–5 步都跑通；记录流式粒度（Claude 按段落）和延迟；明确 Kotlin SDK 在 Android 上是否可用；给出“采用 / 观望 / 放弃”的结论和理由。

**失败时的备选方案**：herdr-acp 不稳定 → 只借鉴它的 hook 思路，自己实现 L1 审批（Spike 5）；Kotlin SDK 不能用 → 自己写一个最小的 JSON-RPC 客户端。

**需要记录**：herdr-acp 和 Node.js 版本、完整的 ACP 消息记录（脱敏后）、遇到的错误、对主机环境的改动清单。

**建议用时**：0.5–1 天。**关闭的待验证项**：第 12 项。

---

## 结果记录模板

结果**不在本文件里填**，而是分别写到仓库根目录的四个交付文件：`RESULTS.md`（实测值 + 证据位置）、`DECISIONS.md`（确认的决策）、`ISSUES.md`（问题与复现）、`NEXT-STEPS.md`（后续建议）。下面的模板是它们的骨架。

### 总表（写进 `RESULTS.md`）

每条结论的 **状态** 只能是 `已验证` / `验证失败` / `尚未验证`。

| # | Spike | 状态 | 结论（通过 / 部分通过 / 不通过） | 关键数据 | 证据文件 | 采用的方案 | 遗留问题 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | SSH + herdr | | | 连接 P50 = ；请求 P50/P95 = ；MaxSessions = | `results/s1-*.csv` | A / B / C | |
| 2 | API 订阅 / 快照 / 写 | | | 状态延迟 P95 = ；events_lost 恢复 = /20；写操作自动重发 = 次 | `results/s2-*.csv` | | |
| 3 | 定位 Claude JSONL | | | 方法① = /6；方法② = /6；定位耗时 = | `results/s3-matrix.csv` | | |
| 4 | 增量解析 + 渲染 | | | 端到端 P95 = ；解析 avg/P99 = ；掉帧率 = | `results/s4-*.csv` | SFTP / tail | |
| 5 | 审批安全 | | | L2 错题率 = %；对照组 = %；拦截率 = %；L1 = /10 | `results/s5-*.csv` | L1 / L2 / L3 | |
| 6 | 后台与网络 | | | 通知送达率 = ；耗电 = %/h；恢复 P95 = | `results/s6-*` | A / B / C | |
| 7 | 渲染性能 | | | 首帧 = ；jank = ；键盘超时帧 = | `results/s7-*` | | |
| 8 | ACP / herdr-acp | | | 跑通步骤 = /5；SDK 可用 = 是 / 否 | `results/s8-*` | 采用 / 观望 / 放弃 | |

### 环境记录

| 项目 | 值 |
| --- | --- |
| 主机系统 / 架构 | |
| herdr 版本 / 协议号 / schema 导出时间 | |
| Claude Code 版本 / herdr Claude 集成版本 | |
| sshd 版本 / MaxSessions / AllowStreamLocalForwarding | |
| 手机机型 / Android 版本 / 刷新率（真机还是模拟器） | |
| JDK / Gradle / sshj / Compose BOM / Material3 版本 | |
| 网络环境（局域网 / 4G / VPN） | |
| 本次执行对环境的改动清单（包括回滚方式） | |

### 单个 Spike 的记录格式（写进 `RESULTS.md`）

```text
## Spike N：<名称>
- 状态：已验证 / 验证失败 / 尚未验证
- 日期 / 执行者：
- 做了什么（按步骤编号）：
- 数据文件：results/sN-*.csv
- 通过标准逐条对照：
  - [x] 标准 1：实际值 = …（证据：…）
  - [ ] 标准 2：未执行，原因 = …
- 意外发现：
- 关闭的待验证项：#…（结论：…；状态：…）
- 新增的待验证项：
- 对调研 v2 的修改建议：
```

### 待验证项关闭情况（写进 `RESULTS.md`）

| 调研 v2 编号 | 事项 | 状态（已验证 / 验证失败 / 尚未验证） | 结论 | 证据（文件 / 截图） |
| --- | --- | --- | --- | --- |
| 1 | sshj streamlocal | | | |
| 2 | bridge 延迟、MaxSessions | | | |
| … | … | | | |

## 给执行 AI 的总指令

> 你是这个项目的 Spike 执行者。先完整阅读 `herdr-android-client-research-v2.md`（v2.1）和本任务书；让用户填写 `spike/.env`（包括 `SPIKE_HOST_KEY_SHA256`），然后按 Spike 1 → 8 的顺序执行（1–4 是关键路径）。
>
> 硬性要求：
> 1. **每条结论只能写 `已验证` / `验证失败` / `尚未验证`**，并有可重放的命令和证据文件；没跑过的步骤**绝不得**写成已验证，也不得用“应该 / 估讦 / 大体上”代替数据。
> 2. **不修改生产 / 团队机器的 SSH 配置或 herdr 会话**；不改 herdr 源码；不为了跑通而关闭指纹校验或扩大权限。需要装软件、改配置、加密钥时，**先列变更清单、等批准**。
> 3. 写操作 **永不自动重试**；`Unknown` 不能被自动当成成功；任何审批路径都不允许静默批准。
> 4. 参数名、字段、订阅粒度以 `herdr api schema --json` 为准；和本文不符就如实记录并在 `ISSUES.md` 写复现方式。
> 5. 真机相关项（延迟、耗电、后台、键盘）必须用真机；只有模拟器就标 `尚未验证`。
> 6. 某个 Spike 超过建议用时的 1.5 倍还没结论，就记录现状和卡点，进入下一个。
>
> 交付：`RESULTS.md`、`DECISIONS.md`、`ISSUES.md`、`NEXT-STEPS.md` 四个文件，外加 `spike/results/` 里的原始数据。最后给一份不超过一页的总结：哪些路线确认可行、选定了哪些方案、还有哪些风险。
