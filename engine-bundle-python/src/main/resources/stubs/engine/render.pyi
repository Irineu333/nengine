"""Stubs for engine render types: Color and Renderer."""

from engine.math import Vec2, Rect


class Color:
    """RGBA color with float components in [0.0, 1.0]. Immutable (Kotlin data class)."""

    r: float
    g: float
    b: float
    a: float

    # Companion object constants
    BLACK: "Color"
    WHITE: "Color"
    RED: "Color"
    GREEN: "Color"
    BLUE: "Color"
    GRAY: "Color"

    def __init__(self, r: float, g: float, b: float, a: float = 1.0) -> None: ...


class Renderer:
    """Drawing surface passed to _draw(self, renderer). All coordinates are in world space."""

    def clear(self, color: Color) -> None: ...

    def draw_rect(self, rect: Rect, color: Color, filled: bool = True) -> None:
        """Kotlin: drawRect(rect, color, filled)"""
        ...

    def draw_circle(
        self,
        center: Vec2,
        radius: float,
        color: Color,
        filled: bool = True,
        thickness: float = 1.0,
    ) -> None:
        """Kotlin: drawCircle(center, radius, color, filled, thickness)"""
        ...

    def draw_line(self, from_: Vec2, to: Vec2, thickness: float, color: Color) -> None:
        """Kotlin: drawLine(from, to, thickness, color)"""
        ...

    def draw_text(self, text: str, position: Vec2, size: float, color: Color) -> None:
        """Kotlin: drawText(text, position, size, color)"""
        ...

    def measure_text(self, text: str, size: float) -> Vec2:
        """Kotlin: measureText(text, size)"""
        ...
