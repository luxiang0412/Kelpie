package herdrspike.keyprobe

import net.schmizz.sshj.SSHClient
import org.junit.jupiter.api.Test
import java.io.File

/** 只读探针：验证本机私钥能否被 sshj 加载并用于认证（不写远端）。 */
class KeyProbeTest {

    private fun probe(path: String, host: String, user: String) {
        val keyFile = File(System.getProperty("user.home"), ".ssh/$path")
        if (!keyFile.exists()) { println("SKIP $path (missing)"); return }
        val ssh = SSHClient()
        ssh.loadKnownHosts()
        try {
            ssh.connect(host, 22)
            val provider = runCatching {
                val kp: net.schmizz.sshj.userauth.keyprovider.KeyProvider =
                    if (keyFile.readText().contains("BEGIN OPENSSH PRIVATE KEY")) {
                        net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile().also { it.init(keyFile) }
                    } else {
                        net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile().also { it.init(keyFile) }
                    }
                kp
            }.getOrElse {
                println("LOAD_FAIL $path -> ${it.javaClass.name}: ${it.message}")
                return
            }
            println("LOADED $path type=${provider.public.javaClass.name} algo=${provider.public.algorithm}")
            val ok = runCatching { ssh.authPublickey(user, provider) }
            println("AUTH $path ok=${ok.isSuccess} err=${ok.exceptionOrNull()?.message}")
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    @Test
    fun probeKeys() {
        probe("id_rsa", "192.168.5.8", "luxiang")
        probe("luxiang_rsa", "192.168.5.8", "luxiang")
        probe("id_ed25519_spike", "192.168.5.8", "luxiang")
    }
}
