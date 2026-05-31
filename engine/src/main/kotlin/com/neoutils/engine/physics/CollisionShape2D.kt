package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.Inspect
import kotlinx.serialization.Serializable

/**
 * Node child of a [CollisionObject2D] that carries a polymorphic [Shape2D]
 * resource. Inactive when [shape] is `null` or [disabled] is `true`; in that
 * case [broadPhaseBounds] and [localBounds] return `null` and [PhysicsSystem]
 * ignores it.
 *
 * Only meaningful as a direct child of a [CollisionObject2D]; placing it
 * elsewhere does not crash but is simply not enumerated by the physics step.
 */
@Serializable
open class CollisionShape2D : Node2D() {

    @Inspect
    var shape: Shape2D? = null

    @Inspect
    var disabled: Boolean = false

    /**
     * World-space AABB the physics broad-phase consumes, via
     * [Shape2D.bounds]. `null` when [disabled] or [shape] is `null`. Both
     * [RectangleShape2D] and [CircleShape2D] are centered on their local
     * origin, so this AABB agrees with the inherited [worldBounds] (which
     * projects the centered [localBounds] through `world()`) for the same
     * shape — the two are kept in sync by construction.
     */
    fun broadPhaseBounds(): Rect? {
        if (disabled) return null
        val s = shape ?: return null
        return s.bounds(world(), Vec2.ZERO)
    }

    /**
     * Centered local extent of the carried [shape] (see [Shape2D.localBounds]),
     * or `null` when [disabled] or [shape] is `null`. Bridges collision objects
     * into the polymorphic bounds contract: a `CollisionObject2D`'s
     * [worldBounds]/[treeBounds] fall out of `Node2D` recursion with no extra
     * method on the object itself.
     */
    override fun localBounds(): Rect? {
        if (disabled) return null
        return shape?.localBounds()
    }
}
