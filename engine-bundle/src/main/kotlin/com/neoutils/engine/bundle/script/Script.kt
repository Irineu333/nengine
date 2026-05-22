package com.neoutils.engine.bundle.script

import com.neoutils.engine.scene.Node
import kotlin.reflect.KClass

interface Script {
    val path: String
    val extendsType: KClass<out Node>
    val exports: List<ExportedProperty>
}
