package com.neoutils.engine.bundle

import com.neoutils.engine.bundle.script.*
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneLoader
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.*

// ── Fake ScriptHost infrastructure ──────────────────────────────────────────

private class FakeScript(
    override val path: String,
    override val extendsType: KClass<out Node> = Node2D::class,
    override val exports: List<ExportedProperty> = listOf(
        ExportedProperty("value", Int::class, default = 0)
    ),
) : Script

private class FakeScriptInstance : ScriptInstance {
    val applied = mutableMapOf<String, Any?>()
    var updateCallCount = 0

    override fun setExport(name: String, value: Any?) { applied[name] = value }
    override fun onEnter() {}
    override fun onUpdate(dt: Float) { updateCallCount++ }
    override fun onRender(renderer: Renderer) {}
    override fun onCollide(other: Node) {}
}

private class FakeScriptHost : ScriptHost {
    override val extension = ".py"
    val loaded = mutableListOf<String>()
    val instances = mutableListOf<FakeScriptInstance>()

    override fun load(path: String, bundle: BundleSource): Script {
        loaded += path
        return FakeScript(path)
    }

    override fun attach(node: Node, script: Script): ScriptInstance {
        val instance = FakeScriptInstance()
        instances += instance
        return instance
    }
}

// ── Test class ──────────────────────────────────────────────────────────────

class BundleLoaderTest {

    private lateinit var fakeHost: FakeScriptHost

    @BeforeTest
    fun setUp() {
        NodeRegistry.clear()
        ScriptHostRegistry.clear()
        fakeHost = FakeScriptHost()
        ScriptHostRegistry.register(fakeHost)
    }

    @AfterTest
    fun tearDown() {
        NodeRegistry.clear()
        ScriptHostRegistry.clear()
    }

    @Test
    fun `fromResources returns a detached scene with the expected tree`() {
        val scene = BundleLoader.fromResources("test-bundle")
        assertFalse(scene.isLive)
        assertEquals("TestRoot", scene.name)
        assertEquals(2, scene.children.size)

        val foo = scene.children[0]
        assertEquals("fooScript", foo.name)
        assertTrue(foo is Node2D)
        // verify scriptInstance was attached: calling onUpdate propagates to the instance
        foo.onUpdate(0f)
        assertEquals(1, fakeHost.instances[0].updateCallCount)

        val collider = scene.children[1]
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
            assertTrue(fromDisk.children[0] is Node2D)
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
            ScriptHostRegistry.clear()
            ScriptHostRegistry.register(FakeScriptHost())
            val fromPath = BundleLoader.fromPath(temp)
            assertEquals(treeShape(fromResources), treeShape(fromPath))
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `orphan script in scripts directory is not loaded`() {
        BundleLoader.fromResources("test-bundle")
        assertFalse(
            fakeHost.loaded.any { it.contains("orphan", ignoreCase = true) },
            "Orphan script should not have been loaded; loaded: ${fakeHost.loaded}"
        )
        assertTrue(
            fakeHost.loaded.any { it.contains("dummy", ignoreCase = true) },
            "dummy.py should have been loaded"
        )
    }

    @Test
    fun `same script referenced multiple times loads once`() {
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
                      { "type": "com.neoutils.engine.scene.Node2D", "name": "first", "properties": {},
                        "script": "scripts/dummy.py", "props": { "value": 1 }, "children": [] },
                      { "type": "com.neoutils.engine.scene.Node2D", "name": "second", "properties": {},
                        "script": "scripts/dummy.py", "props": { "value": 2 }, "children": [] }
                    ]
                  }
                }
                """.trimIndent()
            )
            val scene = BundleLoader.fromPath(temp)
            assertEquals(2, scene.children.size)
            assertEquals("first", scene.children[0].name)
            assertEquals("second", scene.children[1].name)
            // same script path → loaded only once
            assertEquals(1, fakeHost.loaded.count { it == "scripts/dummy.py" })
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
                      { "type": "${TestCustomNode::class.qualifiedName}", "name": "custom",
                        "properties": {}, "children": [] }
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
            assertTrue(scene.children[0] is com.neoutils.engine.physics.BoxCollider)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `node with script and props attaches scriptInstance and applies export`() {
        val temp = createTempDir("bundle-script-props")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "scripted",
                        "properties": {},
                        "script": "scripts/dummy.py",
                        "props": { "value": 42 },
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/dummy.py").writeText("# extends Node2D\nvalue: int = 0\n")

            val scene = BundleLoader.fromPath(temp)
            val node = scene.children[0]
            // calling onUpdate propagates iff scriptInstance was attached
            node.onUpdate(0f)
            assertEquals(1, fakeHost.instances[0].updateCallCount, "scriptInstance must be attached")
            assertEquals(42, fakeHost.instances[0].applied["value"])
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `script with unknown extension fails fast`() {
        val temp = createTempDir("bundle-bad-ext")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "bad",
                        "properties": {},
                        "script": "scripts/thing.lua",
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            File(temp, "scripts").mkdirs()
            File(temp, "scripts/thing.lua").writeText("-- lua script")
            assertFailsWith<UnsupportedScriptExtensionException> {
                BundleLoader.fromPath(temp)
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun `props without script fails fast`() {
        val temp = createTempDir("bundle-props-no-script")
        try {
            File(temp, "scene.json").writeText(
                """
                {
                  "version": 1,
                  "root": {
                    "type": "com.neoutils.engine.scene.Scene",
                    "name": "Root",
                    "properties": {},
                    "children": [
                      {
                        "type": "com.neoutils.engine.scene.Node2D",
                        "name": "bad",
                        "properties": {},
                        "props": { "value": 1 },
                        "children": []
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            val ex = assertFailsWith<IllegalStateException> {
                BundleLoader.fromPath(temp)
            }
            assertTrue(ex.message!!.contains("props"), "Error should mention 'props': ${ex.message}")
        } finally {
            temp.deleteRecursively()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun materializeTestBundle(target: File) {
        target.mkdirs()
        val sceneJson = readResource("test-bundle/scene.json")
        File(target, "scene.json").writeText(sceneJson)
        val scriptsDir = File(target, "scripts").apply { mkdirs() }
        File(scriptsDir, "dummy.py").writeText(readResource("test-bundle/scripts/dummy.py"))
        File(scriptsDir, "orphan.py").writeText(readResource("test-bundle/scripts/orphan.py"))
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

class TestCustomNode : Node()
