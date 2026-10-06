package com.simple.ui.precompute.node

import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.MeasurePolicy

// ─────────────────────────────────────────────────────────────────────────────
// ConstraintChild — mô tả 1 child với các constraint liên kết.
// ConstraintNode  — container layout theo constraint, tương tự ConstraintLayout.
// Kết quả trả về  — GroupSpec (cùng DrawSpec type với LinearNode).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Một child trong [ConstraintNode].
 *
 * Quy ước đặt tên constraint giống ConstraintLayout XML:
 *   [startToStartOf] = "This.start snaps to target.start"
 *   [startToEndOf]   = "This.start snaps to target.end"
 *   … v.v.
 *
 * Dùng [ConstraintNode.PARENT] làm target ID để trỏ đến container.
 *
 * **Bias** (0f = hugged start/top, 1f = hugged end/bottom, 0.5f = center) chỉ
 * có hiệu lực khi **cả 2** constraint cùng trục được set.
 */
data class ConstraintChild(
    val id: String,
    val node: LayoutNode,
    // ── Horizontal constraints ────────────────────────────────────────────
    /** This.start = target.start */
    val startToStartOf: String? = null,
    /** This.start = target.end   */
    val startToEndOf: String? = null,
    /** This.end   = target.end   */
    val endToEndOf: String? = null,
    /** This.end   = target.start */
    val endToStartOf: String? = null,
    // ── Vertical constraints ──────────────────────────────────────────────
    /** This.top    = target.top    */
    val topToTopOf: String? = null,
    /** This.top    = target.bottom */
    val topToBottomOf: String? = null,
    /** This.bottom = target.bottom */
    val bottomToBottomOf: String? = null,
    /** This.bottom = target.top    */
    val bottomToTopOf: String? = null,
    // ── Margins (px) ─────────────────────────────────────────────────────
    val marginStart: Int = 0,
    val marginEnd: Int = 0,
    val marginTop: Int = 0,
    val marginBottom: Int = 0,
    // ── Dimension mode ────────────────────────────────────────────────────
    val width: LayoutDimension = LayoutDimension.WrapContent,
    val height: LayoutDimension = LayoutDimension.WrapContent,
    // ── Bias (chỉ active khi cả 2 constraint cùng trục được set) ─────────
    val horizontalBias: Float = 0.5f,
    val verticalBias: Float = 0.5f,
)

/**
 * Container layout theo kiểu constraint — tương tự [ConstraintLayout].
 *
 * ## Thuật toán
 * **Iterative topological sort**: mỗi vòng lặp, chọn các child mà tất cả
 * target ID trong constraint đã resolved, đo + gán vị trí, rồi tiếp tục.
 * Worst case **O(n²)** với n children — phù hợp cho < 50 views trong 1 màn.
 *
 * ## Hạn chế hiện tại
 * - Không hỗ trợ chain (horizontal/vertical chain).
 * - Không hỗ trợ Guideline / Barrier.
 * - [LayoutDimension.MatchParent] fill khoảng khả dụng theo anchor hiện có.
 * - Chiều cao container = wrap-to-content (max bottom của children + padding).
 * - Dependency cycle → child bị đặt tại (0, 0) làm fallback.
 *
 * ## WrapContent + neo tới PARENT (two-pass)
 * Khi container là [LayoutDimension.WrapContent], engine đo 2 lượt: lượt 1 ở
 * kích thước khả dụng để biết size các child, rồi CO inner về đúng extent nội
 * dung (tính từ phía start, bỏ phần nở do neo cạnh xa PARENT) và resolve lại.
 * Nhờ vậy child neo `endToEndOf`/`bottomToBottomOf = PARENT` nằm ở MÉP nội dung
 * thay vì kéo container phình tới parentMax.
 * Lưu ý: child [LayoutDimension.MatchParent] căng giữa 2 anchor PARENT thì vẫn
 * fill (không thể wrap quanh một child chiếm hết) — muốn full width hãy đặt
 * `layoutWidth = MatchParent` tường minh.
 *
 * ## Kết quả
 * Trả về [GroupSpec] — cùng kiểu với [LinearNode] để dùng chung [com.simple.ui.precompute.PrecomputedView].
 *
 * ## Ví dụ
 * ```kotlin
 * ConstraintNode(
 *     children = listOf(
 *         ConstraintChild(
 *             id = "title",
 *             node = TextNode(BigText("Hello"), sp18, Color.BLACK),
 *             startToStartOf = PARENT, endToEndOf = PARENT,
 *             topToTopOf = PARENT, marginTop = dp16,
 *             width = LayoutDimension.MatchParent,
 *         ),
 *         ConstraintChild(
 *             id = "subtitle",
 *             node = TextNode(BigText("World"), sp14, Color.GRAY),
 *             startToStartOf = "title",
 *             topToBottomOf = "title", marginTop = dp8,
 *         ),
 *     )
 * )
 * ```
 */
interface ConstraintMeasureNode {

    val children: List<ConstraintChild>
}

data class ConstraintNode(
    override val children: List<ConstraintChild>,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent
) : LayoutNode(), ConstraintMeasureNode {

    companion object {

        /** ID reserved trỏ đến container — dùng làm target trong constraint. */
        const val PARENT = "parent"
    }

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): GroupSpec =
        ConstraintMeasurePolicy<ConstraintNode>().measure(this, ctx, c, x, y)
}

open class ConstraintMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : ConstraintMeasureNode {

    private lateinit var activeNode: N

    private val children: List<ConstraintChild>
        get() = activeNode.children

    private val padding: EdgeInsets
        get() = activeNode.padding

    private val layoutWidth: LayoutDimension
        get() = activeNode.layoutWidth

    private val layoutHeight: LayoutDimension
        get() = activeNode.layoutHeight

    private companion object {

        const val PARENT = ConstraintNode.PARENT
    }

    private data class ConstraintMeasureState(
        val bounds: HashMap<String, IntArray>,
        val specs: HashMap<String, DrawSpec>,
        val remaining: MutableList<ConstraintChild>
    )

    private data class MeasuredChild(
        val spec: DrawSpec,
        val width: Int,
        val height: Int
    )

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
        val innerW = (measureMaxW - p.horizontal).coerceAtLeast(0)
        val innerH = (measureMaxH - p.vertical).coerceAtLeast(0)

        var state = resolveAll(ctx, innerW, innerH)

        // WrapContent: co inner về đúng EXTENT NỘI DUNG (bỏ phần nở do child neo
        // tới cạnh xa của PARENT) rồi resolve lại — để child neo-PARENT nằm trong
        // content thật thay vì kéo container phình tới parentMax.
        val wrapW = if (layoutWidth == LayoutDimension.WrapContent) contentExtentW(state, innerW) else innerW
        val wrapH = if (layoutHeight == LayoutDimension.WrapContent) contentExtentH(state, innerH) else innerH
        if (wrapW != innerW || wrapH != innerH) {

            state = resolveAll(ctx, wrapW, wrapH)
        }

        val placed = buildPlacedSpecs(state, p)
        val naturalW = naturalWidth(state.bounds, p)
        val naturalH = naturalHeight(state.bounds, p)
        val totalW = layoutWidth.resolve(naturalW.coerceAtLeast(p.horizontal), c.maxWidth)
        val totalH = layoutHeight.resolve(naturalH.coerceAtLeast(p.vertical), c.maxHeight)
        return createSpec(node, x, y, totalW, totalH, placed)
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

    private fun resolveAll(ctx: MeasureContext, innerW: Int, innerH: Int): ConstraintMeasureState {

        val state = createMeasureState(innerW, innerH)
        // Iterative resolve: mỗi pass giải những child đã đủ dependency.
        repeat(children.size + 1) {

            resolveReadyChildren(ctx, state, innerW, innerH)
        }
        placeUnresolvedChildren(ctx, state, innerW, innerH)
        return state
    }

    /**
     * Bề rộng nội dung thật (toạ độ inner) tính từ phía START của mỗi child,
     * BỎ QUA phần kéo giãn do neo tới cạnh xa của PARENT:
     * - child có start-anchor → right = startAnchor + width (đã đo).
     * - child không có start-anchor (chỉ end-to-parent / không neo ngang) →
     *   chỉ tính width (coi như đặt ở mép trái content; sẽ được dời sang phải
     *   ở pass sau khi đã biết bề rộng content).
     */
    private fun contentExtentW(state: ConstraintMeasureState, innerW: Int): Int {

        var maxRight = 0
        children.forEach { child ->

            val b = state.bounds[child.id] ?: return@forEach
            val width = b[2] - b[0]
            val hasStart = child.startToStartOf != null || child.startToEndOf != null
            val right = if (hasStart) startAnchor(child, state.bounds) + width else width
            if (right > maxRight) maxRight = right
        }
        return maxRight.coerceIn(0, innerW)
    }

    private fun contentExtentH(state: ConstraintMeasureState, innerH: Int): Int {

        var maxBottom = 0
        children.forEach { child ->

            val b = state.bounds[child.id] ?: return@forEach
            val height = b[3] - b[1]
            val hasTop = child.topToTopOf != null || child.topToBottomOf != null
            val bottom = if (hasTop) topAnchor(child, state.bounds) + height else height
            if (bottom > maxBottom) maxBottom = bottom
        }
        return maxBottom.coerceIn(0, innerH)
    }

    private fun createMeasureState(innerW: Int, innerH: Int): ConstraintMeasureState {

        // bounds[id] = [left, top, right, bottom] trong toạ độ inner
        val bounds = HashMap<String, IntArray>(children.size + 1)
        bounds[PARENT] = intArrayOf(0, 0, innerW, innerH)

        return ConstraintMeasureState(
            bounds = bounds,
            specs = HashMap(children.size),
            remaining = children.toMutableList()
        )
    }

    private fun resolveReadyChildren(
        ctx: MeasureContext,
        state: ConstraintMeasureState,
        innerW: Int,
        innerH: Int
    ) {

        // Snapshot để an toàn khi remove trong lúc duyệt.
        state.remaining.toList().forEach { child ->

            resolveReadyChild(ctx, state, child, innerW, innerH)
        }
    }

    private fun resolveReadyChild(
        ctx: MeasureContext,
        state: ConstraintMeasureState,
        child: ConstraintChild,
        innerW: Int,
        innerH: Int
    ) {

        if (!canResolve(child, state.bounds)) return

        val measured = measureResolvedChild(ctx, child, state.bounds, innerW, innerH)
        val left = resolveLeft(child, state.bounds, measured.width, innerW)
        val top = resolveTop(child, state.bounds, measured.height, innerH)

        state.specs[child.id] = measured.spec
        state.bounds[child.id] = intArrayOf(left, top, left + measured.width, top + measured.height)
        state.remaining.remove(child)
    }

    private fun placeUnresolvedChildren(
        ctx: MeasureContext,
        state: ConstraintMeasureState,
        innerW: Int,
        innerH: Int
    ) {

        // Fallback: child còn lại (dependency cycle hoặc thiếu constraint).
        state.remaining.forEach { child ->

            val measured = measureFallbackChild(ctx, child, innerW, innerH)
            state.specs[child.id] = measured.spec
            state.bounds[child.id] = intArrayOf(0, 0, measured.width, measured.height)
        }
    }

    private fun measureResolvedChild(
        ctx: MeasureContext,
        child: ConstraintChild,
        bounds: Map<String, IntArray>,
        innerW: Int,
        innerH: Int
    ): MeasuredChild {

        val availableWidth = availW(child, bounds, innerW)
        val availableHeight = availH(child, bounds, innerH)
        val measuredSpec = ctx.measure(
            child.node,
            Constraints(availableWidth.coerceAtLeast(0), availableHeight.coerceAtLeast(0)),
            0,
            0
        )

        return measuredSpec.toMeasuredChild(child, availableWidth, availableHeight)
    }

    private fun measureFallbackChild(
        ctx: MeasureContext,
        child: ConstraintChild,
        innerW: Int,
        innerH: Int
    ): MeasuredChild {

        val measuredSpec = ctx.measure(child.node, Constraints(innerW, innerH), 0, 0)
        return measuredSpec.toMeasuredChild(child, innerW, innerH)
    }

    private fun DrawSpec.toMeasuredChild(
        child: ConstraintChild,
        availableWidth: Int,
        availableHeight: Int
    ): MeasuredChild {

        val width = resolveChildWidth(child, this, availableWidth)
        val height = resolveChildHeight(child, this, availableHeight)

        return MeasuredChild(
            spec = withSize(width, height),
            width = width,
            height = height
        )
    }

    private fun resolveChildWidth(
        child: ConstraintChild,
        measuredSpec: DrawSpec,
        availableWidth: Int
    ): Int =
        when (val dimension = child.width) {

            is LayoutDimension.Fixed -> dimension.px
            LayoutDimension.MatchParent -> availableWidth
            LayoutDimension.WrapContent -> measuredSpec.width
        }

    private fun resolveChildHeight(
        child: ConstraintChild,
        measuredSpec: DrawSpec,
        availableHeight: Int
    ): Int =
        when (val dimension = child.height) {

            is LayoutDimension.Fixed -> dimension.px
            LayoutDimension.MatchParent -> availableHeight
            LayoutDimension.WrapContent -> measuredSpec.height
        }

    private fun buildPlacedSpecs(state: ConstraintMeasureState, p: EdgeInsets): List<DrawSpec> =
        children.mapNotNull { child ->

            val bounds = state.bounds[child.id] ?: return@mapNotNull null
            state.specs[child.id]?.withPosition(p.left + bounds[0], p.top + bounds[1])
        }

    private fun naturalWidth(bounds: Map<String, IntArray>, p: EdgeInsets): Int =
        (children.mapNotNull { bounds[it.id]?.get(2) }.maxOrNull() ?: 0) + p.horizontal

    private fun naturalHeight(bounds: Map<String, IntArray>, p: EdgeInsets): Int =
        (children.mapNotNull { bounds[it.id]?.get(3) }.maxOrNull() ?: 0) + p.vertical

    // ── Dependency check ────────────────────────────────────────────────────

    /**
     * Tất cả ID được tham chiếu trong constraint của [child] đã resolved chưa.
     */
    private fun canResolve(child: ConstraintChild, bounds: Map<String, IntArray>): Boolean =
        listOfNotNull(
            child.startToStartOf, child.startToEndOf,
            child.endToEndOf,     child.endToStartOf,
            child.topToTopOf,     child.topToBottomOf,
            child.bottomToBottomOf, child.bottomToTopOf
        ).all { target ->

            target in bounds
        }

    // ── Available size ──────────────────────────────────────────────────────

    /**
     * Width khả dụng truyền vào [MeasureContext.measure]:
     * - [LayoutDimension.MatchParent] → khoảng giữa các anchor ngang.
     * - [LayoutDimension.Fixed]       → giá trị cố định.
     * - [LayoutDimension.WrapContent] → innerW (child tự co lại sau khi đo).
     */
    private fun availW(child: ConstraintChild, bounds: Map<String, IntArray>, innerW: Int): Int =
        when (val d = child.width) {

            is LayoutDimension.Fixed -> d.px
            LayoutDimension.MatchParent ->
                (endAnchor(child, bounds, innerW) - startAnchor(child, bounds)).coerceAtLeast(0)
            LayoutDimension.WrapContent -> innerW
        }

    private fun availH(child: ConstraintChild, bounds: Map<String, IntArray>, innerH: Int): Int =
        when (val d = child.height) {

            is LayoutDimension.Fixed -> d.px
            LayoutDimension.MatchParent ->
                (bottomAnchor(child, bounds, innerH) - topAnchor(child, bounds)).coerceAtLeast(0)
            LayoutDimension.WrapContent -> innerH
        }

    // ── Anchor helpers (tọa độ inner) ──────────────────────────────────────

    private fun startAnchor(c: ConstraintChild, b: Map<String, IntArray>): Int =
        when {

            c.startToStartOf != null -> b[c.startToStartOf]!![0] + c.marginStart
            c.startToEndOf   != null -> b[c.startToEndOf]!![2]   + c.marginStart
            else                     -> c.marginStart
        }

    private fun endAnchor(c: ConstraintChild, b: Map<String, IntArray>, innerW: Int): Int =
        when {

            c.endToEndOf   != null -> b[c.endToEndOf]!![2]   - c.marginEnd
            c.endToStartOf != null -> b[c.endToStartOf]!![0] - c.marginEnd
            else                   -> innerW - c.marginEnd
        }

    private fun topAnchor(c: ConstraintChild, b: Map<String, IntArray>): Int =
        when {

            c.topToTopOf    != null -> b[c.topToTopOf]!![1]    + c.marginTop
            c.topToBottomOf != null -> b[c.topToBottomOf]!![3] + c.marginTop
            else                    -> c.marginTop
        }

    private fun bottomAnchor(c: ConstraintChild, b: Map<String, IntArray>, innerH: Int): Int =
        when {

            c.bottomToBottomOf != null -> b[c.bottomToBottomOf]!![3] - c.marginBottom
            c.bottomToTopOf    != null -> b[c.bottomToTopOf]!![1]    - c.marginBottom
            else                       -> innerH - c.marginBottom
        }

    // ── Position resolution ─────────────────────────────────────────────────

    /**
     * Giải left position (toạ độ inner).
     *
     * - Có cả start + end → dùng [ConstraintChild.horizontalBias] để phân phối
     *   khoảng trống giữa 2 anchor.
     * - Chỉ có start → hugged start.
     * - Chỉ có end   → hugged end.
     * - Không có gì  → 0.
     */
    private fun resolveLeft(c: ConstraintChild, b: Map<String, IntArray>, cw: Int, innerW: Int): Int {

        val hasStart = c.startToStartOf != null || c.startToEndOf != null
        val hasEnd   = c.endToEndOf     != null || c.endToStartOf != null
        val sa = if (hasStart) startAnchor(c, b) else null
        val ea = if (hasEnd) endAnchor(c, b, innerW) else null
        return when {

            hasStart && hasEnd ->
                sa!! + ((ea!! - sa - cw).coerceAtLeast(0) * c.horizontalBias).toInt()
            hasStart -> sa!!
            hasEnd   -> ea!! - cw
            else     -> 0
        }.coerceAtLeast(0)
    }

    /**
     * Giải top position (toạ độ inner).
     *
     * Logic đối xứng với [resolveLeft] theo trục dọc.
     */
    private fun resolveTop(c: ConstraintChild, b: Map<String, IntArray>, ch: Int, innerH: Int): Int {

        val hasTop    = c.topToTopOf       != null || c.topToBottomOf    != null
        val hasBottom = c.bottomToBottomOf != null || c.bottomToTopOf    != null
        val ta = if (hasTop) topAnchor(c, b) else null
        val ba = if (hasBottom) bottomAnchor(c, b, innerH) else null
        return when {

            hasTop && hasBottom ->
                ta!! + ((ba!! - ta - ch).coerceAtLeast(0) * c.verticalBias).toInt()
            hasTop    -> ta!!
            hasBottom -> ba!! - ch
            else      -> 0
        }.coerceAtLeast(0)
    }

}
