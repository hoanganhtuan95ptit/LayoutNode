package com.simple.ui.precompute

import android.view.Choreographer
import android.view.View

class PrecomputedRuntime(val view: View) {

    private val choreographer: Choreographer by lazy(LazyThreadSafetyMode.NONE) {

        Choreographer.getInstance()
    }

    private val frameCallbacks = LinkedHashSet<FrameCallback>()
    private val dispatchCallbacks = ArrayList<FrameCallback>()
    private var framePosted = false
    private var drawPosted = false
    private var layoutPosted = false
    private var dispatchingFrame = false
    private var drawRequestedInFrame = false
    private var attached = view.isAttachedToWindow

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
        if (drawRequestedInFrame && attached) view.invalidate()
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
        if (!framePosted) return

        choreographer.removeFrameCallback(frameDispatcher)
        framePosted = false
    }

    fun requestDraw() {

        if (!attached) return

        requestPostedDraw()
    }

    fun requestDraw(left: Int, top: Int, right: Int, bottom: Int) {

        requestDraw()
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
            if (attached) view.invalidate()
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
