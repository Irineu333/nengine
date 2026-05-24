package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.ScriptInstanceContract

interface ScriptInstance : ScriptInstanceContract {
    fun setExport(name: String, value: Any?)

    /**
     * Reads the current value of the named export from the live script
     * instance, converted back to the Kotlin type declared in
     * [ExportedProperty.type]. Exists only to support `SceneLoader.save`
     * round-trip; the engine never invokes hooks through this path.
     *
     * Implementations MUST throw `IllegalArgumentException` (naming the
     * unknown `name` and the script path) when the name is not declared in
     * the owning `Script.exports`.
     */
    fun currentValue(name: String): Any?
}
