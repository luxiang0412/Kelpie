package io.github.luxiang0412.kelpie.core.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 主机启用 / 停用：持久化、不改顺序、旧配置默认启用。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostRepositoryTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private fun host(id: String) = HostRepository.HostConfig(id = id, label = id, host = "$id.lan", user = "u")

    @Test
    fun `disable keeps the host, its order and survives a reload`() {
        val repo = HostRepository(ctx)
        listOf("a", "b", "c").forEach { repo.upsert(host(it)) }
        repo.setEnabled("b", false)
        assertEquals(listOf("a", "b", "c"), repo.list().map { it.id })
        assertFalse(repo.get("b")!!.enabled)
        assertFalse(repo.hosts.value.first { it.id == "b" }.enabled)
        // 重新读取（相当于重启 App）
        assertFalse(HostRepository(ctx).get("b")!!.enabled)
        repo.setEnabled("b", true)
        assertTrue(HostRepository(ctx).get("b")!!.enabled)
    }

    @Test
    fun `hosts saved before the flag existed are enabled`() {
        ctx.getSharedPreferences("herdr-hosts", Context.MODE_PRIVATE).edit()
            .putString("hosts", """[{"id":"old","label":"old","host":"10.0.0.1","port":22,"user":"u","session":"test"}]""")
            .commit()
        assertTrue(HostRepository(ctx).get("old")!!.enabled)
    }
}
