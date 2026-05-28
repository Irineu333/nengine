package com.neoutils.engine.dx

/**
 * Process-wide log configuration. Other debug flags (FPS overlay, collider
 * outlines, momentum overlay, current FPS reading) moved to
 * `SceneTree.debug` so they are per-tree and read by the auto-inserted
 * `DebugOverlayLayer` in the scene graph — `GameHost.render` no longer
 * draws overlays directly.
 */
object Debug {
    val log: LogConfig = LogConfig()
}
