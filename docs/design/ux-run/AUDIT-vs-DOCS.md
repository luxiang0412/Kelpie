# 与文档/原型对齐审计（对话页 + Composer，2026-11-01）

> 依据：`COMPOSER-SPEC v0.2` §3.1/§3.3、`CONVERSATION-SPEC v0.2` §3.3/§4/§5、`DESIGN-SYSTEM v0.1` §2.2/§2.3/§4.2/§9.2、原型 `chat-zh-light.png` / `focus-zh-light.png`
> 证据：真机 Redmi 22021211RC（`192.168.1.8:39527`）截图 `audit-conv2.png`（Light）、`audit-conv2-dark.png`（Dark）

## 1. 本轮已对齐（有截图）

| 项 | 文档值 | 现状 |
| --- | --- | --- |
| 对话标题 | `type.titlePage 18/26` | ✅ 已改为 `titleMedium` 18/26 |
| 顶栏分隔线 | 原型有 1dp | ✅ 已加 `HorizontalDivider(borderSubtle)` |
| 右侧更多 | 原型 `⋯` 图标 | ✅ 改为 `ic_more` 矢量（48dp 触达） |
| 副标题 | `Agent · 项目 · 状态` | ✅ `Pi · spike-sandbox · 空闲` |
| Composer 表面内边距 | 水平 14dp、上下 **10–12dp** | ✅ 改 `14 / 10` |
| Composer 圆角 | 20dp（窄屏 18） | ✅ |
| Send 视觉圆面 | **40dp**（触达 ≥48） | ✅ 44 → **40dp** |
| Placeholder | 14–15sp / 21sp Secondary | ✅ `bodyMedium` 14/21 |
| 描边层数 | 「不在 Composer 外再套第二层卡片」「聚焦 1–2dp Focus Ring」 | ✅ 单层容器 1dp / 聚焦 2dp |
| 正文排版 | 16/25、段落 10–14、列表 4–6 | ✅ 16/25、12、4 |
| 行内码 | 不得插入空格伪装 padding | ✅ 保真 |

## 2. 仍未对齐（逐条带文档出处）

| # | 文档要求 | 现状 | 严重度 |
| --- | --- | --- | --- |
| 1 | `COMPOSER-SPEC §2.1`：「默认 Composer **不单独显示常驻大标题或永久"连接正常"文案**，这些是高频无效信息」 | 底部**常驻两行**安全说明（`指令已接收…/发送结果无法确认…`） | 中（信息正确但常驻） |
| 2 | `DESIGN-SYSTEM §4.3` 线框：Action Row 含可用的 `+` / Mic / Expand | 只有 Expand + Send（Mic/附件未实现，按规范隐藏） | 中（能力缺口，不是视觉 bug） |
| 3 | `CONVERSATION-SPEC §5.1`：用户气泡「靠尾部一角可用 6dp」 | 四角均为 16dp | 低（"可用"非强制） |
| 4 | 原型：分区标题 `已完成 · 1`（带中点） | `已完成 1` | 低 |
| 5 | 原型：工作台标题行含「搜索 + 主题」图标 | 只有「刷新」 | 低（保留既有能力） |
| 6 | `DESIGN-SYSTEM §2.4`：每个图标一种语义、装饰图标不重复朗读 | Tool 卡图标无 contentDescription ✅；但 agent 组首图标重复文字「Pi」 | 低 |
| 7 | `CONVERSATION-SPEC §7.5`：完整输出页支持选择/复制/按需加载 | 已有全屏页（可选中/复制），**未验证 >12 行场景** | 中（未验证） |
| 8 | `CONVERSATION-SPEC §6.1`：行内码 4–6dp 圆角背景 | 背景为矩形（SpanStyle 无圆角） | 低（已在 UX-01b 记录为保真优先） |
| 9 | `DESIGN-SYSTEM §9.2`：「用户浏览历史时输入法弹出不强行跳底」 | 未在真机验证 IME 场景 | 中（未验证） |
| 10 | `DESIGN-SYSTEM §9.1` 矩阵：P3/P4(zh/en) × 深浅 × 320/600 | 仅 P1/P2/P5/P8 采过；**本轮改动后未重采 P5（320/1.6）** | 中（覆盖不足） |

## 3. 截图

- `spike/results/audit-conv2.png`（真机 / Light）：标题 18/26、`⋯`、1dp 分隔线、40dp Send、10dp 内边距
- `spike/results/audit-conv2-dark.png`（真机 / Dark）
- 对照原型：`docs/design/ux-d0/prototype/screenshots/herdr-ux-d0-chat-zh-light.png`

## 4. 结论

- 对话页与 Composer 的**结构性**差异（描边层数、标题字阶、分隔线、图标、间距、圆角、Send 尺寸）**已对齐**。
- 剩余差异集中在三类：**能力缺口**（Mic/附件、搜索高亮未视觉验证）、**未验证项**（IME、>12 行输出、P5 复采）、**低优先视觉**（分区标题中点、气泡单角圆角、行内码圆角）。
