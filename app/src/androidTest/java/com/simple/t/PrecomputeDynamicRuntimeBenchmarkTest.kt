package com.simple.t

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.SparseIntArray
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simple.ui.precompute.LayoutEngine
import com.simple.ui.precompute.MainActivity
import com.simple.ui.precompute.PrecomputedView
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.node.ConstraintChild
import com.simple.ui.precompute.node.ConstraintNode
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.OutlineNode
import com.simple.ui.precompute.node.OutlineState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrecomputeDynamicRuntimeBenchmarkTest {

    @Test
    @RequiresApi(Build.VERSION_CODES.N)
    fun drawManyDynamicSpecsAndReportFrameMetrics() {

        runBenchmark(dashed = true)
    }

    @Test
    @RequiresApi(Build.VERSION_CODES.N)
    fun drawManyDynamicSpecsWithoutDashAndReportFrameMetrics() {

        runBenchmark(dashed = false)
    }

    private fun runBenchmark(dashed: Boolean) {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchActivity() as MainActivity
        val density = activity.resources.displayMetrics.density
        val width = activity.resources.displayMetrics.widthPixels
        val height = (360 * density).toInt()
        val spec = LayoutEngine.measure(
            createStressNode(width, height, density, dashed),
            Constraints(width, height)
        )

        instrumentation.runOnMainSync {

            activity.setContentView(createStressView(activity, width, height, spec))
        }
        instrumentation.waitForIdleSync()

        val aggregator = FrameMetricsAggregator(FrameMetricsAggregator.TOTAL_DURATION)
        aggregator.add(activity)

        SystemClock.sleep(MEASURE_DURATION_MS)
        instrumentation.waitForIdleSync()

        val metrics = aggregator.remove(activity)
        val totalMetrics = metrics?.get(FrameMetricsAggregator.TOTAL_INDEX) ?: SparseIntArray()
        val report = buildReport(totalMetrics, dashed)

        Log.i(TAG, report)
        instrumentation.sendStatus(0, Bundle().apply {

            putString("precompute_dynamic_benchmark", report)
        })

        assertTrue("No dynamic frame metrics were captured", totalMetrics.totalCount() > 0)
    }

    private fun launchActivity(): Activity {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return instrumentation.startActivitySync(intent)
    }

    private fun createStressView(
        activity: Activity,
        width: Int,
        height: Int,
        spec: DrawSpec
    ): PrecomputedView {

        val view = PrecomputedView(activity)
        view.layoutParams = FrameLayout.LayoutParams(width, height)
        view.setBackgroundColor(Color.WHITE)
        view.spec = spec
        return view
    }

    private fun createStressNode(
        width: Int,
        height: Int,
        density: Float,
        dashed: Boolean
    ): ConstraintNode {

        return ConstraintNode(
            children = List(DYNAMIC_SPEC_COUNT) { index ->

                val inset = ((index % 10) * 3 * density).toInt()
                ConstraintChild(
                    id = "outline-$index",
                    node = OutlineNode(
                        backgroundColor = Color.TRANSPARENT,
                        strokeColor = COLORS[index % COLORS.size],
                        strokeWidth = density,
                        cornerRadius = (12 * density) + inset,
                        dashWidth = if (dashed) (8 * density) + index else 0f,
                        dashGap = if (dashed) (6 * density) + (index % 5) else 0f,
                        loadingSegmentRatio = 0.2f + ((index % 5) * 0.08f),
                        loadingDurationMs = 900L + (index * 37L),
                        state = OutlineState.LOADING,
                        padding = EdgeInsets.all(inset),
                        layoutWidth = LayoutDimension.MatchParent,
                        layoutHeight = LayoutDimension.MatchParent
                    ),
                    startToStartOf = ConstraintNode.PARENT,
                    endToEndOf = ConstraintNode.PARENT,
                    topToTopOf = ConstraintNode.PARENT,
                    bottomToBottomOf = ConstraintNode.PARENT,
                    width = LayoutDimension.MatchParent,
                    height = LayoutDimension.MatchParent
                )
            },
            layoutWidth = LayoutDimension.Fixed(width),
            layoutHeight = LayoutDimension.Fixed(height)
        )
    }

    private fun buildReport(totalMetrics: SparseIntArray, dashed: Boolean): String {

        val runtime = Runtime.getRuntime()
        val frames = totalMetrics.totalCount()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()

        return buildString {

            append("{")
            appendBoolean("dashed", dashed)
            appendNumber("dynamicSpecCount", DYNAMIC_SPEC_COUNT)
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

    private fun StringBuilder.appendBoolean(name: String, value: Boolean, last: Boolean = false) {

        append("\"")
        append(name)
        append("\":")
        append(value)
        if (!last) append(",")
    }

    private companion object {

        const val TAG = "PrecomputeDynamicBenchmark"
        const val DYNAMIC_SPEC_COUNT = 30
        const val MEASURE_DURATION_MS = 5_000L

        val COLORS = intArrayOf(
            0xFFE91E63.toInt(),
            0xFF4CAF50.toInt(),
            0xFF2196F3.toInt(),
            0xFFFF9800.toInt(),
            0xFF6200EE.toInt()
        )
    }
}
