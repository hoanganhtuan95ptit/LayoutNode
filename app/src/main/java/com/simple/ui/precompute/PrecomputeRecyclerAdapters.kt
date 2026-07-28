package com.simple.ui.precompute

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.simple.adapter.ViewItem
import com.simple.adapter.ViewItemAdapter
import com.simple.t.R

data class PrecomputedCardItem(
    val id: String,
    val spec: DrawSpec,
    val topMarginPx: Int
) : ViewItem {

    override fun areItemsTheSame(): List<Any> = listOf(id)

    override fun getContentsCompare(): List<Pair<Any, String>> =
        listOf(spec to "spec", topMarginPx to "topMarginPx")
}

data class DemoTextItem(
    val id: String,
    val text: String,
    val style: DemoTextStyle,
    val topMarginPx: Int = 0,
    val bottomMarginPx: Int = 0
) : ViewItem {

    override fun areItemsTheSame(): List<Any> = listOf(id)

    override fun getContentsCompare(): List<Pair<Any, String>> =
        listOf(
            text to "text",
            style to "style",
            topMarginPx to "topMarginPx",
            bottomMarginPx to "bottomMarginPx"
        )
}

enum class DemoTextStyle {
    SECTION,
    FOOTER
}

class DemoTextAdapter : ViewItemAdapter<DemoTextItem, TextViewBinding>() {

    override val viewItemClass: Class<DemoTextItem> by lazy {

        DemoTextItem::class.java
    }

    override fun createViewBinding(
        layoutInflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): TextViewBinding {

        return TextViewBinding(TextView(parent.context))
    }

    override fun onBindViewHolder(
        binding: TextViewBinding,
        viewType: Int,
        position: Int,
        item: DemoTextItem,
        payloads: List<String>
    ) {

        bindText(binding.rootView, item)
        bindMargins(binding.rootView, item.topMarginPx, item.bottomMarginPx)
    }

    private fun bindText(textView: TextView, item: DemoTextItem) {

        textView.text = item.text
        when (item.style) {
            DemoTextStyle.SECTION -> bindSection(textView)
            DemoTextStyle.FOOTER -> bindFooter(textView)
        }
    }

    private fun bindSection(textView: TextView) {

        val dp = textView.resources.displayMetrics.density
        textView.gravity = Gravity.START
        textView.textSize = 13f
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.setTextColor(Color.WHITE)
        textView.setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
        textView.setBackgroundColor(0xFF6200EE.toInt())
    }

    private fun bindFooter(textView: TextView) {

        textView.gravity = Gravity.CENTER
        textView.textSize = 12f
        textView.typeface = Typeface.DEFAULT
        textView.setTextColor(Color.GRAY)
        textView.setPadding(0, 0, 0, 0)
        textView.setBackgroundColor(Color.TRANSPARENT)
    }
}

class PrecomputedCardAdapter : ViewItemAdapter<PrecomputedCardItem, PrecomputedCardBinding>() {

    override val viewItemClass: Class<PrecomputedCardItem> by lazy {

        PrecomputedCardItem::class.java
    }

    override fun createViewBinding(
        layoutInflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): PrecomputedCardBinding {

        val view = PrecomputedView(parent.context)
        view.setBackgroundResource(R.drawable.card_background)
        return PrecomputedCardBinding(view)
    }

    override fun onBindViewHolder(
        binding: PrecomputedCardBinding,
        viewType: Int,
        position: Int,
        item: PrecomputedCardItem,
        payloads: List<String>
    ) {

        binding.precomputedView.spec = item.spec
        bindMargins(binding.precomputedView, item.topMarginPx, 0)
    }

}

class TextViewBinding(
    val rootView: TextView
) : ViewBinding {

    override fun getRoot(): View = rootView
}

class PrecomputedCardBinding(
    val precomputedView: PrecomputedView
) : ViewBinding {

    override fun getRoot(): View = precomputedView
}

private fun bindMargins(view: View, top: Int, bottom: Int) {

    val params = view.layoutParams as? RecyclerView.LayoutParams
        ?: RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.WRAP_CONTENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    params.topMargin = top
    params.bottomMargin = bottom
    view.layoutParams = params
}
