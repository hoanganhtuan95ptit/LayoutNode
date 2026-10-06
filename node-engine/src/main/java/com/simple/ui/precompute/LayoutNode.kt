package com.simple.ui.precompute.node

import com.simple.ui.precompute.DrawSpec
import com.simple.ui.precompute.MeasureContext

// ─────────────────────────────────────────────────────────────────────────────
// Shared types dùng chung cho toàn bộ engine.
// Concrete node types nằm trong file riêng:
//   TextNode   → TextNode.kt
//   ImageNode  → ImageNode.kt
//   LinearNode → LinearNode.kt
//   FlexboxNode → FlexboxNode.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hard constraints from the parent (in pixels).
 * Use [Int.MAX_VALUE] to express "unbounded".
 */
data class Constraints(
    val maxWidth: Int,
    val maxHeight: Int = Int.MAX_VALUE
)

data class EdgeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom

    companion object {
        val ZERO = EdgeInsets()
        fun all(v: Int) = EdgeInsets(v, v, v, v)
        fun symmetric(h: Int = 0, v: Int = 0) = EdgeInsets(h, v, h, v)
    }
}

enum class Orientation { HORIZONTAL, VERTICAL }

enum class CrossAlign { START, CENTER, END }

/** Loại sự kiện touch cấp node. */
enum class TouchAction { DOWN, MOVE, UP, CANCEL }

/**
 * Một sự kiện touch gửi tới [LayoutNode.onTouch].
 * - [x]/[y]: toạ độ LOCAL so với góc trên-trái của node (đã trừ vị trí node).
 * - [rawX]/[rawY]: toạ độ trong View.
 */
class NodeTouch(
    val action: TouchAction,
    val x: Float,
    val y: Float,
    val rawX: Float,
    val rawY: Float
)

/**
 * XML-like dimension mode shared by every [LayoutNode].
 *
 * - [WrapContent] uses the measured content size, capped by parent constraints.
 * - [MatchParent] fills the bounded parent constraint. If the axis is unbounded,
 *   it falls back to wrap-content semantics.
 * - [Fixed] requests a concrete pixel size, still capped by parent constraints.
 */
sealed class LayoutDimension {
    object WrapContent : LayoutDimension()
    object MatchParent : LayoutDimension()
    data class Fixed(val px: Int) : LayoutDimension() {
        init {
            require(px >= 0) { "Fixed dimension must be >= 0, was $px" }
        }
    }

    companion object {
        fun fixed(px: Int) = Fixed(px)

        fun Int.toLayoutDimension() = if (this == -1) {
            MatchParent
        } else if (this == -2) {
            WrapContent
        } else {
            Fixed(this)
        }
    }
}

internal fun LayoutDimension.maxForMeasure(parentMax: Int): Int =
    when (this) {
        is LayoutDimension.Fixed -> px.capTo(parentMax)
        LayoutDimension.MatchParent -> parentMax
        LayoutDimension.WrapContent -> parentMax
    }.coerceAtLeast(0)

internal fun LayoutDimension.resolve(contentSize: Int, parentMax: Int): Int {
    val resolved = when (this) {
        is LayoutDimension.Fixed -> px
        LayoutDimension.MatchParent -> {
            if (parentMax == Int.MAX_VALUE) contentSize else parentMax
        }
        LayoutDimension.WrapContent -> contentSize
    }
    return resolved.capTo(parentMax).coerceAtLeast(0)
}

private fun Int.capTo(parentMax: Int): Int =
    if (parentMax == Int.MAX_VALUE) this else coerceAtMost(parentMax)

/**
 * Immutable description of a sub-tree to be laid out.
 * Everything here must be safe to read from a background thread:
 * no references to View, Context, Resources, etc.
 *
 * Pre-load Bitmap / resolve color / resolve typeface BEFORE building.
 *
 * **Mở rộng**: thêm 1 loại node mới = tạo class kế thừa [LayoutNode] + 1 class
 * kế thừa [com.simple.ui.precompute.DrawSpec], implement [measure]. Không cần đụng [com.simple.ui.precompute.LayoutEngine] hay
 * các node có sẵn.
 */
abstract class LayoutNode {

    abstract val padding: EdgeInsets

    open val layoutWidth: LayoutDimension = LayoutDimension.WrapContent

    open val layoutHeight: LayoutDimension = LayoutDimension.WrapContent

    /**
     * Optional stable identity cho TẦNG TRÊN dùng (không phải engine):
     * nhận diện/diff một logical unit qua các lần rebuild — vd
     * [com.simple.ui.precompute.node.TransitionSpec] so children theo id để biết
     * cái nào vào/ra/di chuyển, hay [com.simple.ui.precompute.node.KeyedNode].
     *
     * [com.simple.ui.precompute.LayoutEngine] KHÔNG cache/reuse theo id — mỗi
     * lần đo ra cây spec mới (cơ chế cache cũ đã bị gỡ để tránh memory leak).
     *
     * Quy ước khi dùng: cùng một logical unit qua các lần rebuild nên giữ cùng
     * [id]; trùng id giữa 2 unit khác nhau trong cùng tree = undefined behavior.
     */
    open val id: Any? = null

    /**
     * Callback chạy trên **main thread** khi user tap trong bounds của node
     * này (top-most con thắng — xem [com.simple.ui.precompute.DrawSpec.hitTest]).
     *
     * `null` = node "trong suốt" với click; hit-test sẽ bỏ qua và tìm parent
     * gần nhất có [onClick].
     *
     * Bản thân property chỉ là giá trị bất biến để build spec — dispatch thực
     * tế do [com.simple.ui.precompute.PrecomputedView] xử lý qua
     * [android.view.GestureDetector]. Không giữ ref tới View / Context ở đây.
     */
    open val onClick: (() -> Unit)? = null

    /**
     * Callback khi user nhấn giữ (long-press) trong bounds của node (top-most
     * con thắng). Dispatch trên main thread qua [android.view.GestureDetector].
     */
    open val onLongClick: (() -> Unit)? = null

    /**
     * Nhận raw touch của node: DOWN → MOVE* → UP/CANCEL, với toạ độ LOCAL
     * ([NodeTouch.x]/[y] đã trừ vị trí node) + raw theo View. Node được "khoá"
     * theo điểm DOWN: mọi MOVE/UP/CANCEL của cùng gesture đi về node đó.
     * Trả `true` nếu đã tiêu thụ. Dùng cho kéo/vuốt/giữ... ở mức node.
     */
    open val onTouch: ((NodeTouch) -> Boolean)? = null

    /**
     * Mô tả cho accessibility / UI test. Node [isInteractive] nên có giá trị để
     * TalkBack đọc và Espresso/UiAutomator định vị được (xem
     * [com.simple.ui.precompute.PrecomputedA11yHelper]).
     */
    open val contentDescription: String? = null

    /** Node có bất kỳ handler nào (click/long-click/touch) → tham gia hit-test. */
    open val isInteractive: Boolean
        get() = onClick != null || onLongClick != null || onTouch != null

    /**
     * Tự đo và trả về [com.simple.ui.precompute.DrawSpec] tại vị trí ([x], [y]).
     * Dùng [ctx] để đệ quy đo các child nếu là node container.
     */
    abstract fun measure(
        ctx: MeasureContext,
        c: Constraints,
        x: Int,
        y: Int
    ): DrawSpec
}
