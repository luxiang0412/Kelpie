package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.t
import io.github.luxiang0412.kelpie.core.ssh.HostKeyId
import io.github.luxiang0412.kelpie.core.ssh.TrustStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.Base64

/**
 * 二维码配对（`companion/pair/kelpie_pair.py` 生成）。
 *
 * 链接：`kelpie://pair?d=<base64url(JSON)>`。二维码由开发机自己显示，所以里面的 host key 指纹
 * 视为已经人工核对过（带外通道），配对时直接固定；之后连接仍然严格校验，不匹配就拒绝。
 */
data class PairingPayload(
    val label: String,
    val host: String,
    val port: Int,
    val user: String,
    val session: String,
    val herdrBin: String,
    /** 审批请求目录（字段 `a`，可选；旧二维码没有时用默认值）。 */
    val approvalsDir: String = ApprovalsPath.DEFAULT,
    /** 算法 → `SHA256:…` */
    val hostKeys: Map<String, String>,
    val privateKey: String?,
    val expiresAtSec: Long?,
) {
    /** 手机上展示用：不含私钥。 */
    override fun toString() = "PairingPayload($label, $user@$host:$port, session=$session, keys=${hostKeys.keys})"

    companion object {
        const val SCHEME = "kelpie"
        private val FP = Regex("^SHA256:[A-Za-z0-9+/]{43}$")
        private val ALG = Regex("^[a-z0-9@.-]{3,64}$")
        /** 允许的时钟偏差（手机和开发机的时间不一定一致）。 */
        private const val SKEW_SEC = 300

        fun looksLikePairing(text: String) = text.trim().startsWith("$SCHEME://pair")

        fun parse(text: String, nowSec: Long = System.currentTimeMillis() / 1000): Result<PairingPayload> = runCatching {
            val s = text.trim()
            require(looksLikePairing(s)) { t("这不是 Kelpie 的配对二维码", "This isn't a Kelpie pairing code") }
            val d = s.substringAfter("?").split('&').firstOrNull { it.startsWith("d=") }?.removePrefix("d=")
            requireNotNull(d) { t("配对二维码不完整", "The pairing code is incomplete") }
            val raw = runCatching { Base64.getUrlDecoder().decode(d.trimEnd('=')) }.getOrNull()
            requireNotNull(raw) { t("配对二维码损坏（不是有效的 base64）", "The pairing code is damaged (bad base64)") }
            val o = runCatching { Json.parseToJsonElement(raw.decodeToString()).jsonObject }.getOrNull()
            requireNotNull(o) { t("配对二维码损坏（不是有效的 JSON）", "The pairing code is damaged (bad JSON)") }

            val v = o.int("v")
            require(v == 1) { t("配对二维码版本不支持（$v），请更新 App", "Unsupported pairing version ($v); update the app") }
            val exp = o["exp"]?.jsonPrimitive?.longOrNull
            if (exp != null) require(nowSec <= exp + SKEW_SEC) { t("配对二维码已过期，请在电脑上重新生成", "This pairing code has expired; generate a new one on the computer") }

            val host = o.str("h")
            require(!host.isNullOrBlank() && host.length <= 253 && host.none { it.isWhitespace() }) { t("配对二维码里的地址不对", "The address in the pairing code is invalid") }
            val user = o.str("u")
            require(!user.isNullOrBlank() && user.length <= 64 && user.none { it.isWhitespace() }) { t("配对二维码里的用户名不对", "The username in the pairing code is invalid") }
            val port = o.int("p") ?: 22
            require(port in 1..65535) { t("配对二维码里的端口不对", "The port in the pairing code is invalid") }
            val session = o.str("s")?.takeIf { it.isNotBlank() } ?: "default"
            val bin = o.str("b")?.takeIf { it.startsWith("/") } ?: "/home/$user/.local/bin/herdr"

            // 审批目录写错不致命：退回默认值，不拒绝整个二维码
            val approvalsDir = o.str("a")?.let { ApprovalsPath.normalize(it).getOrNull() } ?: ApprovalsPath.DEFAULT

            val keys = (o["k"] as? JsonObject)?.mapNotNull { (alg, fp) ->
                val f = runCatching { fp.jsonPrimitive.contentOrNull }.getOrNull() ?: return@mapNotNull null
                if (ALG.matches(alg) && FP.matches(f)) alg to f else null
            }?.toMap().orEmpty()
            require(keys.isNotEmpty()) { t("配对二维码里没有主机指纹，不能安全地添加", "The pairing code has no host fingerprint; can't add the host safely") }

            val key = o.str("key")?.trim()?.takeIf { it.isNotEmpty() }
            if (key != null) require(key.contains("PRIVATE KEY-----")) { t("配对二维码里的私钥格式不对", "The private key in the pairing code is malformed") }

            PairingPayload(
                label = o.str("n")?.trim()?.take(64)?.ifBlank { null } ?: host,
                host = host, port = port, user = user, session = session, herdrBin = bin,
                approvalsDir = approvalsDir, hostKeys = keys, privateKey = key, expiresAtSec = exp,
            )
        }

        private fun JsonObject.str(k: String) = runCatching { this[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        private fun JsonObject.int(k: String) = runCatching { this[k]?.jsonPrimitive?.intOrNull }.getOrNull()
    }
}

object Pairing {
    /** 已有的同一台主机（地址 + 端口 + 用户 + 会话都相同）。 */
    fun findExisting(repo: HostRepository, p: PairingPayload): HostRepository.HostConfig? =
        repo.list().firstOrNull { it.host == p.host && it.port == p.port && it.user == p.user && it.session == p.session }

    /**
     * 已经固定过、但和二维码不一致的指纹（算法 → 已保存的指纹）。非空时必须拒绝：
     * 要么二维码是伪造的，要么主机重装过——都不该悄悄覆盖。
     */
    fun conflicts(trust: TrustStore, p: PairingPayload): Map<String, String> =
        p.hostKeys.mapNotNull { (alg, fp) ->
            val pinned = trust.get(HostKeyId(p.host, p.port, alg))
            if (pinned != null && pinned != fp) alg to pinned else null
        }.toMap()

    /**
     * 写入：先私钥、再指纹、最后配置（配置一变连接层就会去连，那时前两样必须已经就位）。
     * 没带私钥的二维码只能更新已有主机（沿用它的私钥）。
     */
    fun apply(repo: HostRepository, trustFileFor: (String) -> File, p: PairingPayload, existing: HostRepository.HostConfig?): Result<String> = runCatching {
        val id = existing?.id ?: repo.newId()
        if (p.privateKey != null) repo.importKey(id, p.privateKey).getOrThrow()
        else require(existing != null && repo.hasKey(id)) { t("二维码里没有私钥，这台手机也还没有这台主机的私钥", "The code has no private key and this phone has none for that host") }
        val trust = TrustStore(trustFileFor(id))
        require(conflicts(trust, p).isEmpty()) { t("主机指纹和之前保存的不一致，已拒绝", "Host fingerprint differs from the saved one; refused") }
        p.hostKeys.forEach { (alg, fp) -> trust.pin(HostKeyId(p.host, p.port, alg), fp) }
        repo.upsert(
            HostRepository.HostConfig(
                id = id, label = existing?.label ?: p.label, host = p.host, port = p.port,
                user = p.user, session = p.session, herdrBin = p.herdrBin, approvalsDir = p.approvalsDir,
            ),
        )
        id
    }
}
