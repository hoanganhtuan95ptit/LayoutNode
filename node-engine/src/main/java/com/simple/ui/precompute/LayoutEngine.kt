package com.simple.ui.precompute

import android.os.Looper
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.LayoutNode

/**
 * Pure measurement engine. No View, no Context, no main-thread APIs.
 * Safe to call from Dispatchers.Default.
 *
 * ## Basic
 *
 *   val spec = LayoutEngine.measure(node, Constraints(screenWidth))
 */
object LayoutEngine {

    fun measure(
        node: LayoutNode,
        constraints: Constraints,
        id: Any? = null
    ): DrawSpec {

        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            error("luồng tính toán cần phải xử lý ở background")
        }

        val ctx = MeasureContext()
        return ctx.measure(node, constraints, 0, 0)
    }

    /**
     * Giữ lại để tương thích ngược API (không còn lưu cache).
     */
    fun evict(id: Any) {
    }

    /**
     * Giữ lại để tương thích ngược API (không còn lưu cache).
     */
    fun clearCache() {
    }
}

/**
 * Context truyền xuống [LayoutNode.measure] để node container (vd Linear)
 * có thể đệ quy đo các child mà không cần biết concrete type.
 */
class MeasureContext {

    fun measure(
        node: LayoutNode,
        c: Constraints,
        x: Int = 0,
        y: Int = 0
    ): DrawSpec {

        return node.measure(this, c, x, y)
    }
}
