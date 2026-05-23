"""Stubs for engine math types: Vec2 and Rect."""

class Vec2:
    """2D float vector. Immutable (Kotlin data class)."""

    x: float
    y: float

    # Companion object constants (exposed as class attributes via GraalPy)
    ZERO: "Vec2"
    ONE: "Vec2"

    def __init__(self, x: float, y: float) -> None: ...

    # Kotlin operator overloads exposed as Python dunder methods
    def __add__(self, other: "Vec2") -> "Vec2": ...
    def __sub__(self, other: "Vec2") -> "Vec2": ...
    def __mul__(self, scalar: float) -> "Vec2": ...
    def __neg__(self) -> "Vec2": ...

    @property
    def length(self) -> float: ...

    @property
    def normalized(self) -> "Vec2": ...


class Rect:
    """Axis-aligned bounding rectangle. Immutable (Kotlin data class)."""

    origin: Vec2
    size: Vec2

    def __init__(self, origin: Vec2, size: Vec2) -> None: ...

    @property
    def left(self) -> float: ...

    @property
    def top(self) -> float: ...

    @property
    def right(self) -> float: ...

    @property
    def bottom(self) -> float: ...

    def intersects(self, other: "Rect") -> bool: ...
    def contains(self, point: Vec2) -> bool: ...
