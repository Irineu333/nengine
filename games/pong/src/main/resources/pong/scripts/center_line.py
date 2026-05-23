# extends Node2D

x: float = 400.0
height: float = 600.0


def on_render(self, renderer):
    dash_height = 12.0
    gap = 8.0
    dash_color = Color(1.0, 1.0, 1.0, 0.3)
    y = 0.0
    while y < self.height:
        renderer.drawRect(
            Rect(Vec2(self.x - 1.0, y), Vec2(2.0, dash_height)),
            dash_color,
            True,
        )
        y += dash_height + gap
