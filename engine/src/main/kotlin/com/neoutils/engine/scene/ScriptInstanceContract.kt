package com.neoutils.engine.scene

import com.neoutils.engine.render.Renderer
import com.neoutils.engine.serialization.Signal

/**
 * Minimal contract that `:engine` needs to dispatch lifecycle hooks to an
 * attached script without depending on `:engine-bundle` or any scripting
 * runtime. The full [ScriptInstance] interface (which adds `setExport`) lives
 * in `:engine-bundle` and extends this one.
 */
interface ScriptInstanceContract {
    fun onEnter()
    fun onProcess(dt: Float)
    fun onPhysicsProcess(dt: Float)
    fun onDraw(renderer: Renderer)
    fun onExit()
    fun onCollide(other: Node)

    /**
     * Signals declared at the top level of the script and instantiated at
     * attach time. Keyed by signal name as it appears in the script source.
     */
    val signals: Map<String, Signal<*>>
}
