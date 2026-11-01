# Milestone 1 — 只读看板（Android）

```text
android/
  app/            # Application / Activity / 手工 DI
  core/ssh/       # 指纹信任、sshj 初始化（BCAPP）、连接、通道限流
  core/herdr/     # NDJSON 编解码、HerdrApi、SessionReconciler、ConnectionSupervisor、分组
  core/data/      # Room 缓存 + SettingsStore（主机设置/私钥）
  feature/board/  # Compose：主机设置 / 看板 / Pane 详情
```

## 技术基线（照抄 DECISIONS，不重新发明）

- D1/D12 指纹：KEX → 展示 → 带外核对 → 显式确认；fail-closed；指纹按 SSH wire blob 算 SHA256
- D8 能力协商以 schema/版本为准；启动 `ping` 校验 protocol=22
- D9 两层订阅：全局生命周期 + 逐 pane 状态；`pane.created/closed` 后重订阅
- D15 单连接复用；exec 并发上限 4；订阅单独占 1 通道
- D17 不用 `pane.read.revision` 判断内容变化
- D29 事件驱动 + snapshot 批量刷新；不做逐 pane 轮询
- D30 `pane_id` 当不透明字符串（base36，如 `w4:pA`）
- 写操作：`WriteOutcome` 三态语义保留，但 M1 **不开放写 UI**

## 构建

```bash
export JAVA_HOME=<jdk17>; export ANDROID_HOME=<sdk>
gradle :app:assembleDebug          # 首次需联网
gradle :core:herdr:test            # 12 个 JVM 单测
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 注：AGP 9 内置 Kotlin，不能再 apply `org.jetbrains.kotlin.android`；
> KSP 需要 `android.disallowKotlinSourceSets=false`；Compose BOM 2026.09 要求 `compileSdk = 37`。

## 私钥导入

界面里「主机设置 → 导入私钥（粘贴）」粘贴 OpenSSH 私钥；只写入 `filesDir/keys/id_ed25519`（0600）。
自动化测试时可用 adb 预置：

```bash
adb push ~/.ssh/id_ed25519_spike /data/local/tmp/k
adb shell run-as dev.herdr.app mkdir -p files/keys
adb shell run-as dev.herdr.app cp /data/local/tmp/k files/keys/id_ed25519
```

## 现在能做什么（M4 末）

- 多主机看板（状态分组/计数），断线自动恢复；
- **Pane 详情**：字段 + 最近输出（`pane.read`，2 s 刷新）+ **发送消息**（`agent.prompt`，三态账本）；
- **对话页（只读）**：pi JSONL → 结构化消息，增量读取 + Room 缓存 + 2 s 自动刷新；底部也能发消息；
- **后台提醒**：`dataSync` 前台服务 + 本地通知（完成/需要处理），`completionMode` 用 `completion_seq` 判完成；
- **L1 审批客户端**：沙箱文件 IPC（`.req`/`.answer`，nonce + TTL + 一次性），看板弹出审批卡片。

## 依赖与前提

- 对话页需要 pane 上报 `agent_session`：在**跑 agent 的主机**上 `herdr integration install pi`，**然后重启 pi**（扩展不会热加载）；
- 本机 Windows 的 `herdr integration status` 显示“未安装”是正常的（不同 HOME）。

## 仍未做

- 真实审批触发（Claude `PermissionRequest` hook / pi 扩展）——需批准；
- 终端接管、Markdown 渲染、CodeMirror 高亮、多主机聚合、后台推送；
- 长对话性能（30–44% jank，待 Macrobenchmark 基线）；
- Room 不做迁移（`fallbackToDestructiveMigration`，开发期可接受）。
