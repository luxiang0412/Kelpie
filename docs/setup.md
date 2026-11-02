# 安装与配置

Kelpie **没有自己的服务端**：手机通过 SSH 直接连开发机，调用 herdr 自带的接口（`herdr remote-api-bridge`），再用 SFTP 读 agent 的对话记录。
开发机上要准备的，就是让 SSH、herdr、agent 这三样就位。

## 开发机

### 1. SSH

- **手机要能连到这台机器的 SSH 端口**。同一个局域网可以；不在一个网络时，用 Tailscale、ZeroTier 这类组网工具。二维码里放的就是这个地址。
- **用公钥登录**：配对脚本会生成一把手机专用的密钥，并问你要不要加进 `~/.ssh/authorized_keys`。
- **SFTP 要能用**：读对话记录和审批请求都走 SFTP。大多数发行版的 sshd 默认开着，可以用 `sftp user@host` 试一下。
- 手机第一次连接时要核对主机指纹，不会自动信任。扫码添加的主机，指纹已经包含在二维码里。

### 2. herdr

- **版本**：App 要求 herdr 的接口协议版本是 **22**（herdr 0.9.5）。版本不一致时，App 会显示“协议版本不匹配”并拒绝连接。herdr 升级后协议号变了，App 也要跟着更新。
- **路径**：默认找 `~/.local/bin/herdr`。装在别处的话，配对脚本会用 `which herdr` 找到的路径（也可以用 `--herdr-bin` 指定），或者在 App 的主机设置里改。
- **会话要一直开着**：在开发机上运行一次 `herdr`（默认会话），或者 `herdr --session <名称>`。之后断开终端，herdr 服务仍在后台运行。
  App 连的是这个会话：配对脚本用 `--session` 指定，默认取环境变量 `HERDR_SESSION`，没有就是 `default`。
- **agent 要在 herdr 的 pane 里启动**。在普通终端里跑的 agent，herdr 和 App 都看不到。

### 3. herdr 的 agent 集成

```bash
herdr integration install claude
herdr integration install codex
herdr integration install pi
herdr integration status        # 确认已安装
```

集成负责向 herdr 上报 agent 的状态（工作中、等你、完成）和会话 id。没有集成，App 只能看到终端画面，分不清 agent 的状态，也找不到对话记录。

- **Codex**：装完集成后第一次启动 Codex，会提示新的 hook 需要确认（Hooks need review），要选信任。在确认之前就已经在跑的 Codex 没有会话 id，App 只能按工作目录去猜它的对话记录；同一个目录下开着两个 Codex 时不猜，只显示终端。
- **对话记录的位置**：App 只在默认目录里找：Claude Code 在 `~/.claude/projects/`，Codex 在 `~/.codex/sessions/`，Pi 在 `~/.pi/`。用 `CLAUDE_CONFIG_DIR` 或 `CODEX_HOME` 改过目录的 agent，App 找不到它的对话记录，只显示终端。

### 4. 配对脚本的依赖

`companion/pair/kelpie_pair.py` 需要 Python 3 和 `ssh-keygen`。用法见 [`companion/README.md`](../companion/README.md)。

### 5. 手机审批（可选）

要在手机上批准 agent 运行命令，还需要给 Claude Code 或 Codex 装审批 hook（`companion/hooks/kelpie_approve.py`）。
请求目录两边默认都是 `~/.local/state/kelpie/approvals`；改了的话，在手机的主机设置 → 手机审批里填同一个目录。
目前**没有安装脚本**，只在隔离配置里验证过；配置方法和启用前要决定的事见 [`approvals.md`](approvals.md)。不装也不影响其他功能。

### 自查清单

在开发机上依次运行，都正常就可以去手机上扫码了：

```bash
herdr --version                      # 0.9.5（协议 22）
herdr status server                  # herdr 服务在运行
herdr --session <名称> pane list     # 能看到你的 agent
herdr integration status             # claude / codex / pi 已安装
python3 --version && which ssh-keygen
```

## 手机

1. **安装**：Android 8.0 及以上。装 `dist/kelpie-<版本>.apk`。从别处下载的安装包，系统会要求允许“安装未知应用”。
2. **添加主机**：设置 → 添加主机 → 扫描二维码（也可以从相册里的截图识别，或者用系统相机扫）→ 在确认页点按钮。
   不想扫码的话，可以手动填地址、用户、会话名，再粘贴私钥；这种方式第一次连接时要人工核对指纹。
3. **通知**：第一次打开时允许通知。App 在后台时靠一条常驻的低优先级通知维持连接，agent 在等你或完成时会提醒你。
   - 实测 MIUI 上 App 在后台时能收到完成提醒。如果锁屏很久以后提醒变慢或收不到，可以在系统设置里把 Kelpie 的省电策略改成“无限制”，并允许自启动。这一条还没有系统地测过。
   - 没有推送服务：App 被系统彻底杀掉以后就收不到提醒，直到下次打开。原因见 [`push-options.md`](push-options.md)。

## 常见问题

| 现象 | 原因 |
| --- | --- |
| 协议版本不匹配 | 开发机上 herdr 的版本和 App 支持的不一致，见上面“herdr · 版本” |
| 连不上，提示网络错误 | 手机到开发机的网络不通（不在同一网络、VPN 没开、端口不对） |
| 指纹不一致，拒绝连接 | 开发机的 host key 变了（比如重装了系统）。确认是你自己的改动以后，删掉这台主机重新添加 |
| 看得到 agent，但对话页只显示终端 | 没装 herdr 集成、Codex 的 hook 没有确认信任，或者 agent 用了自定义配置目录（见上面第 3 节） |
| 首页没有这台机器的 agent | agent 不是在 herdr 的 pane 里启动的，或者 App 连的会话名不对 |
