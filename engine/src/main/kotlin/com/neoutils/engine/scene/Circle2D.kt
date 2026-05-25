package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled circle. Center is `world().position + (radius, radius)` so the
 * bounding box left edge sits on the node's world position; the rendered
 * radius scales with the world transform's `scale.x`.
 */
@Serializable
open class Circle2D : Node2D() {

    @Inspect
    var radius: Float = 5f

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        val world = world()
        val effective = radius * world.scale.x
        renderer.drawCircle(
            center = Vec2(world.position.x + radius, world.position.y + radius),
            radius = effective,
            color = color,
            filled = true,
        )
        super.onDraw(renderer)
    }
}
