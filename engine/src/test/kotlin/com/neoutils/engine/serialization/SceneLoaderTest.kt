package com.neoutils.engine.serialization

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Circle2D
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Scene
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
            addChild(ColorRect().apply {
                name = "rectangle"
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
            addChild(ColorRect().apply {
                name = "first"
                size = Vec2(10f, 10f)
                color = Color.GREEN
            })
            addChild(Circle2D().apply {
                name = "second"
                radius = 10f
                color = Color.BLUE
            })
        }
        val loaded = SceneLoader.load(SceneLoader.save(original))
        assertEquals(2, loaded.children.size)
        val first = loaded.children[0] as ColorRect
        val second = loaded.children[1] as Circle2D
        assertEquals("first", first.name)
        assertEquals(Vec2(10f, 10f), first.size)
        assertEquals(Color.GREEN, first.color)
        assertEquals("second", second.name)
        assertEquals(10f, second.radius)
    }

    @Test
    fun `round-trip is stable`() {
        val scene = Scene().apply {
            addChild(Circle2D().apply {
                radius = 25f
                color = Color.WHITE
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

    @Test
    fun `script-path identifier resolves like any other identifier`() {
        NodeRegistry.register("scripts/counter.nengine.kts", CounterNode::class) { CounterNode() }
        val jsonText = """
            {
              "version": 1,
              "root": {
                "type": "com.neoutils.engine.scene.Scene",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "scripts/counter.nengine.kts",
                    "name": "my_script",
                    "properties": {},
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()

        val loaded = SceneLoader.load(jsonText)
        assertEquals(1, loaded.children.size)
        assertTrue(loaded.children[0] is CounterNode)
        assertEquals("my_script", loaded.children[0].name)
    }

    @Test
    fun `unknown type fails fast regardless of suffix`() {
        val jsonText = """
            {
              "version": 1,
              "root": {
                "type": "com.neoutils.engine.scene.Scene",
                "name": "root",
                "properties": {},
                "children": [
                  {
                    "type": "scripts/missing.nengine.kts",
                    "name": "x",
                    "properties": {},
                    "children": []
                  }
                ]
              }
            }
        """.trimIndent()
        val ex = kotlin.test.assertFailsWith<UnknownNodeTypeException> {
            SceneLoader.load(jsonText)
        }
        assertEquals("scripts/missing.nengine.kts", ex.typeName)
    }

    @Test
    fun `save uses identifierFor when class was registered under a custom identifier`() {
        NodeRegistry.register("scripts/counter.nengine.kts", CounterNode::class) { CounterNode() }
        val scene = Scene().apply {
            name = "root"
            addChild(CounterNode().apply { name = "my_script" })
        }
        val text = SceneLoader.save(scene)
        val obj = Json.parseToJsonElement(text).jsonObject
        val child = (obj["root"]!!.jsonObject["children"] as kotlinx.serialization.json.JsonArray)[0].jsonObject
        assertEquals("scripts/counter.nengine.kts", child["type"]!!.jsonPrimitive.content)
    }
}

class CounterNode : com.neoutils.engine.scene.Node() {
    override fun onEnter() { totalEnters++ }
    companion object {
        @Volatile var totalEnters: Int = 0
    }
}
