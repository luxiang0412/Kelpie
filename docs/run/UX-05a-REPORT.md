# UX-05a report — i18n 资源梳理与中英文覆盖（PARTIAL，含测试本地化修复）

- baseline：`8f580ce` / `ux/graphite-editorial`
- changed：
  - `feature/board/src/main/res/values/strings.xml`（英语基准）+ `values-zh-rCN/strings.xml`（简体中文），共 **46 key**
  - 已接线 `stringResource`：工作台（标题/刷新/选择主机/主机管理/展开收起/空态四项）、底部导航（Board/Hosts）、
    对话（返回/更多）、Composer（占位符/发送/发送中/无 agent/三态说明/长 Prompt 标题/取消/完成）、
    消息操作（复制/已复制/更多/复制原文/引用到输入框）、思考过程标签
  - **测试本地化**：`ComposerUiTest`、`MessageActionsTest` 原先断言中文字面量，在所有语言下都会脆断；
    改为 `InstrumentationRegistry.targetContext.getString(R.string.…)`，与语言无关
- 验证：
  - AVD `persist.sys.locale=en-US` → 截图 `ux05a-en.png`（Agent Console / Refresh / Select host / No agents / Host settings / Retry / Board / Hosts 全英文；host 名与代码保持原文）
  - 切回 `zh-CN` → `ux05a-zh.png`
  - 全量 UI 测试 **OK (26 tests)**（在 en 环境下运行，证明测试不再依赖中文）
- 四类结论：
  - **已实现并验证**：46 key 双语资源、三屏主文案接线、英文环境截图、测试去中文化
  - **已实现但未验证**：en + 320dp/1.6 英文换行
  - **未实现**：`StatusText`（普通 object，需 Context/Composable 化）、`HostsScreen` 全部文案、`TechnicalInfoSheet`、通知 channel 名与文案、大量 `contentDescription` 中文
  - **已知问题**：语言混排仍会出现在 Hosts 页与部分状态词；无「未翻译 key 一致性检查」脚本
- commit：见提交


---

## 补充（后续同一轮继续）：StatusText 与缓存 Banner 已本地化

- `design/Tokens.kt` 的 `StatusText.label/section` 由**普通函数**改为 **`@Composable`**，
  内部用 `stringResource(board_status_* / board_section_*)` → 状态词与分区标题随语言切换
- 工作台缓存 Banner 前缀抽成 `board_cache_banner_prefix`（en: "Showing cached content" / zh: "显示的是缓存内容"）
- 受影响调用点同步改（`CompactAgentRow`、`StatusChip`、`StatusDot`、`SectionHeader`、`CollapsibleHeader`、
  `TechnicalInfoSheet`），语义 lambda 里先把字符串取到局部变量（`@Composable` 不能进 `semantics {}`）
- **测试去中文化**：`BoardScreenTest`、`BoardBannerTest` 改为 `res(R.string.…)` 断言
- 验证：`:app:assembleDebug` + `assembleDebugAndroidTest` OK；AVD 全量 UI **OK (26 tests)**；
  英文截图 `ux05a-en-board.png`（Agent Console / Refresh / Select host / No agents / Add a host to start /
  Host settings / Retry / Board / Hosts 全英文），中文截图 `ux05a-zh-board.png`
- 剩余未接线的硬编码：`HostsScreen` 全部文案、`TechnicalInfoSheet` 其余字段、通知 channel 名与正文、
  部分 `contentDescription`（"几时同步"、tool 状态词等）—— 仍为 PARTIAL
