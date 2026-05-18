package com.neoutils.engine.games.pong

import com.neoutils.engine.input.Key
import com.neoutils.engine.math.Vec2
import com.neoutils.engine.render.Color
import com.neoutils.engine.render.Renderer
import com.neoutils.engine.scene.Node
import com.neoutils.engine.scene.Scene

class PongScene(
    defaultWidth: Float = 800f,
    defaultHeight: Float = 600f,
) : Scene() {

    val leftScore: Score = Score()
    val rightScore: Score = Score()

    val leftPaddle: Paddle = Paddle(
        playFieldHeight = defaultHeight,
        upKey = Key.W,
        downKey = Key.S,
    ).apply { name = "left" }

    val rightPaddle: Paddle = Paddle(
        playFieldHeight = defaultHeight,
        ai = true,
    ).apply { name = "right" }

    val ball: Ball = Ball(fieldCenter = Vec2(defaultWidth / 2f, defaultHeight / 2f)) { scorer ->
        when (scorer) {
            Goal.Side.Left -> leftScore.increment()
            Goal.Side.Right -> rightScore.increment()
        }
    }

    private val topWall = Wall(Vec2(defaultWidth, WALL_THICKNESS)).apply { name = "topWall" }
    private val bottomWall = Wall(Vec2(defaultWidth, WALL_THICKNESS)).apply { name = "bottomWall" }

    private val leftGoal = Goal(Goal.Side.Left, Vec2(GOAL_THICKNESS, defaultHeight)).apply {
        name = "leftGoal"
    }
    private val rightGoal = Goal(Goal.Side.Right, Vec2(GOAL_THICKNESS, defaultHeight)).apply {
        name = "rightGoal"
    }

    private val centerLine = CenterLine(x = defaultWidth / 2f, height = defaultHeight)

    init {
        name = "PongScene"
        addChild(centerLine)
        addChild(leftPaddle)
        addChild(rightPaddle)
        addChild(ball)
        addChild(topWall)
        addChild(bottomWall)
        addChild(leftGoal)
        addChild(rightGoal)
        addChild(leftScore)
        addChild(rightScore)

        rightPaddle.aiTargetY = { ball.transform.position.y + ball.size / 2f }
        layout(defaultWidth, defaultHeight)
    }

    override fun onResize(width: Float, height: Float) {
        layout(width, height)
    }

    private fun layout(width: Float, height: Float) {
        // Walls
        topWall.size = Vec2(width, WALL_THICKNESS)
        topWall.transform = topWall.transform.copy(position = Vec2(0f, 0f))
        bottomWall.size = Vec2(width, WALL_THICKNESS)
        bottomWall.transform = bottomWall.transform.copy(
            position = Vec2(0f, height - WALL_THICKNESS)
        )

        // Goals just past the visible play field
        leftGoal.size = Vec2(GOAL_THICKNESS, height)
        leftGoal.transform = leftGoal.transform.copy(position = Vec2(-GOAL_THICKNESS, 0f))
        rightGoal.size = Vec2(GOAL_THICKNESS, height)
        rightGoal.transform = rightGoal.transform.copy(position = Vec2(width, 0f))

        // Paddles
        leftPaddle.playFieldHeight = height
        leftPaddle.transform = leftPaddle.transform.copy(
            position = Vec2(PADDLE_MARGIN, height / 2f - Paddle.HEIGHT / 2f)
        )
        rightPaddle.playFieldHeight = height
        rightPaddle.transform = rightPaddle.transform.copy(
            position = Vec2(width - PADDLE_MARGIN - Paddle.WIDTH, height / 2f - Paddle.HEIGHT / 2f)
        )

        // Ball + center line
        ball.fieldCenter = Vec2(width / 2f, height / 2f)
        ball.reset(serveToward = if (ball.velocity.x >= 0f) 1f else -1f)
        centerLine.x = width / 2f
        centerLine.height = height

        // Scores: left of and right of the center line
        val scoreY = 24f
        val scoreOffset = 80f
        leftScore.transform = leftScore.transform.copy(
            position = Vec2(width / 2f - scoreOffset, scoreY)
        )
        rightScore.transform = rightScore.transform.copy(
            position = Vec2(width / 2f + scoreOffset / 2f, scoreY)
        )
    }

    companion object {
        const val WALL_THICKNESS: Float = 8f
        const val GOAL_THICKNESS: Float = 8f
        const val PADDLE_MARGIN: Float = 32f
    }
}

private class CenterLine(var x: Float, var height: Float) : Node() {
    override fun onRender(renderer: Renderer) {
        val dashHeight = 12f
        val gap = 8f
        val color = Color(1f, 1f, 1f, 0.3f)
        var y = 0f
        while (y < height) {
            renderer.drawRect(
                com.neoutils.engine.math.Rect(Vec2(x - 1f, y), Vec2(2f, dashHeight)),
                color,
                filled = true,
            )
            y += dashHeight + gap
        }
    }
}
