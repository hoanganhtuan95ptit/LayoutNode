package com.simple.ui.precompute.node

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.text.BigText
import kotlin.math.ceil
import androidx.core.graphics.withSave
import com.simple.ui.precompute.MeasurePolicy

// ─────────────────────────────────────────────────────────────────────────────
// TextNode — mô tả một đoạn text cần đo/vẽ.
// TextSpec  — kết quả sau khi đo: giữ Picture đã record sẵn, vẽ 0-alloc.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Contract dữ liệu cho policy đo text.
 *
 * Node custom chỉ cần implement interface này để tái sử dụng
 * [TextMeasurePolicy] mà không phải kế thừa/copy [TextNode].
 */
interface TextMeasureNode {

    val text: BigText
    val textSizePx: Float
    val color: Int
    val maxLines: Int
    val typeface: Typeface?
    val lineSpacingMul: Float
    val lineSpacingAdd: Float
    val textPaintDensity: Float
    val alignment: CrossAlign
}

/**
 * Mô tả một đoạn text cần layout.
 *
 * Mọi tham số phải resolve sẵn trước khi truyền vào (color là Int,
 * typeface đã load) — engine không được đụng Context.
 */
data class TextNode(
    override val text: BigText,
    override val textSizePx: Float = 1f,
    override val color: Int = Color.TRANSPARENT,
    override val maxLines: Int = Int.MAX_VALUE,
    override val typeface: Typeface? = null,
    override val lineSpacingMul: Float = 1f,
    override val lineSpacingAdd: Float = 0f,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val alignment: CrossAlign = CrossAlign.START,
    override val textPaintDensity: Float = Resources.getSystem().displayMetrics.density,
    override val onClick: (() -> Unit)? = null
) : LayoutNode(), TextMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): TextSpec =
        TextMeasurePolicy<TextNode>().measure(this, ctx, c, x, y)
}

open class TextMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : TextMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): TextSpec {

        val p = node.padding
        val measureWidth = node.layoutWidth.maxForMeasure(c.maxWidth)
        val innerWidth = (measureWidth - p.horizontal).coerceAtLeast(0)
        val textChar = node.text.textChar

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = node.textSizePx
            density = node.textPaintDensity
            color = node.color
            node.typeface?.let { typeface = it }
        }

        val alignment = when (node.alignment) {
            CrossAlign.START -> Layout.Alignment.ALIGN_NORMAL
            CrossAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            CrossAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val layout = StaticLayout.Builder
            .obtain(textChar, 0, textChar.length, paint, innerWidth)
            .setAlignment(alignment)
            .setLineSpacing(node.lineSpacingAdd, node.lineSpacingMul)
            .setMaxLines(node.maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

        val usedWidth = (0 until layout.lineCount)
            .maxOfOrNull { layout.getLineWidth(it) }
            ?.let { ceil(it.toDouble()).toInt() }
            ?: 0

        val contentW = usedWidth.coerceAtMost(innerWidth) + p.horizontal
        val contentH = layout.height + p.vertical
        val w = node.layoutWidth.resolve(contentW, c.maxWidth)
        val h = node.layoutHeight.resolve(contentH, c.maxHeight)
        val picture = recordTextPicture(layout, p.left, p.top, w, h)
        return createSpec(
            left = x,
            top = y,
            width = w,
            height = h,
            picture = picture,
            layout = layout,
            contentLeft = p.left,
            contentTop = p.top,
            node = node
        )
    }

    protected open fun createSpec(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        picture: Picture,
        layout: StaticLayout,
        contentLeft: Int,
        contentTop: Int,
        node: N
    ): TextSpec {

        return TextSpec(left, top, width, height, picture, node)
    }

    protected open fun recordTextPicture(
        layout: StaticLayout,
        contentLeft: Int,
        contentTop: Int,
        width: Int,
        height: Int
    ): Picture {
        val picture = Picture()
        val canvas = picture.beginRecording(width.coerceAtLeast(1), height.coerceAtLeast(1))
        canvas.withSave {
            if (contentLeft != 0 || contentTop != 0) {
                canvas.translate(contentLeft.toFloat(), contentTop.toFloat())
            }
            layout.draw(canvas)
        }
        picture.endRecording()
        return picture
    }
}

/**
 * Kết quả đo của [TextNode]. [StaticLayout] chỉ dùng trong lúc đo/record;
 * [onDrawContent] replay [Picture] đã có sẵn — zero allocation.
 */
open class TextSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val picture: Picture,
    override val node: LayoutNode
) : DrawSpec() {

    override open fun onDrawContent(canvas: Canvas) {
        picture.draw(canvas)
    }

    override fun withPosition(newLeft: Int, newTop: Int) =
        TextSpec(newLeft, newTop, width, height, picture, node)
}
