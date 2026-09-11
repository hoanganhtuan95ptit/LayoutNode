package com.simple.ui.precompute.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

class ColorFilterTransformation(
    private val color: Int,
    private val mode: PorterDuff.Mode = PorterDuff.Mode.SRC_IN
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {

        val width = toTransform.width
        val height = toTransform.height

        val bitmap = pool.get(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)
        bitmap.density = toTransform.density

        val canvas = Canvas(bitmap)
        canvas.density = toTransform.density

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

            colorFilter = PorterDuffColorFilter(color, mode)
        }
        canvas.drawBitmap(toTransform, 0f, 0f, paint)

        return bitmap
    }

    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (other !is ColorFilterTransformation) return false
        return color == other.color && mode == other.mode
    }

    override fun hashCode(): Int {

        var result = ID.hashCode()
        result = 31 * result + color
        result = 31 * result + mode.hashCode()
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {

        messageDigest.update(ID_BYTES)
        messageDigest.update(ByteBuffer.allocate(8).putInt(color).putInt(mode.ordinal).array())
    }

    companion object {

        private const val ID = "com.simple.ui.precompute.image.ColorFilterTransformation.1"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
