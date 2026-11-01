# UX-00 更正：必读文档清单（6/6 全部存在，已读全）

> 本文件更正 `00-DOC-INVENTORY-AND-GAPS.md` 中「2 份文档缺失」的错误结论。
> 现状：6 份规格**全部存在**，我已全部读完。此前判断错误的原因：我只执行了一次 `ls` 且只看了部分输出就下结论，没有复核。

## 1. 清单（全部存在）

| # | 文档 | 行数 | 读取状态 |
| --- | --- | --- | --- |
| 1 | `HERDR-ANDROID-UI-ENGINEERING-CONTRACT-v0.1.md` | 312 | ✅ 已读（契约、红线、Task Packet 13 字段） |
| 2 | `HERDR-UX-D0-DESIGN-SYSTEM-v0.1.md` | 546 | ✅ **已读全**（此前误判为不存在） |
| 3 | `HERDR-UX-D0-BOARD-SPEC-v0.2.md` | 213 | ✅ 已读 |
| 4 | `HERDR-UX-D0-CONVERSATION-SPEC-v0.2.md` | 577 | ✅ 已读 |
| 5 | `HERDR-UX-D0-COMPOSER-SPEC-v0.2.md` | 574 | ✅ **已读全**（此前误判为不存在，误用了 v0.1） |
| 6 | `HERDR-UX-D0-VISUAL-REVIEW-v0.1.md` | 114 | ✅ 已读 |

另有可用参考：`ux-d0/prototype/`（HTML 原型 + **12 张 393dp 三屏双语双主题截图 + 3 张 320dp/1.6 截图** + contact sheet）。
**重要**：这些对照图一直在仓库里，我之前没有拿它们做视觉比对，属于流程失误。

## 2. 读完全部规格后新增的偏差（此前未登记）

### 2.1 Design System v0.1

| 规格 | 我的实现 | 严重度 |
| --- | --- | --- |
| 术语：`blocked`=**需要处理**、`Accepted`=**指令已接收**、`UnknownWrite`=**发送结果无法确认** | 用「需要你」「已接受（Accepted）」「结果未知（Unknown）」 | 高（安全文案） |
| `nav_board` zh=**工作台** / en=**Agents**；工作台标题 en 也用 Agents | 导航仍是「看板 / Board」，标题 en 已是 Agents → 同屏不一致 | 中 |
| 语义色值：`attention` L `#8B3E0A`/`#FFF2E6`、D `#FFC28A`/`#3A2B23`；新增 `error` L `#B42318`/`#FFF0EF`、D `#FFB4AB`/`#402524` | 我用了旧色值（L `#98470B`/`#FFF3EA`，D `#FFB86B`/`#3A2A18`），且**没有 error token** | 中 |
| 字阶：`titlePage` 18/26、`bodySecondary` 14/21、`labelAction` 13/20 **Medium**、`mdH1` 21/29、`mdH2` 18/26、`mdH3` 16/24 | 对话标题 16/24；bodyMedium 14/22；labelLarge 14/20 **SemiBold**；Markdown 标题 24/30、17/24、16/24 | 高 |
| `shape.card` = **16dp** | `Radius.card` = 18dp（部分处已改 16，但 token 未统一） | 中 |
| `stroke.focus` = **2dp Accent**；`borderSubtle` 只能做装饰，**不能单独表示控件边界** | Composer 输入框只有 1dp borderSubtle，**没有 focus ring** | 高（可访问性） |
| Banner 优先级：**安全阻断/主机身份 > Unknown > 认证失败 > 连接/缓存 > Blocked > 普通同步** | 我的顺序是 Unknown **最优先**，host-key/auth 排在后面 | 高（安全排序） |
| 多问题并存：一条最重要 + **「查看全部问题」入口** | 只有一条 Banner，**没有查看全部** | 中 |
| Motion tokens（80–320ms）+ **减少动画时直接 snap** | 用 `AnimatedVisibility` 默认时长，无 reduce-motion 分支 | 中 |
| 每项设计改动需 **Before/After 同 Fixture 同主题同宽度同字体** 对照截图 | 多数任务只有 After | 高（验收证据不足） |
| §10.2 列有 **UX-00b（Shape/Spacing/Icons）** 任务包 | 主任务书未列该包，我未做也未登记差异 | 中 |

### 2.2 Composer Spec v0.2

| 规格 | 我的实现 | 严重度 |
| --- | --- | --- |
| `OfflineDraft`：**Send 禁用**，文案「已离线，可继续编辑草稿」 | 离线时 Send 仍可点（点了才 Rejected） | 高 |
| 文案：`指令已接收` / `发送被拒绝` / `发送结果无法确认` + **`查看发送记录`** 动作 | 用「被拒绝：code」「结果未知（Unknown）」且无「查看发送记录」按钮（入口在工作台 Banner/技术 Sheet） | 高 |
| Send **视觉圆面 40dp**，点击区 ≥48dp | 使用胶囊按钮 | 中 |
| 聚焦：1–2dp Accent **Focus Ring** | 无 | 高 |
| ≥600dp：Composer **最大宽 720dp 居中** | 全宽 | 低 |
| 长编辑文案「编辑长指令 / 完成编辑 / 取消编辑」 | 「长 Prompt / 完成 / 取消」 | 低 |
| `IdentityUncertain` 状态：明确告知身份不可靠并让用户选择恢复/放弃草稿 | 只有 NeedsConfirm 拒绝发送，无草稿归属提示 | 高 |
| Insets 单一消费路径，禁止双层 `imePadding+navigationBarsPadding` 叠加 | 目前单层（Composer 内），但未验证根部是否叠加 | 待验 |

### 2.3 Engineering Contract v0.1

| 规格 | 状态 |
| --- | --- |
| Task Packet 必须填 13 个字段，禁「看起来好」 | 我的报告普遍缺 `禁止改动范围`、`diff 概述`、`Before/After` |
| 缺失翻译需静态检查 | 未做 |
| 依赖方向 UI→VM→Repo，Composable 不直接发网络 | ✅ 符合 |

### 2.4 Visual Review v0.1

| 规格 | 状态 |
| --- | --- |
| 仓库内已有 12 张 393dp + 3 张 320dp/1.6 原型截图可作视觉对照 | ❌ 我未使用（最大遗漏） |
| `BOARD-HOST-001` / `CMP-STYLE-001` 仍是 **Proposed**，未获用户确认 | 我实现为默认值并标注 PROVISIONAL ✅ |
| 明确「禁止把 UX-D0 全套文档作为一条『全部实现』任务」 | 我按任务包推进 ✅ |

## 3. 修正后的结论

- 文档不缺；缺的是我**先读全再动手**的纪律。
- 现有实现与规格的偏差集中在三类：**安全相关文案与优先级**、**字阶/色值/描边等 Token**、**缺失的交互状态（离线禁用发送、focus ring、身份不确定、查看全部问题、发送记录入口）**。
- 下一步应按 Design System §10.2 的排序重做：`UX-00a/00b`（Token 收敛）→ `UX-01a/01b` 回归 → `UX-02a/b/c` → `UX-03a–d` → `UX-04a–c` → `UX-05` → `UX-07` → `UX-08` → `UX-09`，每个含 Before/After 对照截图。
