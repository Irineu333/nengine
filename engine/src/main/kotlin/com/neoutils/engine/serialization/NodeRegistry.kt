package com.neoutils.engine.serialization

import com.neoutils.engine.physics.BoxCollider
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.scene.Shape
import com.neoutils.engine.scene.Text
import kotlin.reflect.KClass

/**
 * Maps fully-qualified Kotlin class names to no-args factories so the
 * scene loader can rebuild a tree without reflection on the JVM.
 *
 * Game modules must call [register] for every `Node` subclass they intend
 * to appear in a serialized scene file before invoking the loader. Engine
 * built-ins are registered together via [registerEngineTypes].
 */
object NodeRegistry {

    private val factories: MutableMap<String, () -> Node> = mutableMapOf()

    fun register(type: KClass<out Node>, factory: () -> Node) {
        val name = type.qualifiedName
            ?: error("Cannot register a node type with no qualified name: $type")
        factories[name] = factory
    }

    fun create(typeName: String): Node {
        val factory = factories[typeName] ?: throw UnknownNodeTypeException(typeName)
        return factory()
    }

    fun isRegistered(typeName: String): Boolean = typeName in factories

    /** Drops every registration. Intended for tests; production code typically
     *  registers once at startup and never clears. */
    fun clear() {
        factories.clear()
    }

    /** Registers every concrete `Node` subclass shipped by `:engine`. */
    fun registerEngineTypes() {
        register(Scene::class) { Scene() }
        register(Node2D::class) { Node2D() }
        register(Shape::class) { Shape() }
        register(Text::class) { Text() }
        register(BoxCollider::class) { BoxCollider() }
    }
}

class UnknownNodeTypeException(val typeName: String) :
    RuntimeException("No factory registered for node type: $typeName")
