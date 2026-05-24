package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.Node
import kotlin.reflect.KClass

interface Script {
    val path: String
    val extendsType: KClass<out Node>
    val exports: List<ExportedProperty>

    /**
     * Top-level signal declarations discovered statically (e.g. AST scan for
     * `name: Signal = signal(type)` in Python). Keyed by signal name.
     */
    val signals: Map<String, SignalDeclaration> get() = emptyMap()
}

/**
 * Static descriptor of a signal declared in a script. Carries only the name
 * for now; future extensions may track the declared payload type for
 * inspector tooling.
 */
data class SignalDeclaration(val name: String)
