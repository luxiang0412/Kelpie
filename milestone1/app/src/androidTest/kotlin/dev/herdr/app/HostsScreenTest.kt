package dev.herdr.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.herdr.core.data.HostConnectionManager
import dev.herdr.core.data.HostRepository
import dev.herdr.feature.board.HerdrTheme
import dev.herdr.feature.board.HostsScreen
import dev.herdr.feature.board.HostsViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 回归：**有主机**时打开 Hosts 页不得崩溃。
 * 之前 AVD 没有配置主机，只覆盖了空态，导致 `LazyColumn` 嵌在 `verticalScroll` 里的崩溃逃过测试。
 */
@RunWith(AndroidJUnit4::class)
class HostsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<TestHostActivity>()

    private fun state(hosts: List<HostsViewModel.HostState>) = HostsViewModel.UiState(
        hosts = hosts,
        activeId = hosts.firstOrNull()?.cfg?.id,
    )

    private fun host(id: String, label: String) = HostsViewModel.HostState(
        cfg = HostRepository.HostConfig(id = id, label = label, host = "192.168.5.8", user = "u", session = "test"),
        hasKey = true,
        connKind = HostConnectionManager.ConnKind.CONNECTED,
        connText = "已连接",
        paneCount = 2,
    )

    @Test
    fun withHosts_rendersCardsAndSettingsWithoutCrash() {
        rule.setContent {
            HerdrTheme {
                HostsScreen(
                    state = state(listOf(host("h1", "开发机"), host("h2", "第二台"))),
                    onSave = {},
                    onDelete = {},
                    onImportKey = { _, _ -> Result.success(Unit) },
                    onDeleteKey = {},
                    onConnect = {},
                    onDisconnect = {},
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("开发机", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("第二台", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun emptyHosts_stillShowsLanguageSection() {
        rule.setContent {
            HerdrTheme {
                HostsScreen(
                    state = state(emptyList()),
                    onSave = {},
                    onDelete = {},
                    onImportKey = { _, _ -> Result.success(Unit) },
                    onDeleteKey = {},
                    onConnect = {},
                    onDisconnect = {},
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Language", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("语言", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
}
