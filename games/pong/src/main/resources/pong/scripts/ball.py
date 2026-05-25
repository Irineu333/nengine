# extends BoxCollider

import math
import random as _random

ballSize: float = 16.0
initialSpeed: float = 280.0
maxSpeed: float = 560.0
speedupPerHit: float = 1.05
fieldCenter: Vec2 = Vec2(400.0, 300.0)

# Emitted with the side string ("Left" or "Right") when the ball touches a
# goal collider. The PongScene wires this signal up to the scoreboards.
scored: Signal = signal(str)


def _ready(self):
    self._velocity = Vec2(0.0, 0.0)
    self._scored_this_tick = False
    self.size = Vec2(self.ballSize, self.ballSize)
    if not getattr(self, '_initialized', False):
        _reset(self, 1.0 if _random.random() > 0.5 else -1.0)
        self._initialized = True


def _physics_process(self, dt):
    self._scored_this_tick = False
    self.size = Vec2(self.ballSize, self.ballSize)
    pos = self.position
    self.position = Vec2(pos.x + self._velocity.x * dt, pos.y + self._velocity.y * dt)


def _draw(self, renderer):
    wp = self.world().position
    center = Vec2(wp.x + self.ballSize / 2.0, wp.y + self.ballSize / 2.0)
    renderer.drawCircle(center, self.ballSize / 2.0, Color(1.0, 1.0, 1.0, 1.0), True, 1.0)


def _on_collide(self, other):
    if self._scored_this_tick:
        return
    name = other.name
    if name == "leftGoal":
        self.scored.emit("Right")
        _reset(self, 1.0)
        self._scored_this_tick = True
        return
    if name == "rightGoal":
        self.scored.emit("Left")
        _reset(self, -1.0)
        self._scored_this_tick = True
        return
    # Paddle hit: the collider lives one level under a Paddle (named "left" /
    # "right" in scene.json). Bouncing geometry uses the paddle bounds so the
    # angle reflects where on the paddle the ball struck.
    parent = other.parent
    if parent is not None and parent.name in ("left", "right"):
        paddle_bounds = other.bounds()
        paddle_center_y = paddle_bounds.top + paddle_bounds.size.y / 2.0
        ball_center_y = self.position.y + self.ballSize / 2.0
        rel = (ball_center_y - paddle_center_y) / (paddle_bounds.size.y / 2.0)
        if rel < -1.0:
            rel = -1.0
        elif rel > 1.0:
            rel = 1.0
        vlen = self._velocity.length
        new_speed = vlen * self.speedupPerHit
        if new_speed > self.maxSpeed:
            new_speed = self.maxSpeed
        h_sign = -1.0 if self._velocity.x > 0.0 else 1.0
        max_angle = math.pi / 3.0
        angle = rel * max_angle
        self._velocity = Vec2(h_sign * new_speed * math.cos(angle), new_speed * math.sin(angle))
        ball_pos = self.position
        ball_right = ball_pos.x + self.ballSize
        ball_left = ball_pos.x
        if h_sign < 0.0:
            shift = paddle_bounds.left - ball_right - 0.5
        else:
            shift = paddle_bounds.right - ball_left + 0.5
        self.position = Vec2(ball_pos.x + shift, ball_pos.y)
        return
    # Fall-through: walls (topWall / bottomWall) flip the vertical component.
    self._velocity = Vec2(self._velocity.x, -self._velocity.y)


def reset(self, serve_toward):
    _reset(self, serve_toward)


def _reset(self, serve_toward):
    self.size = Vec2(self.ballSize, self.ballSize)
    self.position = Vec2(
        self.fieldCenter.x - self.ballSize / 2.0,
        self.fieldCenter.y - self.ballSize / 2.0,
    )
    angle = (_random.random() - 0.5) * 1.4
    sx = 1.0 if serve_toward >= 0.0 else -1.0
    self._velocity = Vec2(
        sx * self.initialSpeed * math.cos(angle),
        self.initialSpeed * math.sin(angle),
    )
