package com.neoutils.engine.debug

import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Renderer

/**
 * Top-left screen-space FPS readout. Owns its `FpsCounter` and samples
 * `System.nanoTime()` each `onProcess` call — no host involvement, no
 * shared state. Defaults to `enabled = false`; user enables via the HUD.
 *
 * Draws a `DebugTheme` panel at the `DebugDock`-assigned `dockOrigin`.
 */
class FpsWidget : ScreenDebugWidget() {

    override val title: String = "FPS"

    override val slot: DockSlot = DockSlot.TOP_LEFT

    private val counter: FpsCounter = FpsCounter()

    init { name = "FpsWidget" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (enabled) counter.record(System.nanoTime())
    }

    override fun contentSize(): Vec2 = Vec2(
        WIDTH,
        DebugTheme.padding * 2f + DebugTheme.titleTextSize,
    )

    override fun drawDebug(renderer: Renderer) {
        val size = contentSize()
        drawPanelChrome(renderer, dockOrigin, size)
        renderer.drawText(
            text = "fps ${counter.current.toInt()}",
            position = Vec2(dockOrigin.x + DebugTheme.padding, dockOrigin.y + DebugTheme.padding),
            size = DebugTheme.titleTextSize,
            color = DebugTheme.textColor,
        )
    }

    companion object {
        private const val WIDTH: Float = 88f
    }
}

/** Draws the shared `DebugTheme` panel background + border at [origin]/[size]. */
internal fun drawPanelChrome(renderer: Renderer, origin: Vec2, size: Vec2) {
    val rect = Rect(origin, size)
    renderer.drawRect(rect, DebugTheme.panelBackground, filled = true)
    renderer.drawRect(rect, DebugTheme.panelBorderColor, filled = false)
}
