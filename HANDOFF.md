# HANDOFF — 换到 Linux 机器继续

本仓库当前状态：Phase 0 的**文档修订**与**离线验证**已完成，在线部分只做了只读检查，
`test` 会话尚未创建，sshj 认证仍失败。全部结论见 `RESULTS.md`。

## 1. 拉代码后先做什么

```bash
git clone <this-repo> herdr-gui && cd herdr-gui

# JDK 17（Kotlin toolchain 会找 17）
java -version   # 需要 17+

# 复用仓库自带的 Gradle Wrapper（9.4.1）
cd spike/jvm
./gradlew test            # 期望 22 PASSED / 0 FAILED
./gradlew installDist     # 生成 build/install/herdr-spike-jvm/bin/herdr-spike-jvm
```

首次构建需要联网拉依赖（Kotlin 插件、sshj 0.41.2、JUnit 5.11.4）。
`--offline` 会失败（插件解析不到），这是已知行为，见 `ISSUES.md` I-5。

## 2. 环境变量

```bash
cp archive/spike/.env.example archive/spike/.env && ${EDITOR} archive/spike/.env
```

`.env` 已被 git 忽略。里面的主机/密钥/指纹按你的新环境改。
注意 Windows 上用的路径（`C:/Users/luxia/...`）在 Linux 上要换成 `~/.ssh/...`。

## 3. 已导出的证据（不需要重新生成）

| 文件 | 说明 |
| --- | --- |
| `docs/evidence/herdr-api.remote-0.9.0.schema.json` | 目标主机 herdr 0.9.0 的 schema |
| `docs/evidence/herdr-api.schema.json` | 本机 herdr 0.9.3 的 schema |
| `docs/evidence/environment-report.md` | 只读环境报告 |
| `docs/evidence/s1-*.{csv,txt,log}` | 延迟、指纹用例、sshj 失败日志 |

`archive/spike/jvm/known_hosts.spike`（本地信任库）**未入库**，属于运行期状态；换机器要重新走一次
「未知主机 → 显示指纹 → 带外核对 → 显式确认」。

## 4. 三条待批准的动作（在 `NEXT-STEPS.md` §1 有完整命令与回滚）

| 编号 | 内容 | 为什么必须先做 |
| --- | --- | --- |
| A-6 | 创建 herdr `test` 会话（`herdr --session test server`） | 没有 socket，`ping`/`snapshot`/`subscribe` 全部做不了 |
| A-7 | 生成专用 Ed25519 密钥并追加一行到 `authorized_keys` | 复测 sshj；产品本来就只用 Ed25519 |
| A-8 | 建 `~/spike-sandbox` 与隔离的 `~/.claude-spike` | Spike 3/4 前置 |

## 5. 接下来最该做的三件事（按顺序）

1. **修 sshj（阻塞项）**：用 Ed25519 复测 `--mode connect`。
   - 如果通过 → 继续 `--mode channels` 测单连接多 channel / MaxSessions。
   - 如果仍失败 → 用 Apache MINA SSHD 或 JSch 做一次最小对照，再决定是否换库。
   - 记录方式：追加到 `RESULTS.md` 的 ON-12，并更新 `DECISIONS.md` D13。
2. **创建 `test` 会话后跑 Spike 2A（只读）**：`ping` → `snapshot` → 双层 `events.subscribe`
   → 与 CLI snapshot 逐项比对。注意 0.9.0 没有 `remote-api-bridge`，用
   `socat - UNIX-CONNECT:<sock>` 或 `ssh -L`（见 `DECISIONS.md` D11）。
3. **Spike 3**：只用 `CLAUDE_CONFIG_DIR=~/.claude-spike`，不改 `~/.claude`。

## 6. 不要在别处重复的坑

- herdr 0.9.0 **没有** `remote-api-bridge`；本机（Windows 0.9.3）有。判断能力前先看版本。
- `ping.capabilities` 只是特性开关，**不是方法清单**。
- `pane.agent_status_changed` **必须**带 `pane_id`；全局事件**不能**带。
- sshj 的 `getEncoded()` 是 X.509，`SecurityUtils.getFingerprint()` 是 MD5；指纹要自己按
  SSH wire blob 算 SHA256，才对得上 `ssh-keygen -lf`。
- 该主机非交互 shell 的 PATH 不含 `~/.local/bin`，调用 herdr 一律用绝对路径。
- 写操作没有任何幂等键：超时只能标 `Unknown`，**永不自动重试**。
