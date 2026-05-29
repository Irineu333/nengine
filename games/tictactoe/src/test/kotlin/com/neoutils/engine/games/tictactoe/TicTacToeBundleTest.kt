package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.lua.LuaScriptHost
import com.neoutils.engine.debug.DrawCommand
import com.neoutils.engine.scene.Camera2D
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.tree.SceneTree
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TicTacToeBundleTest {

    @Before
    fun setUp() {
        NodeRegistry.clear()
    }

    @After
    fun tearDown() {
        NodeRegistry.clear()
    }

    @Test
    fun `bundle loads with expected tree shape`() {
        val lua = LuaScriptHost.create()
        val root = BundleLoader.fromResources("tictactoe", scripting = lua)

        val camera = root.findChild("MainCamera") as? Camera2D
        assertNotNull(camera, "MainCamera missing")
        assertTrue(camera.current, "MainCamera.current must be true")
        assertEquals(600f, camera.bounds.size.x)
        assertEquals(600f, camera.bounds.size.y)

        val hud = root.findChild("Hud")
        assertNotNull(hud, "Hud missing")
        assertNotNull(hud.findChild("status"), "status label missing")
    }

    @Test
    fun `board debug overlay is off by default and enqueues cell indices once enabled`() {
        val lua = LuaScriptHost.create()
        val root = BundleLoader.fromResources("tictactoe", scripting = lua)
        val tree = SceneTree(root = root)
        tree.start()

        // Disabled by default — `_process` calls the draw verbs but they no-op.
        assertEquals(false, tree.debug.draw.enabled, "immediate-draw must be off by default")
        tree.process(0.016f)
        assertEquals(0, tree.debug.draw.world.commands.size, "no gizmos while disabled")

        // Enabling (as the F1 HUD "Debug Draw" row does) makes the Lua script
        // path enqueue the nine cell-index labels into the world buffer
        // (cleared on render — assert before rendering).
        tree.debug.draw.enabled = true
        tree.process(0.016f)

        val world = tree.debug.draw.world.commands
        // No Input is injected in this test, so only the nine index labels are
        // emitted (the pointer-driven hover/screen gizmos are skipped).
        assertEquals(9, world.size, "expected 9 cell-index labels, got ${world.size}")
        assertTrue(world.all { it is DrawCommand.Text }, "cell indices must be text commands")
    }
}
