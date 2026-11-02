# signal/ — Herdr Signal（Android 客户端 0.2.0）

手机上监督远程开发机里由 herdr 托管的 coding agent（Claude Code / Pi / Codex）。
这是对 `android/` 的重写：设计依据是 `docs/design/signal/SIGNAL-DESIGN-SPEC-v1.md`（方向 A：一张按注意力排序的列表，日间为主、夜间模式）。
旧客户端 `android/` 保留不动；两者包名不同（`dev.herdr.app` / `dev.herdr.signal`），可以同时安装。

![logo](art/logo-512.png)

## 模块

```text
signal/
  app/                 # Activity、导航、各页面（首页 / 对话 / 审批弹层 / 设置 / 主机编辑）、通知、前台服务
  core/ssh/            # 从 android/ 原样复制：指纹信任、sshj、连接与通道
  core/herdr/          # 从 android/ 原样复制：NDJSON 协议、ConnectionSupervisor；新增 L10n（中英文案）
  core/data/           # 主机 / 缓存 / 草稿 / 审批 IPC + 新架构：
                       #   runtime/  Fleet（每台主机一个 HostRuntime）、Sender（四态回执）、Previews、ApprovalCenter
                       #   attention/ AttentionTracker + HomeDerivation（纯 Kotlin，首页的排序和时长都在这里算）
  core/designsystem/   # 颜色 / 字阶 / 间距 token、图标、基础组件（ContrastTest 覆盖日夜两套）
  core/markdown/       # Markdown 渲染（从 android/ 复制，换成 Signal token）
  art/                 # 标志源文件（SVG + 512px PNG）
  tools/               # 开发脚本，见下
```

几条不变的规则（详见设计规范和 `docs/run/SIGNAL-REMOTE-SCOPE.md`）：

- 主机指纹必须人工核对后才信任，没有 TOFU。
- 写操作不是幂等的：结果未知的发送**不会自动重发**，界面上也没有"重试"按钮。
- 快捷回复只填进输入框，不会直接发送；"允许"必须按住 600 ms（读屏用户走确认对话框）。
- 首页只为"等你"的 pane 读 transcript 预览；事件只当作失效信号，状态以 10 秒一次的对账为准。

## 构建与运行

需要 JDK 17、Android SDK（compileSdk 37），`local.properties` 里写 `sdk.dir`。

```bash
cd signal
./gradlew :app:assembleDebug        # 调试包 dev.herdr.signal.debug
./gradlew :app:assembleMinified     # R8 混淆 + adb 导入钩子，包名 .min，只用于真机验证
./gradlew :app:assembleRelease      # 发布包；有 keystore.properties 时用它签名，否则用 debug 签名
./gradlew test                      # 全部单元测试
./gradlew :app:verifyRoborazziDebug # 首页截图回归（Robolectric，JVM 上跑，不需要设备）
./gradlew :app:recordRoborazziDebug # UI 有意改动后重新录基准图（app/src/test/screenshots/），和代码一起提交
```

发布签名：`signal/keystore.properties`（`storeFile / storePassword / keyAlias / keyPassword`）+ `signal/signal-release.jks`，两者都在 .gitignore 里，**只存在于本机，需要另外备份**。
证书 SHA-256：`FE:8D:7D:EA:A2:F7:C7:6C:41:D2:42:6B:38:4D:9C:88:44:68:25:63:11:7A:1B:E5:80:03:11:A3:95:76:D2:EA`。
没有这两个文件时 release 退回 debug 签名（0.2.0 就是这样发的；从 debug 签名的包升级到正式签名需要先卸载）。

CI：`.github/workflows/signal.yml`（单元测试、lint、debug + release 构建、`tools/i18n_literals.py --check`）。
要在 CI 里签正式包，把 jks 的 base64 放进 secret `SIGNAL_KEYSTORE_BASE64`、密码放进 `SIGNAL_KEYSTORE_PASSWORD`。

### 扫码添加主机

在**要添加的那台开发机**上运行（只依赖 Python 3 标准库和 ssh-keygen）：

```bash
python3 signal/companion/pair/signal_pair.py --session test --host <手机能连到的地址>
```

脚本会生成一把这台手机专用的 ed25519 密钥（注释 `herdr-signal-<名称>-<日期>`），问你是否加入 `~/.ssh/authorized_keys`，
然后在终端画出二维码（`--png` 另存图片）。二维码里有地址、本机全部 host key 指纹和私钥，10 分钟后手机不再接受。
App 里：设置 → 添加主机 → 扫描二维码 / 从图片识别；系统相机扫到也会打开 App。**所有入口都要在确认页按下按钮才会写入**。

- 指纹来自开发机自己显示的二维码，视为已经核对过，直接固定；和已保存的指纹不一致时拒绝，不会覆盖。
- 同一地址 + 用户 + 会话的主机已存在时，更新它而不是新增一条。
- 多网卡 / VPN 的机器请务必用 `--host` 指定地址（脚本会列出候选）。
- `--list` 列出发过的手机密钥，`--revoke <名称>` 撤销（会先备份 authorized_keys）。

### 调试包快速配置主机（免去在手机上粘贴私钥）

```bash
D=/sdcard/Android/data/dev.herdr.signal.debug/files
adb push provision.json $D/provision.json   # {"hosts":[{"id","label","host","port","user","session","herdrBin","keyFile"}]}
adb push ~/.ssh/id_xxx  $D/<keyFile>
```

App 启动时导入并删除这两个文件；指纹仍要在界面上确认。只在 debug / minified 包里存在。

## 工具

| 文件 | 用途 |
|---|---|
| `tools/shots.sh [输出目录]` | 真机截首页 / 空闲列表 / 设置 / 对话，用于人工视觉对照（`PKG=` 可指定包名） |
| `tools/profdump2baseline.py` | 把 `cmd package dump-profiles` 的输出转成 `app/src/main/baseline-prof.txt` |
| `tools/i18n_literals.py` | 找出 Kotlin 里含中文的字面量，按映射表包成 `t("中文", "English")` |
| `FixtureActivity`（debug / minified） | `am start -n <pkg>/dev.herdr.signal.FixtureActivity --ei count 2000`：确定性长对话，用来测滚动 |

### 刷新基线配置（baseline profile）

MIUI 会拦截 Macrobenchmark 启动目标 Activity，所以直接从设备导出：

```bash
adb shell cmd package compile --reset dev.herdr.signal.debug
# 在手机上走一遍：首页 → 对话 → 来回滑动 → 设置 → 展开空闲列表
adb shell cmd package dump-profiles dev.herdr.signal.debug
adb pull /data/misc/profman/dev.herdr.signal.debug-primary.prof.txt raw.txt
python3 tools/profdump2baseline.py raw.txt > app/src/main/baseline-prof.txt
```

## 多语言

文案用 `t("中文", "English")` 写在一起（`core/herdr/L10n.kt`），纯 Kotlin 模块里的时长、连接原因、错误提示也一样。
语言跟随系统或 Android 13+ 的"应用语言"设置；中文以外一律显示英文。英文单复数用 `plural(n, "agent")`。

## 性能（MIUI 真机，0.2.0）

| 项目 | 结果 |
|---|---|
| APK 大小 | debug 17.6 MB → release 5.2 MB |
| 冷启动 | debug ~650 ms → R8 包 ~340 ms |
| 2000 条消息长对话滚动 | janky 2.9%（gfxinfo），p99 19 ms；JankStats 0.1% |
| 首次打开对话滚动（基线配置前 → 后） | janky 2.9% → 1.2%，p99 ~21 → ~15 ms |

应用内 JankStats 按界面统计，Activity 停止时在 logcat 打一行：`adb logcat -s SignalJank`。

更详细的过程、每次远端写操作和各阶段自查见 `docs/run/SIGNAL-RUN-LOG.md`。
