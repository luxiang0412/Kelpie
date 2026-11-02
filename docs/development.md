# 开发指南

构建、测试、发版、调试。项目介绍和目录结构见根目录 [README](../README.md)。

## 模块细节

- `core/ssh/`、`core/herdr/`、`core/markdown/` 最初从旧版客户端复制，后来各自演进。
- `core/data/runtime/`：`Fleet`（每台主机一个 `HostRuntime`）、`Sender`（四态回执）、`Previews`、`ApprovalCenter`。
- `core/data/attention/`：`AttentionTracker` + `HomeDerivation`（纯 Kotlin，首页的排序和时长都在这里算）。
- `core/designsystem/` 的 `ContrastTest` 覆盖日夜两套配色。

## Gradle 结构

- 依赖和插件版本只写在 `gradle/libs.versions.toml`，模块里用别名（`implementation(libs.room.runtime)`）。
- `build-logic/convention/` 是约定插件，各模块共用的配置只写一处：
  - `herdr.android.application`：App 的 compileSdk / minSdk / targetSdk、Java 17。
  - `herdr.android.library`：core 模块的 compileSdk / minSdk、Java 17、JUnit。
  - `herdr.android.compose`：打开 Compose，加 Compose 编译器插件、BOM 和预览工具。
- SDK 版本也在版本目录里（`compileSdk` / `minSdk` / `targetSdk`）。

代码注释里的 `Dn` / `I-n` / `RESULTS §x` 是旧版的决策 / 问题 / 实测编号，摘录在 `docs/background.md`。

几条不变的规则（详见设计规范）：

- 主机指纹必须人工核对后才信任，没有 TOFU。
- 写操作不是幂等的：结果未知的发送**不会自动重发**，界面上也没有"重试"按钮。
- 快捷回复只填进输入框，不会直接发送；"允许"必须按住 600 ms（读屏用户走确认对话框）。
- 首页只为"等你"的 pane 读 transcript 预览；事件只当作失效信号，状态以 10 秒一次的对账为准。

## 构建与运行

需要 JDK 17、Android SDK（compileSdk 37），`local.properties` 里写 `sdk.dir`。

```bash
./gradlew :app:assembleDebug        # 调试包 dev.herdr.signal.debug
./gradlew :app:assembleMinified     # R8 混淆 + adb 导入钩子，包名 .min，只用于真机验证
./gradlew :app:assembleRelease      # 发布包；必须有 keystore.properties（见下面“发版”），否则构建失败
./gradlew test                      # 全部单元测试
./gradlew :app:verifyRoborazziDebug # 首页截图回归（Robolectric，JVM 上跑，不需要设备）
./gradlew :app:recordRoborazziDebug # UI 有意改动后重新录基准图（app/src/test/screenshots/），和代码一起提交
./gradlew :app:lintDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :benchmark:connectedMinifiedAndroidTest   # Macrobenchmark（模拟器）
```

## 发版

发布签名：仓库根目录的 `keystore.properties`（`storeFile / storePassword / keyAlias / keyPassword`）+ `signal-release.jks`，两者都在 .gitignore 里，**只存在于本机，需要另外备份**。
证书 SHA-256：`FE:8D:7D:EA:A2:F7:C7:6C:41:D2:42:6B:38:4D:9C:88:44:68:25:63:11:7A:1B:E5:80:03:11:A3:95:76:D2:EA`。
没有 `keystore.properties` 时 release 构建直接失败；只是想试构建可以加 `-Psignal.allowDebugSigning=true`（产物是 debug 签名，不能发布）。
0.2.0 是 debug 签名发的，0.3.0 起用正式密钥；从 0.2.0 升级需要先卸载。

发版步骤：改 `app/build.gradle.kts` 的 `versionCode` / `versionName` → 在 `CHANGELOG.md` 写更新说明 →
`./gradlew test :app:verifyRoborazziDebug :app:lintDebug :app:assembleRelease` → 提交 → `git tag signal-v<版本>`。
产物：`app/build/outputs/apk/release/app-release.apk`（不入库）。

CI：`.github/workflows/android.yml`（单元测试、截图回归、lint、debug + release 构建、`tools/i18n_literals.py --check`）。
要在 CI 里签正式包，把 jks 的 base64 放进 secret `SIGNAL_KEYSTORE_BASE64`、密码放进 `SIGNAL_KEYSTORE_PASSWORD`；没配时 CI 显式用 debug 签名构建。

### 手机审批、扫码添加主机

开发机上的脚本见 [`companion/README.md`](../companion/README.md)。

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
| `tools/check_doc_links.py` | 检查文档里的路径引用是否还存在 |
| `tools/i18n_literals.py` | 找出 Kotlin 里含中文的字面量，按映射表包成 `t("中文", "English")`；`--check` 只检查（CI 用） |
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

更详细的过程、每次远端写操作和各阶段自查见 [`history/run-log.md`](history/run-log.md)。
