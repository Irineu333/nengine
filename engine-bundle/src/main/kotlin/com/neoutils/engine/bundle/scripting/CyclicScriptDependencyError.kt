package com.neoutils.engine.bundle.scripting

/**
 * Raised by [KotlinScriptingHost.compileAll] when the round-robin / fixed-point
 * loop cannot make further progress: every remaining script still references
 * symbols published by other scripts that are themselves still pending.
 */
internal class CyclicScriptDependencyError(
    val paths: Set<String>
) : RuntimeException("Cyclic script dependency detected; cannot resolve: ${paths.sorted().joinToString(", ")}")
