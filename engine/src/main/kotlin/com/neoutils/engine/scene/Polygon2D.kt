package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Filled polygon defined by vertices in local space. Each vertex is offset by
 * `world().position` before submitting to `Renderer.drawPolygon`. Concavity
 * is allowed; self-intersection is undefined.
 */
@Serializable
open class Polygon2D : Node2D() {

    @Inspect
    var points: List<Vec2> = emptyList()

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        if (points.size >= 3) {
            val origin = world().position
            val translated = points.map { Vec2(origin.x + it.x, origin.y + it.y) }
            renderer.drawPolygon(translated, color)
        }
        super.onDraw(renderer)
    }
}
