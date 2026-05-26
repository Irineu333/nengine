package com.neoutils.engine.bundle.lua

import com.neoutils.engine.scene.Node
import com.neoutils.engine.serialization.Signal
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Reflection helpers used by `LuaScriptHost`'s metatable `__index/__newindex`
 * to resolve property reads/writes and method calls against the underlying
 * Kotlin Node.
 *
 * Caches are keyed on the concrete `KClass`; lookups are O(1) after warm-up.
 */
internal object LuaReflect {

    internal data class SignalInfo(val signal: Signal<Any?>, val isUnit: Boolean)

    private val propertyCache = mutableMapOf<KClass<*>, Map<String, KProperty1<Any, Any?>>>()
    private val methodCache = mutableMapOf<KClass<*>, Map<String, Method>>()

    @Suppress("UNCHECKED_CAST")
    fun signalProperty(node: Node, name: String): SignalInfo? {
        val prop = propertiesOf(node::class)[name] ?: return null
        val classifier = prop.returnType.classifier as? KClass<*> ?: return null
        if (classifier != Signal::class) return null
        val argType = prop.returnType.arguments.firstOrNull()?.type
        val isUnit = (argType?.classifier as? KClass<*>) == Unit::class
        val sig = prop.get(node) as? Signal<Any?> ?: return null
        return SignalInfo(sig, isUnit)
    }

    fun readProperty(node: Node, name: String): Any? {
        val prop = propertiesOf(node::class)[name] ?: return null
        return prop.get(node)
    }

    @Suppress("UNCHECKED_CAST")
    fun writeProperty(node: Node, name: String, value: Any?): Boolean {
        val prop = propertiesOf(node::class)[name] ?: return false
        val mutable = prop as? KMutableProperty1<Any, Any?> ?: return false
        mutable.set(node, value)
        return true
    }

    fun method(node: Node, name: String): Method? {
        return methodsOf(node::class)[name]
    }

    @Suppress("UNCHECKED_CAST")
    private fun propertiesOf(klass: KClass<*>): Map<String, KProperty1<Any, Any?>> {
        propertyCache[klass]?.let { return it }
        val map = mutableMapOf<String, KProperty1<Any, Any?>>()
        for (prop in klass.memberProperties) {
            if (prop.visibility != KVisibility.PUBLIC) continue
            map[prop.name] = prop as KProperty1<Any, Any?>
        }
        propertyCache[klass] = map
        return map
    }

    private fun methodsOf(klass: KClass<*>): Map<String, Method> {
        methodCache[klass]?.let { return it }
        val map = mutableMapOf<String, Method>()
        // Use Java reflection for public methods — KFunctions are awkward to
        // dispatch through; java.lang.reflect.Method handles overloading and
        // primitive coercion natively via `Method.invoke`.
        for (method in klass.java.methods) {
            if (java.lang.reflect.Modifier.isStatic(method.modifiers)) continue
            // First-wins for simple-name lookup. For overloads, callers must
            // either use the colon syntax with matching arity or call the
            // method indirectly (LuaJ has no overload resolution beyond
            // signature-match).
            map.putIfAbsent(method.name, method)
        }
        methodCache[klass] = map
        return map
    }
}
