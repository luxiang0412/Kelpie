package dev.herdr.signal

import android.app.Activity
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState

/**
 * 应用内掉帧统计（JankStats）：按“当前界面”分桶计数，Activity 停止时打一行汇总到 logcat：
 *   SignalJank: screen=conversation frames=812 jank=9 (1.1%) worst=41ms
 * 只计数，不上报；界面名由 [screen] 设置（导航层在路由变化时更新）。
 */
object JankMonitor {
    private class Bucket { var frames = 0; var jank = 0; var worstMs = 0L }
    private val buckets = LinkedHashMap<String, Bucket>()
    @Volatile private var current = "unknown"
    private var stats: JankStats? = null

    fun screen(name: String) { current = name }

    fun <A> attach(activity: A, initial: String) where A : Activity, A : LifecycleOwner {
        current = initial
        val s = JankStats.createAndTrack(activity.window) { f ->
            synchronized(buckets) {
                val b = buckets.getOrPut(current) { Bucket() }
                b.frames++
                if (f.isJank) b.jank++
                val ms = f.frameDurationUiNanos / 1_000_000
                if (ms > b.worstMs) b.worstMs = ms
            }
        }
        stats = s
        PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { s.isTrackingEnabled = true }
            override fun onPause(owner: LifecycleOwner) { s.isTrackingEnabled = false }
            override fun onStop(owner: LifecycleOwner) = dump()
        })
    }

    fun dump() = synchronized(buckets) {
        buckets.forEach { (k, b) ->
            val pct = if (b.frames == 0) 0.0 else b.jank * 100.0 / b.frames
            Log.i("SignalJank", "screen=$k frames=${b.frames} jank=${b.jank} (${"%.1f".format(pct)}%) worst=${b.worstMs}ms")
        }
        buckets.clear()
    }
}
