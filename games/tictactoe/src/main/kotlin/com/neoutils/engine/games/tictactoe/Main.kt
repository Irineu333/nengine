package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.bundle.BundleLoader
import com.neoutils.engine.bundle.lua.LuaScriptHost
import com.neoutils.engine.runtime.GameConfig
import com.neoutils.engine.skiko.SkikoHost
import com.neoutils.engine.tree.SceneTree

fun main() {
    val lua = LuaScriptHost.create()
    val root = BundleLoader.fromResources("tictactoe", scripting = lua)
    SkikoHost().run(
        tree = SceneTree(root = root),
        config = GameConfig(title = "Tic Tac Toe", width = 600, height = 600),
    )
}
