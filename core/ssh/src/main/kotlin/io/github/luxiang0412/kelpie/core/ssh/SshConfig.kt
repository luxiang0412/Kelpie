package io.github.luxiang0412.kelpie.core.ssh

import java.io.File

/** 一台主机的连接配置。`session` 是 herdr 命名会话。 */
data class SshConfig(
    val hostId: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val herdrSession: String,
    /** herdr 二进制的绝对路径（非交互 shell 的 PATH 常常不含它，见 I-14）。 */
    val herdrBin: String = "herdr",
    /** 私钥文件（OpenSSH 格式）。 */
    val keyFile: File,
)

sealed class SshFailure(msg: String, cause: Throwable? = null) : Exception(msg, cause) {
    class UnknownHostKey(val error: HostKeyUnknownException) :
        SshFailure(error.message ?: "unknown host key", error)
    class HostKeyChanged(val error: HostKeyMismatchException) :
        SshFailure(error.message ?: "host key changed", error)
    class Auth(msg: String, cause: Throwable? = null) : SshFailure(msg, cause)
    class Network(msg: String, cause: Throwable? = null) : SshFailure(msg, cause)
    class Timeout(msg: String) : SshFailure(msg)
    class Protocol(msg: String, cause: Throwable? = null) : SshFailure(msg, cause)
    class KeyUnusable(msg: String, cause: Throwable? = null) : SshFailure(msg, cause)
}
