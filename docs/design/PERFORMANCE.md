# PERFORMANCE — 基线（M0.3 部分完成）

> 设备：Redmi 22021211RC / Android 14 / 1080×2400 / **120 Hz**。
> 构建：**release**（`isMinifyEnabled = false`，debug 签名，`<profileable android:shell="true"/>`，已真机安装运行）。

## 1. 冷启动（`am start -W`，`TotalTime`）

| 次数 | 1（首次 dexopt） | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ms | 960 | 394 | 363 | 456 | 358 | 397 | 365 | 379 |

- **中位 ≈ 379 ms**（排除首次 dexopt）；对比改动前 debug 中位 **555 ms**。
- 原始记录：`docs/evidence/m1-coldstart-release.txt`。
- 测量方式：`adb shell am force-stop` → 间隔 1 s → `am start -W`，共 8 次，无并行任务。

## 2. 滚动帧统计（`dumpsys gfxinfo`，固定脚本）

脚本（可复现）：`reset gfxinfo` → 8 次 `input swipe 540 1600 540 900 200`，间隔 150 ms → 读取统计。

| 场景 | 总帧 | Janky | 50th | 90th | 95th | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| 看板（2 个 agent） | **0** | — | — | — | — | ❌ 无效样本：列表不足一屏，未产生滚动帧（记录为不可用，不作为“性能良好”的证据） |
| 对话（约 10 条消息） | 483 | **3（0.62%）** | 6 ms | 7 ms | 8 ms | 短会话基线（**不代表 500 条长会话**） |

原始记录：`docs/evidence/m1-jank-board-release.txt`、`m1-jank-conversation-release.txt`。

## 3. 未完成项（诚实记录）

| 要求 | 状态 | 原因 / 下一步 |
| --- | --- | --- |
| 500 条消息滚动基线 | ⛔ **未完成** | 需要可控的 500 条真实会话（当前 herdr 会话里没有）。计划：在沙箱 pane 里生成 500 条 pi JSONL 后测量，或用 `:benchmark` 模块的 Macrobenchmark（`FrameTimingMetric`） |
| 输入法弹出/收起帧 | ⛔ 未完成 | 同上，需固定场景脚本 + FrameTimingMetric |
| 消息增量更新帧 | ⛔ 未完成 | 需要可控流式数据源 |
| Macrobenchmark / Perfetto | ⛔ 未完成 | 建立 `:benchmark` 模块（`com.android.test` + `benchmark-macro-junit4`）留到 M2 第一件事 |
| Jank 定义 | 说明 | 现在用的是 `gfxinfo` 的 “Janky frames”（基于 frame deadline），非 `FrameTimingMetric`；两者不可直接比较 |

## 4. 结论

- 冷启动在 release 下 **379 ms**，满足“< 600 ms”的目标区间；
- 短会话滚动 0.62% jank；
- **长会话基线仍未建立**，因此本轮**不宣称**长对话性能已改善（遵守“不使用未验证的性能数字”的约束）。
