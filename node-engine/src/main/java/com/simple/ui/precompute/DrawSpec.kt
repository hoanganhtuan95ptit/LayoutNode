package com.simple.ui.precompute

import android.graphics.Canvas
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode

// ─────────────────────────────────────────────────────────────────────────────
// DrawSpec — abstract base class cho mọi "lệnh vẽ" đã đo sẵn.
// Concrete spec types nằm trong file riêng:
//   TextSpec  → TextNode.kt
//   ImageSpec → ImageNode.kt
//   GroupSpec → LinearNode.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Một "lệnh vẽ" đã đo sẵn. Tự biết vị trí, kích thước và cách vẽ chính mình.
 *
 * Base class lo wrap canvas.save() / translate() / restore() — subclass chỉ
 * cần implement [onDrawContent] để vẽ trong toạ độ local (đã translate sẵn).
 *
 * Mở rộng: muốn thêm Border, Divider, Shape... → tạo class mới kế thừa
 * [DrawSpec], không cần sửa View hay LayoutEngine (nếu tự đo).
 */
abstract class DrawSpec {

    abstract val left: Int
    abstract val top: Int
    abstract val width: Int
    abstract val height: Int

    /**
     * [LayoutNode] gốc đã sinh ra spec này.
     *
     * Dùng để so sánh khi cập nhật spec: nếu node mới structurally-equal với
     * node của spec cũ, [PrecomputedDelegate] có thể tái sử dụng spec cũ,
     * tránh requestLayout / mất state runtime (drawable, animator...).
     *
     * `open` + mặc định `null` cho tương thích ngược; concrete spec nên override
     * và trả về node đã tạo ra chính nó (thường qua constructor param).
     */
    open val node: LayoutNode? = null

    val right: Int get() = left + width
    val bottom: Int get() = top + height

    /** Gọi từ parent (View hoặc GroupSpec). Toạ độ canvas hiện tại = parent. */
    fun draw(canvas: Canvas) {
        if (canvas.quickReject(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())) return

        val saved = canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        canvas.clipRect(0, 0, width, height)
        onDrawContent(canvas)
        canvas.restoreToCount(saved)
    }

    /** Vẽ nội dung trong toạ độ local của spec này. */
    protected abstract fun onDrawContent(canvas: Canvas)

    /** Trả về copy với (left, top) mới — phục vụ layout engine khi assign vị trí. */
    abstract fun withPosition(newLeft: Int, newTop: Int): DrawSpec

    /**
     * Trả về copy với kích thước mới. Mặc định bọc spec hiện tại trong một
     * measured box; spec nào phụ thuộc trực tiếp vào bounds có thể override.
     */
    open fun withSize(newWidth: Int, newHeight: Int): DrawSpec {
        val w = newWidth.coerceAtLeast(0)
        val h = newHeight.coerceAtLeast(0)
        return if (width == w && height == h) {
            this
        } else {
            SizedSpec(left, top, w, h, withPosition(0, 0))
        }
    }

    /**
     * Reference-counted attach.
     *
     * Cùng một [DrawSpec] có thể nằm trong **cả cây spec cũ lẫn cây spec mới**
     * (do cache-by-id trong [MeasureContext] tái sử dụng). Nếu container cứ
     * gọi thẳng [onAttachedToRuntime] / [onDetachedFromRuntime] khi đệ quy vào
     * children, shared ref sẽ chịu chu kỳ detach→attach vô ích — animator
     * (OutlineSpec) restart, scope (ImageSpec) huỷ+tái tạo, callback re-set.
     *
     * Counter đảm bảo:
     * - [attach]: chỉ gọi [onAttachedToRuntime] khi counter đi từ 0→1
     *   (lần đầu spec này gắn vào view).
     * - [detach]: chỉ gọi [onDetachedFromRuntime] khi counter đi từ 1→0
     *   (spec không còn nằm trong tree nào của view).
     *
     * Container spec (GroupSpec, SizedSpec) đệ quy children qua **[attach] /
     * [detach]** (không phải [onAttachedToRuntime] / [onDetachedFromRuntime]).
     * Delegate cũng gọi [attach] / [detach] chứ không gọi thẳng hook.
     */
    fun attach(runtime: PrecomputedRuntime, parentLeft: Int = 0, parentTop: Int = 0) {
        val wasZero = attachCount == 0
        runtimeRef = runtime
        runtimeLeft = parentLeft + left
        runtimeTop = parentTop + top
        attachCount++
        if (wasZero) onAttachedToRuntime(runtime)
    }

    fun detach(runtime: PrecomputedRuntime) {
        if (attachCount == 0) return
        attachCount--
        if (attachCount == 0) {

            onDetachedFromRuntime(runtime)
            runtimeRef = null
        }
    }

    private var attachCount: Int = 0
    private var runtimeRef: PrecomputedRuntime? = null
    protected var runtimeLeft: Int = 0
        private set
    protected var runtimeTop: Int = 0
        private set
    protected val runtime: PrecomputedRuntime?
        get() = runtimeRef

    protected fun requestDraw() {

        runtimeRef?.requestDraw(runtimeLeft, runtimeTop, runtimeLeft + width, runtimeTop + height)
    }

    /**
     * Hook cho subclass setup (start animator, kick off async load...).
     *
     * KHÔNG gọi trực tiếp từ container hay delegate — dùng [attach] để đi
     * qua reference counter. Container recurse vào children cũng phải qua
     * `child.attach(runtime)`.
     */
    open fun onAttachedToRuntime(runtime: PrecomputedRuntime) {}

    /**
     * Hook cho subclass teardown (stop animator, cancel scope, clear callback...).
     *
     * KHÔNG gọi trực tiếp — dùng [detach] để đi qua reference counter.
     */
    open fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {}

    /**
     * Spec này (đã đo trước) có còn dùng được dưới constraint mới [c] không.
     *
     * Dùng bởi [MeasureContext] khi tra cache theo `node.id` — trước khi bỏ
     * qua `node.measure()`, ta phải chắc kết quả nếu đo lại vẫn ra đúng
     * kích thước hiện tại.
     *
     * Logic suy từ [LayoutDimension.resolve] của mỗi axis:
     * - **Fixed(px)**: `resolve = min(px, max)`. Reuse an toàn khi
     *   `cached == px && px <= max` (cached chưa từng bị cap và max mới đủ chỗ).
     * - **MatchParent**: `resolve = max` (unbounded → wrap). Reuse an toàn
     *   khi `cached == max`. Trường hợp max mới unbounded thì skip cache
     *   vì kết quả sẽ phụ thuộc contentSize không lưu ở đây.
     * - **WrapContent**: `resolve = min(content, max)`. Bảo thủ: chỉ reuse
     *   khi `cached < max` — vì nếu `cached == max` thì có thể đã bị cap
     *   (content > max), lần đo mới với max khác sẽ ra khác. Trường hợp
     *   `cached < max` giả định content chính bằng cached (uncapped).
     *
     * Trả về `false` khi [node] null (spec ẩn danh — không đủ metadata suy).
     */
    open fun canReuseUnder(c: Constraints): Boolean {
        val n = node ?: return false
        return axisReusable(n.layoutWidth, width, c.maxWidth) &&
                axisReusable(n.layoutHeight, height, c.maxHeight)
    }

    /**
     * Hit-test tại điểm ([x], [y]) trong hệ toạ độ **local của parent**
     * (view space nếu spec này là root).
     *
     * Trả về spec sâu nhất (top-most con) có `node?.onClick != null` bao phủ
     * điểm này. Không có → null.
     *
     * Base impl chỉ check bounds + [LayoutNode.onClick] của [node]. Container
     * spec (GroupSpec, SizedSpec) override để đệ quy vào children — child vẽ
     * sau (topmost) được ưu tiên.
     */
    open fun hitTest(x: Int, y: Int): DrawSpec? {
        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null
        return if (node?.onClick != null) this else null
    }

    private fun axisReusable(mode: LayoutDimension, cached: Int, maxAvail: Int): Boolean {
        if (maxAvail == Int.MAX_VALUE) {
            // Unbounded parent: Fixed & WrapContent chỉ phụ thuộc node content,
            // cached vẫn đúng. MatchParent unbounded falls back to wrap semantics
            // → không đủ info để so, skip cache cho case này.
            return mode !is LayoutDimension.MatchParent
        }
        return when (mode) {
            is LayoutDimension.Fixed -> mode.px == cached && cached <= maxAvail
            LayoutDimension.MatchParent -> cached == maxAvail
            LayoutDimension.WrapContent -> cached < maxAvail
        }
    }
}

/**
 * A measured box that delegates drawing/lifecycle to a child spec.
 * Used when parent layout rules force a size different from the child's
 * natural measured size.
 */
internal open class SizedSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val child: DrawSpec
) : DrawSpec() {

    /** Delegate node cho child — SizedSpec chỉ là size adapter, không phải node riêng. */
    override val node: LayoutNode?
        get() = child.node

    override fun onDrawContent(canvas: Canvas) {
        child.draw(canvas)
    }

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec =
        SizedSpec(newLeft, newTop, width, height, child)

    override fun withSize(newWidth: Int, newHeight: Int): DrawSpec {
        val w = newWidth.coerceAtLeast(0)
        val h = newHeight.coerceAtLeast(0)
        return if (width == w && height == h) this else SizedSpec(left, top, w, h, child)
    }

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {
        child.attach(runtime, runtimeLeft, runtimeTop)
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {
        child.detach(runtime)
    }

    override fun hitTest(x: Int, y: Int): DrawSpec? {
        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null
        // Child được đặt tại (0,0) trong local space của SizedSpec — xem withSize.
        val hit = child.hitTest(lx, ly)
        if (hit != null) return hit
        return if (node?.onClick != null) this else null
    }
}
