package dev.herdr.core.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * 一条已认证的 SSH 连接。
 *
 * - 所有请求复用同一条连接（D15：实测不复用 = 1181 ms/请求）；
 * - exec 通道并发上限 4（实测通道上限 9）；
 * - 每个请求一个 exec channel，因为 herdr `remote-api-bridge` 一个连接只处理一个请求。
 */
class SshConnection(
    private val ssh: SSHClient,
    val generation: Long,
    private val maxConcurrentExec: Int = 4,
) : Closeable {

    private val permits = Semaphore(maxConcurrentExec)
    @Volatile private var closed = false

    val isAlive: Boolean get() = !closed && ssh.isConnected && ssh.isAuthenticated

    /** 一次 exec 的原始结果。 */
    data class ExecResult(val line: String, val elapsedMs: Long)

    suspend fun execOnce(
        command: String,
        stdinLine: String? = null,
        timeoutMs: Long = 15_000,
    ): ExecResult = withContext(Dispatchers.IO) {
        if (closed) throw SshFailure.Network("connection closed")
        permits.withPermit {
            val t0 = System.nanoTime()
            val session = try {
                ssh.startSession()
            } catch (e: Exception) {
                throw SshFailure.Network("open channel failed: ${e.message}", e)
            }
            try {
                withTimeout(timeoutMs) {
                    val cmd = session.exec(command)
                    if (stdinLine != null) {
                        cmd.outputStream.use { it.write((stdinLine + "\n").toByteArray()); it.flush() }
                    }
                    val line = cmd.inputStream.bufferedReader().readLine() ?: ""
                    cmd.join(5, TimeUnit.SECONDS)
                    runCatching { IOUtils.readFully(cmd.errorStream).close() }
                    ExecResult(line, (System.nanoTime() - t0) / 1_000_000)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw SshFailure.Timeout("exec timeout after ${timeoutMs}ms")
            } finally {
                runCatching { session.close() }
            }
        }
    }

    /** 打开一个 SFTP 通道（只读用途）。调用方负责 close。 */
    suspend fun openSftp(): net.schmizz.sshj.sftp.SFTPClient = withContext(Dispatchers.IO) {
        if (closed) throw SshFailure.Network("connection closed")
        try {
            ssh.newSFTPClient()
        } catch (e: Exception) {
            throw SshFailure.Network("open sftp failed: ${e.message}", e)
        }
    }

    /**
     * 打开一个长连接 exec（用于订阅）。调用方负责 close。
     * 不走 permits：订阅是长连接，单独占 1 个通道（D15 的预算里已留 1 个）。
     */
    suspend fun openStream(command: String, stdinLine: String): StreamHandle = withContext(Dispatchers.IO) {
        if (closed) throw SshFailure.Network("connection closed")
        val session = ssh.startSession()
        val cmd = session.exec(command)
        cmd.outputStream.use { it.write((stdinLine + "\n").toByteArray()); it.flush() }
        StreamHandle(session, cmd)
    }

    class StreamHandle(
        private val session: Session,
        private val cmd: Session.Command,
    ) : Closeable {
        val lines: Sequence<String> get() = generateSequence { cmd.inputStream.bufferedReader().readLine() }
        fun readLine(): String? = runCatching { cmd.inputStream.bufferedReader().readLine() }.getOrNull()
        override fun close() {
            runCatching { cmd.close() }
            runCatching { session.close() }
        }
    }

    override fun close() {
        closed = true
        runCatching { ssh.disconnect() }
        runCatching { ssh.close() }
    }
}
