# Signal 重写 · 运行日志

按 [remote-scope.md](remote-scope.md) 执行。这里记录每一次远端写操作和每个阶段的自查结论。

## 远端写操作

| 时间 (2026-11-02) | 主机 | 目标 | 操作 | 结果 |
|---|---|---|---|---|
| 23:10 / 23:11 | .2 / .3 | — | 在手机上信任主机指纹（ED25519，先用 `ssh-keyscan \| ssh-keygen -lf -` 核对一致） | 写入 App 私有 known_hosts，不改主机 |
| 23:20:12 | .2 | w8:p1（沙箱 Pi） | `agent.prompt` "reply with just SIGNAL-OK" | 已送达；Pi 回复 SIGNAL-OK；会话文件里只出现 1 次 |
| 23:22 | .2 | ~/spike-sandbox/approvals | 手工放一条 `ls -la` 请求（带 nonce）→ 手机上短按（未批准）→ 按住 1 秒批准 | `.answer` 为 allow，nonce 匹配；测试文件已删 |
| 23:24 | .2 | ~/spike-sandbox/approvals | 同上，手机上拒绝 | `.answer` 为 deny；测试文件已删 |
| 23:29–23:36 | .2 | w8:p1（沙箱 Pi） | 从电脑上通过 bridge 发 `agent.prompt` "reply with just NOTIFY-OK[-2…6]"，共 6 次，用来在 App 处于后台时触发“完成” | 每次都回复对应文本；第 6 次时后台通知送达 |
| 00:02–00:15 | .2 | ~/spike-sandbox/approvals | 手工放一条长命令的 `ls -la … && cat README.md \| head -50` 请求（`layoutcheck0001`），只用来检查卡片和审批弹层在不同宽度 / 字号下的排版；**没有回答** | 检查完已删除 |
| 00:32 | .2 | ~/spike-sandbox（无头，不经 herdr） | 新建隔离配置目录 `~/spike-sandbox/.claude-signal`；`claude -p "reply with just KEY-OK"`，OpenRouter + anthropic/claude-haiku-5.5，key 只从 .2 环境变量读取 | 回复 KEY-OK；`~/.claude` 未改动 |

仓库目录下的 agent（w7 / w9 / wA / wB）全程只读。

## 阶段自查

### P0 脚手架
- `signal/` 新工程，旧 `android/` 未改动；core:ssh / core:herdr 原样复制，39 个测试通过。
- 设计系统 token 与规范一致；ContrastTest 日间 / 夜间全部通过。

### P1 首页
- 真机（MIUI，22021211RC）连上两台主机，6 个 agent 全部显示。
- 发现并修复：两台主机同时要求确认指纹时只能存一个 → 改为队列；指纹弹窗补上算法名。
- 发现并修复：行间距被 LazyColumn 统一间隔放大；展开“空闲”后标题重复；同名 pane 无法区分（补 pane 编号）。
- 截图偏灰是 MIUI 纸质护眼滤镜（整体暗约 3%，有颗粒纹理），不是代码问题。

### P2 设置
- 主机列表、主题切换（立即生效，系统栏跟随）、通知开关、版本信息；夜间模式检查通过。

### P3 对话 / 发送 / 审批
- 修复：对话页首次组合时读取了未初始化的属性导致崩溃（start 改为组合前同步调用）。
- 发送回执链路真机验证（发送中 → 已送达 → 工作中 → 已完成，输入框清空，草稿删除）。
- 修复：已回答但请求方还没清理的 `.req` 会在下次轮询时重新出现 → 跳过已有 `.answer` 的请求。
- 修复：夜间模式下次要按钮与弹层同色。

### P4 通知
- App 在后台时，状态转换协程不运行：MIUI 把缓存进程冻结了 → 加前台服务（dataSync，低优先级常驻通知），只在前台时启动。
- 运行时权限已授予，MIUI 仍把应用通知设为关闭（importance NONE，appop ignore）→ `Notifier.allowed()` 同时检查 `areNotificationsEnabled()`，设置页显示提示并跳到系统通知设置。
- 系统在通知权限变更时会杀掉 App 进程（ActivityManager: PermissionHelper），需要重新打开一次。
- 验证：后台收到“spike-sandbox 完成了 · Pi · MS-7E07”，点通知直达该对话；锁屏只显示公开版本。

### P4 离线
- 把 .3 的端口临时改成 2（只改 App 本地配置）：横幅“连不上 10.144.144.3 · 连接被拒绝 · N 秒后第 K 次重连”，副标题“1 台在线 · 1 台连不上”，.3 的 agent 显示虚线圆 +“最后看到”；改回 22 后自动恢复。
- 横幅原来只写“重连中（第 3 次）”，改为带上翻译过的失败原因（拒绝 / 超时 / 网络不通 / 认证失败 / 找不到地址 / 指纹不一致）。
- “手机没有网络”分支没有在真机上验证：手机走 Wi‑Fi 调试，断网会断开 adb。逻辑只依赖 ConnectivityManager 的默认网络回调。

### P5 R8 / 性能（第一轮）
- release 开启 R8 + 资源收缩；sshj / BouncyCastle / eddsa 整包 keep（反射加载算法和 JCA provider），导航路由序列化器 keep。
- 新增 `minified` 构建类型：release 语义（R8、不可调试）+ debug 的 adb 导入钩子，包名 `.min`，只用于真机验证，不发布。
- 真机（minified）：两台主机指纹确认 → 连上 → 6 个 agent → 打开 spike-sandbox 对话读出 transcript，全程无崩溃。
- APK：debug 17.6 MB → release 5.2 MB。
- 冷启动（am start -W，4 次）：debug 640–657 ms → minified 334–382 ms。
- 对话页来回快速滑动 12 次：852 帧，janky 2.35%，p50 6 ms / p90 9 ms / p99 15 ms。

### P5 基线配置（baseline profile）
- 没用 Macrobenchmark：旧项目已验证 MIUI 的后台启动限制会让 instrumentation 启动目标 Activity 失败。改为：在 debug 包上手动走一遍首页 → 对话 → 滑动 → 设置 → 空闲列表，`cmd package dump-profiles` 导出 ART 配置，用 `signal/tools/profdump2baseline.py` 转成 `app/src/main/baseline-prof.txt`（9680 条，只保留自家代码、sshj、BouncyCastle、coroutines、navigation、room、serialization；Compose 自带）。
- 冷启动 verify vs speed-profile 各 5 次：307–374 vs 308–343 ms，基本持平（这个体量下主要是进程创建）。
- 首次打开对话后快速滑动（2 轮）：janky 2.96% / 2.76% → 1.17% / 1.17%，p99 20 / 22 → 14 / 16 ms。
- 截图测试：Roborazzi / Paparazzi 依赖 Robolectric / layoutlib，目前对 compileSdk 37 + AGP 9.2 的支持不确定，本轮不引入；视觉回归继续用 `signal/tools/shots.sh` 在真机上截取关键页面（日 / 夜）人工对照。

### P5 长对话 + JankStats
- 新增 `FixtureActivity`（debug / minified）：确定性 500 / 2000 条消息（Markdown、长代码、表格、工具、思考、超长路径），直接渲染对话页的 `MessageList`。
- 新增 `JankMonitor`（JankStats）：按界面（home / conversation / settings / host-edit / fixture）分桶，Activity 停止时 logcat 打一行汇总，tag `SignalJank`。
- minified + speed-profile，上翻 10 次再下翻 10 次：
  - 500 条：1046 帧，gfxinfo janky 2.20%，p50 7 / p90 11 / p99 16 ms；JankStats 0 帧
  - 2000 条：1080 帧，gfxinfo janky 2.87%，p50 7 / p90 10 / p99 19 ms；JankStats 1 帧（0.1%）
- 结论：2000 条下 p99 仍在 120 Hz 两帧以内，没有需要专门优化的点。P5 完成。

### P6 收尾
- 中英文案：`L10n.t(zh, en)`；真机切到 en-US（应用语言）逐页检查，补了英文单复数。
- 宽度 × 字号矩阵（wm density 480/440/419 ≈ 360/393/412dp × font_scale 1.0/1.3/2.0），首页带一张长命令权限卡：
  - 修：开关块在 360dp/2.0× 时数字掉到第二行 → 标签可省略、数字永不截断；
  - 修：副标题折行时圆点错位 → 对齐第一行；
  - 修：一台都没连上时副标题写"有新情况会通知你" → 改为"正在连接 N 台主机…"；
  - 修：审批弹层在 2.0× 时按钮被挤出屏幕且不能滚动 → 详情可滚动、拒绝 / 按住允许固定在底部；
  - 修：冷启动时权限卡要等 ~10 秒、重连瞬间会闪没 → 主机上线立即轮询；读不到时保留上次结果。
- 夜间：系统夜间模式下"跟随系统"即时切换，四个页面截图正常。
- 读屏：真机开 TalkBack 会先弹出它自己的教程，盖住整个屏幕，无法用 adb 继续操作，已立即关闭并恢复原来的无障碍服务设置。改用 UI 层级检查：拒绝 / 按住允许都以可点击节点暴露给无障碍服务；按住允许的无障碍点击走确认对话框（代码路径）。**完整的 TalkBack 实操没有做。**
- 标志：`signal/art/logo.svg` + `logo-512.png`（取启动器可见的 72dp 区域）。
- 通知权限：首次打开空 App 不再立刻弹系统授权，等有了主机后回到 App 时再问。
- 发布：versionName 0.2.0 / versionCode 2；release 5.2 MB，**debug 签名**（仓库没有发布密钥，未擅自生成）；全新安装后启动正常。全部单元测试通过（各构建变体合计 121 次运行，0 失败）。
- 设备状态已复原：density / 字号恢复默认、应用语言恢复跟随系统、主题"跟随系统"、"任务完成时通知"关回默认、测试用审批请求已删除。

### 第 9 节验收清单

| 条目 | 状态 |
|---|---|
| 1.3 对比度组合进 `ContrastTest`，日夜都通过 | ✅ 单元测试 |
| 360/393/412dp × 1.0/1.3/2.0 截图，等待时长和审批命令不被截断 | ✅ 等待时长全部完整；卡片上命令最多 6 行后省略，审批弹层里完整显示（2.0× 下可滚动） |
| 日间 / 夜间 / 跟随系统即时生效，不重启 Activity | ✅ 真机 |
| 读屏判断的 blocked 不显示成权限卡 | ✅ 结构上保证：权限卡只来自审批目录里的结构化请求 |
| 快捷回复只填输入框 | ✅ 代码 + 真机 |
| 结果未知的发送没有"重试" | ✅ 代码 |
| 按住允许：松手不触发；TalkBack 走确认对话框 | ✅ 短按不批准（真机）；TalkBack 路径只核对了代码和无障碍节点，⚠️ 未实操 |
| 离线时"实时"消失，时间改为"N 分钟前" | ✅ 主机连不上（真机）；⚠️ "手机没网"分支未在真机验证（断网会断 adb） |
| 进度环只在可见时转，"移除动画"时静止 | ✅ 代码（读取 ANIMATOR_DURATION_SCALE；角度只在绘制阶段读取） |
| 不用文字当图标、不用未换肤的系统对话框 | ✅ 四个对话框都用 Signal 颜色 |

### 留给之后
- 正式发布密钥（`signal/keystore.properties`）。
- 非 MIUI 设备 / 模拟器上补：TalkBack 实操、"手机没网"、Macrobenchmark。
- 支线：herdr 远程端点审计、Claude 审批 hook 身份实验，本轮都没有开始。

## 夜间批次（2026-11-03）

用户授权（00:2x）：OpenRouter key 用 .2 环境变量里的 `OPENROUTER_API_KEY`（禁止复制 `~/.claude/.credentials.json`）；允许在 .2 `test` 会话里新建 1 个沙箱 pane（用完关闭）；发布密钥由我生成；模拟器镜像随意下载。费用自限：OpenRouter 整晚 ≤ 2 美元（起点 usage = 0.1115）。

### 审批实验期间的远端写操作（.2，全部在 ~/spike-sandbox 或新建的沙箱 pane）

| 时间 | 目标 | 操作 | 结果 |
|---|---|---|---|
| 00:35 | ~/spike-sandbox/.claude-signal | 写 `settings.json`（PreToolUse → `signal_approve.py`；SessionStart → 引用 herdr 现有集成脚本，不改它）、`signal_approve.py`、`run-claude.sh` | — |
| 00:35 | 无头 claude | `claude -p` 让它跑 `pwd`；手机按住允许 | 执行，输出沙箱路径 |
| 00:37:34 | `test` 会话 | **新建 tab `w8:t2`（pane `w8:p2`）**，cwd ~/spike-sandbox，标签 signal-claude（授权的 1 个沙箱 pane） | — |
| 00:37–00:38 | w8:p2 | `pane.send_text` 启动 `run-claude.sh`；首次引导按键（Enter×3、Down+Enter 选"信任此目录"；第一次误回车到"No, exit"后重启） | Claude 就绪 |
| 00:38–00:54 | w8:p2 | `agent.prompt` 共 8 次，内容都是"用 Bash 运行 pwd / 并发运行 pwd 和 ls"之类 | 见实验文档 |
| 00:38–00:54 | approvals | 手机回答：允许 pwd ×3、拒绝 pwd ×1、允许 pwd + 拒绝 ls（并发） | Claude 均按回答执行 / 不执行 |
| 00:41、00:45、00:52 | w8:p2 | `pane.send_keys Escape`（取消等待中的审批 / 取消终端确认框） | — |
| 00:44 | w8:p2 | 超时退回终端后 `send_keys Enter`（选 Yes，命令是 pwd） | 执行 |
| 00:45 | approvals | 手工写一个 nonce 错误的 `.answer`（伪造回答测试） | 被拒收，退回终端 |

OpenRouter 用量：0.1115 → 0.1220 美元。沙箱 pane `w8:p2` 暂时保留给后面的实验，结束时关闭。

### 扫码配对测试（01:15–01:21）

| 时间 | 目标 | 操作 |
|---|---|---|
| 01:15 | .2 ~/spike-sandbox | 复制 `signal_pair.py`，运行三次（`--install no`：生成的 ed25519 密钥**没有**加入 authorized_keys；authorized_keys 的 sha256 前后一致）。生成 `pair-test.png`，测试后删除；脚本也已删除 |
| 01:15 | .2 /etc/ssh | 只读：复制三个 `ssh_host_*_key.pub`（公钥）到本机 scratchpad，用来拼测试链接 |
| 01:16 | 手机 | 推送二维码到 /sdcard/Pictures，从系统照片选择器只选了这一张，识别后在确认页**取消**；随后删除图片 |
| 01:17–01:18 | 手机 App（debug） | 深链（不带私钥）→ 更新 MS-7E07：私钥未变（md5 相同），信任文件从 1 条 ed25519 补到 ed25519 + ecdsa + rsa 三条，指纹与主机一致；之后两台主机都在线 |
| 01:19 | 手机 App | 过期链接 → 错误页；伪造 ed25519 指纹 → 标红并禁用按钮。都没有写入 |
| 01:20 | 手机 | 临时 `pm grant` 相机权限，打开扫码页确认能启动，随即 `pm revoke`（恢复为 granted=false） |
| 01:21 | 手机 App（minified） | 深链一个不存在的主机、不带私钥 → 正确阻止添加，未写入 |

发现并已修复：多网卡机器默认地址选错（改为列出候选、提示 `--host`）；MainActivity 有两个实例时深链被后台实例消费（改为 singleTask）；配对完成后返回首页被二次弹栈导致白屏；配对链接不再放进导航参数（避免私钥进入 saved state）。

### Claude 阻塞预览验证（01:24–01:28）

| 时间 | 目标 | 操作 |
|---|---|---|
| 01:24:04 | w8:p2 | `agent.prompt`："Please use the Bash tool to run: ls"；手机上不回答，120 秒后 hook 退回终端确认，herdr 判为 blocked |
| 01:28 | w8:p2 | `send_keys Escape` 取消终端确认框（ls 没有执行） |

发现并已修复：
- 会话文件预览会拿**上一轮**的助手回复充当问题（Claude 弹权限框时这一轮通常没有正文）→ 只看最后一条用户消息之后；待批准的工具调用显示工具名 + 命令。
- 读屏预览只取最后 8 行，Claude 确认框里有用的内容被“1. Yes / 2. No / Esc to cancel”挤掉 → 识别 Claude 确认框，只保留标题（如 “Bash command”）和框内容。
- 卡片右上角“等了 等待中”→ 只知道下限且不到 1 分钟时显示“刚发现”。

### 模拟器（01:33–02:05）

在本机 AVD（API 36.1）上：用 `luxiang_rsa` 配置两台主机（指纹人工核对：.2 `lfFYDn…G0T8`、.3 `I70h1U…x2is`），测完卸载 debug 包、关闭模拟器。TalkBack 打开过，已恢复（enabled_accessibility_services 为空）。

- 手机断网横幅：飞行模式 → 几秒内出现横幅、列表变“最后看到”；关闭飞行模式 15 秒内恢复在线。
- 无障碍：uiautomator 节点树审计（首页、对话、对话菜单、设置、添加主机）。修了添加主机页 5 个无名输入框。adb 注入的手势 / 快捷键都不被 TalkBack 当作导航，**真人 TalkBack 手势走查没做**。
- Macrobenchmark（minified 包，模拟器软件渲染，只看趋势）：冷启动 TTID 中位数 395 ms（无基线配置）→ 365 ms（带基线配置）；2000 条长对话滑动帧 CPU p50 14 ms / p90 21 ms / p99 31 ms。

## 早报（2026-11-03 02:10）

夜里的清单全部做完了，每项都提交在 main 上（没有推送）：

| # | 事项 | 结果 | 提交 |
|---|---|---|---|
| 1 | herdr 远程端点审计 | `docs/herdr-api.md`：现有 bridge 足够，缺的“给 pane 挂结构化信息”herdr 已有（`pane.report_metadata` tokens + `HERDR_PANE_ID`） | 4cb9145 |
| 2 | Claude 手机审批 | 端到端可用：允许 / 拒绝 / 并发两个调用 / 电脑上 Esc / 120 秒超时退回终端 / 伪造回答被拒。实验和待决定事项见 `docs/approvals.md` | 4cb9145 |
| 3 | 发布签名 + CI | 自己生成了发布密钥；`.github/workflows/android.yml`（测试、lint、截图对比、构建、i18n 检查），没推送所以没跑过 | 2ec53f7 |
| 4 | 对话页“更多” | 对话内搜索（计数 + 上下跳转）、复制会话路径 / 工作目录、工具完整输出（可选中、可复制） | 2fd03d4 |
| 5 | 推送方案 | `docs/push-options.md`：建议先 ntfy、以后 UnifiedPush，不用 FCM（MIUI 国行） | 2fd03d4 |
| 6 | 扫码配对 | `signal/companion/pair/signal_pair.py`（纯标准库，自带 QR 编码器）+ App 扫码 / 相册识别 / 深链，统一进确认页 | b4a5c20 |
| 7 | Claude 阻塞预览 | 修了：不再拿上一轮回复当问题；能认出 Claude 确认框，显示“等待批准 · Bash command”+ 命令 | be0d75c |
| 8 | Roborazzi | 能用（需 1.76 + Robolectric 4.17），首页 5 张基准图，第一次对比就抓到一个文案问题 | 3ea5b11 |
| 9 | 模拟器三项 | 断网横幅 ✅、无障碍审计 ✅（真人 TalkBack 走查 ❌）、Macrobenchmark ✅ | 6d8066c, ab879ba |

**需要你做 / 决定的：**

1. **备份发布密钥**：`signal/signal-release.jks` + `signal/keystore.properties`（都被 gitignore，只在这台电脑上）。丢了就没法再发同签名的升级。手机上现有的 `dev.herdr.signal` 是 debug 签名的，换正式签名要先卸载（会清数据），我没动。
2. **审批要不要装到真实的 Claude 上**：现在只在隔离配置里。需要在 `~/.claude/settings.json` 加 PreToolUse；还要定“拦哪些工具”“离开电脑时才启用？”“电脑前的人怎么自己批”——见实验文档结论部分。
3. **推送**：要不要搭 ntfy，放哪台机器。
4. **真人 TalkBack 走查**：手机上开 TalkBack，把首页 → 对话 → 审批弹层 → 设置走一遍，大约 5 分钟。
5. 手机息屏时间现在是“永不”（夜里 00:30 时还是 30 分钟）。我没改过这个设置，如果不是你改的请看一下。

**资源：** OpenRouter 用量 0.1115 → 0.1256 美元（整夜约 1.4 美分）。.2 的 `test` 会话里新建的沙箱 tab `w8:t2` 已关闭；`~/spike-sandbox/.claude-signal`（隔离配置，含 hook）保留着，以后还能接着实验，不要了可以直接删。没碰过 `~/.claude`、authorized_keys、herdr 配置和其他会话。

## Codex 适配（2026-11-03 06:00–06:35，用户确认后进行）

用户 06:1x 确认：可以在 `test` 会话新开 1 个沙箱 pane；Codex 用 OpenRouter 登录（不复制 `~/.codex/auth.json`）。

**提交：** ec15f78、0c31ffd、10e3e71（读 Codex 会话文件）、3061534（Codex 手机审批），以及本条文档。

### 远端操作（.2）

| 时间 | 目标 | 操作 |
|---|---|---|
| 06:0x | `~/.codex` | 只读：统计 `sessions/` 下的 rollout 格式；读 `hooks.json`、`config.toml` 前 20 行、herdr 的 `herdr-agent-state.sh`；`strings` 查 codex 二进制里的 hook schema。复制 30 个 rollout 到本机 scratchpad 做解析测试，测完已删除 |
| 06:18 | `~/spike-sandbox/.codex-signal`、`~/spike-sandbox/codex-work` | 新建隔离 CODEX_HOME：config.toml（OpenRouter，key 从环境变量读）、hooks.json（PermissionRequest → signal_approve.py；SessionStart → 引用 herdr 现有脚本，不改它）、run-codex.sh |
| 06:18 | 无头 | `codex exec "reply with just KEY-OK"`（第一次报 `untrusted` 不再支持，改 `on-request` 后通过） |
| 06:18:56 | `test` 会话 | **新建 tab `w8:t3`（pane `w8:p3`）**，cwd ~/spike-sandbox/codex-work，标签 signal-codex |
| 06:19 | w8:p3 | 启动 run-codex.sh；“Hooks need review”选“Trust all and continue”（只是沙箱里的两个 hook） |
| 06:19–06:29 | w8:p3 | 发 6 条提示词，都是“运行 touch <文件>”；Esc ×4（取消等待中的请求 / 拒绝终端确认框） |
| 06:20 | 手机 | 允许 `touch hello.txt`（创建了，空文件）；拒绝 `touch denied.txt` |
| 06:25 | approvals | 手动删除 Esc 测试遗留的 `0643c927c3f8d96d.req`（修复前的 bug） |
| 06:26 | 沙箱 | 用修复后的 signal_approve.py 覆盖沙箱里的副本 |
| 06:32:20 | w8:p3 | Codex `/exit`；**关闭 tab `w8:t3`** |
| 06:32 | 进程 | 沙箱 Codex 自己拉起的 app-server 守护进程（2 个，CODEX_HOME 指向沙箱）发 SIGTERM 停掉；你自己的 Codex 守护进程（pid 3682734）没动 |
| 06:33 | 沙箱 | 删除 `~/spike-sandbox/.codex-signal/packages`（Codex 下载的 517 MB 程序副本；再运行会重新下载） |

**本机：** 06:1x 本地测试 hook 时没有清掉 `HERDR_*` 环境变量，hook 往我自己所在的本地 herdr pane（`wB5:p1`）写了一个 `sa_…` token，随后又自己清掉了。之后的本地测试都先 `unset HERDR_*`。

OpenRouter 用量：0.1256 → 0.1400 美元。

**留在 .2 上的：** `~/spike-sandbox/.codex-signal`（214 MB，配置、hook、会话记录、日志库）、`~/spike-sandbox/codex-work/hello.txt`。不要了可以直接删。

## 0.3.0 与仓库整理（2026-11-03 06:40–07:00）

- **wA 里 Codex 的项目评审**（06:34，用户发起）：核实并修复了 5 条问题，提交 8146efc。
  - “结果未知”的发送在重启后不显示。
  - 待确认写操作的保存不是事务。
  - `Fleet` 的主机表没加锁就读、`tracker` 被并发修改。
  - 主机的状态监听不随主机一起取消。
  - release 缺密钥时悄悄用 debug 签名。
- **仓库整理**（用户选择：旧客户端一起归档、旧版文档直接删除）：提交 6aa62c1。
  - `android/` 移到 `archive/android/`。
  - Signal 文档集中到 `docs/signal/`。
  - 旧版文档删除。代码注释里还在引用的旧决策 / 旧问题摘录到 `BACKGROUND.md`。
- **发版 0.3.0**：提交 dcd3071，打 tag `signal-v0.3.0`。
  - 第一次用正式密钥签名，证书 SHA-256 `FE:8D:7D:…:76:D2:EA`。
  - 产物 `dist/herdr-signal-0.3.0.apk`，不入库。
  - 发版前在手机上冒烟测试了 minified 包：首页正常、两台主机在线、Codex 对话正常显示。
  - 正式包没有装到手机上：手机上的 `dev.herdr.signal` 0.2.0 是 debug 签名，要先卸载，由用户决定。

- **07:0x 删除 `archive/`**（用户要求）：删掉旧版客户端 `archive/android/` 和早期探针 `archive/spike/`，共 160 个文件，全部在 git 里，没有只存在本地的内容。需要时用 `git checkout signal-v0.3.0 -- archive/` 恢复。

## 目录结构改成主流 Android 骨架（2026-11-03 07:xx，用户要求）

- Android 工程从 `signal/` 挪到仓库根目录：`app/`、`core/`、`benchmark/`、`gradle/`、`settings.gradle.kts` 等；`companion/`、`art/`、`tools/`、`CHANGELOG.md` 也在根目录。
- 新增 `gradle/libs.versions.toml`（版本目录）和 `build-logic/convention`（约定插件 `herdr.android.application` / `library` / `compose`）。各模块不再重复写 SDK、Java 版本和依赖版本。
- `core/designsystem`、`core/markdown` 的包名从 `dev.herdr.signal.*` 改成 `dev.herdr.core.*`，基线配置同步改了。App 的包名和 applicationId 没变。
- 文档从 `docs/signal/` 拍平到 `docs/`，文件名改成小写；过程记录放进 `docs/history/`。旧的 `signal/README.md` 拆成根目录 README（介绍、目录）、`docs/development.md`（开发指南）和 `companion/README.md`。
- CI 改名为 `.github/workflows/android.yml`，去掉 `working-directory: signal`，只改文档时不跑。
- 签名文件（`keystore.properties`、`signal-release.jks`）和 `local.properties` 跟着挪到根目录，仍然不入库。

## 改名 Kelpie，0.4.0（2026-11-03，用户要求）

- 查重：Google Play、GitHub 上没有叫 Kelpie 的同类 App / 开发工具；美国有两个还有效的 KELPIE 商标，分别是超声探伤探头（Novosound）和一个车辆类商标（Agent Oriented Software），都不在这个领域。
- App 名 Herdr Signal → Kelpie；包名 `dev.herdr.signal` / `dev.herdr.core` → `io.github.luxiang0412.kelpie`（以及 `.core.*`）。配对 scheme 改为 `kelpie://`，脚本改为 `kelpie_pair.py` / `kelpie_approve.py`，环境变量 `SIGNAL_*` → `KELPIE_*`。`herdr` 作为被监督工具的名字（`core/herdr`、协议类）没改。
- `kelpie_pair.py --list / --revoke` 也认改名前的 `herdr-signal-` 密钥（在临时 HOME 里测过）。
- 本机签名文件改名为 `kelpie-release.jks`（`keystore.properties` 同步改），证书不变。
- 截图基准图重录：只有首页标题从“Herdr”变成“Kelpie”（逐张算了差异区域，都只在标题处）。
- 真机：MIUI 拦截了新包名的 adb 安装（`INSTALL_FAILED_USER_RESTRICTED`），要在手机上确认。安装前推上去的 provision.json 和私钥已立即从 `/sdcard/Android/data/io.github.luxiang0412.kelpie.min/` 删除。
