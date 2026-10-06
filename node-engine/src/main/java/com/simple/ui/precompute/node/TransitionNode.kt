package com.simple.ui.precompute.node

import android.graphics.Canvas
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.PrecomputedRuntime

// ─────────────────────────────────────────────────────────────────────────────
// TransitionNode — container cho animation layout giống Android TransitionManager.
//
// Ý tưởng: giữ một "scene" (danh sách DrawSpec con đã đặt vị trí). Khi được đẩy
// sang scene mới qua [TransitionSpec.transitionTo], nó diff theo `node.id` của
// mỗi child (xem [KeyedNode]) rồi animate:
//
//   • CHANGE_BOUNDS — child có ở cả hai scene nhưng đổi vị trí → trượt từ cũ
//     sang mới (nội suy rect lúc vẽ, KHÔNG re-measure).
//   • ENTER         — child chỉ có ở scene mới → fade/scale vào.
//   • EXIT          — child chỉ có ở scene cũ → fade/scale ra (vẫn giữ spec cũ
//     + attach để ảnh/animatable của nó còn sống tới khi biến mất hẳn).
//
// Trigger theo kiểu `beginDelayedTransition`: imperative. Giữ ref tới
// [TransitionSpec] rồi gọi `transitionTo(newChildren)`.
// ─────────────────────────────────────────────────────────────────────────────

/** Kiểu enter/exit cho child xuất hiện / biến mất. */
enum class TransitionType {

    NONE,
    FADE,
    SCALE,
    FADE_SCALE
}

/**
 * Cách xử lý node đổi vị trí/size (track Change):
 * - [CLIP]: nội suy khung, nội dung ở layout đích, CLIP theo bounds — giống
 *   `ChangeBounds` của Android (co/giãn có thể thấy "cắt" nội dung).
 * - [MORPH]: trượt + **SCALE** nội dung theo khung + **cross-fade** nội dung cũ
 *   sang mới (kiểu Material container transform) — mượt, không cắt, và chuyển
 *   được cả khi nội dung đổi.
 */
enum class BoundsMode {

    CLIP,
    MORPH
}

data class TransitionConfig(
    val changeBounds: Boolean = true,
    val enterExit: TransitionType = TransitionType.FADE,
    val durationMs: Long = 260L,
    val boundsMode: BoundsMode = BoundsMode.CLIP
)

data class TransitionNode(
    override val orientation: Orientation,
    override val children: List<LayoutNode>,
    val config: TransitionConfig = TransitionConfig(),
    /**
     * Khóa "trạng thái" của scene. Đổi giá trị này giữa 2 lần dựng cây = yêu
     * cầu animate thay đổi (giống đổi scene cho TransitionManager). Giữ nguyên
     * = dựng lại mà không animate. Cần [id] khác null để nhớ scene trước.
     */
    val transitionKey: Any? = null,
    override val gap: Int = 0,
    override val crossAlign: CrossAlign = CrossAlign.START,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val id: Any? = null,
    override val onClick: (() -> Unit)? = null,
    override val onLongClick: (() -> Unit)? = null,
    override val onTouch: ((NodeTouch) -> Boolean)? = null,
    override val contentDescription: String? = null,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent
) : LayoutNode(), LinearMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): GroupSpec =
        TransitionMeasurePolicy().measure(this, ctx, c, x, y)
}

/** Tái dùng toàn bộ cách xếp con của Linear, chỉ đổi spec trả về. */
open class TransitionMeasurePolicy : LinearMeasurePolicy<TransitionNode>() {

    override fun createSpec(
        node: TransitionNode,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        children: List<DrawSpec>
    ): GroupSpec {

        return TransitionSpec(
            left, top, width, height, children, node, node.config,
            transitionKey = node.transitionKey,
            sceneKey = node.id
        )
    }
}

/**
 * Scene-aware [GroupSpec]. Idle thì vẽ như group thường; khi [transitionTo]
 * được gọi, chạy animation diff giữa scene hiện tại và scene mới.
 *
 * Mọi thao tác (transitionTo, frame callback, draw) đều trên main thread nên
 * không cần đồng bộ.
 */
open class TransitionSpec(
    left: Int,
    top: Int,
    w0: Int,
    h0: Int,
    initialChildren: List<DrawSpec>,
    node: LayoutNode,
    config: TransitionConfig,
    /** Thay đổi giữa 2 scene = animate. Null = chỉ dùng [transitionTo] imperative. */
    private val transitionKey: Any? = null,
    /** Key để nhớ scene trước trong [PrecomputedRuntime]. Null = không rebuild-trigger. */
    private val sceneKey: Any? = null
) : GroupSpec(left, top, w0, h0, initialChildren, node) {

    var config: TransitionConfig = config
        private set

    // Size "ổn định" = size của scene đích. Trong lúc animate, spec báo size =
    // max(scene cũ, scene mới) để không cắt nội dung; xong thì co/giãn về idle.
    // onMeasure của PrecomputedView đọc width/height này, nên khi đổi size cần
    // gọi requestRemeasure ở lúc bắt đầu và lúc kết thúc transition.
    private var idleWidth: Int = w0
    private var idleHeight: Int = h0
    private var animMaxWidth: Int = w0
    private var animMaxHeight: Int = h0

    override val width: Int get() = if (animating) animMaxWidth else idleWidth

    override val height: Int get() = if (animating) animMaxHeight else idleHeight

    /** Scene đã "ổn định" hiện tại (đích của transition đang chạy, nếu có). */
    private var currentChildren: List<DrawSpec> = initialChildren

    /** Spec cũ của các child đang exit — giữ attach tới khi fade-out xong. */
    private var exitingChildren: List<DrawSpec> = emptyList()

    private var tracks: List<Track> = emptyList()
    /** Cây END đã bỏ các node tracked — vẽ "nền" tĩnh (structure không id). */
    private var backgroundChildren: List<DrawSpec> = emptyList()
    /** Có ít nhất 1 Change cần cross-fade (MORPH + nội dung đổi) → gom 1 lớp. */
    private var hasCrossFade = false
    private var animating = false
    private var progress = 1f

    private var frameRegistration: PrecomputedRuntime.FrameRegistration? = null
    private var lastFrameMs = 0L
    private val frameCallback = PrecomputedRuntime.FrameCallback { frameTimeNanos ->

        onFrame(frameTimeNanos)
    }

    /**
     * Đẩy sang scene mới [newChildren] (đã đặt vị trí trong local của spec này).
     * Diff theo `node.id` rồi animate. Gọi lại khi đang chạy sẽ bắt đầu
     * transition mới từ scene đích hiện tại.
     */
    fun transitionTo(
        newChildren: List<DrawSpec>,
        newConfig: TransitionConfig = config,
        endWidth: Int = idleWidth,
        endHeight: Int = idleHeight
    ) {

        config = newConfig
        val prevW = idleWidth
        val prevH = idleHeight
        idleWidth = endWidth
        idleHeight = endHeight
        animMaxWidth = maxOf(prevW, endWidth)
        animMaxHeight = maxOf(prevH, endHeight)
        retarget(newChildren)
        runtime?.requestRemeasure()
    }

    private fun retarget(end: List<DrawSpec>) {

        val rt = runtime

        // Interrupt liền mạch: nếu đang animate, lấy rect ĐANG hiển thị (đã nội
        // suy ở progress hiện tại) làm start cho lượt mới → child không nhảy về
        // scene đích cũ rồi mới chạy.
        val startRectOverride = if (animating) currentRectsById() else emptyMap()

        // Bị chen giữa chừng: drop các exit spec còn dang dở của lượt trước
        // (đã attach) để không rò; lượt mới sẽ tính exit từ scene hiện tại.
        if (rt != null) exitingChildren.forEach { it.detach(rt) }
        exitingChildren = emptyList()

        val start = currentChildren
        val endIds = end.mapNotNull { it.node?.id }.toHashSet()
        val stillExiting = start.filter { (it.node?.id) !in endIds }
        val replacedStart = start.filter { (it.node?.id) in endIds }

        prepareTransition(start, end, startRectOverride)

        if (rt != null) {

            // replacedStart: instance cũ của child persist → bị thay bằng
            // instance mới (end), detach. stillExiting: đã attach từ scene
            // trước, giữ nguyên. end: instance mới, attach.
            replacedStart.forEach { it.detach(rt) }
            end.forEach { it.attach(rt, runtimeLeft, runtimeTop) }
        }

        exitingChildren = stillExiting
        currentChildren = end
        progress = 0f
        animating = true
        lastFrameMs = 0L
        startAnimating()
        requestDraw()
    }

    /**
     * Deep capture: thu thập node có `id` ở MỌI độ sâu (dừng tại node tracked —
     * subtree đi theo nó) với toạ độ TUYỆT ĐỐI, diff start↔end → [tracks], và
     * dựng [backgroundChildren] = cây end bỏ các node tracked (phần "nền" tĩnh).
     */
    private fun prepareTransition(
        start: List<DrawSpec>,
        end: List<DrawSpec>,
        startRectOverride: Map<Any?, IntRect>
    ) {

        val startT = LinkedHashMap<Any, Pair<DrawSpec, IntRect>>()
        start.forEach { collectTarget(it, 0, 0, startT) }
        val endT = LinkedHashMap<Any, Pair<DrawSpec, IntRect>>()
        end.forEach { collectTarget(it, 0, 0, endT) }

        val out = ArrayList<Track>(startT.size + endT.size)
        endT.forEach { (id, pair) ->

            val s = startT[id]
            if (s == null) {

                out.add(Track.Enter(pair.first, pair.second))
            } else {

                // Ưu tiên rect đang-hiển-thị (interrupt), fallback rect scene cũ.
                out.add(Track.Change(pair.first, startRectOverride[id] ?: s.second, pair.second, s.first))
            }
        }
        startT.forEach { (id, pair) ->

            if (id !in endT) out.add(Track.Exit(pair.first, pair.second))
        }

        tracks = out
        backgroundChildren = end.mapNotNull { stripTracked(it, endT.keys) }
        hasCrossFade = config.boundsMode == BoundsMode.MORPH &&
                config.changeBounds &&
                out.any { it is Track.Change && it.fromSpec.node != it.spec.node }
    }

    private fun isCrossFade(t: Track.Change): Boolean =
        config.boundsMode == BoundsMode.MORPH && config.changeBounds && t.fromSpec.node != t.spec.node

    /** Gom node tracked (dừng tại node có id) kèm abs rect vào [out]. */
    private fun collectTarget(
        s: DrawSpec,
        baseLeft: Int,
        baseTop: Int,
        out: LinkedHashMap<Any, Pair<DrawSpec, IntRect>>
    ) {

        val absL = baseLeft + s.left
        val absT = baseTop + s.top
        val id = s.node?.id
        if (id != null) {

            out[id] = s to IntRect(absL, absT, s.width, s.height)
        } else {

            s.forEachChildSpec { child -> collectTarget(child, absL, absT, out) }
        }
    }

    /** Trả về [spec] đã bỏ mọi node tracked (null nếu chính nó tracked). */
    private fun stripTracked(spec: DrawSpec, trackedIds: Set<Any>): DrawSpec? {

        val id = spec.node?.id
        if (id != null && id in trackedIds) return null
        if (spec !is GroupSpec) return spec

        val kept = ArrayList<DrawSpec>(spec.children.size)
        var changed = false
        spec.children.forEach { c ->

            val r = stripTracked(c, trackedIds)
            if (r == null) changed = true else { kept.add(r); if (r !== c) changed = true }
        }
        if (!changed) return spec
        return GroupSpec(spec.left, spec.top, spec.width, spec.height, kept, spec.node)
    }

    override fun onDrawContent(canvas: Canvas) {

        if (!animating) {

            val list = currentChildren
            for (i in list.indices) list[i].draw(canvas)
            return
        }

        val p = ease(progress)

        // 1. Nền tĩnh (structure không id).
        for (i in backgroundChildren.indices) backgroundChildren[i].draw(canvas)

        // 2. Nội dung ĐÍCH (solid) của mọi Change — vẽ đặc, không lớp.
        val list = tracks
        for (i in list.indices) {

            val t = list[i]
            if (t is Track.Change) drawChangeTarget(canvas, t, p)
        }

        // 3. Gom TẤT CẢ nội dung CŨ của các Change cross-fade vào MỘT lớp
        //    (alpha = 1-p), đè lên nội dung mới → cross-fade sạch, 1 saveLayer
        //    cho mọi N (mọi node cùng progress nên chung 1 alpha).
        if (hasCrossFade) {

            val a = ((1f - p) * 255f).toInt().coerceIn(0, 255)
            val saved = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), a)
            for (i in list.indices) {

                val t = list[i]
                if (t is Track.Change && isCrossFade(t)) {

                    drawScaledFade(canvas, t.fromSpec, t.from, lerpRect(t.from, t.to, p), 1f)
                }
            }
            canvas.restoreToCount(saved)
        }

        // 4. Enter / Exit (thường ít node) — giữ per-node.
        for (i in list.indices) {

            when (val t = list[i]) {
                is Track.Enter -> drawAppearing(canvas, t.spec, t.at, p)
                is Track.Exit -> drawAppearing(canvas, t.spec, t.at, 1f - p)
                is Track.Change -> {}
            }
        }
    }

    override fun forEachChildSpec(action: (DrawSpec) -> Unit) {

        val list = currentChildren
        for (i in list.indices) action(list[i])
    }

    /** Vẽ nội dung ĐÍCH (mới) của một Change, SOLID (nội dung cũ gom ở lớp riêng). */
    private fun drawChangeTarget(canvas: Canvas, track: Track.Change, p: Float) {

        val spec = track.spec
        if (!config.changeBounds) {

            spec.withPosition(track.to.left, track.to.top).draw(canvas)
            return
        }

        val rect = lerpRect(track.from, track.to, p)
        if (config.boundsMode == BoundsMode.MORPH) {

            // MORPH: scale nội dung mới khít rect (mượt, không clip-cut).
            drawScaledFade(canvas, spec, track.to, rect, 1f)
        } else {

            // CLIP = ChangeBounds: nội dung ở layout đích, clip theo bounds nội suy.
            var drawn = spec.withPosition(rect.left, rect.top)
            if (rect.width != spec.width || rect.height != spec.height) drawn = drawn.withSize(rect.width, rect.height)
            drawn.draw(canvas)
        }
    }

    /** Vẽ [spec] (kích thước gốc = [src]) scale khít [dst] + alpha. */
    private fun drawScaledFade(canvas: Canvas, spec: DrawSpec, src: IntRect, dst: IntRect, alpha: Float) {

        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0f) return

        val sx = dst.width.toFloat() / src.width.coerceAtLeast(1)
        val sy = dst.height.toFloat() / src.height.coerceAtLeast(1)
        val positioned = spec.withPosition(dst.left, dst.top)

        val saved = canvas.save()
        if (a < 1f) {

            canvas.saveLayerAlpha(
                dst.left.toFloat(), dst.top.toFloat(),
                (dst.left + dst.width).toFloat(), (dst.top + dst.height).toFloat(),
                (a * 255).toInt()
            )
        }
        canvas.scale(sx, sy, dst.left.toFloat(), dst.top.toFloat())
        positioned.draw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun drawAppearing(canvas: Canvas, spec0: DrawSpec, at: IntRect, visibility: Float) {

        val v = visibility.coerceIn(0f, 1f)
        if (v <= 0f) return

        val spec = spec0.withPosition(at.left, at.top)
        val style = config.enterExit
        if (style == TransitionType.NONE) {

            if (v >= 0.5f) spec.draw(canvas)
            return
        }

        val saved = canvas.save()
        if (style == TransitionType.SCALE || style == TransitionType.FADE_SCALE) {

            val scale = 0.6f + 0.4f * v
            val pivotX = (spec.left + spec.width / 2).toFloat()
            val pivotY = (spec.top + spec.height / 2).toFloat()
            canvas.scale(scale, scale, pivotX, pivotY)
        }
        if (style == TransitionType.FADE || style == TransitionType.FADE_SCALE) {

            val l = spec.left.toFloat()
            val t = spec.top.toFloat()
            canvas.saveLayerAlpha(l, t, l + spec.width, t + spec.height, (v * 255).toInt())
        }
        spec.draw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {

        val prev = sceneKey?.let { runtime.stateGet(it) as? TransitionScene }
        if (prev != null && prev.key != transitionKey && !animating) {

            // Rebuild-trigger: scene trước (của spec cũ, giờ bị thay) khác scene
            // này → animate từ nó sang scene hiện tại.
            beginStoredTransition(runtime, prev)
        } else {

            currentChildren.forEach { it.attach(runtime, runtimeLeft, runtimeTop) }
            exitingChildren.forEach { it.attach(runtime, runtimeLeft, runtimeTop) }
        }

        // Ghi lại scene hiện tại làm "trạng thái đang hiển thị" cho lần sau.
        if (sceneKey != null) {

            runtime.statePut(sceneKey, TransitionScene(currentChildren, transitionKey, idleWidth, idleHeight))
        }
        if (animating) {

            startAnimating()
            runtime.requestRemeasure()
        }
    }

    private fun beginStoredTransition(rt: PrecomputedRuntime, prev: TransitionScene) {

        val start = prev.children
        animMaxWidth = maxOf(prev.width, idleWidth)
        animMaxHeight = maxOf(prev.height, idleHeight)

        prepareTransition(start, currentChildren, emptyMap())

        // Attach instance của scene mới (end). Các child persist bên start thuộc
        // spec cũ — chỉ mượn rect (track.from), không attach ở đây; spec cũ tự
        // detach chúng. Child exit (chỉ có ở start) thì attach để sống qua lúc
        // spec cũ detach (reference counter giữ > 0).
        currentChildren.forEach { it.attach(rt, runtimeLeft, runtimeTop) }

        val currentIds = currentChildren.mapNotNull { it.node?.id }.toHashSet()
        exitingChildren = start.filter { (it.node?.id) == null || it.node?.id !in currentIds }
        exitingChildren.forEach { it.attach(rt, runtimeLeft, runtimeTop) }

        progress = 0f
        animating = true
        lastFrameMs = 0L
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {

        stopAnimating()
        currentChildren.forEach { it.detach(runtime) }
        exitingChildren.forEach { it.detach(runtime) }
    }

    override fun hitTest(x: Int, y: Int): DrawSpec? {

        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null

        val list = currentChildren
        for (i in list.indices.reversed()) {

            val hit = list[i].hitTest(lx, ly)
            if (hit != null) return hit
        }
        return if (node.isInteractive) this else null
    }

    override fun withPosition(newLeft: Int, newTop: Int): GroupSpec {

        if (newLeft == left && newTop == top) return this
        return TransitionSpec(
            newLeft, newTop, idleWidth, idleHeight, currentChildren, node, config,
            transitionKey, sceneKey
        )
    }

    private fun onFrame(frameTimeNanos: Long) {

        val now = frameTimeNanos / 1_000_000L
        if (lastFrameMs == 0L) {

            lastFrameMs = now
            requestDraw()
            return
        }

        val dt = (now - lastFrameMs).coerceAtLeast(0L)
        lastFrameMs = now
        progress += dt.toFloat() / config.durationMs.coerceAtLeast(1L)
        if (progress >= 1f) {

            progress = 1f
            finishTransition()
        }
        requestDraw()
    }

    private fun finishTransition() {

        animating = false
        tracks = emptyList()
        backgroundChildren = emptyList()
        hasCrossFade = false
        val rt = runtime
        if (rt != null) exitingChildren.forEach { it.detach(rt) }
        exitingChildren = emptyList()
        stopAnimating()
        // Hết animate → width/height trả về idle; yêu cầu view đo lại đúng size.
        rt?.requestRemeasure()
    }

    private fun startAnimating() {

        val rt = runtime ?: return
        if (frameRegistration != null) return

        lastFrameMs = 0L
        frameRegistration = rt.registerFrameCallback(frameCallback)
    }

    private fun stopAnimating() {

        frameRegistration?.close()
        frameRegistration = null
    }

    private fun ease(t: Float): Float {

        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerp(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).toInt()

    private fun lerpRect(a: IntRect, b: IntRect, t: Float): IntRect =
        IntRect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.width, b.width, t), lerp(a.height, b.height, t))

    /** Rect đang hiển thị của từng id ở progress hiện tại (dùng cho interrupt). */
    private fun currentRectsById(): Map<Any?, IntRect> {

        val p = ease(progress)
        val map = HashMap<Any?, IntRect>(tracks.size)
        tracks.forEach { track ->

            when (track) {
                is Track.Change -> track.spec.node?.id?.let { map[it] = lerpRect(track.from, track.to, p) }
                is Track.Enter -> track.spec.node?.id?.let { map[it] = track.at }
                is Track.Exit -> track.spec.node?.id?.let { map[it] = track.at }
            }
        }
        return map
    }

    private sealed class Track {

        // Mọi rect là toạ độ TUYỆT ĐỐI trong TransitionSpec (deep capture).
        // [spec] = nội dung ĐÍCH, [fromSpec] = nội dung NGUỒN (cho MORPH cross-fade).
        class Change(val spec: DrawSpec, val from: IntRect, val to: IntRect, val fromSpec: DrawSpec) : Track()
        class Enter(val spec: DrawSpec, val at: IntRect) : Track()
        class Exit(val spec: DrawSpec, val at: IntRect) : Track()
    }

    protected class IntRect(val left: Int, val top: Int, val width: Int, val height: Int) {

        companion object {

            fun of(spec: DrawSpec) = IntRect(spec.left, spec.top, spec.width, spec.height)
        }
    }
}

/**
 * Snapshot một scene để [PrecomputedRuntime] nhớ qua các lần dựng lại spec.
 * [children] là các DrawSpec đã đặt vị trí của scene đang hiển thị;
 * [key] là transitionKey ứng với scene đó.
 */
class TransitionScene(
    val children: List<DrawSpec>,
    val key: Any?,
    val width: Int,
    val height: Int
)
