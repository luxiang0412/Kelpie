# FIX report — Hosts 页 LazyColumn 嵌套 verticalScroll 崩溃（真机实测发现）

- 发现方式：真机 `192.168.1.8:39527` 安装后打开「主机」页 → `logcat -b crash`：
  ```
  FATAL EXCEPTION: main  (dev.herdr.app)
  java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints,
  which is disallowed. ... nesting layouts like LazyColumn and Column(Modifier.verticalScroll())
  at androidx.compose.foundation.lazy.LazyListKt$rememberLazyListMeasurePolicy$1$1.measure
  ```
- 根因：**我自己上一批改动引入**——
  - 更早为修「设置项不可达」给 `HostsScreen` 外层加了 `Modifier.verticalScroll(...)`
  - 同时该分支内仍是 `LazyColumn { items(state.hosts) }`
  - 一旦**存在主机**（真机有 2 台），LazyColumn 收到无限高度约束 → 直接崩溃
  - AVD 上没有配置主机，所以走的是空态分支，**测试没覆盖到**——这是测试矩阵的漏洞
- 修复：外层保留单一滚动容器（`verticalScroll`），把 `LazyColumn` 换成 `Column { state.hosts.forEach { key(id) { HostCard(...) } } }`
  （主机数量是个位数，无需惰性；`key()` 保持项身份稳定）
- 验证：
  - `:app:assembleDebug` BUILD SUCCESSFUL
  - **真机**重新安装 → 打开「主机」页 → `logcat -b crash` **FATAL EXCEPTION = 0**
  - 截图 `docs/evidence/real-hosts.png`：两台主机卡片、连接状态、私钥状态、四组操作（断开/私钥/编辑/删除）全部正常；分组标题「外观与语言」「通知与后台」在顶部
- 四类结论：
  - **已实现并验证（真机）**：崩溃消除、主机列表与设置分组同页显示正常
  - **已实现但未验证**：主机数达到几十条时的滚动性能（当前用 Column 非惰性）
  - **未实现**：把「主机列表 + 设置」做成同一 LazyColumn 的 `item()`（更规范但也更大改动）
  - **已知问题**：这次崩溃暴露出**AVD 没有主机配置 → 有主机分支长期未被自动测试覆盖**；应补一条带主机 Fixture 的 Compose UI 测试
- 回滚：`git revert <this commit>`
