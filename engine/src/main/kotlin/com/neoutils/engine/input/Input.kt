package com.neoutils.engine.input

import com.neoutils.engine.math.Vec2

interface Input {

    val pointerPosition: Vec2

    fun isKeyDown(key: Key): Boolean

    fun wasKeyPressed(key: Key): Boolean

    fun isMouseDown(button: MouseButton): Boolean

    fun wasMouseClicked(button: MouseButton): Boolean
}
