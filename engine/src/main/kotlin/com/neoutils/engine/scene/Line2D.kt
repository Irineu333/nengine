package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Polyline drawn by chaining consecutive points with `Renderer.drawLine`.
 * Each point is interpreted as an offset from `world().position`.
 */
@Serializable
open class Line2D : Node2D() {

    @Inspect
    var points: List<Vec2> = emptyList()

    @Inspect
    var thickness: Float = 1f

    @Inspect
    var color: Color = Color.WHITE

    override fun onDraw(renderer: Renderer) {
        if (points.size >= 2) {
            val origin = world().position
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                renderer.drawLine(
                    from = Vec2(origin.x + a.x, origin.y + a.y),
                    to = Vec2(origin.x + b.x, origin.y + b.y),
                    thickness = thickness,
                    color = color,
                )
            }
        }
        super.onDraw(renderer)
    }
}
