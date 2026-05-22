package com.neoutils.engine.bundle.script

import kotlin.reflect.KClass

data class ExportedProperty(
    val name: String,
    val type: KClass<*>,
    val default: Any?,
    val nullable: Boolean = false,
)
