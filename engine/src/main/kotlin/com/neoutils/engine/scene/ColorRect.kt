package com.neoutils.engine.scene

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Axis-aligned filled rectangle anchored at the node's world position.
 * `size` is composed with `world().scale`; rotation is part of the
 * composed transform but is not applied to the drawing yet (same limitation
 * as the legacy `Shape`; the visual rotation will land when `Renderer`
 * grows a `withTransform` helper).
 */
@Serializable
open class ColorRect : Node2D() {

    @Inspect
    var size: Vec2 = Vec2(10f, 10f)

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        val world = world()
        val w = size.x * world.scale.x
        val h = size.y * world.scale.y
        renderer.drawRect(Rect(world.position, Vec2(w, h)), color, filled = true)
        super.onDraw(renderer)
    }
}
