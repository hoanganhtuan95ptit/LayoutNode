package com.simple.t

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import androidx.annotation.RequiresApi
import androidx.core.app.FrameMetricsAggregator
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simple.ui.precompute.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrecomputeRecyclerBenchmarkTest {

    @Test
    @RequiresApi(Build.VERSION_CODES.N)
    fun scrollRecyclerAndReportFrameMetrics() {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchActivity() as MainActivity
        val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerView)

        waitForItems(activity, recyclerView)
        instrumentation.waitForIdleSync()

        val aggregator = FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION)
        aggregator.add(activity)

        runScrollScenario(activity, recyclerView)
        instrumentation.waitForIdleSync()

        val metrics = aggregator.remove(activity)
        val totalMetrics = metrics?.get(FrameMetricsAggregator.TOTAL_INDEX) ?: SparseIntArray()
        val report = buildReport(recyclerView, totalMetrics)

        Log.i(TAG, report)
        instrumentation.sendStatus(0, Bundle().apply {

            putString("precompute_recycler_benchmark", report)
        })

        assertTrue("No frame metrics were captured", totalMetrics.totalCount() > 0)
    }

    private fun launchActivity(): Activity {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent)
    }

    private fun waitForItems(activity: Activity, recyclerView: RecyclerView) {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(100) {

            var count = 0
            instrumentation.runOnMainSync {

                count = recyclerView.adapter?.itemCount ?: 0
            }
            if (count > 0) return
            SystemClock.sleep(50)
        }
        error("RecyclerView items were not ready")
    }

    private fun runScrollScenario(activity: Activity, recyclerView: RecyclerView) {

        repeat(4) {

            smoothScroll(activity, recyclerView, recyclerView.height * 3)
            SystemClock.sleep(1_300)
        }

        repeat(4) {

            smoothScroll(activity, recyclerView, -recyclerView.height * 3)
            SystemClock.sleep(1_300)
        }
    }

    private fun smoothScroll(activity: Activity, recyclerView: RecyclerView, dy: Int) {

        InstrumentationRegistry.getInstrumentation().runOnMainSync {

            recyclerView.smoothScrollBy(0, dy)
        }
    }

    private fun buildReport(recyclerView: RecyclerView, totalMetrics: SparseIntArray): String {

        val runtime = Runtime.getRuntime()
        val frames = totalMetrics.totalCount()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()

        return buildString {

            append("{")
            appendNumber("itemCount", recyclerView.adapter?.itemCount ?: 0)
            appendNumber("frames", frames)
            appendNumber("jankyFramesOver16Ms", totalMetrics.countGreaterThan(16))
            appendNumber("slowFramesOver32Ms", totalMetrics.countGreaterThan(32))
            appendNumber("frozenFramesOver700Ms", totalMetrics.countGreaterThan(700))
            appendNumber("frameMsP50", totalMetrics.percentile(50))
            appendNumber("frameMsP90", totalMetrics.percentile(90))
            appendNumber("frameMsP95", totalMetrics.percentile(95))
            appendNumber("frameMsP99", totalMetrics.percentile(99))
            appendNumber("usedHeapBytes", usedHeap)
            appendNumber("maxHeapBytes", runtime.maxMemory(), last = true)
            append("}")
        }
    }

    private fun SparseIntArray.totalCount(): Int {

        var total = 0
        for (i in 0 until size()) {

            total += valueAt(i)
        }
        return total
    }

    private fun SparseIntArray.countGreaterThan(thresholdMs: Int): Int {

        var total = 0
        for (i in 0 until size()) {

            if (keyAt(i) > thresholdMs) total += valueAt(i)
        }
        return total
    }

    private fun SparseIntArray.percentile(percentile: Int): Int {

        val total = totalCount()
        if (total == 0) return 0

        val target = ((total * percentile) + 99) / 100
        var running = 0
        for (i in 0 until size()) {

            running += valueAt(i)
            if (running >= target) return keyAt(i)
        }
        return keyAt(size() - 1)
    }

    private fun StringBuilder.appendNumber(name: String, value: Number, last: Boolean = false) {

        append("\"")
        append(name)
        append("\":")
        append(value)
        if (!last) append(",")
    }

    private companion object {

        const val TAG = "PrecomputeBenchmark"
    }
}
