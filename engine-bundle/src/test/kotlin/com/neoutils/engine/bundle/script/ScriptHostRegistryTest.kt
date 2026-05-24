package com.neoutils.engine.bundle.script

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import org.junit.After
import org.junit.Before
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScriptHostRegistryTest {

    private val noopBundle = object : BundleSource {
        override fun read(path: String) = ""
        override fun exists(path: String) = false
    }

    private fun fakeHost(ext: String): ScriptHost = object : ScriptHost {
        override val extension = ext
        override fun load(path: String, bundle: BundleSource): Script = fakeScript(path)
        override fun attach(node: Node, script: Script): ScriptInstance = noopInstance()
    }

    private fun fakeScript(scriptPath: String): Script = object : Script {
        override val path = scriptPath
        override val extendsType: KClass<out Node> = Node2D::class
        override val exports = emptyList<ExportedProperty>()
    }

    private fun noopInstance(): ScriptInstance = object : ScriptInstance {
        override val signals: Map<String, com.neoutils.engine.serialization.Signal<*>> = emptyMap()
        override fun setExport(name: String, value: Any?) = Unit
        override fun onEnter() = Unit
        override fun onProcess(dt: Float) = Unit
        override fun onPhysicsProcess(dt: Float) = Unit
        override fun onDraw(renderer: Renderer) = Unit
        override fun onExit() = Unit
        override fun onCollide(other: Node) = Unit
    }

    @Before
    fun setUp() = ScriptHostRegistry.clear()

    @After
    fun tearDown() = ScriptHostRegistry.clear()

    @Test
    fun `hostFor returns registered host by extension`() {
        val pyHost = fakeHost(".py")
        ScriptHostRegistry.register(pyHost)
        val found = ScriptHostRegistry.hostFor("scripts/paddle.py")
        assertNotNull(found)
        assertEquals(".py", found.extension)
    }

    @Test
    fun `hostFor returns null for unknown extension`() {
        ScriptHostRegistry.register(fakeHost(".py"))
        assertNull(ScriptHostRegistry.hostFor("scripts/paddle.lua"))
    }

    @Test
    fun `hostFor returns null when path has no extension`() {
        ScriptHostRegistry.register(fakeHost(".py"))
        assertNull(ScriptHostRegistry.hostFor("scripts/noext"))
    }

    @Test
    fun `loadAll dispatches each path to its host`() {
        val pyHost = fakeHost(".py")
        ScriptHostRegistry.register(pyHost)
        val result = ScriptHostRegistry.loadAll(setOf("a.py", "b.py"), noopBundle)
        assertEquals(2, result.size)
        assertNotNull(result["a.py"])
        assertNotNull(result["b.py"])
    }

    @Test
    fun `loadAll with unknown extension throws naming path and extension`() {
        ScriptHostRegistry.register(fakeHost(".py"))
        val ex = assertFailsWith<UnsupportedScriptExtensionException> {
            ScriptHostRegistry.loadAll(setOf("scripts/paddle.lua"), noopBundle)
        }
        assert(ex.message!!.contains("scripts/paddle.lua"))
        assert(ex.message!!.contains(".lua"))
    }

    @Test
    fun `multiple hosts coexist and dispatch correctly`() {
        val pyHost = fakeHost(".py")
        val luaHost = fakeHost(".lua")
        ScriptHostRegistry.register(pyHost)
        ScriptHostRegistry.register(luaHost)
        val result = ScriptHostRegistry.loadAll(setOf("a.py", "b.lua"), noopBundle)
        assertEquals(2, result.size)
        assertEquals("a.py", result["a.py"]?.path)
        assertEquals("b.lua", result["b.lua"]?.path)
    }

    @Test
    fun `clear removes all registered hosts`() {
        ScriptHostRegistry.register(fakeHost(".py"))
        ScriptHostRegistry.clear()
        assertNull(ScriptHostRegistry.hostFor("scripts/test.py"))
    }
}
