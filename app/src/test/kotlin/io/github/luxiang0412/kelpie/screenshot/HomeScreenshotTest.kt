package io.github.luxiang0412.kelpie.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.luxiang0412.kelpie.core.data.Approvals
import io.github.luxiang0412.kelpie.core.data.attention.AgentItem
import io.github.luxiang0412.kelpie.core.data.attention.AgentKey
import io.github.luxiang0412.kelpie.core.data.attention.HomeState
import io.github.luxiang0412.kelpie.core.data.attention.HostInput
import io.github.luxiang0412.kelpie.core.data.attention.HostReach
import io.github.luxiang0412.kelpie.core.data.runtime.ApprovalItem
import io.github.luxiang0412.kelpie.core.data.runtime.BlockedPreview
import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane
import io.github.luxiang0412.kelpie.core.designsystem.KelpieTheme
import io.github.luxiang0412.kelpie.core.designsystem.ThemeMode
import io.github.luxiang0412.kelpie.home.HomeScreen
import io.github.luxiang0412.kelpie.home.HomeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页截图回归（Roborazzi + Robolectric，JVM 上跑，不需要设备）。
 * 固定时间、固定数据；基准图在 app/src/test/screenshots/。
 *   录制：./gradlew :app:recordRoborazziDebug
 *   对比：./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_793_640_000_000L

    // 时间显示用本机时区；固定成 UTC，换台机器跑基准图也一致
    @org.junit.Before fun fixedEnv() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(java.util.Locale.ENGLISH)
    }

    @org.junit.After fun restore() = io.github.luxiang0412.kelpie.core.herdr.L10n.update(java.util.Locale.getDefault())

    private fun pane(id: String, agent: String, title: String, status: AgentStatus) = Pane(
        paneId = id, terminalId = null, workspaceId = "w1", tabId = "w1:t1", agent = agent,
        status = status, title = title, cwd = "/home/dev/project", revision = 1,
    )

    private fun item(id: String, agent: String, title: String, status: AgentStatus, sinceMin: Long, host: String = "MS-7E07") =
        AgentItem(AgentKey("h1", id), host, pane(id, agent, title, status), status, now - sinceMin * 60_000, false, null, false)

    private val hosts = listOf(HostInput("h1", "MS-7E07", HostReach.Online, emptyList(), now - 2_000))

    private fun shot(name: String, mode: ThemeMode, ui: io.github.luxiang0412.kelpie.home.HomeUi) {
        compose.setContent {
            KelpieTheme(mode) { HomeScreen(ui, onOpen = { _, _ -> }, onOpenApproval = {}, onSettings = {}, onAddHost = {}, onRetry = {}) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun busy(): io.github.luxiang0412.kelpie.home.HomeUi {
        val blocked = item("p1", "pi", "refactor-auth", AgentStatus.BLOCKED, 7)
        val state = HomeState(
            needsYou = listOf(blocked),
            working = listOf(item("p2", "claude", "Running tests", AgentStatus.WORKING, 3), item("p3", "codex", "docs-site", AgentStatus.WORKING, 12)),
            recentlyDone = listOf(item("p4", "pi", "spike-sandbox", AgentStatus.DONE, 1)),
            quiet = listOf(item("p5", "pi", "notes", AgentStatus.IDLE, 90)),
            hosts = hosts, noHosts = false, loading = false,
        )
        val approval = ApprovalItem(
            "h1", "MS-7E07",
            Approvals.Request(
                hostId = "h1", requestId = "toolu_01", nonce = "n", question = "Run the unit tests",
                createdAtMs = now - 20_000, expiresAtMs = now + 100_000, path = "/x", paneId = "p2", tool = "Bash",
                command = "./gradlew test --console=plain",
            ),
        )
        val previews = mapOf(blocked.key to BlockedPreview("要把旧的 session 表直接删掉，还是先迁移数据再删？", BlockedPreview.Source.Transcript))
        return HomeViewModel.build(state, previews, listOf(approval), emptyMap(), emptyMap(), phoneOnline = true, now = now)
    }

    @Test fun busyLight() = shot("home_busy_light", ThemeMode.Light, busy())

    @Test fun busyLightZh() {
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(java.util.Locale.SIMPLIFIED_CHINESE)
        shot("home_busy_light_zh", ThemeMode.Light, busy())
    }
    @Test fun busyDark() = shot("home_busy_dark", ThemeMode.Dark, busy())

    @Test fun phoneOffline() {
        val state = HomeState(
            needsYou = emptyList(), working = listOf(item("p2", "claude", "Running tests", AgentStatus.WORKING, 3)),
            recentlyDone = emptyList(), quiet = emptyList(), hosts = hosts, noHosts = false, loading = false,
        )
        io.github.luxiang0412.kelpie.core.herdr.L10n.update(java.util.Locale.SIMPLIFIED_CHINESE)
        shot("home_phone_offline_zh", ThemeMode.Light, HomeViewModel.build(state, emptyMap(), emptyList(), emptyMap(), emptyMap(), phoneOnline = false, now = now))
    }

    @Test fun allClear() {
        val state = HomeState(emptyList(), emptyList(), emptyList(), listOf(item("p5", "pi", "notes", AgentStatus.IDLE, 90)), hosts, noHosts = false, loading = false)
        shot("home_all_clear", ThemeMode.Light, HomeViewModel.build(state, emptyMap(), emptyList(), emptyMap(), emptyMap(), phoneOnline = true, now = now))
    }
}
