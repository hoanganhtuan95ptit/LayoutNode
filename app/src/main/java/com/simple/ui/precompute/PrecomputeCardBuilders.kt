package com.simple.ui.precompute

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import com.simple.t.R
import com.simple.ui.precompute.image.BigImage
import com.simple.ui.precompute.image.CircleCrop
import com.simple.ui.precompute.image.RoundedCorners
import com.simple.ui.precompute.image.addTransform
import com.simple.ui.precompute.image.build
import com.simple.ui.precompute.image.toBuilder
import com.simple.ui.precompute.node.ConstraintChild
import com.simple.ui.precompute.node.ConstraintNode
import com.simple.ui.precompute.node.CrossAlign
import com.simple.ui.precompute.node.EdgeInsets
import com.simple.ui.precompute.node.FlexAlignContent
import com.simple.ui.precompute.node.FlexAlignItems
import com.simple.ui.precompute.node.FlexChild
import com.simple.ui.precompute.node.FlexDirection
import com.simple.ui.precompute.node.FlexJustifyContent
import com.simple.ui.precompute.node.FlexWrap
import com.simple.ui.precompute.node.FlexboxNode
import com.simple.ui.precompute.node.GaugeArcNode
import com.simple.ui.precompute.node.GaugeScoreNode
import com.simple.ui.precompute.node.ImageNode
import com.simple.ui.precompute.node.LayoutDimension
import com.simple.ui.precompute.node.LayoutNode
import com.simple.ui.precompute.node.LineNode
import com.simple.ui.precompute.node.LinearNode
import com.simple.ui.precompute.node.OutlineNode
import com.simple.ui.precompute.node.OutlineState
import com.simple.ui.precompute.node.ProgressBarNode
import com.simple.ui.precompute.node.SpaceNode
import com.simple.ui.precompute.node.TextNode
import com.simple.ui.precompute.text.BigText
import com.simple.ui.precompute.text.build
import com.simple.ui.precompute.text.span.BigForegroundColor
import com.simple.ui.precompute.text.span.BigTextSize
import com.simple.ui.precompute.text.with

class PrecomputeCardBuilders(
    private val activity: androidx.appcompat.app.AppCompatActivity,
) {

    private val dp by lazy { activity.resources.displayMetrics.density }

    fun buildLinearCard(
        word: String,
        ipa: String,
        meaning: String,
        iconSource: BigImage,
        iconSizePx: Int,
    ): LayoutNode = LinearNode(
        orientation = com.simple.ui.precompute.node.Orientation.HORIZONTAL,
        crossAlign = CrossAlign.CENTER,
        gap = dp(12),
        padding = EdgeInsets.all(dp(12)),
        children = listOf(
            ImageNode(source = iconSource, layoutWidth = LayoutDimension.Fixed(iconSizePx), layoutHeight = LayoutDimension.Fixed(iconSizePx)),
            LinearNode(
                orientation = com.simple.ui.precompute.node.Orientation.VERTICAL,
                gap = dp(4),
                children = listOf(
                    TextNode(BigText(word), sp(16f), Color.BLACK, typeface = Typeface.DEFAULT_BOLD, maxLines = 2),
                    TextNode(BigText(ipa), sp(14f), 0xFF6200EE.toInt(), maxLines = 1),
                    TextNode(BigText(meaning), sp(12f), Color.GRAY, maxLines = 2),
                )
            )
        )
    )

    fun buildNoteRowFromXml(title: String, note: String, iconSource: BigImage): LayoutNode = LinearNode(
        orientation = com.simple.ui.precompute.node.Orientation.HORIZONTAL,
        crossAlign = CrossAlign.START,
        padding = EdgeInsets.symmetric(h = dp(16), v = dp(8)),
        layoutWidth = LayoutDimension.MatchParent,
        children = listOf(
            ImageNode(source = iconSource, layoutWidth = LayoutDimension.Fixed(dp(28)), layoutHeight = LayoutDimension.Fixed(dp(28))),
            SpaceNode.horizontal(dp(16)),
            LinearNode(
                orientation = com.simple.ui.precompute.node.Orientation.VERTICAL,
                layoutWidth = LayoutDimension.MatchParent,
                children = listOf(
                    TextNode(text = BigText(title), textSizePx = sp(14f), color = 0xFF202124.toInt(), layoutWidth = LayoutDimension.MatchParent),
                    SpaceNode.vertical(dp(8)),
                    TextNode(text = BigText(note), textSizePx = sp(14f), color = 0xFF5F6368.toInt(), layoutWidth = LayoutDimension.MatchParent)
                )
            )
        )
    )

    fun buildFlexboxTagsCard(): LayoutNode { /* unchanged body moved below */ 
        val tags = listOf(
            "Kotlin" to 0xFFE91E63.toInt(),
            "Android" to 0xFF4CAF50.toInt(),
            "Precompute" to 0xFF2196F3.toInt(),
            "LayoutEngine" to 0xFF795548.toInt(),
            "FlexboxLayout" to 0xFF6200EE.toInt(),
            "wrapBefore" to 0xFFFF9800.toInt(),
            "space_between" to 0xFF009688.toInt(),
            "alignItems" to 0xFF3F51B5.toInt(),
            "DrawSpec" to 0xFF607D8B.toInt(),
        )
        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.SPACE_BETWEEN,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(8),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = tags.mapIndexed { index, (label, color) ->
                FlexChild(node = buildTagChip(label, color), order = index, wrapBefore = label == "space_between")
            }
        )
    }

    fun buildColorChangingFlexboxCard(): LayoutNode {
        val labels = listOf(
            "normal" to "node-1",
            "aaa" to "aaa",
            "reused measure" to "node-2",
            "custom spec" to "node-3",
            "5s ticker" to "node-4",
        )
        return ColorChangingFlexboxNode(
            targetId = "aaa",
            colors = listOf(0xFFE91E63.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFF9800.toInt()),
            intervalMs = 5_000L,
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.CENTER,
            gap = dp(10),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = labels.mapIndexed { index, (label, id) ->
                FlexChild(
                    node = RuntimeColorTextNode(
                        id = id,
                        text = BigText(label),
                        textSizePx = sp(15f),
                        color = if (id == "aaa") 0xFFE91E63.toInt() else 0xFF5F6368.toInt(),
                        typeface = if (id == "aaa") Typeface.DEFAULT_BOLD else null,
                        maxLines = 1,
                        padding = EdgeInsets.symmetric(h = dp(10), v = dp(6)),
                    ),
                    order = index
                )
            }
        )
    }

    fun buildConstraintCard(word: String, ipa: String, meaning: String, iconSource: BigImage, iconSizePx: Int): LayoutNode = ConstraintNode(
        padding = EdgeInsets.all(dp(12)),
        children = listOf(
            ConstraintChild(id = "icon", node = ImageNode(source = iconSource, layoutWidth = LayoutDimension.Fixed(iconSizePx), layoutHeight = LayoutDimension.Fixed(iconSizePx)), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT),
            ConstraintChild(id = "badge", node = TextNode(text = BigText("EN"), textSizePx = sp(10f), color = 0xFF6200EE.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(6), v = dp(3))), endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT),
            ConstraintChild(id = "title", node = TextNode(BigText(word), sp(16f), Color.BLACK, typeface = Typeface.DEFAULT_BOLD, maxLines = 2), startToEndOf = "icon", marginStart = dp(12), endToStartOf = "badge", marginEnd = dp(8), topToTopOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent),
            ConstraintChild(id = "ipa", node = TextNode(BigText(ipa), sp(14f), 0xFF6200EE.toInt(), maxLines = 1), startToStartOf = "title", endToEndOf = "title", topToBottomOf = "title", marginTop = dp(4), width = LayoutDimension.MatchParent),
            ConstraintChild(id = "meaning", node = TextNode(BigText(meaning), sp(12f), Color.GRAY, maxLines = 2), startToStartOf = "ipa", endToEndOf = "ipa", topToBottomOf = "ipa", marginTop = dp(4), width = LayoutDimension.MatchParent),
        )
    )

    fun buildProfileConstraintCard(name: String, tag: String, role: String, avatarSource: BigImage, avatarSizePx: Int): LayoutNode = ConstraintNode(
        padding = EdgeInsets(left = dp(12), top = dp(12), right = dp(12), bottom = dp(12)),
        children = listOf(
            ConstraintChild(id = "avatar", node = ImageNode(source = avatarSource, layoutWidth = LayoutDimension.Fixed(avatarSizePx), layoutHeight = LayoutDimension.Fixed(avatarSizePx)), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT),
            ConstraintChild(id = "name", node = TextNode(BigText(name), sp(16f), Color.BLACK, typeface = Typeface.DEFAULT_BOLD, maxLines = 1), startToEndOf = "avatar", marginStart = dp(12), endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent),
            ConstraintChild(id = "tag", node = TextNode(text = BigText(tag), textSizePx = sp(11f), color = 0xFFFFFFFF.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(6), v = dp(3))), startToEndOf = "avatar", marginStart = dp(12), topToBottomOf = "name", marginTop = dp(4)),
            ConstraintChild(id = "role", node = TextNode(BigText(role), sp(11f), Color.DKGRAY, maxLines = 1), startToEndOf = "tag", marginStart = dp(6), endToEndOf = ConstraintNode.PARENT, topToTopOf = "tag", width = LayoutDimension.MatchParent),
            ConstraintChild(id = "bio", node = TextNode(BigText("Tapped: view ③ leo ④ (ngang) → ⑤ leo ④ (dọc)"), sp(10f), 0xFF9E9E9E.toInt(), maxLines = 2), startToEndOf = "avatar", marginStart = dp(12), endToEndOf = ConstraintNode.PARENT, topToBottomOf = "role", marginTop = dp(4), width = LayoutDimension.MatchParent),
            ConstraintChild(id = "btn_like", node = TextNode(text = BigText("♥  Like"), textSizePx = sp(12f), color = 0xFFFFFFFF.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(14), v = dp(7))), startToStartOf = ConstraintNode.PARENT, topToBottomOf = "bio", marginTop = dp(10)),
            ConstraintChild(id = "btn_share", node = TextNode(text = BigText("↗  Share"), textSizePx = sp(12f), color = 0xFF6200EE.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(14), v = dp(7))), startToEndOf = "btn_like", marginStart = dp(8), topToTopOf = "btn_like"),
        )
    )

    fun buildWrapContentTagsCard(): LayoutNode = ConstraintNode(
        padding = EdgeInsets.all(dp(16)),
        children = listOf(
            ConstraintChild(id = "tag_1", node = TextNode("Kotlin".with(BigForegroundColor(Color.GREEN), BigTextSize(20)).build(), sp(1f), 0xFFE91E63.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(12), v = dp(6))), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT),
            ConstraintChild(id = "tag_2", node = TextNode(BigText("Android"), sp(14f), 0xFF4CAF50.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(12), v = dp(6))), startToEndOf = "tag_1", marginStart = dp(8), topToTopOf = "tag_1"),
            ConstraintChild(id = "tag_3", node = TextNode(BigText("WrapContent"), sp(14f), 0xFF2196F3.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(12), v = dp(6))), startToEndOf = "tag_2", marginStart = dp(8), topToTopOf = "tag_2")
        )
    )

    fun buildWrapContentCenterCard(): LayoutNode = ConstraintNode(
        layoutWidth = LayoutDimension.MatchParent,
        padding = EdgeInsets.all(dp(16)),
        children = listOf(
            ConstraintChild(id = "btn_confirm", node = TextNode(text = BigText("NÚT CĂN GIỮA MÀN HÌNH"), textSizePx = sp(14f), color = 0xFF6200EE.toInt(), typeface = Typeface.DEFAULT_BOLD, padding = EdgeInsets.symmetric(h = dp(24), v = dp(12))), startToStartOf = ConstraintNode.PARENT, endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT)
        )
    )

    fun buildTransformCard(iconSizePx: Int): LayoutNode {
        val baseSource = R.mipmap.ic_launcher
        val rounded = dp(12)
        fun item(label: String, image: BigImage) = LinearNode(
            orientation = com.simple.ui.precompute.node.Orientation.VERTICAL,
            crossAlign = CrossAlign.CENTER,
            gap = dp(6),
            children = listOf(ImageNode(source = image, layoutWidth = LayoutDimension.Fixed(iconSizePx), layoutHeight = LayoutDimension.Fixed(iconSizePx)), TextNode(text = BigText(label), textSizePx = sp(11f), color = Color.DKGRAY, typeface = Typeface.DEFAULT_BOLD, maxLines = 1))
        )
        return LinearNode(
            orientation = com.simple.ui.precompute.node.Orientation.HORIZONTAL,
            crossAlign = CrossAlign.CENTER,
            gap = dp(20),
            padding = EdgeInsets.all(dp(16)),
            children = listOf(
                item("Original", BigImage(source = baseSource)),
                item("CircleCrop", R.drawable.image.toBuilder().addTransform(CircleCrop).build()),
                item("Rounded ${rounded}px", R.drawable.img1.toBuilder().addTransform(RoundedCorners(rounded)).build()),
            )
        )
    }

    fun buildPhoneticChip(phonetic: String): LayoutNode = ConstraintNode(
        children = listOf(
            ConstraintChild(id = "bg", node = OutlineNode(backgroundColor = Color.parseColor("#55BB55"), strokeWidth = 0f, layoutWidth = LayoutDimension.MatchParent, layoutHeight = LayoutDimension.MatchParent), startToStartOf = "content", endToEndOf = "content", topToTopOf = "content", bottomToBottomOf = "content", width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
            ConstraintChild(id = "content", node = LinearNode(orientation = com.simple.ui.precompute.node.Orientation.HORIZONTAL, crossAlign = CrossAlign.CENTER, gap = dp(4), padding = EdgeInsets.symmetric(h = dp(8), v = dp(4)), children = listOf(TextNode(text = BigText(phonetic), textSizePx = sp(14f), color = Color.WHITE), ImageNode(source = BigImage(R.mipmap.ic_launcher), layoutWidth = LayoutDimension.Fixed(dp(10)), layoutHeight = LayoutDimension.Fixed(dp(24))))), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT)
        )
    )

    fun buildTrySpeakChipFromImage(): LayoutNode = ConstraintNode(
        children = listOf(
            ConstraintChild(id = "outline", node = OutlineNode(backgroundColor = Color.WHITE, strokeColor = 0xFF19D96B.toInt(), strokeWidth = dp(2).toFloat(), cornerRadius = dp(24).toFloat(), dashWidth = dp(5).toFloat(), dashGap = dp(5).toFloat(), layoutWidth = LayoutDimension.MatchParent, layoutHeight = LayoutDimension.MatchParent), startToStartOf = "content", endToEndOf = "content", topToTopOf = "content", bottomToBottomOf = "content", width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
            ConstraintChild(id = "content", node = LinearNode(orientation = com.simple.ui.precompute.node.Orientation.HORIZONTAL, crossAlign = CrossAlign.CENTER, gap = dp(10), padding = EdgeInsets(left = dp(16), top = dp(10), right = dp(18), bottom = dp(10)), children = listOf(ImageNode(source = BigImage(R.drawable.ic_try_speak_mic), layoutWidth = LayoutDimension.Fixed(dp(22)), layoutHeight = LayoutDimension.Fixed(dp(22))), TextNode(text = BigText("Thử nói"), textSizePx = sp(20f), color = 0xFF19D96B.toInt(), maxLines = 1))), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT)
        )
    )

    fun buildDashedLineTextFromImage(): LayoutNode = LinearNode(
        orientation = com.simple.ui.precompute.node.Orientation.VERTICAL,
        crossAlign = CrossAlign.CENTER,
        gap = dp(12),
        padding = EdgeInsets(top = dp(14)),
        layoutWidth = LayoutDimension.MatchParent,
        layoutHeight = LayoutDimension.Fixed(dp(86)),
        children = listOf(LineNode(color = 0xFFB8B8B8.toInt(), strokeWidth = 1.5f * dp, dashWidth = dp(6).toFloat(), dashGap = dp(6).toFloat(), layoutWidth = LayoutDimension.Fixed(dp(240)), layoutHeight = LayoutDimension.Fixed(dp(2))), TextNode(text = BigText("rất vui được quen biết bạn"), textSizePx = sp(20f), color = 0xFF202124.toInt(), maxLines = 1))
    )

    fun buildLoadingOutlineCard(): LayoutNode = ConstraintNode(
        layoutWidth = LayoutDimension.MatchParent,
        layoutHeight = LayoutDimension.Fixed(dp(92)),
        children = listOf(
            ConstraintChild(id = "outline", node = OutlineNode(layoutWidth = LayoutDimension.MatchParent, layoutHeight = LayoutDimension.MatchParent, backgroundColor = 0x11E91E63, strokeColor = 0xFFE91E63.toInt(), strokeWidth = dp(2).toFloat(), cornerRadius = dp(16).toFloat(), dashWidth = dp(10).toFloat(), dashGap = dp(6).toFloat(), loadingSegmentRatio = 0.35f, loadingDurationMs = 10000L, state = OutlineState.LOADING), startToStartOf = ConstraintNode.PARENT, endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT, bottomToBottomOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
            ConstraintChild(id = "content", node = LinearNode(orientation = com.simple.ui.precompute.node.Orientation.VERTICAL, gap = dp(4), padding = EdgeInsets.all(dp(16)), layoutWidth = LayoutDimension.MatchParent, children = listOf(TextNode(text = BigText("Outline đang xử lý"), textSizePx = sp(16f), color = Color.BLACK, typeface = Typeface.DEFAULT_BOLD, maxLines = 1), TextNode(text = BigText("OutlineNode chỉ vẽ effect; content nằm ở sibling khác."), textSizePx = sp(12f), color = Color.DKGRAY, maxLines = 2))), startToStartOf = ConstraintNode.PARENT, endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT, bottomToBottomOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent, verticalBias = 0.5f)
        )
    )

    fun buildProgressBarCard(title: String, progress: Int, max: Int, progressColor: Int): LayoutNode {
        val safeMax = max.coerceAtLeast(1)
        val safeProgress = progress.coerceIn(0, safeMax)
        return LinearNode(
            orientation = com.simple.ui.precompute.node.Orientation.VERTICAL,
            gap = dp(8),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = listOf(TextNode(text = BigText("$title  $safeProgress/$safeMax"), textSizePx = sp(14f), color = 0xFF202124.toInt(), typeface = Typeface.DEFAULT_BOLD, maxLines = 1), ProgressBarNode(progress = safeProgress, max = safeMax, trackColor = 0xFFE8EAED.toInt(), progressColor = progressColor, layoutWidth = LayoutDimension.MatchParent, layoutHeight = LayoutDimension.Fixed(dp(8))))
        )
    }

    fun buildProgressFlexboxTagsCard(): LayoutNode {
        val items = listOf(
            "Kotlin" to 92,
            "Android" to 88,
            "Compose" to 74,
            "RecyclerView" to 81,
            "Glide" to 69,
            "Canvas" to 77,
            "LayoutEngine" to 95,
            "DrawSpec" to 86,
            "FlexboxNode" to 79,
            "MeasurePolicy" to 91,
            "Picture text" to 66,
            "Runtime" to 84,
            "Background" to 58,
            "ProgressBarNode" to 72,
            "ConstraintNode" to 89,
            "Precompute" to 97,
        )

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(8),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, (label, progress) ->
                FlexChild(
                    order = index,
                    node = buildProgressTextChip(
                        label = "$label $progress%",
                        progress = progress,
                        progressColor = colorByProgress(progress)
                    )
                )
            }
        )
    }

    fun buildProgressFlexboxGridCard(): LayoutNode {
        val items = List(30) { index ->
            "Task ${index + 1}" to ((index * 11 + 35) % 100)
        }

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.STRETCH,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(8),
            padding = EdgeInsets.all(dp(12)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, (label, progress) ->
                FlexChild(
                    order = index,
                    flexGrow = 1f,
                    flexShrink = 1f,
                    flexBasisPercent = 0.31f,
                    node = buildProgressTextCell(
                        label = "$label  $progress%",
                        progress = progress,
                        progressColor = colorByProgress(progress)
                    )
                )
            }
        )
    }

    fun buildProgressFlexboxStatusCard(): LayoutNode {
        val items = listOf(
            ProgressChipData("Done", 100, 0xFF2E7D32.toInt()),
            ProgressChipData("Loading", 64, 0xFF1976D2.toInt()),
            ProgressChipData("Warning", 48, 0xFFF9A825.toInt()),
            ProgressChipData("Failed", 22, 0xFFC62828.toInt()),
            ProgressChipData("Queued", 12, 0xFF607D8B.toInt()),
            ProgressChipData("Syncing", 73, 0xFF00897B.toInt()),
            ProgressChipData("Cached", 91, 0xFF5E35B1.toInt()),
            ProgressChipData("Decoded", 55, 0xFF546E7A.toInt()),
            ProgressChipData("Measured", 83, 0xFF3949AB.toInt()),
            ProgressChipData("Drawn", 76, 0xFF6D4C41.toInt()),
        )

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.SPACE_BETWEEN,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(10),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, item ->
                FlexChild(
                    order = index,
                    wrapBefore = item.label == "Cached",
                    node = buildProgressTextChip(
                        label = "${item.label} ${item.progress}%",
                        progress = item.progress,
                        progressColor = item.color,
                        trackColor = 0xFFF1F3F4.toInt()
                    )
                )
            }
        )
    }

    fun buildProgressFlexboxDenseCard(): LayoutNode {
        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            mainGap = dp(6),
            crossGap = dp(6),
            padding = EdgeInsets.all(dp(12)),
            layoutWidth = LayoutDimension.MatchParent,
            children = List(80) { index ->
                val progress = (20 + index * 7) % 100
                FlexChild(
                    order = index,
                    node = buildProgressTextChip(
                        label = "#${index + 1} $progress%",
                        progress = progress,
                        progressColor = colorByProgress(progress),
                        horizontalPadding = dp(9),
                        verticalPadding = dp(5),
                        textSizePx = sp(11f)
                    )
                )
            }
        )
    }

    fun buildOutlineFlexboxTagsCard(): LayoutNode {
        val items = listOf(
            OutlineChipData("Kotlin", 0xFFE91E63.toInt(), 0x1AE91E63),
            OutlineChipData("Android", 0xFF2E7D32.toInt(), 0x1A2E7D32),
            OutlineChipData("Precompute", 0xFF1565C0.toInt(), 0x1A1565C0),
            OutlineChipData("LayoutEngine", 0xFF6D4C41.toInt(), 0x1A6D4C41),
            OutlineChipData("FlexboxNode", 0xFF5E35B1.toInt(), 0x1A5E35B1),
            OutlineChipData("OutlineNode", 0xFF00897B.toInt(), 0x1A00897B),
            OutlineChipData("TextNode", 0xFFF57C00.toInt(), 0x1AF57C00),
            OutlineChipData("MeasurePolicy", 0xFF455A64.toInt(), 0x1A455A64),
            OutlineChipData("DrawSpec", 0xFFC2185B.toInt(), 0x1AC2185B),
            OutlineChipData("Picture", 0xFF3949AB.toInt(), 0x1A3949AB),
            OutlineChipData("Runtime", 0xFF00796B.toInt(), 0x1A00796B),
            OutlineChipData("Recycler item", 0xFF7B1FA2.toInt(), 0x1A7B1FA2),
            OutlineChipData("Background", 0xFF3F51B5.toInt(), 0x1A3F51B5),
            OutlineChipData("Constraints", 0xFF5D4037.toInt(), 0x1A5D4037),
            OutlineChipData("Cache", 0xFFAF7A00.toInt(), 0x1AAF7A00),
            OutlineChipData("No View child", 0xFF00695C.toInt(), 0x1A00695C),
        )

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(8),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, item ->
                FlexChild(
                    order = index,
                    node = buildOutlineTextChip(
                        label = item.label,
                        strokeColor = item.strokeColor,
                        backgroundColor = item.backgroundColor,
                        textColor = item.strokeColor
                    )
                )
            }
        )
    }

    fun buildOutlineFlexboxGridCard(): LayoutNode {
        val items = List(36) { index ->
            val color = outlineColorAt(index)
            OutlineChipData(
                label = "Cell ${index + 1}",
                strokeColor = color,
                backgroundColor = color.withAlpha(0x14)
            )
        }

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.STRETCH,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(8),
            padding = EdgeInsets.all(dp(12)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, item ->
                FlexChild(
                    order = index,
                    flexGrow = 1f,
                    flexShrink = 1f,
                    flexBasisPercent = 0.31f,
                    node = buildOutlineTextCell(
                        label = item.label,
                        strokeColor = item.strokeColor,
                        backgroundColor = item.backgroundColor
                    )
                )
            }
        )
    }

    fun buildOutlineFlexboxStatusCard(): LayoutNode {
        val items = listOf(
            OutlineStatusChipData("Idle", 0xFF607D8B.toInt(), false),
            OutlineStatusChipData("Measuring", 0xFF1976D2.toInt(), true),
            OutlineStatusChipData("Ready", 0xFF2E7D32.toInt(), false),
            OutlineStatusChipData("Dirty", 0xFFF9A825.toInt(), true),
            OutlineStatusChipData("Invalidated", 0xFFE53935.toInt(), true),
            OutlineStatusChipData("Cached", 0xFF5E35B1.toInt(), false),
            OutlineStatusChipData("Attached", 0xFF00897B.toInt(), false),
            OutlineStatusChipData("Detached", 0xFF6D4C41.toInt(), true),
            OutlineStatusChipData("Draw only", 0xFF3949AB.toInt(), false),
            OutlineStatusChipData("Remeasure", 0xFFC2185B.toInt(), true),
            OutlineStatusChipData("Static", 0xFF00796B.toInt(), false),
            OutlineStatusChipData("Dynamic", 0xFFF57C00.toInt(), true),
        )

        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.SPACE_BETWEEN,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            gap = dp(10),
            padding = EdgeInsets.all(dp(16)),
            layoutWidth = LayoutDimension.MatchParent,
            children = items.mapIndexed { index, item ->
                FlexChild(
                    order = index,
                    wrapBefore = item.label == "Attached",
                    node = buildOutlineTextChip(
                        label = item.label,
                        strokeColor = item.strokeColor,
                        backgroundColor = item.strokeColor.withAlpha(0x12),
                        textColor = item.strokeColor,
                        dashed = item.dashed
                    )
                )
            }
        )
    }

    fun buildOutlineFlexboxDenseCard(): LayoutNode {
        return FlexboxNode(
            flexDirection = FlexDirection.ROW,
            flexWrap = FlexWrap.WRAP,
            justifyContent = FlexJustifyContent.FLEX_START,
            alignItems = FlexAlignItems.CENTER,
            alignContent = FlexAlignContent.FLEX_START,
            mainGap = dp(6),
            crossGap = dp(6),
            padding = EdgeInsets.all(dp(12)),
            layoutWidth = LayoutDimension.MatchParent,
            children = List(96) { index ->
                val color = outlineColorAt(index)
                FlexChild(
                    order = index,
                    node = buildOutlineTextChip(
                        label = "N${index + 1}",
                        strokeColor = color,
                        backgroundColor = color.withAlpha(0x10),
                        textColor = 0xFF202124.toInt(),
                        horizontalPadding = dp(9),
                        verticalPadding = dp(5),
                        textSizePx = sp(11f),
                        dashed = index % 7 == 0
                    )
                )
            }
        )
    }

    fun buildScoreGaugeSpec(progress: Int, grade: String = "", label: String = "ĐIỂM", sizePx: Int, strokeWidthPx: Float = dp(1).toFloat()): LayoutNode = ConstraintNode(
        layoutWidth = LayoutDimension.Fixed(sizePx),
        layoutHeight = LayoutDimension.Fixed(sizePx),
        children = listOf(
            ConstraintChild(id = "arc", node = GaugeArcNode(progress = progress, progressColor = Color.BLACK, strokeWidthPx = strokeWidthPx), startToStartOf = ConstraintNode.PARENT, endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT, bottomToBottomOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
            ConstraintChild(id = "score", node = GaugeScoreNode(progress = progress, label = label, grade = grade, gradeColor = Color.BLACK), startToStartOf = ConstraintNode.PARENT, endToEndOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT, bottomToBottomOf = ConstraintNode.PARENT, width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
        )
    )

    private fun buildTagChip(label: String, color: Int): LayoutNode = ConstraintNode(
        children = listOf(
            ConstraintChild(id = "bg", node = OutlineNode(backgroundColor = color, strokeWidth = 0f, cornerRadius = dp(18).toFloat(), layoutWidth = LayoutDimension.MatchParent, layoutHeight = LayoutDimension.MatchParent), startToStartOf = "text", endToEndOf = "text", topToTopOf = "text", bottomToBottomOf = "text", width = LayoutDimension.MatchParent, height = LayoutDimension.MatchParent),
            ConstraintChild(id = "text", node = TextNode(text = BigText(label), textSizePx = sp(12f), color = Color.WHITE, typeface = Typeface.DEFAULT_BOLD, maxLines = 1, padding = EdgeInsets.symmetric(h = dp(10), v = dp(6))), startToStartOf = ConstraintNode.PARENT, topToTopOf = ConstraintNode.PARENT)
        )
    )

    private fun buildProgressTextChip(
        label: String,
        progress: Int,
        progressColor: Int,
        trackColor: Int = 0xFFE8EAED.toInt(),
        horizontalPadding: Int = dp(12),
        verticalPadding: Int = dp(7),
        textSizePx: Float = sp(12f)
    ): LayoutNode = ConstraintNode(
        children = listOf(
            ConstraintChild(
                id = "bg",
                node = ProgressBarNode(
                    progress = progress.coerceIn(0, 100),
                    max = 100,
                    trackColor = trackColor,
                    progressColor = progressColor,
                    cornerRadius = dp(18).toFloat(),
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
                    textSizePx = textSizePx,
                    color = 0xFF202124.toInt(),
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = horizontalPadding, v = verticalPadding)
                ),
                startToStartOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT
            )
        )
    )

    private fun buildProgressTextCell(
        label: String,
        progress: Int,
        progressColor: Int
    ): LayoutNode = ConstraintNode(
        layoutWidth = LayoutDimension.MatchParent,
        layoutHeight = LayoutDimension.Fixed(dp(40)),
        children = listOf(
            ConstraintChild(
                id = "bg",
                node = ProgressBarNode(
                    progress = progress.coerceIn(0, 100),
                    max = 100,
                    trackColor = 0xFFECEFF1.toInt(),
                    progressColor = progressColor,
                    cornerRadius = dp(10).toFloat(),
                    layoutWidth = LayoutDimension.MatchParent,
                    layoutHeight = LayoutDimension.MatchParent
                ),
                startToStartOf = ConstraintNode.PARENT,
                endToEndOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT,
                bottomToBottomOf = ConstraintNode.PARENT,
                width = LayoutDimension.MatchParent,
                height = LayoutDimension.MatchParent
            ),
            ConstraintChild(
                id = "text",
                node = TextNode(
                    text = BigText(label),
                    textSizePx = sp(12f),
                    color = 0xFF202124.toInt(),
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = dp(8), v = dp(7)),
                    layoutWidth = LayoutDimension.MatchParent
                ),
                startToStartOf = ConstraintNode.PARENT,
                endToEndOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT,
                bottomToBottomOf = ConstraintNode.PARENT,
                width = LayoutDimension.MatchParent
            )
        )
    )

    private fun buildOutlineTextChip(
        label: String,
        strokeColor: Int,
        backgroundColor: Int,
        textColor: Int,
        horizontalPadding: Int = dp(12),
        verticalPadding: Int = dp(7),
        textSizePx: Float = sp(12f),
        dashed: Boolean = false
    ): LayoutNode = ConstraintNode(
        children = listOf(
            ConstraintChild(
                id = "outline",
                node = OutlineNode(
                    backgroundColor = backgroundColor,
                    strokeColor = strokeColor,
                    strokeWidth = dp(1).toFloat(),
                    cornerRadius = dp(18).toFloat(),
                    dashWidth = if (dashed) dp(5).toFloat() else 0f,
                    dashGap = if (dashed) dp(4).toFloat() else 0f,
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
                    textSizePx = textSizePx,
                    color = textColor,
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = horizontalPadding, v = verticalPadding)
                ),
                startToStartOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT
            )
        )
    )

    private fun buildOutlineTextCell(
        label: String,
        strokeColor: Int,
        backgroundColor: Int
    ): LayoutNode = ConstraintNode(
        layoutWidth = LayoutDimension.MatchParent,
        layoutHeight = LayoutDimension.Fixed(dp(42)),
        children = listOf(
            ConstraintChild(
                id = "outline",
                node = OutlineNode(
                    backgroundColor = backgroundColor,
                    strokeColor = strokeColor,
                    strokeWidth = dp(1).toFloat(),
                    cornerRadius = dp(10).toFloat(),
                    layoutWidth = LayoutDimension.MatchParent,
                    layoutHeight = LayoutDimension.MatchParent
                ),
                startToStartOf = ConstraintNode.PARENT,
                endToEndOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT,
                bottomToBottomOf = ConstraintNode.PARENT,
                width = LayoutDimension.MatchParent,
                height = LayoutDimension.MatchParent
            ),
            ConstraintChild(
                id = "text",
                node = TextNode(
                    text = BigText(label),
                    textSizePx = sp(12f),
                    color = 0xFF202124.toInt(),
                    typeface = Typeface.DEFAULT_BOLD,
                    maxLines = 1,
                    padding = EdgeInsets.symmetric(h = dp(8), v = dp(8)),
                    layoutWidth = LayoutDimension.MatchParent
                ),
                startToStartOf = ConstraintNode.PARENT,
                endToEndOf = ConstraintNode.PARENT,
                topToTopOf = ConstraintNode.PARENT,
                bottomToBottomOf = ConstraintNode.PARENT,
                width = LayoutDimension.MatchParent
            )
        )
    )

    private fun colorByProgress(progress: Int): Int =
        when {
            progress >= 80 -> 0xFF43A047.toInt()
            progress >= 60 -> 0xFF1E88E5.toInt()
            progress >= 40 -> 0xFFFFB300.toInt()
            else -> 0xFFE53935.toInt()
        }

    private fun outlineColorAt(index: Int): Int {
        val colors = intArrayOf(
            0xFFE91E63.toInt(),
            0xFF2E7D32.toInt(),
            0xFF1565C0.toInt(),
            0xFF6D4C41.toInt(),
            0xFF5E35B1.toInt(),
            0xFF00897B.toInt(),
            0xFFF57C00.toInt(),
            0xFF455A64.toInt()
        )
        return colors[index % colors.size]
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Int): Int = (value * dp).toInt()

    private fun sp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, activity.resources.displayMetrics)

    private data class ProgressChipData(
        val label: String,
        val progress: Int,
        val color: Int
    )

    private data class OutlineChipData(
        val label: String,
        val strokeColor: Int,
        val backgroundColor: Int
    )

    private data class OutlineStatusChipData(
        val label: String,
        val strokeColor: Int,
        val dashed: Boolean
    )
}
