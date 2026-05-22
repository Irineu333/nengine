package com.neoutils.engine.bundle.scripting

import com.neoutils.engine.scene.Node
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLClassLoader
import java.security.MessageDigest
import kotlin.reflect.KClass
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

internal class KotlinScriptingHost(
    private val source: ScriptSource,
    private val cacheDir: File,
    engineVersion: String? = null,
) {

    private val engineVersion: String = engineVersion ?: loadEngineVersionFromResource()
    private val classesDir: File = File(cacheDir, "classes").absoluteFile

    var compilationCount = 0
        private set

    init {
        cacheDir.mkdirs()
    }

    /**
     * Compile every script path in [paths] using the round-robin / fixed-point
     * algorithm. Returns a map from path to its top-level Node subclass.
     *
     * Each call rebuilds [classesDir] from scratch so bytecode from prior
     * runs of unrelated scripts cannot leak into the resulting class loader.
     */
    fun compileAll(paths: Set<String>): Map<String, KClass<out Node>> {
        classesDir.deleteRecursively()
        classesDir.mkdirs()

        val classLoader = URLClassLoader(
            arrayOf(classesDir.toURI().toURL()),
            this::class.java.classLoader
        )

        if (paths.isEmpty()) return emptyMap()

        val sources: Map<String, String> = paths.associateWith { source.read(it) }
        val topLevelByPath: Map<String, Set<String>> = sources
            .mapValues { (_, src) -> extractTopLevelClassNames(src) }

        val resolved = linkedMapOf<String, KClass<out Node>>()
        val compiledWrapperClasses = mutableListOf<String>()
        val pending = paths.toMutableSet()

        while (pending.isNotEmpty()) {
            var progressed = false
            for (path in pending.toList()) {
                val pendingOtherNames: Set<String> = pending
                    .filter { it != path }
                    .flatMap { topLevelByPath.getValue(it) }
                    .toSet()

                val klass = tryResolveOne(
                    path = path,
                    src = sources.getValue(path),
                    wrapperClasses = compiledWrapperClasses,
                    classLoader = classLoader,
                    pendingClassNames = pendingOtherNames,
                ) ?: continue

                resolved[path] = klass
                pending.remove(path)
                progressed = true
            }
            if (!progressed) {
                throw CyclicScriptDependencyError(pending.toSet())
            }
        }

        return resolved
    }

    private fun tryResolveOne(
        path: String,
        src: String,
        wrapperClasses: MutableList<String>,
        classLoader: URLClassLoader,
        pendingClassNames: Set<String>,
    ): KClass<out Node>? {
        val sortedImports = wrapperClasses.sorted()
        val cacheKey = sha256(
            buildString {
                append(src)
                append(CACHE_DELIMITER)
                append(sortedImports.joinToString("\n"))
                append(CACHE_DELIMITER)
                append(engineVersion)
            }
        )
        val cacheFile = File(cacheDir, "$cacheKey.bin")

        val filesMap: Map<String, ByteArray> = if (cacheFile.exists()) {
            loadFromCache(cacheFile)
        } else {
            when (val outcome = performCompilation(path, src, wrapperClasses)) {
                is CompileOutcome.Success -> {
                    saveToCache(cacheFile, outcome.files)
                    outcome.files
                }
                is CompileOutcome.Failure -> {
                    if (outcome.allUnresolvedAndPending(pendingClassNames)) {
                        return null
                    }
                    throw IllegalStateException(
                        "Compilation failed for script $path:\n${outcome.formattedMessages}"
                    )
                }
            }
        }

        for ((name, bytes) in filesMap) {
            val destFile = File(classesDir, name)
            destFile.parentFile.mkdirs()
            destFile.writeBytes(bytes)
        }

        val wrapperClassName = filesMap.keys
            .firstOrNull { it.endsWith(".class") && !it.contains("$") }
            ?.removeSuffix(".class")
            ?.replace('/', '.')
            ?: throw IllegalStateException("No wrapper class found in compiled files for script $path")

        wrapperClasses.add(wrapperClassName)

        val nodeClasses = mutableListOf<Class<*>>()
        for (filename in filesMap.keys) {
            if (!filename.endsWith(".class")) continue
            val className = filename.removeSuffix(".class").replace('/', '.')
            val prefix = "$wrapperClassName$"
            val isDirectNested = className.startsWith(prefix) &&
                !className.substring(prefix.length).contains("$")
            if (!isDirectNested) continue
            val loaded = classLoader.loadClass(className)
            if (Node::class.java.isAssignableFrom(loaded)) {
                nodeClasses.add(loaded)
            }
        }

        if (nodeClasses.isEmpty()) {
            throw IllegalStateException("Script $path contains zero top-level classes extending Node")
        }
        if (nodeClasses.size > 1) {
            throw IllegalStateException(
                "Script $path contains more than one top-level class extending Node: " +
                    nodeClasses.map { it.simpleName }
            )
        }

        @Suppress("UNCHECKED_CAST")
        return nodeClasses[0].kotlin as KClass<out Node>
    }

    private sealed class CompileOutcome {
        data class Success(val files: Map<String, ByteArray>) : CompileOutcome()
        data class Failure(
            val rawDiagnostics: List<ScriptDiagnostic>,
            val path: String,
        ) : CompileOutcome() {
            val formattedMessages: String
                get() = rawDiagnostics.joinToString("\n") { d ->
                    val loc = d.location
                    "[${d.severity}] ${d.sourcePath ?: path}:${loc?.start?.line ?: "?"}:${loc?.start?.col ?: "?"}: ${d.message}"
                }

            fun allUnresolvedAndPending(pendingClassNames: Set<String>): Boolean {
                if (rawDiagnostics.isEmpty()) return false
                // Lenient: defer as soon as any unresolved reference points at a
                // pending script's top-level class. Other diagnostics (cascade
                // "cannot infer type", "needs opt-in", member references on the
                // unresolved class) are treated as downstream noise — once the
                // pending class resolves, the script is retried and any
                // remaining real errors propagate on the next iteration.
                for (diag in rawDiagnostics) {
                    val symbol = unresolvedReferenceSymbol(diag.message) ?: continue
                    if (symbol in pendingClassNames) return true
                }
                return false
            }
        }
    }

    private fun performCompilation(
        path: String,
        scriptContent: String,
        wrapperClasses: List<String>,
    ): CompileOutcome {
        compilationCount++
        val host = BasicJvmScriptingHost()
        val prependedContent = "package scripts\n\n$scriptContent"
        val source = prependedContent.toScriptSource(path)

        val config = ScriptCompilationConfiguration(NEngineScriptCompilationConfiguration) {
            val baseImports = NEngineScriptCompilationConfiguration[ScriptCompilationConfiguration.defaultImports] ?: emptyList()
            defaultImports(baseImports + wrapperClasses.map { "$it.*" })
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
                val classpathStr = System.getProperty("java.class.path") ?: ""
                val systemClasspath = classpathStr
                    .split(File.pathSeparator)
                    .map { File(it) }
                    .filter { it.exists() }
                updateClasspath(systemClasspath + classesDir)
            }
        }

        val result = runBlocking { host.compiler(source, config) }
        val errors = result.reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR }
        if (errors.isNotEmpty()) {
            return CompileOutcome.Failure(errors, path)
        }

        val compiled = result.valueOrNull() as? KJvmCompiledScript
            ?: throw IllegalStateException("Compilation returned null or unexpected compiled script type for $path")

        val compiledModuleField = KJvmCompiledScript::class.java.getDeclaredField("compiledModule")
        compiledModuleField.isAccessible = true
        val module = compiledModuleField.get(compiled)
            ?: throw IllegalStateException("KJvmCompiledScript compiledModule was null for $path")

        val getCompilerOutputFilesMethod = module.javaClass.getDeclaredMethod("getCompilerOutputFiles")
        getCompilerOutputFilesMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val files = getCompilerOutputFilesMethod.invoke(module) as? Map<String, ByteArray>
            ?: throw IllegalStateException("Failed to retrieve compiler output files for $path")

        return CompileOutcome.Success(files)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFromCache(file: File): Map<String, ByteArray> {
        return ObjectInputStream(FileInputStream(file)).use {
            it.readObject() as Map<String, ByteArray>
        }
    }

    private fun saveToCache(file: File, compiledFiles: Map<String, ByteArray>) {
        file.parentFile.mkdirs()
        val tempFile = File.createTempFile("compile-cache-", ".tmp", file.parentFile)
        try {
            ObjectOutputStream(FileOutputStream(tempFile)).use {
                it.writeObject(compiledFiles)
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private companion object {
        private const val CACHE_DELIMITER = "\n---\n"
        private const val DEFAULT_ENGINE_VERSION = "0.0.0-default"

        private val TOP_LEVEL_CLASS_REGEX = Regex(
            """^\s*(?:public\s+|open\s+|abstract\s+|sealed\s+)?class\s+(\w+)""",
            RegexOption.MULTILINE
        )

        private val UNRESOLVED_REFERENCE_REGEX = Regex(
            """unresolved reference[:\s]+'?([A-Za-z_]\w*)'?""",
            RegexOption.IGNORE_CASE
        )

        fun extractTopLevelClassNames(src: String): Set<String> =
            TOP_LEVEL_CLASS_REGEX.findAll(src).map { it.groupValues[1] }.toSet()

        fun unresolvedReferenceSymbol(message: String?): String? {
            if (message == null) return null
            return UNRESOLVED_REFERENCE_REGEX.find(message)?.groupValues?.getOrNull(1)
        }

        fun loadEngineVersionFromResource(): String {
            val resource = KotlinScriptingHost::class.java.classLoader
                .getResource("META-INF/nengine.version")
                ?: return DEFAULT_ENGINE_VERSION
            return resource.readText().trim().ifEmpty { DEFAULT_ENGINE_VERSION }
        }
    }
}
