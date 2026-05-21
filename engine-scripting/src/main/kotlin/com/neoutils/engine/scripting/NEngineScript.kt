package com.neoutils.engine.scripting

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

@KotlinScript(
    displayName = "NEngine Script",
    fileExtension = "nengine.kts",
    compilationConfiguration = NEngineScriptCompilationConfiguration::class
)
abstract class NEngineScript

object NEngineScriptCompilationConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "com.neoutils.engine.scene.*",
        "com.neoutils.engine.math.*",
        "com.neoutils.engine.render.*",
        "com.neoutils.engine.input.*",
        "com.neoutils.engine.serialization.*",
        "com.neoutils.engine.physics.*"
    )
    compilerOptions.append("-jvm-target=21")
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})
