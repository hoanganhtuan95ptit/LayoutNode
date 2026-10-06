package com.simple.ui.precompute.node

import android.graphics.Canvas
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.PrecomputedRuntime

// ─────────────────────────────────────────────────────────────────────────────
// KeyedNode — bọc một node con và gắn cho nó một [key] ổn định.
//
// Vai trò giống `transitionName` của View trong TransitionManager: đây là thứ
// [TransitionNode] / [TransitionSpec] dùng để match một child giữa hai trạng
// thái layout (cùng key = cùng một logical child → animate ChangeBounds; chỉ
// có ở bên này = enter; chỉ có ở bên kia = exit).
//
// KeyedSpec chỉ là một SizedSpec "trong suốt": vẽ/attach/hit-test uỷ cho child,
// nhưng expose [node] = chính KeyedNode nên `spec.node?.id` trả về [key].
// ─────────────────────────────────────────────────────────────────────────────

data class KeyedNode(
    val key: Any,
    val child: LayoutNode,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
    override val onTouch: ((NodeTouch) -> Boolean)? = null,
    override val contentDescription: String? = null,
    override val padding: EdgeInsets = EdgeInsets.ZERO
) : LayoutNode() {

    override val id: Any get() = key

    override val layoutWidth: LayoutDimension get() = child.layoutWidth

    override val layoutHeight: LayoutDimension get() = child.layoutHeight

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): DrawSpec {

        // Đo child tại local (0,0) rồi để KeyedSpec giữ vị trí ngoài — giống
        // SizedSpec. Nhờ vậy withPosition chỉ dời KeyedSpec, child vẫn ở (0,0).
        val childSpec = ctx.measure(child, c, 0, 0)
        return KeyedSpec(x, y, childSpec.width, childSpec.height, childSpec, this)
    }
}

open class KeyedSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    private val child: DrawSpec,
    override val node: LayoutNode
) : DrawSpec() {

    override fun onDrawContent(canvas: Canvas) {

        child.draw(canvas)
    }

    override fun forEachChildSpec(action: (DrawSpec) -> Unit) = action(child)

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {

        child.attach(runtime, runtimeLeft, runtimeTop)
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {

        child.detach(runtime)
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec {

        if (newLeft == left && newTop == top) return this
        return KeyedSpec(newLeft, newTop, width, height, child, node)
    }

    override fun hitTest(x: Int, y: Int): DrawSpec? {

        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null

        val hit = child.hitTest(lx, ly)
        if (hit != null) return hit
        return if (node.isInteractive) this else null
    }
}
