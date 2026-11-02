# Signal 重写 · 运行日志

按 [SIGNAL-REMOTE-SCOPE.md](SIGNAL-REMOTE-SCOPE.md) 执行。这里记录每一次远端写操作和每个阶段的自查结论。

## 远端写操作

| 时间 (2026-11-02) | 主机 | 目标 | 操作 | 结果 |
|---|---|---|---|---|
| 23:10 / 23:11 | .2 / .3 | — | 在手机上信任主机指纹（ED25519，先用 `ssh-keyscan \| ssh-keygen -lf -` 核对一致） | 写入 App 私有 known_hosts，不改主机 |
| 23:20:12 | .2 | w8:p1（沙箱 Pi） | `agent.prompt` "reply with just SIGNAL-OK" | 已送达；Pi 回复 SIGNAL-OK；会话文件里只出现 1 次 |
| 23:22 | .2 | ~/spike-sandbox/approvals | 手工放一条 `ls -la` 请求（带 nonce）→ 手机上短按（未批准）→ 按住 1 秒批准 | `.answer` 为 allow，nonce 匹配；测试文件已删 |
| 23:24 | .2 | ~/spike-sandbox/approvals | 同上，手机上拒绝 | `.answer` 为 deny；测试文件已删 |
| 23:29–23:36 | .2 | w8:p1（沙箱 Pi） | 从电脑上通过 bridge 发 `agent.prompt` "reply with just NOTIFY-OK[-2…6]"，共 6 次，用来在 App 处于后台时触发“完成” | 每次都回复对应文本；第 6 次时后台通知送达 |
| 00:02–00:15 | .2 | ~/spike-sandbox/approvals | 手工放一条长命令的 `ls -la … && cat README.md \| head -50` 请求（`layoutcheck0001`），只用来检查卡片和审批弹层在不同宽度 / 字号下的排版；**没有回答** | 检查完已删除 |

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
