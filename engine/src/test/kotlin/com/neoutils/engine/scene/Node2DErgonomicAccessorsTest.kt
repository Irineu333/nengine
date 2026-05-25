package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class Node2DErgonomicAccessorsTest {

    @Test
    fun `position getter mirrors transform position`() {
        val node = Node2D().apply { transform = Transform(position = Vec2(10f, 20f)) }
        assertEquals(Vec2(10f, 20f), node.position)
    }

    @Test
    fun `rotation getter mirrors transform rotation`() {
        val node = Node2D().apply { transform = Transform(rotation = 1.5f) }
        assertEquals(1.5f, node.rotation)
    }

    @Test
    fun `scale getter mirrors transform scale`() {
        val node = Node2D().apply { transform = Transform(scale = Vec2(2f, 3f)) }
        assertEquals(Vec2(2f, 3f), node.scale)
    }

    @Test
    fun `position setter preserves scale and rotation`() {
        val node = Node2D().apply {
            transform = Transform(
                position = Vec2(10f, 20f),
                scale = Vec2(2f, 2f),
                rotation = PI.toFloat(),
            )
        }
        node.position = Vec2(50f, 60f)
        assertEquals(Vec2(50f, 60f), node.transform.position)
        assertEquals(Vec2(2f, 2f), node.transform.scale)
        assertEquals(PI.toFloat(), node.transform.rotation)
    }

    @Test
    fun `rotation setter preserves position and scale`() {
        val node = Node2D().apply {
            transform = Transform(position = Vec2(10f, 20f), scale = Vec2(2f, 2f))
        }
        node.rotation = 1.5f
        assertEquals(1.5f, node.transform.rotation)
        assertEquals(Vec2(10f, 20f), node.transform.position)
        assertEquals(Vec2(2f, 2f), node.transform.scale)
    }

    @Test
    fun `scale setter preserves position and rotation`() {
        val node = Node2D().apply {
            transform = Transform(position = Vec2(10f, 20f), rotation = 0.5f)
        }
        node.scale = Vec2(3f, 4f)
        assertEquals(Vec2(3f, 4f), node.transform.scale)
        assertEquals(Vec2(10f, 20f), node.transform.position)
        assertEquals(0.5f, node.transform.rotation)
    }

    @Test
    fun `writing through position accessor invalidates descendant cache`() {
        val root = Node()
        val parent = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        val child = Node2D().apply { transform = Transform(position = Vec2(5f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        assertEquals(Vec2(15f, 0f), child.world().position)
        parent.position = Vec2(99f, 99f)
        assertEquals(Vec2(104f, 99f), child.world().position)
    }

    @Test
    fun `writing through rotation accessor invalidates descendant cache`() {
        val root = Node()
        val parent = Node2D()
        val child = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        child.world()
        parent.rotation = (PI / 2.0).toFloat()
        val world = child.world()
        assertTrue(kotlin.math.abs(world.position.x - 0f) < 1e-3f)
        assertTrue(kotlin.math.abs(world.position.y - 10f) < 1e-3f)
    }

    @Test
    fun `writing through scale accessor invalidates descendant cache`() {
        val root = Node()
        val parent = Node2D()
        val child = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        child.world()
        parent.scale = Vec2(2f, 3f)
        assertEquals(Vec2(20f, 0f), child.world().position)
        assertEquals(Vec2(2f, 3f), child.world().scale)
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
