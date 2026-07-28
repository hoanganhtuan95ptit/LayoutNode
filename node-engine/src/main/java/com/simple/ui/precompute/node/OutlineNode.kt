package com.simple.ui.precompute.node

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy
import com.simple.ui.precompute.PrecomputedRuntime

enum class OutlineState { IDLE, LOADING, HIDDEN }

data class Outline(
    val backgroundColor: Int = Color.TRANSPARENT,
    val strokeColor: Int = Color.BLACK,
    val strokeWidth: Float = 1f,
    val cornerRadius: Float = 0f,
    val dashWidth: Float = 0f,
    val dashGap: Float = 0f,
    val loadingSegmentRatio: Float = 0.5f,
    val loadingDurationMs: Long = 1200L,
    val state: OutlineState = OutlineState.IDLE,
)

/**
 * Draws a rounded outline effect in its measured bounds, including dashed strokes
 * and the loading segment behavior from the legacy OutlineDelegate.
 */
interface OutlineMeasureNode {

    val backgroundColor: Int
    val strokeColor: Int
    val strokeWidth: Float
    val cornerRadius: Float
    val dashWidth: Float
    val dashGap: Float
    val loadingSegmentRatio: Float
    val loadingDurationMs: Long
    val state: OutlineState
}

data class OutlineNode(
    override val backgroundColor: Int = Color.TRANSPARENT,
    override val strokeColor: Int = Color.BLACK,
    override val strokeWidth: Float = 1f,
    override val cornerRadius: Float = 0f,
    override val dashWidth: Float = 0f,
    override val dashGap: Float = 0f,
    override val loadingSegmentRatio: Float = 0.5f,
    override val loadingDurationMs: Long = 1200L,
    override val state: OutlineState = OutlineState.IDLE,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent
) : LayoutNode(), OutlineMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): OutlineSpec =
        OutlineMeasurePolicy<OutlineNode>().measure(this, ctx, c, x, y)
}

open class OutlineMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : OutlineMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): OutlineSpec {

        val p = node.padding
        val naturalW = p.horizontal
        val naturalH = p.vertical
        val w = node.layoutWidth.resolve(naturalW, c.maxWidth)
        val h = node.layoutHeight.resolve(naturalH, c.maxHeight)

        return createSpec(node, x, y, w, h, p)
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        padding: EdgeInsets
    ): OutlineSpec {

        return OutlineSpec(
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
            loadingSegmentRatio = node.loadingSegmentRatio,
            loadingDurationMs = node.loadingDurationMs,
            state = node.state,
            node = node
        )
    }
}

open class OutlineSpec(
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
    loadingSegmentRatio: Float,
    loadingDurationMs: Long,
    state: OutlineState,
    override val node: LayoutNode
) : DrawSpec() {

    var backgroundColor: Int = backgroundColor
        set(value) {
            field = value
            backgroundPaint.color = value
            invalidate()
        }

    var strokeColor: Int = strokeColor
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    var strokeWidth: Float = strokeWidth.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            paint.strokeWidth = field
            updatePath()
            invalidate()
        }

    var cornerRadius: Float = cornerRadius.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updatePath()
            invalidate()
        }

    var dashWidth: Float = dashWidth.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updateDashEffect()
            invalidate()
        }

    var dashGap: Float = dashGap.coerceAtLeast(0f)
        set(value) {
            field = value.coerceAtLeast(0f)
            updateDashEffect()
            invalidate()
        }

    var loadingSegmentRatio: Float = loadingSegmentRatio.coerceIn(0.05f, 1f)
        set(value) {
            field = value.coerceIn(0.05f, 1f)
            if (state == OutlineState.LOADING) {
                targetSegLen = field
                if (internalState == InternalState.LOADING) segLen = field
            }
            invalidate()
        }

    var loadingDurationMs: Long = loadingDurationMs.coerceAtLeast(50L)
        set(value) {
            val coerced = value.coerceAtLeast(50L)
            if (field == coerced) return
            field = coerced
            // Re-register frame callback so the new duration takes effect immediately.
            if (frameRegistration != null) {
                stopAnimating()
                startAnimating()
            }
        }

    var state: OutlineState = state
        private set

    private enum class InternalState { IDLE, LOADING, HIDDEN, SHRINKING, GROWING }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@OutlineSpec.backgroundColor
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = this@OutlineSpec.strokeColor
        setStrokeWidth(this@OutlineSpec.strokeWidth)
    }

    private val fullPath = Path()
    private val segmentPath = Path()
    private val outlineRect = RectF()
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f
    private var outlineRadius = 0f

    private var internalState = state.toInternalState()
    private var settledInternalState = internalState
    private var tailPos = 0f
    private var segLen = state.targetSegmentLength()
    private var targetSegLen = segLen

    private var frameRegistration: PrecomputedRuntime.FrameRegistration? = null
    private var lastFrameMs: Long = 0L
    private val frameCallback = PrecomputedRuntime.FrameCallback { frameTimeNanos ->

        onFrame(frameTimeNanos)
    }

    init {
        updateDashEffect()
        updatePath()
    }

    @JvmOverloads
    fun setLoading(loading: Boolean, show: Boolean = true, animate: Boolean = true) {
        setState(
            when {
                !show -> OutlineState.HIDDEN
                loading -> OutlineState.LOADING
                else -> OutlineState.IDLE
            },
            animate
        )
    }

    fun setState(state: OutlineState, animate: Boolean = true) {
        this.state = state

        val targetLen = state.targetSegmentLength()
        val settled = state.toInternalState()
        targetSegLen = targetLen
        settledInternalState = settled

        if (!animate) {
            segLen = targetLen
            internalState = settled
            if (needsAnimating(settled)) startAnimating() else stopAnimating()
            invalidate()
            return
        }

        internalState = when {
            segLen > targetLen + EPS -> InternalState.SHRINKING
            segLen < targetLen - EPS -> InternalState.GROWING
            else -> settled
        }

        if (needsAnimating(internalState)) startAnimating() else {
            stopAnimating()
            invalidate()
        }
    }

    fun isLoading(): Boolean = state == OutlineState.LOADING

    fun isHidden(): Boolean = state == OutlineState.HIDDEN

    override fun onDrawContent(canvas: Canvas) {
        drawBackground(canvas)
        drawOutline(canvas)
    }

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {
        if (needsAnimating(internalState)) startAnimating()
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {
        stopAnimating()
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        copyTo(newLeft, newTop, width, height)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec =
        copyTo(left, top, newWidth.coerceAtLeast(0), newHeight.coerceAtLeast(0))

    private fun drawBackground(canvas: Canvas) {
        if (Color.alpha(backgroundColor) == 0) return
        if (outlineRect.width() <= 0f || outlineRect.height() <= 0f) return

        canvas.drawRoundRect(outlineRect, outlineRadius, outlineRadius, backgroundPaint)
    }

    private fun drawOutline(canvas: Canvas) {
        if (pathLength <= 0f) return
        if (internalState == InternalState.HIDDEN || segLen <= 0f) return

        if (segLen >= 1f) {
            canvas.drawPath(fullPath, paint)
            return
        }

        val start = tailPos * pathLength
        val end = start + segLen * pathLength

        segmentPath.reset()
        if (end <= pathLength) {
            pathMeasure.getSegment(start, end, segmentPath, true)
        } else {
            pathMeasure.getSegment(start, pathLength, segmentPath, true)
            pathMeasure.getSegment(0f, end - pathLength, segmentPath, true)
        }
        canvas.drawPath(segmentPath, paint)
    }

    private fun updatePath() {
        if (width <= 0 || height <= 0) {
            pathLength = 0f
            fullPath.reset()
            outlineRect.setEmpty()
            return
        }

        val inset = if (strokeWidth > 0f) strokeWidth / 2f else 0f
        outlineRect.set(
            padding.left + inset,
            padding.top + inset,
            width - padding.right - inset,
            height - padding.bottom - inset
        )
        if (outlineRect.width() <= 0f || outlineRect.height() <= 0f) {
            pathLength = 0f
            fullPath.reset()
            return
        }

        val maxRadius = minOf(outlineRect.width(), outlineRect.height()) / 2f
        outlineRadius = cornerRadius.coerceIn(0f, maxRadius)

        if (strokeWidth <= 0f) {
            pathLength = 0f
            fullPath.reset()
            return
        }

        fullPath.reset()
        fullPath.addRoundRect(outlineRect, outlineRadius, outlineRadius, Path.Direction.CW)
        pathMeasure.setPath(fullPath, true)
        pathLength = pathMeasure.length
    }

    private fun updateDashEffect() {
        paint.pathEffect = if (dashWidth > 0f && dashGap > 0f) {
            DashPathEffect(floatArrayOf(dashWidth, dashGap), 0f)
        } else {
            null
        }
    }

    private fun needsAnimating(s: InternalState): Boolean =
        when (s) {
            InternalState.IDLE, InternalState.HIDDEN -> false
            InternalState.LOADING, InternalState.SHRINKING, InternalState.GROWING -> true
        }

    private fun startAnimating() {
        val runtime = runtime ?: return
        if (frameRegistration != null) return

        lastFrameMs = 0L
        frameRegistration = runtime.registerFrameCallback(frameCallback)
    }

    private fun stopAnimating() {
        frameRegistration?.close()
        frameRegistration = null
    }

    private fun onFrame(frameTimeNanos: Long) {
        val now = frameTimeNanos / 1_000_000L
        if (lastFrameMs == 0L) {

            lastFrameMs = now
            invalidate()
            return
        }

        val dt = (now - lastFrameMs).coerceAtLeast(0L)
        lastFrameMs = now

        val delta = dt.toFloat() / loadingDurationMs

        when (internalState) {
            InternalState.IDLE, InternalState.HIDDEN -> {
                stopAnimating()
                return
            }

            InternalState.LOADING -> {
                tailPos = wrap(tailPos + delta)
            }

            InternalState.SHRINKING -> {
                tailPos = wrap(tailPos + delta)
                segLen -= delta
                if (segLen <= targetSegLen) {
                    segLen = targetSegLen
                    internalState = settledInternalState
                    if (!needsAnimating(internalState)) stopAnimating()
                }
            }

            InternalState.GROWING -> {
                segLen += delta
                if (segLen >= targetSegLen) {
                    segLen = targetSegLen
                    internalState = settledInternalState
                    if (!needsAnimating(internalState)) stopAnimating()
                }
            }
        }
        invalidate()
    }

    private fun invalidate() {
        requestDraw()
    }

    private fun copyTo(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int): OutlineSpec =
        OutlineSpec(
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
            loadingSegmentRatio = loadingSegmentRatio,
            loadingDurationMs = loadingDurationMs,
            state = state,
            node = node
        ).also {
            it.internalState = internalState
            it.settledInternalState = settledInternalState
            it.tailPos = tailPos
            it.segLen = segLen
            it.targetSegLen = targetSegLen
        }

    private fun OutlineState.targetSegmentLength(): Float =
        when (this) {
            OutlineState.HIDDEN -> 0f
            OutlineState.LOADING -> loadingSegmentRatio
            OutlineState.IDLE -> 1f
        }

    private fun OutlineState.toInternalState(): InternalState =
        when (this) {
            OutlineState.HIDDEN -> InternalState.HIDDEN
            OutlineState.LOADING -> InternalState.LOADING
            OutlineState.IDLE -> InternalState.IDLE
        }

    private fun wrap(v: Float): Float {
        var x = v % 1f
        if (x < 0f) x += 1f
        return x
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
