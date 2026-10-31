package herdrspike.android

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import herdrspike.trust.HostKeyId
import herdrspike.trust.HostKeyMismatchException
import herdrspike.trust.HostKeyUnknownException
import herdrspike.trust.SshjHostKeyVerifier
import herdrspike.trust.StrictHostKeyTrust
import herdrspike.trust.TrustStore
import net.schmizz.sshj.SSHClient
import java.io.File

/**
 * 最小 Android 真机 Spike（Spike 1）：
 *  F1 未知主机必须被拒且不落盘；F2 显式确认后 pin；F3 再次连接不再询问；F4 篡改必须拒绝；
 *  然后测 connect+auth 耗时，以及一次真实的 herdr `ping`（经 remote-api-bridge）。
 *
 * 结果既打 Log.i("SPIKE")，也显示在界面上。
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "SPIKE"
        private const val HOST = "192.168.5.8"
        private const val PORT = 22
        private const val USER = "luxiang"
        private const val KEY_NAME = "id_ed25519_spike"
        private const val HERDR = "/home/luxiang/.local/bin/herdr"
        private const val SESSION = "test"
    }

    private lateinit var output: TextView
    private val sb = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }
        val rerun = Button(this).apply {
            text = "重新运行"
            setOnClickListener { sb.setLength(0); output.text = ""; Thread { runSpike() }.start() }
        }
        val showDialog = Button(this).apply {
            text = "演示指纹确认对话框（不连接）"
            setOnClickListener { confirmDialog("SHA256:demo-fingerprint-不是真实指纹") { } }
        }
        output = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.START
        }
        root.addView(rerun)
        root.addView(showDialog)
        root.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        Thread { runSpike() }.start()
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        runOnUiThread {
            sb.append(line).append('\n')
            output.text = sb.toString()
        }
    }

    // ---------- 测试主体 ----------

    private fun runSpike() {
        log("ENV,device=${Build.MANUFACTURER} ${Build.MODEL},android=${Build.VERSION.RELEASE},sdk=${Build.VERSION.SDK_INT}")
        // Android 自带的老 BouncyCastle 缺少 X25519/SHA-256；移除后换成 APK 内的新版 BC。
        runCatching {
            val removed = java.security.Security.removeProvider("BC")
            val added = java.security.Security.addProvider(
                org.bouncycastle.jce.provider.BouncyCastleProvider()
            )
            net.schmizz.sshj.common.SecurityUtils.setRegisterBouncyCastle(true)
            val prov = net.schmizz.sshj.common.SecurityUtils.registerSecurityProvider("BC")
            log("PROVIDER,removed_old=$removed,add_new=$added,sshj_registered=$prov," +
                "sshj_provider=${net.schmizz.sshj.common.SecurityUtils.getSecurityProvider()}")
        }.onFailure { log("PROVIDER,setup_failed=$it") }
        val keyFile = File(filesDir, KEY_NAME)
        if (!keyFile.exists()) {
            log("ABORT,missing_key,expected=${keyFile.absolutePath}")
            log("HINT,push: adb push <key> /data/local/tmp/$KEY_NAME && adb shell run-as $packageName cp /data/local/tmp/$KEY_NAME ${keyFile.absolutePath}")
            return
        }
        val storeFile = File(filesDir, "known_hosts.spike")

        // F1: 未知主机 + 用户不确认
        storeFile.delete()
        run {
            var rejected = false
            val t0 = System.nanoTime()
            try {
                connect(TrustStore(storeFile), onUnknown = { false })
            } catch (e: Exception) {
                rejected = hasCause<HostKeyUnknownException>(e)
                if (!rejected) log("F1,unexpected_error=$e")
            }
            val ms = (System.nanoTime() - t0) / 1e6
            val wrote = storeFile.exists() && storeFile.length() > 0
            log("RESULT,F1,rejected=$rejected,store_written=$wrote,ms=%.0f".format(ms))
        }

        // F2: 显式确认（用真实对话框；自动化时点“信任”）
        var prompts = 0
        var ssh: SSHClient? = null
        run {
            val t0 = System.nanoTime()
            ssh = try {
                connect(TrustStore(storeFile), onUnknown = { ex -> prompts++; askUser(ex) })
            } catch (e: Exception) {
                log("RESULT,F2,failed=$e"); null
            }
            val ms = (System.nanoTime() - t0) / 1e6
            val pinned = if (storeFile.exists()) storeFile.readText().trim() else ""
            log("RESULT,F2,prompts=$prompts,connect_auth_ms=%.0f,pinned=$pinned".format(ms))
        }

        // F3: 再次连接不应再询问
        run {
            val before = prompts
            try {
                val c = connect(TrustStore(storeFile), onUnknown = { prompts++; true })
                c.close()
                log("RESULT,F3,extra_prompts=${prompts - before}")
            } catch (e: Exception) {
                log("RESULT,F3,failed=$e")
            }
        }

        // F4: 篡改指纹
        run {
            storeFile.writeText("$HOST:$PORT:ssh-ed25519 SHA256:AAAAtamperedAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n")
            var mismatch = false
            try {
                connect(TrustStore(storeFile), onUnknown = { true })
            } catch (e: Exception) {
                mismatch = hasCause<HostKeyMismatchException>(e)
                if (!mismatch) log("F4,unexpected_error=$e")
            }
            val unchanged = storeFile.readText().contains("SHA256:AAAA")
            log("RESULT,F4,mismatch=$mismatch,pin_unchanged=$unchanged")
        }

        // 恢复正确 pin（重新确认一次），然后测延迟与 ping
        storeFile.delete()
        val conn = try {
            connect(TrustStore(storeFile), onUnknown = { true })
        } catch (e: Exception) {
            log("RESULT,RECONNECT,failed=$e"); return
        }
        conn.use {
            var total = 0.0
            repeat(5) { i ->
                val t0 = System.nanoTime()
                execCapture(it, "true")
                val ms = (System.nanoTime() - t0) / 1e6
                total += ms
                log("RESULT,EXEC,run=$i,ms=%.0f".format(ms))
            }
            log("RESULT,EXEC_AVG,ms=%.0f".format(total / 5))

            repeat(5) { i ->
                val t0 = System.nanoTime()
                val line = execCapture(
                    it,
                    "$HERDR --session $SESSION remote-api-bridge",
                    """{"id":"p$i","method":"ping","params":{}}"""
                )
                val ms = (System.nanoTime() - t0) / 1e6
                log("RESULT,PING,run=$i,ms=%.0f,resp=%s".format(ms, line.take(100)))
            }
        }
        // 第二阶段：在真机上验证 snapshot + 双层订阅 + 端到端事件时延
        runCatching { phase2() }.onFailure { log("PHASE2,failed=$it") }
        runCatching { phase3Sftp() }.onFailure { log("PHASE3,failed=$it") }
        log("DONE")
    }

    /**
     * Phase 2：snapshot 延迟、订阅建立延迟、以及“自己触发事件 → 自己收到事件”的端到端时延。
     * 触发用 pane.split/close，只作用于 w4（我们的专用 workspace），不碰用户 pane。
     */
    private fun phase2() {
        val store = TrustStore(File(filesDir, "known_hosts.spike"))
        val ssh = connect(store) { true }
        ssh.use {
            // 1) snapshot x3
            repeat(3) { i ->
                val t0 = System.nanoTime()
                val line = execCapture(
                    it, "$HERDR --session $SESSION remote-api-bridge",
                    """{"id":"s$i","method":"session.snapshot","params":{}}"""
                )
                val ms = (System.nanoTime() - t0) / 1e6
                val panes = Regex("\"pane_id\":").findAll(line).count()
                log("RESULT,SNAPSHOT,run=$i,ms=%.0f,bytes=%d,panes=%d".format(ms, line.length, panes))
            }

            // 2) 建订阅（全局生命周期 + w4:p1 状态）
            val sess = it.startSession()
            val cmd = sess.exec("$HERDR --session $SESSION remote-api-bridge")
            val subJson = """{"id":"subDev","method":"events.subscribe","params":{"subscriptions":[""" +
                """{"type":"pane.created"},{"type":"pane.closed"},{"type":"pane.updated"},""" +
                """{"type":"pane.agent_status_changed","pane_id":"w4:p1"}]}}"""
            val t0 = System.nanoTime()
            cmd.outputStream.write((subJson + "\n").toByteArray())
            cmd.outputStream.flush()
            val lines = java.util.Collections.synchronizedList(ArrayList<Pair<Long, String>>())
            val reader = Thread {
                runCatching {
                    cmd.inputStream.bufferedReader().forEachLine { l ->
                        lines.add(Pair(System.nanoTime(), l))
                    }
                }
            }.apply { isDaemon = true; start() }

            fun waitFor(pred: (String) -> Boolean, timeoutMs: Long): Pair<Long, String>? {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    synchronized(lines) {
                        val hit = lines.firstOrNull { pred(it.second) }
                        if (hit != null) return hit
                    }
                    Thread.sleep(5)
                }
                return null
            }

            val started = waitFor({ it.contains("subscription_started") }, 10_000)
            log("RESULT,SUBSCRIBE,started=%s,ms=%.0f".format(
                started != null, (System.nanoTime() - t0) / 1e6))

            // 3) 自己触发 split → 等 pane_created
            val tSplit = System.nanoTime()
            val splitResp = execCapture(
                it, "$HERDR --session $SESSION remote-api-bridge",
                """{"id":"sp","method":"pane.split","params":{"target_pane_id":"w4:p1","direction":"right","cwd":"/home/luxiang/spike-sandbox"}}"""
            )
            val newPane = Regex("\"pane_id\":\"([^\"]+)\"").find(splitResp)?.groupValues?.get(1) ?: "?"
            val created = waitFor({ it.contains("pane_created") }, 10_000)
            log("RESULT,EVENT_CREATED,pane=$newPane,split_rtt_ms=%.0f,event_ms=%.0f".format(
                (System.nanoTime() - tSplit) / 1e6,
                created?.let { (it.first - tSplit) / 1e6 } ?: -1.0))

            // 4) 关闭 → 等 pane_closed
            val tClose = System.nanoTime()
            execCapture(
                it, "$HERDR --session $SESSION remote-api-bridge",
                """{"id":"cl","method":"pane.close","params":{"pane_id":"$newPane"}}"""
            )
            val closed = waitFor({ it.contains("pane_closed") }, 10_000)
            log("RESULT,EVENT_CLOSED,pane=$newPane,close_rtt_ms=%.0f,event_ms=%.0f".format(
                (System.nanoTime() - tClose) / 1e6,
                closed?.let { (it.first - tClose) / 1e6 } ?: -1.0))

            log("RESULT,EVENT_COUNT,total=${lines.size}")
            runCatching { cmd.close() }
            runCatching { sess.close() }
        }
    }

    /** Phase 3：真机 SFTP + SftpTail（追加 / 截断 / 替换 / 回填）。只操作沙箱文件。 */
    private fun phase3Sftp() {
        val path = "/home/luxiang/spike-sandbox/sftp-android.jsonl"
        val ssh = connect(TrustStore(File(filesDir, "known_hosts.spike"))) { true }
        ssh.use {
            execCapture(it, "printf '%s\n' '{\"n\":1}' '{\"n\":2}' '{\"n\":3}' > $path")
            val sftp = it.newSFTPClient()
            val src = herdrspike.tail.SftpByteSource(sftp)
            val tail = herdrspike.tail.SftpTail(src, path)
            fun drain(tag: String) {
                val t0 = System.nanoTime()
                val r = tail.poll()
                val ms = (System.nanoTime() - t0) / 1e6
                when (r) {
                    is herdrspike.tail.TailResult.Lines ->
                        log("RESULT,SFTP,$tag,lines=%d,last=%s,ms=%.0f".format(r.lines.size, r.lines.lastOrNull() ?: "", ms))
                    else -> log("RESULT,SFTP,$tag,result=$r,ms=%.0f".format(ms))
                }
            }
            val st = src.stat(path)
            log("RESULT,SFTP,stat=$st,attrs=${src.lastAttributes},err=${src.lastError}")
            drain("1-initial")
            execCapture(it, "printf '%s\n' '{\"n\":4}' '{\"n\":5}' >> $path")
            drain("2-append")
            execCapture(it, "truncate -s 10 $path")
            drain("3-truncate")
            drain("4-after-truncate")
            execCapture(it, "seq 1 100 | sed 's/^/{\"big\":/;s/$/}/' > $path")
            drain("5-replace-larger")
            drain("6-after-replace")
            val t0 = System.nanoTime()
            val back = tail.backfillLastLines(30)
            log("RESULT,SFTP,7-backfill,lines=%d,last=%s,ms=%.0f".format(
                back.size, back.lastOrNull() ?: "", (System.nanoTime() - t0) / 1e6))
            runCatching { sftp.close() }
        }
    }

    /** 弹真实对话框；返回用户是否点了“信任”。 */
    private fun askUser(ex: HostKeyUnknownException): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        val answer = java.util.concurrent.atomic.AtomicBoolean(false)
        runOnUiThread {
            confirmDialog("${ex.id}\n${ex.fingerprint}") {
                answer.set(true)
                latch.countDown()
            }
        }
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
        Log.i(TAG, "DIALOG,answered=${answer.get()}")
        return answer.get()
    }

    private fun confirmDialog(message: String, onTrust: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle("确认主机指纹（请带外核对）")
            .setMessage(message + "\n\n首次连接必须人工确认；指纹不一致时请取消。")
            .setPositiveButton("信任该主机") { _, _ -> onTrust() }
            .setNegativeButton("取消") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    // ---------- sshj 辅助 ----------

    private fun connect(store: TrustStore, onUnknown: (HostKeyUnknownException) -> Boolean): SSHClient {
        val ssh = SSHClient(newConfig()).apply {
            addHostKeyVerifier(SshjHostKeyVerifier(StrictHostKeyTrust(store), onUnknown))
            connectTimeout = 10_000
            timeout = 20_000
        }
        try {
            ssh.connect(HOST, PORT)
            ssh.authPublickey(USER, loadKey(ssh))
            return ssh
        } catch (e: Exception) {
            runCatching { ssh.close() }
            throw e
        }
    }

    /**
     * Android 上 sshj 默认的 curve25519 KEX 依赖 BouncyCastle 的 X25519，
     * 而 Android 自带的 BC 太旧 → "no such algorithm: X25519 for provider BC"。
     * 这里只保留 JCE 原生可用的 KEX（ECDH / DH group14-16 / GEX）。
     */
    private fun newConfig(): net.schmizz.sshj.DefaultConfig {
        // Android 自带一个很老的 BouncyCastle（provider 名 BC），缺少 sshj 需要的
        // X25519 / SHA-256 等算法 → 让 sshj 不要优先用 BC，走默认 JCE。
        runCatching { net.schmizz.sshj.common.SecurityUtils.setRegisterBouncyCastle(false) }
        runCatching { net.schmizz.sshj.common.SecurityUtils.setSecurityProvider(null) }
        log("PROVIDER,sshj=" + runCatching { net.schmizz.sshj.common.SecurityUtils.getSecurityProvider() }.getOrElse { "err:$it" })
        val cfg = net.schmizz.sshj.DefaultConfig()
        val keep = listOf("curve25519-sha256", "curve25519-sha256@libssh.org",
            "ecdh-sha2-nistp256", "ecdh-sha2-nistp384", "ecdh-sha2-nistp521",
            "diffie-hellman-group14-sha256", "diffie-hellman-group16-sha512",
            "diffie-hellman-group-exchange-sha256")
        val filtered = cfg.keyExchangeFactories.filter { it.name in keep }
        if (filtered.isNotEmpty()) cfg.keyExchangeFactories = filtered
        log("CONFIG,kex=" + cfg.keyExchangeFactories.map { it.name })
        return cfg
    }

    private fun loadKey(ssh: SSHClient): List<net.schmizz.sshj.userauth.keyprovider.KeyProvider> {
        val f = File(filesDir, KEY_NAME)
        val v1 = runCatching<net.schmizz.sshj.userauth.keyprovider.KeyProvider> {
            com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile().apply { init(f) }
        }.getOrNull()
        if (v1 != null) return listOf(v1)
        return listOf(ssh.loadKeys(f.absolutePath))
    }

    private fun execCapture(ssh: SSHClient, cmd: String, stdin: String? = null): String {
        ssh.startSession().use { s ->
            val c = s.exec(cmd)
            if (stdin != null) {
                c.outputStream.use { it.write((stdin + "\n").toByteArray()); it.flush() }
            }
            val line = c.inputStream.bufferedReader().readLine() ?: ""
            c.join(15, java.util.concurrent.TimeUnit.SECONDS)
            runCatching { c.errorStream.close() }
            return line
        }
    }

    private inline fun <reified T : Throwable> hasCause(t: Throwable): Boolean {
        var c: Throwable? = t
        var guard = 0
        while (c != null && guard++ < 10) {
            if (c is T) return true
            c = c.cause
        }
        return false
    }
}
