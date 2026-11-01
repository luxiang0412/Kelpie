package herdrspike

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import herdrspike.trust.Fingerprint
import java.security.Security

/**
 * Spike A（JVM 侧）：用 mwiede/JSch 做与 SSHJ 同样的动作，回答：
 *  - JSch 能否用 Ed25519 认证；
 *  - 是否需要动全局 BouncyCastle/Provider；
 *  - SFTP 是否可用；
 *  - 单次 exec/channel 延迟。
 *
 * 注意：这是 spike 代码，主机指纹用“连接后比对”而不是生产级的“认证前确认”。
 */
object JschProbe {

    fun run(host: String, port: Int, user: String, keyPath: String, expectedFp: String?, sftpPath: String?) {
        val providersBefore = Security.getProviders().map { it.name }
        val jsch = JSch()
        jsch.addIdentity(keyPath)
        val session: Session = jsch.getSession(user, host, port)
        session.setConfig("StrictHostKeyChecking", "no")   // spike：连接后再严格比对
        session.setConfig("PreferredAuthentications", "publickey")
        session.userInfo = object : UserInfo {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = null
            override fun promptPassword(message: String?): Boolean = false
            override fun promptPassphrase(message: String?): Boolean = false
            override fun promptYesNo(message: String?): Boolean = false
            override fun showMessage(message: String?) { }
        }
        val t0 = System.nanoTime()
        session.connect(15_000)
        val connectMs = (System.nanoTime() - t0) / 1_000_000.0

        val hostKey: HostKey? = session.hostKey
        // JSch 的 getKey() 是 base64 文本；解码后才是 SSH wire blob
        val blob: ByteArray? = hostKey?.key?.let { java.util.Base64.getDecoder().decode(it) }
        val fp = blob?.let { Fingerprint.sha256(it) }
        println("RESULT,jsch,connected=true,connect_ms=%.0f,hostkey_type=%s,fingerprint=%s".format(
            connectMs, hostKey?.type ?: "?", fp ?: "?"))
        if (expectedFp != null && fp != expectedFp) {
            println("RESULT,jsch,FINGERPRINT_MISMATCH,expected=$expectedFp,actual=$fp")
        }

        // exec：连续 10 次
        val times = ArrayList<Double>()
        repeat(10) {
            val s = System.nanoTime()
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand("true")
            ch.inputStream = null
            ch.connect(10_000)
            while (!ch.isClosed) Thread.sleep(2)
            ch.disconnect()
            times += (System.nanoTime() - s) / 1_000_000.0
        }
        times.sort()
        println("RESULT,jsch,exec,n=%d,min=%.0f,p50=%.0f,max=%.0f".format(
            times.size, times.first(), times[times.size / 2], times.last()))

        if (sftpPath != null) {
            try {
                val s = System.nanoTime()
                val ch = session.openChannel("sftp") as ChannelSftp
                ch.connect(10_000)
                val attrs = ch.stat(sftpPath)
                val readStart = System.nanoTime()
                val input = ch.get(sftpPath)
                val bytes = input.readBytes()
                input.close()
                println("RESULT,jsch,sftp,ok=true,size=%d,read_bytes=%d,stat_ms=%.0f,read_ms=%.0f".format(
                    attrs.size, bytes.size,
                    (readStart - s) / 1_000_000.0, (System.nanoTime() - readStart) / 1_000_000.0))
                ch.disconnect()
            } catch (e: Exception) {
                println("RESULT,jsch,sftp,ok=false,error=${e.javaClass.simpleName}:${e.message}")
            }
        }

        val providersAfter = Security.getProviders().map { it.name }
        println("RESULT,jsch,providers_before=${providersBefore},providers_after=${providersAfter}")
        session.disconnect()
    }
}
