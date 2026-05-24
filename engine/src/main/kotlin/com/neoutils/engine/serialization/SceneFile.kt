package com.neoutils.engine.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Single node serialized to JSON. */
@Serializable
data class NodeEntry(
    val type: String,
    val name: String,
    val properties: JsonObject,
    val children: List<NodeEntry> = emptyList(),
    val script: String? = null,
)

/** Top-level wrapper around the root node entry. `version` lets future
 *  changes break the format without silently corrupting older files. */
@Serializable
data class SceneFile(
    val version: Int = 2,
    val root: NodeEntry,
)
