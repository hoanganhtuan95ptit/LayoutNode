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

    /**
     * Đo [node] thành cây [DrawSpec]. Hàm THUẦN, không trạng thái, KHÔNG CACHE:
     * cùng (node, constraints) luôn ra cùng kết quả; không giữ lại spec nào giữa
     * các lần gọi (tránh rò rỉ bộ nhớ). Mỗi lần rebuild = đo lại toàn bộ cây.
     * Phải chạy trên background thread.
     */
    fun measure(
        node: LayoutNode,
        constraints: Constraints
    ): DrawSpec {

        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            error("luồng tính toán cần phải xử lý ở background")
        }

        return MeasureContext().measure(node, constraints, 0, 0)
    }
}

/**
 * Context truyền xuống [LayoutNode.measure] để node container (vd Linear)
 * có thể đệ quy đo các child mà không cần biết concrete type. Không giữ state.
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
