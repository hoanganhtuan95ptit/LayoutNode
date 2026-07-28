@file:Suppress("DEPRECATION")

package com.simple.ui.precompute.image

import android.graphics.Bitmap
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop as GlideCenterCrop
import com.google.auto.service.AutoService
import jp.wasabeef.glide.transformations.BlurTransformation
import jp.wasabeef.glide.transformations.ColorFilterTransformation
import jp.wasabeef.glide.transformations.CropCircleTransformation
import jp.wasabeef.glide.transformations.CropCircleWithBorderTransformation
import jp.wasabeef.glide.transformations.CropSquareTransformation
import jp.wasabeef.glide.transformations.CropTransformation
import jp.wasabeef.glide.transformations.GrayscaleTransformation
import jp.wasabeef.glide.transformations.MaskTransformation
import jp.wasabeef.glide.transformations.RoundedCornersTransformation

@AutoService(BigImageTransformConvert::class)
class WasabeefBigTransformConvert : BigImageTransformConvert {

    override fun convert(transform: BigImageTransform): Transformation<Bitmap>? = when (transform) {
        is CenterCrop -> GlideCenterCrop()
        is CircleCrop -> CropCircleTransformation()
        is CropSquare -> CropSquareTransformation()
        is Crop -> CropTransformation(
            transform.widthPx.coerceAtLeast(0),
            transform.heightPx.coerceAtLeast(0),
            transform.cropType.toWasabeef()
        )
        is CircleCropWithBorder -> CropCircleWithBorderTransformation(
            transform.borderSizePx.coerceAtLeast(0),
            transform.borderColor
        )
        is RoundedCorners -> RoundedCornersTransformation(
            transform.radiusPx.coerceAtLeast(0),
            transform.marginPx.coerceAtLeast(0),
            transform.cornerType.toWasabeef()
        )
        is Blur -> BlurTransformation(
            transform.radius.coerceIn(1, 25),
            transform.sampling.coerceAtLeast(1)
        )
        is ColorFilter -> ColorFilterTransformation(transform.color)
        is Grayscale -> GrayscaleTransformation()
        is Mask -> transform.maskResId
            .takeIf { it != 0 }
            ?.let(::MaskTransformation)
        else -> null
    }
}

private fun CropType.toWasabeef(): CropTransformation.CropType = when (this) {
    CropType.TOP -> CropTransformation.CropType.TOP
    CropType.CENTER -> CropTransformation.CropType.CENTER
    CropType.BOTTOM -> CropTransformation.CropType.BOTTOM
}

private fun CornerType.toWasabeef(): RoundedCornersTransformation.CornerType = when (this) {
    CornerType.ALL -> RoundedCornersTransformation.CornerType.ALL
    CornerType.TOP_LEFT -> RoundedCornersTransformation.CornerType.TOP_LEFT
    CornerType.TOP_RIGHT -> RoundedCornersTransformation.CornerType.TOP_RIGHT
    CornerType.BOTTOM_LEFT -> RoundedCornersTransformation.CornerType.BOTTOM_LEFT
    CornerType.BOTTOM_RIGHT -> RoundedCornersTransformation.CornerType.BOTTOM_RIGHT
    CornerType.TOP -> RoundedCornersTransformation.CornerType.TOP
    CornerType.BOTTOM -> RoundedCornersTransformation.CornerType.BOTTOM
    CornerType.LEFT -> RoundedCornersTransformation.CornerType.LEFT
    CornerType.RIGHT -> RoundedCornersTransformation.CornerType.RIGHT
    CornerType.OTHER_TOP_LEFT -> RoundedCornersTransformation.CornerType.OTHER_TOP_LEFT
    CornerType.OTHER_TOP_RIGHT -> RoundedCornersTransformation.CornerType.OTHER_TOP_RIGHT
    CornerType.OTHER_BOTTOM_LEFT -> RoundedCornersTransformation.CornerType.OTHER_BOTTOM_LEFT
    CornerType.OTHER_BOTTOM_RIGHT -> RoundedCornersTransformation.CornerType.OTHER_BOTTOM_RIGHT
    CornerType.DIAGONAL_FROM_TOP_LEFT -> RoundedCornersTransformation.CornerType.DIAGONAL_FROM_TOP_LEFT
    CornerType.DIAGONAL_FROM_TOP_RIGHT -> RoundedCornersTransformation.CornerType.DIAGONAL_FROM_TOP_RIGHT
}
