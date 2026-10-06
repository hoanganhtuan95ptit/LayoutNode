package com.simple.ui.precompute

import android.graphics.Canvas
import android.os.Build
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
     * Chủ yếu để tầng trên nhận diện / diff spec (vd TransitionSpec so children
     * theo id, [com.simple.ui.precompute.node.KeyedNode]) và cho hit-test.
     * [LayoutEngine] KHÔNG dùng nó để cache hay reuse — mỗi lần đo ra một cây
     * spec mới hoàn toàn.
     *
     * `open` + mặc định `null` cho tương thích ngược; concrete spec nên override
     * và trả về node đã tạo ra chính nó (thường qua constructor param).
     */
    open val node: LayoutNode? = null

    val right: Int get() = left + width
    val bottom: Int get() = top + height

    /** Gọi từ parent (View hoặc GroupSpec). Toạ độ canvas hiện tại = parent. */
    fun draw(canvas: Canvas) {
        val l = left.toFloat()
        val t = top.toFloat()
        val r = right.toFloat()
        val b = bottom.toFloat()
        // Canvas.quickReject(FFFF) không-EdgeType chỉ có từ API 30 (Android 11).
        // Trên API 24–29 phải dùng overload cũ có EdgeType, nếu không sẽ
        // NoSuchMethodError ở runtime khi app compile với SDK ≥ 30.
        val rejected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            canvas.quickReject(l, t, r, b)
        } else {
            @Suppress("DEPRECATION")
            canvas.quickReject(l, t, r, b, Canvas.EdgeType.AA)
        }
        if (rejected) return

        val saved = canvas.save()
        canvas.translate(l, t)
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
     * Cùng một [DrawSpec] có thể xuất hiện **nhiều lần / ở cả cây cũ lẫn mới**
     * khi tầng trên tái dùng instance — vd `withPosition` trả copy dùng CHUNG
     * spec con bên trong, hay TransitionSpec giữ lại child qua các scene.
     * ([LayoutEngine] không cache, nên share là do caller chủ động, không phải
     * engine.) Nếu container cứ gọi thẳng [onAttachedToRuntime] /
     * [onDetachedFromRuntime] khi đệ quy vào children, shared ref sẽ chịu chu kỳ
     * detach→attach vô ích — animator (OutlineSpec) restart, scope (ImageSpec)
     * huỷ+tái tạo, callback re-set.
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

    /** Toạ độ tuyệt đối (trong View) của spec — hợp lệ khi đã attach. */
    val viewLeft: Int get() = runtimeLeft
    val viewTop: Int get() = runtimeTop
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
     * Hit-test tại điểm ([x], [y]) trong hệ toạ độ **local của parent**
     * (view space nếu spec này là root).
     *
     * Trả về spec sâu nhất (top-most con) có [LayoutNode.isInteractive] (click /
     * long-click / touch) bao phủ điểm này. Không có → null.
     *
     * Base impl chỉ check bounds + [node]. Container spec (GroupSpec, SizedSpec)
     * override để đệ quy vào children — child vẽ sau (topmost) được ưu tiên.
     */
    open fun hitTest(x: Int, y: Int): DrawSpec? {
        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null
        return if (node?.isInteractive == true) this else null
    }

    /**
     * Duyệt các spec con trực tiếp (nếu có). Mặc định leaf, không con. Container
     * override để liệt kê con. Dùng cho [collectInteractive] (accessibility).
     */
    open fun forEachChildSpec(action: (DrawSpec) -> Unit) {}

    /**
     * Gom mọi spec [isInteractive] trong subtree (kèm chính nó) vào [out] —
     * phục vụ [PrecomputedA11yHelper] expose virtual view cho TalkBack / UI test.
     */
    fun collectInteractive(out: MutableList<DrawSpec>) {
        if (node?.isInteractive == true) out.add(this)
        forEachChildSpec { it.collectInteractive(out) }
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

    override fun forEachChildSpec(action: (DrawSpec) -> Unit) = action(child)

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
        return if (node?.isInteractive == true) this else null
    }
}
