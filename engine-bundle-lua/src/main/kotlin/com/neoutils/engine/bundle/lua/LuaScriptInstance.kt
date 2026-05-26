package com.neoutils.engine.bundle.lua

import com.neoutils.engine.bundle.script.Script
import com.neoutils.engine.bundle.script.ScriptInstance
import com.neoutils.engine.physics.Area2D
import com.neoutils.engine.physics.PhysicsBody2D
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.serialization.Signal
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua

internal class LuaScriptInstance(
    private val host: LuaScriptHost,
    private val instance: LuaTable,
    @Suppress("unused") private val node: Node,
    private val script: Script,
    override val signals: Map<String, Signal<*>>,
) : ScriptInstance {

    override fun setExport(name: String, value: Any?) {
        instance.rawset(name, kotlinToLua(value))
    }

    override fun currentValue(name: String): Any? {
        val export = script.exports.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException(
                "Export '$name' is not declared in script '${script.path}'"
            )
        val raw = instance.rawget(name)
        if (raw.isnil()) return export.default
        return luaValueToKotlin(raw, export.type)
    }

    override fun onEnter() = callHook("_ready")
    override fun onProcess(dt: Float) = callHook("_process", LuaValue.valueOf(dt.toDouble()))
    override fun onPhysicsProcess(dt: Float) = callHook("_physics_process", LuaValue.valueOf(dt.toDouble()))
    override fun onDraw(renderer: Renderer) = callHook("_draw", host.toLua(renderer))
    override fun onExit() = callHook("_exit_tree")
    override fun onAreaEntered(area: Area2D) = callHook("_on_area_entered", host.toLua(area))
    override fun onAreaExited(area: Area2D) = callHook("_on_area_exited", host.toLua(area))
    override fun onBodyEntered(body: PhysicsBody2D) = callHook("_on_body_entered", host.toLua(body))
    override fun onBodyExited(body: PhysicsBody2D) = callHook("_on_body_exited", host.toLua(body))

    private fun callHook(name: String, vararg luaArgs: LuaValue) {
        val fn = instance.rawget(name)
        if (fn.isnil() || !fn.isfunction()) return
        when (luaArgs.size) {
            0 -> fn.call(instance)
            1 -> fn.call(instance, luaArgs[0])
            2 -> fn.call(instance, luaArgs[0], luaArgs[1])
            else -> {
                val varargs = arrayOf<LuaValue>(instance, *luaArgs)
                fn.invoke(varargs)
            }
        }
    }
}
