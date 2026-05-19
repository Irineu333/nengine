package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.scene.Node2D
import kotlinx.serialization.Serializable

@Serializable
abstract class Collider : Node2D() {

    abstract fun bounds(): Rect

    open fun onCollide(other: Collider) {}
}
