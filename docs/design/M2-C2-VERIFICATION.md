# M2-C2 验收报告（真实验收与可靠性收尾）

> 基线代码：`ddeff85`｜设备：Redmi 22021211RC（MIUI/A14，真机）+ 隔离 AVD `herdr_m2c1`（Android 34）
> 远端：`luxiang@192.168.5.8`，仅使用专用实验 Session `test`

## 0. 摘要

| 项 | 状态 |
| --- | --- |
| 远端环境只读确认 | ✅ 已完成（`test` 为专用实验 Session，2 个 pi pane 空闲） |
| 真实 Pi 会话 E2E（实际写入） | ❌ **未执行**：按任务要求需先获批，本轮未取得该轮授权（见 §2） |
| 草稿恢复专项（导航/旋转/进程死亡/防抖窗口/切换/删除主机/上限） | 部分 ✅（代码与单测）、部分 ❌（真机进程死亡未测） |
| Action Journal 跨进程可靠性 | ✅ **发现真实缺陷并修复**（I-88），新增 5 个回归测试 |
| 草稿数据库安全与迁移 | ✅ 已核查；新增显式 `MIGRATION_1_2`（不清库） |
| 真机人工交互（中文 IME / TalkBack / 旋转 / 最大字号） | ❌ 未执行（需要人工操作，本轮无人工验收环节） |

**本轮最重要的产出**：发现并修复了 **Unknown 写操作在进程重建后丢失** 的真实缺陷。这不是新功能，是可靠性缺口。

---

## 1. 已实现并验证

### 1.1 远端环境只读确认（未做任何写入）

```
$ ssh luxiang@192.168.5.8 "herdr session list"
default   running  /home/luxiang/.config/herdr
ljn       stopped  /home/luxiang/.config/herdr/sessions/ljn
test      running  /home/luxiang/.config/herdr/sessions/test
```

`herdr --session test api snapshot`（脱敏摘要）：

| pane | agent | agent_status | completion_seq | cwd |
| --- | --- | --- | --- | --- |
| `w7:p1` | pi | idle | 112 | `~/project/private/herdr-gui-research` |
| `w8:p1` | pi | idle | 120 | `~/spike-sandbox` |

结论：`test` 仍是**独立目录**的实验 Session；两个 pi pane 均 idle，`agent_session` 均为 jsonl 路径；
未触及 `default` / `ljn`；未读取任何其他 Session 的数据。

### 1.2 Action Journal 跨进程持久化（I-88，修复）

**缺陷**：`WriteLedger` 的记录只存在内存 `LinkedHashMap` 里。进程被杀后，
「结果未知（Unknown）」的操作**彻底消失** —— 用户再也看不到待确认操作，
违反「Unknown 必须保留、允许用户重新检查」的要求。

**修复**（最小实现）：
- `WriteLedger` 新增可选 `PendingWriteSink`，只在 **Pending/Unknown** 变化时回调；
  Accepted/Rejected 不落盘（已完结，无需跨进程）。
- `WriteLedger.restore(records)`：启动时恢复未确认记录，**不触发任何发送**，`autoRetryAllowed` 恒为 false。
- Room 表 `pending_writes`（在 `herdr-drafts.db` 内）+ `RoomPendingWriteSink`；
  只存审计字段（method/target/payload sha256/时间/尝试次数/结果），**不额外复制 prompt 全文**。
- 容器启动时 `restorePendingWrites(load())` 回填。

**回归测试**（`WriteLedgerPersistTest`，5 个，全通过）：
1. Pending 写入 sink；Accepted 后从 sink 消失；
2. Unknown 持久化 → 新账本 `restore` 后仍是 Unknown，且 `autoRetryAllowed == false`；
3. Rejected 不进 pending 集合；
4. 相同文本重发 → **新的 clientRequestId**，旧 Unknown 保留（审计两次尝试），payload 指纹相同；
5. `restore` 忽略已 Accepted 的记录。

### 1.3 草稿数据库安全与迁移核查（§5）

| 检查项 | 结论 |
| --- | --- |
| `herdr-drafts.db` 是否启用破坏性迁移 | ✅ **不是**：`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2).build()`，无 `fallbackToDestructiveMigration`；`herdr.db`（纯缓存）才用破坏性迁移 |
| 是否存在明确 Migration | ✅ 新增 `MIGRATION_1_2`（`CREATE TABLE pending_writes`），升级**不清空草稿** |
| Room Migration 测试 | ❌ 未写 instrumentation 迁移测试（记录为 I-89） |
| Android Auto Backup 是否含草稿 | ✅ `android:allowBackup="false"`（Application 级）→ 草稿、密钥都不进云备份/设备迁移 |
| 是否需要从默认备份排除 | 不需要（已全局关闭备份） |
| 删除主机是否真删草稿 | ✅ `removeHost` → `drafts.deleteByHost(id)`；单测 `deleteByHost_onlyAffectsThatHost` |
| 草稿/密钥是否进日志 | ✅ 未在日志中打印草稿文本；本轮报告只出现长度与指纹，不含内容 |

### 1.4 自动化测试（回归）

```
Compose UI:  OK (16 tests)   ← 看板 9 + Composer 7，无回归
JVM:         全部通过         ← 新增 WriteLedgerPersistTest 5 个
```

---

## 2. 未执行：真实 Pi E2E（需要批准）

按任务要求，写入远端之前必须列出计划并取得批准。本轮**没有**获得该轮明确批准，因此**未执行任何写操作**。

### 拟执行的测试计划（待批准）

| 项 | 内容 |
| --- | --- |
| 目标 | Session `test` → pane `w7:p1`（cwd `~/project/private/herdr-gui-research`，pi，idle） |
| 拟发送 Prompt | `仅回复一行：M2C2V-ACK-<时间戳>`（不含任何命令、不触发工具调用） |
| 写入范围 | 该 pane 的 pi 会话 JSONL（`~/.pi/agent/sessions/--home-luxiang-project-private-herdr-gui-research--/*.jsonl` 追加一条事件） |
| 不触碰 | 其他 Session、全局 `~/.pi` 配置、Claude hook、任何 git 仓库文件 |
| 回滚方式 | 无需回滚代码/配置；仅在会话历史里留下一条 `M2C2V-ACK-*` 消息（可忽略）。若需彻底清理，可在该 pane 用 `/new` 开新会话（**需另行批准**） |
| 能否重复 | 可以，每次发送产生新的 Action ID，旧记录保留 |

### 因此本轮**未验证**的 E2E 项

1. 真机发送 → Herdr Accepted；2. Pi 实际收到；3. JSONL 新增；4. 增量显示；
5. 新消息滚动位置；6. 上滑不跳底；7. 「新消息」回到最新；8. Tool 关联；
9. 断线恢复不重发；10. 分支切换；11. APPEND/PATCH/FULL 命中计数。

---

## 3. 草稿恢复专项（§3）逐项状态

| 场景 | 状态 | 说明 |
| --- | --- | --- |
| 导航离开并返回 | ✅ 代码路径 | `closeConversation` → `flushDraft()`；返回时 `loadDraft`（不覆盖已输入） |
| 横竖屏切换 | ⚠️ 未真机验证 | Activity 重建后 ViewModel 存活，草稿在 Room 中，理论保留 |
| 系统回收进程后重开 | ⚠️ 未验证 | 依赖 Room 写入；请在批准 E2E 时一并测 |
| 400ms 防抖窗口内终止 | ⚠️ 已知风险 | 防抖期间被杀 → 该次输入可能未落盘（**记录为 I-90**，修复建议：`onStop` 同步落盘或缩短防抖） |
| 不同 Agent Session 切换 | ✅ 单测 | `differentAgentSessions_differentKeys` |
| 同 Pane 退出并重启 Agent | ✅ 单测 + 发送前校验 | 占用指纹变化 → key 变化；若指纹巧合相同，`SendTargetCheck` 返回 NeedsConfirm |
| 删除主机后清理 | ✅ 单测 | `deleteByHost_onlyAffectsThatHost` |
| 长 Prompt 8000 字符上限 | ✅ 单测 | `oversizedDraft_isTruncated` |
| 旧发送未完成时继续输入 | ✅ 单测 + UI 测试 | Accepted 仅在 `text == snapshot` 时清空 |

> 明确区分：以上「✅ 单测」只覆盖**逻辑**，不等于真机进程死亡/旋转实测通过。

---

## 4. 已知问题（本轮新增）

| # | 问题 | 影响 | 建议 |
| --- | --- | --- | --- |
| I-88 | Unknown 写操作跨进程丢失 | ✅ **已修复**（本月） | 已加持久化 + 回归测试 |
| I-89 | 草稿库缺少 Room Migration instrumentation 测试 | 迁移回归只能用编译/人工验证 | 下轮加 `MigrationTestHelper` |
| I-90 | 防抖窗口内进程被杀可能丢最后一次输入 | 极端场景丢最新草稿 | 在 `onStop`/`DisposableEffect` 强制落盘，或把防抖缩到 150ms |
| I-91 | 真机中文 IME / TalkBack / 旋转 / 最大字号未人工验收 | 输入体验的真实手感未知 | 需人工走查（本轮无人工环节） |
| I-86 | 真实 Pi E2E 未执行 | 发送侧变更未经真实远端确认 | 待批准后执行（§2 计划已备好） |

---

## 5. 剩余风险

1. **Unknown 恢复后的呈现**：持久化已具备，但 UI 目前只在 composer 内显示最近一次 Unknown 的待确认条目；
   进程重建后需要刷新一次会话/重进对话才能看到恢复的记录（尚未在 UI 顶部做「历史待确认」入口）。
2. 草稿为空但 `pending_writes` 仍有记录时，用户可能不知道该确认什么 → 建议 M2-C3 在技术信息 Sheet 里列出待确认操作。
3. 真实 E2E 未做，因此「Herdr 接受 ≠ Pi 已处理」这条语义只在 UI 文案与单测层面成立。

---

## 6. 建议的下一步（不自动进入）

1. 批准 §2 的 E2E 计划 → 执行并记录 APPEND/PATCH/FULL 命中与耗时；
2. 真机人工走查中文 IME、光标/选区、粘贴堆栈、TalkBack、最大字号、旋转（I-91）；
3. 补 Room 迁移测试（I-89）；防抖落盘加固（I-90）；
4. 技术信息 Sheet 增加「待确认写操作」列表（承接 I-88 的 UI 呈现）。
