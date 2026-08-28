package com.simple.ui.precompute.image

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide


fun ImageView.setImage(image: BigImage) {
    
    val source = image.source
    if (source is Drawable) {

        Glide.with(context).clear(this)
        setImageDrawable(source)
        return
    }

    Glide.with(context)
        .load(image.source)
        .let {
            if (image.placeholder != 0) it.placeholder(image.placeholder) else it
        }
        .let {
            if (image.error != 0) it.error(image.error) else it
        }
        .transform(*image.transforms)
        .into(this)
}


private val EMPTY by lazy {
    BigImage("")
}

fun emptyImage() = EMPTY


fun Any.toBigImage(): BigImage = BigImage(this)

fun Int.toBigImage(colorFilter: Int = 0): BigImage {

    if (colorFilter == 0) return BigImage(this)

    return this.toBuilder()
        .addTransform(ColorFilter(colorFilter))
        .build()
}


fun Int.toBuilder(): BigImageBuilder {

    return BigImageBuilder(this)
}

fun String.toBuilder(): BigImageBuilder {

    return BigImageBuilder(this)
}

fun Drawable.toBuilder(): BigImageBuilder {

    return BigImageBuilder(this)
}

fun BigImageBuilder.addTransform(vararg transform: BigImageTransform): BigImageBuilder {

    add(*transform)
    return this
}

fun BigImageBuilder.setError(error: Int): BigImageBuilder {

    this.error = error
    return this
}

fun BigImageBuilder.setPlaceholder(placeholder: Int): BigImageBuilder {

    this.placeholder = placeholder
    return this
}

fun BigImageBuilder.build() = BigImage(
    source = source,
    error = error,
    placeholder = placeholder,
    bigTransforms = transforms
)
