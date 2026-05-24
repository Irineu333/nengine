# extends Scene

# Layout / orchestration script for the Pong root. Owns the resize-driven
# placement of walls, goals, paddles, scoreboard and ball, and wires the
# `scored` signal from ball.py into the leftScore / rightScore instances.

WALL_THICKNESS: float = 8.0
GOAL_THICKNESS: float = 8.0
PADDLE_MARGIN: float = 32.0
PADDLE_WIDTH: float = 16.0
PADDLE_HEIGHT: float = 96.0
SCORE_Y: float = 24.0
SCORE_OFFSET: float = 80.0


def _ready(self):
    self._last_w = -1.0
    self._last_h = -1.0
    _wire_scoring(self)


def _process(self, dt):
    # Scene.onResize isn't part of the ScriptHost hook contract, so the
    # scene's current size is polled here. Scene.size is kept current by the
    # runtime via Scene.resize() so the check is cheap.
    width = self._node.width
    height = self._node.height
    if width != self._last_w or height != self._last_h:
        self._last_w = width
        self._last_h = height
        if width > 0.0 and height > 0.0:
            _layout(self, width, height)


def _wire_scoring(self):
    ball_node = self._node.findChild("Ball")
    left_score_node = self._node.findChild("leftScore")
    right_score_node = self._node.findChild("rightScore")
    if ball_node is None:
        return
    ball = script_of(ball_node)
    left_score = script_of(left_score_node) if left_score_node is not None else None
    right_score = script_of(right_score_node) if right_score_node is not None else None
    if ball is None:
        return

    # Closures capture the scoreboards by ref; the bound method is plain
    # Python and GraalPy SAM-converts it for Signal.connect.
    def _on_scored(side):
        if side == "Left" and left_score is not None:
            left_score.increment()
        elif side == "Right" and right_score is not None:
            right_score.increment()

    ball.scored.connect(_on_scored)


def _layout(self, width, height):
    root = self._node
    top_wall = root.findChild("topWall")
    bottom_wall = root.findChild("bottomWall")
    left_goal = root.findChild("leftGoal")
    right_goal = root.findChild("rightGoal")
    left_paddle = root.findChild("left")
    right_paddle = root.findChild("right")
    ball_node = root.findChild("Ball")
    left_score_node = root.findChild("leftScore")
    right_score_node = root.findChild("rightScore")

    if top_wall is not None:
        top_wall.size = Vec2(width, WALL_THICKNESS)
        top_wall.transform = Transform(
            Vec2(0.0, 0.0), top_wall.transform.scale, top_wall.transform.rotation
        )
    if bottom_wall is not None:
        bottom_wall.size = Vec2(width, WALL_THICKNESS)
        bottom_wall.transform = Transform(
            Vec2(0.0, height - WALL_THICKNESS),
            bottom_wall.transform.scale,
            bottom_wall.transform.rotation,
        )
    if left_goal is not None:
        left_goal.size = Vec2(GOAL_THICKNESS, height)
        left_goal.transform = Transform(
            Vec2(-GOAL_THICKNESS, 0.0),
            left_goal.transform.scale,
            left_goal.transform.rotation,
        )
    if right_goal is not None:
        right_goal.size = Vec2(GOAL_THICKNESS, height)
        right_goal.transform = Transform(
            Vec2(width, 0.0), right_goal.transform.scale, right_goal.transform.rotation
        )

    if left_paddle is not None:
        left_paddle.transform = Transform(
            Vec2(PADDLE_MARGIN, height / 2.0 - PADDLE_HEIGHT / 2.0),
            left_paddle.transform.scale,
            left_paddle.transform.rotation,
        )
    if right_paddle is not None:
        right_paddle.transform = Transform(
            Vec2(width - PADDLE_MARGIN - PADDLE_WIDTH, height / 2.0 - PADDLE_HEIGHT / 2.0),
            right_paddle.transform.scale,
            right_paddle.transform.rotation,
        )

    if ball_node is not None:
        ball = script_of(ball_node)
        if ball is not None:
            ball.fieldCenter = Vec2(width / 2.0, height / 2.0)
            if root.isLive:
                serve_toward = 1.0 if ball._velocity.x >= 0.0 else -1.0
                ball.reset(serve_toward)

    if left_score_node is not None:
        left_score_node.transform = Transform(
            Vec2(width / 2.0 - SCORE_OFFSET, SCORE_Y),
            left_score_node.transform.scale,
            left_score_node.transform.rotation,
        )
    if right_score_node is not None:
        right_score_node.transform = Transform(
            Vec2(width / 2.0 + SCORE_OFFSET / 2.0, SCORE_Y),
            right_score_node.transform.scale,
            right_score_node.transform.rotation,
        )
