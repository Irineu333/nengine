package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node

/**
 * Base for debug widgets that render in screen pixels (no `Camera2D` view
 * transform). Lives under the `ScreenDebugCanvas` (`CanvasLayer`) child of
 * the auto-inserted `DebugLayer`. The final `onDraw` gates `drawDebug` on
 * [enabled], so subclasses focus on the visualization without re-checking
 * the flag.
 *
 * Positioning is the `DebugDock`'s job, not the widget's: the widget declares
 * a [slot], reports the size it occupies via [contentSize], and draws from
 * [dockOrigin] (assigned by the dock each frame). Subclasses never hardcode a
 * screen corner.
 */
abstract class ScreenDebugWidget : Node(), DebugWidget {

    override var enabled: Boolean = false

    /**
     * Corner/center this widget docks to. The `DebugDock` stacks every enabled
     * widget sharing a slot, so the default is safe even when unset; widgets
     * with a deliberate placement override it.
     */
    open val slot: DockSlot = DockSlot.TOP_LEFT

    /**
     * Screen-pixel top-left where this widget should draw, assigned by the
     * `DebugDock` each render from [slot] and [contentSize]. Defaults to the
     * origin until the first relayout.
     */
    var dockOrigin: Vec2 = Vec2.ZERO
        internal set

    /**
     * Size in screen pixels the widget currently occupies, measured from its
     * content; `(0, 0)` when there is nothing to show. The dock stacks and
     * aligns widgets by this and skips zero-size ones. Widgets of variable
     * height recompute it from current state, so the dock re-flows as they grow.
     */
    open fun contentSize(): Vec2 = Vec2.ZERO

    final override fun onDraw(renderer: Renderer) {
        if (enabled) drawDebug(renderer)
    }
}
