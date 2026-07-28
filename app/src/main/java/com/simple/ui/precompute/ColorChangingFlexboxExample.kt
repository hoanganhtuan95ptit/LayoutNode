package com.simple.ui.precompute

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.graphics.Typeface
import android.text.StaticLayout
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.FlexAlignContent
import com.simple.ui.precompute.node.FlexAlignItems
import com.simple.ui.precompute.node.FlexChild
import com.simple.ui.precompute.node.FlexDirection
import com.simple.ui.precompute.node.FlexJustifyContent
import com.simple.ui.precompute.node.FlexWrap
import com.simple.ui.precompute.node.FlexboxMeasureNode
import com.simple.ui.precompute.node.FlexboxMeasurePolicy
import com.simple.ui.precompute.node.GroupSpec
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode
import com.simple.ui.precompute.node.TextMeasureNode
import com.simple.ui.precompute.node.TextMeasurePolicy
import com.simple.ui.precompute.node.TextSpec
import com.simple.ui.precompute.text.BigText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ColorChangingFlexboxNode(
    override val children: List<FlexChild>,
    val targetId: Any = "aaa",
    val colors: List<Int> = listOf(
        0xFFE91E63.toInt(),
        0xFF4CAF50.toInt(),
        0xFF2196F3.toInt(),
        0xFFFF9800.toInt()
    ),
    val intervalMs: Long = 5_000L,
    override val flexDirection: FlexDirection = FlexDirection.ROW,
    override val flexWrap: FlexWrap = FlexWrap.WRAP,
    override val justifyContent: FlexJustifyContent = FlexJustifyContent.FLEX_START,
    override val alignItems: FlexAlignItems = FlexAlignItems.CENTER,
    override val alignContent: FlexAlignContent = FlexAlignContent.FLEX_START,
    override val gap: Int = 0,
    override val mainGap: Int = gap,
    override val crossGap: Int = gap,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent
) : LayoutNode(), FlexboxMeasureNode {

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): GroupSpec =
        ColorChangingFlexboxMeasurePolicy().measure(this, ctx, c, x, y)
}

open class ColorChangingFlexboxMeasurePolicy :
        FlexboxMeasurePolicy<ColorChangingFlexboxNode>() {

    override fun createSpec(
        node: ColorChangingFlexboxNode,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        children: List<DrawSpec>
    ): GroupSpec {

        return ColorChangingFlexboxSpec(
            left = left,
            top = top,
            width = width,
            height = height,
            children = children,
            node = node,
            targetId = node.targetId,
            colors = node.colors,
            intervalMs = node.intervalMs
        )
    }
}

open class ColorChangingFlexboxSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    override val children: List<DrawSpec>,
    override val node: LayoutNode,
    private val targetId: Any,
    private val colors: List<Int>,
    private val intervalMs: Long
) : GroupSpec(left, top, width, height, children, node) {

    private var scope: CoroutineScope? = null
    private var colorIndex = 0

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {

        super.onAttachedToRuntime(runtime)
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = nextScope
        nextScope.launch {

            while (isActive) {

                delay(intervalMs.coerceAtLeast(1L))
                updateTargetColor()
                requestDraw()
            }
        }
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {

        scope?.cancel()
        scope = null
        super.onDetachedFromRuntime(runtime)
    }

    override fun withPosition(newLeft: Int, newTop: Int): ColorChangingFlexboxSpec =
        ColorChangingFlexboxSpec(
            left = newLeft,
            top = newTop,
            width = width,
            height = height,
            children = children,
            node = node,
            targetId = targetId,
            colors = colors,
            intervalMs = intervalMs
        )

    private fun updateTargetColor() {

        if (colors.isEmpty()) return

        val nextColor = colors[colorIndex % colors.size]
        colorIndex++
        children.forEach { child ->

            child.updateColorById(targetId, nextColor)
        }
    }
}

interface RuntimeColorSpec {

    fun updateColor(color: Int)
}

private fun DrawSpec.updateColorById(targetId: Any, color: Int): Boolean {

    if (node?.id == targetId && this is RuntimeColorSpec) {

        updateColor(color)
        return true
    }

    if (this is GroupSpec) {

        return children.any { it.updateColorById(targetId, color) }
    }

    return false
}

data class RuntimeColorTextNode(
    override val text: BigText,
    override val textSizePx: Float = 1f,
    override val color: Int = Color.TRANSPARENT,
    override val id: Any? = null,
    override val maxLines: Int = Int.MAX_VALUE,
    override val typeface: Typeface? = null,
    override val lineSpacingMul: Float = 1f,
    override val lineSpacingAdd: Float = 0f,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val textPaintDensity: Float = Resources.getSystem().displayMetrics.density
) : LayoutNode(), TextMeasureNode {

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): DrawSpec =
        RuntimeColorTextMeasurePolicy().measure(this, ctx, c, x, y)
}

open class RuntimeColorTextMeasurePolicy : TextMeasurePolicy<RuntimeColorTextNode>() {

    override fun createSpec(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        picture: Picture,
        layout: StaticLayout,
        contentLeft: Int,
        contentTop: Int,
        node: RuntimeColorTextNode
    ): TextSpec {

        return RuntimeColorTextSpec(
            left = left,
            top = top,
            width = width,
            height = height,
            picture = picture,
            layout = layout,
            contentLeft = contentLeft,
            contentTop = contentTop,
            color = node.color,
            node = node
        )
    }
}

open class RuntimeColorTextSpec(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    picture: Picture,
    private val layout: StaticLayout,
    private val contentLeft: Int,
    private val contentTop: Int,
    color: Int,
    node: LayoutNode
) : TextSpec(left, top, width, height, picture, node), RuntimeColorSpec {

    private var currentColor = color

    override fun updateColor(color: Int) {

        currentColor = color
    }

    override fun onDrawContent(canvas: Canvas) {

        layout.paint.color = currentColor
        if (contentLeft != 0 || contentTop != 0) {

            canvas.translate(contentLeft.toFloat(), contentTop.toFloat())
        }
        layout.draw(canvas)
    }

    override fun withPosition(newLeft: Int, newTop: Int): RuntimeColorTextSpec =
        RuntimeColorTextSpec(
            left = newLeft,
            top = newTop,
            width = width,
            height = height,
            picture = picture,
            layout = layout,
            contentLeft = contentLeft,
            contentTop = contentTop,
            color = currentColor,
            node = node
        )
}
