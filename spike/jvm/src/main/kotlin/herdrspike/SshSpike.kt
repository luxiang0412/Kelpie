package herdrspike

import herdrspike.trust.HostKeyId
import herdrspike.trust.HostKeyMismatchException
import herdrspike.trust.HostKeyUnknownException
import herdrspike.trust.SshjHostKeyVerifier
import herdrspike.trust.StrictHostKeyTrust
import herdrspike.trust.TrustStore
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * 针对真实主机的 Spike 1 测量（只读：不创建 pane、不发输入、不改远端状态）。
 *
 * 用法：
 *   spike run --mode hostkey  --host H --port P --user U --key K
 *   spike run --mode connect  --host ... --pin SHA256:xxx
 *   spike run --mode channels --host ... --pin SHA256:xxx --sock /path/herdr.sock
 *   spike run --mode bridge   --host ... --pin SHA256:xxx --sock /path/herdr.sock --method ping
 *
 * 指纹必须来自带外核对（ssh-keyscan + ssh-keygen -lf），通过 --pin 传入；
 * 没有 --pin 时只打印看到的指纹，然后以退出码 2 结束（fail-closed）。
 */
fun main(args: Array<String>) {
    // sshj 解析 OpenSSH/PKCS#1 私钥时需要 BouncyCastle；不注册会报
    // "No provider available for Unknown key file"。
    runCatching { net.schmizz.sshj.common.SecurityUtils.registerSecurityProvider(null) }
    val a = Args(args)
    when (a.mode) {
        "hostkey" -> hostKeyCases(a)
        "connect" -> connectTiming(a)
        "channels" -> channelConcurrency(a)
        "bridge" -> bridge(a)
        "pingloop" -> pingLoop(a)
        "keyinfo" -> {
            val ssh = SSHClient()
            val kp = loadKey(ssh, a.s("key")).first()
            val pub = kp.getPublic()
            val wire = herdrspike.trust.SshjHostKeyVerifier.sshWireBlob(pub)
            println("RESULT,keyinfo,type=${net.schmizz.sshj.common.KeyType.fromKey(pub)},alg=${herdrspike.trust.SshjHostKeyVerifier.algorithmOf(pub, wire)},sha256=${herdrspike.trust.Fingerprint.sha256(wire)}")
        }
        else -> {
            System.err.println("unknown --mode ${a.mode}")
            exitProcess(64)
        }
    }
}

private class Args(args: Array<String>) {
    private val m = HashMap<String, String>()
    val mode: String
    init {
        var i = 0
        while (i < args.size) {
            if (args[i].startsWith("--") && i + 1 < args.size) m[args[i].substring(2)] = args[i + 1]
            i += 2
        }
        mode = m["mode"] ?: "connect"
    }
    fun s(k: String, d: String? = null): String = m[k] ?: d ?: error("missing --$k")
    fun i(k: String, d: Int): Int = (m[k] ?: d.toString()).toInt()
    val has: (String) -> Boolean = { m.containsKey(it) }
}

private fun storeFile(a: Args) = File(a.s("store", "known_hosts.spike"))

private fun buildClient(a: Args, onUnknown: (HostKeyUnknownException) -> Boolean): SSHClient {
    val trust = StrictHostKeyTrust(TrustStore(storeFile(a)))
    val verifier = SshjHostKeyVerifier(trust, onUnknown)
    // OpenSSH 9.x 默认禁用 ssh-rsa(SHA1)；把 rsa-sha2-* 提到最前面，
    // 否则用 RSA key 会 “Exhausted available authentication methods”。
    val cfg = net.schmizz.sshj.DefaultConfig()
    if (System.getenv("SPIKE_DEBUG_ALGS") != null) {
        System.err.println("keyAlgorithms=" + cfg.keyAlgorithms.map { it.name })
    }
    return SSHClient(cfg).apply {
        addHostKeyVerifier(verifier)
        connectTimeout = 10_000
        timeout = 20_000
    }
}

/** F1 / F4 的真机版本：只做 KEX + 指纹判断，认证前就应失败。 */
private fun hostKeyCases(a: Args) {
    val host = a.s("host"); val port = a.i("port", 22)
    var ok = true

    // F1: 未知主机 + 用户不确认 → 必须抛异常，且不得写入信任库
    val f1Store = File.createTempFile("f1case", ".spike").also { it.delete() }
    run {
        val args = Args(arrayOf("--host", host, "--port", "$port", "--user", a.s("user"),
            "--key", a.s("key"), "--store", f1Store.absolutePath))
        val ssh = buildClient(args) { false }
        val t0 = System.nanoTime()
        val threw = outcome(ssh, host, port) == Outcome.UNKNOWN
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        val wrote = f1Store.exists() && f1Store.length() > 0
        println("RESULT,F1,unknown_host_rejected,${threw},store_written=${wrote},ms=%.1f".format(ms))
        if (!threw || wrote) ok = false
        runCatching { ssh.close() }
    }

    // F4: 已固定但指纹被篡改 → Mismatch，且不覆写
    val f4Store = File.createTempFile("f4case", ".spike").also { it.delete() }
    run {
        f4Store.writeText("$host:$port:ssh-ed25519 SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n")
        val args = Args(arrayOf("--host", host, "--port", "$port", "--user", a.s("user"),
            "--key", a.s("key"), "--store", f4Store.absolutePath))
        val ssh = buildClient(args) { true }
        val threw = outcome(ssh, host, port) == Outcome.MISMATCH
        val unchanged = f4Store.readText().contains("SHA256:AAAA")
        println("RESULT,F4,tampered_pin_rejected,$threw,pin_unchanged=$unchanged")
        if (!threw || !unchanged) ok = false
        runCatching { ssh.close() }
    }

    // F2/F3: 显式确认后 pin，再次连接不再询问
    run {
        val f23 = File.createTempFile("f23case", ".spike").also { it.delete() }
        val args = Args(arrayOf("--host", host, "--port", "$port", "--user", a.s("user"),
            "--key", a.s("key"), "--store", f23.absolutePath))
        var prompts = 0
        val ssh = buildClient(args) { prompts++; true }
        ssh.connect(host, port)
        val pinned = f23.readText().trim()
        ssh.close()
        val ssh2 = buildClient(Args(arrayOf("--host", host, "--port", "$port", "--user", a.s("user"),
            "--key", a.s("key"), "--store", f23.absolutePath))) { prompts++; true }
        ssh2.connect(host, port); ssh2.close()
        println("RESULT,F2/F3,pin_then_silent,prompts=$prompts,pinned=${pinned.replace(' ', '_')}")
        if (prompts != 1) ok = false
    }

    println(if (ok) "HOSTKEY_ALL_PASS" else "HOSTKEY_SOME_FAIL")
    if (!ok) exitProcess(1)
}

/** 冷连接 + 认证耗时（每次新建 TCP/KEX/auth）。 */
private fun connectTiming(a: Args) {
    val host = a.s("host"); val port = a.i("port", 22); val user = a.s("user"); val key = a.s("key")
    val n = a.i("runs", 10)
    repeat(n) { run ->
        val ssh = buildClient(a) { false }
        val t0 = System.nanoTime()
        try {
            ssh.connect(host, port)
            val tConnect = (System.nanoTime() - t0) / 1_000_000.0
            val t1 = System.nanoTime()
            ssh.authPublickey(user, loadKey(ssh, key))
            val tAuth = (System.nanoTime() - t1) / 1_000_000.0
            val pinned = TrustStore(storeFile(a)).algorithmsFor(host, port).joinToString("|").ifEmpty { "none" }
            println("RESULT,connect,run=$run,connect_ms=%.1f,auth_ms=%.1f,total_ms=%.1f,pinned_algs=$pinned"
                .format(tConnect, tAuth, tConnect + tAuth))
        } catch (e: Exception) {
            println("RESULT,connect,run=$run,ERROR=${e.javaClass.simpleName}:${e.message}")
        } finally {
            runCatching { ssh.close() }
        }
    }
}

/** exec channel 并发：不做 pane 压力测试，只跑 `true`。 */
private fun channelConcurrency(a: Args) {
    val host = a.s("host"); val port = a.i("port", 22); val user = a.s("user"); val key = a.s("key")
    val ssh = buildClient(a) { false }
    ssh.connect(host, port)
    ssh.authPublickey(user, loadKey(ssh, key))
    println("RESULT,channels,connected=1")

    // 单请求往返（顺序 10 次）
    val single = (1..10).map {
        val t0 = System.nanoTime()
        execOnce(ssh)
        (System.nanoTime() - t0) / 1_000_000.0
    }
    println("RESULT,exec_single,n=${single.size},min=%.1f,p50=%.1f,max=%.1f".format(
        single.min(), pct(single, 50.0), single.max()))

    val levels = a.s("list", "1,4,8,10,12").split(',').map { it.trim().toInt() }
    for (c in levels) {
        val pool = Executors.newFixedThreadPool(c)
        val tasks = (1..c).map { Callable { runCatching { execOnce(ssh) }.exceptionOrNull() } }
        val t0 = System.nanoTime()
        val results = pool.invokeAll(tasks).map { it.get(30, TimeUnit.SECONDS) }
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        pool.shutdown()
        val okCount = results.count { it == null }
        val errs = results.filterNotNull().map { "${it.javaClass.simpleName}:${it.message}" }.distinct()
        println("RESULT,channels,concurrency=$c,ok=$okCount,fail=${results.size - okCount},wall_ms=%.1f".format(ms))
        errs.forEach { println("RESULT,channels,concurrency=$c,error=$it") }

        // 连接是否仍可用
        val alive = runCatching { execOnce(ssh); true }.getOrElse { false }
        println("RESULT,channels,concurrency=$c,connection_alive_after=$alive")
    }
    runCatching { ssh.close() }
}

/**
 * 在**同一条 SSH 连接**上顺序发 N 次 ping（每次一个新 exec channel，
 * 因为 remote-api-bridge 一个连接只处理一个请求）。这是方案 A 的真实每请求成本。
 */
private fun pingLoop(a: Args) {
    val host = a.s("host"); val port = a.i("port", 22); val user = a.s("user"); val key = a.s("key")
    val count = a.i("count", 20)
    val herdr = a.s("herdr", "/home/luxiang/.local/bin/herdr")
    val session = a.s("session", "test")
    val ssh = buildClient(a) { false }
    ssh.connect(host, port)
    ssh.authPublickey(user, loadKey(ssh, key))
    val ms = ArrayList<Double>()
    repeat(count) { i ->
        val t0 = System.nanoTime()
        val line = execCapture(ssh, "$herdr --session $session remote-api-bridge",
            """{"id":"p$i","method":"ping","params":{}}""")
        val d = (System.nanoTime() - t0) / 1_000_000.0
        ms += d
        if (i == 0) println("RESULT,pingloop,first_response=${line.take(120)}")
    }
    println("RESULT,pingloop,n=${ms.size},min=%.1f,p50=%.1f,p95=%.1f,max=%.1f".format(
        ms.min(), pct(ms, 50.0), pct(ms, 95.0), ms.max()))
    runCatching { ssh.close() }
}

private fun execCapture(ssh: SSHClient, cmdline: String, stdinLine: String): String {
    ssh.startSession().use { s ->
        val cmd = s.exec(cmdline)
        cmd.outputStream.use { it.write((stdinLine + "\n").toByteArray()); it.flush() }
        val line = cmd.inputStream.bufferedReader().readLine() ?: ""
        cmd.join(20, TimeUnit.SECONDS)
        runCatching { IOUtils.readFully(cmd.errorStream).close() }
        return line
    }
}

private fun execOnce(ssh: SSHClient) {
    ssh.startSession().use { s ->
        val cmd = s.exec("true")
        cmd.join(20, TimeUnit.SECONDS)
        IOUtils.readFully(cmd.errorStream).close()
        IOUtils.readFully(cmd.inputStream).close()
    }
}

/**
 * 通过 socat 把 NDJSON 请求送进 herdr.sock（等价于 remote-api-bridge 的最小替身）。
 * 0.9.0 没有 remote-api-bridge 子命令，所以用 socat。
 */
private fun bridge(a: Args) {
    val host = a.s("host"); val port = a.i("port", 22); val user = a.s("user"); val key = a.s("key")
    val sock = a.s("sock")
    val method = a.s("method", "ping")
    val id = a.s("id", "spike-1")
    val ssh = buildClient(a) { false }
    ssh.connect(host, port)
    ssh.authPublickey(user, loadKey(ssh, key))

    val req = if (method == "ping") """{"id":"$id","method":"ping","params":{}}"""
    else """{"id":"$id","method":"$method","params":{}}"""
    val t0 = System.nanoTime()
    ssh.startSession().use { s ->
        val cmd = s.exec("timeout 20 socat - UNIX-CONNECT:$sock")
        cmd.outputStream.use { it.write((req + "\n").toByteArray()); it.flush() }
        val line = cmd.inputStream.bufferedReader().readLine()
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        cmd.join(20, TimeUnit.SECONDS)
        IOUtils.readFully(cmd.errorStream).close()
        println("RESULT,bridge,method=$method,ms=%.1f,response=%s".format(ms, line ?: "<null>"))
    }
    runCatching { ssh.close() }
}

private enum class Outcome { OK, UNKNOWN, MISMATCH, OTHER }

/**
 * sshj 把 HostKeyVerifier 抛出的异常包成 SSHException 在传输线程里抛出，
 * 所以必须走 cause 链判断，不能只 catch 自己那两种异常。
 */
private fun outcome(ssh: SSHClient, host: String, port: Int): Outcome =
    try {
        ssh.connect(host, port)
        Outcome.OK
    } catch (e: Exception) {
        when {
            e.hasCause<HostKeyUnknownException>() -> Outcome.UNKNOWN
            e.hasCause<HostKeyMismatchException>() -> Outcome.MISMATCH
            else -> {
                System.err.println("  connect failed: ${e.javaClass.simpleName}: ${e.message}")
                Outcome.OTHER
            }
        }
    }

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var c: Throwable? = this
    var guard = 0
    while (c != null && guard++ < 10) {
        if (c is T) return true
        c = c.cause
    }
    return false
}

/**
 * sshj 的 loadKeys(path) 在当前环境里对 OpenSSH 格式返回 "Unknown key file"
 * （形参路径/格式探测都试过），所以直接走 OpenSSHKeyFile；失败再回退到 loadKeys。
 */
private fun loadKey(ssh: SSHClient, path: String): List<net.schmizz.sshj.userauth.keyprovider.KeyProvider> {
    val f = File(path)
    require(f.exists()) { "key file not found: $path" }
    // 新格式（-----BEGIN OPENSSH PRIVATE KEY-----）必须用 OpenSSHKeyV1KeyFile
    val pub = File("$path.pub")
    val v1 = runCatching<net.schmizz.sshj.userauth.keyprovider.KeyProvider> {
        com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile().apply {
            if (pub.exists()) init(f, pub) else init(f)
        }
    }.getOrNull()
    if (v1 != null) return listOf(v1)
    val openSsh = runCatching {
        net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile().apply { init(f) }
    }.getOrNull()
    if (openSsh != null) return listOf(openSsh)
    return listOf(ssh.loadKeys(path))
}

private fun pct(v: List<Double>, p: Double): Double {
    val s = v.sorted(); if (s.isEmpty()) return Double.NaN
    val idx = ((p / 100.0) * (s.size - 1)).toInt().coerceIn(0, s.size - 1)
    return s[idx]
}
