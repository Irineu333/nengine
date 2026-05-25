package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Serializable
open class BoxCollider : Collider() {

    @Inspect
    var size: Vec2 = Vec2(10f, 10f)

    override fun bounds(): Rect {
        val world = world()
        val w = size.x * world.scale.x
        val h = size.y * world.scale.y
        if (world.rotation == 0f) return Rect(world.position, Vec2(w, h))

        val c = cos(world.rotation)
        val s = sin(world.rotation)
        val corners = arrayOf(
            Vec2(0f, 0f),
            Vec2(w, 0f),
            Vec2(0f, h),
            Vec2(w, h),
        )
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (corner in corners) {
            val rx = corner.x * c - corner.y * s + world.position.x
            val ry = corner.x * s + corner.y * c + world.position.y
            minX = min(minX, rx)
            minY = min(minY, ry)
            maxX = max(maxX, rx)
            maxY = max(maxY, ry)
        }
        return Rect(Vec2(minX, minY), Vec2(abs(maxX - minX), abs(maxY - minY)))
    }
}
