package com.simple.ui.precompute

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
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

// ─────────────────────────────────────────────────────────────────────────────
// Hai card demo cho TransitionNode / TransitionSpec — ĐIỀU KHIỂN BẰNG TAY.
//
// Card A (imperative, transitionTo):
//   • Chạm HEADER → đổi hiệu ứng enter/exit.
//   • Chạm vùng CHIP → đổi trạng thái kế tiếp. Chip "Canvas" to/nhỏ giữa các
//     scene để thấy ChangeBounds nội suy CẢ size (không scale méo chữ).
//
// Card B (rebuild-driven, qua scene-store trong PrecomputedRuntime):
//   • Chạm → dựng LẠI spec mới (transitionKey khác) và gắn vào view. Engine tự
//     đọc scene trước trong store rồi animate — không cần giữ ref spec.
// ─────────────────────────────────────────────────────────────────────────────

private val PALETTE = mapOf(
    "kotlin" to Pair("Kotlin", 0xFFE91E63.toInt()),
    "android" to Pair("Android", 0xFF4CAF50.toInt()),
    "canvas" to Pair("Canvas", 0xFF2196F3.toInt()),
    "node" to Pair("Node", 0xFF795548.toInt()),
    "spec" to Pair("DrawSpec", 0xFF6200EE.toInt()),
    "glide" to Pair("Glide", 0xFFFF9800.toInt())
)

// ─── Card A ─────────────────────────────────────────────────────────────────

fun buildTransitionDemoCard(cardWidth: Int, density: Float): DrawSpec {

    fun dp(v: Int): Int = (v * density).toInt()
    fun sp(v: Float): Float = v * density

    val padLeft = dp(16)
    val padRight = dp(16)
    val gap = dp(10)

    // key -> có "big" không ở từng scene. "canvas" phình/thu để minh hoạ size.
    val scenes = listOf(
        listOf("kotlin" to false, "android" to false, "canvas" to false, "node" to false, "spec" to false),
        listOf("canvas" to true, "kotlin" to false, "spec" to false),
        listOf("spec" to false, "node" to false, "canvas" to false, "android" to false, "kotlin" to false, "glide" to false),
        listOf("android" to false, "glide" to false, "canvas" to true),
        listOf("kotlin" to false, "android" to false, "canvas" to false, "node" to false, "spec" to false, "glide" to false)
    )
    val effects = listOf(
        "Fade" to TransitionConfig(changeBounds = true, enterExit = TransitionType.FADE),
        "Scale" to TransitionConfig(changeBounds = true, enterExit = TransitionType.SCALE),
        "Fade + Scale" to TransitionConfig(changeBounds = true, enterExit = TransitionType.FADE_SCALE),
        "Chỉ di chuyển" to TransitionConfig(changeBounds = true, enterExit = TransitionType.NONE),
        "Cross (bỏ ChangeBounds)" to TransitionConfig(changeBounds = false, enterExit = TransitionType.FADE)
    )

    val headerSpecs = effects.map { (name, _) ->
        measureHeader(
            "Hiệu ứng: $name  (chạm dòng này để đổi)\nChạm vùng chip bên dưới để đổi trạng thái (Canvas phình/thu)",
            cardWidth, padLeft, padRight, ::dp, ::sp
        )
    }
    val headerHeight = (headerSpecs.maxOfOrNull { it.top + it.height } ?: 0) + dp(12)

    val flow = ChipFlow(cardWidth, padLeft, padRight, gap, headerHeight)
    val sceneSpecs = scenes.map { refs ->
        flow.layout(refs.map { (key, big) -> measureChip(key, big, cardWidth, ::dp, ::sp) })
    }
    val sceneHeights = sceneSpecs.map { flow.bottomOf(it) + dp(16) }

    return TransitionDemoSpec(
        left = 0,
        top = 0,
        width = cardWidth,
        height = sceneHeights[0],
        node = DemoNode("transition-demo-imperative"),
        initialConfig = effects[0].second,
        sceneSpecs = sceneSpecs,
        sceneHeights = sceneHeights,
        effects = effects,
        headerSpecs = headerSpecs,
        headerHeight = headerHeight
    )
}

// ─── Card B ─────────────────────────────────────────────────────────────────

fun buildRebuildTransitionCard(cardWidth: Int, density: Float): DrawSpec {

    fun dp(v: Int): Int = (v * density).toInt()
    fun sp(v: Float): Float = v * density

    val padLeft = dp(16)
    val padRight = dp(16)
    val gap = dp(10)

    val scenes = listOf(
        listOf("kotlin" to false, "android" to false, "spec" to false),
        listOf("spec" to false, "canvas" to true),
        listOf("canvas" to false, "node" to false, "glide" to false, "kotlin" to false),
        listOf("android" to true, "spec" to false)
    )

    val header = measureHeader(
        "Rebuild-driven: mỗi lần chạm dựng LẠI spec (transitionKey mới)\nEngine tự animate qua scene-store — không giữ ref spec",
        cardWidth, padLeft, padRight, ::dp, ::sp
    )
    val headerHeight = header.top + header.height + dp(12)

    val flow = ChipFlow(cardWidth, padLeft, padRight, gap, headerHeight)
    val sceneSpecs = scenes.map { refs ->
        flow.layout(refs.map { (key, big) -> measureChip(key, big, cardWidth, ::dp, ::sp) })
    }
    val sceneHeights = sceneSpecs.map { flow.bottomOf(it) + dp(16) }

    return RebuildDemoSpec(
        left = 0,
        top = 0,
        width = cardWidth,
        node = DemoNode("transition-demo-rebuild"),
        config = TransitionConfig(changeBounds = true, enterExit = TransitionType.FADE_SCALE),
        startIndex = 0,
        sceneSpecs = sceneSpecs,
        sceneHeights = sceneHeights,
        header = header
    )
}

// ─── Shared builders ──────────────────────────────────────────────────────────

private fun measureChip(
    key: String,
    big: Boolean,
    cardWidth: Int,
    dp: (Int) -> Int,
    sp: (Float) -> Float
): DrawSpec {

    val (label, color) = PALETTE.getValue(key)
    val hPad = if (big) dp(22) else dp(14)
    val vPad = if (big) dp(14) else dp(8)
    val textSize = if (big) sp(20f) else sp(13f)
    val radius = if (big) dp(24) else dp(18)

    val node = KeyedNode(
        key = key,
        child = ConstraintNode(
            children = listOf(
                ConstraintChild(
                    id = "bg",
                    node = OutlineNode(
                        backgroundColor = color,
                        strokeWidth = 0f,
                        cornerRadius = radius.toFloat(),
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
                        textSizePx = textSize,
                        color = Color.WHITE,
                        typeface = Typeface.DEFAULT_BOLD,
                        maxLines = 1,
                        padding = EdgeInsets.symmetric(h = hPad, v = vPad)
                    ),
                    startToStartOf = ConstraintNode.PARENT,
                    topToTopOf = ConstraintNode.PARENT
                )
            )
        )
    )
    return LayoutEngine.measure(node, Constraints(cardWidth))
}

private fun measureHeader(
    text: String,
    cardWidth: Int,
    padLeft: Int,
    padRight: Int,
    dp: (Int) -> Int,
    sp: (Float) -> Float
): DrawSpec {

    val node = TextNode(
        text = BigText(text),
        textSizePx = sp(12.5f),
        color = 0xFF202124.toInt(),
        typeface = Typeface.DEFAULT_BOLD,
        maxLines = 2,
        lineSpacingAdd = dp(3).toFloat(),
        layoutWidth = LayoutDimension.Fixed((cardWidth - padLeft - padRight).coerceAtLeast(0))
    )
    return LayoutEngine.measure(node, Constraints(cardWidth)).withPosition(padLeft, dp(10))
}

/** Xếp spec trái→phải (theo thứ tự), wrap xuống dòng khi tràn, bắt đầu dưới header. */
private class ChipFlow(
    private val width: Int,
    private val padLeft: Int,
    private val padRight: Int,
    private val gap: Int,
    private val topOffset: Int
) {

    fun layout(specs: List<DrawSpec>): List<DrawSpec> {

        var x = padLeft
        var y = topOffset
        var rowHeight = 0
        val out = ArrayList<DrawSpec>(specs.size)
        specs.forEach { base ->

            if (x > padLeft && x + base.width > width - padRight) {

                x = padLeft
                y += rowHeight + gap
                rowHeight = 0
            }
            out.add(base.withPosition(x, y))
            x += base.width + gap
            if (base.height > rowHeight) rowHeight = base.height
        }
        return out
    }

    fun bottomOf(specs: List<DrawSpec>): Int =
        specs.maxOfOrNull { it.top + it.height } ?: topOffset
}

/** Node rỗng, chỉ để spec có `node` hợp lệ; click xử lý ở hitTest của spec. */
private class DemoNode(override val id: Any) : LayoutNode() {

    override val padding: EdgeInsets = EdgeInsets.ZERO

    override fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): DrawSpec = throw UnsupportedOperationException("spec dựng thủ công")
}

/** Spec 0 chiều, chỉ mang một [onClick] để hitTest trả về cho delegate. */
private class ActionSpec(action: () -> Unit) : DrawSpec() {

    override val left: Int = 0
    override val top: Int = 0
    override val width: Int = 0
    override val height: Int = 0
    override val node: LayoutNode = ActionNode(action)

    override fun onDrawContent(canvas: Canvas) {}

    override fun withPosition(newLeft: Int, newTop: Int): DrawSpec = this

    private class ActionNode(override val onClick: (() -> Unit)?) : LayoutNode() {

        override val id: Any = Any()

        override val padding: EdgeInsets = EdgeInsets.ZERO

        override fun measure(
            ctx: MeasureContext,
            c: Constraints,
            x: Int,
            y: Int
        ): DrawSpec = throw UnsupportedOperationException()
    }
}

// ─── Card A spec: imperative transitionTo ─────────────────────────────────────

private class TransitionDemoSpec(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    node: LayoutNode,
    initialConfig: TransitionConfig,
    private val sceneSpecs: List<List<DrawSpec>>,
    private val sceneHeights: List<Int>,
    private val effects: List<Pair<String, TransitionConfig>>,
    private val headerSpecs: List<DrawSpec>,
    private val headerHeight: Int
) : TransitionSpec(left, top, width, height, sceneSpecs[0], node, initialConfig) {

    private var sceneIndex = 0
    private var effectIndex = 0

    private val switchEffectHit = ActionSpec { switchEffect() }
    private val nextSceneHit = ActionSpec { nextScene() }

    override fun onDrawContent(canvas: Canvas) {

        headerSpecs[effectIndex].draw(canvas)
        super.onDrawContent(canvas)
    }

    override fun hitTest(x: Int, y: Int): DrawSpec? {

        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null

        return if (ly < headerHeight) switchEffectHit else nextSceneHit
    }

    private fun switchEffect() {

        effectIndex = (effectIndex + 1) % effects.size
        requestDraw()
    }

    private fun nextScene() {

        sceneIndex = (sceneIndex + 1) % sceneSpecs.size
        transitionTo(
            sceneSpecs[sceneIndex],
            effects[effectIndex].second,
            endHeight = sceneHeights[sceneIndex]
        )
    }
}

// ─── Card B spec: rebuild-driven qua scene-store ──────────────────────────────

private class RebuildDemoSpec(
    left: Int,
    top: Int,
    width: Int,
    node: LayoutNode,
    config: TransitionConfig,
    private val startIndex: Int,
    private val sceneSpecs: List<List<DrawSpec>>,
    private val sceneHeights: List<Int>,
    private val header: DrawSpec
) : TransitionSpec(
    left, top, width, sceneHeights[startIndex], sceneSpecs[startIndex], node, config,
    transitionKey = startIndex,
    sceneKey = REBUILD_SCENE_KEY
) {

    private val tapHit = ActionSpec { swapNext() }

    override fun onDrawContent(canvas: Canvas) {

        header.draw(canvas)
        super.onDrawContent(canvas)
    }

    override fun hitTest(x: Int, y: Int): DrawSpec? {

        val lx = x - left
        val ly = y - top
        if (lx < 0 || ly < 0 || lx >= width || ly >= height) return null

        return tapHit
    }

    private fun swapNext() {

        val host = runtime?.view as? PrecomputedHost ?: return
        val next = RebuildDemoSpec(
            left, top, width, node, config,
            startIndex = (startIndex + 1) % sceneSpecs.size,
            sceneSpecs = sceneSpecs,
            sceneHeights = sceneHeights,
            header = header
        )
        // Dựng lại cây = gắn spec mới vào view. Spec mới khi attach sẽ đọc scene
        // trước trong store (do spec này ghi lúc attach) rồi tự animate.
        host.spec = next
    }

    private companion object {

        const val REBUILD_SCENE_KEY = "transition-demo-rebuild-scene"
    }
}
