package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2

/**
 * Per-`SceneTree` layout coordinator for `ScreenDebugWidget`s. Each widget
 * declares a [DockSlot]; the dock stacks every enabled, non-empty widget of a
 * slot from its corner/center inward, separated by `DebugTheme.gutter` and
 * inset from the edges by `DebugTheme.margin`. The result is written back to
 * each widget's `dockOrigin`, which the widget draws from — no widget hardcodes
 * a corner.
 *
 * A widget the user has dragged carries a `customOrigin` override: the dock
 * leaves its position alone (only re-clamping it into the viewport) and does
 * **not** reserve slot space for it, so the remaining slot widgets re-flow as
 * if it were gone.
 *
 * [relayout] is called by `SceneTree.render` each frame, so the layout re-flows
 * automatically on `tree.resize` and as variable-height widgets (picker, log)
 * change size. Plain class, never a `Node`; owned by `DebugRegistry`.
 */
class DebugDock {

    // Registration order, mirroring DebugRegistry.register; widgets stack in
    // this order from the corner inward within their slot.
    private val widgets: MutableList<ScreenDebugWidget> = mutableListOf()

    internal fun add(widget: ScreenDebugWidget) {
        if (widget !in widgets) widgets += widget
    }

    internal fun remove(widget: ScreenDebugWidget) {
        widgets -= widget
    }

    /** Recompute every widget's `dockOrigin` for the current [surface] size. */
    fun relayout(surface: Vec2) {
        for (slot in DockSlot.values()) {
            val items = widgets
                // Dragged panels (customOrigin != null) own their position; skip
                // them here so the slot stacks the remaining widgets only.
                .filter { it.slot == slot && it.enabled && it.customOrigin == null }
                .mapNotNull { w ->
                    val size = w.contentSize()
                    if (size.x > 0f && size.y > 0f) w to size else null
                }
            if (items.isEmpty()) continue
            layoutSlot(slot, surface, items)
        }
        // Keep dragged panels inside the viewport after a resize.
        for (w in widgets) {
            if (w.enabled && w.customOrigin != null) w.reclampCustomOrigin(surface)
        }
    }

    private fun layoutSlot(
        slot: DockSlot,
        surface: Vec2,
        items: List<Pair<ScreenDebugWidget, Vec2>>,
    ) {
        val margin = DebugTheme.margin
        val gutter = DebugTheme.gutter
        if (slot.isTop) {
            var y = margin
            for ((widget, size) in items) {
                widget.dockOrigin = Vec2(slotX(slot, surface, size.x), y)
                y += size.y + gutter
            }
        } else {
            // Bottom slots grow upward: the first widget hugs the bottom edge.
            var bottom = surface.y - margin
            for ((widget, size) in items) {
                val y = bottom - size.y
                widget.dockOrigin = Vec2(slotX(slot, surface, size.x), y)
                bottom = y - gutter
            }
        }
    }

    private fun slotX(slot: DockSlot, surface: Vec2, width: Float): Float {
        val margin = DebugTheme.margin
        return when (slot) {
            DockSlot.TOP_LEFT, DockSlot.BOTTOM_LEFT -> margin
            DockSlot.TOP_RIGHT, DockSlot.BOTTOM_RIGHT -> maxOf(margin, surface.x - margin - width)
            DockSlot.TOP_CENTER, DockSlot.BOTTOM_CENTER -> maxOf(margin, (surface.x - width) / 2f)
        }
    }

    private val DockSlot.isTop: Boolean
        get() = this == DockSlot.TOP_LEFT || this == DockSlot.TOP_RIGHT || this == DockSlot.TOP_CENTER
}
