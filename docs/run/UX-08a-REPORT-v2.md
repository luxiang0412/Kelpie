# UX-08a report — Hosts 顶栏自适应 + 设置分组

- baseline：`64d42ed` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §2.3「AppTopBar 高度可增长，禁止固定两行高度裁剪」、§5 组件契约、§10 治理（不新增假功能）
- 改动（`HostsScreen.kt`）：
  1. 固定高度 `TopAppBar` → **内容自适应顶栏**（`statusBarsPadding` + `heightIn(min = 56.dp)` + 底部 1dp 分隔线）；返回/Add 均 `heightIn(min = 48.dp)`
  2. 新增 *设置分组*：**`Appearance and language` / `外观与语言`**、**`Notifications and background` / `通知与后台`**（`SectionTitleStyle` 14/20 SemiBold）
  3. **修复可用性缺陷**：此前无主机时空态占满整页，语言/后台监控**完全不可达**；现在空态下也渲染「外观与语言」分组（截图可见）
  4. 语言三档（跟随系统 / 简体中文 / English）沿用 API33+ `LocaleManager`，<33 禁用并说明
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图：`docs/evidence/ux08a-hosts.png`（AVD 393dp / en / Light）
  - 可见：自适应顶栏（Back / Hosts / Add + 分隔线）、空态、**Appearance and language 分组 + Language 三档**
- 四类结论：
  - **已实现并验证**：自适应顶栏、两个设置分组标题、空态下设置可达；UI 26/26；截图
  - **已实现但未验证**：1.3/1.6 字体下顶栏与分组标题的实际高度（未截图）；`Notifications and background` 分组仅在**有主机**的分支里渲染
  - **未实现**：「连接与安全」「隐私与数据」「关于」分组；分组内的分隔与留白 token 化；`HostsScreen` 的 `LazyColumn` 与设置区共存的滚动方案（当前有主机时设置区在列表下方，需滚动）
  - **已知问题**：空态分支与有主机分支的**设置区块重复渲染**（两处 `LanguageSection`），后续应抽成一个 `SettingsSections()` 复用
- 回滚：`git revert <this commit>`
