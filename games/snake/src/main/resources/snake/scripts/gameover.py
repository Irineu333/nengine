# extends Label

# `Label` doesn't expose a `visible` flag today, so visibility is toggled via
# `color.a` (0.0 hidden, 1.0 visible) — kept as a single channel so the rest
# of the color survives across show/hide.


def _ready(self):
    # Start hidden regardless of what scene.json declared, so a future edit to
    # the JSON color cannot accidentally leak the label at boot.
    c = self.color
    self.color = Color(c.r, c.g, c.b, 0.0)
    self._centered = False

    # GameOverLabel now lives in screen-space under the Hud CanvasLayer,
    # so we center to `tree.size` directly. No Camera2D dependency.
    snake_node = self._node.parent.parent.findChild("Snake")
    snake = script_of(snake_node)
    snake.gameOver.connect(lambda _v: _show(self))
    snake.restart.connect(lambda _v: _hide(self))


def _draw(self, renderer):
    # Label parent already drew the text this frame; we piggyback on the first
    # _draw to obtain the renderer and reposition ourselves to the center of
    # the screen surface. From frame 2 onward the position is correct.
    if not self._centered:
        tree = self._node.tree
        if tree is None:
            return
        m = renderer.measureText(self.text, self.size)
        cx = tree.size.x / 2.0 - m.x / 2.0
        cy = tree.size.y / 2.0 - m.y / 2.0
        self.position = Vec2(cx, cy)
        self._centered = True


def _show(self):
    c = self.color
    self.color = Color(c.r, c.g, c.b, 1.0)


def _hide(self):
    c = self.color
    self.color = Color(c.r, c.g, c.b, 0.0)
