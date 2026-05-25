package com.neoutils.engine.games.demos

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree

fun main() {
    SkikoHost().run(
        tree = SceneTree(root = DemoSwitcherRoot()),
        config = GameConfig(title = "engine-consistency demos", width = 800, height = 600),
    )
}
