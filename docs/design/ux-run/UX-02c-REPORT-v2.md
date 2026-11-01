# UX-02c（重做）report — 异常 Banner 优先级与「全部问题」入口

- baseline：`af061e9` / `ux/graphite-editorial`
- 依据：DESIGN-SYSTEM v0.1 §3「优先级：安全阻断/主机身份变化 > Unknown 写入 > 明确认证失败 > 连接/数据过期 > Blocked > 普通同步」+「多问题并存不叠 Banner，展示一条 + 查看全部问题入口」
- 改动（`BoardScreen.kt` `StatusLine`）：
  1. 由「when 取一条」改为**构造有序 issues 列表**，顺序严格按规格：
     `AWAITING_HOST_KEY` → Unknown 写操作 → `FAILED`（用新 `errorFg` 色）→ 连接中/重连/空闲/陈旧 → 缓存过期
  2. 文案对齐术语：Unknown = **`发送结果无法确认（N）：远端可能已执行，不会自动重发`**，动作 **`查看发送记录`**
  3. 主机身份待确认加显式前缀 `主机身份待确认：…`（安全状态必须自解释）
  4. 多个问题并存时显示 **`全部问题 N`** 按钮 → AlertDialog 列出全部问题文本（不叠多条 Banner）
  5. 认证失败改用新增的 `error` 语义色
- 测试：
  - `assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL
  - AVD `emulator-5556` → **OK (26 tests)**（更新 3 处断言：主机身份用 substring、Unknown 用新文案与「查看发送记录」）
- 截图：`spike/results/ux02c-banner-nodata.png`（AVD，无主机态——本轮 AVD 无配置主机，Banner 未触发；异常态需 Fixture，记为未验证）
- 四类结论：
  - **已实现并验证**：优先级顺序、Unknown 文案与动作、认证失败用 error 色、全部问题入口；UI 26/26
  - **已实现但未验证**：真机/AVD 上同时出现多问题时的视觉（`BoardBannerTest` 已覆盖 Unknown+重连并存的**行为**，但未截图）
  - **未实现**：`AWAITING_HOST_KEY` 的专用安全操作入口（目前统一为「重试」）；通知与 Banner 去重联动
  - **已知问题**：`transportTrouble` 变量仍用于判断，但已被 issues 列表取代一部分判断，存在冗余逻辑待清理
- 回滚：`git revert <this commit>`
