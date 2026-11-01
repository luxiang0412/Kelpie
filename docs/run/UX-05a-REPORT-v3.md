# UX-05a（补完）report — Hosts 页文案本地化（33 处）

- baseline：`18dad7a` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §7.2「按钮、菜单、状态、占位符、说明全部本地化」
- 改动（真实执行）：
  - `HostsScreen.kt`：**33 处**中文字面量改为 `stringResource(R.string.hosts_*)`，含 1 处带参（`hosts_key_label` → `私钥 · %1$s` / `Private key · %1$s`）
  - `values/strings.xml` + `values-zh-rCN/strings.xml` 各新增 33 个 `hosts_*` key
- 遇到的问题（已解决，记录以便复用）：
  1. **本机用 `python3 - <<EOF` 写中文会被按本地代码页解码**，导致 zh 资源写坏 → 改为脚本读原文件、文本以 UTF-8 常量提供
  2. **aapt2 不接受裸撇号**：`app's` 会让 mergeDebugResources 报 `Invalid unicode escape sequence`；`&#39;` 也会被 AGP 还原成裸引号，最终改为改写文案避开撇号
  3. 排版引号 `“ ”` 统一改为 `"`，避免资源编译差异
- 测试：`:app:assembleDebug` BUILD SUCCESSFUL；`:app:assembleDebugAndroidTest` + AVD `emulator-5556` → **OK (26 tests)**
- 截图：`docs/evidence/ux05a-hosts-en.png`（AVD 393dp / **en-US** / Light）
  - 可见：`Back / Hosts / Add / No hosts yet / Tap Add in the top right… / Add host` 全英文，底部导航 `Agents / Hosts`
  - zh 版见更早的 `ux05b-lang.png`
- 安全回归：未改主机增删改逻辑、密钥导入、连接/断开
- 四类结论：
  - **已实现并验证**：Hosts 页 33 处文案双语、带参文案、en 截图；UI 26/26 无回归
  - **已实现但未验证**：zh 环境下本次新增键的截图（此前中文截图是本轮之前的版本）
  - **未实现**：`TechnicalInfoSheet` 文案、通知 channel 与正文、部分 `contentDescription`
  - **已知问题**：`HostsScreen` 仍用固定高度 Material `TopAppBar`（DESIGN-SYSTEM §2.3 要求高度可增长），留待 UX-08a
- 回滚：`git revert <this commit>`
