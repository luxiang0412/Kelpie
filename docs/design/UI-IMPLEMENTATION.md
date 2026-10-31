# UI-IMPLEMENTATION — M0 + M1 改动记录

## 1. 改动范围

| 文件 | 变更 |
| --- | --- |
| `core/data/HostConnectionManager.kt` | **P0**：`startAll()` 幂等（不再重连）；`connect()` 才是唯一显式重连入口；`setMonitoring()` 控制 5 s/2 s 低频对账；`HostState` 增加 `dataStale/dataAtMs/dataVersion`（传输与数据有效性分开） |
| `app/HerdrApplication.kt` / `app/MonitorService.kt` | 启动只调一次 `startAll()`；服务启停时 `setMonitoring(true/false)` |
| `feature/board/design/Tokens.kt` | 新增 Quiet Command tokens（颜色/字阶/间距/圆角/动效/状态文案） |
| `feature/board/BoardScreen.kt` | **重写**：紧急度分区（需要处理→运行中→已完成→其他）、blocked 大卡 + 紧凑行、主机筛选下拉、缓存/传输状态分开展示、底部导航 |
| `feature/board/ConversationScreen.kt` | **新增**：角色区分（用户右气泡/assistant 全宽）、thinking/tool 折叠、无 `agent_session` 时降级为终端尾部、Composer 1–5 行 |
| `feature/board/TechnicalInfoSheet.kt` | **新增**：技术字段 + 复制；`revision` 标注为诊断 |
| `feature/board/AppRoot.kt` | **重写**：看板 ⇄ 对话直达；主机为第二个一级目的地；技术信息走 Sheet；保留指纹确认对话框 |
| `feature/board/PaneDetailScreen.kt` / `TranscriptScreen.kt` | **删除**（能力并入 W2/W3） |
| `feature/board/Theme.kt` | 改为薄封装到 `HerdrThemeCompat` |
| `app/AndroidManifest.xml` | 增加 `<profileable android:shell="true"/>`（release 可测量） |

## 2. 导航（与设计报告一致）

```text
底部导航：看板 | 主机
看板 ──点 Agent 卡片/行──▶ 对话（隐藏底部导航，输入栏占底部）
对话 ──「技术信息」──▶ Bottom Sheet（pane_id / agent_session / cwd / terminal_id / revision）
对话 ── 返回 ──▶ 看板（保留看板滚动与筛选状态）
```

- 不再有“Pane 详情”中间页；
- 返回键：对话 → 看板；Sheet → 关闭 Sheet；Root 不拦截（允许系统预测性返回）。

## 3. 状态表达

| 层 | 数据 | UI 表现 |
| --- | --- | --- |
| 传输 | `HostState.connKind` | 连接正常时**不显示横幅**；异常时一行 + 「重试」 |
| 数据有效性 | `HostState.dataStale / dataAtMs` | 缓存时显示「显示的是缓存数据 · 上次同步 x」 |
| 后台监控 | `manager.monitoring` | 主机页开关；通知文案不承诺常驻 |
| Agent 状态 | `pane.status` | 5 状态：需要你/运行中/已完成/空闲/状态未知（`StatusText`，唯一来源） |
| 写操作 | `WriteOutcome` | 已发送 / 被拒绝 / 结果未知（不自动重发），composer 下常驻说明 |

## 4. 已知取舍

1. **跨主机聚合未做**：看板仍是“选中一台主机看一台”（顶栏下拉切换）。M2 再做跨主机合并与“等你处理总数”。
2. **Markdown 未做**：assistant 正文按纯文本显示（`**粗体**` 会原样出现）。M2 按“已完成消息缓存解析 + 流式轻量渲染”实现。
3. **图标**：正式图标集（Material Icons / 自定义）留 M4；当前用 `▤`/`⚙` 字形占位，避免引入大依赖。
4. **blocked 视觉未经真实样本验证**：当前 herdr 会话里没有 blocked agent（pi 默认不产生确认），blocked 卡片只做了代码路径，未获真实截图 → 见 `VISUAL-QA.md`。
5. **草稿持久化**：当前只在进程内保持（导航切换不丢），未落盘（M2）。
