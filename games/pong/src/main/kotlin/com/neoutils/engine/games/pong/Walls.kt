package com.neoutils.engine.games.pong

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.physics.Collider

/** Horizontal boundary at the top or bottom of the field. */
class Wall(size: Vec2) : BoxCollider(size)

/** Vertical boundary that triggers a score for the opposite side. */
class Goal(val side: Side, size: Vec2) : BoxCollider(size) {
    enum class Side { Left, Right }
}

/** Tagged collider attached to a paddle so the ball can detect paddle hits. */
class PaddleCollider(size: Vec2) : BoxCollider(size) {

    var hits: Int = 0
        private set

    override fun onCollide(other: Collider) {
        // The Ball owns reflection; we just record the hit for stats/debug.
        hits++
    }
}
