package com.simple.ui.precompute

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.simple.ui.precompute.node.Constraints
import com.simple.ui.precompute.node.ConstraintChild
import com.simple.ui.precompute.node.ConstraintNode
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode
import com.simple.ui.precompute.node.OutlineNode
import com.simple.ui.precompute.node.TextNode
import com.simple.ui.precompute.text.BigText

// ─────────────────────────────────────────────────────────────────────────────
// Demo: list CHAT 1.000.000 item.
//
// KIẾN TRÚC ĐÚNG:
//   • Nguồn 1 triệu item nằm NGOÀI adapter (ở đây là provider index→node; có thể
//     thay bằng List<LayoutNode> hoặc List<data> thật — adapter không đụng hết).
//   • Đo được CHỦ ĐỘNG theo CỬA SỔ hiển thị ([SpecWindow]) trên background,
//     tách hẳn khỏi binding; item cuộn ra ngoài cửa sổ bị evict → giải phóng spec.
//   • onBindViewHolder CHỈ làm `view.spec = <spec đã đo sẵn>` — không đo gì.
//
// → RAM không phụ thuộc kích thước list (chỉ ~cửa sổ hiển thị + buffer).
// ─────────────────────────────────────────────────────────────────────────────

class LazyChatActivity : AppCompatActivity() {

    private val itemCount = 1_000_000

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val cardWidth = resources.displayMetrics.widthPixels
        val constraints = Constraints(cardWidth)

        val placeholder = runBlocking(Dispatchers.Default) {
            LayoutEngine.measure(placeholderNode(density), constraints)
        }

        val recycler = RecyclerView(this)
        val layoutManager = LinearLayoutManager(this)
        val window = SpecWindow(
            nodeAt = { pos -> chatNode(pos, cardWidth, density) },
            constraints = constraints,
            scope = lifecycleScope,
            onReady = { pos, spec ->
                // Item đang hiển thị (đang cho placeholder) → gắn spec thật ngay.
                // Item chưa hiển thị thì onBind sẽ đọc từ window khi cuộn tới.
                (recycler.findViewHolderForAdapterPosition(pos) as? ChatHolder)?.view?.spec = spec
            }
        )
        val adapter = ChatAdapter(itemCount, window, placeholder)

        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {

                window.update(
                    layoutManager.findFirstVisibleItemPosition(),
                    layoutManager.findLastVisibleItemPosition()
                )
            }
        })
        setContentView(recycler, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val jump = intent.getIntExtra("jump", -1)
        recycler.post {
            if (jump in 0 until itemCount) layoutManager.scrollToPosition(jump)
            window.update(
                layoutManager.findFirstVisibleItemPosition(),
                layoutManager.findLastVisibleItemPosition()
            )
        }
    }
}

/**
 * Đo CHỦ ĐỘNG specs cho cửa sổ [first-buffer, last+buffer] trên background,
 * evict ngoài cửa sổ. Hoàn toàn tách khỏi binding.
 */
private class SpecWindow(
    private val nodeAt: (Int) -> LayoutNode,
    private val constraints: Constraints,
    private val scope: CoroutineScope,
    private val buffer: Int = 8,
    private val onReady: (Int, DrawSpec) -> Unit
) {

    private val specs = HashMap<Int, DrawSpec>()
    private val jobs = HashMap<Int, Job>()

    fun get(position: Int): DrawSpec? = specs[position]

    fun update(firstVisible: Int, lastVisible: Int) {

        if (firstVisible < 0 || lastVisible < 0) return

        val lo = (firstVisible - buffer).coerceAtLeast(0)
        val hi = lastVisible + buffer

        // Evict ngoài cửa sổ → giải phóng spec (Picture) đã đo.
        val keys = specs.keys.iterator()
        while (keys.hasNext()) {

            val k = keys.next()
            if (k < lo || k > hi) keys.remove()
        }
        val jobKeys = jobs.keys.iterator()
        while (jobKeys.hasNext()) {

            val k = jobKeys.next()
            if (k < lo || k > hi) { jobs[k]?.cancel(); jobKeys.remove() }
        }

        // Đo những item còn thiếu trong cửa sổ.
        for (p in lo..hi) {

            if (specs.containsKey(p) || jobs.containsKey(p)) continue
            jobs[p] = scope.launch {

                val spec = withContext(Dispatchers.Default) { LayoutEngine.measure(nodeAt(p), constraints) }
                jobs.remove(p)
                if (p in lo..hi) {

                    specs[p] = spec
                    onReady(p, spec)
                }
            }
        }
    }
}

private class ChatHolder(val view: PrecomputedView) : RecyclerView.ViewHolder(view)

private class ChatAdapter(
    private val count: Int,
    private val window: SpecWindow,
    private val placeholder: DrawSpec
) : RecyclerView.Adapter<ChatHolder>() {

    override fun getItemCount(): Int = count

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatHolder {

        val view = PrecomputedView(parent.context).apply {
            isFlexibleSize = true
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        return ChatHolder(view)
    }

    // Binding CHỈ set spec — không đo. Spec đã (hoặc sẽ) được [SpecWindow] đo sẵn.
    override fun onBindViewHolder(holder: ChatHolder, position: Int) {

        holder.view.spec = window.get(position) ?: placeholder
    }
}

private fun placeholderNode(density: Float): LayoutNode = OutlineNode(
    backgroundColor = 0x11000000,
    strokeWidth = 0f,
    layoutWidth = LayoutDimension.MatchParent,
    layoutHeight = LayoutDimension.Fixed((64 * density).toInt())
)

private fun chatNode(position: Int, cardWidth: Int, density: Float): LayoutNode {

    fun dp(v: Int): Int = (v * density).toInt()
    fun sp(v: Float): Float = v * density

    val mine = position % 3 == 0
    val bubbleColor = if (mine) 0xFF2196F3.toInt() else 0xFFECECEC.toInt()
    val textColor = if (mine) Color.WHITE else 0xFF202124.toInt()
    val words = (position % 8) + 1
    val text = "#$position  " + "lorem ipsum dolor ".repeat(words).trim()

    return ConstraintNode(
        layoutWidth = LayoutDimension.MatchParent,
        padding = EdgeInsets.symmetric(h = dp(12), v = dp(4)),
        children = listOf(
            ConstraintChild(
                id = "bg",
                node = OutlineNode(
                    backgroundColor = bubbleColor,
                    strokeWidth = 0f,
                    cornerRadius = dp(16).toFloat(),
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
                    text = BigText(text),
                    textSizePx = sp(15f),
                    color = textColor,
                    maxLines = 20,
                    padding = EdgeInsets.symmetric(h = dp(14), v = dp(10))
                ),
                startToStartOf = if (mine) null else ConstraintNode.PARENT,
                endToEndOf = if (mine) ConstraintNode.PARENT else null,
                topToTopOf = ConstraintNode.PARENT,
                width = LayoutDimension.Fixed((cardWidth * 0.74f).toInt())
            )
        )
    )
}
