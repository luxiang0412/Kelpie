# companion/ —— 装在开发机上的脚本

都只依赖 Python 3 标准库，复制到开发机上就能用。

## pair/kelpie_pair.py：扫码添加主机

在**要添加的那台开发机**上运行（还需要 `ssh-keygen`）：

```bash
python3 companion/pair/kelpie_pair.py --session test --host <手机能连到的地址>
```

脚本会：
1. 生成一把这台手机专用的 ed25519 密钥，注释是 `kelpie-<名称>-<日期>`；
2. 问你是否把它加入 `~/.ssh/authorized_keys`；
3. 在终端画出二维码，加 `--png` 可以另存成图片。

二维码里有地址、本机全部 host key 指纹和私钥，10 分钟后手机不再接受。

App 里有三种方式：设置 → 添加主机 → 扫描二维码，或者从图片识别；用系统相机扫到也会打开 App。**所有方式都要在确认页按下按钮才会写入**。

- 指纹来自开发机自己显示的二维码，视为已经核对过，直接固定；和已保存的指纹不一致时拒绝，不会覆盖。
- 同一地址、用户、会话的主机已经存在时，会更新它，不会新增一条。
- 多网卡或开着 VPN 的机器，务必用 `--host` 指定地址（脚本会列出候选）。
- `--list` 列出发过的手机密钥，`--revoke <名称>` 撤销（会先备份 `authorized_keys`）。

## hooks/kelpie_approve.py：手机审批

Claude Code 和 Codex 用的审批 hook：agent 要运行命令时，请求出现在手机首页，按住允许或拒绝。

- Claude Code：PreToolUse hook（`settings.json`），默认只拦 `Bash`（`KELPIE_APPROVAL_TOOLS`）。
- Codex：PermissionRequest hook（`$CODEX_HOME/hooks.json`），只在 Codex 本来就要问人时触发。新的 hook 要在 Codex 启动时确认信任。
- 没有回答（默认 120 秒）、回答校验失败、hook 出错，都退回 agent 自己的终端确认，绝不放行。
- 请求目录用 `KELPIE_APPROVALS_DIR` 指定，默认 `~/spike-sandbox/approvals`，这是实验时的约定，App 里也是读这个目录。

目前只在隔离配置里验证过，**还没有安装脚本**。配置示例、实验结果、启用前要决定的事见 [`docs/approvals.md`](../docs/approvals.md)。
