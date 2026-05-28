package com.neoutils.engine.scene

import com.neoutils.engine.dx.DEBUG_AREA_COLOR
import com.neoutils.engine.dx.DEBUG_BODY_COLOR
import com.neoutils.engine.dx.MomentumOverlay
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.collectActiveCollisionShapes
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import kotlinx.serialization.Serializable

/**
 * Engine-owned `CanvasLayer` auto-inserted into every `SceneTree` (via
 * `SceneTree.ensureDebugOverlay()`). Lives at `layer = Int.MAX_VALUE - 1` so
 * it always paints on top of any game UI. Its children read `tree.debug.*`
 * flags every draw and stay invisible (no draw calls emitted) while the
 * flags are off — keeping the cost of the auto-insertion negligible.
 *
 * Has a stable name `__debug` so tests and tooling can find it via
 * `tree.root.findChild("__debug")`.
 */
@Serializable
open class DebugOverlayLayer : CanvasLayer() {

    init {
        name = NODE_NAME
        layer = LAYER_INDEX
        // No script-instance attach point — engine-owned subtree.
        addChild(FpsLabel())
        addChild(ColliderOverlay())
        addChild(MomentumOverlayNode())
    }

    companion object {
        const val NODE_NAME = "__debug"

        /** Reserved layer index for the debug overlay (just below `Int.MAX_VALUE`). */
        const val LAYER_INDEX = Int.MAX_VALUE - 1
    }
}

/**
 * Top-left FPS counter, visible only while `tree.debug.showFps` is `true`.
 * Plain `Node` (not `Node2D`) so it does not contribute a transform push —
 * draws directly under the `CanvasLayer`'s identity transform.
 */
internal class FpsLabel : Node() {

    init { name = "FpsLabel" }

    override fun onDraw(renderer: Renderer) {
        val tree = this.tree ?: return
        if (!tree.debug.showFps) return
        renderer.drawText(
            text = "fps ${tree.debug.currentFps.toInt()}",
            position = Vec2(8f, 24f),
            size = 18f,
            color = Color.WHITE,
        )
    }
}

/**
 * Walks the world subtree each draw to outline every active
 * `CollisionShape2D`'s world AABB. Only visible while
 * `tree.debug.showColliders` is `true`. Pushes the current `Camera2D` view
 * transform locally so AABBs (which are world-space rects) align with the
 * projected scene.
 */
internal class ColliderOverlay : Node() {

    init { name = "ColliderOverlay" }

    override fun onDraw(renderer: Renderer) {
        val tree = this.tree ?: return
        if (!tree.debug.showColliders) return
        val view = tree.currentCamera()?.computeViewTransform(tree.size)
        if (view != null) {
            renderer.pushTransform(view.first, 0f, view.second)
            try {
                drawShapes(renderer)
            } finally {
                renderer.popTransform()
            }
        } else {
            drawShapes(renderer)
        }
    }

    private fun drawShapes(renderer: Renderer) {
        val tree = this.tree ?: return
        for ((shape, owner) in collectActiveCollisionShapes(tree)) {
            val bounds = shape.worldBounds() ?: continue
            val color = if (owner is Area2D) DEBUG_AREA_COLOR else DEBUG_BODY_COLOR
            renderer.drawRect(bounds, color, filled = false)
        }
    }
}

/**
 * Wraps the existing `MomentumOverlay` singleton (ring buffer + sparkline
 * renderer) as a scene node. The buffer is still fed by
 * `GameLoop.tick` (which calls `MomentumOverlay.recordSample` each physics
 * step when the flag is on); this node only drives the per-frame draw.
 */
internal class MomentumOverlayNode : Node() {

    init { name = "MomentumOverlay" }

    override fun onDraw(renderer: Renderer) {
        val tree = this.tree ?: return
        if (!tree.debug.showMomentum) return
        MomentumOverlay.renderOverlay(renderer, tree.size.x, tree.size.y)
    }
}

