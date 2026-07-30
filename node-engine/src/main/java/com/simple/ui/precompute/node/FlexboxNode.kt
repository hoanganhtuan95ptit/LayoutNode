package com.simple.ui.precompute.node

import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy
import kotlin.math.roundToInt

/**
 * Main-axis direction, mirroring the common values from Google's FlexboxLayout.
 */
enum class FlexDirection {
    ROW,
    ROW_REVERSE,
    COLUMN,
    COLUMN_REVERSE
}

enum class FlexWrap {
    NOWRAP,
    WRAP,
    WRAP_REVERSE
}

enum class FlexJustifyContent {
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY
}

enum class FlexAlignItems {
    FLEX_START,
    FLEX_END,
    CENTER,
    STRETCH
}

enum class FlexAlignSelf {
    AUTO,
    FLEX_START,
    FLEX_END,
    CENTER,
    STRETCH
}

enum class FlexAlignContent {
    FLEX_START,
    FLEX_END,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
    STRETCH
}

/**
 * Child metadata equivalent to the useful part of FlexboxLayout.LayoutParams.
 *
 * Baseline alignment is intentionally not modeled because [DrawSpec] does not
 * expose baseline metrics across arbitrary node types.
 */
data class FlexChild(
    val node: LayoutNode,
    val order: Int = 0,
    val flexGrow: Float = 0f,
    val flexShrink: Float = 1f,
    val alignSelf: FlexAlignSelf = FlexAlignSelf.AUTO,
    val flexBasisPercent: Float = Float.NaN,
    val wrapBefore: Boolean = false
) {
    init {
        require(flexGrow >= 0f) { "flexGrow must be >= 0, was $flexGrow" }
        require(flexShrink >= 0f) { "flexShrink must be >= 0, was $flexShrink" }
        require(flexBasisPercent.isNaN() || flexBasisPercent in 0f..1f) {
            "flexBasisPercent must be NaN or in 0f..1f, was $flexBasisPercent"
        }
    }
}

/**
 * Flexbox-style container node.
 *
 * Supported FlexboxLayout-like behavior:
 * - row/column and reverse directions
 * - nowrap/wrap/wrap-reverse
 * - justifyContent
 * - alignItems / alignSelf
 * - alignContent
 * - order, flexGrow, flexShrink, flexBasisPercent, wrapBefore
 */
interface FlexboxMeasureNode {

    val children: List<FlexChild>
    val flexDirection: FlexDirection
    val flexWrap: FlexWrap
    val justifyContent: FlexJustifyContent
    val alignItems: FlexAlignItems
    val alignContent: FlexAlignContent
    val gap: Int
    val mainGap: Int
    val crossGap: Int
}

data class FlexboxNode(
    override val children: List<FlexChild>,
    override val flexDirection: FlexDirection = FlexDirection.ROW,
    override val flexWrap: FlexWrap = FlexWrap.NOWRAP,
    override val justifyContent: FlexJustifyContent = FlexJustifyContent.FLEX_START,
    override val alignItems: FlexAlignItems = FlexAlignItems.STRETCH,
    override val alignContent: FlexAlignContent = FlexAlignContent.STRETCH,
    override val gap: Int = 0,
    override val mainGap: Int = gap,
    override val crossGap: Int = gap,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val onClick: (() -> Unit)? = null
) : LayoutNode(), FlexboxMeasureNode {

    init {
        require(gap >= 0) { "gap must be >= 0, was $gap" }
        require(mainGap >= 0) { "mainGap must be >= 0, was $mainGap" }
        require(crossGap >= 0) { "crossGap must be >= 0, was $crossGap" }
    }

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): GroupSpec =
        FlexboxMeasurePolicy<FlexboxNode>().measure(this, ctx, c, x, y)
}

open class FlexboxMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : FlexboxMeasureNode {

    private lateinit var activeNode: N

    private val children: List<FlexChild>
        get() = activeNode.children

    private val flexDirection: FlexDirection
        get() = activeNode.flexDirection

    private val flexWrap: FlexWrap
        get() = activeNode.flexWrap

    private val justifyContent: FlexJustifyContent
        get() = activeNode.justifyContent

    private val alignItems: FlexAlignItems
        get() = activeNode.alignItems

    private val alignContent: FlexAlignContent
        get() = activeNode.alignContent

    private val mainGap: Int
        get() = activeNode.mainGap

    private val crossGap: Int
        get() = activeNode.crossGap

    private val padding: EdgeInsets
        get() = activeNode.padding

    private val layoutWidth: LayoutDimension
        get() = activeNode.layoutWidth

    private val layoutHeight: LayoutDimension
        get() = activeNode.layoutHeight

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GroupSpec {

        activeNode = node
        val p = padding
        val measureMaxW = layoutWidth.maxForMeasure(c.maxWidth)
        val measureMaxH = layoutHeight.maxForMeasure(c.maxHeight)
        val innerMaxW = (measureMaxW - p.horizontal).coerceAtLeast(0)
        val innerMaxH = (measureMaxH - p.vertical).coerceAtLeast(0)
        val isRow = flexDirection.isRow

        val lines = measureLines(
            ctx = ctx,
            innerMaxW = innerMaxW,
            innerMaxH = innerMaxH,
            isRow = isRow
        )

        val naturalInnerMain = lines.maxOfOrNull { it.mainSize } ?: 0
        val naturalInnerCross = lines.crossSizeWithGaps()
        val naturalW = if (isRow) {
            naturalInnerMain + p.horizontal
        } else {
            naturalInnerCross + p.horizontal
        }
        val naturalH = if (isRow) {
            naturalInnerCross + p.vertical
        } else {
            naturalInnerMain + p.vertical
        }

        val width = layoutWidth.resolve(naturalW, c.maxWidth)
        val height = layoutHeight.resolve(naturalH, c.maxHeight)
        val innerW = (width - p.horizontal).coerceAtLeast(0)
        val innerH = (height - p.vertical).coerceAtLeast(0)
        val finalMain = if (isRow) innerW else innerH
        val finalCross = if (isRow) innerH else innerW

        val placed = placeLines(
            lines = lines,
            finalMain = finalMain,
            finalCross = finalCross,
            padding = p,
            isRow = isRow
        )

        return createSpec(node, x, y, width, height, placed)
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

    private fun measureLines(
        ctx: MeasureContext,
        innerMaxW: Int,
        innerMaxH: Int,
        isRow: Boolean
    ): List<FlexLine> {
        if (children.isEmpty()) return emptyList()

        val mainLimit = if (isRow) innerMaxW else innerMaxH
        val canWrap = flexWrap != FlexWrap.NOWRAP && mainLimit != Int.MAX_VALUE
        val ordered = children
            .mapIndexed { index, child -> OrderedFlexChild(index, child) }
            .sortedWith(compareBy<OrderedFlexChild> { it.child.order }.thenBy { it.index })

        val lines = ArrayList<FlexLine>()
        var line = FlexLine()

        ordered.forEach { orderedChild ->
            val item = measureChild(ctx, orderedChild.child, innerMaxW, innerMaxH, isRow)
            val shouldWrap = line.items.isNotEmpty() &&
                    canWrap &&
                    (orderedChild.child.wrapBefore || line.mainSize + mainGap + item.mainSize > mainLimit)

            if (shouldWrap) {
                lines.add(line)
                line = FlexLine()
            }
            line.add(item, mainGap)
        }

        if (line.items.isNotEmpty()) lines.add(line)
        return lines
    }

    private fun measureChild(
        ctx: MeasureContext,
        child: FlexChild,
        innerMaxW: Int,
        innerMaxH: Int,
        isRow: Boolean
    ): FlexMeasuredItem {
        val basis = child.flexBasisPercent
            .takeIf { !it.isNaN() }
            ?.let { percent ->
                val mainMax = if (isRow) innerMaxW else innerMaxH
                if (mainMax == Int.MAX_VALUE) null else (mainMax * percent).roundToInt()
            }

        val childConstraints = if (basis != null) {
            if (isRow) {
                Constraints(basis, innerMaxH)
            } else {
                Constraints(innerMaxW, basis)
            }
        } else {
            Constraints(innerMaxW, innerMaxH)
        }

        val measured = ctx.measure(child.node, childConstraints, 0, 0)
        val spec = if (basis != null) {
            measured.withMainSize(basis.coerceAtLeast(0), isRow)
        } else {
            measured
        }

        return FlexMeasuredItem(
            child = child,
            spec = spec,
            mainSize = spec.mainSize(isRow),
            crossSize = spec.crossSize(isRow)
        )
    }

    private fun placeLines(
        lines: List<FlexLine>,
        finalMain: Int,
        finalCross: Int,
        padding: EdgeInsets,
        isRow: Boolean
    ): List<DrawSpec> {
        if (lines.isEmpty()) return emptyList()

        lines.forEach { it.applyFlex(finalMain, isRow) }

        val visualLines = if (flexWrap == FlexWrap.WRAP_REVERSE) {
            lines.asReversed()
        } else {
            lines
        }
        val crossPlacement = resolveCrossPlacement(visualLines, finalCross)

        val placed = ArrayList<DrawSpec>(children.size)
        visualLines.forEachIndexed { visualLineIndex, line ->
            val lineCrossSize = crossPlacement.sizes[visualLineIndex]
            val lineCrossStart = crossPlacement.offsets[visualLineIndex]
            val mainDistribution = distributeMain(
                contentSize = line.mainSize,
                containerSize = finalMain,
                itemCount = line.items.size
            )

            var cursor = mainDistribution.start
            line.items.forEach { item ->
                item.applyStretch(lineCrossSize, isRow)

                val itemMain = item.mainSize
                val itemCross = item.crossSize
                val mainStart = if (flexDirection.isMainReverse) {
                    finalMain - cursor - itemMain
                } else {
                    cursor
                }.coerceAtLeast(0)
                val crossStart = lineCrossStart + item.crossOffset(lineCrossSize)

                placed.add(
                    item.spec.withAxisPosition(
                        main = paddingMain(padding, isRow) + mainStart,
                        cross = paddingCross(padding, isRow) + crossStart,
                        isRow = isRow
                    )
                )
                cursor += itemMain + mainDistribution.between
            }
        }
        return placed
    }

    private fun FlexLine.applyFlex(finalMain: Int, isRow: Boolean) {
        if (items.isEmpty()) return

        val growSpace = finalMain - mainSize
        val totalGrow = items.sumGrow()
        if (growSpace > 0 && totalGrow > 0f) {
            val growItems = items.filter { it.child.flexGrow > 0f }
            var used = 0
            growItems.forEachIndexed { index, item ->
                val delta = if (index == growItems.lastIndex) {
                    growSpace - used
                } else {
                    (growSpace * item.child.flexGrow / totalGrow).roundToInt()
                }.coerceAtLeast(0)
                used += delta
                item.setMainSize(item.mainSize + delta, isRow)
            }
            recalculate(mainGap)
            return
        }

        val overflow = mainSize - finalMain
        val totalShrink = items.sumShrinkWeight()
        if (overflow > 0 && totalShrink > 0f) {
            val shrinkItems = items.filter { it.child.flexShrink > 0f }
            var removed = 0
            shrinkItems.forEachIndexed { index, item ->
                val weight = item.mainSize * item.child.flexShrink
                val delta = if (index == shrinkItems.lastIndex) {
                    overflow - removed
                } else {
                    (overflow * weight / totalShrink).roundToInt()
                }.coerceAtLeast(0)
                removed += delta
                item.setMainSize((item.mainSize - delta).coerceAtLeast(0), isRow)
            }
            recalculate(mainGap)
        }
    }

    private fun FlexMeasuredItem.applyStretch(lineCrossSize: Int, isRow: Boolean) {
        if (align() != FlexAlignItems.STRETCH || crossSize == lineCrossSize) return
        setCrossSize(lineCrossSize, isRow)
    }

    private fun FlexMeasuredItem.crossOffset(lineCrossSize: Int): Int {
        val free = (lineCrossSize - crossSize).coerceAtLeast(0)
        return when (align()) {
            FlexAlignItems.FLEX_START,
            FlexAlignItems.STRETCH -> 0
            FlexAlignItems.CENTER -> free / 2
            FlexAlignItems.FLEX_END -> free
        }
    }

    private fun FlexMeasuredItem.align(): FlexAlignItems =
        when (child.alignSelf) {
            FlexAlignSelf.AUTO -> alignItems
            FlexAlignSelf.FLEX_START -> FlexAlignItems.FLEX_START
            FlexAlignSelf.FLEX_END -> FlexAlignItems.FLEX_END
            FlexAlignSelf.CENTER -> FlexAlignItems.CENTER
            FlexAlignSelf.STRETCH -> FlexAlignItems.STRETCH
        }

    private fun resolveCrossPlacement(lines: List<FlexLine>, finalCross: Int): CrossPlacement {
        val natural = lines.crossSizeWithGaps()
        val free = (finalCross - natural).coerceAtLeast(0)

        if (alignContent == FlexAlignContent.STRETCH && free > 0) {
            val sizes = lines.map { it.crossSize }.toMutableList()
            val extraEach = free / lines.size
            var remainder = free % lines.size
            for (i in sizes.indices) {
                val extra = extraEach + if (remainder > 0) 1 else 0
                if (remainder > 0) remainder--
                sizes[i] += extra
            }
            return CrossPlacement(sizes, sequentialOffsets(sizes, crossGap, 0))
        }

        val distribution = distributeCross(
            contentSize = natural,
            containerSize = finalCross,
            lineCount = lines.size
        )
        val sizes = lines.map { it.crossSize }
        return CrossPlacement(
            sizes = sizes,
            offsets = sequentialOffsets(sizes, distribution.between, distribution.start)
        )
    }

    private fun distributeMain(
        contentSize: Int,
        containerSize: Int,
        itemCount: Int
    ): Distribution {
        val free = (containerSize - contentSize).coerceAtLeast(0)
        return when (justifyContent) {
            FlexJustifyContent.FLEX_START -> Distribution(0, mainGap)
            FlexJustifyContent.FLEX_END -> Distribution(free, mainGap)
            FlexJustifyContent.CENTER -> Distribution(free / 2, mainGap)
            FlexJustifyContent.SPACE_BETWEEN -> {
                if (itemCount > 1) Distribution(0, mainGap + free / (itemCount - 1))
                else Distribution(free / 2, mainGap)
            }
            FlexJustifyContent.SPACE_AROUND -> {
                if (itemCount > 0) {
                    val slot = free / itemCount
                    Distribution(slot / 2, mainGap + slot)
                } else {
                    Distribution(0, mainGap)
                }
            }
            FlexJustifyContent.SPACE_EVENLY -> {
                val slot = free / (itemCount + 1)
                Distribution(slot, mainGap + slot)
            }
        }
    }

    private fun distributeCross(
        contentSize: Int,
        containerSize: Int,
        lineCount: Int
    ): Distribution {
        val free = (containerSize - contentSize).coerceAtLeast(0)
        return when (alignContent) {
            FlexAlignContent.FLEX_START,
            FlexAlignContent.STRETCH -> Distribution(0, crossGap)
            FlexAlignContent.FLEX_END -> Distribution(free, crossGap)
            FlexAlignContent.CENTER -> Distribution(free / 2, crossGap)
            FlexAlignContent.SPACE_BETWEEN -> {
                if (lineCount > 1) Distribution(0, crossGap + free / (lineCount - 1))
                else Distribution(free / 2, crossGap)
            }
            FlexAlignContent.SPACE_AROUND -> {
                val slot = free / lineCount
                Distribution(slot / 2, crossGap + slot)
            }
            FlexAlignContent.SPACE_EVENLY -> {
                val slot = free / (lineCount + 1)
                Distribution(slot, crossGap + slot)
            }
        }
    }

    private fun sequentialOffsets(sizes: List<Int>, gap: Int, start: Int): List<Int> {
        val offsets = ArrayList<Int>(sizes.size)
        var cursor = start
        sizes.forEach { size ->
            offsets.add(cursor)
            cursor += size + gap
        }
        return offsets
    }

    private fun List<FlexLine>.crossSizeWithGaps(): Int =
        sumOf { it.crossSize } + crossGap * (size - 1).coerceAtLeast(0)

    private fun paddingMain(p: EdgeInsets, isRow: Boolean): Int =
        if (isRow) p.left else p.top

    private fun paddingCross(p: EdgeInsets, isRow: Boolean): Int =
        if (isRow) p.top else p.left

    private val FlexDirection.isRow: Boolean
        get() = this == FlexDirection.ROW || this == FlexDirection.ROW_REVERSE

    private val FlexDirection.isMainReverse: Boolean
        get() = this == FlexDirection.ROW_REVERSE || this == FlexDirection.COLUMN_REVERSE
}

private data class OrderedFlexChild(
    val index: Int,
    val child: FlexChild
)

private data class FlexLine(
    val items: MutableList<FlexMeasuredItem> = ArrayList(),
    var mainSize: Int = 0,
    var crossSize: Int = 0
) {

    fun add(item: FlexMeasuredItem, gap: Int) {
        if (items.isNotEmpty()) mainSize += gap
        items.add(item)
        mainSize += item.mainSize
        crossSize = maxOf(crossSize, item.crossSize)
    }

    fun recalculate(gap: Int) {
        mainSize = items.sumOf { it.mainSize } + gap * (items.size - 1).coerceAtLeast(0)
        crossSize = items.maxOfOrNull { it.crossSize } ?: 0
    }
}

private data class FlexMeasuredItem(
    val child: FlexChild,
    var spec: DrawSpec,
    var mainSize: Int,
    var crossSize: Int
) {

    fun setMainSize(size: Int, isRow: Boolean) {
        mainSize = size.coerceAtLeast(0)
        spec = spec.withAxisSize(mainSize, crossSize, isRow)
    }

    fun setCrossSize(size: Int, isRow: Boolean) {
        crossSize = size.coerceAtLeast(0)
        spec = spec.withAxisSize(mainSize, crossSize, isRow)
    }
}

private data class Distribution(
    val start: Int,
    val between: Int
)

private data class CrossPlacement(
    val sizes: List<Int>,
    val offsets: List<Int>
)

private fun List<FlexMeasuredItem>.sumGrow(): Float {
    var total = 0f
    for (item in this) total += item.child.flexGrow
    return total
}

private fun List<FlexMeasuredItem>.sumShrinkWeight(): Float {
    var total = 0f
    for (item in this) total += item.mainSize * item.child.flexShrink
    return total
}

private fun DrawSpec.mainSize(isRow: Boolean): Int =
    if (isRow) width else height

private fun DrawSpec.crossSize(isRow: Boolean): Int =
    if (isRow) height else width

private fun DrawSpec.withMainSize(mainSize: Int, isRow: Boolean): DrawSpec =
    if (isRow) withSize(mainSize, height) else withSize(width, mainSize)

private fun DrawSpec.withAxisSize(main: Int, cross: Int, isRow: Boolean): DrawSpec =
    if (isRow) withSize(main, cross) else withSize(cross, main)

private fun DrawSpec.withAxisPosition(main: Int, cross: Int, isRow: Boolean): DrawSpec =
    if (isRow) withPosition(main, cross) else withPosition(cross, main)
