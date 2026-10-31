package dev.herdr.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 对话页性能基准（M2-A）。
 *
 * 数据集来自 app 的 debug Fixture（dev.herdr.app.FixtureActivity，count = 100/500/2000），
 * 确定性生成、不依赖真实 agent。
 *
 * 运行前提：设备允许 instrumentation 启动目标 App 的 Activity。
 * 本机（MIUI）会因后台启动限制失败，见 docs/design/M2-A-REPORT.md。
 */
@RunWith(AndroidJUnit4::class)
class ConversationBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun fixture(count: Int) = Intent().apply {
        setClassName("dev.herdr.app", "dev.herdr.app.FixtureActivity")
        putExtra("count", count)
    }

    private fun scroll(mode: CompilationMode, count: Int, swipes: Int) = rule.measureRepeated(
        packageName = "dev.herdr.app",
        metrics = listOf(FrameTimingMetric(), StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait(fixture(count))
        val device: UiDevice = device
        device.wait(Until.hasObject(By.pkg("dev.herdr.app")), 5_000)
        repeat(swipes) {
            device.swipe(540, 1800, 540, 800, 12)
        }
    }

    @Test
    fun scroll_100() = scroll(CompilationMode.None(), 100, 6)

    @Test
    fun scroll_500() = scroll(CompilationMode.None(), 500, 10)

    @Test
    fun scroll_2000() = scroll(CompilationMode.None(), 2000, 14)

    @Test
    fun startup_500() = rule.measureRepeated(
        packageName = "dev.herdr.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.None(),
    ) {
        pressHome()
        startActivityAndWait(fixture(500))
    }
}
