package herdrspike.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import herdrspike.trust.Fingerprint
import herdrspike.trust.HostKeyId
import herdrspike.trust.HostKeyUnknownException
import herdrspike.trust.SshCrypto
import herdrspike.trust.SshKeyBlob
import herdrspike.trust.StrictHostKeyTrust
import herdrspike.trust.TrustStore
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.PublicKey
import java.security.Security

/**
 * Spike A（Android 侧）：SSHJ vs JSch；以及“能否避免全局 removeProvider('BC')”。
 *
 *  A1 SSHJ + 委托式 BCAPP（不删系统 BC）
 *  A2 SSHJ + 删除系统 BC 并注册新版 BC（对照，已知可行）
 *  A3 JSch + Ed25519 → 认证 + exec + SFTP
 */
class CryptoSpikeActivity : Activity() {

    companion object {
        private const val TAG = "CRYPTO"
        private const val HOST = "192.168.5.8"
        private const val PORT = 22
        private const val USER = "luxiang"
        private const val KEY = "id_ed25519_spike"
        private const val SFTP_PATH = "/home/luxiang/spike-sandbox/sftp-test.jsonl"
        private const val EXPECTED_FP = "SHA256:lfFYDnJNKAPT1jtyrA6bU89XDcKtHQiy7PPdiX0G0T8"
    }

    private lateinit var out: TextView
    private val sb = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        val rerun = Button(this).apply {
            text = "重新运行 Spike A"
            setOnClickListener { sb.setLength(0); out.text = ""; Thread { runAll() }.start() }
        }
        out = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        root.addView(rerun)
        root.addView(out, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        Thread { runAll() }.start()
    }

    private fun log(s: String) {
        Log.i(TAG, s)
        runOnUiThread { sb.append(s).append('\n'); out.text = sb.toString() }
    }

    private fun keyFile(): File? = File(filesDir, KEY).takeIf { it.exists() }

    private fun trust(): TrustStore = TrustStore(File(filesDir, "known_hosts.crypto"))

    private fun runAll() {
        val kf = keyFile()
        if (kf == null) { log("ABORT,no_key: push $KEY first"); return }
        log("ENV,android=${android.os.Build.VERSION.RELEASE}," +
            "providers_before=${Security.getProviders().map { it.name }}")

        runCatching { sshjProbe("A1-BCAPP-delegate", kf, keepSystemBc = true) }
            .onFailure { log("RESULT,A1,failed=${it.javaClass.simpleName}:${it.message}") }

        runCatching { sshjProbe("A2-remove-BC", kf, keepSystemBc = false) }
            .onFailure { log("RESULT,A2,failed=${it.javaClass.simpleName}:${it.message}") }

        runCatching { jschProbe(kf) }
            .onFailure { log("RESULT,A3,failed=${it.javaClass.simpleName}:${it.message}") }

        log("DONE")
    }

    private fun sshjProbe(tag: String, key: File, keepSystemBc: Boolean) {
        val before = Security.getProviders().map { it.name }
        if (!keepSystemBc) {
            Security.removeProvider("BC")
            if (Security.getProvider("BC") == null) {
                Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
            }
        }
        SshCrypto.ensureInitialized()

        val ssh = SSHClient(SshCrypto.defaultConfig())
        ssh.addHostKeyVerifier(Adapter(StrictHostKeyTrust(trust())))
        val t0 = System.nanoTime()
        ssh.connect(HOST, PORT)
        ssh.authPublickey(
            USER,
            listOf(com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile().apply { init(key) }),
        )
        val authMs = (System.nanoTime() - t0) / 1e6

        val times = ArrayList<Double>()
        repeat(5) {
            val s = System.nanoTime()
            ssh.startSession().use { sess ->
                val cmd = sess.exec("true")
                cmd.join(5, java.util.concurrent.TimeUnit.SECONDS)
                cmd.inputStream.close()
            }
            times += (System.nanoTime() - s) / 1e6
        }
        times.sort()
        log("RESULT,$tag,ok=true,auth_ms=${"%.0f".format(authMs)}," +
            "exec_p50=${"%.0f".format(times[times.size / 2])}," +
            "providers_after=${Security.getProviders().map { it.name }},before=$before")
        runCatching { ssh.close() }
    }

    private fun jschProbe(key: File) {
        val before = Security.getProviders().map { it.name }
        val jsch = JSch()
        jsch.addIdentity(key.absolutePath)
        val session: Session = jsch.getSession(USER, HOST, PORT)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey")

        val t0 = System.nanoTime()
        session.connect(15_000)
        val connectMs = (System.nanoTime() - t0) / 1e6

        val blob = java.util.Base64.getDecoder().decode(session.hostKey.key)
        val fp = Fingerprint.sha256(blob)

        val times = ArrayList<Double>()
        repeat(5) {
            val s = System.nanoTime()
            val ch = session.openChannel("exec") as ChannelExec
            ch.setCommand("true")
            ch.inputStream = null
            ch.connect(10_000)
            while (!ch.isClosed) Thread.sleep(2)
            ch.disconnect()
            times += (System.nanoTime() - s) / 1e6
        }
        times.sort()

        var sftpOk = false
        var sftpMs = 0.0
        runCatching {
            val s = System.nanoTime()
            val ch = session.openChannel("sftp") as ChannelSftp
            ch.connect(10_000)
            val input = ch.get(SFTP_PATH)
            input.readBytes()
            input.close()
            sftpMs = (System.nanoTime() - s) / 1e6
            sftpOk = true
            ch.disconnect()
        }

        log("RESULT,A3-JSch,ok=true,connect_ms=${"%.0f".format(connectMs)}," +
            "exec_p50=${"%.0f".format(times[times.size / 2])},fingerprint_match=${fp == EXPECTED_FP}," +
            "sftp=$sftpOk(${"%.0f".format(sftpMs)}ms)," +
            "providers_unchanged=${before == Security.getProviders().map { it.name }}")
        session.disconnect()
    }

    /** 最小 host key 适配：校验逻辑复用 core:ssh，避免依赖 UI 模块。 */
    private class Adapter(private val trust: StrictHostKeyTrust) : HostKeyVerifier {
        override fun verify(host: String?, port: Int, key: PublicKey): Boolean {
            val blob = runCatching { Buffer.PlainBuffer().putPublicKey(key).compactData }
                .getOrElse { key.encoded ?: return false }
            val alg = runCatching { SshKeyBlob.algorithmName(blob) }
                .getOrElse { KeyType.fromKey(key).toString() }
            return trust.verify(HostKeyId(host ?: "", port, alg), blob) { _: HostKeyUnknownException -> true }
        }

        override fun findExistingAlgorithms(host: String?, port: Int): List<String> =
            listOf("ssh-ed25519", "ecdsa-sha2-nistp256")
    }
}
