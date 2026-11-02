# 架构

Kelpie 是一个纯客户端：没有自己的服务器，也不需要在开发机上常驻任何进程。
手机通过 SSH 连到每台开发机：调用 herdr 自带的接口拿到 agent 状态、发送指令；再用 SFTP 读 agent 的对话记录和审批请求。

```text
手机（Kelpie）                                   开发机
┌────────────────────────────┐   SSH exec    ┌──────────────────────────────┐
│ app：页面、通知、前台服务     │ ────────────▶ │ herdr --session s             │
│ core:data：多主机运行时       │   NDJSON      │   remote-api-bridge           │
│ core:herdr：协议、解析        │ ◀──────────── │   （状态、读屏、发送、事件）     │
│ core:ssh：连接、指纹          │               │                              │
│                            │   SFTP        │ ~/.claude/projects/  对话记录  │
│                            │ ────────────▶ │ ~/.codex/sessions/            │
│                            │               │ ~/.pi/                        │
│                            │               │ ~/.local/state/kelpie/approvals │
│                            │               │   审批请求（目录可配置）          │
└────────────────────────────┘               └──────────────────────────────┘
```

## 模块

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `app` | Activity、导航、页面（首页 / 对话 / 审批弹层 / 设置 / 主机编辑 / 配对）、通知、前台服务、`AppContainer`（手动依赖注入） | 下面全部 |
| `core:markdown` | 自己实现的 Markdown 解析和 Compose 渲染（选型见 [markdown-rendering.md](markdown-rendering.md)） | designsystem、herdr |
| `core:designsystem` | 颜色 / 字阶 / 间距 token、图标、基础组件（规范见 [design-spec.md](design-spec.md)） | Compose |
| `core:data` | 主机配置、本地缓存（Room）、草稿、审批、配对；`runtime/` 多主机运行时；`attention/` 首页排序 | herdr |
| `core:herdr` | herdr 协议（`HerdrApi` / `HerdrCodec`）、连接监督、快照对账、三种对话记录解析、写操作台账、中英文案（`L10n`） | ssh |
| `core:ssh` | sshj 封装、主机指纹信任（`HostKeyTrust` / `TrustStore`）、加密算法检查 | — |
| `benchmark` | Macrobenchmark：冷启动、长对话滚动 | 测 `app` 的 minified 包 |

`core:herdr`、`core:ssh` 和 `core:data` 里的逻辑大多是纯 Kotlin，单元测试不需要 Android 设备。

## 连接与状态

**传输**：`HerdrApi` 每个请求开一个新的 SSH exec 通道，运行 `herdr --session <s> remote-api-bridge`，按行收发 JSON。bridge 一个连接只处理一个请求。
订阅事件（`events.subscribe`）用的是一条长期打开的通道。

**每台主机一个运行时**：`Fleet` 按主机配置为每台主机创建一个 `HostRuntime`。主机增删改时，整张表在锁里一次性替换，旧的运行时和它的协程一起取消。
`ConnectionSupervisor` 负责连接、协议版本检查（必须是 22）、断线重连和退避。

**状态以对账为准**：
- 每 10 秒左右取一次 `session.snapshot`（全量 pane 列表），由 `SessionReconciler` 合并；
- herdr 的事件只当作“有变化了，早点刷新”的信号，不直接改状态。事件丢了、乱序了，下一次对账也会纠正过来。

**首页怎么排**（`core:data/attention`）：
- `AttentionTracker` 记录每个 agent 进入当前状态的时间和最近一次完成的时间，并产生状态转换事件；通知就是从这些事件来的。
- `HomeDerivation.derive` 是纯函数，把所有主机的 pane 分成四组：
  1. **在等你**（blocked），等得最久的排前面；
  2. **工作中**（working）；
  3. **刚完成**：状态是 done，或者 30 分钟内完成过、现在空闲；
  4. **空闲**：其余的，按主机和标题排。
- 连不上的主机，它的 agent 仍然显示（标记为最后看到的状态），排在同组可达主机的后面。

**在等你的卡片显示什么**（`Previews`）：只为在等你的 pane 读预览，避免首页给每个 agent 都去读对话记录。
- Claude Code / Codex 的终端确认框，从屏幕文字里识别出“要运行什么命令、为什么”；
- 其他情况读对话记录的最后几条。

## 对话记录

1. **定位文件**（`*SessionLocator` / `*SessionResolver`）：
   - 优先用 herdr 上报的会话 id（`agent_session`）去找文件；
   - 没有 id 时，按工作目录找最近的交互式会话；
   - 同一个目录下有多个 Codex 时不猜，只显示终端画面。
2. **增量读取**（`TranscriptTailer`，SFTP）：
   - 记住读到的字节位置，只在换行处前进；
   - 用文件开头的哈希判断文件有没有被替换，被替换或变短了就从头重读；
   - 每次最多读一段，长会话不会一次全拉下来。
3. **解析**：`TranscriptParser` 按路径选择 `ClaudeTranscriptParser`、`CodexTranscriptParser` 或 `PiTranscriptParser`，统一成消息、工具调用、工具结果。
   agent 注入的上下文（环境信息、AGENTS.md 等）会被过滤掉。
4. **缓存与显示**：解析结果存进 Room（`transcript_messages`、`transcript_cursor`），对话页只订阅 Room，网络层只负责把新读到的字节变成消息；所以离线也能看已经读过的历史。
   对话页的增量映射（`IncrementalConversationMapper`）只重新计算变化的部分，2000 条消息的对话也能流畅滚动。
5. **找不到对话记录时**，对话页显示终端最近的输出（`pane.read`）。

## 发送：写操作不是幂等的

往 agent 发一条指令，重发一次就可能执行两次。所以：

- `Sender` 给每次发送一个四态回执：**发送中 → 已送达 / 被拒绝 / 结果未知**。
- 连接在发送途中断开，就是“结果未知”：不自动重发，界面上也没有“重试”按钮。用户自己去对话里看发出去没有。
- 结果未知的记录由 `WriteLedger` 保存到 Room（`pending_writes`），App 重启后还在。
  用户关掉提示，或者再给同一个 agent 发新消息时，记录才被清掉。
- 快捷回复只填进输入框，不会直接发送。

## 审批

细节和实验记录见 [approvals.md](approvals.md)。

1. agent 要运行命令时，hook（`companion/hooks/kelpie_approve.py`）在开发机上写一个请求文件 `<id>.req`，里面有命令、工作目录和一个一次性随机串（nonce）。
2. hook 通过 herdr 给自己所在的 pane 挂一个 `sa_*` token。
3. App 在快照里看到 token 就立刻去读请求；另外也每 3 秒用 SFTP 轮询一次目录。
4. 用户按住“允许”600 毫秒或点“拒绝”，App 写 `<id>.answer`。
5. hook 核对请求 id 和 nonce 后，告诉 agent 允许还是拒绝。
6. 超时、核对失败、hook 出错，都退回 agent 自己的终端确认，**不会放行**。

请求目录按主机配置（主机设置 → 手机审批 → 请求目录，或者配对时 `kelpie_pair.py --approvals-dir`），默认 `~/.local/state/kelpie/approvals`，要和 hook 的 `KELPIE_APPROVALS_DIR` 一致。
`~/` 开头的路径连上后用 SFTP 的家目录展开（`ApprovalsPath`），所以 macOS 的 `/Users/…` 也行。回答写回读到请求的同一个目录。

App 端是 `ApprovalCenter`（读请求、写回答）和 `ApprovalSheet`（弹层）。读屏判断出来的“等待批准”只显示命令，不会显示成可以按的审批卡，因为那种请求没有 hook 接着。

## 配对

1. 开发机上运行 `companion/pair/kelpie_pair.py`：生成一把手机专用的 ed25519 密钥，把公钥加进 `authorized_keys`，再显示二维码。
   二维码内容是 `kelpie://pair?d=<base64url(JSON)>`，JSON 里有地址、端口、用户、会话名、herdr 路径、这台机器全部的主机指纹、私钥和过期时间。
2. 手机端 `Pairing.kt` 解析，`PairScreen` 显示确认页。不管从哪个入口进来（App 内扫码、从图片识别、系统相机打开链接），都必须在确认页按下按钮才写入。
3. 指纹来自开发机自己显示的二维码，视为已经核对过，直接固定。和已保存的指纹不一致时拒绝，不覆盖。

## 本地存储

| 内容 | 位置 |
| --- | --- |
| 主机配置 | SharedPreferences `herdr-hosts`，JSON，只存非敏感字段（`HostRepository`） |
| 私钥 | `filesDir/keys/<hostId>.key`，权限只允许 App 自己读 |
| 已信任的主机指纹 | `filesDir/trust/<hostId>.known_hosts`。不读也不写系统的 known_hosts 和 ssh config |
| 首页缓存、对话缓存 | Room：`kelpie-board.db`（panes、session_meta、transcript_messages、transcript_cursor） |
| 草稿、结果未知的发送 | Room：`kelpie-drafts.db`（drafts、pending_writes） |
| 设置（通知开关、主题等） | SharedPreferences `kelpie-prefs` |

清单里关闭了系统备份（`allowBackup="false"`），私钥不会进云备份。

## 后台与通知

- App 进入后台时，如果开着通知，`MonitorService` 以前台服务（dataSync 类型，一条低优先级常驻通知）维持连接。不开前台服务的话，MIUI 会把后台进程冻结，状态转换就检测不到了。
- `Notifier` 订阅 `Fleet` 的状态转换：agent 开始等你、或者完成时发通知；App 在前台时不发。点通知直接打开那个对话；锁屏上只显示不含内容的版本。
- Android 15 起 dataSync 前台服务每天有 6 小时配额，用完系统会停掉服务，要等下次打开 App。
- 没有推送：App 被系统彻底杀掉后收不到提醒。方案对比见 [push-options.md](push-options.md)，目前决定不做。

## 安全上的几条约定

- **主机指纹**：第一次连接要人工核对（或者来自开发机自己显示的二维码），不会自动信任。指纹变了就拒绝连接，不会覆盖。
- **私钥**：每台手机用自己的一把密钥，可以在开发机上单独撤销（`kelpie_pair.py --revoke`）。二维码里有私钥，10 分钟后手机不再接受；看完要清屏。
- **审批**：任何异常都退回终端确认。允许要按住，防止误触；读屏用户走确认对话框。
- **调试入口**：`provision.json` 导入只存在于 debug / minified 包；正式包里是空实现。

## 多语言

所有文案写成 `t("中文", "English")`，单复数用 `plural(n, "agent")`，都在 `core/herdr/L10n.kt`。纯 Kotlin 模块里的时长、错误提示也用它。
语言跟随系统，或者 Android 13+ 的“应用语言”设置；不是中文就显示英文。
CI 用 `tools/i18n_literals.py --check` 检查有没有漏包的中文字面量。

## 测试

| 层 | 内容 |
| --- | --- |
| 单元测试（JVM） | 协议编解码、三种对话记录的解析和定位、增量读取、首页排序和时长、写操作台账、配对链接、Markdown 解析和表格、对话分支和搜索、配色对比度（日夜两套） |
| 截图回归 | Roborazzi + Robolectric，首页 5 种状态（日间 / 夜间 / 中英文 / 离线），JVM 上跑，不需要设备 |
| 性能 | Macrobenchmark（模拟器）；真机上用 `FixtureActivity` 的 2000 条消息测滚动，JankStats 打到 logcat |
| 真机 | MIUI 手机连两台测试主机手动走查；minified 包和正式包用同样的 R8 配置 |

## 已知限制

- **审批**：
  - 还没有安装脚本，hook 要手动配置。
  - Pi 还不支持审批。
  - 请求是否过期，用的是手机时钟和开发机写的过期时间比较。两边时钟差到分钟级会判断错。
- **herdr 版本**：App 只接受协议 22。herdr 升级改了协议号，App 要跟着改。
- **对话记录**：只在默认目录里找。用了自定义配置目录（`CLAUDE_CONFIG_DIR`、`CODEX_HOME`）的 agent 找不到。
- **通知**：没有推送；App 被系统杀掉后收不到提醒。
