package com.neoutils.engine.bundle

import com.neoutils.engine.bundle.script.BundleSource
import com.neoutils.engine.bundle.script.ClasspathBundleSource
import com.neoutils.engine.bundle.script.DirectoryBundleSource
import com.neoutils.engine.bundle.script.PropCoercion
import com.neoutils.engine.bundle.script.ScriptHostRegistry
import com.neoutils.engine.bundle.scripting.KotlinScriptingHost
import com.neoutils.engine.bundle.scripting.ScriptSource
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene
import com.neoutils.engine.serialization.NodeEntry
import com.neoutils.engine.serialization.NodeRegistry
import com.neoutils.engine.serialization.SceneFile
import com.neoutils.engine.serialization.SceneLoader
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
 * Scripts are discovered in two ways:
 * - Legacy: `NodeEntry.type` ending in `.nengine.kts` (compiled by [KotlinScriptingHost]).
 *   This path is kept until E8 of `add-python-scripting`.
 * - New: `NodeEntry.script` field pointing to a script file, dispatched via
 *   [ScriptHostRegistry] and attached to the node after creation.
 */
object BundleLoader {

    private const val SCENE_FILE_NAME = "scene.json"
    private const val KTS_SUFFIX = ".nengine.kts"

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
            legacyScriptSource = ScriptSource.Classpath(bundleRoot = name),
            sceneJsonText = sceneJson,
            cacheDir = File("build/scripting-cache/$name").absoluteFile,
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
            legacyScriptSource = ScriptSource.Directory(bundleDir = bundleDir),
            sceneJsonText = sceneFile.readText(),
            cacheDir = File(bundleDir, ".nengine-cache"),
            types = types,
        )
    }

    private fun load(
        bundleSource: BundleSource,
        legacyScriptSource: ScriptSource,
        sceneJsonText: String,
        cacheDir: File,
        types: List<KClass<out Node>>,
    ): Scene {
        NodeRegistry.registerEngineTypes()

        for (klass in types) {
            NodeRegistry.register(klass, buildNoArgFactory(klass))
        }

        val sceneFile = Json.decodeFromString(SceneFile.serializer(), sceneJsonText)

        // Legacy path: .nengine.kts scripts referenced via the `type` field (kept until E8).
        val ktsPaths = collectKtsPaths(sceneFile.root)
        if (ktsPaths.isNotEmpty()) {
            val host = KotlinScriptingHost(legacyScriptSource, cacheDir)
            val resolved = host.compileAll(ktsPaths)
            for ((path, klass) in resolved) {
                NodeRegistry.register(path, klass, buildNoArgFactory(klass))
            }
        }

        // New path: scripts referenced via NodeEntry.script, dispatched by ScriptHostRegistry.
        val scriptPaths = collectScriptPaths(sceneFile.root)
        val scripts = if (scriptPaths.isNotEmpty()) {
            ScriptHostRegistry.loadAll(scriptPaths, bundleSource)
        } else {
            emptyMap()
        }

        return SceneLoader.load(sceneJsonText) { node, scriptPath, props ->
            val script = scripts[scriptPath]
                ?: error("Script '$scriptPath' was collected but not loaded — this is a bug")
            val host = ScriptHostRegistry.hostFor(scriptPath)
                ?: error("No ScriptHost registered for '$scriptPath' — this is a bug")
            val instance = host.attach(node, script)
            if (props != null) {
                for ((name, jsonEl) in props) {
                    val export = script.exports.find { it.name == name } ?: continue
                    val value = PropCoercion.coerce(jsonEl, export.type, export.nullable)
                    instance.setExport(name, value)
                }
            }
            instance
        }
    }

    private fun collectKtsPaths(entry: NodeEntry): Set<String> {
        val result = linkedSetOf<String>()
        walkKts(entry, result)
        return result
    }

    private fun walkKts(entry: NodeEntry, out: MutableSet<String>) {
        if (entry.type.endsWith(KTS_SUFFIX)) out.add(entry.type)
        for (child in entry.children) walkKts(child, out)
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
