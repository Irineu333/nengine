package com.neoutils.engine.serialization

import com.neoutils.engine.scene.ScriptInstanceContract
import kotlinx.serialization.json.JsonElement

/**
 * Bridge between `SceneLoader` and a script host. Built by the loader's
 * `attachScript` callback (typically `BundleLoader`); used by the loader to
 * route `properties` keys that match a script export.
 *
 * - [instance] is stored in `Node.scriptInstance` so the engine can dispatch
 *   lifecycle hooks.
 * - [exportNames] is consulted (along with `@Inspect` names on the Node) to
 *   decide where each `properties` key lands.
 * - [applyExport] is invoked for each key routed as an export, receiving the
 *   raw `JsonElement` from the bag.
 */
data class ScriptAttachment(
    val instance: ScriptInstanceContract,
    val exportNames: Set<String>,
    val applyExport: (name: String, value: JsonElement) -> Unit,
)
