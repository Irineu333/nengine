package com.neoutils.engine.serialization

import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.scene.ScriptInstanceContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Saves a scene tree to and loads it from a JSON document. The serialized
 * shape is `SceneFile` over `NodeEntry`: each node carries an identifier (the
 * value registered in [NodeRegistry] for its class), its `name`, an `@Inspect`
 * property map, and its children in order.
 *
 * Load does not invoke `Scene.start()` — the returned scene is detached and
 * `isLive == false`. The caller decides when to make it live so deferred
 * setup (e.g. resource binding) has a chance to run first.
 */
object SceneLoader {

    private const val NAME_PROPERTY = "name"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun save(scene: Scene): String {
        val root = nodeToEntry(scene)
        return json.encodeToString(SceneFile.serializer(), SceneFile(root = root))
    }

    fun load(
        text: String,
        attachScript: ((node: Node, scriptPath: String, props: JsonObject?) -> ScriptInstanceContract?)? = null,
    ): Scene {
        val file = json.decodeFromString(SceneFile.serializer(), text)
        val root = entryToNode(file.root, attachScript)
        return root as? Scene
            ?: error("Root node is not a Scene: ${file.root.type}")
    }

    private fun nodeToEntry(node: Node): NodeEntry {
        val typeName = NodeRegistry.identifierFor(node::class)
            ?: node::class.qualifiedName
            ?: error("Node type has no qualified name (anonymous?): ${node::class}")
        val children = node.children.map(::nodeToEntry)
        return NodeEntry(
            type = typeName,
            name = node.name,
            properties = extractInspectProperties(node),
            children = children,
        )
    }

    private fun extractInspectProperties(node: Node): JsonObject {
        val out = linkedMapOf<String, JsonElement>()
        val klass = node::class
        for (property in klass.memberProperties) {
            property.findAnnotation<Inspect>() ?: continue
            if (property.name == NAME_PROPERTY) continue
            @Suppress("UNCHECKED_CAST")
            val getter = property as kotlin.reflect.KProperty1<Any, Any?>
            val value = getter.get(node)
            val serializer = json.serializersModule.serializer(property.returnType)
            out[property.name] = json.encodeToJsonElement(serializer, value)
        }
        return JsonObject(out)
    }

    private fun entryToNode(
        entry: NodeEntry,
        attachScript: ((node: Node, scriptPath: String, props: JsonObject?) -> ScriptInstanceContract?)?,
    ): Node {
        if (entry.props != null && entry.script == null) {
            error("NodeEntry '${entry.name}': 'props' requires 'script' to be non-null")
        }
        val node = NodeRegistry.create(entry.type)
        node.name = entry.name
        applyProperties(node, entry.properties)
        entry.script?.let { scriptPath ->
            node.scriptInstance = attachScript?.invoke(node, scriptPath, entry.props)
        }
        for (child in entry.children) {
            node.addChild(entryToNode(child, attachScript))
        }
        return node
    }

    private fun applyProperties(node: Node, properties: JsonObject) {
        val klass = node::class
        for (property in klass.memberProperties) {
            property.findAnnotation<Inspect>() ?: continue
            if (property.name == NAME_PROPERTY) continue
            val element = properties[property.name] ?: continue
            val mutable = property as? KMutableProperty1<*, *> ?: continue
            val serializer = json.serializersModule.serializer(property.returnType)
            val value = json.decodeFromJsonElement(serializer, element)
            @Suppress("UNCHECKED_CAST")
            (mutable as KMutableProperty1<Any, Any?>).set(node, value)
        }
    }
}
