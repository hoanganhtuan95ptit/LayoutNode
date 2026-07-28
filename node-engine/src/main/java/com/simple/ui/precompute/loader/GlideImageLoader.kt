package com.simple.ui.precompute.loader

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Animatable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.simple.ui.precompute.image.BigImageTransform
import com.simple.ui.precompute.node.ImageSpec
import kotlin.math.roundToInt
import java.util.WeakHashMap
import java.util.concurrent.Executor

/**
 * ImageLoader dùng Glide. An toàn singleton ở Application scope.
 *
 * ## Threading
 * - [load] và [cancel] được engine dispatch trên [dispatcher] (single-thread
 *   bg) — toàn bộ setup (build `RequestBuilder`, override size, resolve
 *   transforms, lookup `RequestManager` qua `Glide.with(appContext)`) chạy
 *   off-main.
 * - Glide bắt buộc `RequestBuilder.into(target)` và `RequestManager.clear(target)`
 *   chạy ở main thread → loader post chúng qua [mainHandler].
 * - Vì [dispatcher] là single-thread, cancel của một spec luôn chạy sau load
 *   của chính spec đó (cùng FIFO).
 *
 * Cách dùng (gọi 1 lần ở Application.onCreate):
 *   ImageLoader.install(GlideImageLoader(this))
 */
class GlideImageLoader(
    context: Context,
    maxMemoryCacheKb: Int = defaultMemoryCacheKb(),
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS
) : ImageLoader {

    private val appContext = context as? Application ?: context.applicationContext

    private val handlerThread = HandlerThread("GlideImageLoader").apply {

        start()
    }

    private val bgHandler = Handler(handlerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cacheLock = Any()

    private val memoryCache = object : LruCache<ImageCacheKey, CachedBitmap>(maxMemoryCacheKb) {

        override fun sizeOf(key: ImageCacheKey, value: CachedBitmap): Int {

            return value.sizeKb
        }
    }

    private var pruneScheduled = false

    private val trimCallback = object : ComponentCallbacks2 {

        override fun onTrimMemory(level: Int) {

            trimMemoryCache(level)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {

            clearMemoryCache()
        }

        override fun onLowMemory() {

            clearMemoryCache()
        }
    }

    init {

        appContext.registerComponentCallbacks(trimCallback)
    }

    override val dispatcher: Executor = Executor { command ->

        bgHandler.post(command)
    }

    /** Tracking target theo spec để cancel chính xác. Truy cập từ bg thread. */
    private val targets = WeakHashMap<ImageSpec, CustomTarget<Drawable>>()

    private data class RequestSize(
        val width: Int,
        val height: Int
    )

    private data class ImageCacheKey(
        val source: Any,
        val width: Int,
        val height: Int,
        val transforms: List<BigImageTransform>
    )

    private data class CachedBitmap(
        val bitmap: Bitmap,
        var lastUsedAtMillis: Long
    ) {

        val sizeKb: Int
            get() = (bitmap.allocationByteCount / BYTES_PER_KB).coerceAtLeast(1)
    }

    override fun cached(spec: ImageSpec): Drawable? {

        val key = spec.cacheKey() ?: return null
        val bitmap = getCachedBitmap(key) ?: return null
        return BitmapDrawable(appContext.resources, bitmap)
    }

    override fun load(spec: ImageSpec, onReady: () -> Unit) {

        // Chạy trên bg thread (dispatcher).
        if (spec.drawable != null) return

        val size = spec.requestSize()
        val request = createRequest(spec, size)
        val target = createTarget(spec, size, onReady)

        targets[spec] = target

        // Glide yêu cầu into(target) chạy ở main thread.
        mainHandler.post {

            request.into(target)
        }
    }

    override fun cancel(spec: ImageSpec) {

        // Chạy trên bg thread (dispatcher).
        val target = targets.remove(spec) ?: return

        // Glide.clear cũng đụng RequestManager — post về main cho an toàn.
        mainHandler.post {

            Glide.with(appContext).clear(target)
        }
    }

    private fun ImageSpec.requestSize(): RequestSize =
        RequestSize(
            width = dst.width().coerceAtLeast(1),
            height = dst.height().coerceAtLeast(1)
        )

    private fun createRequest(spec: ImageSpec, size: RequestSize): RequestBuilder<Drawable> {

        var withModel: RequestBuilder<Drawable> = Glide.with(appContext)
            .load(spec.source.source)
            .override(size.width, size.height)

        if (spec.source.placeholder != 0) {

            withModel = withModel.placeholder(spec.source.placeholder)
        }

        if (spec.source.error != 0) {

            withModel = withModel.error(spec.source.error)
        }

        if(spec.source.transforms.isNotEmpty()){

            withModel = withModel.transform(*spec.source.transforms)
        }

        return withModel
    }

    private fun createTarget(
        spec: ImageSpec,
        size: RequestSize,
        onReady: () -> Unit
    ): CustomTarget<Drawable> =
        object : CustomTarget<Drawable>(size.width, size.height) {

            override fun onLoadStarted(placeholder: Drawable?) {

                // Chỉ set placeholder khi spec chưa có gì để vẽ. Tránh clobber
                // drawable cũ (hit cache hoặc đã load lần trước) bằng placeholder
                // — đó là nguồn gốc của flicker.
                spec.setPlaceholderIfEmpty(placeholder, onReady)
            }

            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {

                // Cache trước khi gán drawable: lần attach sau cho cùng source
                // + size sẽ lấy bitmap snapshot đồng bộ và bỏ qua vòng Glide.
                rememberLoadedDrawable(spec, size, resource)
                spec.setLoadedDrawable(resource, onReady)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {

                spec.drawable = errorDrawable
                onReady()
            }

            override fun onLoadCleared(placeholder: Drawable?) {

                spec.drawable = null
            }
        }

    private fun ImageSpec.setPlaceholderIfEmpty(placeholder: Drawable?, onReady: () -> Unit) {

        if (drawable != null || placeholder == null) return

        drawable = placeholder
        onReady()
    }

    private fun ImageSpec.setLoadedDrawable(resource: Drawable, onReady: () -> Unit) {

        drawable = resource
        onReady()
    }

    fun clearMemoryCache() {

        synchronized(cacheLock) {

            memoryCache.evictAll()
        }
    }

    private fun trimMemoryCache(level: Int) {

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {

            clearMemoryCache()
            return
        }

        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return

        synchronized(cacheLock) {

            memoryCache.trimToSize(memoryCache.maxSize() / 2)
        }
    }

    private fun getCachedBitmap(key: ImageCacheKey): Bitmap? {

        val now = SystemClock.uptimeMillis()
        val cached = synchronized(cacheLock) {

            memoryCache.get(key)
        } ?: return null

        if (cached.isInvalid(now)) {

            removeCachedBitmap(key)
            return null
        }

        cached.lastUsedAtMillis = now
        scheduleCachePrune()
        return cached.bitmap
    }

    private fun rememberLoadedDrawable(spec: ImageSpec, size: RequestSize, drawable: Drawable) {

        if (drawable is Animatable) return

        val key = spec.cacheKey() ?: return
        val bitmap = drawable.toCacheBitmap(size) ?: return
        val cached = CachedBitmap(bitmap, SystemClock.uptimeMillis())

        synchronized(cacheLock) {

            memoryCache.put(key, cached)
        }

        scheduleCachePrune()
    }

    private fun removeCachedBitmap(key: ImageCacheKey) {

        synchronized(cacheLock) {

            memoryCache.remove(key)
        }
    }

    private fun scheduleCachePrune() {

        if (cacheTtlMillis <= 0L || pruneScheduled) return

        pruneScheduled = true
        bgHandler.postDelayed({

            pruneScheduled = false
            trimExpiredCache()
            scheduleCachePruneIfNeeded()
        }, cacheTtlMillis)
    }

    private fun scheduleCachePruneIfNeeded() {

        val hasCache = synchronized(cacheLock) {

            memoryCache.size() > 0
        }

        if (!hasCache) return

        scheduleCachePrune()
    }

    private fun trimExpiredCache() {

        val now = SystemClock.uptimeMillis()
        val expiredKeys = synchronized(cacheLock) {

            memoryCache.snapshot()
                .filter { it.value.isInvalid(now) }
                .map { it.key }
        }

        expiredKeys.forEach { removeCachedBitmap(it) }
    }

    private fun CachedBitmap.isInvalid(now: Long): Boolean {

        if (bitmap.isRecycled) return true
        if (cacheTtlMillis <= 0L) return false

        return now - lastUsedAtMillis >= cacheTtlMillis
    }

    private fun ImageSpec.cacheKey(): ImageCacheKey? {

        val model = source.source
        if (model is Bitmap || model is Drawable) return null

        return ImageCacheKey(
            source = model,
            width = dst.width().coerceAtLeast(1),
            height = dst.height().coerceAtLeast(1),
            transforms = source.bigTransforms
        )
    }

    private fun Drawable.toCacheBitmap(size: RequestSize): Bitmap? = runCatching {

        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(bounds)

        try {

            bounds = centerInside(Rect(0, 0, size.width, size.height))
            draw(canvas)
        } finally {

            bounds = oldBounds
        }

        bitmap
    }.getOrNull()

    private fun Drawable.centerInside(container: Rect): Rect {

        val containerW = container.width()
        val containerH = container.height()
        val sourceW = intrinsicWidth
        val sourceH = intrinsicHeight

        if (containerW <= 0 || containerH <= 0) {

            return Rect(container.left, container.top, container.left, container.top)
        }

        if (sourceW <= 0 || sourceH <= 0) return Rect(container)

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

    companion object {

        private const val DEFAULT_CACHE_TTL_MILLIS = 300_000L
        private const val MIN_MEMORY_CACHE_KB = 4 * 1024
        private const val MAX_MEMORY_CACHE_KB = 24 * 1024
        private const val BYTES_PER_KB = 1024

        private fun defaultMemoryCacheKb(): Int {

            val runtimeKb = (Runtime.getRuntime().maxMemory() / BYTES_PER_KB / 8L).toInt()
            return runtimeKb.coerceIn(MIN_MEMORY_CACHE_KB, MAX_MEMORY_CACHE_KB)
        }
    }
}
