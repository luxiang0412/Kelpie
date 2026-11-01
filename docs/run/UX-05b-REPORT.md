# UX-05b report — App 内语言设置（PARTIAL）

- baseline：`63b0533` / `ux/graphite-editorial`
- inspected：`MainActivity`、`HostsScreen`（当前唯一的"设置类"页面）、AGP/targetSdk 34、minSdk 26
- goal：跟随系统 / 简体中文 / English 三选一；只改 UI 语言，不丢草稿/会话/滚动/连接
- 技术选择（与任务书一致）：
  - **Android 13+（API 33）走官方 `LocaleManager.setApplicationLocales`**：系统级 per-app locale，系统设置里也会出现本 App 的语言项
  - **13 以下不做 AppCompat 迁移**（任务书明确禁止在无 AppCompat 的工程里盲用 `AppCompatDelegate`）→ UI 中如实提示「当前系统版本不支持应用内切换语言（需 Android 13+），将跟随系统」，选项置灰
- changed：`HostsScreen.kt` 新增 `LanguageSection`：三个 `FilterChip`（跟随系统 / 简体中文 / English），当前值从 `LocaleManager.applicationLocales` 读回；选择即写回
- 为什么放在 Hosts 页：当前没有独立 Settings 页（UX-08a 才规划分组），Hosts 页是既有唯一设置入口，避免制造"空壳设置页"
- tests：`:app:assembleDebug` OK；进入 Hosts 页截图确认页面结构正常（`docs/evidence/ux05b-lang.png`）
- 四类结论：
  - **已实现并验证（编译 + 页面可达）**：三档语言选择、API33+ 生效路径、<33 明确禁用并解释
  - **已实现但未验证**：**语言切换的实际生效与状态保留未验证** —— 需要 `LocaleManager` 触发 Activity 重建，本 AVD 上未完成 zh→en→system 往返测试；Composer 草稿、Unknown、筛选、锚点在重建后的保留未测
  - **未实现**：Android 12 及以下的 App 内语言切换（不做 AppCompat 迁移）；通知文案本地化（通知 channel 名仍硬编码）
  - **已知问题**：`LanguageSection` 位于 Hosts 页空态下方，无主机时需滚动才可见；UX-08a 会把它并入「外观与语言」分组
- commit：见提交
