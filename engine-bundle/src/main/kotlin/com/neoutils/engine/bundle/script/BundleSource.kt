package com.neoutils.engine.bundle.script

/**
 * Abstracts reading script source files from a bundle so [ScriptHost]
 * implementations do not couple to classpath or filesystem directly.
 */
interface BundleSource {
    fun read(path: String): String
    fun exists(path: String): Boolean
}
