package com.simple.ui.precompute.node

import android.graphics.Canvas
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy
import com.simple.ui.precompute.PrecomputedRuntime

// ─────────────────────────────────────────────────────────────────────────────
// LinearNode — mô tả một container xếp children theo chiều ngang / dọc.
// GroupSpec  — kết quả sau khi đo: danh sách DrawSpec con đã gán vị trí.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mô tả một container xếp [children] tuần tự theo [orientation].
 *
 * - [gap]: khoảng cách giữa các children trên trục chính.
 * - [crossAlign]: căn chỉnh trên trục phụ (START / CENTER / END).
 * - [padding]: padding bao ngoài toàn bộ container.
 *
 * Đo 2 lượt:
 *   1. Pass 1 — đo tất cả children để biết cross size lớn nhất.
 *   2. Pass 2 — gán vị trí với cross-align qua [com.simple.ui.precompute.DrawSpec.withPosition].
 */
interface LinearMeasureNode {

    val orientation: Orientation
    val children: List<LayoutNode>
    val gap: Int
    val crossAlign: CrossAlign
}

data class LinearNode(
    override val orientation: Orientation,
    override val children: List<LayoutNode>,
    override val gap: Int = 0,
    override val crossAlign: CrossAlign = CrossAlign.START,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent
) : LayoutNode(), LinearMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GroupSpec =
        LinearMeasurePolicy<LinearNode>().measure(this, ctx, c, x, y)
}

open class LinearMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : LinearMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GroupSpec {

        val p = node.padding
        val measureMaxW = node.layoutWidth.maxForMeasure(c.maxWidth)
        val measureMaxH = node.layoutHeight.maxForMeasure(c.maxHeight)
        val innerMaxW = (measureMaxW - p.horizontal).coerceAtLeast(0)
        val innerMaxH = (measureMaxH - p.vertical).coerceAtLeast(0)

        // 1st pass: đo mọi child, chưa cần biết vị trí.
        val measured = ArrayList<DrawSpec>(node.children.size)
        var mainUsed = 0
        var crossMax = 0

        node.children.forEachIndexed { i, child ->
            val cc = when (node.orientation) {
                Orientation.HORIZONTAL ->
                    Constraints((innerMaxW - mainUsed).coerceAtLeast(0), innerMaxH)
                Orientation.VERTICAL ->
                    Constraints(innerMaxW, (innerMaxH - mainUsed).coerceAtLeast(0))
            }
            val s = ctx.measure(child, cc, 0, 0)
            measured.add(s)

            val mainSize = if (node.orientation == Orientation.HORIZONTAL) s.width else s.height
            val crossSize = if (node.orientation == Orientation.HORIZONTAL) s.height else s.width
            mainUsed += mainSize
            if (i < node.children.lastIndex) mainUsed += node.gap
            if (crossSize > crossMax) crossMax = crossSize
        }

        // 2nd pass: gán vị trí dựa trên cross-align — dùng withPosition(),
        // không cần biết concrete type.
        val placed = ArrayList<DrawSpec>(measured.size)
        var cursor = 0
        val naturalMain = if (measured.isEmpty()) 0 else mainUsed
        val naturalW = when (node.orientation) {
            Orientation.HORIZONTAL -> naturalMain + p.horizontal
            Orientation.VERTICAL -> crossMax + p.horizontal
        }
        val naturalH = when (node.orientation) {
            Orientation.HORIZONTAL -> crossMax + p.vertical
            Orientation.VERTICAL -> naturalMain + p.vertical
        }
        val w = node.layoutWidth.resolve(naturalW, c.maxWidth)
        val h = node.layoutHeight.resolve(naturalH, c.maxHeight)
        val crossSlot = when (node.orientation) {
            Orientation.HORIZONTAL -> (h - p.vertical).coerceAtLeast(0)
            Orientation.VERTICAL -> (w - p.horizontal).coerceAtLeast(0)
        }

        for (s in measured) {
            val (cx, cy) = when (node.orientation) {
                Orientation.HORIZONTAL -> {
                    val offCross = crossOffset(crossSlot, s.height, node.crossAlign)
                    Pair(p.left + cursor, p.top + offCross)
                }
                Orientation.VERTICAL -> {
                    val offCross = crossOffset(crossSlot, s.width, node.crossAlign)
                    Pair(p.left + offCross, p.top + cursor)
                }
            }
            placed.add(s.withPosition(cx, cy))

            val mainSize = if (node.orientation == Orientation.HORIZONTAL) s.width else s.height
            cursor += mainSize + node.gap
        }
        if (placed.isNotEmpty()) cursor -= node.gap

        return createSpec(node, x, y, w, h, placed)
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        children: List<DrawSpec>
    ): GroupSpec {

        return GroupSpec(left, top, width, height, children, node)
    }

    protected open fun crossOffset(parent: Int, child: Int, align: CrossAlign): Int =
        when (align) {
            CrossAlign.START -> 0
            CrossAlign.CENTER -> (parent - child).coerceAtLeast(0) / 2
            CrossAlign.END -> (parent - child).coerceAtLeast(0)
        }
}

/**
 * Kết quả đo của [LinearNode]. Đệ quy: mỗi child tự [draw] trong toạ độ
 * đã translate của GroupSpec — không cần biết concrete type của child.
 */
open class GroupSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val children: List<DrawSpec>,
    override val node: LayoutNode
) : DrawSpec() {

    override fun onDrawContent(canvas: Canvas) {
        for (i in children.indices) children[i].draw(canvas)
    }

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {
        for (i in children.indices) children[i].attach(runtime, runtimeLeft, runtimeTop)
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {
        for (i in children.indices) children[i].detach(runtime)
    }

    override fun withPosition(newLeft: Int, newTop: Int) =
        GroupSpec(newLeft, newTop, width, height, children, node)

    override fun hitTest(x: Int, y: Int): DrawSpec? {
        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null
        // Duyệt ngược: child vẽ sau (topmost) thắng.
        for (i in children.indices.reversed()) {
            val hit = children[i].hitTest(lx, ly)
            if (hit != null) return hit
        }
        return if (node.onClick != null) this else null
    }
}
