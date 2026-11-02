package io.github.luxiang0412.kelpie.core.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 建立并认证 SSH 连接。
 *
 * 指纹流程（D1）：先 KEX 拿 host key → 未知时回调让 UI 展示并由用户确认 → 只有确认后才 auth。
 * 绝不写系统 known_hosts，也不读系统 ssh config。
 */
class SshConnector(
    private val trustFile: File,
    private val generationSource: AtomicLong = AtomicLong(0),
) {

    val trustStore: TrustStore get() = TrustStore(trustFile)

    suspend fun connect(
        config: SshConfig,
        onUnknownHostKey: (HostKeyUnknownException) -> Boolean,
    ): SshConnection = withContext(Dispatchers.IO) {
        SshCrypto.ensureInitialized()
        val trust = StrictHostKeyTrust(TrustStore(trustFile))
        val ssh = SSHClient(SshCrypto.defaultConfig())
        try {
            ssh.addHostKeyVerifier(SshCrypto.Verifier(trust) { ex -> onUnknownHostKey(ex) })
            ssh.connectTimeout = 10_000
            ssh.timeout = 20_000
            ssh.connection.keepAlive.keepAliveInterval = 15

            try {
                ssh.connect(config.host, config.port)
            } catch (e: Exception) {
                throw mapConnectFailure(e)
            }
            try {
                ssh.authPublickey(config.user, loadKey(ssh, config.keyFile))
            } catch (e: Exception) {
                throw SshFailure.Auth("auth failed: ${e.message}", e)
            }
            if (!ssh.isAuthenticated) throw SshFailure.Auth("not authenticated")
            SshConnection(ssh, generationSource.incrementAndGet())
        } catch (e: Throwable) {
            runCatching { ssh.close() }
            throw e
        }
    }

    private fun mapConnectFailure(e: Exception): Throwable {
        var c: Throwable? = e
        var guard = 0
        while (c != null && guard++ < 10) {
            when (c) {
                is HostKeyUnknownException -> return SshFailure.UnknownHostKey(c)
                is HostKeyMismatchException -> return SshFailure.HostKeyChanged(c)
            }
            c = c.cause
        }
        return SshFailure.Network("connect failed: ${e.message}", e)
    }

    /**
     * 按**文件内容**选择 key provider（不能只试一种）：
     *  - `BEGIN OPENSSH PRIVATE KEY` → OpenSSH v1（ed25519 / rsa-openssh）
     *  - 其它 PEM（`BEGIN RSA PRIVATE KEY` / `BEGIN PRIVATE KEY`）→ PKCS8KeyFile
     * 都失败才报 KeyUnusable。返回全部成功的 provider，由 sshj 依次尝试。
     */
    private fun loadKey(ssh: SSHClient, file: File): List<KeyProvider> {
        if (!file.exists()) throw SshFailure.KeyUnusable("key not found: ${file.name}")
        val text = runCatching { file.readText() }.getOrDefault("")
        val isOpenSshV1 = text.contains("BEGIN OPENSSH PRIVATE KEY")
        val candidates = ArrayList<KeyProvider>(2)
        if (isOpenSshV1) {
            runCatching { OpenSSHKeyV1KeyFile().apply { init(file) } }.getOrNull()?.let { candidates += it }
        } else {
            runCatching {
                net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile().apply { init(file) }
            }.getOrNull()?.let { candidates += it }
            // 兼容 OpenSSH 库对老 PEM 的解析（个别机型/格式差异）
            runCatching { OpenSSHKeyV1KeyFile().apply { init(file) } }.getOrNull()?.let { candidates += it }
        }
        if (candidates.isNotEmpty()) return candidates
        return runCatching { listOf(ssh.loadKeys(file.absolutePath)) }
            .getOrElse { throw SshFailure.KeyUnusable("unsupported key format: ${it.message}", it) }
    }
}
