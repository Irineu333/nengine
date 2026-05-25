package com.neoutils.engine.scene

import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorldTransformCacheTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    // 4.2: Two consecutive reads without mutation return equal Transforms
    @Test
    fun `consecutive world calls return equal result`() {
        val root = Node()
        val parent = Node2D().apply { transform = Transform(position = Vec2(10f, 20f)) }
        val child = Node2D().apply { transform = Transform(position = Vec2(3f, 4f)) }
        root.addChild(parent)
        parent.addChild(child)
        val first = child.world()
        val second = child.world()
        assertEquals(first.position, second.position)
        assertEquals(first.scale, second.scale)
        assertEquals(first.rotation, second.rotation)
    }

    // 4.3: After parent.transform = ..., parent and child reflect new position
    @Test
    fun `assigning parent transform invalidates parent and child cache`() {
        val root = Node()
        val parent = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        val child = Node2D().apply { transform = Transform(position = Vec2(5f, 0f)) }
        root.addChild(parent)
        parent.addChild(child)
        parent.world()
        child.world()
        parent.transform = parent.transform.copy(position = Vec2(50f, 50f))
        assertEquals(Vec2(50f, 50f), parent.world().position)
        assertEquals(Vec2(55f, 50f), child.world().position)
    }

    // 4.4: grandparent (Node2D) → middle (raw Node) → grandchild (Node2D)
    @Test
    fun `invalidation propagates through non-Node2D intermediates`() {
        val root = Node()
        val grandparent = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        val middle = Node()
        val grandchild = Node2D().apply { transform = Transform(position = Vec2(5f, 0f)) }
        root.addChild(grandparent)
        grandparent.addChild(middle)
        middle.addChild(grandchild)
        grandparent.world()
        grandchild.world()
        grandparent.transform = grandparent.transform.copy(position = Vec2(100f, 0f))
        assertEquals(Vec2(100f, 0f), grandparent.world().position)
        assertEquals(Vec2(105f, 0f), grandchild.world().position)
    }

    // 4.5: After reparenting, child.world() reflects new parent
    @Test
    fun `reparenting invalidates child world transform`() {
        val root = Node()
        val p1 = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        val p2 = Node2D().apply { transform = Transform(position = Vec2(100f, 0f)) }
        val child = Node2D().apply { transform = Transform(position = Vec2(5f, 0f)) }
        root.addChild(p1)
        root.addChild(p2)
        p1.addChild(child)
        assertEquals(Vec2(15f, 0f), child.world().position)
        p1.removeChild(child)
        p2.addChild(child)
        assertEquals(Vec2(105f, 0f), child.world().position)
    }

    // 4.6: Sibling's cache is not affected by another sibling's transform change
    @Test
    fun `reassigning local transform does not affect sibling cache`() {
        val root = Node()
        val parent = Node2D().apply { transform = Transform(position = Vec2(10f, 0f)) }
        val child1 = Node2D().apply { transform = Transform(position = Vec2(1f, 0f)) }
        val child2 = Node2D().apply { transform = Transform(position = Vec2(2f, 0f)) }
        root.addChild(parent)
        parent.addChild(child1)
        parent.addChild(child2)
        child1.world()
        child2.world()
        child1.transform = child1.transform.copy(position = Vec2(99f, 0f))
        assertEquals(Vec2(12f, 0f), child2.world().position)
        assertEquals(Vec2(109f, 0f), child1.world().position)
    }

    // 4.7: SceneLoader.save does not include the cache field, and load yields
    // a node whose cache is unpopulated until the first read.
    @Test
    fun `saved JSON does not contain cachedWorld field`() {
        val root = Node()
        val node = Node2D().apply { transform = Transform(position = Vec2(5f, 10f)) }
        root.addChild(node)
        node.world()
        val json = SceneLoader.save(root)
        assertFalse(json.contains("cachedWorld"), "JSON must not contain cachedWorld")

        val loaded = SceneLoader.load(json)
        val loadedNode = loaded.children.single() as Node2D
        val field = Node2D::class.java.getDeclaredField("cachedWorld")
        field.isAccessible = true
        assertNull(field.get(loadedNode), "loaded node's cache must start null")
        loadedNode.world()
        assertNotNull(field.get(loadedNode), "loaded node's cache must populate on first read")
    }

    // 5.2: Use reflection to assert cache is populated after first read and unchanged after second
    @Test
    fun `cache is populated after first world call and stable on second`() {
        val node = Node2D().apply { transform = Transform(position = Vec2(1f, 2f)) }
        val field = Node2D::class.java.getDeclaredField("cachedWorld")
        field.isAccessible = true
        assertNull(field.get(node), "cache should be null before any call")
        val result = node.world()
        val cached = field.get(node) as? Transform
        assertNotNull(cached, "cache should be populated after first call")
        assertEquals(result.position, cached.position)
        node.world()
        assertEquals(cached, field.get(node), "cache object must not change on second call")
    }
}
