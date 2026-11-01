# UX-08b report — 减少动效（reduce motion）落地

- baseline：`0a18c25` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §6「使用系统/Android 动画设置并支持禁用非必要动效；减少动画时可直接切换」
- 改动：
  1. `design/Tokens.kt` 新增 `@Composable fun animationsEnabled()`：读取 `Settings.Global.ANIMATOR_DURATION_SCALE`（`remember(resolver)` 缓存，避免每帧读取；不用 try/catch 包 Composable）
  2. 对话页 Tool/Thinking 折叠动画：动效开启时 `fadeIn+expandVertically / fadeOut+shrinkVertically`，系统关闭动画时使用 `EnterTransition.None / ExitTransition.None`（直接 snap）
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：动效差异无法用静帧证明，**未截图**（如实记录）
- 四类结论：
  - **已实现并验证**：reduce-motion 开关读取、折叠动画的条件分支、编译与 26 项 UI 无回归
  - **已实现但未验证**：真机把「动画时长缩放」设为 0 后的实际表现（需人工/`settings put global animator_duration_scale 0` 验证 + 录屏）
  - **未实现**：Motion token 时长统一（当前仍用 Compose 默认时长而非 `Motion.FAST/NORMAL/SLOW`）；按钮/状态切换动画的 token 化；统一 `EmptyState` 组件（本包只做了动效部分）
  - **已知问题**：`animationsEnabled()` 只在折叠处使用，其他 `AnimatedVisibility`/`animateScrollToItem` 未接入
- 回滚：`git revert <this commit>`
