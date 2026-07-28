package com.simple.ui.precompute

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.simple.adapter.MultiAdapter
import com.simple.adapter.MultiRecyclerView
import com.simple.adapter.ViewItem
import com.simple.t.R
import com.simple.ui.precompute.image.BigImage
import com.simple.ui.precompute.loader.GlideImageLoader
import com.simple.ui.precompute.loader.ImageLoader
import com.simple.ui.precompute.node.Constraints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrecomputeDemoScreen(
    private val activity: AppCompatActivity,
) {

    private val dp by lazy { activity.resources.displayMetrics.density }
    private val cardWidth by lazy { activity.resources.displayMetrics.widthPixels - (32 * dp).toInt() }

    fun render() {

        val recyclerView = activity.findViewById<MultiRecyclerView>(R.id.recyclerView)
        val iconSource = BigImage(DEMO_ICON_URL)
        val dp48 = dp(48)

        if (ImageLoader.get() == null) {

            ImageLoader.install(GlideImageLoader(activity))
        }
        setupRecyclerView(recyclerView)

        activity.lifecycleScope.launch {

            val viewItems = withContext(Dispatchers.Default) {

                PrecomputeUiSectionRenderer(
                    activity = activity,
                    cardWidth = cardWidth,
                    iconSource = iconSource,
                    iconSizePx = dp48,
                    items = PrecomputeDemoData.items,
                    profiles = PrecomputeDemoData.profiles,
                    notes = PrecomputeDemoData.notes,
                ).buildItems()
            }

            val adapter = recyclerView.adapter as? MultiAdapter ?: return@launch
            adapter.submitList(
                viewItems,
                adapter = listOf(
                    DemoTextAdapter::class.java.name,
                    PrecomputedCardAdapter::class.java.name
                )
            )
        }
    }

    private fun setupRecyclerView(recyclerView: MultiRecyclerView) {

        recyclerView.layoutManager = LinearLayoutManager(activity)
    }

    private fun dp(value: Int): Int = (value * dp).toInt()

    private companion object {

        const val DEMO_ICON_URL = "https://developer.android.com/static/images/brand/Android_Robot.png"
    }
}

private class PrecomputeUiSectionRenderer(
    private val activity: AppCompatActivity,
    private val cardWidth: Int,
    private val iconSource: BigImage,
    private val iconSizePx: Int,
    private val items: List<PrecomputeDemoData.WordItem>,
    private val profiles: List<PrecomputeDemoData.ProfileItem>,
    private val notes: List<PrecomputeDemoData.NoteItem>,
) {

    private val builders = PrecomputeCardBuilders(activity)
    private val dp by lazy { activity.resources.displayMetrics.density }
    private val constraints = Constraints(cardWidth)

    fun buildItems(): List<ViewItem> {

        val out = ArrayList<ViewItem>()
        addLinearCards(out)
        addConstraintCards(out)
        addProfileCards(out)
        addWrapContentCards(out)
        addLoadingOutlineCard(out)
        addTransformCard(out)
        addPhoneticChipCards(out)
        addScoreGaugeCards(out)
        addProgressBarCards(out)
        addNoteCards(out)
        addTrySpeakCard(out)
        addDashedLineTextCard(out)
        addFlexboxTagsCard(out)
        addFlexboxProgressExamples(out)
        addFlexboxOutlineExamples(out)
        addColorChangingFlexboxCard(out)
        addFooter(out)
        return out
    }

    private fun addLinearCards(out: MutableList<ViewItem>) {

        addSection(out, "① LinearNode  —  Row / Column")
        addCards(out, "linear") {
            items.map { item ->
                builders.buildLinearCard(item.word, item.ipa, item.meaning, iconSource, iconSizePx)
            }
        }
    }

    private fun addConstraintCards(out: MutableList<ViewItem>) {

        addSection(out, "② ConstraintNode  —  tương tự ConstraintLayout")
        addCards(out, "constraint") {
            items.map { item ->
                builders.buildConstraintCard(item.word, item.ipa, item.meaning, iconSource, iconSizePx)
            }
        }
    }

    private fun addProfileCards(out: MutableList<ViewItem>) {

        addSection(out, "③ ConstraintNode  —  view con leo nhau (view-to-view)")
        addCards(out, "profile") {
            profiles.map { profile ->
                builders.buildProfileConstraintCard(profile.name, profile.tag, profile.role, iconSource, iconSizePx)
            }
        }
    }

    private fun addWrapContentCards(out: MutableList<ViewItem>) {

        addSection(out, "④ ConstraintNode  —  WrapContent (vừa khít nội dung)")
        addCards(out, "wrap") {
            listOf(
                builders.buildWrapContentTagsCard(),
                builders.buildWrapContentCenterCard()
            )
        }
    }

    private fun addLoadingOutlineCard(out: MutableList<ViewItem>) {

        addSection(out, "⑤ OutlineNode  —  viền loading bo góc")
        addCards(out, "outline") {
            listOf(builders.buildLoadingOutlineCard())
        }
    }

    private fun addTransformCard(out: MutableList<ViewItem>) {

        addSection(out, "⑥ ImageNode  —  Glide transform (Circle / Rounded)")
        addCards(out, "transform") {
            listOf(builders.buildTransformCard(iconSizePx))
        }
    }

    private fun addPhoneticChipCards(out: MutableList<ViewItem>) {

        addSection(out, "⑦ PhoneticChip  —  từ XML → Node")
        addCards(out, "phonetic") {
            items.map { item ->
                builders.buildPhoneticChip("${item.word}\n${item.ipa}")
            }
        }
    }

    private fun addScoreGaugeCards(out: MutableList<ViewItem>) {

        addSection(out, "⑧ ScoreGauge  —  GaugeArcNode + GaugeScoreNode")
        addCards(out, "score") {
            items.map {
                builders.buildScoreGaugeSpec(progress = 90, sizePx = dp(160))
            }
        }
    }

    private fun addProgressBarCards(out: MutableList<ViewItem>) {

        addSection(out, "⑨ ProgressBarNode  —  thanh tiến độ ngang")
        addCards(out, "progress") {
            listOf(
                builders.buildProgressBarCard("Listening accuracy", 68, 100, 0xFF1B998B.toInt()),
                builders.buildProgressBarCard("Speaking fluency", 42, 100, 0xFFE76F51.toInt()),
                builders.buildProgressBarCard("Daily goal", 9, 12, 0xFF5B7CFA.toInt())
            )
        }
    }

    private fun addNoteCards(out: MutableList<ViewItem>) {

        addSection(out, "⑩ XML NoteRow  —  LinearLayout → Node")
        addCards(out, "note") {
            notes.map { note ->
                builders.buildNoteRowFromXml(note.title, note.note, iconSource)
            }
        }
    }

    private fun addTrySpeakCard(out: MutableList<ViewItem>) {

        addSection(out, "⑪ TrySpeakChip  —  ảnh mẫu → node cũ")
        addCards(out, "try-speak") {
            listOf(builders.buildTrySpeakChipFromImage())
        }
    }

    private fun addDashedLineTextCard(out: MutableList<ViewItem>) {

        addSection(out, "⑫ DashedLineText  —  LineNode + TextNode")
        addCards(out, "dashed-line") {
            listOf(builders.buildDashedLineTextFromImage())
        }
    }

    private fun addFlexboxTagsCard(out: MutableList<ViewItem>) {

        addSection(out, "⑬ FlexboxNode  —  wrap tags giống FlexboxLayout")
        addCards(out, "flexbox") {
            listOf(builders.buildFlexboxTagsCard())
        }
    }

    private fun addFlexboxProgressExamples(out: MutableList<ViewItem>) {

        addSection(out, "⑭ FlexboxNode  —  nhiều text item có ProgressBarNode làm nền")
        addCards(out, "flexbox-progress") {
            listOf(
                builders.buildProgressFlexboxTagsCard(),
                builders.buildProgressFlexboxGridCard(),
                builders.buildProgressFlexboxStatusCard(),
                builders.buildProgressFlexboxDenseCard()
            )
        }
    }

    private fun addColorChangingFlexboxCard(out: MutableList<ViewItem>) {

        addSection(out, "⑯ Custom FlexboxNode  —  đổi màu node id aaa mỗi 5s")
        addCards(out, "color-flexbox") {
            listOf(builders.buildColorChangingFlexboxCard())
        }
    }

    private fun addFlexboxOutlineExamples(out: MutableList<ViewItem>) {

        addSection(out, "⑮ FlexboxNode  —  nhiều TextNode có OutlineNode làm nền")
        addCards(out, "flexbox-outline") {
            listOf(
                builders.buildOutlineFlexboxTagsCard(),
                builders.buildOutlineFlexboxGridCard(),
                builders.buildOutlineFlexboxStatusCard(),
                builders.buildOutlineFlexboxDenseCard()
            )
        }
    }

    private fun addFooter(out: MutableList<ViewItem>) {

        val totalCards = out.count { it is PrecomputedCardItem }
        out.add(
            DemoTextItem(
                id = "footer",
                text = "$totalCards cards — RecyclerView + MultiAdapter + PrecomputedView, measured on bg thread",
                style = DemoTextStyle.FOOTER,
                topMarginPx = dp(16),
                bottomMarginPx = dp(24)
            )
        )
    }

    private fun addSection(out: MutableList<ViewItem>, label: String) {

        val index = out.size
        out.add(
            DemoTextItem(
                id = "section-$index",
                text = label,
                style = DemoTextStyle.SECTION,
                topMarginPx = dp(20)
            )
        )
    }

    private fun addCards(
        out: MutableList<ViewItem>,
        prefix: String,
        nodeProvider: () -> List<com.simple.ui.precompute.node.LayoutNode>
    ) {

        nodeProvider().forEachIndexed { index, node ->

            out.add(
                PrecomputedCardItem(
                    id = "$prefix-$index",
                    spec = LayoutEngine.measure(node, constraints),
                    topMarginPx = dp(10)
                )
            )
        }
    }

    private fun dp(value: Int): Int = (value * dp).toInt()
}
