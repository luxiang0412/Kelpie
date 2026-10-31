# Phase 0 阶段总结（一页）

> 时间：2026-10-31｜环境：Windows 客户端 + `luxiang@192.168.5.8`（Ubuntu 24.04，herdr 0.9.5，RTT 50 ms）
> 详细数据：`RESULTS.md`｜决策：`DECISIONS.md`｜问题：`ISSUES.md`｜后续：`NEXT-STEPS.md`

## 一、一句话

**控制面路线成立**（SSH → herdr JSON API → 事件驱动对账），**内容面用 pi/Claude 的 JSONL 也成立**；
但**读屏审批在手机网络下不安全**，审批必须走 L1 结构化，否则只能只读。

## 二、已验证（VERIFIED）

| 能力 | 结论 | 关键数据 |
| --- | --- | --- |
| SSH 连接 + 主机指纹显式校验 | 可用，fail-closed | 未知主机被拒且不落盘；篡改被拒；指纹与 `ssh-keygen -lf` 逐字符一致 |
| sshj 0.41.2 + Ed25519 | 可用 | connect 239 ms + auth 265 ms ≈ 500 ms；RSA 仍失败（产品只用 Ed25519） |
| herdr API 版本/能力协商 | 可用，但**必须按 schema/版本**，`ping.capabilities` 不是方法清单 | 0.9.5：102 方法、protocol 22 |
| `session.snapshot` 对账 | bridge 与 CLI 输出**完全一致** | 空/非空两种状态都验证过 |
| 事件订阅 | 可用；逐 pane 订阅 `agent_status_changed`（必带 `pane_id`）；不存在的 pane → `pane_not_found` | 空闲无心跳/无重放 |
| 写操作 | `pane.send_text` / `send_keys` / `agent.prompt` 均可用 | `agent.prompt` ack 301 ms |
| 写操作 Unknown 语义 | 账本三态、永不自动重试（单测覆盖） | 断线注入仍 NOT TESTED |
| `agent.wait` | 只是阻塞查询，**不是锁** | 达标 2 ms；未达标精确阻塞 3007 ms 后 `error.timeout`；working 期间他人写入 1 ms 被接受 |
| pi 作为数据源 | 可用 | `--session-dir` 扁平目录；JSONL 带 `id/parentId/timestamp` |
| SFTP 增量读取 | 可用，已优化 | 稳态 poll **195 ms**（原 718）；替换/截断均 Reset |

## 三、已否决 / 禁止（UNSAFE 或 BLOCKED）

| 结论 | 依据 |
| --- | --- |
| **手机端「一键批准」读屏审批** | 二次校验本地把错题率 24%→3.6%，但窗口 B 变 250 ms 后反弹到 **34%**（D22） |
| 用 `agent.wait` 做互斥/加锁 | 无锁、不排斥写者（实证） |
| 自动信任首次连接的主机指纹（TOFU） | P0-1 |
| 写操作超时自动重试 | 无幂等键，必然重复输入 |
| 用 `ping.capabilities` 判断方法可用性 | 只是 6 个特性开关 |
| 方案 B（streamlocal）在当前客户端栈 | Windows ssh 不支持 unix-socket `-L`；sshj 无 `direct-streamlocal` |
| 团队 Presence/Lease/Ownership | Herdr 无任何协作原语 |
| 用 pi 默认配置验证审批链路 | pi 不产生确认，`blocked` 造不出来 |
| 大文件整读 / 每次 poll 读前缀 | 50 ms RTT 下往返爆炸（I-44，D25） |

## 四、尚未验证（NOT TESTED / BLOCKED）

| 项 | 原因 |
| --- | --- |
| `events_lost` 复现与恢复 | 需要 >100 pane 量级事件，属压力测试（30 事件/25 s 卡顿不会触发） |
| 断线注入下的写操作三态 | 需要专门注入脚本，未做 |
| `agent_blocked` 与 L1 审批 | pi 不确认；Claude 需登录/额度（你已决定暂不做） |
| L2 的“blocked 前置条件” | 合成 TUI 不会被识别为 agent |
| Android 真机：延迟/耗电/后台/键盘/120 Hz | 只有模拟器 |
| `pane.send_keys` 各类按键映射全集 | 只测了 enter/ctrl+c/digit |

## 五、关键数字（Phase 1 预算用）

| 指标 | 实测 |
| --- | --- |
| 网络 RTT | 50 ms |
| SSH connect+auth | ≈ 500 ms（Ed25519） |
| 复用连接上单 API 请求 | 248 ms（其中 exec channel 201 ms） |
| 不复用连接（每次重连） | 1181 ms → **禁止** |
| 并发 exec channel 上限 | 9（第 10 个 `open failed`，连接不断）→ 客户端上限 4 |
| SFTP 单次 poll（稳态） | 195 ms；建议 500 ms–1 s 自适应轮询 |
| 状态变化 → API 可见 | 约 400 ms（pi 从 prompt 到 working） |

## 六、Phase 1 的硬性前置

1. **主机指纹**：显式确认 + fail-closed，不用系统 `known_hosts`（已实现并验证）。
2. **连接复用**：单连接 + 通道池，并发上限 4，禁止每请求重连。
3. **传输抽象**：`remote-api-bridge` / socat / streamlocal 三选一，启动时探测；capabilities 按 Transport 分别读取。
4. **写操作**：三态账本 + 永不自动重试 + 权威读取。
5. **订阅**：全局生命周期 + 逐 pane 状态；`pane.created` 后补订阅。
6. **SFTP**：句柄缓存 + 重叠校验 + 周期前缀复检（D25）。
7. **审批**：手机端不做一键批准；Phase 2 只做只读提示，审批留给 L1。
8. **Android**：仍需真机验证 sshj/Ed25519、后台与性能。

## 七、下一阶段建议顺序

1. 真机接入 → 复测 sshj + 单请求延迟 + 后台策略（Spike 6/7）。
2. Spike 3 收尾（pi 会话定位的“首次落盘时机”与多会话区分）。
3. 决定审批路线：做 L1（Claude hook / ACP）还是 Phase 1–2 只读。
4. 若仍想降延迟：评估方案 C（主机端复用脚本，需批准安装）。
