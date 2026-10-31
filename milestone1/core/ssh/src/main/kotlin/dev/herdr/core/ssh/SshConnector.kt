package dev.herdr.core.ssh

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

    private fun loadKey(ssh: SSHClient, file: File): List<KeyProvider> {
        if (!file.exists()) throw SshFailure.KeyUnusable("key not found: ${file.name}")
        val v1 = runCatching<KeyProvider> { OpenSSHKeyV1KeyFile().apply { init(file) } }.getOrNull()
        if (v1 != null) return listOf(v1)
        return runCatching { listOf(ssh.loadKeys(file.absolutePath)) }
            .getOrElse { throw SshFailure.KeyUnusable("unsupported key format: ${it.message}", it) }
    }
}
