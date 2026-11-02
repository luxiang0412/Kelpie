package dev.herdr.signal.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PKG = "dev.herdr.signal.min"

@RunWith(AndroidJUnit4::class)
class SignalBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    /** 冷启动：无基线配置 vs 用 App 自带的 baseline-prof.txt。 */
    @Test fun startupNone() = startup(CompilationMode.None())
    @Test fun startupBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 8,
    ) {
        pressHome()
        startActivityAndWait()
    }

    /** 2000 条消息的确定性长对话（FixtureActivity），来回快速滑动。 */
    @Test fun conversationScroll() = rule.measureRepeated(
        packageName = PKG,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(Intent().setClassName(PKG, "dev.herdr.signal.FixtureActivity").putExtra("count", 2000))
        },
    ) {
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        // 按坐标滑：LazyColumn 滚动时节点会重建，拿着 UiObject2 去 fling 会 StaleObjectException
        val x = device.displayWidth / 2
        val top = device.displayHeight / 4
        val bottom = device.displayHeight * 3 / 4
        repeat(3) { device.swipe(x, top, x, bottom, 8); device.waitForIdle() }   // 倒序列表：手指向下滑 = 翻旧消息
        repeat(3) { device.swipe(x, bottom, x, top, 8); device.waitForIdle() }
    }
}
