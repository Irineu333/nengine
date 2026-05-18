package com.neoutils.engine.math

data class Rect(val origin: Vec2, val size: Vec2) {

    val left: Float get() = origin.x
    val top: Float get() = origin.y
    val right: Float get() = origin.x + size.x
    val bottom: Float get() = origin.y + size.y

    fun intersects(other: Rect): Boolean =
        left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top

    fun contains(point: Vec2): Boolean =
        point.x >= left && point.x < right &&
            point.y >= top && point.y < bottom
}
