package com.neoutils.engine.scene

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.math.Rect
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals

private val testRenderer: Renderer = object : Renderer {
    override fun clear(color: Color) {}
    override fun drawRect(rect: Rect, color: Color, filled: Boolean) {}
    override fun drawCircle(center: Vec2, radius: Float, color: Color, filled: Boolean, thickness: Float) {}
    override fun drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color) {}
    override fun drawText(text: String, position: Vec2, size: Float, color: Color) {}
    override fun measureText(text: String, size: Float): Vec2 = Vec2.ZERO
}

class NodeScriptInstanceTest {

    private class RecordingContract : ScriptInstanceContract {
        val calls = mutableListOf<String>()
        override fun onEnter() { calls += "onEnter" }
        override fun onUpdate(dt: Float) { calls += "onUpdate($dt)" }
        override fun onRender(renderer: Renderer) { calls += "onRender" }
        override fun onCollide(other: Node) { calls += "onCollide" }
    }

    @Test
    fun `onUpdate dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onUpdate(0.016f)
        assertEquals(listOf("onUpdate(0.016)"), contract.calls)
    }

    @Test
    fun `onEnter dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onEnter()
        assertEquals(listOf("onEnter"), contract.calls)
    }

    @Test
    fun `onRender dispatches to scriptInstance`() {
        val node = Node2D()
        val contract = RecordingContract()
        node.scriptInstance = contract
        node.onRender(testRenderer)
        assertEquals(listOf("onRender"), contract.calls)
    }

    @Test
    fun `node without scriptInstance behaves like before`() {
        val node = Node2D()
        // no exception, no dispatch
        node.onUpdate(0.016f)
        node.onEnter()
    }
}
