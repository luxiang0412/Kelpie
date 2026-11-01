# 测试与基准基础设施报告（M2-B Track A）

## A1. 正式 benchmark 构建（已完成并验证）

`app/build.gradle.kts` 新增 `benchmark` build type：

| 配置 | 值 | 验证方式 |
| --- | --- | --- |
| 基线 | `initWith(release)` | — |
| 可调试 | `isDebuggable = false` | 设备上 `run-as dev.herdr.app id` → `package not debuggable` ✅ |
| R8 | `isMinifyEnabled = true` + `proguard-android-optimize.txt` | `:app:assembleBenchmark` 走 R8 任务 ✅ |
| 签名 | 本地 debug signingConfig | 可安装 ✅ |
| Baseline Profile 安装器 | `androidx.profileinstaller:profileinstaller:1.4.1` | 编译通过；`CompilationMode.Partial()` 前置条件满足 |
| Fixture 隔离 | `sourceSets["benchmark"]` 仅追加 `src/debug/{kotlin,AndroidManifest.xml}`；`release` 不含 | release 变体未引入 debug 源集 |
| 数据隔离 | Fixture 只构造内存中的 `List<ChatMessage>` | 不写 Room、不连 SSH |

> 注意：1.4.2 不存在（Maven 无该版本），实际使用 **1.4.1**。

### 实测（benchmark 变体，R8，非 debuggable，真机 Redmi 22021211RC）

| 消息数 | Fixture 首帧 `TotalTime` |
| --- | --- |
| 100 | **375 ms** |
| 500 | **344 ms** |
| 2000 | **298 ms** |

对比（同设备、同协议）：debug 变体 787 / 786 / 834 ms；M2-A 优化前 1577 / 1529 / 1583 ms。
证据：`docs/evidence/m2b-fixture-start-benchmark.txt`。

⚠️ 这是 **Fixture 页首帧**，不是 `StartupTimingMetric`，也不等于正式 App 冷启动（M1 release 中位 379 ms）。

## A2. Instrumentation 阻断诊断（已定位，未解除）

| 步骤 | 结果 | 证据 |
| --- | --- | --- |
| 1. 手动启动 Activity | ✅ 正常 | 看板/对话/Sheet 全部可用 |
| 2. `adb shell am start` 启动 Activity | ✅ 正常（含 benchmark 变体） | 上述首帧数据 |
| 3. 最小 instrumentation | ✅ Runner 能启动并执行到测试体 | `am instrument -w` 返回 JUnit 结果 |
| 4. instrumentation 启动测试 Activity | ❌ **被拒** | `MIULOG- Permission Denied Activity ... result code=102`，随后 `BLASTSyncEngine ... ANR likely` |
| 5. Manifest/exported/Runner/权限 | 已排除 | 测试宿主 Activity 已 `exported=true`、独立进程仍失败；与 Manifest 无关 |

**结论：这是 MIUI 的 BAL（后台 Activity 启动）限制，与 profileinstaller / shader cache 是两个独立问题。**

- profileinstaller 缺失问题 ✅ 已修（加依赖 + `CompilationMode.Partial()` 前置条件）。
- shader cache 问题 ❌ 仍在：`dropShaderCache` 在 MIUI 上失败，属 Macrobenchmark 设备侧限制。
- 未做（按约束）：Root、关闭系统安全、改非测试应用配置。

## A3. 模拟器（未恢复）

- 现状：`emulator-5554` 的 `pm`/`package` 服务缺失（`cmd: Can't find service: package`），`reboot` 无效。
- 本轮未创建新 AVD（需下载系统镜像，且不影响结论）：**记录为待办 I-75**。
- 结论：本轮所有设备侧结论均来自真机；模拟器未参与。

## A4. 正式性能基线（部分达成）

| 指标 | 状态 |
| --- | --- |
| Fixture 首帧（100/500/2000） | ✅ 已测（debug / benchmark 两套变体） |
| `StartupTimingMetric` | ❌ 未获得（依赖 instrumentation 启动 Activity，被 BAL 拦） |
| `FrameTimingMetric`（滚动/追加/折叠/IME） | ❌ 未获得（同上 + `dropShaderCache` 失败） |
| Perfetto trace | ❌ 未获得（同上） |

**因此本轮不宣称任何滚动/追加/IME 性能结论。** M2-B 的渲染改动只按「正确性与可读性」验收，性能影响未知。


---

# 附录（M2-C1 更新，2026-10-31）

## A3'. 隔离 AVD（已解决 I-75 的可用性）

| 项 | 结果 |
| --- | --- |
| AVD 名称 | `herdr_m2c1`（新建；用户已有 `touchlab_api34` 未改动） |
| 镜像 | `system-images;android-34;google_apis;x86_64`（本地已有，无需下载） |
| 启动 | headless、`-no-snapshot -gpu swiftshader_indirect`，50 s 到 `sys.boot_completed=1` |
| `pm` 服务 | ✅ 正常（`pm list packages` 返回完整列表） |
| 安装 | ✅ `app-debug.apk` / `app-debug-androidTest.apk` 安装成功 |
| Instrumentation | ✅ Runner 正常，Compose UI 测试 **9/9 通过**（26 s） |
| Macrobenchmark | ✅ `startup_500` 通过，`timeToInitialDisplayMs` median 2869.6 ms，产出 5 份 Perfetto trace |

> 旧的 `emulator-5554`（package 服务损坏）保留未删除，仅不再使用。

## 测量口径（重要）

| 数据 | 来源 | 可用于 |
| --- | --- | --- |
| Mapper 算法耗时 | JVM 单测 `MapperCostTest` | 算法回归、增量收益（相对值） |
| Markdown 解析耗时 | JVM 单测 `MarkdownParserTest.largeInput` | 解析性能上限 |
| Compose UI 帧时 | `FrameTimingMetric` | **尚未获得**（真机 MIUI 阻断；模拟器仅验证流程） |
| Activity 启动 | `am start -W` / `StartupTimingMetric` | 真机 fixture 首帧；模拟器 startup_500 |

模拟器 `startup_500`（2869.6 ms）**只能**说明流程可运行：模拟器无真实 GPU、且目标 App 是 debuggable。
