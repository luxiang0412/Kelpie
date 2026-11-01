# UX-00 审计：设计文档 → 源码映射

## 1. 页面 → Composable → ViewModel → Repository

| 页面 | Composable | 位置 | ViewModel/状态 | 数据来源 |
| --- | --- | --- | --- | --- |
| 工作台 | `BoardScreen` / `AttentionCard` / `CompactAgentRow` | `feature/board/.../BoardScreen.kt:64,268,331` | `HostsViewModel`（`:22`，`UiState/BoardView/HostState`） | `HostConnectionManager` + `BoardRepository`(Room) |
| 对话 | `ConversationScreen` / `ComposerBar` | `ConversationScreen.kt:77,471` | `HostsViewModel.transcript/composer/pendingWrites` | `TranscriptTailer` → `PiTranscriptParser` → `TranscriptStore` |
| 技术信息 | `TechnicalInfoSheet` | `TechnicalInfoSheet.kt:42` | 由 `AppRoot` 传 pane + `pendingWrites` | `PendingWriteDao` |
| Markdown | `MarkdownText` / `inline()` | `markdown/MarkdownText.kt:65,233` | `MarkdownAstCache` + `MarkdownParser` | `ChatMessage.text` |
| 主机管理 | `HostsScreen` | `HostsScreen.kt` | `HostsViewModel` | `HostRepository`(SharedPreferences) |

## 2. 设计系统 / 数据层关键文件（均已存在，切勿重建）

- Token/主题：`feature/board/src/main/kotlin/dev/herdr/feature/board/design/Tokens.kt`（`HerdrPalette`、`LightPalette/DarkPalette`、`Space`、`Radius`、`Motion`、`StatusText`、`MonoStyle`、`typography()`、`LocalHerdrPalette`）、`Theme.kt`
- 草稿：`core/data/.../drafts/Drafts.kt`（`DraftDatabase` v2 + `MIGRATION_1_2`、`DraftStore`、`DraftKey`、`SendTargetCheck`）
- 未确认写操作：`drafts/PendingWrites.kt`（`pending_writes` 表 + `RoomPendingWriteSink`）+ `core/herdr/.../WriteLedger.kt`（`PendingWriteSink`）
- 增量渲染：`conversation/IncrementalConversationMapper.kt`、`ConversationResolve.kt`、`ConversationBranch.kt`
- 连接：`core/data/.../HostConnectionManager.kt`（连接/重连/看板/写操作）

## 3. 现状能力与差距（相对 UX-D0）

| 项 | 现状 |
| --- | --- |
| 已接通 | 看板（真实数据/分区/状态词）、对话（Markdown 子集/折叠/增量）、Composer（1–5 行/长编辑/三态）、草稿（含跨进程）、Unknown 持久化 + 技术信息入口 |
| 未接通 | 结构化审批触发源、跨主机聚合、语音/附件、活动 Inbox、正式图标 |
| 视觉差距 | Token 缺 borderSubtle/raised 分层；字阶实际只用 3 档；`›`/`▸`/`⤢` 字形当图标；InlineCode 用空格伪 padding；顶栏大字体裁剪（I-93） |

## 4. 基线测试结果

见 `PROGRESS.md` 的 baseline 行（JVM 全绿；UI 16/16 在 AVD）。
