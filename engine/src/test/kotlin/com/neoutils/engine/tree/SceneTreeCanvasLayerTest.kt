package com.neoutils.engine.tree

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.AspectMode
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneTreeCanvasLayerTest {

    @Test
    fun `world pass skips CanvasLayer subtree`() {
        val root = Node2D()
        val worldRect = ColorRect().apply {
            transform = Transform(position = Vec2(1f, 1f))
            size = Vec2(5f, 5f)
        }
        val layer = CanvasLayer()
        val uiRect = ColorRect().apply {
            transform = Transform(position = Vec2(100f, 100f))
            size = Vec2(20f, 20f)
        }
        layer.addChild(uiRect)
        root.addChild(worldRect)
        root.addChild(layer)

        val tree = SceneTree(root)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // World pass draws worldRect; UI pass draws uiRect.
        val rects = recorder.events.filterIsInstance<RecordedEvent.Rect>()
        assertEquals(2, rects.size)
        assertEquals(Vec2(5f, 5f), rects[0].rect.size)
        assertEquals(Vec2(20f, 20f), rects[1].rect.size)
    }

    @Test
    fun `UI pass starts from identity transform regardless of Camera2D`() {
        val root = Node()
        val camera = Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f)) // half of surface
            current = true
            aspectMode = AspectMode.FIT
        }
        root.addChild(camera)

        val layer = CanvasLayer()
        val uiRect = ColorRect().apply {
            transform = Transform(position = Vec2(50f, 50f))
            size = Vec2(10f, 10f)
        }
        layer.addChild(uiRect)
        root.addChild(layer)

        val tree = SceneTree(root)
        tree.resize(800f, 600f) // camera bounds 400x300 → view scale 2x
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Two passes: world (view push, camera push/pop, view pop) then UI (uiRect push, draw, pop).
        // Find the push immediately preceding the rect draw and assert it is the UI push (no view scale).
        val rectIndex = recorder.events.indexOfFirst { it is RecordedEvent.Rect }
        assertTrue(rectIndex >= 0)
        val precedingPush = (rectIndex - 1 downTo 0)
            .mapNotNull { recorder.events[it] as? RecordedEvent.Push }
            .first()
        assertEquals(Vec2(50f, 50f), precedingPush.translation)
        assertEquals(Vec2(1f, 1f), precedingPush.scale, "UI pass must start from identity (scale 1), not camera scale 2")
    }

    @Test
    fun `CanvasLayers render in layer order with dfs tie-break`() {
        val root = Node()
        val a = CanvasLayer().apply { name = "A"; layer = 0 }
        val aPanel = MarkerNode("aPanel")
        a.addChild(aPanel)
        val b = CanvasLayer().apply { name = "B"; layer = 10 }
        val bPanel = MarkerNode("bPanel")
        b.addChild(bPanel)
        val c = CanvasLayer().apply { name = "C"; layer = 0 }
        val cPanel = MarkerNode("cPanel")
        c.addChild(cPanel)
        // DFS discovery: A, B, C.
        root.addChild(a)
        root.addChild(b)
        root.addChild(c)

        val tree = SceneTree(root)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Expected ordering by (layer asc, dfs-order asc): A (0,0), C (0,2), B (10,1).
        val markerOrder = recorder.events
            .filterIsInstance<RecordedEvent.Text>()
            .map { it.text }
        assertEquals(listOf("aPanel", "cPanel", "bPanel"), markerOrder)
    }

    @Test
    fun `nested CanvasLayer inside another layer still renders once in global order`() {
        val root = Node()
        val outer = CanvasLayer().apply { name = "outer"; layer = 0 }
        val outerMark = MarkerNode("outer")
        outer.addChild(outerMark)
        val inner = CanvasLayer().apply { name = "inner"; layer = 5 }
        val innerMark = MarkerNode("inner")
        inner.addChild(innerMark)
        outer.addChild(inner)
        root.addChild(outer)

        val tree = SceneTree(root)
        tree.start()

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val order = recorder.events
            .filterIsInstance<RecordedEvent.Text>()
            .map { it.text }
        // outer first (layer 0), inner second (layer 5); both rendered once.
        assertEquals(listOf("outer", "inner"), order)
    }
}

/**
 * Helper Node2D that emits a single drawText with its name as the text. Lets
 * tests recover the rendering order across CanvasLayers without coupling to
 * push/pop noise.
 */
private class MarkerNode(label: String) : Node2D() {
    init { name = label }
    override fun onDraw(renderer: Renderer) {
        renderer.drawText(name, Vec2.ZERO, 10f, com.neoutils.engine.render.Color.WHITE)
    }
}
