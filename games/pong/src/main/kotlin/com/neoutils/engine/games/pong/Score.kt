package com.neoutils.engine.games.pong

import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Text

class Score(
    val textSize: Float = 48f,
    val color: Color = Color.WHITE,
) : Node2D() {

    var value: Int = 0
        private set

    private val label: Text = Text(text = "0", size = textSize, color = color)

    fun increment() {
        value++
        label.text = value.toString()
    }

    override fun onRender(renderer: Renderer) {
        // Render the label directly so it inherits this node's world position
        // without exposing the internal Text child as part of the public API.
        renderer.drawText(label.text, transform.position, textSize, color)
    }
}
