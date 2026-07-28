package com.simple.ui.precompute.node

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources
import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasurePolicy
import com.simple.ui.precompute.loader.ImageLoader
import com.simple.ui.precompute.MeasureContext
import com.simple.ui.precompute.PrecomputedRuntime
import com.simple.ui.precompute.image.BigImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// BigImage   — nguồn ảnh public, tương tự BigText.
// ImageNode   — mô tả một ảnh cần layout.
// ImageSpec   — kết quả sau khi đo; load ảnh qua ImageLoader/Glide.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Mô tả một ảnh cần layout.
 *
 *   [LayoutDimension.WrapContent] sẽ lấy theo kích thước ảnh.
 * - Với các source không có intrinsic size sẵn (Res/Path/Url), cần có kích thước
 *   trước khi ảnh về: đặt [layoutWidth]/[layoutHeight] thành [LayoutDimension.Fixed]
 *   hoặc bounded [LayoutDimension.MatchParent].
 */
interface ImageMeasureNode {

    val source: BigImage
}

data class ImageNode(
    override val source: BigImage,
    override val layoutWidth: LayoutDimension = LayoutDimension.WrapContent,
    override val layoutHeight: LayoutDimension = LayoutDimension.WrapContent,
    override val padding: EdgeInsets = EdgeInsets.ZERO,
    override val onClick: (() -> Unit)? = null
) : LayoutNode(), ImageMeasureNode {

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): ImageSpec =
        ImageMeasurePolicy<ImageNode>().measure(this, ctx, c, x, y)
}

open class ImageMeasurePolicy<N> : MeasurePolicy<N>()
        where N : LayoutNode,
              N : ImageMeasureNode {

    override fun measure(
        node: N,
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): ImageSpec {

        val p = node.padding

        val rawW = node.layoutWidth.contentSizeFrom(c.maxWidth, p.horizontal)
            ?: node.source.intrinsicWidth()
            ?: 0
        val rawH = node.layoutHeight.contentSizeFrom(c.maxHeight, p.vertical)
            ?: node.source.intrinsicHeight()
            ?: 0

        val w = node.layoutWidth.resolve(rawW + p.horizontal, c.maxWidth)
        val h = node.layoutHeight.resolve(rawH + p.vertical, c.maxHeight)
        val dstW = (w - p.horizontal).coerceAtLeast(0)
        val dstH = (h - p.vertical).coerceAtLeast(0)

        val dst = Rect(p.left, p.top, p.left + dstW, p.top + dstH)
        return createSpec(node, x, y, w, h, dst)
    }

    protected open fun createSpec(
        node: N,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        dst: Rect
    ): ImageSpec {

        return ImageSpec(left, top, width, height, node.source, dst, node)
    }

    private fun LayoutDimension.contentSizeFrom(parentMax: Int, padding: Int): Int? =
        when (this) {
            is LayoutDimension.Fixed -> (maxForMeasure(parentMax) - padding).coerceAtLeast(0)
            LayoutDimension.MatchParent -> {
                if (parentMax == Int.MAX_VALUE) null else (parentMax - padding).coerceAtLeast(0)
            }
            LayoutDimension.WrapContent -> null
        }
}

/**
 * Kết quả đo của [ImageNode].
 *
 * Không phải data class vì [drawable] được loader cập nhật
 * sau khi spec đã measure xong (cho UrlSource / ResSource / ...).
 *
 * [drawable] null cho tới khi [ImageLoader] gọi setter,
 * ngay khi gắn vào runtime — case sau giúp tránh flicker khi spec mới
 * thay thế spec cũ cùng [source].
 *
 * Threading: setter của [drawable] luôn được gọi trên main (Glide CustomTarget
 * callbacks + onAttach), [onDrawContent] cũng ở main → không cần @Volatile.
 */
open class ImageSpec(
    override val left: Int,
    override val top: Int,
    override val width: Int,
    override val height: Int,
    open val source: BigImage,
    open val dst: Rect,
    override val node: LayoutNode
) : DrawSpec() {

    var drawable: Drawable? = null
        set(value) {
            if (field === value) return

            // Tháo drawable cũ: stop animation + clear callback để giải phóng
            // tham chiếu tới view (tránh leak khi drawable cũ vẫn còn ref).
            val old = field
            if (old != null) {
                (old as? Animatable)?.stop()
                old.callback = null
            }

            field = value
            if (value == null) return

            // centerInside chỉ là arithmetic integer — chạy sync trên main rẻ
            // hơn nhiều so với dispatch sang Default rồi bounce về Main.
            value.bounds = value.centerInside(dst)

            // Chỉ gắn callback + start animation khi runtime đang attached.
            if (runtime != null) {
                value.callback = drawableCallback
                (value as? Animatable)?.start()
            }
        }

    /**
     * Scope sống từ attach → detach. Dùng để gọi [ImageLoader.load] off-main,
     * tự cancel khi detach. Main.immediate để onReady callback từ Glide
     * (đang ở main) không phải post lại 1 tick.
     */
    private var scope: CoroutineScope? = null

    /**
     * Lazy: spec chưa từng có drawable thì khỏi cấp phát object này.
     * NONE vì mọi truy cập đều trên main thread.
     */
    private val drawableCallback: Drawable.Callback by lazy(LazyThreadSafetyMode.NONE) {
        object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                this@ImageSpec.requestDraw()
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                val delay = `when` - android.os.SystemClock.uptimeMillis()
                this@ImageSpec.runtime?.postDelayed(what, delay)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                this@ImageSpec.runtime?.removeCallbacks(what)
            }
        }
    }

    override fun onDrawContent(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun onAttachedToRuntime(runtime: PrecomputedRuntime) {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s

        drawable?.let {
            it.callback = drawableCallback
            (it as? Animatable)?.start()
        }

        // Đã có ảnh từ lần load trước thì không cần load lại.
        if (drawable != null) return

        val immediateDrawable = source.immediateDrawable()
        if (immediateDrawable != null) {

            drawable = immediateDrawable
            return
        }

        val loader = ImageLoader.get() ?: return
        val cachedDrawable = loader.cached(this)
        if (cachedDrawable != null) {

            drawable = cachedDrawable
            return
        }

        // Load off-main; nếu detach trước khi xong sẽ tự cancel theo scope.
        s.launch(Dispatchers.Default) {
            loader.load(this@ImageSpec) { this@ImageSpec.requestDraw() }
        }
    }

    override fun onDetachedFromRuntime(runtime: PrecomputedRuntime) {
        val d = drawable
        if (d != null) {
            d.callback = null
            (d as? Animatable)?.stop()
        }

        // Huỷ scope → in-flight load coroutine dừng. Cancel của loader đi qua
        // dispatcher của chính nó để giữ thứ tự load↔cancel cho cùng spec.
        scope?.cancel()
        scope = null

        val loader = ImageLoader.get() ?: return
        loader.dispatcher.execute { loader.cancel(this) }
    }

    /**
     * withPosition tạo instance mới với vị trí khác nhưng **giữ drawable đã load**.
     *
     * Ban đầu invariant là "withPosition chỉ gọi trên spec vừa-measure (chưa
     * có drawable)". Cache-by-id trong [com.simple.ui.precompute.MeasureContext]
     * phá vỡ invariant đó: khi node không đổi (cache hit) nhưng vị trí bị
     * parent xê dịch (vd sibling ở trên thay đổi kích thước), engine trả
     * `cached.withPosition(x, y)` — nếu không copy drawable, đường tránh-
     * tệ hơn là re-load qua Glide nếu cache miss).
     *
     * Chỉ copy drawable; không copy [runtime]/[scope] — attach lifecycle
     * sẽ được [com.simple.ui.precompute.PrecomputedDelegate] chạy lại trên
     * instance mới.
     */
    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec {
        if (newLeft == left && newTop == top) return this
        val next = ImageSpec(newLeft, newTop, width, height, source, dst, node)
        val d = drawable
        if (d != null) next.drawable = d
        return next
    }

    private fun Drawable.centerInside(container: Rect): Rect {
        val containerW = container.width()
        val containerH = container.height()
        val sourceW = intrinsicWidth
        val sourceH = intrinsicHeight

        if (containerW <= 0 || containerH <= 0) {
            return Rect(container.left, container.top, container.left, container.top)
        }

        if (sourceW <= 0 || sourceH <= 0) {
            return Rect(container)
        }

        val scale = minOf(
            containerW.toFloat() / sourceW.toFloat(),
            containerH.toFloat() / sourceH.toFloat()
        )
        val drawW = (sourceW * scale).roundToInt().coerceIn(1, containerW)
        val drawH = (sourceH * scale).roundToInt().coerceIn(1, containerH)
        val left = container.left + (containerW - drawW) / 2
        val top = container.top + (containerH - drawH) / 2

        return Rect(left, top, left + drawW, top + drawH)
    }
}

private fun BigImage.intrinsicWidth(): Int? =
    when (val value = source) {
        is Bitmap -> value.width
        is Drawable -> value.intrinsicWidth.takeIf { it > 0 }
        else -> null
    }

private fun BigImage.intrinsicHeight(): Int? =
    when (val value = source) {
        is Bitmap -> value.height
        is Drawable -> value.intrinsicHeight.takeIf { it > 0 }
        else -> null
    }

private fun BigImage.immediateDrawable(): Drawable? =
    when (val value = source) {
        is Bitmap -> BitmapDrawable(Resources.getSystem(), value)
        is Drawable -> value.copyForImageSpec()
        else -> null
    }

private fun Drawable.copyForImageSpec(): Drawable =
    constantState?.newDrawable()?.mutate() ?: mutate()
