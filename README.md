# Herdr Android 客户端

人离开电脑后，用手机监督远端开发机上的 coding agent（Pi / Claude Code）：看看谁在等你、
读结构化对话、补发指令。**不是**移动 SSH 终端。

## 构建

```bash
export JAVA_HOME=<JDK17>
export ANDROID_HOME=<Android SDK>
cd android
gradle :app:assembleDebug          # 调试包
gradle :app:assembleRelease        # 正式包（需 android/keystore.properties，见 docs/release/）
gradle :core:herdr:test :core:data:testDebugUnitTest :feature:board:testDebugUnitTest
```

UI 测试（需要可跑 instrumentation 的设备/模拟器）：

```bash
gradle :app:assembleDebugAndroidTest
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.herdr.app.test/androidx.test.runner.AndroidJUnitRunner
```

## 目录

| 目录 | 内容 |
| --- | --- |
| `android/` | Android 工程（`core:{ssh,herdr,data}` + `feature:board` + `app` + `benchmark`） |
| `docs/design/` | **现行**设计规格（v0.2 为唯一真相）、设计系统、工程契约 |
| `docs/run/` | 实施过程记录（进度、审计、验收） |
| `docs/evidence/` | 截图与原始数据（报告里引用的证据都在这里，见其 README） |
| `docs/release/` | 发版说明 |
| `archive/` | 历史存档：`archive/spike`（探针工程，已被 `android/` 取代）、`archive/docs`（过期文档） |
| `tools/` | 仓库维护脚本（`check_doc_links.py` 校验文档路径引用） |

## 活文档（就地维护，不要复制）

`DECISIONS.md`（技术决策）、`ISSUES.md`（问题台账）、`RESULTS.md`（实测结果）、
`NEXT-STEPS.md`（下一步）、`PI-START-HERE.md`（给 agent 的开工说明）、
`herdr-android-client-research-v2.md` / `herdr-android-spike-plan.md`（背景研究，已冻结）。

## 发版

当前 `0.1.1`（versionCode 2）。产物在 `dist/`（不入库），说明见 `docs/release/`。
签名密钥不在仓库里；丢失后无法覆盖升级。
