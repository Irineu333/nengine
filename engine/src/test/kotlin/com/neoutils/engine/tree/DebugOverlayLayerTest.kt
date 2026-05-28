package com.neoutils.engine.tree

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.CollisionShape2D
import com.neoutils.engine.physics.RectangleShape2D
import com.neoutils.engine.render.RecordedEvent
import com.neoutils.engine.render.RecordingRenderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.DebugOverlayLayer
import com.neoutils.engine.scene.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebugOverlayLayerTest {

    @Test
    fun `every started SceneTree carries an auto-inserted DebugOverlayLayer`() {
        val tree = SceneTree(Node())
        tree.start()
        val overlay = tree.root.findChild(DebugOverlayLayer.NODE_NAME)
        assertNotNull(overlay, "expected __debug child auto-inserted after start()")
        assertTrue(overlay is DebugOverlayLayer)
    }

    @Test
    fun `auto-insert is idempotent across re-start`() {
        val root = Node()
        val tree = SceneTree(root)
        tree.start()
        val first = tree.root.findChild(DebugOverlayLayer.NODE_NAME)
        assertNotNull(first)
        tree.stop()
        // The detached subtree (with __debug still attached) is reused; starting
        // again should not produce a second overlay layer.
        tree.start()
        val matches = tree.root.children.count { it.name == DebugOverlayLayer.NODE_NAME }
        assertEquals(1, matches)
    }

    @Test
    fun `default debug flags are all false`() {
        val tree = SceneTree(Node())
        assertEquals(false, tree.debug.showFps)
        assertEquals(false, tree.debug.showColliders)
        assertEquals(false, tree.debug.showMomentum)
    }

    @Test
    fun `with all flags false the overlay emits zero draw calls`() {
        val tree = SceneTree(Node())
        tree.start()
        val recorder = RecordingRenderer()
        tree.render(recorder)
        // No world-pass nodes; the only thing in the UI pass is the
        // auto-inserted DebugOverlayLayer with all flags off → no draws.
        val draws = recorder.events.count {
            it is RecordedEvent.Text || it is RecordedEvent.Rect || it is RecordedEvent.Line
        }
        assertEquals(0, draws)
    }

    @Test
    fun `enabling showFps produces an FPS text`() {
        val tree = SceneTree(Node())
        tree.start()
        tree.debug.showFps = true
        tree.debug.currentFps = 60f

        val recorder = RecordingRenderer()
        tree.render(recorder)

        val text = recorder.events.filterIsInstance<RecordedEvent.Text>().single()
        assertTrue(text.text.startsWith("fps "))
        assertTrue(text.text.contains("60"))
    }

    @Test
    fun `enabling showColliders draws Area2D bounds in green`() {
        val area = Area2D().apply { name = "Trigger" }
        val shape = CollisionShape2D().apply { this.shape = RectangleShape2D().apply { size = Vec2(20f, 20f) } }
        area.addChild(shape)
        area.transform = Transform(position = Vec2(50f, 50f))
        val root = Node()
        root.addChild(area)
        val tree = SceneTree(root); tree.start()
        tree.debug.showColliders = true

        val recorder = RecordingRenderer()
        tree.render(recorder)
        val outlineRects = recorder.events.filterIsInstance<RecordedEvent.Rect>().filter { !it.filled }
        assertTrue(outlineRects.isNotEmpty(), "expected at least one outline rect")
        // Area2D should use the green debug color (g > 0.9).
        assertTrue(outlineRects.any { it.color.g > 0.9f })
    }

    @Test
    fun `ColliderOverlay ignores Button (not a CollisionObject)`() {
        val root = Node()
        val canvas = CanvasLayer()
        canvas.addChild(Button().apply {
            transform = Transform(position = Vec2(100f, 100f))
            size = Vec2(80f, 40f)
        })
        root.addChild(canvas)
        val tree = SceneTree(root); tree.start()
        tree.debug.showColliders = true

        val recorder = RecordingRenderer()
        tree.render(recorder)

        // Button does not draw with `filled=false`; only the filled fill is
        // drawn each frame. ColliderOverlay sees no CollisionObject so emits
        // no outline rects.
        val outlines = recorder.events.filterIsInstance<RecordedEvent.Rect>().filter { !it.filled }
        assertEquals(0, outlines.size)
    }

    @Test
    fun `ColliderOverlay applies Camera2D view transform locally`() {
        // ColliderOverlay must push the camera view transform before drawing
        // world-space AABBs so they line up on screen. Validate by checking a
        // push appears before the outline draw, even though we are inside the
        // UI pass (which starts at identity).
        val root = Node()
        val camera = com.neoutils.engine.scene.Camera2D().apply {
            bounds = Rect(Vec2.ZERO, Vec2(400f, 300f))
            current = true
        }
        root.addChild(camera)

        val area = Area2D().apply { transform = Transform(position = Vec2(10f, 10f)) }
        area.addChild(CollisionShape2D().apply { shape = RectangleShape2D().apply { size = Vec2(10f, 10f) } })
        root.addChild(area)

        val tree = SceneTree(root)
        tree.resize(800f, 600f)
        tree.start()
        tree.debug.showColliders = true

        val recorder = RecordingRenderer()
        tree.render(recorder)
        // World pass: camera view push, area push, area pop, camera view pop
        // UI pass: __debug layer + ColliderOverlay walks; ColliderOverlay
        // itself pushes the camera view again before drawing outlines, then pops.
        val outlineIdx = recorder.events.indexOfFirst { it is RecordedEvent.Rect && !(it as RecordedEvent.Rect).filled }
        assertTrue(outlineIdx > 0, "outline missing")
        // The push immediately before the outline must carry the view scale (≠ 1) since the
        // surface is 800x600 and bounds are 400x300 → 2x scale.
        val precedingPush = (outlineIdx - 1 downTo 0)
            .mapNotNull { recorder.events[it] as? RecordedEvent.Push }
            .firstOrNull()
        assertNotNull(precedingPush)
        assertEquals(Vec2(2f, 2f), precedingPush.scale)
    }
}
