# Spike 工作区

配套文档：`../herdr-android-client-research-v2.md`（v2.1）、`../herdr-android-spike-plan.md`。
本轮执行记录：`../RESULTS.md`、`../DECISIONS.md`、`../ISSUES.md`、`../NEXT-STEPS.md`。

## 目录

```text
spike/
  .env.example          # 环境变量模板（.env 不提交）
  jvm/                  # Kotlin/JVM 验证工程（已可编译、22 个单测全绿）
    src/main/kotlin/herdrspike/trust/   主机指纹显式信任（fail-closed）
    src/main/kotlin/herdrspike/write/   写操作三态账本（Unknown 不自动重试）
    src/main/kotlin/herdrspike/tail/    SFTP Tailer 契约实现 + 本地 ByteSource
    src/main/kotlin/herdrspike/Ndjson.kt herdr socket API 编解码
  host/                 # 主机端脚本（待：会话 hook、假 TUI 竞态脚本、时间戳脚本）
  samples/              # 脱敏后的 JSONL 样本（待）
  results/              # 原始证据：schema 导出、单测输出、环境记录、CSV
```

## 运行（离线部分，无需任何远端主机）

```bash
cd spike/jvm
export JAVA_HOME=/path/to/jdk17
# 注意：第一次必须联网解析 Kotlin 插件与 sshj 依赖（--offline 会失败）
gradle test          # 或本机 gradle 发行版路径
```

当前结果：22/22 通过，见 `results/s1-local-unit-tests.txt`。

## 在线探针（对真实主机，只读）

```bash
cd spike/jvm && gradle installDist
BIN=build/install/herdr-spike-jvm/bin/herdr-spike-jvm
# F1/F2/F3/F4 主机指纹（真机，fail-closed）
$BIN --mode hostkey --host 192.168.5.8 --user luxiang --key 'C:/Users/luxia/.ssh/id_rsa'
# 冷连接/认证耗时
$BIN --mode connect --host 192.168.5.8 --user luxiang --key 'C:/Users/luxia/.ssh/id_rsa' --runs 10
# 单连接多 channel（依赖 sshj 认证可用）
$BIN --mode channels --host ... --user ... --key ...
# 通过 socat 读 herdr.sock（0.9.0 没有 remote-api-bridge）
$BIN --mode bridge --host ... --user ... --key ... --sock /home/luxiang/.config/herdr/sessions/test/herdr.sock --method ping
```

结果见 `results/`。

## 在线部分（需要批准后才能做）

Spike 1/2 的在线测量需要一个**用户批准的隔离主机**（herdr ≥ 0.9.5 更好）和一个**Android 真机**。
所需变更与阻断项见 `../NEXT-STEPS.md` 的“等待批准”一节。未获批准前，所有在线项在 `../RESULTS.md` 里都是 `尚未验证`。
