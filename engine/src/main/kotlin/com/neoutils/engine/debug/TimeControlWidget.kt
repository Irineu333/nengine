package com.neoutils.engine.debug

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Border
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.Panel
import java.util.Locale

/**
 * Screen-space debug panel exposing the [com.neoutils.engine.tree.SceneTree]
 * time controls — pause/resume, single-step, and a speed-preset cycle. The
 * buttons mutate `tree.paused` / `tree.timeScale` / `tree.requestStep()`
 * directly and, because they live under the `ScreenDebugCanvas`, are driven
 * through `SceneTree.hitTestUI` — so they remain operable while paused (the
 * loop still runs `hitTestUI` and `process(0f)`).
 *
 * Keyboard shortcuts for the same actions are polled by the engine-internal
 * [TimeControlShortcutNode] under `process`, alive under pause for the same
 * reason. Mirrors `DebugHud`'s build-panel-on-enable lifecycle.
 */
class TimeControlWidget : ScreenDebugWidget() {

    override val title: String = "Time"

    private var panel: Panel? = null
    private var pauseButton: Button? = null
    private var speedButton: Button? = null
    private var lastEnabled: Boolean = false

    init { name = "TimeControlWidget" }

    override fun onProcess(dt: Float) {
        super.onProcess(dt)
        if (enabled != lastEnabled) {
            lastEnabled = enabled
            if (enabled) buildPanel() else tearDownPanel()
            return
        }
        if (!enabled) return
        refreshLabels()
        repositionPanel()
    }

    override fun drawDebug(renderer: Renderer) {
        // Panel + Buttons draw themselves through the scene-graph traversal;
        // this widget itself emits nothing.
    }

    private fun buildPanel() {
        val owningTree = tree ?: return
        val newPanel = Panel().apply {
            name = "TimeControlPanel"
            size = Vec2(PANEL_WIDTH, PANEL_HEADER + ROW_HEIGHT * 3 + ROW_GAP)
            color = PANEL_COLOR
            border = Border(color = PANEL_BORDER, width = 1f)
        }
        addChild(newPanel)
        panel = newPanel

        val pause = rowButton("TimeControlPause", 0, pauseLabel(owningTree.paused))
        pause.pressed.connect { tree?.let { it.paused = !it.paused } }
        newPanel.addChild(pause)
        pauseButton = pause

        val step = rowButton("TimeControlStep", 1, "Step")
        step.pressed.connect { tree?.requestStep() }
        newPanel.addChild(step)

        val speed = rowButton("TimeControlSpeed", 2, speedLabel(owningTree.timeScale))
        speed.pressed.connect { tree?.let { it.timeScale = cycleSpeed(it.timeScale) } }
        newPanel.addChild(speed)
        speedButton = speed

        repositionPanel()
    }

    private fun rowButton(buttonName: String, index: Int, label: String): Button =
        Button().apply {
            name = buttonName
            size = Vec2(PANEL_WIDTH - ROW_GAP * 2f, ROW_HEIGHT - ROW_GAP)
            position = Vec2(ROW_GAP, PANEL_HEADER + index * ROW_HEIGHT)
            text = label
            textSize = 13f
        }

    private fun tearDownPanel() {
        val current = panel ?: return
        removeChild(current)
        panel = null
        pauseButton = null
        speedButton = null
    }

    private fun refreshLabels() {
        val owningTree = tree ?: return
        pauseButton?.let {
            val expected = pauseLabel(owningTree.paused)
            if (it.text != expected) it.text = expected
        }
        speedButton?.let {
            val expected = speedLabel(owningTree.timeScale)
            if (it.text != expected) it.text = expected
        }
    }

    private fun repositionPanel() {
        val p = panel ?: return
        p.position = Vec2(PANEL_MARGIN, PANEL_MARGIN)
    }

    private fun pauseLabel(paused: Boolean): String = if (paused) "Resume" else "Pause"

    private fun speedLabel(scale: Float): String =
        "Speed: ${String.format(Locale.US, "%.2f", scale)}x"

    companion object {
        /** Speed-preset ring shared by the widget button and the shortcut node. */
        val SPEED_PRESETS: List<Float> = listOf(0.25f, 0.5f, 1f, 2f, 4f)

        /**
         * Next preset strictly greater than [current], wrapping to the first
         * once past the last. Stateless so the button and keyboard shortcut
         * cycle identically regardless of how `timeScale` was last set.
         */
        fun cycleSpeed(current: Float): Float =
            SPEED_PRESETS.firstOrNull { it > current + 1e-4f } ?: SPEED_PRESETS.first()

        private const val PANEL_WIDTH: Float = 140f
        private const val PANEL_MARGIN: Float = 12f
        private const val PANEL_HEADER: Float = 6f
        private const val ROW_HEIGHT: Float = 24f
        private const val ROW_GAP: Float = 4f
        private val PANEL_COLOR: Color = Color(0.10f, 0.10f, 0.12f, 0.85f)
        private val PANEL_BORDER: Color = Color(0.55f, 0.55f, 0.60f, 1f)
    }
}
