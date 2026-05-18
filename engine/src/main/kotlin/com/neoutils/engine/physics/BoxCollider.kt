package com.neoutils.engine.physics

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D

open class BoxCollider(var size: Vec2) : Collider() {

    override fun bounds(): Rect = Rect(worldPosition(), size)

    private fun worldPosition(): Vec2 {
        var p: Node? = this
        var accumX = 0f
        var accumY = 0f
        while (p != null) {
            if (p is Node2D) {
                accumX += p.transform.position.x
                accumY += p.transform.position.y
            }
            p = p.parent
        }
        return Vec2(accumX, accumY)
    }
}
