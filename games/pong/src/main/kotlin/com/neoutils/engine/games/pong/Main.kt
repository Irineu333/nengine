package com.neoutils.engine.games.pong

import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost

fun main() {
    SkikoHost().run(PongScene(), GameConfig(title = "Pong", width = 800, height = 600))
}
