package com.neoutils.engine.games.demos

import com.neoutils.engine.dx.Log
import com.neoutils.engine.math.Rect
import com.neoutils.engine.math.Transform
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Button
import com.neoutils.engine.scene.CanvasLayer
import com.neoutils.engine.scene.ColorRect
import com.neoutils.engine.scene.Label
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Node2D
import com.neoutils.engine.scene.Panel

/**
 * Scene 7 — `ui-foundation` validator.
 *
 * Two `CanvasLayer`s prove z-order: the HUD at `layer = 0` sits beneath the
 * menu at `layer = 10`. The menu's three buttons exercise normal / hover /
 * pressed / disabled visuals (Settings is disabled at startup). Background is
 * a dark `ColorRect` in world-space so it is visually obvious that the UI
 * lives on a different transform stack.
 *
 * Clicking a button prints a recognizable string via [Log.i]. Validation
 * checklist:
 *
 *  - Buttons render at fixed screen positions even when the window resizes
 *    (HUD does not zoom with any Camera2D).
 *  - Settings (disabled) renders with the disabled color and never emits
 *    `pressed`; the click also is NOT consumed (passes through).
 *  - Start / Quit emit `pressed` exactly once per click cycle; the click is
 *    consumed (no gameplay node would see `wasMouseClicked` for that tick).
 *  - F1/F2/F3 toggle the auto-inserted DebugOverlayLayer as in scenes 1–6.
 */
class UiPlaygroundDemo : Node() {

    init { name = "UiPlayground" }

    override fun onEnter() {
        super.onEnter()
        if (children.isNotEmpty()) return

        // Dark background fills the entire surface in world-space. No Camera2D
        // → world coordinates ARE screen pixels here, so size = tree.size at
        // construction time (we read tree?.size lazily — but for the MVP we
        // just oversize and rely on the window clip).
        addChild(ColorRect().apply {
            name = "Background"
            transform = Transform(position = Vec2(0f, 0f))
            size = Vec2(4000f, 4000f)
            color = Color(0.07f, 0.07f, 0.10f, 1f)
        })

        // HUD layer (bottom-most UI).
        addChild(buildHud())
        // Menu layer (on top of HUD).
        addChild(buildMenu())
    }

    private fun buildHud(): CanvasLayer = CanvasLayer().apply {
        name = "Hud"
        layer = 0
        // Translucent panel as visual frame for the score/lives strip.
        addChild(Panel().apply {
            name = "HudBackdrop"
            transform = Transform(position = Vec2(10f, 540f))
            size = Vec2(200f, 50f)
            color = Color(0f, 0f, 0f, 0.5f)
        })
        addChild(Label().apply {
            name = "Score"
            transform = Transform(position = Vec2(20f, 558f))
            text = "Score: 0"
            size = 16f
            color = Color.WHITE
        })
        addChild(Label().apply {
            name = "Lives"
            transform = Transform(position = Vec2(110f, 558f))
            text = "Lives: 3"
            size = 16f
            color = Color.WHITE
        })
    }

    private fun buildMenu(): CanvasLayer = CanvasLayer().apply {
        name = "Menu"
        layer = 10
        // 800x600 window assumed; buttons centered horizontally (button width
        // 200 → x = 300) and stacked vertically.
        addChild(Button().apply {
            name = "Start"
            transform = Transform(position = Vec2(300f, 200f))
            size = Vec2(200f, 50f)
            text = "Start"
            pressed.connect { Log.i(TAG, "start clicked") }
        })
        addChild(Button().apply {
            name = "Settings"
            transform = Transform(position = Vec2(300f, 270f))
            size = Vec2(200f, 50f)
            text = "Settings (disabled)"
            disabled = true
            pressed.connect { Log.i(TAG, "settings clicked — should NOT print") }
        })
        addChild(Button().apply {
            name = "Quit"
            transform = Transform(position = Vec2(300f, 340f))
            size = Vec2(200f, 50f)
            text = "Quit"
            pressed.connect { Log.i(TAG, "quit clicked") }
        })
    }

    private companion object {
        const val TAG = "UiPlayground"
    }
}
