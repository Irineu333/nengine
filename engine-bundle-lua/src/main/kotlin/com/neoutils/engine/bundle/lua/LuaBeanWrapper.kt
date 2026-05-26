package com.neoutils.engine.bundle.lua

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.full.memberProperties

/**
 * Wraps any Kotlin/Java object as a LuaTable whose metatable resolves
 * field/property reads and writes via reflection. This is the bean-aware
 * counterpart to LuaJ's default `JavaInstance`, which only exposes public
 * Java fields and methods literally — Kotlin `val x: Float` would compile to
 * a private field + `getX()` getter, so `obj.x` in Lua would return `nil`.
 *
 * Caches one wrapper per object (identity-keyed) on the owning host so that
 * repeated `host.toLua(sameObject)` returns the same Lua handle, and so that
 * `wrapped.method()` returns Lua values that themselves are re-wrapped
 * uniformly.
 */
internal class LuaBeanWrapper(
    private val host: LuaScriptHost,
    private val obj: Any,
) : ThreeArgFunction() {

    override fun call(self: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
        // ThreeArgFunction is reused for __newindex (3 args) — see Companion.
        throw LuaError("unreachable")
    }

    companion object {

        fun create(host: LuaScriptHost, obj: Any): LuaTable {
            val table = LuaTable()
            table.rawset(LuaScriptHost.NODE_MARKER_KEY, CoerceJavaToLua.coerce(obj))
            val metatable = LuaTable()
            metatable.set("__index", BeanIndex(host, obj))
            metatable.set("__newindex", BeanNewIndex(host, obj))
            table.setmetatable(metatable)
            return table
        }
    }
}

private class BeanIndex(
    private val host: LuaScriptHost,
    private val obj: Any,
) : TwoArgFunction() {
    override fun call(self: LuaValue, key: LuaValue): LuaValue {
        val name = key.tojstring()
        // Kotlin properties — covers data class `val x: Float` (which has no
        // public field, only getX()).
        val prop = obj::class.memberProperties.firstOrNull { it.name == name }
        if (prop != null) {
            @Suppress("UNCHECKED_CAST")
            val getter = prop as kotlin.reflect.KProperty1<Any, Any?>
            return host.toLua(getter.get(obj))
        }
        // Bean-style getX/isX getters.
        val getter = findGetter(obj::class.java, name)
        if (getter != null) {
            val raw = getter.invoke(obj)
            return host.toLua(raw)
        }
        // Method dispatch: return a Lua-callable that invokes the named
        // method on the wrapped object, dropping the `self` argument the
        // colon-call syntax injects.
        val method = findMethod(obj::class.java, name)
        if (method != null) {
            return BeanMethod(host, obj, method)
        }
        return LuaValue.NIL
    }

    private fun findGetter(klass: Class<*>, name: String): Method? {
        val pascal = name.replaceFirstChar { it.uppercase() }
        val candidates = arrayOf("get$pascal", "is$pascal")
        for (method in klass.methods) {
            if (Modifier.isStatic(method.modifiers)) continue
            if (method.parameterCount != 0) continue
            if (method.name in candidates) return method
        }
        return null
    }

    private fun findMethod(klass: Class<*>, name: String): Method? {
        for (method in klass.methods) {
            if (Modifier.isStatic(method.modifiers)) continue
            if (method.name == name) return method
        }
        return null
    }
}

private class BeanNewIndex(
    @Suppress("unused") private val host: LuaScriptHost,
    private val obj: Any,
) : ThreeArgFunction() {
    override fun call(self: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
        val name = key.tojstring()
        // Kotlin property with setter (`var x: Float` or `var text: String`).
        val prop = obj::class.memberProperties.firstOrNull { it.name == name }
        if (prop is kotlin.reflect.KMutableProperty1<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val mut = prop as kotlin.reflect.KMutableProperty1<Any, Any?>
            val coerced = coerceForProperty(mut, value)
            mut.set(obj, coerced)
            return LuaValue.NIL
        }
        // Bean-style setter fallback (setName).
        val pascal = name.replaceFirstChar { it.uppercase() }
        for (method in obj::class.java.methods) {
            if (Modifier.isStatic(method.modifiers)) continue
            if (method.parameterCount != 1) continue
            if (method.name == "set$pascal") {
                val arg = coerceArgument(method.parameterTypes[0], value)
                method.invoke(obj, arg)
                return LuaValue.NIL
            }
        }
        throw LuaError("cannot set '$name' on ${obj::class.simpleName}")
    }

    private fun coerceForProperty(prop: kotlin.reflect.KMutableProperty1<*, *>, value: LuaValue): Any? {
        val returnClassifier = prop.returnType.classifier as? kotlin.reflect.KClass<*> ?: return luaValueToAny(value)
        return when (returnClassifier) {
            Float::class -> value.todouble().toFloat()
            Double::class -> value.todouble()
            Int::class -> value.toint()
            Long::class -> value.tolong()
            Boolean::class -> value.toboolean()
            String::class -> value.tojstring()
            else -> luaValueToAny(value)
        }
    }
}

private class BeanMethod(
    private val host: LuaScriptHost,
    private val obj: Any,
    private val method: Method,
) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        // args.arg1() is `self`; skip it for the colon-call sugar.
        val paramTypes = method.parameterTypes
        val javaArgs = Array<Any?>(paramTypes.size) { i ->
            coerceArgument(paramTypes[i], args.arg(i + 2))
        }
        val result = try {
            method.invoke(obj, *javaArgs)
        } catch (err: java.lang.reflect.InvocationTargetException) {
            val cause = err.targetException
            if (cause is RuntimeException) throw cause else throw err
        }
        return host.toLua(result)
    }
}

private fun coerceArgument(targetType: Class<*>, value: LuaValue): Any? {
    return when (targetType) {
        java.lang.Float.TYPE, java.lang.Float::class.java -> value.todouble().toFloat()
        java.lang.Double.TYPE, java.lang.Double::class.java -> value.todouble()
        java.lang.Integer.TYPE, java.lang.Integer::class.java -> value.toint()
        java.lang.Long.TYPE, java.lang.Long::class.java -> value.tolong()
        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> value.toboolean()
        java.lang.String::class.java -> value.tojstring()
        else -> luaValueToAny(value)
    }
}
