package com.neoutils.engine.bundle.lua

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.serialization.NodeRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaScriptHostTest {

    @Before
    fun setUp() {
        NodeRegistry.registerEngineTypes()
    }

    @After
    fun tearDown() {
        // NodeRegistry.registerEngineTypes is idempotent — leave registered.
    }

    @Test
    fun extensionIsDotLua() {
        assertEquals(".lua", LuaScriptHost.create().extension)
    }

    @Test
    fun loadAcceptsMinimalChunkReturningTable() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/min.lua", FakeBundle("scripts/min.lua" to """
            return { extends = "Node2D" }
        """.trimIndent()))
        assertEquals("scripts/min.lua", script.path)
        assertEquals(Node2D::class, script.extendsType)
        assertTrue(script.exports.isEmpty())
        assertTrue(script.signals.isEmpty())
    }

    @Test
    fun loadFailsWhenChunkReturnsNil() {
        val host = LuaScriptHost.create()
        assertFailsWith<LuaScriptContractException> {
            host.load("scripts/missing.lua", FakeBundle("scripts/missing.lua" to "-- no return"))
        }
    }

    @Test
    fun loadFailsWhenExtendsMissing() {
        val host = LuaScriptHost.create()
        assertFailsWith<LuaScriptContractException> {
            host.load("scripts/no_extends.lua", FakeBundle("scripts/no_extends.lua" to "return {}"))
        }
    }

    @Test
    fun loadFailsWhenExtendsUnknown() {
        val host = LuaScriptHost.create()
        val ex = assertFailsWith<LuaScriptContractException> {
            host.load("scripts/bad.lua", FakeBundle("scripts/bad.lua" to """
                return { extends = "NotANode" }
            """.trimIndent()))
        }
        assertTrue(ex.message!!.contains("NotANode"))
    }

    @Test
    fun exportsAreDiscovered() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/exp.lua", FakeBundle("scripts/exp.lua" to """
            return {
                extends = "Node2D",
                exports = {
                    speed = { type = "float", default = 360 },
                    ai    = { type = "bool",  default = false },
                    spawn = { type = "Vec2",  default = nengine.Vec2(1, 2) },
                },
            }
        """.trimIndent()))
        assertEquals(3, script.exports.size)
        val byName = script.exports.associateBy { it.name }
        assertEquals(360f, byName["speed"]!!.default)
        assertEquals(false, byName["ai"]!!.default)
        assertEquals(Vec2(1f, 2f), byName["spawn"]!!.default)
    }

    @Test
    fun signalsAreDiscoveredAndCollideWithExports() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/sig.lua", FakeBundle("scripts/sig.lua" to """
            return {
                extends = "Node2D",
                signals = { scored = "string", landed = "" },
            }
        """.trimIndent()))
        assertEquals(setOf("scored", "landed"), script.signals.keys)

        assertFailsWith<LuaScriptContractException> {
            host.load("scripts/dup.lua", FakeBundle("scripts/dup.lua" to """
                return {
                    extends = "Node2D",
                    exports = { x = { type = "float", default = 0 } },
                    signals = { x = "string" },
                }
            """.trimIndent()))
        }
    }

    @Test
    fun attachWiresPositionRoundTrip() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/p.lua", FakeBundle("scripts/p.lua" to """
            return {
                extends = "Node2D",
                _ready = function(self)
                    self.position = nengine.Vec2(10, 20)
                end,
            }
        """.trimIndent()))
        val node = Node2D()
        val instance = host.attach(node, script)
        instance.onEnter()
        assertEquals(Vec2(10f, 20f), node.position)
    }

    @Test
    fun processHookReceivesDt() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/dt.lua", FakeBundle("scripts/dt.lua" to """
            return {
                extends = "Node2D",
                exports = { last_dt = { type = "float", default = -1 } },
                _process = function(self, dt)
                    self.last_dt = dt
                end,
            }
        """.trimIndent()))
        val node = Node2D()
        val instance = host.attach(node, script)
        instance.onProcess(0.25f)
        assertEquals(0.25f, instance.currentValue("last_dt"))
    }

    @Test
    fun missingHookIsNoOp() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/none.lua", FakeBundle("scripts/none.lua" to """
            return { extends = "Node2D" }
        """.trimIndent()))
        val instance = host.attach(Node2D(), script)
        instance.onProcess(0.016f)
        instance.onPhysicsProcess(0.016f)
        instance.onExit()
    }

    @Test
    fun currentValueFallsBackToDefault() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/d.lua", FakeBundle("scripts/d.lua" to """
            return {
                extends = "Node2D",
                exports = { speed = { type = "float", default = 360 } },
            }
        """.trimIndent()))
        val instance = host.attach(Node2D(), script)
        assertEquals(360f, instance.currentValue("speed"))
        assertFailsWith<IllegalArgumentException> { instance.currentValue("bogus") }
    }

    @Test
    fun requireResolvesAgainstBundle() {
        val host = LuaScriptHost.create()
        val bundle = FakeBundle(
            "scripts/main.lua" to """
                local utils = require "scripts.utils"
                return {
                    extends = "Node2D",
                    exports = { v = { type = "int", default = utils.value } },
                }
            """.trimIndent(),
            "scripts/utils.lua" to """
                return { value = 7 }
            """.trimIndent(),
        )
        val script = host.load("scripts/main.lua", bundle)
        val byName = script.exports.associateBy { it.name }
        assertEquals(7, byName["v"]!!.default)
    }

    @Test
    fun scriptOfReturnsTheAttachedInstance() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/echo.lua", FakeBundle("scripts/echo.lua" to """
            return {
                extends = "Node2D",
                exports = { tag = { type = "string", default = "x" } },
            }
        """.trimIndent()))
        val node = Node2D()
        host.attach(node, script)
        val inst = host.lookupInstanceTable(node)
        assertNotNull(inst)
    }

    @Test
    fun reflectiveSignalConnectFiresHandler() {
        val host = LuaScriptHost.create()
        val script = host.load("scripts/timer.lua", FakeBundle("scripts/timer.lua" to """
            return {
                extends = "Timer",
                exports = { hits = { type = "int", default = 0 } },
                _ready = function(self)
                    self.timeout:connect(function() self.hits = self.hits + 1 end)
                end,
            }
        """.trimIndent()))
        val timer = com.neoutils.engine.scene.Timer()
        val instance = host.attach(timer, script)
        instance.onEnter()
        timer.timeout.emit(Unit)
        timer.timeout.emit(Unit)
        assertEquals(2, instance.currentValue("hits"))
    }

    private class FakeBundle(vararg files: Pair<String, String>) : BundleSource {
        private val map = files.toMap()
        override fun read(path: String): String =
            map[path] ?: error("FakeBundle missing $path")
        override fun exists(path: String): Boolean = path in map
    }
}
