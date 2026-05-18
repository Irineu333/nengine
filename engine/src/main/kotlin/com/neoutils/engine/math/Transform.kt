package com.neoutils.engine.math

data class Transform(
    val position: Vec2 = Vec2.ZERO,
    val scale: Vec2 = Vec2.ONE,
    val rotation: Float = 0f,
)
