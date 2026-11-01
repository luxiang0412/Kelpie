# UX-09 — 全局 QA（P1/P2/P5/P8 实测 + 覆盖说明）

> 本轮在 **AVD `emulator-5556`（Android 34）** 上按 DESIGN-SYSTEM §9.1 采集 4 个 profile。
> **真机 `192.168.1.8:41449` 本轮已离线**（`device not found`），真机截图沿用更早的 `real-*` 系列，未冒充本轮数据。

## 1. 本轮截图矩阵（实际产出）

| Profile | 规格 | 来源 | 文件 | 结论 |
| --- | --- | --- | --- | --- |
| P1 | 393dp / 1.0 / en / Light | AVD | `ux09-p1-board-light.png` | ✅ 标题 26/34、主机选择器在标题下、1dp 分隔线、空态居中 |
| P2 | 393dp / 1.0 / en / Dark | AVD | `ux09-p2-board-dark.png` | ✅ 深色层次（canvas/surface/raised）、状态词单一语义色 |
| P5 | 320dp / **1.6** / en / Dark | AVD（`wm size 640x1280`、`density 320`） | `ux09-p5-320-dark-1.6.png` | ✅ 标题与 Refresh 同行不裁剪、空态三行完整、底部导航两标签完整 |
| P8 | 600dp / 1.0 / en / Light | AVD（`wm size 1600x2560`、`density 320`） | `ux09-p8-600-light.png` | ⚠️ 布局不溢出，但**内容未按 ≥600dp 居中（仍左对齐整宽）**；仅 Composer 有 720dp 约束 |

**未采集**：P3/P4（zh）本轮用的是 en；P6（320/1.6 light）；P7（393/1.3）；对话页与 Composer 的完整矩阵；真机全部。

## 2. 与仓库原型截图的对照（`ux-d0/prototype/screenshots/`）

| 原型 | 我们当前 | 差异 |
| --- | --- | --- |
| `board-zh-light/dark` | 结构一致：标题 → 主机选择器 → 分区 → 底部导航 | 原型的分区计数与行留白更松；我们缺少「已连接」低强调状态行（规格允许不显示） |
| `chat-zh-light/dark` | Agent 全宽、用户右气泡、Tool 卡、消息尾 Copy/More | 原型源码有命中高亮与全文阅读页入口，我们**没有高亮** |
| `focus-zh-*`（Composer） | 双层 Surface、圆形 Send、Expand | 原型有 Mic 位（我们未实现，按规格隐藏 ✅） |
| `*-320dp-1p6x` | 我们的 320/1.6 标题不裁剪 | 原型窄屏下标题与按钮分两行，我们同一行（宽度仍够） |

## 3. 逐项自检（DESIGN-SYSTEM §10.3）

- [x] 未触碰 SSH 指纹、Herdr 写入语义、WriteLedger 规则
- [x] 无假按钮（Mic/附件/活动/跨主机聚合均未出现）
- [x] 页面级硬编码色值已收敛到 Tokens（`errorFg/focusRing/SectionTitleStyle/MdH1-3`）
- [x] `done ≠ idle`；`blocked` 不提供一键批准
- [x] 消息尾「复制 + 更多」常显；正文可长按选择
- [ ] 语音 B 规则：**未实现**（无 STT）
- [x] 新增文案均有 zh/en 资源（Hosts / Sheet / 搜索 / 工具卡）
- [ ] IME / Insets 双倍留白：**未在真机验证**
- [x] 未测项已在各 task 报告中如实记录

## 4. 仍未解决（明确列出）

1. **命中高亮缺失**（UX-06）：只做定位不做高亮，需改 Markdown SpanStyle 管线；
2. **600dp 布局未居中**（P8 截图已暴露）：除 Composer 外，看板/对话没有 720dp 内容最大宽；
3. **真机全部 profile 未采集**：设备离线；
4. **ToolResult 全量输出页**：已实现（`FullOutputDialog`），但"仅保存部分输出"的判定逻辑未做（数据源无法区分）；
5. **通知文案未本地化**；
6. **UX-07b 语音**：SKIPPED（无本地识别路径）。
