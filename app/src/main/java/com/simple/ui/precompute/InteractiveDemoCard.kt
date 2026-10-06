package com.simple.ui.precompute

import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.ConstraintChild
import com.simple.ui.precompute.node.ConstraintNode
import com.simple.ui.precompute.node.CrossAlign
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.KeyedNode
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode
import com.simple.ui.precompute.node.LinearNode
import com.simple.ui.precompute.node.NodeTouch
import com.simple.ui.precompute.node.Orientation
import com.simple.ui.precompute.node.OutlineNode
import com.simple.ui.precompute.node.TextNode
import com.simple.ui.precompute.text.BigText

// ─────────────────────────────────────────────────────────────────────────────
// Card demo cho CLICK / LONG-CLICK / TOUCH per-node + ACCESSIBILITY.
//
// 3 nút, mỗi nút là một KeyedNode có handler riêng + contentDescription (để
// TalkBack đọc và Espresso/UiAutomator định vị). Kết quả log ra tag "NodeDemo".
// ─────────────────────────────────────────────────────────────────────────────

private const val TAG = "NodeDemo"

fun buildInteractiveDemoCard(cardWidth: Int, density: Float): LayoutNode {

    fun dp(v: Int): Int = (v * density).toInt()
    fun sp(v: Float): Float = v * density

    return LinearNode(
        orientation = Orientation.HORIZONTAL,
        crossAlign = CrossAlign.CENTER,
        gap = dp(10),
        padding = EdgeInsets.all(dp(16)),
        layoutWidth = LayoutDimension.MatchParent,
        children = listOf(
            button(
                key = "btn-tap",
                label = "Tap",
                color = 0xFF2196F3.toInt(),
                contentDescription = "btn-tap",
                onClick = { Log.d(TAG, "CLICK btn-tap") },
                dp = ::dp, sp = ::sp
            ),
            button(
                key = "btn-hold",
                label = "Hold",
                color = 0xFF4CAF50.toInt(),
                contentDescription = "btn-hold",
                onClick = { Log.d(TAG, "CLICK btn-hold") },
                onLongClick = { Log.d(TAG, "LONGCLICK btn-hold") },
                dp = ::dp, sp = ::sp
            ),
            button(
                key = "btn-drag",
                label = "Drag",
                color = 0xFFE91E63.toInt(),
                contentDescription = "btn-drag",
                onTouch = { t ->
                    Log.d(TAG, "TOUCH btn-drag ${t.action} local=(${t.x.toInt()},${t.y.toInt()}) raw=(${t.rawX.toInt()},${t.rawY.toInt()})")
                    true
                },
                dp = ::dp, sp = ::sp
            )
        )
    )
}

private fun button(
    key: String,
    label: String,
    color: Int,
    contentDescription: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onTouch: ((NodeTouch) -> Boolean)? = null,
    dp: (Int) -> Int,
    sp: (Float) -> Float
): KeyedNode = KeyedNode(
    key = key,
    onClick = onClick,
    onLongClick = onLongClick,
    onTouch = onTouch,
    contentDescription = contentDescription,
    child = ConstraintNode(
        children = listOf(
            ConstraintChild(
                id = "bg",
                node = OutlineNode(
                    backgroundColor = color,
                    strokeWidth = 0f,
                    cornerRadius = dp(10).toFloat(),
                    layoutWidth = LayoutDimension.MatchParent,
                    layoutHeight = LayoutDimension.MatchParent
                ),
                startToStartOf = "text",
                endToEndOf = "text",
                topToTopOf = "text",
                bottomToBottomOf = "text",
                width = LayoutDimension.MatchParent,
                height = LayoutDimension.MatchParent
            ),
            ConstraintChild(
                id = "text",
                node = TextNode(
                    text = BigText(label),
                    textSizePx = sp(15f),
                    color = Color.WHITE,
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = dp(20), v = dp(12))
                ),
                startToStartOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT
            )
        )
    )
)
