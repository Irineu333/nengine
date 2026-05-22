package com.neoutils.engine.scene

import com.neoutils.engine.render.Renderer

/**
 * Minimal contract that `:engine` needs to dispatch lifecycle hooks to an
 * attached script without depending on `:engine-bundle` or any scripting
 * runtime. The full [ScriptInstance] interface (which adds `setExport`) lives
 * in `:engine-bundle` and extends this one.
 */
interface ScriptInstanceContract {
    fun onEnter()
    fun onUpdate(dt: Float)
    fun onRender(renderer: Renderer)
    fun onCollide(other: Node)
}
