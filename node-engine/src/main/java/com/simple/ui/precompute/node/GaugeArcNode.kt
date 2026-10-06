package com.simple.ui.precompute.node

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// GaugeArcNode — vẽ một vòng cung gauge tròn:
//   - track ring (nền) phủ toàn bộ 360°,
//   - progress arc bắt đầu từ -90° (12 giờ), quét theo chiều kim đồng hồ.
// GaugeArcSpec — kết quả sau khi đo: paints + ring rect được pre-allocate.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mô tả một vòng cung gauge tròn.
 *
 * Mọi màu sắc / kích thước nét phải resolve sẵn (Int, px) trước khi truyền vào —
 * engine không được đụng Context.
 *
 * Mặc định [layoutWidth] / [layoutHeight] = [LayoutDimension.MatchParent] để node
 * chiếm hết slot được cấp; thường dùng cùng [GaugeScoreNode] trong một stack/frame.
 */
interface GaugeArcMeasureNode {

    val progress: Int
    val trackColor: Int
    val progressColor: Int
    val strokeWidthPx: Float
}

data class GaugeArcNode(
    override val progress: Int,
    override val trackColor: Int = 0xFFE6E5DE.toInt(),
    override val progressColor: Int = 0xFF1ED760.toInt(),
    override val strokeWidthPx: Float,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.MatchParent,
    override val layoutHeight: LayoutDimension = LayoutDimension.MatchParent,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
    override val onTouch: ((NodeTouch) -> Boolean)? = null,
    override val contentDescription: String? = null
) : LayoutNode(), GaugeArcMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GaugeArcSpec =
        GaugeArcMeasurePolicy<GaugeArcNode>().measure(this, ctx, c, x, y)
}

open class GaugeArcMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : GaugeArcMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GaugeArcSpec {

        val p = node.padding
        val w = node.layoutWidth.resolve(p.horizontal, c.maxWidth)
        val h = node.layoutHeight.resolve(p.vertical, c.maxHeight)
        return createSpec(
            node = node,
            left = x,
            top = y,
            width = w,
            height = h,
            padding = p,
        )
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        padding: EdgeInsets
    ): GaugeArcSpec {

        return GaugeArcSpec(
            left = left,
            top = top,
            width = width,
            height = height,
            padding = padding,
            progress = node.progress.coerceIn(0, 100),
            trackColor = node.trackColor,
            progressColor = node.progressColor,
            strokeWidthPx = node.strokeWidthPx.coerceAtLeast(0f),
            node = node
        )
    }
}

/**
 * Kết quả đo của [GaugeArcNode]. Paints + [ringRect] đã pre-allocate trong
 * init, [onDrawContent] zero-allocation.
 */
open class GaugeArcSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val padding: EdgeInsets,
    progress: Int,
    trackColor: Int,
    progressColor: Int,
    strokeWidthPx: Float,
    override val node: LayoutNode
) : DrawSpec() {

    var progress: Int = progress.coerceIn(0, 100)
        set(value) {
            field = value.coerceIn(0, 100)
        }

    var trackColor: Int = trackColor
        set(value) {
            field = value
            trackPaint.color = value
        }

    var progressColor: Int = progressColor
        set(value) {
            field = value
            progressPaint.color = value
        }

    var strokeWidthPx: Float = strokeWidthPx.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            trackPaint.strokeWidth = field
            progressPaint.strokeWidth = field
            updateRingRect()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = this@GaugeArcSpec.trackColor
        strokeWidth = this@GaugeArcSpec.strokeWidthPx
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = this@GaugeArcSpec.progressColor
        strokeWidth = this@GaugeArcSpec.strokeWidthPx
    }

    private val ringRect = RectF()

    init {
        updateRingRect()
    }

    private fun updateRingRect() {
        val inset = strokeWidthPx / 2f
        val innerL = padding.left + inset
        val innerT = padding.top + inset
        val innerR = width - padding.right - inset
        val innerB = height - padding.bottom - inset

        val rectW = innerR - innerL
        val rectH = innerB - innerT
        if (rectW <= 0f || rectH <= 0f) {
            ringRect.setEmpty()
            return
        }

        // Ép vòng tròn — chọn cạnh ngắn hơn để tránh oval khi bounds non-square.
        val size = min(rectW, rectH)
        val cx = (innerL + innerR) / 2f
        val cy = (innerT + innerB) / 2f
        val r = size / 2f
        ringRect.set(cx - r, cy - r, cx + r, cy + r)
    }

    override fun onDrawContent(canvas: Canvas) {
        if (ringRect.width() <= 0f || ringRect.height() <= 0f) return

        // Track 360°.
        canvas.drawArc(ringRect, START_ANGLE, SWEEP_TOTAL, false, trackPaint)

        // Progress arc theo % hiện tại.
        if (progress > 0) {
            val sweep = SWEEP_TOTAL * progress / 100f
            canvas.drawArc(ringRect, START_ANGLE, sweep, false, progressPaint)
        }
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        copyTo(newLeft, newTop, width, height)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec =
        copyTo(left, top, newWidth.coerceAtLeast(0), newHeight.coerceAtLeast(0))

    private fun copyTo(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int): GaugeArcSpec =
        GaugeArcSpec(
            left = newLeft,
            top = newTop,
            width = newWidth,
            height = newHeight,
            padding = padding,
            progress = progress,
            trackColor = trackColor,
            progressColor = progressColor,
            strokeWidthPx = strokeWidthPx,
            node = node
        )

    private companion object {
        const val START_ANGLE = -90f
        const val SWEEP_TOTAL = 360f
    }
}
