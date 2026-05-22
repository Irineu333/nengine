package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.ScriptInstanceContract

interface ScriptInstance : ScriptInstanceContract {
    fun setExport(name: String, value: Any?)
}
