# 后台提醒：FCM / ntfy / UnifiedPush 对比

日期：2026-11-03。只是文档，没有接入任何服务。依据是我已有的知识，没有联网核对各服务当天的价格和条款。

## 现状

Signal 0.2.0 靠前台服务常驻 SSH 连接，每 10 秒对账，状态变化时本地发通知。

- 优点：零外部依赖，数据不出"手机 ↔ 你的主机"。
- 缺点：
  - 耗电。
  - MIUI 等系统需要用户手动放开后台限制。
  - 手机休眠（Doze）时网络被推迟，提醒可能晚几分钟。
  - 用户划掉前台通知后就没有提醒了。

要在手机不常驻连接的情况下也能及时提醒，需要"主机 → 推送服务 → 手机"这条通路。主机侧的触发点已经有了：herdr 的状态事件，或审批 hook 写请求的那一刻。

## 三个选项

| | FCM（Firebase Cloud Messaging） | ntfy（自建或 ntfy.sh） | UnifiedPush |
|---|---|---|---|
| 是什么 | Google 的推送通道，Android 系统级 | 一个 HTTP 发布 / 订阅服务，有自己的 Android App | 一个开放协议：App 不绑定具体推送商，由用户装的"分发器"决定走哪条通道（ntfy、NextPush、FCM 中转等） |
| 唤醒能力（Doze 下） | 最好：高优先级消息能直接唤醒 App | 走 ntfy App 自己的长连接或 FCM 中转。ntfy.sh 官方实例的 Google Play 版本用 FCM 唤醒，自建实例要靠 ntfy App 常驻 | 取决于分发器；用 ntfy 作分发器时和上一列相同 |
| 国内 / 无 GMS 手机 | **不可用或不稳定**（MIUI 国行一般没有 GMS，连接 Google 也受限） | 自建实例可用（ntfy App 常驻长连接，同样要放开后台限制） | 同 ntfy；协议本身不依赖 Google |
| 数据经过谁 | Google | 你自己（自建）或 ntfy.sh | 你选的分发器 / 服务器 |
| 主机侧需要什么 | 服务账号凭据（FCM HTTP v1），主机要能访问 Google | 一行 `curl -d "..." https://<ntfy>/<topic>`；topic 名就是共享密钥，也可以加 token 鉴权 | 主机向 App 注册时拿到的 endpoint 发 HTTP POST（WebPush 格式，可端到端加密） |
| App 侧工作量 | 加 Firebase SDK + google-services 配置，引入 GMS 依赖 | 最少：只要求用户装 ntfy 并订阅一个 topic；Signal 自己可以完全不改，点通知用 deep link 打开 Signal | 加 UnifiedPush connector 库（小），实现注册 / 解注册回调，把 endpoint 交给主机 |
| 隐私 | 内容经过 Google；可以只推"有新状态"不带内容 | 自建则完全自己掌控；ntfy.sh 上 topic 公开可猜就会泄露 | 可以端到端加密（WebPush） |

## 建议

**先做 ntfy，作为可选项，不推内容只推"去看一眼"。路线是 UnifiedPush。** 不做 FCM。

理由：

1. 你的手机是 MIUI，FCM 在国内环境不可靠，而且会给 App 引入 GMS 依赖。
2. ntfy 主机侧只需要一条 curl，可以直接放进审批 hook 和一个监听 herdr 事件的小脚本，Signal 本身不用改就能先用起来。
3. UnifiedPush 是 ntfy 的超集：以后在 Signal 里接上 UnifiedPush connector，用户可以选 ntfy、NextPush 或任何分发器，主机侧改成往 endpoint POST（还能加密）。
4. 通知里不放命令和对话内容，只写"MS-7E07 上 Claude Code 在等你批准"。点开后 Signal 通过 SSH 拉真实数据，所以推送通道不可信也无所谓（最多泄露"有事发生"）。

## 落地步骤（需要你决定后再做）

1. 决定用自建 ntfy（放在哪台机器、要不要公网可达）还是 ntfy.sh。
2. 主机侧：`signal_approve.py` 写完请求后 `curl` 一次。另起一个 `herdr events` 监听，blocked / done 时 `curl`。都放在隔离配置里试。
3. 手机侧：先用 ntfy App 订阅验证延迟和 MIUI 下的可靠性。确认可行后，在 Signal 里接 UnifiedPush，前台服务改成可关。
