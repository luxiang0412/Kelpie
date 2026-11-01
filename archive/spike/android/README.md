# Android 真机 Spike（Spike 1）

最小 App：`MainActivity` 一个界面 + 四个用例 + 延迟测量。

## 用途

在真机上验证：
1. sshj + Ed25519 能否在 Android 上完成 SSH 认证；
2. 主机指纹 **fail-closed**（F1 未知拒绝且不写盘 / F2 显式确认 / F3 不再询问 / F4 篡改拒绝）；
3. 一次真实的 herdr `ping`（经 `remote-api-bridge`）与 exec 往返。

结果同时打到 `Log.i("SPIKE", ...)` 和屏幕。

## 构建 & 运行

```bash
export JAVA_HOME=<jdk17>; export ANDROID_HOME=<sdk>
gradle :app:assembleDebug          # 或 ./gradlew（本目录未带 wrapper，用上层 spike/jvm 的也行）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 私钥：只放运行时目录，绝不入库
adb push ~/.ssh/id_ed25519_spike /data/local/tmp/id_ed25519_spike
adb shell run-as herdrspike.android mkdir -p files
adb shell run-as herdrspike.android cp /data/local/tmp/id_ed25519_spike files/id_ed25519_spike
adb shell run-as herdrspike.android chmod 600 files/id_ed25519_spike

adb logcat -c && adb shell am start -n herdrspike.android/.MainActivity
adb logcat -d -s SPIKE:*
```

## Android 上必须处理的两个坑（已解决，见 ISSUES I-48 / DECISIONS D27）

1. **BouncyCastle 冲突**：Android 自带的老 BC 没有 `X25519` / `SHA-256`。
   解决：`Security.removeProvider("BC")` → `addProvider(BouncyCastleProvider())`（APK 内 `bcprov-jdk18on`），
   再 `SecurityUtils.setRegisterBouncyCastle(true)` + `registerSecurityProvider("BC")`。
2. **KEX 白名单**：`DefaultConfig.keyExchangeFactories` 只保留 `curve25519-sha256` / `ecdh-sha2-*` / `dh-group14/16/gex`。

## 已知限制

- 直接复制了 `archive/spike/jvm/.../trust/` 的两个文件（SPIKE 专用，避免跨模块配置复杂度）。
- 私钥通过 `run-as` 放进 app 私有目录，仅用于本次验证。
