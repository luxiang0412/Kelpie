# DESIGN-SYSTEM — Quiet Command

> 代码位置：`android/feature/board/src/main/kotlin/dev/herdr/feature/board/design/Tokens.kt`
> 依据：`HERDR-ANDROID-UX-DESIGN-REVIEW-2026-10-31.md` §4/§5。**所有页面禁止硬编码颜色与尺寸**，一律取 tokens。

## 1. 颜色

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| background | `#F7F8FA` | `#0B111C` | 页面底色 |
| surface | `#FFFFFF` | `#141B29` | 卡片/Sheet |
| surface-raised | `#F1F3F8` | `#1C2636` | 输入框、代码块、用户气泡 |
| text-primary | `#172033` | `#F1F5FA` | 正文/标题 |
| text-secondary | `#526074` | `#B4C0D0` | 辅助信息 |
| accent | `#3556CE` | `#9DB2FF` | 主操作（发送/链接） |
| accent-on | `#FFFFFF` | `#0B111C` | accent 上的文字 |
| outline | `#D6DDE7` | `#344356` | 分隔线/描边 |

**状态语义色（前景 / 背景，成对使用）**

| 状态 | Light fg/bg | Dark fg/bg |
| --- | --- | --- |
| blocked（需要你） | `#98470B` / `#FFF3EA` | `#FFB386` / `#3B241A` |
| working（运行中） | `#12558A` / `#EAF4FF` | `#8BC5F9` / `#142C43` |
| done（已完成） | `#17683E` / `#EAF8EF` | `#8CD8AE` / `#153326` |
| idle（空闲） | `#596374` / `#F0F2F5` | `#C1CCD8` / `#28313D` |
| unknown（状态未知） | `#74530A` / `#FFF7E0` | `#EBD28D` / `#3B3119` |

约束：**状态必须同时用文字或形状表达**（当前：状态点 + 文案），不能只靠颜色；对比度按报告实测值（最低 5.41:1）。

## 2. 字体与排版

| 级别 | 字号/行高 | 用途 |
| --- | --- | --- |
| titleLarge | 24/30 SemiBold | 页面标题（"Agent 工作台"） |
| titleMedium | 17/24 SemiBold | 分区标题 |
| titleSmall | 16/24 Medium | 卡片主标题 |
| bodyLarge | 16/24 | 对话正文、列表主行 |
| bodyMedium | 14/22 | 次要正文 |
| labelLarge | 13/18 | 按钮、状态行 |
| labelMedium | 12/18 | 状态胶囊、辅助 |
| labelSmall | 11/16 | 技术字段说明 |
| Mono | 12/18 monospace | 路径、命令、tool 参数 |

## 3. 间距 / 圆角 / 动效

- 间距刻度：4 / 8 / 12 / 16 / 20 / 24 / 32 dp；页面左右 16 dp；分区之间 12–16 dp。
- 圆角：卡片 18 dp；输入框/代码块 12–18 dp；状态胶囊全圆；Sheet 顶部 24 dp。
- 动效：120 / 200 / 300 ms 三档（`Motion`）。当前只用于：折叠展开（AnimatedVisibility 默认）、导航切换（系统默认）。**不引入装饰性动画**。
- 点击区域：行高 ≥ 48 dp；发送按钮 ≥ 44×44 dp（实际 48）。

## 4. 组件（当前实现）

| 组件 | 位置 | 说明 |
| --- | --- | --- |
| `StatusDot` / `StatusChip` | `BoardScreen.kt` | 点 + 文案；颜色取 palette |
| `AttentionCard` | `BoardScreen.kt` | blocked 专用大卡（状态、标题、等待提示、项目行、chevron） |
| `CompactAgentRow` | `BoardScreen.kt` | 其他状态紧凑行（64–76 dp） |
| `CollapsibleHeader` | `BoardScreen.kt` | 已完成/其他分区折叠 |
| `ConversationScreen` | `ConversationScreen.kt` | 用户右气泡 / assistant 全宽文本 / thinking·tool 折叠行 |
| `ComposerBar` | `ConversationScreen.kt` | 1–5 行自适应、发送、三态回执提示 |
| `TechnicalInfoSheet` | `TechnicalInfoSheet.kt` | 技术字段 + 复制 |

## 5. 未落地（M2/M4）

- 正式图标集（当前用 `▤` `⚙` 字形占位；不引入 material-icons-extended，避免体积）
- Markdown/代码高亮/Diff（当前为纯文本）
- 品牌 App 图标（`Focus Orbit` 方向待评审，见报告 §8）
- 动效规范细化（列表新增/状态切换的具体曲线）
