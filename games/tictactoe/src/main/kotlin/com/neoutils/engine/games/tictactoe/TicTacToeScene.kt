package com.neoutils.engine.games.tictactoe

import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.scene.Scene
import kotlinx.serialization.Serializable

@Serializable
class TicTacToeScene : Scene() {

    init {
        name = "TicTacToeScene"
        if (children.isEmpty()) {
            addChild(Board().apply { name = "board" })
            addChild(
                StatusText().apply {
                    name = "status"
                    size = STATUS_TEXT_SIZE
                    color = Color.WHITE
                    baselineY = STATUS_BASELINE_Y
                }
            )
        }
    }

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    override fun onUpdate(dt: Float) {
        val board = findChild("board") as? Board ?: return
        val status = findChild("status") as? StatusText ?: return
        status.text = statusFor(board)
    }

    private fun layout(width: Float, height: Float) {
        val board = findChild("board") as? Board ?: return
        val availableHeight = (height - STATUS_RESERVED).coerceAtLeast(0f)
        val side = minOf(width, availableHeight).coerceAtLeast(0f)
        board.cellSize = side / 3f
        val boardSide = board.cellSize * 3f
        val originX = (width - boardSide) / 2f
        val originY = STATUS_RESERVED + (availableHeight - boardSide) / 2f
        board.origin = Vec2(originX, originY)
    }

    companion object {
        const val STATUS_TEXT_SIZE: Float = 22f
        const val STATUS_RESERVED: Float = 60f
        const val STATUS_BASELINE_Y: Float = 16f
    }
}

internal fun statusFor(board: Board): String = when {
    board.winner != null -> "${board.winner} venceu — clique para jogar de novo"
    board.isDraw -> "Empate — clique para jogar de novo"
    else -> "Vez de ${board.currentPlayer}"
}
