# NEXT-STEPS — 下一步执行建议

> 更新：2026-10-31 15:20（远端/本机同刻）
> 已完成：A-6（建 `test` 会话）、A-7（ed25519 专用密钥 + 授权）、A-8（沙箱目录）
> 当前卡点：`test` 会话里 **0 个 pane**，所有 pane 级实验都还没开始。
> 详见 `RESULTS.md` §9、`DECISIONS.md` D11/D13/D15/D16、`ISSUES.md` I-29…I-33。

## 0. 现在能做什么 / 不能做什么

| 能力 | 状态 |
| --- | --- |
| SSH 连接 + 指纹校验（Ed25519 + RSA） | 可做（Ed25519 正常；RSA 在 sshj 上仍失败） |
| `ping` / `snapshot` / 全局订阅 | 已做（VERIFIED） |
| 每请求延迟 / 并发上限 | 已做：复用连接 248 ms，并发上限 9 |
| 需要 pane 的一切（状态事件、写操作、`events_lost`、pi、审批） | **等批准 P-1** |
| streamlocal 对照实验 | **等批准 P-5**（只改本地临时 socket） |
| MaxSessions 真实配置值 | 需要 root（P-4，可选） |

## 1. 待批准（按顺序，都是小动作）

### P-1 建一个 workspace + pane（解锁 pane 级实验）

```bash
/home/luxiang/.local/bin/herdr --session test workspace create \
  --cwd /home/luxiang/spike-sandbox --label spike --no-focus
```

**副作用**：在 `test` 会话里新建 1 个 workspace、1 个 tab、**1 个 shell pane**（cwd 在沙箱里），不抢焦点。
**不影响**：`default`、`ljn`。
**回滚**：`herdr --session test workspace close <id>`（`workspace list` 拿 id），或整个会话 `server stop` + 删目录。

解锁后立刻能测（都是只读或无害）：
- `pane.agent_status_changed` 的真实事件载荷（idle/working）
- `pane.get` / `pane.process_info` / `pane.read` 的字段与 `revision`
- `terminal session observe` 只读帧（不影响其他客户端）
- 与 CLI 快照的逐项比对（这次有内容了）

### P-2 在沙箱 pane 里跑 pi（约 1 次模型调用）

```bash
# 在 P-1 建好的 pane 里（通过 herdr pane send_text 或直接在电脑上操作）
cd /home/luxiang/spike-sandbox && pi --session-dir /home/luxiang/spike-sandbox/pi-sessions "列出当前目录"
```

**副作用**：启动 pi，消耗**你的模型额度**（1 次很小的请求）；会话 JSONL 落在 `~/spike-sandbox/pi-sessions`（隔离，不污染 `~/.pi`）。
**回滚**：关掉 pane / 删除 `pi-sessions` 下的那次会话。
**产出**：验证 Spike 3 的核心假设——`pi` 的 JSONL 路径可预测、`--session-dir` 生效、herdr 能否识别 pi 为 agent（`agent_session` 是否出现）。

### P-3 pane 事件与写操作（需要 2–3 次小动作）

| 动作 | 目的 | 影响 |
| --- | --- | --- |
| `pane split` × 2 + `close` | 触发 `pane.created/closed`，观察全局订阅；测 pane id 是否复用 | 该会话内短暂多 2 个 pane |
| `pane.send_text` → 一个跑 `cat` 的 pane | 写操作语义 + 回显可观测性 | 向该 pane 输入文本 |
| 手工断开 channel 1 次 | `Unknown` 判定（不重试） | 仅本机断开 |
| （可选）停读订阅 30 s + 10 次 split/close | `events_lost` 复现 | 会话内增删 10 个 pane，不影响其他会话 |

建议顺序：`send_text`（最便宜）→ split/close（事件）→ `events_lost`。不做 200 次那种压力测试。

### P-4（可选）确认 MaxSessions 真实值

```bash
sudo -n sshd -T 2>/dev/null | grep -iE 'maxsessions|allowstreamlocal'
```

只读、不改配置；需要一次 sudo。若你不想给 sudo，就用观测值 9（已足够做预算）。

### P-5（可选）streamlocal 对照

```bash
# 本地临时 socket，用完删除；不改远端
ssh -N -L /tmp/herdr-test.sock:/home/luxiang/.config/herdr/sessions/test/herdr.sock luxiang@192.168.5.8
printf '%s\n' '{"id":"x","method":"ping","params":{}}' | socat - UNIX-CONNECT:/tmp/herdr-test.sock
```

**用途**：验证“不启进程、不占 MaxSessions”的通道是否更快（当前每请求 200 ms 主要花在 exec channel）。
**影响**：本地 `/tmp` 一个 socket；远端无进程、无文件。

## 2. 批准后的执行顺序与预期产出

```text
P-1  → 写操作无关的 pane 观察（快照比对、pane.get/read、observe）      ≈15 min
     → P-2 跑 pi（1 次小请求）→ 定位 JSONL → Spike 3 结论               ≈30 min
     → P-3 send_text / split / events_lost                            ≈30 min
P-5  → streamlocal 对照（若 P-1 后你想优化 200 ms）                      ≈15 min
P-4  → 只在你想把“9”变成确定值时做                                       ≈2 min
```

预期能关掉的待验证项：#4（pane id）、#8（SFTP vs tail 的在线部分）、#9（读屏审批，需先有 blocked 态）、#20（已关）、#22（`events_lost` 真实码）、#23（补订阅代价）、#24（`agent.wait` 并发）、#27（inode）。

## 3. Spike 3–8 调整（改用 pi）

| Spike | 调整 |
| --- | --- |
| **3 定位 JSONL** | 主路径改为 **pi**：`pi --session-dir ~/spike-sandbox/pi-sessions`，会话文件形如 `--<cwd>--/<ts>_<id>.jsonl`。**不再需要** `~/.claude-spike`、不需要改 `~/.claude`、不需要 Claude 登录。 |
| **4 增量解析** | 用 pi 的 JSONL（`session` 头 + `message`/`model_change`/`compaction`/`branch_summary`，`id`/`parentId` 树）。`SftpTail` 已离线验证，直接对真实文件跑。 |
| **5 审批安全** | pi 的“等你确认”来自扩展的 select/confirm，不像 Claude 有专门 `PermissionRequest` hook。**L1 结构化审批改到 Phase 3 用 Claude 或 herdr-acp 验证**；本轮只用假 TUI 做 L2 竞态测量（不需要真实 Agent，成本最低）。 |
| **6/7** | 仍需 Android 真机。 |
| **8 herdr-acp** | 需要装 Node.js（单独批准）；pi 已有 `--mode rpc`，可先作为“结构化通道”的替代对照。 |

## 4. 三个可能翻转的结论

| 结论 | 现状 | 翻转条件 |
| --- | --- | --- |
| 每请求 248 ms 可接受 | 未定稿 | 若 streamlocal 能降到 <100 ms，则方案 B 优先；否则只能靠减少请求数（事件驱动） |
| 有效上限 9 足够 | 未定稿 | 若 SFTP + 订阅 + observe + 4 并发实际会打满，则需要方案 B/C |
| sshj 可用 | JVM 上 Ed25519 通过 | Android 上失败 → 换库 |

## 5. 仍未做（并且不会在未批准时做）

- 未创建任何 workspace/pane；未运行 pi 或任何 Agent；未发送任何输入。
- 未做 `pane split` 压力测试、未做故障注入。
- 未改 `~/.claude`、`~/.pi` 的配置；未装 `herdr integration install pi`。
- 未用 sudo；未改 `sshd_config`；未升级/降级 herdr。

---

# Milestone 1 完成后的建议（M2 计划）

## 已完成（M1 验收）

- 只读看板：指纹确认 → 连接 → snapshot → 双层订阅 → 事件驱动对账 → Room 缓存 → 分组看板 + Pane 详情
- 真机验证：连接/分组/详情/手动重连/**真实断链 1.67 s 自动恢复**
- 测试：JVM 12/12；Room 仪器测试 4/4；Compose UI 测试在 MIUI 上挂起（I-59，BLOCKED）
- Spike A：BCAPP 委托方案可行（不删系统 BC）；JSch exec 快一倍（备选）
- Spike B：隔离 HOME 安装 pi 集成成功，`agent_session` 返回精确 JSONL 路径

## M2 建议（按优先级）

1. **多主机 / 多会话**：数据结构已按 `hostId+session` 设计；补 UI（列表 + 切换）与连接生命周期隔离（每主机一个 Supervisor）。
2. **Compose UI 测试**：换设备或改用 UiAutomator（I-59）；把它纳入 CI，避免 UI 回归靠肉眼。
3. **性能基线（Spike 7）**：冷启动、看板滚动、120 Hz 掉帧、键盘动画；用 Macrobenchmark + JankStats。
4. **写操作（Phase 2，仅 L1）**：`agent.prompt` + 结构化审批；用 pi 集成（D35）或 Claude hook；**不做读屏一键批准**（D22）。
5. **transcript 视图**：用 `agent_session.path`（D35）走 SFTP + `SftpTail`；先做 pi 的 JSONL 渲染。
6. **主机端 pi 集成**：若要在真实使用中拿 `agent_session`，需要在用户真实 `~/.pi` 安装集成（需批准，D35 已说明隔离方案）。
7. **传输优化（可选）**：JSch 对照（D33）或 streamlocal 自研 channel（D20）—— 都要先有明确收益。

## 当前未做、也不打算在 M1 做的

- 读屏审批、终端接管、后台常驻服务、推送、多主机 UI、性能优化。

## M2-1 完成情况（2026-10-31）

- ✅ 多主机配置（每主机独立私钥、独立 known_hosts、独立 `ConnectionSupervisor`）
- ✅ 看板主机 chips（状态点 + pane 数）+ 主机管理页（增删改、连接/断开、私钥导入）
- ✅ 真机验证：两台主机（一台不可达）互不影响；切换主机数据正确
- ⛔ Compose UI 测试仍在 MIUI 上挂起（I-59）

### M2-2 候选

1. **性能基线（Spike 7）**：冷启动/滚动/键盘/120 Hz，Macrobenchmark + JankStats。
2. **transcript 视图（只读）**：用 `agent_session.path`（D35）+ SFTP + `SftpTail` 渲染 pi 的 JSONL。
3. **主机体验**：只连活动主机开关、排序/搜索、跨主机聚合“等你处理”总数。
4. **写操作（仅 L1）**：需先定审批路线（Claude hook / herdr-acp）。


## M2 完成后的状态（2026-10-31）

- ✅ M2-1 多主机：两台**真实**主机（.5.8 / .5.31）并行连接、chips 切换、独立 Supervisor/信任库/Room
- ✅ M2-2A 性能基线：冷启动中位 555 ms、滚动 jank 0.90%（120 Hz）
- ✅ M2-2B transcript 只读：`agent_session.path` → SFTP 尾部快照 → pi JSONL → 消息列表；2 s 自动刷新
- 单测：17/17（分组 5 + 编解码 9 + 解析器 3）；Room 仪器测试 4/4

### M3 候选（按价值）

1. **transcript 增量化**：移植 `SftpTail`（D25 三条硬约束）+ Room 缓存 + 离线可看 + 只读历史。
2. **Macrobenchmark**：`:benchmark` 模块、StartupTiming/FrameTiming、Baseline Profile、流式输出场景。
3. **输入与审批（仅 L1）**：`agent.prompt` + 结构化审批（pi 集成或 Claude hook）；D22 禁止读屏一键批准。
4. **多主机聚合**：跨主机“等你处理”总数、只连活动主机开关（D37 边界）。
5. **Claude/Codex 的 transcript parser**（Provider 化，D19）。


---

# M4 完成情况（2026-10-31 夜）

## 已经走通的基本闭环（pi）

```text
看板看状态 → 进 pane → 发消息（agent.prompt，三态）→
看回复（详情页最近输出 / 对话页结构化）→ 后台收到“已完成”提醒
```

| 能力 | 状态 | 备注 |
| --- | --- | --- |
| 发消息 | ✅ | 详情页 + 对话页底部；超时=Unknown，不自动重发 |
| 看回复（终端尾部） | ✅ | 详情页「最近输出」，2 s 刷新 |
| 看回复（结构化） | ✅ | 需 `agent_session`（已在那两台主机装好 pi 集成 v9；**装完要重启 pi**） |
| 后台提醒 | ✅ | `dataSync` 前台服务；`completion_seq` 判完成；5 s 兜底对账；Android 15 有 6h/24h 配额 |
| L1 审批客户端 | ✅（PARTIAL） | nonce/过期/一次性/审计都验证了；**触发源**未定 |

## M5 候选（按价值排序）

1. **真实审批触发**（这块是产品最后一块拼图）
   - Claude：`PermissionRequest` hook（一次调用一次请求）→ 需要批准改 `~/.claude/settings.json`；
   - pi：写扩展注册工具（回调里用 `.req/.answer`）→ 只影响 `~/.pi`，但需查清扩展 API。
2. **对话页体验**：无 `agent_session` 时退化为“终端尾部 + 发送”（免得两个页面分裂）；Markdown/代码高亮。
3. **性能**：Macrobenchmark + Baseline Profile + R8；长对话 jank 30–44% 需要可信测量后再优化。
4. **多主机聚合**：跨主机“等你处理”总数、只连活动主机开关。
5. **更多 agent**：Claude transcript 解析、Codex app-server。
6. **耗电基线**：监控模式 5 s 对账的实际耗电（`batterystats`，1 小时）。

## 风险提醒

- 后台提醒受 Android 15 配额与厂商策略影响，**不能承诺“像微信一样”**；
- `agent_session` 依赖集成 + 重启，用户侧要写清步骤；
- 审批在触发源定型前不要对外宣称可用。

## M2-B 完成后的下一阶段（M2-C）

1. 增量渲染（I-79）：历史条目缓存复用，只重解析最后一条；映射改 append。
2. 真实流式会话验收：新消息提示、缓存失效、折叠状态不跳变。
3. 测试环境：新建隔离 AVD（I-75）+ 非 MIUI 设备或 CI 跑 FrameTimingMetric（I-74/I-78）。
4. Markdown 扩展（表格/嵌套列表）需重新评估是否引入库。
5. 仍未做：Composer 全面重构、跨主机聚合、正式图标、Diff Viewer。


## M2-C1 完成后（M2-C2）

1. Benchmark 补齐确定性增量场景：追加 100 条 / 更新 100 次 / 100 次 ToolResult 更新 / 分支切换 / 展开折叠 / 滚动+新消息；
2. 真机（非 MIUI）或 CI emulator 取 FrameTimingMetric，与 JVM 微基准分开记录；
3. 真实 pi 会话验收：流式增量、新消息提示、分支切换、缓存失效；
4. 视真机数据决定是否把分支解析/映射也做成增量（I-81）；
5. 仍未做：Composer 重构、跨主机聚合、Markdown 表格/图片。

## M2-C2 完成后（M2-C3）

1. 取得授权后执行真实 Pi 会话 E2E（发送 → Accepted → 增量消息 → 新消息提示 → 分支切换），记录增量命中；
2. 草稿跨进程/横竖屏/删除主机的人工验收（I-87）；
3. 中文输入法组合输入、TalkBack、最大字号的人工走查；
4. 真机 FrameTimingMetric（非 MIUI 设备）补齐滚动/追加/IME 场景；
5. 仍未做：跨主机聚合、结构化审批、Markdown 表格/图片。

## M2-C2-V 之后

1. 批准并执行真实 Pi E2E（`test` / `w7:p1`，prompt 与回滚方式见 M2-C2-VERIFICATION §2），记录增量命中；
2. 真机人工走查：中文 IME 组合输入、光标/选区、粘贴堆栈、TalkBack、最大字号、旋转（I-91）；
3. 草稿可靠性加固：`onStop` 强制 flush（I-90）、Room 迁移测试（I-89）；
4. 技术信息 Sheet 增加「待确认写操作」列表（I-92）；
5. 仍未做：跨主机聚合、结构化审批、Markdown 表格/图片。
