package com.simple.ui.precompute.node

import android.graphics.Canvas
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy

// ─────────────────────────────────────────────────────────────────────────────
// SpaceNode — node chỉ chiếm chỗ, không vẽ gì.
// Dùng khi cần khoảng trống explicit trong LinearNode / ConstraintNode thay vì
// tăng gap chung cho mọi child.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mô tả một khoảng trống không vẽ nội dung.
 *
 * Mặc định node có kích thước `0x0`. Dùng [horizontal], [vertical], [fixed],
 * hoặc truyền trực tiếp [layoutWidth] / [layoutHeight] để tạo khoảng trống cụ
 * thể theo pixel.
 */
interface SpaceMeasureNode

data class SpaceNode(
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
    override val onTouch: ((NodeTouch) -> Boolean)? = null,
    override val contentDescription: String? = null
) : LayoutNode(), SpaceMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): SpaceSpec =
        SpaceMeasurePolicy<SpaceNode>().measure(this, ctx, c, x, y)

    companion object {
        fun horizontal(widthPx: Int): SpaceNode =
            fixed(widthPx = widthPx, heightPx = 0)

        fun vertical(heightPx: Int): SpaceNode =
            fixed(widthPx = 0, heightPx = heightPx)

        fun fixed(widthPx: Int, heightPx: Int): SpaceNode =
            SpaceNode(
                layoutWidth = LayoutDimension.Fixed(widthPx.coerceAtLeast(0)),
                layoutHeight = LayoutDimension.Fixed(heightPx.coerceAtLeast(0))
            )
    }
}

open class SpaceMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : SpaceMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): SpaceSpec {

        val p = node.padding
        val w = node.layoutWidth.resolve(p.horizontal, c.maxWidth)
        val h = node.layoutHeight.resolve(p.vertical, c.maxHeight)
        return createSpec(node, x, y, w, h)
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): SpaceSpec {

        return SpaceSpec(left, top, width, height, node)
    }
}

/**
 * Kết quả đo của [SpaceNode]. Không vẽ gì, chỉ giữ bounds để parent layout tính
 * đúng kích thước và vị trí.
 */
open class SpaceSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    override val node: LayoutNode
) : DrawSpec() {

    override fun onDrawContent(canvas: Canvas) = Unit

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        SpaceSpec(newLeft, newTop, width, height, node)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec =
        SpaceSpec(left, top, newWidth.coerceAtLeast(0), newHeight.coerceAtLeast(0), node)
}
