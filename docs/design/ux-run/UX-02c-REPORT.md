# UX-02c report — 工作台异常、缓存、Unknown 入口

- baseline commit / branch：`309e3a1` / `ux/graphite-editorial`
- inspected real files：`BoardScreen.kt`（`StatusLine`、`connLabel`）、`AppRoot.kt`（`pendingWrites` 已由 ViewModel 提供）、`core/herdr/WriteLedger.kt`、`core/data/drafts/PendingWrites.kt`
- exact goal：单一主 Banner 按 §2.3 优先级；Unknown 写操作能从**工作台**直接看到并进入；缓存如实标注；不因在线就宣称最新
- changed files：
  - `BoardScreen.kt`：`StatusLine` 重写为**按优先级只取一条** Banner：
    `Unknown 写操作 > 待确认指纹/鉴权失败 > 离线或重连中 > 缓存过期 > 正常（不显示）`
  - `BoardScreen` 新签名：`pendingWrites` + `onOpenPending`（默认空，保持既有调用可编译）
  - `AppRoot.kt`：新增 `pendingSheet` 对话框，列出 method→target / action id 前缀 / 尝试次数 / Unknown 原因，文案为「结果无法确认，远端可能已经执行。不会自动重发」
  - 缓存文案由「显示的是缓存数据」统一为「显示的是缓存内容」（与 Conversation 侧一致）
- why matches UX-D0：§2.3 优先级表；「Unknown 已持久化但只在技术 Sheet 才看得到的情况必须从 Board 进入」；「不能表示失败」
- tests：新增 `app/src/androidTest/.../BoardBannerTest.kt`（5 项）：
  1. `unknownWrite_outranksTransportBanner_andOpensDetails`（重连中 + Unknown 并存 → 只显示 Unknown，且点「查看」触发回调）
  2. `transportBanner_shownWhenNoUnknown`
  3. `staleData_isLabelledAsCache_onBoardBanner`
  4. `healthyConnection_showsNoPersistentBanner`（无恒常横幅）
  5. `awaitingHostKey_isHighPriority`
  - AVD `herdr_m2c1` 全量：**OK (23 tests)**（原 18 + 新增 5）
- screenshots：本轮只做行为/文案验证；Banner 视觉截图待真机解锁后补（记为未验证）
- 安全回归：未改发送/草稿/账本写入逻辑；**没有**新增任何自动重发路径；`autoRetryAllowed` 仍恒 false
- 四类结论：
  - **已实现并验证**：单条 Banner 优先级；Unknown 从工作台可达并展示原始待确认记录；缓存如实标注；在线不显示常驻横幅；23 项 UI 测试通过
  - **已实现但未验证**：真机 Banner 视觉（设备锁屏）；auth failure 与 host key mismatch 的真实设备场景（仅 Fixture）
  - **未实现**：「查看全部问题」聚合入口（当前只是优先级取舍，其余问题仍需进对应页面）；通知与前台 Banner 去重的联动策略
  - **已知问题**：`StatusDot`/`CircleShape` 等旧 import 可能变成未使用（编译无碍）；Banner 只有文本+按钮，无图标（图标契约留 UX-08b）
- commit：见本条提交
- rollback：`git revert <commit>`
