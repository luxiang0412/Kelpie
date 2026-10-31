# Markdown 渲染技术选型（M2-B / B1）

## 结论

采用 **方案 2：轻量自研 Parser（块级 + 单层行内）+ Compose 自定义渲染组件**。
不使用成熟 Markdown 库，也不继续把内容拼成单一 `AnnotatedString`。

## 候选与评估

| 维度 | 1. 成熟 Compose Markdown 库（如 compose-markdown / multiplatform-markdown-renderer） | 2. 轻量 Parser + 自定义渲染（**采用**） | 3. 现有 AnnotatedString 增强 |
| --- | --- | --- | --- |
| 常见 Markdown 支持 | 全（表/图/HTML 都有） | **受控子集**（标题/列表/引用/围栏/行内/链接） | 无结构，只能整段加样式 |
| Compose 性能 | 不可控（内部往往整段 AnnotatedString + 复杂递归） | **可控**：块级拆分为独立 composable，天然有 contentType 粒度 | 长文本 measure 成本最高（M2-A 已实测） |
| 增量更新成本 | 整体重解析 + 整体重组 | 按消息粒度 `remember(source)`，未变化不重解析 | 每次追加都要重组整段 |
| 代码块控制 | 库内固定样式，难做「语言标签 + 复制 + 横向滚动 + 展开」 | **完全自控**（需求 B3 逐条满足） | 无法实现 |
| 测试成本 | 依赖库行为，回归不可控 | 纯函数 Parser，**JVM 单测直接覆盖** | 无可测单元 |
| 依赖体积 | +300KB~1MB，且常要求特定 Compose 版本 | **0** | 0 |
| 许可证 | 多为 Apache-2.0（可接受） | 无依赖 | — |
| 与 Pi transcript 模型兼容 | 需要把文本再塞进去，仍无 tool 语义 | 直接作用于 `ChatMessage.text` | 同 |

## 明确取舍（写进代码注释与测试）

- 支持：`#` 标题、`-`/`*` 无序列表、`1.` 有序列表、`>` 引用、` ```lang ` 围栏代码、`**粗**`、`*斜*`、`` `code` ``、`[text](url)`。
- 不支持（**有意**）：嵌套列表、表格、图片、HTML、脚注、setext 标题、任意转义。
  不支持的内容一律按普通文本渲染，**不抛异常**（`MarkdownParserTest.unknownContent_doesNotThrow`）。
- 安全性：只有 `http(s)` 链接生成可点击 `LinkAnnotation`；`javascript:` / `file:` / `intent:` 等渲染为删除线文字 + 「（链接已停用）」，不可点击（`link_safeAndUnsafe`、`unsafeScheme_renderedButNotClickable`）。
- 不做「大量正则拼装完整 Markdown」：块级用逐行状态机，行内用单遍扫描 + 括号配对（`matchingParen`）。

## 性能约定

1. 解析在 `Dispatchers.Default`（`produceState` + `withContext`），**主线程不解析**；
2. 解析结果按消息文本 `remember(source)` 缓存，未变化不重复解析；
3. 解析完成前显示纯文本，避免空白闪烁；
4. 代码块默认最多 12 行（`CodePreviewLines`），展开后才渲染全文；
5. 超长 assistant 消息（>2500 字符或 >40 行）折叠，展开后在换行处截断预览。

## 何时该换回库

若 M2-C 需要嵌套列表、表格、任务列表、脚注等能力，应重新评估引入库并**替换 Parser 层**（`MdBlock`/`MdSpan` 已按 AST 形态设计，渲染层可保留）。Parser 与渲染层分离就是为了这个退路。
