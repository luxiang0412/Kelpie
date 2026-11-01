# UX-01b report — InlineCode 文本/复制保真

- baseline commit / branch：`48a94b1` / `ux/graphite-editorial`
- inspected real files：`feature/board/.../markdown/MarkdownText.kt`（`MarkdownText` → `RenderBlock` → `inline(spans)` → `buildAnnotatedString`）；`MarkdownParser.parseInline`；`MarkdownParserTest`
- exact goal：移除「用前后空格伪造 padding」的实现，保证渲染/复制/选区不含被篡改的字符
- changed files：`MarkdownText.kt`（`MdSpan.Code` 分支由 `append(" ${s.text} ")` 改为 `append(s.text)`，仅保留背景 Span 与等宽字体）
- why matches UX-D0：§2.2 InlineCode 4–6dp 视觉差异应靠背景，不得改变字符；草案要求「复制所得等于源文本」
- tests：
  - 新增 `app/src/androidTest/.../InlineCodeFidelityTest.kt`（AVD `herdr_m2c1`）：**OK (2 tests)**
    1. `运行 \`grep -rn unused\` 于 \`spike/\` 目录` → 渲染文本必须精确等于 `运行 grep -rn unused 于 spike/ 目录`（旧实现会是双空格，测试会失败）
    2. `中文\`HANDOFF.md\`紧贴中文` → 必须等于 `中文HANDOFF.md紧贴中文`
  - AVD 全量 UI 测试 16/16 仍通过（本轮另跑）
- screenshots：`docs/evidence/ux01b-inline-code.png`（真机 Redmi 1080×2400，zh-CN，fontScale 1.3 → 恢复 1.3 未改；Light）
- safety regressions checked：未触及发送/草稿/账本/配对/连接；代码块「复制」仍复制 `block.code` 原文，与行内码改动无关
- 四类结论：
  - **已实现并验证**：行内码不再注入空格；两项 UI 断言在 AVD 通过；中英混排截图确认
  - **已实现但未验证**：长按系统文本选择、跨行选区在真机上的手感（需人工）
  - **未实现**：InlineCode 的真实视觉内边距（Compose `SpanStyle` 无 padding；若后续需要，只能用 `InlineContent` 或自定义绘制，本轮按任务书「退回保真背景 Span」处理）
  - **已知问题**：视觉上代码与相邻中文间距变小（这是保真的代价，符合任务书优先级）
- commit：见本条提交
- rollback：`git revert <commit>`
