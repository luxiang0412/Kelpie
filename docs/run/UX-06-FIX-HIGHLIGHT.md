# UX-06 补 — 搜索命中高亮

- baseline：`7d9a9dd`
- 改动：
  - `MarkdownText` 新增可选 `highlight: String?`；`inline()` 中对 `MdSpan.Text` 走 `appendWithHighlight`：
    按命中词（大小写不敏感）切分，命中片段加 `SpanStyle(background = statusBg(WORKING))`，**字符内容不变**
  - 代码块（`CodeBlock`）不参与高亮（避免破坏等宽滚动与复制保真）——已在报告中说明边界
  - `ConversationScreen`：搜索打开时把查询词传给 `AssistantMessage` → `MarkdownText`；关闭/清空时置空
- 测试：`assembleDebug` / `assembleDebugAndroidTest` BUILD SUCCESSFUL；AVD `emulator-5556` → **OK (26 tests)**
- 截图尝试：`ux06-highlight.png` 只拍到搜索栏打开、**未能把查询词输入到输入框**（AVD 上 `adb shell input text` 未聚焦成功），因此**高亮的视觉效果未验证**
- 四类结论：
  - **已实现并验证**：高亮代码路径、无字符篡改、编译与 26 项 UI 无回归
  - **已实现但未验证**：命中高亮的实际渲染（需要人工输入查询词）
  - **未实现**：Tool 卡结果内高亮（Tool 卡也不走 Markdown 渲染）、代码块内高亮、命中处逐字滚动到可视行
  - **已知问题**：高亮背景用 `statusBg(WORKING)`，在深色下是高饱和浅蓝，可能偏亮；待视觉确认
- 回滚：`git revert <this commit>`
