# UX-00a（重做）report — Token 对齐 DESIGN-SYSTEM v0.1

- baseline commit：`641542b` / 分支 `ux/graphite-editorial`
- 依据：`HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md` §2.1.2 / §2.2 / §2.3 / §7.3（此前误用任务书摘要，本次按规格原文重做）
- 改动文件：`feature/board/design/Tokens.kt`、`markdown/MarkdownText.kt`、`BoardScreen.kt`、`design/ContrastTest.kt`
- 具体改动：
  1. **新增语义色 token**：`errorFg/errorBg`（L `#B42318`/`#FFF0EF`、D `#FFB4AB`/`#402524`）、`focusRing`（=accent）
  2. **attention 色值对齐**：L `#8B3E0A`/`#FFF2E6`、D `#FFC28A`/`#3A2B23`（原为旧值）
  3. **其余语义色对齐**：working D `#9FCEFF`/`#1E344A`、done D `#8ED6B0`/`#1C352A`、idle D `#BEC9D8`/`#2A323E`、unknown D `#E9CF87`/`#38321D`
  4. **字阶对齐 §2.2**：`titleMedium` → 18/26（=titlePage）、`bodyMedium` → 14/21（=bodySecondary）、`labelLarge` → 13/20 **Medium**（=labelAction）
  5. **新增 `SectionTitleStyle` 14/20 SemiBold**、**`MdH1Style` 21/29 / `MdH2Style` 18/26 / `MdH3Style` 16/24**，Markdown 标题与分区标题改用它们（原来是 24/30、17/24、16/24）
  6. **Shape**：`Radius.card` 18 → **16dp**
- 测试（实际执行）：
  - `gradle --no-daemon :app:assembleDebug` → BUILD SUCCESSFUL（exit 0）
  - `gradle --no-daemon :feature:board:testDebugUnitTest` → BUILD SUCCESSFUL（exit 0）
  - `gradle --no-daemon :app:assembleDebugAndroidTest` + AVD `emulator-5556` `am instrument` → **OK (26 tests)**
  - 新增对比度断言（`ContrastTest`）实测：
    - light `errorFg/errorBg` **5.94:1**、dark **8.20:1**（阈值 4.5）
    - light `focusRing/canvas` **5.86:1**、dark **8.76:1**（阈值 3.0）
- 截图（AVD 393dp / en-US / Light）：
  - Before：`spike/results/ux03c-toolcard.png`（旧字阶：H1 24/30、无 error token、卡片 18dp 圆角）
  - After：`spike/results/ux00a-v2-conv-light.png`（H1 21/29、「Result not received」工具卡、16dp 圆角、Composer 底部说明已英文化）
- 安全回归：未改发送/草稿/账本/配对/连接；仅 token 与样式引用
- 四类结论：
  - **已实现并验证**：error/focus token、attention 与全部 dark 语义色、字阶 4 项、mdH1–3、SectionTitle、card 16dp；对比度断言通过；26 项 UI 无回归
  - **已实现但未验证**：`focusRing` 尚无组件真正使用（Composer 焦点环属 UX-04a）；320dp/1.6 与 dark 截图未拍
  - **未实现**：`space.1–8` 命名化（现用 `Space.xs/s/m/l…`，值相同但命名不符）；`stroke.focus` 2dp 组件化；`elevation` token
  - **已知问题**：`fillMaxWidth(0.86f)` 用户气泡仍未按 88/86/520dp 三档（属 UX-03a）
- 回滚：`git revert <this commit>`
