package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.Node

interface ScriptHost {
    val extension: String
    fun load(path: String, bundle: BundleSource): Script
    fun attach(node: Node, script: Script): ScriptInstance
}
