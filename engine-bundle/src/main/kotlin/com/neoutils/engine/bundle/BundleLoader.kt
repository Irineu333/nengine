package com.neoutils.engine.bundle

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
 * The loader registers engine types and any caller-supplied [KClass]es with
 * the [NodeRegistry], discovers scripts by tree-walking the parsed scene
 * JSON, compiles only the referenced scripts via the internal
 * [KotlinScriptingHost], and finally delegates to [SceneLoader.load].
 */
object BundleLoader {

    private const val SCENE_FILE_NAME = "scene.json"
    private const val SCRIPT_SUFFIX = ".nengine.kts"

    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene {
        val resource = this::class.java.classLoader.getResource("$name/$SCENE_FILE_NAME")
            ?: throw IllegalArgumentException(
                "Bundle '$name' not found on classpath (missing $name/$SCENE_FILE_NAME)"
            )
        val sceneJson = resource.readText()
        val cacheDir = File("build/scripting-cache/$name").absoluteFile
        return load(
            source = ScriptSource.Classpath(bundleRoot = name),
            sceneJsonText = sceneJson,
            cacheDir = cacheDir,
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
        val cacheDir = File(bundleDir, ".nengine-cache")
        return load(
            source = ScriptSource.Directory(bundleDir = bundleDir),
            sceneJsonText = sceneFile.readText(),
            cacheDir = cacheDir,
            types = types,
        )
    }

    private fun load(
        source: ScriptSource,
        sceneJsonText: String,
        cacheDir: File,
        types: List<KClass<out Node>>,
    ): Scene {
        NodeRegistry.registerEngineTypes()

        for (klass in types) {
            val identifier = klass.qualifiedName
                ?: error("Custom node type has no qualified name: $klass")
            val factory = buildNoArgFactory(klass)
            NodeRegistry.register(identifier, klass, factory)
        }

        val sceneFile = Json.decodeFromString(SceneFile.serializer(), sceneJsonText)
        val scriptPaths = collectScriptPaths(sceneFile.root)

        if (scriptPaths.isNotEmpty()) {
            val host = KotlinScriptingHost(source, cacheDir)
            val resolved = host.compileAll(scriptPaths)
            for ((path, klass) in resolved) {
                NodeRegistry.register(path, klass, buildNoArgFactory(klass))
            }
        }

        return SceneLoader.load(sceneJsonText)
    }

    private fun collectScriptPaths(entry: NodeEntry): Set<String> {
        val result = linkedSetOf<String>()
        walk(entry, result)
        return result
    }

    private fun walk(entry: NodeEntry, out: MutableSet<String>) {
        if (entry.type.endsWith(SCRIPT_SUFFIX)) {
            out.add(entry.type)
        }
        for (child in entry.children) walk(child, out)
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
