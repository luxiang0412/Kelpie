# UX-03b（重做）report — Markdown 排版按 CONVERSATION-SPEC §4/§6

- baseline：`575062b` / `ux/graphite-editorial`
- 依据：CONVERSATION-SPEC v0.2 §4（段落 10–14dp、标题上 18–22 下 8–10、列表 4–6dp）、§6（引用 2dp 中性线 + 12dp 内边距）
- 改动（`markdown/MarkdownText.kt`）：
  - 块间距 `Space.s(8dp)` → **12dp**（段落规格中值）
  - 标题增加 `padding(top = 20dp, bottom = 8dp)`（规格 18–22 / 8–10）
  - 列表项间距 → **4dp**（规格 4–6，悬挂缩进保持 20/28dp 列）
  - 引用左线 `3dp outline` → **2dp borderSubtle**（规格：2dp 中性线，装饰色不作焦点）
  - 沿用上轮：H1/H2/H3 = 21/29、18/26、16/24（`MdH1–3Style`），code 13/20 Mono，行内码无注入空格
- 测试：`:app:assembleDebug` BUILD SUCCESSFUL；`:feature:board:testDebugUnitTest` 通过（`MarkdownParserTest` 12 项不变）；AVD UI **26/26**
- 截图（AVD 393dp / en / Light）：`docs/evidence/ux03ab-spacing.png`
  - 可见：`结论 1` 标题上下留白、列表项 4dp、引用 2dp 细线、`./gradlew check` 行内码背景
- 四类结论：
  - **已实现并验证**：段落/标题/列表间距、引用 2dp、字阶；截图与测试
  - **已实现但未验证**：320dp 下长代码块「不拖动整页」（需 P5 截图）
  - **未实现**：表格/图片/嵌套列表（规格允许原文保真降级）；行内码 4dp 圆角背景（SpanStyle 不支持圆角，需 InlineContent）
  - **已知问题**：行内码背景是矩形（无圆角），与规格「4–6dp 圆角」仍有差距，已在 UX-01b 记录为「保真优先」
- 回滚：`git revert <this commit>`
