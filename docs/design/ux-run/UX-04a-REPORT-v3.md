# UX-04a（按原型重做）report — Composer 去三重描边

- baseline：`d47841f` / `ux/graphite-editorial`
- 用户反馈：「composer 也太丑了吧」——截图复核后确认**成立**，问题是三层同心描边。
- 截图自查（真机 Before `spike/results/real-composer-before.png`）暴露的具体问题：
  1. 外层 `Surface`（白底 20dp 圆角）+ `OutlinedTextField` 自带描边 + 我的 2dp 蓝色 focus ring → **三层框**叠在一起
  2. focus ring 是圆角矩形，与外层 Surface 边缘不重合，看起来像多出来一个盒子
  3. Send 是灰色圆 + 小 `↑`，比例不平衡
  4. 占位符用 `bodyLarge(16sp)`，在输入框里过重
  5. 底部说明与输入框间距不均
- 改动（`ConversationScreen.kt`）：
  1. **只保留一层容器**：`Surface` 平时 1dp `borderSubtle`，聚焦时 **2dp `focusRing`**；输入框自身描边与容器背景全部设为 Transparent（`OutlinedTextFieldDefaults.colors`）
  2. 删除输入框上的 `.border()` 叠加
  3. 占位符回到 `bodyMedium(14sp)` + `textSecondary`
  4. Send：48dp 触达 + **44dp 视觉圆面** + **20dp 矢量上箭头**（新增 `ic_send`，替换原来的 `↑` 文本）
  5. 内边距改为 `start 14dp / end 6dp / top-bottom 6dp`；底部说明 `top 6dp` + 最多两行省略
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (28 tests)**
- 截图（**真机** `192.168.1.8:39527` / 393dp / zh / Light）：
  - Before：`spike/results/real-composer-before.png`（三层框 + 文本框按钮）
  - After：`spike/results/real-composer-after.png`（单层容器 + 细边界 + 44dp 圆形 ↑ + 14sp 占位符）
- 与原型仍有差异（如实列出）：
  1. 原型工具行有 `＋` 与麦克风（未实现，按规格隐藏）；我们只有「展开」+「发送」
  2. 原型 Send 为品牌色实心（有文本时）；截图时输入为空 → 显示禁用灰，属正确状态
  3. 列表顶部第一行在滚动到位时会被顶栏切一半（阅读列表正常滚动行为，但视觉上可再优化——加顶部 contentPadding 或渐隐）
- 回滚：`git revert <this commit>`
