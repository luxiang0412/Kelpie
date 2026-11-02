package dev.herdr.signal.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.herdr.core.data.Pairing
import dev.herdr.core.data.PairingPayload
import dev.herdr.core.herdr.t
import dev.herdr.core.ssh.TrustStore
import dev.herdr.signal.container
import dev.herdr.core.designsystem.Signal
import dev.herdr.core.designsystem.SignalButton
import dev.herdr.core.designsystem.SignalSize
import dev.herdr.signal.ui.BackTopBar

/**
 * 扫码配对的确认页。入口：添加主机页的“扫描二维码 / 从图片识别”，或系统相机扫到
 * `herdr-signal://pair?…` 后通过深链打开。**无论哪个入口，都必须在这里由人确认后才写入。**
 */
@Composable
fun PairConfirmRoute(link: String, onCancel: () -> Unit, onPaired: () -> Unit) {
    val ctx = LocalContext.current
    val c = Signal.colors
    val parsed = remember(link) { PairingPayload.parse(link) }
    val repo = ctx.container.hosts
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.bgGrouped).statusBarsPadding()) {
        BackTopBar(t("扫码添加主机", "Add host from code"), onCancel)
        val p = parsed.getOrNull()
        if (p == null) {
            Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("无法使用这个二维码", "Can't use this code"), style = Signal.type.headline, color = c.textPrimary)
                Text(parsed.exceptionOrNull()?.message.orEmpty(), style = Signal.type.body, color = c.textSecondary)
            }
            Box(Modifier.navigationBarsPadding().padding(16.dp)) {
                SignalButton(t("返回", "Back"), onCancel, Modifier.fillMaxWidth(), height = SignalSize.primaryButton)
            }
            return@Column
        }
        val existing = remember(p) { Pairing.findExisting(repo, p) }
        val conflicts = remember(p, existing) {
            existing?.let { Pairing.conflicts(TrustStore(ctx.container.trustFile(it.id)), p) }.orEmpty()
        }
        val keyMissing = p.privateKey == null && (existing == null || !repo.hasKey(existing.id))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Group {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(existing?.label ?: p.label, style = Signal.type.headline, color = c.textPrimary)
                    Text("${p.user}@${p.host}:${p.port}", style = Signal.type.code, color = c.textPrimary)
                    Text(t("herdr 会话 ${p.session} · ${p.herdrBin}", "herdr session ${p.session} · ${p.herdrBin}"), style = Signal.type.caption, color = c.textSecondary)
                }
            }
            GroupLabel(t("主机指纹（来自二维码，将直接信任）", "Host fingerprints (from the code; will be trusted)"))
            Group {
                p.hostKeys.entries.forEachIndexed { i, (alg, fp) ->
                    if (i > 0) Divider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(alg, style = Signal.type.caption, color = c.textSecondary)
                        Text(fp, style = Signal.type.codeSmall, color = if (alg in conflicts) c.error else c.textPrimary)
                        conflicts[alg]?.let { Text(t("已保存的是 $it", "Saved: $it"), style = Signal.type.codeSmall, color = c.error) }
                    }
                }
            }
            GroupLabel(t("私钥", "Private key"))
            Group {
                Text(
                    when {
                        p.privateKey != null -> t("二维码附带一把这台手机专用的私钥，只保存在 App 私有目录里。", "The code carries a private key made for this phone; it's kept only in this app's private storage.")
                        !keyMissing -> t("二维码没有附带私钥，沿用已保存的私钥。", "No key in the code; the saved key is kept.")
                        else -> t("二维码没有附带私钥，这台手机也还没有这台主机的私钥。请用“添加主机”手动粘贴。", "No key in the code and none saved for this host. Use Add host to paste one.")
                    },
                    style = Signal.type.body, color = if (keyMissing) c.error else c.textPrimary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            val notes = buildList {
                if (existing != null) add(false to t("这台手机上已经有「${existing.label}」（同一地址、用户和会话），会更新它，不会新增一条。", "\"${existing.label}\" (same address, user and session) already exists; it will be updated rather than duplicated."))
                if (conflicts.isNotEmpty()) add(true to t("主机指纹和之前保存的不一致：可能是主机重装过，也可能二维码不是这台主机生成的。为安全起见不能用二维码覆盖；确认主机确实重装过后，先删除这台主机再扫码。", "The fingerprint differs from the saved one: the host may have been reinstalled, or the code isn't from that host. It can't be overwritten from a code; if the host really was reinstalled, delete it first and scan again."))
                p.expiresAtSec?.let { exp ->
                    val min = ((exp - System.currentTimeMillis() / 1000) / 60).coerceAtLeast(0)
                    add(false to t("二维码约 $min 分钟后过期。", "The code expires in about $min min."))
                }
            }
            notes.forEach { (warn, text) -> Text(text, style = Signal.type.caption, color = if (warn) c.error else c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
            error?.let { Text(it, style = Signal.type.metaStrong, color = c.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Spacer(Modifier.height(16.dp))
        }
        val can = conflicts.isEmpty() && !keyMissing
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            SignalButton(
                if (existing != null) t("更新这台主机", "Update host") else t("添加这台主机", "Add this host"),
                {
                    if (!can) return@SignalButton
                    Pairing.apply(repo, ctx.container::trustFile, p, existing)
                        .onSuccess { onPaired() }
                        .onFailure { error = it.message }
                },
                Modifier.fillMaxWidth(), height = SignalSize.primaryButton, enabled = can,
            )
        }
    }
}

/** 从图片里识别二维码（相册里的截图 / 照片）。 */
object QrImage {
    fun decode(context: Context, uri: Uri): String? = runCatching {
        val bmp = load(context, uri) ?: return null
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px)))
        val base = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.CHARACTER_SET to "UTF-8")
        // 先正常检测；失败再按“干净的二维码图”解码（截图 / 生成的 PNG 里，数据区偶尔有像定位点的图案会骗过检测器）
        runCatching { QRCodeReader().decode(bitmap, base + (DecodeHintType.TRY_HARDER to true)).text }.getOrNull()
            ?: runCatching { QRCodeReader().decode(bitmap, base + (DecodeHintType.PURE_BARCODE to true)).text }.getOrNull()
    }.getOrNull()

    private fun load(context: Context, uri: Uri): Bitmap? {
        val src = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = maxOf(info.size.width, info.size.height)
                if (m > 2000) d.setTargetSampleSize((m + 1999) / 2000)
            }
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        return src.copy(Bitmap.Config.ARGB_8888, false)
    }
}
