package com.neoutils.engine.debug

import com.neoutils.engine.input.Key
import com.neoutils.engine.scene.Node

/**
 * Engine-internal `Node` that polls the debug-layout reset shortcut each tick
 * and, on the [resetKey] edge, clears every dragged panel's position override
 * (`tree.debug.resetAllPanelPositions()`) so the panels flow back to their dock
 * slots — the escape hatch from a messy drag layout without restarting.
 *
 * Lives under `ScreenDebugCanvas`, mirroring [DebugToggleNode] /
 * [TimeControlShortcutNode]: not a `DebugWidget` (no HUD row, no `drawDebug`).
 * Gated on `tree.debug.hud.enabled` so the default binding never collides with
 * gameplay input while the debug HUD is closed. [resetKey] is a public `var`
 * so a game can rebind it.
 */
internal class DebugLayoutShortcutNode : Node() {

    var resetKey: Key = Key.BACKSPACE

    init { name = "DebugLayoutShortcutNode" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        val owningTree = tree ?: return
        if (!owningTree.debug.hud.enabled) return
        val input = owningTree.input ?: return
        if (input.wasKeyPressed(resetKey)) {
            owningTree.debug.resetAllPanelPositions()
        }
    }
}
