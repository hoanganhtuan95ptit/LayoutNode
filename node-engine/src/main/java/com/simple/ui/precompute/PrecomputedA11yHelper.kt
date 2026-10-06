package com.simple.ui.precompute

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

// ─────────────────────────────────────────────────────────────────────────────
// PrecomputedA11yHelper — expose mỗi node INTERACTIVE (click/long-click/touch)
// thành một "virtual view" cho hệ accessibility.
//
// Nhờ đó:
//   • TalkBack đọc được từng node (theo contentDescription) và kích hoạt click.
//   • Espresso / UiAutomator định vị & thao tác từng node trong UI test
//     (node vẽ bằng Canvas vốn không có view con để matcher nhìn thấy).
//
// Bounds mỗi virtual view lấy từ toạ độ tuyệt đối của spec ([DrawSpec.viewLeft]/
// [DrawSpec.viewTop]) nên chính xác theo vị trí thật trên màn hình.
// ─────────────────────────────────────────────────────────────────────────────

class PrecomputedA11yHelper(
    host: View,
    private val targetsProvider: () -> List<DrawSpec>
) : ExploreByTouchHelper(host) {

    private var targets: List<DrawSpec> = emptyList()

    private fun refresh(): List<DrawSpec> {

        val next = targetsProvider()
        targets = next
        return next
    }

    override fun getVirtualViewAt(x: Float, y: Float): Int {

        val list = refresh()
        // Duyệt ngược: node vẽ sau (topmost) được ưu tiên.
        for (i in list.indices.reversed()) {

            val s = list[i]
            if (x >= s.viewLeft && x < s.viewLeft + s.width &&
                y >= s.viewTop && y < s.viewTop + s.height
            ) {
                return i
            }
        }
        return HOST_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {

        val list = refresh()
        for (i in list.indices) virtualViewIds.add(i)
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat
    ) {

        val spec = targets.getOrNull(virtualViewId)
        if (spec == null) {

            node.contentDescription = ""
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            return
        }

        val layoutNode = spec.node
        node.contentDescription = layoutNode?.contentDescription ?: "Button"
        node.className = "android.widget.Button"
        node.isClickable = layoutNode?.onClick != null
        node.isLongClickable = layoutNode?.onLongClick != null
        if (layoutNode?.onClick != null) node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        if (layoutNode?.onLongClick != null) node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        node.setBoundsInParent(
            Rect(spec.viewLeft, spec.viewTop, spec.viewLeft + spec.width, spec.viewTop + spec.height)
        )
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {

        val layoutNode = targets.getOrNull(virtualViewId)?.node ?: return false
        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                layoutNode.onClick?.invoke(); layoutNode.onClick != null
            }
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                layoutNode.onLongClick?.invoke(); layoutNode.onLongClick != null
            }
            else -> false
        }
    }
}
