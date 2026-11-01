# UX-03b report — Markdown 正文与 Code Block 视觉

- baseline：`edfaf40` / `ux/graphite-editorial`
- inspected：`markdown/MarkdownText.kt`（`RenderBlock`）、`markdown/MarkdownParser.kt`、`design/Tokens.kt`（`MonoStyle`）
- goal：仅用既有轻量 Parser 做高质量排版；不做新语法引擎
- changed：
  - `Tokens.kt`：`MonoStyle` 12/18 → **13/20**（UX-D0 `code` 字阶），代码块与工具文本共用
  - `MarkdownText.kt`：引用左线内边距 8 → **12dp**；**列表悬挂缩进**（bullet 固定 20dp 列、有序列表 28dp 列，正文换行后与首行左缘对齐）；代码块统一 `MonoStyle`
  - 代码块复制仍取 `block.code` 源文（不含 fence/语言标签），语言标签、横向滚动、12 行折叠保留
- why：§2.2 字阶；Spec「引用中性左线 + 12dp 内边距；列表悬挂缩进保持编号和正文基线」
- tests：`:app:assembleDebug` OK；`:feature:board:testDebugUnitTest` OK（`MarkdownParserTest` 12 项不变，含未闭合围栏、安全链接、CJK 混排、400 段性能上限）
- screenshots：`docs/evidence/ux03b-avd-p2.png`（AVD 393dp / Dark）— 引用左线+缩进、列表悬挂、行内码背景、链接下划线、未支持表格保真降级为文本
- 四类结论：
  - **已实现并验证**：引用 12dp、列表悬挂缩进、code 13/20、复制不含 fence（沿用既有实现并有单测）
  - **已实现但未验证**：320dp 下长代码「不拖动整页」（需 P5 实测）；文字选择（UX-03d）
  - **未实现**：表格/图片/嵌套列表（明确不支持，保真降级）
  - **已知问题**：无
- commit：见提交
