package com.simple.ui.precompute.loader

import android.graphics.drawable.Drawable
import com.simple.ui.precompute.node.ImageSpec
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Loader bất đồng bộ cho [com.simple.ui.precompute.node.ImageSpec].
 *
 * ## Threading contract
 * Engine **không** gọi [load]/[cancel] trực tiếp trên UI thread. Mọi yêu cầu
 * được dispatch qua [dispatcher] để mọi setup (build Glide RequestBuilder,
 * resolve transforms, lookup RequestManager, v.v.) chạy **off-main**.
 *
 * - [load] và [cancel] được gọi trên thread của [dispatcher].
 * - Implementation chịu trách nhiệm switch lại main thread cho những API
 *   bắt buộc main (vd Glide's `into(target)`).
 * - [onReady] có thể được gọi từ bất kỳ thread nào, nhưng thường được
 *   implementation post lên main rồi mới gọi.
 *
 * Mặc định [dispatcher] là một single-thread executor — đảm bảo cancel
 * luôn chạy sau load đã queue cùng spec, tránh race.
 *
 * Implementation chuẩn: `GlideImageLoader` ở module :glide-loader.
 */
interface ImageLoader {

    /**
     * Executor đảm bảo [load]/[cancel] chạy off-main. Mặc định single-thread
     * để serialize thứ tự load↔cancel cho cùng spec.
     */
    val dispatcher: Executor get() = DEFAULT_DISPATCHER

    /**
     * Trả về drawable đã cache sẵn cho [spec], hoặc null nếu chưa có.
     *
     * Hàm này được gọi đồng bộ trên main thread trước khi [load] chạy, giúp
     * [ImageSpec] có thể vẽ ngay khi ảnh đã từng load thành công trước đó.
     * Implementation nên trả về một drawable instance độc lập vì bounds /
     * callback của Drawable là mutable theo từng spec.
     */
    fun cached(spec: ImageSpec): Drawable? = null

    fun load(spec: ImageSpec, onReady: () -> Unit)

    fun cancel(spec: ImageSpec)

    companion object {

        private val DEFAULT_DISPATCHER: Executor by lazy {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "ImageLoader-default").apply { isDaemon = true }
            }
        }

        @Volatile
        private var instance: ImageLoader? = null

        /** Đăng ký loader mặc định (gọi 1 lần ở Application). */
        fun install(loader: ImageLoader) {
            instance = loader
        }

        /** Trả về loader đã đăng ký, hoặc null nếu chưa install. */
        fun get(): ImageLoader? = instance
    }
}
