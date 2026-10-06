package com.simple.ui.precompute

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.simple.ui.precompute.node.BoundsMode
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.ConstraintChild
import com.simple.ui.precompute.node.ConstraintNode
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.KeyedNode
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode
import com.simple.ui.precompute.node.OutlineNode
import com.simple.ui.precompute.node.TextNode
import com.simple.ui.precompute.node.TransitionConfig
import com.simple.ui.precompute.node.TransitionSpec
import com.simple.ui.precompute.node.TransitionType
import com.simple.ui.precompute.text.BigText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Stress test cho MORPH: N chip có id ổn định, mỗi 500ms ĐỔI NỘI DUNG (nhãn) +
// XÁO vị trí → mọi chip vừa trượt + scale + CROSS-FADE (saveLayerAlpha × N mỗi
// frame). Dùng để đo FPS/gfxinfo khi nhiều node morph.
//
//   adb shell am start -n com.simple.t/.ui.precompute.MorphStressActivity \
//       --ei n 60 --es mode morph   (mode = morph | clip)
// ─────────────────────────────────────────────────────────────────────────────

class MorphStressActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        val n = intent.getIntExtra("n", 60)
        val mode = intent.getStringExtra("mode") ?: "morph"
        val density = resources.displayMetrics.density
        val cardWidth = resources.displayMetrics.widthPixels
        val config = TransitionConfig(
            changeBounds = true,
            enterExit = TransitionType.FADE,
            durationMs = 400L,
            boundsMode = if (mode == "clip") BoundsMode.CLIP else BoundsMode.MORPH
        )

        val view = PrecomputedView(this)
        setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        lifecycleScope.launch {

            val order = (0 until n).toMutableList()
            var version = 0
            val first = withContext(Dispatchers.Default) { buildScene(n, order, version, cardWidth, density) }
            val spec = TransitionSpec(0, 0, cardWidth, first.second, first.first, StressNode, config)
            view.spec = spec

            while (isActive) {

                kotlinx.coroutines.delay(500L)
                version++
                order.shuffle()
                val next = withContext(Dispatchers.Default) { buildScene(n, order, version, cardWidth, density) }
                spec.transitionTo(next.first, config, cardWidth, next.second)
            }
        }
    }
}

private val StressNode = object : LayoutNode() {

    override val id: Any = "morph-stress"

    override val padding: EdgeInsets = EdgeInsets.ZERO

    override fun measure(ctx: MeasureContext, c: Constraints, x: Int, y: Int): DrawSpec =
        throw UnsupportedOperationException("dựng thủ công")
}

private val STRESS_COLORS = intArrayOf(
    0xFFE91E63.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF795548.toInt(),
    0xFF6200EE.toInt(), 0xFFFF9800.toInt(), 0xFF009688.toInt(), 0xFF3F51B5.toInt()
)

/** Dựng 1 scene: N chip theo thứ tự [order], nhãn kèm [version] (đổi nội dung). */
private fun buildScene(
    n: Int,
    order: List<Int>,
    version: Int,
    cardWidth: Int,
    density: Float
): Pair<List<DrawSpec>, Int> {

    fun dp(v: Int) = (v * density).toInt()
    fun sp(v: Float) = v * density

    val padLeft = dp(12)
    val padRight = dp(12)
    val gap = dp(8)
    val topPad = dp(12)

    var x = padLeft
    var y = topPad
    var rowH = 0
    val out = ArrayList<DrawSpec>(n)
    order.forEach { i ->

        val node = KeyedNode(
            key = "chip-$i",
            child = chipNode("#$i·$version", STRESS_COLORS[i % STRESS_COLORS.size], ::dp, ::sp)
        )
        val spec = LayoutEngine.measure(node, Constraints(cardWidth))
        if (x > padLeft && x + spec.width > cardWidth - padRight) {

            x = padLeft
            y += rowH + gap
            rowH = 0
        }
        out.add(spec.withPosition(x, y))
        x += spec.width + gap
        if (spec.height > rowH) rowH = spec.height
    }
    val bottom = (out.maxOfOrNull { it.top + it.height } ?: topPad) + dp(12)
    return out to bottom
}

private fun chipNode(label: String, color: Int, dp: (Int) -> Int, sp: (Float) -> Float): LayoutNode =
    ConstraintNode(
        children = listOf(
            ConstraintChild(
                id = "bg",
                node = OutlineNode(
                    backgroundColor = color,
                    strokeWidth = 0f,
                    cornerRadius = dp(14).toFloat(),
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
                    textSizePx = sp(13f),
                    color = Color.WHITE,
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = dp(12), v = dp(8))
                ),
                startToStartOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT
            )
        )
    )
