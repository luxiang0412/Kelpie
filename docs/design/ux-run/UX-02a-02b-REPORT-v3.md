# UX-02a/02b（按原型重做）report — 工作台对齐 `ux-d0/prototype/screenshots/board-*`

- baseline：`0fdc355` / `ux/graphite-editorial`
- 依据：**原型截图 `herdr-ux-d0-board-zh-light.png`（本次真正逐项对照）** + BOARD-SPEC §2/§4 + DESIGN-SYSTEM §2.2/§2.3
- 之前的问题（用户指出「和文档差距大」，成立）：我只按文字规格实现，**没有用仓库里的原型截图对照**，导致结构缺了半层。
- 本次改动（`BoardScreen.kt`）：
  1. 标题上方新增品牌小标 **`HERDR`**（`labelSmall`、`letterSpacing = 2sp`、`textSecondary`）
  2. 主机选择器改为**胶囊控件**（`surfaceRaised` 背景 + 圆角 pill + 名称 + 展开指示），右侧新增**低强调连接摘要**：`● N 个 Agent`（未连接时显示真实 `connText`）
  3. **Attention 卡**结构补齐：eyebrow（真实状态词）+ 任务标题（`titleSmall` 16/24 SemiBold）+ **meta 行**（Agent · 项目 · 主机）+ 右上角 Agent 名；底色 `statusBg(BLOCKED)` + 1dp 语义边框；**不加伪造摘要/等待时长**
  4. **紧凑行**补 40dp 圆角图标块（`surfaceRaised` + 20dp 矢量图标）+ 标题 + meta + chevron；最小高度 64dp
  5. 分区标题计数**靠右**；折叠指示改用矢量 `DisclosureIndicator`（不再用文字"展开/收起"）
  6. 新增 `metaLine(pane, hostLabel)`：`Agent · 项目 · 主机`，全部来自真实字段
- 测试：
  - `assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL
  - AVD `emulator-5556` → **OK (28 tests)**（更新 2 处断言：同一文案现在会同时出现在摘要/卡片，改为集合断言）
  - **真机 `192.168.1.8:39527`**：安装后打开工作台，`logcat -b crash` = **0**
- 截图（Before/After 同结构对照）：
  - Before（真机）：`spike/results/real-board-spec.png` —— 无品牌标、主机纯文本、无图标块、计数紧贴标题
  - After（真机 / 393dp / zh / Light）：`spike/results/real-proto-board-light.png`
    - 可见 `HERDR` / `工作台` / `刷新` / 胶囊 `开发机 .5.8 ›` + 右侧 `● 2 个 Agent` / 1dp 分隔线 / `已完成 1 ›`（收起）/ `空闲 1 ˅`（展开）/ 行 = 40dp 图标块 + `Pi · spike-sandbox` + `Pi · spike-sandbox · 开发机 .5.8` + `›`
  - After（真机 / Dark）：`spike/results/real-proto-board-dark.png`
  - AVD 对照：`spike/results/ux02b-proto-p1.png`（空态）、`ux02b-proto-p2.png`
- 与原型**仍有差异**（如实列出，不宣称 1:1）：
  1. 原型标题行有「搜索/主题」图标，我们保留既有「刷新」（规格允许保留已有能力，不做假入口）
  2. 原型分区标题是 `已完成 · 1`（带分隔点），我们是 `已完成 1`
  3. 原型底部导航有「活动」，我们只有「工作台 / 主机」（无活动数据源，不许放假入口）
  4. 行 meta 里项目名与标题重复（`Pi · spike-sandbox` / `Pi · spike-sandbox · 开发机 .5.8`）——源于真实字段，可考虑去重
- 回滚：`git revert <this commit>`
