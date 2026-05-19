package com.neoutils.engine.serialization

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.scene.Shape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneLoaderTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        NodeRegistry.registerEngineTypes()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `save produces version and root`() {
        val scene = Scene().apply {
            name = "root"
            addChild(Shape().apply {
                name = "rectangle"
                kind = Shape.Kind.Rect
                size = Vec2(20f, 30f)
                color = Color.RED
            })
        }
        val text = SceneLoader.save(scene)
        val obj: JsonObject = Json.parseToJsonElement(text).jsonObject
        assertEquals(1, obj["version"]!!.jsonPrimitive.content.toInt())
        val root = obj["root"]!!.jsonObject
        assertEquals("com.neoutils.engine.scene.Scene", root["type"]!!.jsonPrimitive.content)
        assertEquals("root", root["name"]!!.jsonPrimitive.content)
        assertTrue(root["children"] != null)
        assertTrue(root["properties"] != null)
    }

    @Test
    fun `load preserves order and properties`() {
        val original = Scene().apply {
            addChild(Shape().apply {
                name = "first"
                kind = Shape.Kind.Rect
                size = Vec2(10f, 10f)
                color = Color.GREEN
                filled = false
            })
            addChild(Shape().apply {
                name = "second"
                kind = Shape.Kind.Circle
                size = Vec2(20f, 20f)
                color = Color.BLUE
                filled = true
            })
        }
        val loaded = SceneLoader.load(SceneLoader.save(original))
        assertEquals(2, loaded.children.size)
        val first = loaded.children[0] as Shape
        val second = loaded.children[1] as Shape
        assertEquals("first", first.name)
        assertEquals(Shape.Kind.Rect, first.kind)
        assertEquals(Vec2(10f, 10f), first.size)
        assertEquals(Color.GREEN, first.color)
        assertFalse(first.filled)
        assertEquals("second", second.name)
        assertEquals(Shape.Kind.Circle, second.kind)
    }

    @Test
    fun `round-trip is stable`() {
        val scene = Scene().apply {
            addChild(Shape().apply {
                kind = Shape.Kind.Circle
                size = Vec2(50f, 50f)
            })
        }
        val first = SceneLoader.save(scene)
        val second = SceneLoader.save(SceneLoader.load(first))
        assertEquals(first, second)
    }

    @Test
    fun `load returns detached scene without firing onEnter`() {
        NodeRegistry.register(CounterNode::class) { CounterNode() }
        val original = Scene().apply {
            name = "scene"
            addChild(CounterNode().apply { name = "counter" })
        }
        val text = SceneLoader.save(original)
        CounterNode.totalEnters = 0
        val loaded = SceneLoader.load(text)
        assertFalse(loaded.isLive)
        assertEquals(0, CounterNode.totalEnters)
        loaded.start()
        assertEquals(1, CounterNode.totalEnters)
    }
}

class CounterNode : com.neoutils.engine.scene.Node() {
    override fun onEnter() { totalEnters++ }
    companion object {
        @Volatile var totalEnters: Int = 0
    }
}
