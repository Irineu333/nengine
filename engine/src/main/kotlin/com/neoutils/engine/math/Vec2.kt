package com.neoutils.engine.math

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)
    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    val length: Float get() = sqrt(x * x + y * y)

    val normalized: Vec2
        get() {
            val l = length
            return if (l == 0f) ZERO else Vec2(x / l, y / l)
        }

    companion object {
        val ZERO: Vec2 = Vec2(0f, 0f)
        val ONE: Vec2 = Vec2(1f, 1f)
    }
}
