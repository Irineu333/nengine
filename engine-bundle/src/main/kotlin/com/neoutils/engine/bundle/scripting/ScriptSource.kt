package com.neoutils.engine.bundle.scripting

import java.io.File

/**
 * Abstracts the origin of script source files for a bundle. The
 * `relativePath` is the exact string used as `type` in the scene JSON
 * (e.g. `scripts/foo.nengine.kts`) and is resolved either against the
 * JVM classpath or a directory on disk.
 */
internal sealed interface ScriptSource {

    fun read(relativePath: String): String

    data class Classpath(val bundleRoot: String) : ScriptSource {
        override fun read(relativePath: String): String {
            val fullPath = if (bundleRoot.isEmpty()) relativePath else "$bundleRoot/$relativePath"
            val resource = this::class.java.classLoader.getResource(fullPath)
                ?: throw IllegalArgumentException("Script not found on classpath: $fullPath")
            return resource.readText()
        }
    }

    data class Directory(val bundleDir: File) : ScriptSource {
        override fun read(relativePath: String): String {
            val file = File(bundleDir, relativePath)
            if (!file.isFile) {
                throw IllegalArgumentException("Script not found on disk: ${file.absolutePath}")
            }
            return file.readText()
        }
    }
}
