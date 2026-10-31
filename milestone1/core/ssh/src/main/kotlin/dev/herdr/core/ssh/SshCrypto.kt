package dev.herdr.core.ssh

import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PublicKey
import java.security.Security

/**
 * Android 上的 sshj 初始化（DECISIONS D27）。
 *
 * Android 自带一个裁剪版 BouncyCastle（provider 名 `BC`），没有 X25519 / SHA-256，
 * 而 sshj 的 SecurityUtils 只要发现 `BC` 就会用它 → 直接连不上。
 *
 * 这里**不删除**系统的 `BC`（避免影响同进程其它 TLS 代码），而是把 APK 内的新版 BC
 * 注册成另一个名字（`BCAPP`），再让 sshj 指向它。见 Spike A 的对比结果。
 */
object SshCrypto {

    const val APP_PROVIDER = "BCAPP"

    @Volatile private var initialized = false

    fun ensureInitialized(): List<String> {
        synchronized(this) {
            if (initialized) return lastReport
            val report = mutableListOf<String>()
            val hadBc = Security.getProvider("BC") != null
            report += "system_bc_present=$hadBc"

            if (Security.getProvider(APP_PROVIDER) == null) {
                val added = runCatching { Security.addProvider(AppBouncyCastleProvider()) }.getOrNull()
                report += "app_bc_added=${added != null}"
            }
            val registered = runCatching {
                SecurityUtils.setRegisterBouncyCastle(true)
                SecurityUtils.registerSecurityProvider(APP_PROVIDER)
            }.getOrElse { false }
            report += "sshj_provider=${runCatching { SecurityUtils.getSecurityProvider() }.getOrNull()}"
            report += "registered=$registered"

            val cfg = defaultConfig()
            report += "kex=" + cfg.keyExchangeFactories.map { it.name }
            lastReport = report
            initialized = true
            return report
        }
    }

    private var lastReport: List<String> = emptyList()

    /**
     * APK 内置 BC 的**代理**：换一个 provider 名（BCAPP），避免删除系统 BC。
     * BouncyCastleProvider 是 final，无法继承，所以用委托。
     */
    class AppBouncyCastleProvider(
        private val delegate: java.security.Provider = BouncyCastleProvider(),
    ) : java.security.Provider(APP_PROVIDER, 1.0, "delegates to app-provided BouncyCastle") {
        override fun getService(type: String, algorithm: String): java.security.Provider.Service? =
            delegate.getService(type, algorithm)

        override fun getServices(): MutableSet<java.security.Provider.Service> = delegate.services
    }

    /**
     * KEX 白名单：只用 JCE/BC 都能稳定跑的组合（D27）。
     * 若 `BCAPP` 生效，curve25519 也可用。
     */
    fun defaultConfig(): DefaultConfig {
        val cfg = DefaultConfig()
        val keep = listOf(
            "curve25519-sha256",
            "curve25519-sha256@libssh.org",
            "ecdh-sha2-nistp256",
            "ecdh-sha2-nistp384",
            "ecdh-sha2-nistp521",
            "diffie-hellman-group14-sha256",
            "diffie-hellman-group16-sha512",
            "diffie-hellman-group-exchange-sha256",
        )
        val filtered = cfg.keyExchangeFactories.filter { it.name in keep }
        if (filtered.isNotEmpty()) cfg.keyExchangeFactories = filtered
        return cfg
    }

    /** 主机 key 校验适配（指纹按 SSH wire blob 算，见 D12）。 */
    class Verifier(
        private val trust: StrictHostKeyTrust,
        private val onUnknown: (HostKeyUnknownException) -> Boolean,
    ) : HostKeyVerifier {
        override fun verify(host: String, port: Int, key: PublicKey): Boolean {
            val blob = runCatching {
                net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key).compactData
            }.getOrElse { key.encoded ?: return false }
            val alg = runCatching { SshKeyBlob.algorithmName(blob) }
                .getOrElse { runCatching { KeyType.fromKey(key).toString() }.getOrDefault("unknown") }
            return trust.verify(HostKeyId(host, port, alg), blob, onUnknown)
        }

        override fun findExistingAlgorithms(host: String?, port: Int): List<String> = listOf(
            "ssh-ed25519", "ecdsa-sha2-nistp256", "rsa-sha2-512", "rsa-sha2-256",
        )
    }
}
