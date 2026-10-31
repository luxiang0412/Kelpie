# UX-D0 运行基线（UX-00）

- 分支：`ux/graphite-editorial`（从 `main` @ `f56430c` 切出）
- 工作树：**干净**（导入的 UX-D0 文档/任务书/原型为唯一未追踪文件，已单独提交为文档基线）
- 环境：Windows + Git Bash；JAVA_HOME=zulu17.34.19；Gradle 9.4.1 wrapper（`--no-daemon` 规避 Windows 文件锁，I-58）；AGP 9.2.1（内置 Kotlin，不可再 apply kotlin-android）
- 设备：真机 Redmi 22021211RC（MIUI/A14，adb `192.168.1.8:37687`）；隔离 AVD `herdr_m2c1`（android-34 google_apis x86_64，port 5556）
- 已知平台限制：MIUI 拒绝 instrumentation 启动 Activity（I-74）→ UI 测试与截图验收走 AVD；真机只做人工/am start 验证
- 构建/测试命令（实际存在）：
  - `gradle --no-daemon :app:assembleDebug`
  - `gradle --no-daemon :feature:board:testDebugUnitTest :core:data:testDebugUnitTest :core:herdr:test`
  - UI：`adb -s emulator-5556 shell am instrument -w dev.herdr.app.test/...`（16 项）
