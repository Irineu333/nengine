package com.neoutils.engine.games.demos

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Shape
import kotlin.math.sin

/**
 * A parent `Node2D` with a scale that oscillates between MIN_SCALE and
 * MAX_SCALE. The child `Shape` keeps a fixed local size — the rendered size
 * grows and shrinks because `Shape.onRender` reads `worldTransform().scale`,
 * the post-change behavior. This would have stayed visually static before
 * the change since the old code only honored the child's own scale.
 */
class ScaleHierarchyDemo : Node2D() {

    private val pivot = object : Node2D() {
        private var t: Float = 0f
        override fun onUpdate(dt: Float) {
            t += dt
            val s = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * (0.5f + 0.5f * sin(t * SPEED))
            transform = transform.copy(scale = Vec2(s, s))
        }
    }.apply {
        name = "ScaleParent"
        transform = Transform(position = Vec2(400f, 300f))
    }

    private val child = Shape(
        kind = Shape.Kind.Rect,
        size = Vec2(80f, 80f),
        color = Color(0.6f, 0.85f, 0.3f),
    ).apply {
        transform = Transform(position = Vec2(-40f, -40f))
        name = "ScaleChild"
    }

    private val reference = Shape(
        kind = Shape.Kind.Rect,
        size = Vec2(80f, 80f),
        color = Color(1f, 1f, 1f, 0.15f),
        filled = false,
    ).apply {
        transform = Transform(position = Vec2(360f, 260f))
        name = "ScaleReference"
    }

    init {
        name = "ScaleHierarchyDemo"
        addChild(reference)
        addChild(pivot)
        pivot.addChild(child)
    }

    companion object {
        private const val MIN_SCALE: Float = 0.5f
        private const val MAX_SCALE: Float = 2.0f
        private const val SPEED: Float = 1.5f
    }
}
