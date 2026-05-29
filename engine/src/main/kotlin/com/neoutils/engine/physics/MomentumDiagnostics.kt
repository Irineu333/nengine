package com.neoutils.engine.physics

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.tree.SceneTree

/**
 * Sum of `m · v` over every live, non-disabled [RigidBody2D] in the tree.
 * Returns [Vec2.ZERO] when the tree has no rigid bodies. Pre-order walk;
 * O(N) in tree size. Use to verify linear momentum conservation through
 * elastic + inelastic collisions (it should stay constant in the absence
 * of external forces / gravity).
 */
fun SceneTree.totalLinearMomentum(): Vec2 {
    var sumX = 0f
    var sumY = 0f
    forEachRigid(root) { r ->
        sumX += r.mass * r.linearVelocity.x
        sumY += r.mass * r.linearVelocity.y
    }
    return Vec2(sumX, sumY)
}

/**
 * Sum of `I · ω + m · (x · vy − y · vx)` over every live, non-disabled
 * [RigidBody2D] (the second term is the orbital angular momentum about the
 * world origin). Returns `0f` when the tree has no rigid bodies.
 */
fun SceneTree.totalAngularMomentum(): Float {
    var sum = 0f
    forEachRigid(root) { r ->
        sum += r.effectiveInertia * r.angularVelocity +
            r.mass * (r.position.x * r.linearVelocity.y - r.position.y * r.linearVelocity.x)
    }
    return sum
}

/**
 * Sum of `0.5 · m · |v|² + 0.5 · I · ω²` over every live, non-disabled
 * [RigidBody2D]. Returns `0f` when the tree has no rigid bodies. Use as a
 * sanity check: elastic collisions conserve this; inelastic collisions
 * dissipate it monotonically.
 */
fun SceneTree.totalKineticEnergy(): Float {
    var sum = 0f
    forEachRigid(root) { r ->
        val vSq = r.linearVelocity.x * r.linearVelocity.x + r.linearVelocity.y * r.linearVelocity.y
        sum += 0.5f * r.mass * vSq + 0.5f * r.effectiveInertia * r.angularVelocity * r.angularVelocity
    }
    return sum
}

private fun forEachRigid(node: Node, block: (RigidBody2D) -> Unit) {
    if (!node.isLive) return
    if (node is RigidBody2D && !node.disabled) block(node)
    for (child in node.children) forEachRigid(child, block)
}

/**
 * Visits every live, non-disabled body that carries a linear velocity —
 * [RigidBody2D] (via `linearVelocity`) and [CharacterBody2D] (via `velocity`)
 * — handing the block an **anchor** point and that velocity. Used by the debug
 * velocity gizmo, which draws an arrow per moving body.
 *
 * The anchor is the world-space centroid of the body's active collision shapes
 * (not the raw node origin): linear velocity is identical at every point of a
 * body, so the anchor is purely cosmetic, and centering it on the shapes keeps
 * the arrow on the visible body even when the node origin sits off-center (e.g.
 * a circle whose `CollisionShape2D` carries a local offset). Falls back to the
 * node's world position when the body has no active shapes.
 */
fun SceneTree.forEachBodyVelocity(block: (anchor: Vec2, velocity: Vec2) -> Unit) {
    forEachMovingBody(root, block)
}

private fun forEachMovingBody(node: Node, block: (Vec2, Vec2) -> Unit) {
    if (!node.isLive) return
    when (node) {
        is RigidBody2D -> if (!node.disabled) block(bodyAnchor(node), node.linearVelocity)
        is CharacterBody2D -> if (!node.disabled) block(bodyAnchor(node), node.velocity)
    }
    for (child in node.children) forEachMovingBody(child, block)
}

private fun bodyAnchor(body: CollisionObject2D): Vec2 {
    val shapes = body.collectActiveShapes()
    if (shapes.isEmpty()) return body.world().position
    var sumX = 0f
    var sumY = 0f
    for ((_, bounds) in shapes) {
        sumX += bounds.origin.x + bounds.size.x / 2f
        sumY += bounds.origin.y + bounds.size.y / 2f
    }
    return Vec2(sumX / shapes.size, sumY / shapes.size)
}
