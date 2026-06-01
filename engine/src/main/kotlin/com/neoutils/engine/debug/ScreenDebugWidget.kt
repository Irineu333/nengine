package com.neoutils.engine.debug

import com.neoutils.engine.input.MouseButton
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
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
 * [origin] (the dock-assigned [dockOrigin], or the drag [customOrigin] when the
 * panel has been moved). Subclasses never hardcode a screen corner.
 *
 * The base also makes every screen panel **draggable**: pressing the panel's
 * chrome (everything but its interactive `Button` controls) starts a drag that
 * follows the pointer until released, writing a session-only [customOrigin] that
 * overrides the dock slot (see [updateDrag]).
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
     * Screen-pixel top-left assigned by the `DebugDock` each render from [slot]
     * and [contentSize]. Used only while the panel has no drag [customOrigin];
     * read [origin] (not this) when drawing. Defaults to the origin until the
     * first relayout.
     */
    var dockOrigin: Vec2 = Vec2.ZERO
        internal set

    /**
     * Session-only position override set by dragging the panel. `null` means
     * "follow the dock slot"; once set, it wins over [dockOrigin]. Survives the
     * widget's enable/disable toggle and `tree.resize` (re-clamped into the
     * viewport by the dock via [reclampCustomOrigin]); never persisted to disk.
     * Cleared by [resetPosition].
     */
    var customOrigin: Vec2? = null
        private set

    /**
     * Screen-pixel top-left the widget actually draws from: the drag
     * [customOrigin] when present, otherwise the dock-assigned [dockOrigin].
     * Subclasses draw from this, not from [dockOrigin].
     */
    val origin: Vec2 get() = customOrigin ?: dockOrigin

    /**
     * Size in screen pixels the widget currently occupies, measured from its
     * content; `(0, 0)` when there is nothing to show. The dock stacks and
     * aligns widgets by this and skips zero-size ones. Widgets of variable
     * height recompute it from current state, so the dock re-flows as they grow.
     */
    open fun contentSize(): Vec2 = Vec2.ZERO

    private var dragging: Boolean = false
    private var grabOffset: Vec2 = Vec2.ZERO

    final override fun onDraw(renderer: Renderer) {
        if (enabled) drawDebug(renderer)
    }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        updateDrag()
    }

    /** Clears the drag override so the panel flows back to its dock slot. */
    fun resetPosition() {
        customOrigin = null
        dragging = false
    }

    /**
     * Re-clamp the drag override into [surface], called by the `DebugDock` each
     * relayout so a panel left off-screen by a shrunk window stays visible.
     * No-op when there is no override or the panel currently shows nothing.
     */
    internal fun reclampCustomOrigin(surface: Vec2) {
        val current = customOrigin ?: return
        val size = contentSize()
        if (size.x <= 0f || size.y <= 0f) return
        customOrigin = clampToSurface(current, size, surface)
    }

    /**
     * Polling drag, mirroring the engine's other debug nodes: press the panel's
     * grab zone to begin (capturing [grabOffset]), follow the pointer while the
     * button is held, release to end. While dragging, the panel owns the drag —
     * it flags [com.neoutils.engine.input.Input.mouseDragConsumed] so gameplay
     * pan/drag consumers stand down.
     */
    private fun updateDrag() {
        if (!enabled) {
            dragging = false
            return
        }
        val input = tree?.input ?: return
        val surface = tree?.size ?: return
        val size = contentSize()
        if (size.x <= 0f || size.y <= 0f) {
            dragging = false
            return
        }
        val down = input.isMouseDown(MouseButton.Left)
        if (dragging) {
            if (!down) {
                dragging = false
                return
            }
            customOrigin = clampToSurface(input.pointerPosition - grabOffset, size, surface)
            input.mouseDragConsumed = true
            return
        }
        // Begin only on the press edge inside the grab zone.
        if (down && input.wasMouseClickedRaw(MouseButton.Left) && inGrabZone(input.pointerPosition, size)) {
            dragging = true
            grabOffset = input.pointerPosition - origin
            input.mouseDragConsumed = true
        }
    }

    /**
     * The grab zone is the panel rect minus the rects of its interactive
     * `Button` descendants: pressing empty chrome starts a drag, while pressing
     * a control (HUD rows, time steppers) routes the click to that control as
     * usual — so panels with buttons stay both draggable and clickable.
     */
    private fun inGrabZone(pointer: Vec2, size: Vec2): Boolean {
        if (!Rect(origin, size).contains(pointer)) return false
        return !overInteractiveChild(this, pointer)
    }

    private fun overInteractiveChild(node: Node, pointer: Vec2): Boolean {
        for (child in node.children) {
            if (child is Button && !child.disabled && child.screenRect().contains(pointer)) return true
            if (overInteractiveChild(child, pointer)) return true
        }
        return false
    }

    private fun clampToSurface(pos: Vec2, size: Vec2, surface: Vec2): Vec2 {
        val maxX = (surface.x - size.x).coerceAtLeast(0f)
        val maxY = (surface.y - size.y).coerceAtLeast(0f)
        return Vec2(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
    }
}
