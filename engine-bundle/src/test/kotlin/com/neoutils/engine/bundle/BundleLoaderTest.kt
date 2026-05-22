package com.neoutils.engine.bundle

import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.*

class TestCustomNode : Node() {
    var counter: Int = 0
}

class BundleLoaderTest {

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        File("build/scripting-cache").deleteRecursively()
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `fromResources returns a detached scene with the expected tree`() {
        val scene = BundleLoader.fromResources("test-bundle")
        assertFalse(scene.isLive)
        assertEquals("TestRoot", scene.name)
        assertEquals(2, scene.children.size)

        val foo = scene.children[0]
        assertEquals("fooScript", foo.name)
        assertEquals("FooNode", foo::class.simpleName)

        val collider = scene.children[1]
        assertTrue(collider is BoxCollider)
        assertEquals("engineCollider", collider.name)
    }

    @Test
    fun `fromPath returns equivalent scene from a temp directory`() {
        val temp = createTempDir("bundle-test")
        materializeTestBundle(temp)
        try {
            val fromDisk = BundleLoader.fromPath(temp)
            assertEquals("TestRoot", fromDisk.name)
            assertEquals(2, fromDisk.children.size)
            assertEquals("fooScript", fromDisk.children[0].name)
            assertEquals("FooNode", fromDisk.children[0]::class.simpleName)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `classpath and disk bundles produce semantically equivalent scenes`() {
        val temp = createTempDir("bundle-equiv")
        materializeTestBundle(temp)
        try {
            val fromResources = BundleLoader.fromResources("test-bundle")
            NodeRegistry.clear()
            val fromPath = BundleLoader.fromPath(temp)
            assertEquals(treeShape(fromResources), treeShape(fromPath))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `orphan script in scripts directory is not compiled`() {
        BundleLoader.fromResources("test-bundle")
        val classesDir = File("build/scripting-cache/test-bundle/classes")
        val orphanFiles = classesDir.walkTopDown()
            .filter { it.isFile && it.name.contains("Orphan", ignoreCase = true) }
            .toList()
        assertTrue(
            orphanFiles.isEmpty(),
            "Found orphan bytecode: ${orphanFiles.joinToString { it.absolutePath }}"
        )
    }

    @Test
    fun `same script referenced multiple times compiles once`() {
        val temp = createTempDir("bundle-dup")
        try {
            materializeTestBundle(temp)
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "DupRoot",
                    "properties": {},
                    "children": [
                      { "type": "scripts/foo.nengine.kts", "name": "first", "properties": {}, "children": [] },
                      { "type": "scripts/foo.nengine.kts", "name": "second", "properties": {}, "children": [] }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp)
            assertEquals(2, scene.children.size)
            val first = scene.children[0]
            val second = scene.children[1]
            assertEquals("first", first.name)
            assertEquals("second", second.name)
            assertSame(first::class, second::class)

            val cacheBins = File(temp, ".nengine-cache")
                .listFiles { _, n -> n.endsWith(".bin") }
                ?: emptyArray()
            assertEquals(1, cacheBins.size, "Expected a single compilation cache entry for the deduplicated script")
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `missing scene json raises exception naming the bundle`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            BundleLoader.fromResources("does-not-exist-bundle")
        }
        assertTrue(ex.message!!.contains("does-not-exist-bundle"))
    }

    @Test
    fun `custom types parameter registers compiled Node classes`() {
        val temp = createTempDir("bundle-custom")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "CustomRoot",
                    "properties": {},
                    "children": [
                      { "type": "${TestCustomNode::class.qualifiedName}", "name": "custom", "properties": {}, "children": [] }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp, types = listOf(TestCustomNode::class))
            assertEquals(1, scene.children.size)
            assertTrue(scene.children[0] is TestCustomNode)
            assertEquals("custom", scene.children[0].name)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `engine types resolve without manual registration`() {
        val temp = createTempDir("bundle-engine-only")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "EngineRoot",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.physics.BoxCollider",
                        "name": "wall",
                        "properties": {
                          "transform": { "position": {"x": 0.0, "y": 0.0}, "scale": {"x": 1.0, "y": 1.0}, "rotation": 0.0 },
                          "size": { "x": 10.0, "y": 10.0 }
                        },
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp)
            assertEquals(1, scene.children.size)
            assertTrue(scene.children[0] is BoxCollider)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun materializeTestBundle(target: File) {
        target.mkdirs()
        val sceneJson = readResource("test-bundle/scene.json")
        File(target, "scene.json").writeText(sceneJson)
        val scriptsDir = File(target, "scripts").apply { mkdirs() }
        File(scriptsDir, "foo.nengine.kts").writeText(readResource("test-bundle/scripts/foo.nengine.kts"))
        File(scriptsDir, "orphan.nengine.kts").writeText(readResource("test-bundle/scripts/orphan.nengine.kts"))
    }

    private fun readResource(path: String): String =
        BundleLoaderTest::class.java.classLoader.getResource(path)?.readText()
            ?: error("Test resource not found: $path")

    private fun treeShape(node: Node): String = buildString {
        append(node::class.simpleName)
        append('(')
        append(node.name)
        append(')')
        if (node.children.isNotEmpty()) {
            append('[')
            for ((idx, child) in node.children.withIndex()) {
                if (idx > 0) append(',')
                append(treeShape(child))
            }
            append(']')
        }
    }

    private fun createTempDir(prefix: String): File {
        val dir = File.createTempFile(prefix, "")
        dir.delete()
        dir.mkdirs()
        return dir
    }
}
