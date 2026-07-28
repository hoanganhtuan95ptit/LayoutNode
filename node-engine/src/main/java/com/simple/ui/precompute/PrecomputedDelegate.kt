package com.simple.ui.precompute

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View

class PrecomputedDelegate(private val view: View, context: Context, attrs: AttributeSet?) {

    private val runtime = PrecomputedRuntime(view)

    var spec: DrawSpec? = null
        set(value) {
            // Identity swap thuần: mọi tối ưu tái sử dụng subtree (skip measure,
            // giữ drawable / animator / Picture...) đã được xử lý ở
            // background qua cache-by-id trong [LayoutEngine] + [MeasureContext].
            // Main thread ở đây chỉ làm swap + attach/detach — không diff cây.
            //
            // Cache hit ở root → LayoutEngine trả về đúng spec cũ mà delegate
            // đang giữ → `field === value` bắt ngay, thoát sớm, không đụng gì
            // (không invalidate, không detach/attach).
            if (field === value) return

            val old = field
            // Thứ tự: attach new TRƯỚC, detach old SAU.
            //
            // Cache-by-id có thể trả về cây new chứa những [DrawSpec] cùng
            // reference với cây old (subtree tận dụng lại). Reference counter
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
     * Trả về `true` nếu event đã được tiêu thụ bởi node clickable. Caller
     * (PrecomputedView) fall back về `super.onTouchEvent` khi false.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }
}
