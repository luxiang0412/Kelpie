# FIX — 每次从会话返回都会重连（用户报告，已修复）

- 报告：「每次从会话返回都会重连？」
- 复现（真机 `192.168.1.8:39527`）：
  1. 进入对话，停留 8s：`connected generation` 0 次、`session failed` 0 次
  2. 点返回，停留 8s：`session failed kind=NETWORK msg=connection lost` **1 次** → `connected generation=2`（重连）
- 对照实验：把 `TranscriptTailer.close()` 里的 SFTP 关闭临时去掉 → **返回后 0 失败、0 重连**
- **根因**：`HostsViewModel.closeConversation()` → `TranscriptTailer.close()` → `SFTPClient.close()`。
  关闭 SFTP 通道后，服务端/sshj 把**整条共享 SSH 连接**判定为断开，supervisor 于是走重连流程。
  这是我在 M2/M3 引入的实现缺陷（SFTP 生命周期挂在了「对话页」而不是「连接」上）。
- 修复：
  1. `SshConnection` 新增 **`sharedSftp()`**：同一条连接复用同一个 SFTP；连接 `close()` 时才关闭通道；
     复用前用一次轻量 `stat(".")` 探活，失效则重建
  2. `TranscriptTailer` 改用 `conn.sharedSftp()`；`close()` **只释放引用，不关闭通道**
  3. `HostsViewModel.closeConversation()` 恢复正常调用 `tailer.close()`
- 验证（真机）：
  - 连续两次「进入 → 返回」：`session failed` **0**、`connected generation` **0**（不再重连）
  - 返回后再次进入对话，transcript 正常加载（共享 SFTP 复用有效）：`spike/results/reenter-conv.png`
  - AVD `emulator-5556` Compose UI：**OK (28 tests)**
- 影响面：只动 SFTP 生命周期，未改 SSH 认证/信任、Herdr 协议、写操作三态、草稿
- 四类结论：
  - **已实现并验证**：返回不再重连；再次进入仍能读 transcript；UI 回归通过
  - **已实现但未验证**：连接被服务端真正断开时 `sharedSftp()` 的探活重建（需要制造断链）
  - **未实现**：SFTP 通道数上限保护（若未来多个 tailer 并发只共用 1 个通道，长读可能排队）
  - **已知问题**：`sharedSftp()` 里 `synchronized` + `withContext(IO)` 组合在极端并发下会串行化 stat 探活（可接受，单连接场景）
