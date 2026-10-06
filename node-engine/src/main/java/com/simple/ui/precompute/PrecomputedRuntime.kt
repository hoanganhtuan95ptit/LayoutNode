package com.simple.ui.precompute

import android.view.Choreographer
import android.view.View

class PrecomputedRuntime(val view: View) {

    private val choreographer: Choreographer by lazy(LazyThreadSafetyMode.NONE) {

        Choreographer.getInstance()
    }

    /**
     * Kho state nhỏ keyed, sống theo vòng đời của [view] (một runtime / view).
     *
     * Vai trò giống `remember` của Compose: giữ lại trạng thái qua các lần cây
     * spec bị dựng lại (sau khi [LayoutEngine] bỏ cache, mỗi rebuild ra spec
     * mới — xem commit bỏ cache). [com.simple.ui.precompute.node.TransitionSpec]
     * dùng để nhớ scene trước đó và animate khi scene đổi.
     *
     * Chỉ truy cập trên main thread. Caller chịu trách nhiệm chọn key ổn định
     * và tự dọn ([stateRemove]) nếu slot không còn dùng.
     */
    private val stateStore = HashMap<Any, Any>()

    fun stateGet(key: Any): Any? = stateStore[key]

    fun statePut(key: Any, value: Any?) {

        if (value == null) stateStore.remove(key) else stateStore[key] = value
    }

    fun stateRemove(key: Any) {

        stateStore.remove(key)
    }

    private val frameCallbacks = LinkedHashSet<FrameCallback>()
    private val dispatchCallbacks = ArrayList<FrameCallback>()
    private var framePosted = false
    private var drawPosted = false
    private var layoutPosted = false
    private var dispatchingFrame = false
    private var drawRequestedInFrame = false
    private var attached = view.isAttachedToWindow

    // Vùng "bẩn" gom lại cho một lần invalidate. [dirtyFull] = invalidate cả
    // view; ngược lại chỉ invalidate union của các rect (toạ độ view). Lưu ý:
    // view HW-accelerated vẫn re-record cả RenderNode, rect chỉ thu hẹp damage
    // region cho compositor — lợi ích vừa phải nhưng đúng đắn hơn invalidate full.
    private var dirtyFull = false
    private var hasDirtyRect = false
    private var dirtyLeft = 0
    private var dirtyTop = 0
    private var dirtyRight = 0
    private var dirtyBottom = 0

    private val frameDispatcher = Choreographer.FrameCallback { frameTimeNanos ->

        framePosted = false
        if (!attached) return@FrameCallback

        dispatchCallbacks.clear()
        dispatchCallbacks.addAll(frameCallbacks)
        dispatchingFrame = true
        drawRequestedInFrame = false
        for (i in dispatchCallbacks.indices) {

            dispatchCallbacks[i].onFrame(frameTimeNanos)
        }
        dispatchCallbacks.clear()
        dispatchingFrame = false
        if (drawRequestedInFrame && attached) performInvalidate()
        drawRequestedInFrame = false
        postFrameIfNeeded()
    }

    fun onAttachedToWindow() {

        attached = true
        postFrameIfNeeded()
    }

    fun onDetachedFromWindow() {

        attached = false
        drawPosted = false
        layoutPosted = false
        dispatchingFrame = false
        drawRequestedInFrame = false
        dirtyFull = false
        hasDirtyRect = false
        // View rời window → state "remember" (scene transition...) không còn ý
        // nghĩa; dọn để không tích luỹ qua vòng đời recycle. (Rebuild spec xảy ra
        // khi view VẪN attached nên không bị ảnh hưởng.)
        stateStore.clear()
        if (!framePosted) return

        choreographer.removeFrameCallback(frameDispatcher)
        framePosted = false
    }

    fun requestDraw() {

        if (!attached) return

        dirtyFull = true
        requestPostedDraw()
    }

    fun requestDraw(left: Int, top: Int, right: Int, bottom: Int) {

        if (!attached) return

        unionDirty(left, top, right, bottom)
        requestPostedDraw()
    }

    private fun unionDirty(left: Int, top: Int, right: Int, bottom: Int) {

        if (right <= left || bottom <= top) return

        if (!hasDirtyRect) {

            dirtyLeft = left
            dirtyTop = top
            dirtyRight = right
            dirtyBottom = bottom
            hasDirtyRect = true
            return
        }
        if (left < dirtyLeft) dirtyLeft = left
        if (top < dirtyTop) dirtyTop = top
        if (right > dirtyRight) dirtyRight = right
        if (bottom > dirtyBottom) dirtyBottom = bottom
    }

    private fun performInvalidate() {

        if (dirtyFull || !hasDirtyRect) {

            view.invalidate()
        } else {

            view.invalidate(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom)
        }
        dirtyFull = false
        hasDirtyRect = false
    }

    private fun requestPostedDraw() {

        if (dispatchingFrame) {

            drawRequestedInFrame = true
            return
        }

        if (drawPosted) return

        drawPosted = true
        view.postOnAnimation {

            drawPosted = false
            if (attached) performInvalidate()
        }
    }

    fun requestRemeasure() {

        if (!attached) return

        if (view.isInLayout) {

            // Đang trong lượt layout (vd: set spec trong onBindViewHolder của
            // RecyclerView) → requestLayout thẳng sẽ rơi vào nhánh
            // requestLayoutDuringLayout và bị nuốt, nên hoãn đúng 1 nhịp.
            if (layoutPosted) return

            layoutPosted = true
            view.post {

                layoutPosted = false
                if (attached) view.requestLayout()
            }
        } else {

            // Ngoài lượt layout (vd: bindData qua observeData) → gọi thẳng.
            // requestLayout đặt sync-barrier + post traversal async qua
            // Choreographer, chạy ở VSYNC kế và vượt qua backlog message thường
            // trên main thread → size cập nhật gần như tức thì, không còn cửa
            // sổ trễ như khi bọc trong view.post {}.
            view.requestLayout()
        }
    }

    fun postDelayed(action: Runnable, delayMillis: Long) {

        if (!attached) return

        view.postDelayed(action, delayMillis.coerceAtLeast(0L))
    }

    fun removeCallbacks(action: Runnable) {

        view.removeCallbacks(action)
    }

    fun registerFrameCallback(callback: FrameCallback): FrameRegistration {

        frameCallbacks.add(callback)
        postFrameIfNeeded()
        return FrameRegistration(this, callback)
    }

    private fun unregisterFrameCallback(callback: FrameCallback) {

        frameCallbacks.remove(callback)
    }

    private fun postFrameIfNeeded() {

        if (!attached || framePosted || frameCallbacks.isEmpty()) return

        framePosted = true
        choreographer.postFrameCallback(frameDispatcher)
    }

    fun interface FrameCallback {

        fun onFrame(frameTimeNanos: Long)
    }

    class FrameRegistration internal constructor(
        private val runtime: PrecomputedRuntime,
        private val callback: FrameCallback
    ) {

        private var closed = false

        fun close() {

            if (closed) return

            closed = true
            runtime.unregisterFrameCallback(callback)
        }
    }

}
