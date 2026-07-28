package com.simple.ui.precompute.node

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy

data class BackgroundData(
    val backgroundColor: Int = Color.TRANSPARENT,
    val strokeColor: Int = Color.BLACK,
    val strokeWidth: Float = 1f,
    val cornerRadius: Float = 0f,
    val dashWidth: Float = 0f,
    val dashGap: Float = 0f
)

interface BackgroundMeasureNode {

    val backgroundColor: Int
    val strokeColor: Int
    val strokeWidth: Float
    val cornerRadius: Float
    val dashWidth: Float
    val dashGap: Float
}

data class BackgroundNode(
    override val backgroundColor: Int = Color.TRANSPARENT,
    override val strokeColor: Int = Color.TRANSPARENT,
    override val strokeWidth: Float = 0f,
    override val cornerRadius: Float = 0f,
    override val dashWidth: Float = 0f,
    override val dashGap: Float = 0f,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val onClick: (() -> Unit)? = null
) : LayoutNode(), BackgroundMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): BackgroundSpec =
        BackgroundMeasurePolicy<BackgroundNode>().measure(this, ctx, c, x, y)
}

open class BackgroundMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : BackgroundMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): BackgroundSpec {

        val p = node.padding
        val w = node.layoutWidth.resolve(p.horizontal, c.maxWidth)
        val h = node.layoutHeight.resolve(p.vertical, c.maxHeight)
        return createSpec(node, x, y, w, h, p)
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        padding: EdgeInsets
    ): BackgroundSpec {

        return BackgroundSpec(
            left = left,
            top = top,
            width = width,
            height = height,
            padding = padding,
            backgroundColor = node.backgroundColor,
            strokeColor = node.strokeColor,
            strokeWidth = node.strokeWidth,
            cornerRadius = node.cornerRadius,
            dashWidth = node.dashWidth,
            dashGap = node.dashGap,
            node = node
        )
    }
}

open class BackgroundSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val padding: EdgeInsets,
    backgroundColor: Int,
    strokeColor: Int,
    strokeWidth: Float,
    cornerRadius: Float,
    dashWidth: Float,
    dashGap: Float,
    override val node: LayoutNode
) : DrawSpec() {

    var backgroundColor: Int = backgroundColor
        set(value) {
            field = value
            fillPaint.color = value
        }

    var strokeColor: Int = strokeColor
        set(value) {
            field = value
            strokePaint.color = value
        }

    var strokeWidth: Float = strokeWidth.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            strokePaint.strokeWidth = field
            updateRect()
        }

    var cornerRadius: Float = cornerRadius.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updateRect()
        }

    var dashWidth: Float = dashWidth.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updateDashEffect()
        }

    var dashGap: Float = dashGap.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updateDashEffect()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@BackgroundSpec.backgroundColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = this@BackgroundSpec.strokeColor
        setStrokeWidth(this@BackgroundSpec.strokeWidth)
    }

    private val rect = RectF()
    private var radius = 0f

    init {
        updateDashEffect()
        updateRect()
    }

    override fun onDrawContent(canvas: Canvas) {
        if (rect.width() <= 0f || rect.height() <= 0f) return

        if (Color.alpha(backgroundColor) != 0) {
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }

        if (strokeWidth > 0f) {
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
        }
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        copyTo(newLeft, newTop, width, height)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec =
        copyTo(left, top, newWidth.coerceAtLeast(0), newHeight.coerceAtLeast(0))

    private fun updateRect() {
        if (width <= 0 || height <= 0) {
            rect.setEmpty()
            radius = 0f
            return
        }

        val inset = if (strokeWidth > 0f) strokeWidth / 2f else 0f
        rect.set(
            padding.left + inset,
            padding.top + inset,
            width - padding.right - inset,
            height - padding.bottom - inset
        )

        val maxRadius = minOf(rect.width(), rect.height()) / 2f
        radius = cornerRadius.coerceIn(0f, maxRadius)
    }

    private fun updateDashEffect() {
        strokePaint.pathEffect = if (dashWidth > 0f && dashGap > 0f) {
            DashPathEffect(floatArrayOf(dashWidth, dashGap), 0f)
        } else {
            null
        }
    }

    private fun copyTo(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int): BackgroundSpec =
        BackgroundSpec(
            left = newLeft,
            top = newTop,
            width = newWidth,
            height = newHeight,
            padding = padding,
            backgroundColor = backgroundColor,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            cornerRadius = cornerRadius,
            dashWidth = dashWidth,
            dashGap = dashGap,
            node = node
        )
}
