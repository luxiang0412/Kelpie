package io.github.luxiang0412.kelpie

import android.app.Application
import android.util.Log
import io.github.luxiang0412.kelpie.core.data.HostRepository
import org.json.JSONObject
import java.io.File

/**
 * 仅 debug 包：通过 adb 预置主机，免去在手机上粘贴私钥。
 *
 *   adb push provision.json  /sdcard/Android/data/io.github.luxiang0412.kelpie.debug/files/provision.json
 *   adb push id_xxx          /sdcard/Android/data/io.github.luxiang0412.kelpie.debug/files/<keyFile>
 *
 * provision.json：{"hosts":[{"id","label","host","port","user","session","herdrBin","keyFile"}]}
 * 导入后两个文件都会被删除；私钥只留在 app 私有目录。主机指纹仍然要在界面上确认（不做 TOFU）。
 */
object DebugHooks {
    fun onAppStart(app: Application, container: AppContainer) {
        val dir = app.getExternalFilesDir(null) ?: return
        val f = File(dir, "provision.json")
        if (!f.exists()) return
        runCatching {
            val arr = JSONObject(f.readText()).getJSONArray("hosts")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cfg = HostRepository.HostConfig(
                    id = o.getString("id"),
                    label = o.optString("label", o.getString("host")),
                    host = o.getString("host"),
                    port = o.optInt("port", 22),
                    user = o.getString("user"),
                    session = o.optString("session", "test"),
                    herdrBin = o.optString("herdrBin", "/home/${o.getString("user")}/.local/bin/herdr"),
                )
                container.hosts.upsert(cfg)
                o.optString("keyFile").takeIf { it.isNotBlank() }?.let { name ->
                    val k = File(dir, name)
                    if (k.exists()) {
                        container.hosts.importKey(cfg.id, k.readText()).onFailure { Log.w("Provision", "key ${cfg.id}: ${it.message}") }
                        k.delete()
                    }
                }
                Log.i("Provision", "imported host ${cfg.id} (${cfg.label})")
            }
        }.onFailure { Log.w("Provision", "failed: ${it.message}") }
        f.delete()
    }
}
