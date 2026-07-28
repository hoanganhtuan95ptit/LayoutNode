package com.simple.ui.precompute

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * View "dumb tuyệt đối": chỉ giữ một [DrawSpec] và uỷ thác việc vẽ cho nó.
 * Không biết Text / Image / Group là gì, cũng không biết ImageLoader.
 *
 *   view.spec = LayoutEngine.measure(node, Constraints(w))
 *
 * Lifecycle (attach/detach + đổi spec) được forward xuống spec tree;
 * ImageSpec tự lo việc load / cancel ảnh qua loader của chính nó.
 */
class PrecomputedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), PrecomputedHost {

    override val delegate: PrecomputedDelegate = PrecomputedDelegate(this, context, attrs)

    var isFlexibleSize: Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = delegate.spec
        val width = if (isFlexibleSize) resolveSize(s?.width ?: 0, widthMeasureSpec) else s?.width ?: 0
        val height = if (isFlexibleSize) resolveSize(s?.height ?: 0, heightMeasureSpec) else s?.height ?: 0
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        delegate.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        delegate.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        delegate.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Delegate hit-test qua spec tree; nếu không có node clickable trúng,
        // fall back về hành vi View mặc định.
        if (delegate.onTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        // Onclick thực tế đã invoke ở node level trong delegate; ở đây chỉ
        // super để giữ accessibility contract (không nhân đôi callback).
        return super.performClick()
    }
}
