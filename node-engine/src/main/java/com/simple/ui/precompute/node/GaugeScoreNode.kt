package com.simple.ui.precompute.node

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// GaugeScoreNode — vẽ phần text trung tâm của ScoreGauge:
//
//   ┌───────────────┐
//   │     LABEL     │   ← label (e.g. "ĐIỂM")
//   │      72 %     │   ← score number + "%" superscript
//   │    GRADE B    │   ← grade
//   └───────────────┘
//
// Mọi tham số phải resolve sẵn (Int color, Typeface đã load) — engine không
// được đụng Context.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mô tả phần text trung tâm của ScoreGauge.
 *
 * Kích thước text được tính theo cạnh ngắn của bounds (sau khi trừ padding)
 * nhân với [scoreTextScale] / [percentTextScale] / [labelTextScale].
 *
 * Mặc định [layoutWidth] / [layoutHeight] = [LayoutDimension.MatchParent] để
 * node chiếm hết slot; thường overlay cùng [GaugeArcNode] trong một stack/frame.
 */
interface GaugeScoreMeasureNode {

    val progress: Int
    val label: String
    val grade: String
    val labelColor: Int
    val scoreColor: Int
    val percentColor: Int
    val gradeColor: Int
    val typeface: Typeface?
    val scoreTextScale: Float
    val percentTextScale: Float
    val labelTextScale: Float
}

data class GaugeScoreNode(
    override val progress: Int,
    override val label: String = "",
    override val grade: String = "",
    override val labelColor: Int = 0xFF6F6E69.toInt(),
    override val scoreColor: Int = 0xFF111111.toInt(),
    override val percentColor: Int = 0xFF111111.toInt(),
    override val gradeColor: Int = 0xFF1ED760.toInt(),
    override val typeface: Typeface? = null,
    /** Tỷ lệ kích thước số điểm so với cạnh ngắn của bounds. */
    override val scoreTextScale: Float = 0.28f,
    /** Tỷ lệ kích thước ký tự "%". */
    override val percentTextScale: Float = 0.14f,
    /** Tỷ lệ kích thước label / grade. */
    override val labelTextScale: Float = 0.10f,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.MatchParent,
    override val layoutHeight: LayoutDimension = LayoutDimension.MatchParent,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
    override val onTouch: ((NodeTouch) -> Boolean)? = null,
    override val contentDescription: String? = null
) : LayoutNode(), GaugeScoreMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GaugeScoreSpec =
        GaugeScoreMeasurePolicy<GaugeScoreNode>().measure(this, ctx, c, x, y)
}

open class GaugeScoreMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : GaugeScoreMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GaugeScoreSpec {

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
    ): GaugeScoreSpec {

        return GaugeScoreSpec(
            left = left,
            top = top,
            width = width,
            height = height,
            padding = padding,
            progress = node.progress.coerceIn(0, 100),
            label = node.label,
            grade = node.grade,
            labelColor = node.labelColor,
            scoreColor = node.scoreColor,
            percentColor = node.percentColor,
            gradeColor = node.gradeColor,
            typeface = node.typeface,
            scoreTextScale = node.scoreTextScale,
            percentTextScale = node.percentTextScale,
            labelTextScale = node.labelTextScale,
            node = node
        )
    }
}

/**
 * Kết quả đo của [GaugeScoreNode]. Paints + textSize đã setup trong init,
 * [onDrawContent] chỉ làm float math — zero allocation.
 */
open class GaugeScoreSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val padding: EdgeInsets,
    progress: Int,
    label: String,
    grade: String,
    labelColor: Int,
    scoreColor: Int,
    percentColor: Int,
    gradeColor: Int,
    open val typeface: Typeface?,
    val scoreTextScale: Float,
    val percentTextScale: Float,
    val labelTextScale: Float,
    override val node: LayoutNode
) : DrawSpec() {

    var progress: Int = progress.coerceIn(0, 100)
        set(value) {
            field = value.coerceIn(0, 100)
        }

    var label: String = label
        set(value) {
            field = value
        }

    var grade: String = grade
        set(value) {
            field = value
        }

    var labelColor: Int = labelColor
        set(value) {
            field = value
            labelPaint.color = value
        }

    var scoreColor: Int = scoreColor
        set(value) {
            field = value
            scorePaint.color = value
        }

    var percentColor: Int = percentColor
        set(value) {
            field = value
            percentPaint.color = value
        }

    var gradeColor: Int = gradeColor
        set(value) {
            field = value
            gradePaint.color = value
        }

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = this@GaugeScoreSpec.scoreColor
        this@GaugeScoreSpec.typeface?.let { typeface = it }
    }

    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = this@GaugeScoreSpec.percentColor
        this@GaugeScoreSpec.typeface?.let { typeface = it }
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = this@GaugeScoreSpec.labelColor
        this@GaugeScoreSpec.typeface?.let { typeface = it }
    }

    private val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = this@GaugeScoreSpec.gradeColor
        this@GaugeScoreSpec.typeface?.let { typeface = it }
    }

    private val innerW: Int = (width - padding.horizontal).coerceAtLeast(0)
    private val innerH: Int = (height - padding.vertical).coerceAtLeast(0)
    private val cx: Float = padding.left + innerW / 2f
    private val cy: Float = padding.top + innerH / 2f

    init {
        val size = min(innerW, innerH).toFloat()
        scorePaint.textSize = size * scoreTextScale
        percentPaint.textSize = size * percentTextScale
        labelPaint.textSize = size * labelTextScale
        gradePaint.textSize = size * labelTextScale
    }

    override fun onDrawContent(canvas: Canvas) {
        if (innerW <= 0 || innerH <= 0) return

        // 1. Label phía trên center.
        if (label.isNotEmpty()) {
            val labelY = cy - scorePaint.textSize * 0.70f
            canvas.drawText(label, cx, labelY, labelPaint)
        }

        // 2. Số điểm, lệch trái một chút để chừa chỗ cho "%".
        val scoreStr = progress.toString()
        val scoreY = cy + scorePaint.textSize * 0.35f
        val scoreX = cx - percentPaint.textSize * 0.5f
        canvas.drawText(scoreStr, scoreX, scoreY, scorePaint)

        // 3. "%" dạng superscript bên phải số.
        val percentX = cx + scorePaint.measureText(scoreStr) * 0.5f - percentPaint.textSize * 0.1f
        val percentY = scoreY - scorePaint.textSize * 0.35f + percentPaint.textSize * 0.35f
        canvas.drawText("%", percentX, percentY, percentPaint)

        // 4. Grade phía dưới số điểm.
        if (grade.isNotEmpty()) {
            val gradeY = scoreY + gradePaint.textSize * 1.8f
            canvas.drawText(grade, cx, gradeY, gradePaint)
        }
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        copyTo(newLeft, newTop, width, height)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec =
        copyTo(left, top, newWidth.coerceAtLeast(0), newHeight.coerceAtLeast(0))

    private fun copyTo(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int): GaugeScoreSpec =
        GaugeScoreSpec(
            left = newLeft,
            top = newTop,
            width = newWidth,
            height = newHeight,
            padding = padding,
            progress = progress,
            label = label,
            grade = grade,
            labelColor = labelColor,
            scoreColor = scoreColor,
            percentColor = percentColor,
            gradeColor = gradeColor,
            typeface = typeface,
            scoreTextScale = scoreTextScale,
            percentTextScale = percentTextScale,
            labelTextScale = labelTextScale,
            node = node
        )
}
