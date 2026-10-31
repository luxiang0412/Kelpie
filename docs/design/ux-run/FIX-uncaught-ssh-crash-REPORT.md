# FIX report — 未捕获的 SSH 传输异常导致进程崩溃（P0 稳定性）

- baseline commit / branch：`6b10196` / `ux/graphite-editorial`
- 发现方式：为 UX-00a 拍截图时设备黑屏，`logcat -b crash` 显示 **三次** `FATAL EXCEPTION: DefaultDispatcher-worker-N`，根因为
  `net.schmizz.sshj.connection.ConnectionException: Broken transport; encountered EOF`
  （证据摘要：`spike/results/ux-crash-evidence.txt`，已脱敏）
- exact goal：SSH 断链/宿主消失时**不得**崩溃进程，只降级为连接失败/重连
- changed files：
  - `app/.../HerdrApplication.kt`：`appScope` 增加 `CoroutineExceptionHandler`（仅记录异常类名，不打印主机/路径/内容）
  - `app/.../MonitorService.kt`：其 `CoroutineScope` 同样加 handler
  - `feature/board/.../HostsViewModel.kt`：`manager.readPane(...)` 用 `runCatching` 包裹（尾部输出轮询路径）
- why：任务书 §0.2 允许「发现可复现缺陷并附回归测试」的修复；这是可复现崩溃而非功能改动
- tests：`:app:assembleDebug` BUILD SUCCESSFUL；AVD UI 18/18（无回归）
- 验证尝试与**失败**的诚实记录：
  - 计划用「关闭 WiFi 制造断链」复现，但该真机**仅通过 WiFi adb 连接**（`192.168.1.8:37687`），
    关闭 WiFi 后 adb 立即 `offline`，既无法读取 logcat，也无法把 WiFi 再打开；
  - 因此**修复未经设备侧复现验证**，标记为「已实现但未验证」；
  - 副作用：真机当前处于 WiFi 关闭/离线状态，需用户手动打开 WiFi 或重启手机（不涉及任何数据改动）。
- 四类结论：
  - **已实现并验证**：编译通过、UI 测试无回归、handler 路径明确覆盖「Default 调度器未捕获异常」
  - **已实现但未验证**：真实断链下不再崩溃（需要设备侧复现；且需要 USB adb 或本地网络控制手段）
  - **未实现**：SSH 会话层主动探活/自动重建的加固（仍依赖既有重连逻辑）
  - **已知问题**：崩溃是**传输层**异常，全局 handler 只是兜底；若异常发生在 SshJ 内部线程且被 rethrow 到 `async`，仍需逐个 `runCatching`
- rollback：`git revert <commit>`
