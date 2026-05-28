package com.neoutils.engine.tree

/**
 * Per-`SceneTree` runtime state for the auto-inserted `DebugOverlayLayer`.
 * Hosts flip these flags in response to toggle keys
 * (`GameConfig.toggleFpsKey`/`toggleCollidersKey`/`toggleMomentumOverlayKey`)
 * and stream the current FPS into [currentFps] each frame. The
 * `DebugOverlayLayer` reads them every tick and shows or hides its widgets
 * accordingly.
 *
 * Not a `Node`, not `@Serializable`, never persists — pure runtime state.
 */
class DebugFlags {

    @Volatile var showFps: Boolean = false
    @Volatile var showColliders: Boolean = false
    @Volatile var showMomentum: Boolean = false

    /** Updated by the host each frame; read by `FpsLabel` inside `DebugOverlayLayer`. */
    @Volatile var currentFps: Float = 0f
}
