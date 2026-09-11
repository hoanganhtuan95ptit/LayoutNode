package com.simple.ui.precompute.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

class ColorFilterTransformation(
    private val color: Int,
    private val mode: PorterDuff.Mode? = null
) : BitmapTransformation() {

    val effectiveMode: PorterDuff.Mode
        get() = mode ?: if (Color.alpha(color) < 255) {
            PorterDuff.Mode.SRC_IN
        } else {
            PorterDuff.Mode.SRC_ATOP
        }

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {

        val width = toTransform.width
        val height = toTransform.height

        val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && toTransform.config == Bitmap.Config.HARDWARE) {

            Bitmap.Config.ARGB_8888
        } else {

            toTransform.config ?: Bitmap.Config.ARGB_8888
        }
        val bitmap = pool.get(width, height, config)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.density = toTransform.density

        val canvas = Canvas(bitmap)
        canvas.drawBitmap(toTransform, 0f, 0f, null)
        canvas.drawColor(color, effectiveMode)

        return bitmap
    }

    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (other !is ColorFilterTransformation) return false
        return color == other.color && effectiveMode == other.effectiveMode
    }

    override fun hashCode(): Int {

        var result = ID.hashCode()
        result = 31 * result + color
        result = 31 * result + effectiveMode.hashCode()
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {

        messageDigest.update(ID_BYTES)
        messageDigest.update(ByteBuffer.allocate(8).putInt(color).putInt(effectiveMode.ordinal).array())
    }

    companion object {

        private const val ID = "com.simple.ui.precompute.image.ColorFilterTransformation.2"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
