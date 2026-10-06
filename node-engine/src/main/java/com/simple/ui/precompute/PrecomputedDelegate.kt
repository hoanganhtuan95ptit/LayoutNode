package com.simple.ui.precompute

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import com.simple.ui.precompute.node.NodeTouch
import com.simple.ui.precompute.node.TouchAction

class PrecomputedDelegate(private val view: View, context: Context, attrs: AttributeSet?) {

    private val runtime = PrecomputedRuntime(view)

    /** Expose virtual node cho accessibility / UI test. */
    val a11yHelper = PrecomputedA11yHelper(view) { interactiveTargets() }

    /** Node đang "giữ" gesture hiện tại (set ở ACTION_DOWN) để route touch. */
    private var touchTarget: DrawSpec? = null

    var spec: DrawSpec? = null
        set(value) {
            // Swap thuần: main thread chỉ làm swap + attach/detach, KHÔNG đo,
            // KHÔNG diff cây (đo đã xong ở background, LayoutEngine không cache).
            //
            // Caller set đúng lại chính spec đang giữ → `field === value` bắt
            // ngay, thoát sớm (không invalidate, không detach/attach).
            if (field === value) return

            val old = field
            // Thứ tự: attach new TRƯỚC, detach old SAU.
            //
            // Nếu caller tái dùng instance, cây new có thể chứa [DrawSpec] cùng
            // reference với cây old (subtree dùng chung). Reference counter
            // trong [DrawSpec.attach] / [DrawSpec.detach] cần shared ref
            // không rơi về 0 ở giữa chừng — nếu detach trước, counter đi
            // 1→0 → onDetached chạy, animator stop / scope cancel; sau đó
            // attach lại đi 0→1 → onAttached chạy, phải setup lại từ đầu.
            //
            // Đảo thứ tự: shared ref counter 1→2 (attach no-op) → 2→1
            // (detach no-op) — hook lifecycle không bị đụng, state giữ
            // nguyên. Non-shared spec (old-only / new-only) counter đi
            // đúng 0↔1 như thường.
            if (view.isAttachedToWindow) value?.attach(runtime)
            field = value
            if (old?.width != value?.width || old?.height != value?.height) {
                runtime.requestRemeasure()
            } else {
                runtime.requestDraw()
            }
            if (view.isAttachedToWindow) old?.detach(runtime)
            // Cây đổi → tập node interactive đổi → dựng lại virtual view a11y.
            a11yHelper.invalidateRoot()
        }

    /**
     * GestureDetector chuyên trách dispatch tap → onClick của node trúng
     * hit-test. Dùng detector chuẩn Android để tự động lo tap-slop,
     * double-tap window, cancel khi kéo ra ngoài.
     */
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                // Chỉ "claim" chuỗi event khi điểm chạm rơi trên một spec
                // clickable — trả false để parent (nếu có) xử lý các case
                // trống. Kết quả: view chỉ intercept khi thực sự có target.
                return spec?.hitTest(e.x.toInt(), e.y.toInt()) != null
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = spec?.hitTest(e.x.toInt(), e.y.toInt()) ?: return false
                val cb = hit.node?.onClick ?: return false
                view.playSoundEffect(SoundEffectConstants.CLICK)
                cb.invoke()
                // performClick() để giữ đúng contract accessibility của View
                // (TalkBack, autofill, testing framework...).
                view.performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val hit = spec?.hitTest(e.x.toInt(), e.y.toInt()) ?: return
                val cb = hit.node?.onLongClick ?: return
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                cb.invoke()
            }
        }
    )

    fun onDraw(canvas: Canvas) {
        spec?.draw(canvas)
    }

    fun onAttachedToWindow() {
        runtime.onAttachedToWindow()
        spec?.attach(runtime)
    }

    fun onDetachedFromWindow() {
        spec?.detach(runtime)
        runtime.onDetachedFromWindow()
    }

    /**
     * Trả về `true` nếu event đã được tiêu thụ (có node interactive trúng, hoặc
     * gesture detector claim). Caller (PrecomputedView) fall back về
     * `super.onTouchEvent` khi false.
     *
     * Vừa route raw touch tới [LayoutNode.onTouch] của node trúng điểm DOWN
     * (khoá target cho cả gesture), vừa cho [gestureDetector] lo click/long-click.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTarget = spec?.hitTest(event.x.toInt(), event.y.toInt())
                dispatchTouch(event, TouchAction.DOWN)
            }
            MotionEvent.ACTION_MOVE -> dispatchTouch(event, TouchAction.MOVE)
            MotionEvent.ACTION_UP -> dispatchTouch(event, TouchAction.UP)
            MotionEvent.ACTION_CANCEL -> dispatchTouch(event, TouchAction.CANCEL)
        }

        val gesture = gestureDetector.onTouchEvent(event)
        val hadTarget = touchTarget != null
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            touchTarget = null
        }
        return gesture || hadTarget
    }

    private fun dispatchTouch(event: MotionEvent, action: TouchAction) {

        val target = touchTarget ?: return
        val onTouch = target.node?.onTouch ?: return
        onTouch(
            NodeTouch(
                action = action,
                x = event.x - target.viewLeft,
                y = event.y - target.viewTop,
                rawX = event.x,
                rawY = event.y
            )
        )
    }

    private fun interactiveTargets(): List<DrawSpec> {

        val root = spec ?: return emptyList()
        val out = ArrayList<DrawSpec>()
        root.collectInteractive(out)
        return out
    }
}
