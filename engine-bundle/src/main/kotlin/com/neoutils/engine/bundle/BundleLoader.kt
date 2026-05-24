package com.neoutils.engine.bundle

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.ClasspathBundleSource
import com.neoutils.engine.bundle.script.DirectoryBundleSource
import com.neoutils.engine.bundle.script.PropCoercion
import com.neoutils.engine.bundle.script.ScriptHostRegistry
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeEntry
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneFile
import com.neoutils.engine.serialization.SceneLoader
import com.neoutils.engine.serialization.ScriptAttachment
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.reflect.KClass

/**
 * Loads a scene bundle (a folder containing `scene.json`, an optional
 * `scripts/` directory and a reserved `assets/` directory) into a detached
 * [Scene]. Two entry points cover the deployment scenarios: [fromResources]
 * resolves a bundle baked into the JVM classpath, and [fromPath] resolves a
 * bundle that lives on the filesystem (the editor scenario).
 *
 * Scripts are discovered via the `NodeEntry.script` field and dispatched
 * through [ScriptHostRegistry] by file extension. The legacy `.nengine.kts`
 * path was removed in E8 of `add-python-scripting`.
 */
object BundleLoader {

    private const val SCENE_FILE_NAME = "scene.json"

    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene {
        val resource = this::class.java.classLoader.getResource("$name/$SCENE_FILE_NAME")
            ?: throw IllegalArgumentException(
                "Bundle '$name' not found on classpath (missing $name/$SCENE_FILE_NAME)"
            )
        val sceneJson = resource.readText()
        return load(
            bundleSource = ClasspathBundleSource(bundleRoot = name),
            sceneJsonText = sceneJson,
            types = types,
        )
    }

    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene {
        if (!bundleDir.isDirectory) {
            throw IllegalArgumentException(
                "Bundle directory not found: ${bundleDir.absolutePath}"
            )
        }
        val sceneFile = File(bundleDir, SCENE_FILE_NAME)
        if (!sceneFile.isFile) {
            throw IllegalArgumentException(
                "Bundle at ${bundleDir.absolutePath} is missing $SCENE_FILE_NAME"
            )
        }
        return load(
            bundleSource = DirectoryBundleSource(bundleDir = bundleDir),
            sceneJsonText = sceneFile.readText(),
            types = types,
        )
    }

    private fun load(
        bundleSource: BundleSource,
        sceneJsonText: String,
        types: List<KClass<out Node>>,
    ): Scene {
        NodeRegistry.registerEngineTypes()

        for (klass in types) {
            NodeRegistry.register(klass, buildNoArgFactory(klass))
        }

        val sceneFile = Json.decodeFromString(SceneFile.serializer(), sceneJsonText)

        // Collect and load all scripts referenced in the scene via NodeEntry.script.
        val scriptPaths = collectScriptPaths(sceneFile.root)
        val scripts = if (scriptPaths.isNotEmpty()) {
            ScriptHostRegistry.loadAll(scriptPaths, bundleSource)
        } else {
            emptyMap()
        }

        return SceneLoader.load(sceneJsonText) { node, scriptPath ->
            val script = scripts[scriptPath]
                ?: error("Script '$scriptPath' was collected but not loaded — this is a bug")
            val host = ScriptHostRegistry.hostFor(scriptPath)
                ?: error("No ScriptHost registered for '$scriptPath' — this is a bug")
            val instance = host.attach(node, script)
            ScriptAttachment(
                instance = instance,
                exportNames = script.exports.map { it.name }.toSet(),
                applyExport = { name, jsonEl ->
                    val export = script.exports.first { it.name == name }
                    val value = PropCoercion.coerce(jsonEl, export.type, export.nullable)
                    instance.setExport(name, value)
                },
            )
        }
    }

    private fun collectScriptPaths(entry: NodeEntry): Set<String> {
        val result = linkedSetOf<String>()
        walkScripts(entry, result)
        return result
    }

    private fun walkScripts(entry: NodeEntry, out: MutableSet<String>) {
        entry.script?.let { out.add(it) }
        for (child in entry.children) walkScripts(child, out)
    }

    private fun buildNoArgFactory(klass: KClass<out Node>): () -> Node {
        val constructor = try {
            klass.java.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                "Node type ${klass.qualifiedName ?: klass} has no accessible no-args constructor",
                e
            )
        }
        constructor.isAccessible = true
        return { constructor.newInstance() as Node }
    }
}
