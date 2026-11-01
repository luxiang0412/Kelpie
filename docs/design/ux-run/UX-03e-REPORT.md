# UX-03e report — 阅读位置、滚动与新消息入口

- baseline：`ddb2fb8` / `ux/graphite-editorial`
- inspected：`ConversationScreen.kt` 原实现在 `LaunchedEffect(items.size)` 里用「lastIndex − lastVisible ≤ 2」猜测贴底
- goal：五状态（FollowingLatest / BrowsingHistory / RestoringAnchor / Resyncing / Empty）+ 稳定锚点 + 未读数
- changed（`ConversationScreen.kt`）：
  - 新增 `private enum class ReadMode { FollowingLatest, BrowsingHistory, RestoringAnchor, Empty }`
  - **`atBottom` 用 `derivedStateOf` 精确判定**（末项可见且其底边在视口内 8px 容差），不再用 index 差值猜
  - 用户手势驱动状态：贴底 → FollowingLatest 且未读清零；否则 BrowsingHistory
  - **锚点 = 会话 key + 稳定条目 key（`paneId-index`）+ 像素偏移**，用 `rememberSaveable(sessionKey)` 保存；返回/重建后若该 key 仍存在则恢复（RestoringAnchor），不存在则**保守回退到最新**（不会恢复到别的 Agent）
  - 新内容到达：跟随态自动滚到底；浏览态**不移动位置**并累计 `unread = lastIndex − lastSeenIndex`
  - 「新消息」按钮文案改为 `↓ N 条新消息`，点击清零未读并切回 FollowingLatest
- why：Spec「不是仅用 firstVisibleItemIndex 判定永远自动滚动」「锚点由稳定 Item ID + offset 组成」「tool result 更新不反复增加未读数」（未读数按条目增量计算，同一条目更新不增加）
- tests：`assembleDebug` OK；AVD 全量 UI **OK (26 tests)**（无回归）
- screenshots：未单独截图（滚动状态属于行为，静态截图无法证明）；**标记为未验证**，需要真机手势实测
- 四类结论：
  - **已实现并验证（编译 + 26 项 UI 无回归）**：状态机、精确贴底判定、锚点保存/恢复、未读计数
  - **已实现但未验证**：真实手势下的「上滑不被拉回」「点提示回到底部」；IME 弹出时锚点是否稳定；`Resyncing` 状态**未实现**（断线恢复仍直接沿用缓存内容）
  - **未实现**：`Resyncing` 显式状态；跨分支锚点失效时的用户可见说明（当前静默回退到最新）
  - **已知问题**：`DisposableEffect` 的 `onDispose` 里读取 `listState` 属于离开瞬间快照，可能与最后一帧略有偏差
- commit：见提交


---

## 补充：`Resyncing` 状态已实现

- `ConversationScreen` 新增参数 `resyncing: Boolean = false`；为 true 时在消息区顶部显示
  一行低强调提示「重新同步中 · 下面显示的是已缓存内容」（保留缓存，不隐藏内容）
- `AppRoot` 传入 `activeHost?.connKind != CONNECTED` —— 即连接非 CONNECTED（重连中/离线/失败）时为 true
- 效果：断线重连期间**既不清空历史，也不假装是最新**
- 验证：`:app:assembleDebug` OK；AVD 全量 UI **OK (26 tests)** 无回归
- 仍未验证：真实断网重连时的视觉与滚动锚点（需要设备侧网络操作，且上次误关 WiFi 导致离线，已改用更安全方式）
