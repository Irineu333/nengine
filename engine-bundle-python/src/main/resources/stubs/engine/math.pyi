"""Stubs for engine math types: Vec2 and Rect."""

class Vec2:
    """2D float vector. Immutable (Kotlin ``data class`` with ``val`` fields).

    Individual components cannot be reassigned — ``v.y = 5.0`` raises
    ``AttributeError`` at runtime because ``Vec2.y`` has no setter on the
    Kotlin side. To "change" a component, build a new ``Vec2``::

        v = Vec2(v.x, 5.0)

    Combined with :class:`engine.scene.Node2D`'s ``position`` / ``rotation`` /
    ``scale`` sugar, the idiomatic write is::

        self.position = Vec2(self.position.x, new_y)
    """

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


class Transform:
    """Local or world transform: position + scale + rotation (radians).
    Immutable (Kotlin ``data class``). Use ``copy(position=...)`` to derive a
    new instance with one field changed; on :class:`engine.scene.Node2D` you
    can also write through the ``position`` / ``rotation`` / ``scale`` sugar
    properties, which call ``copy(...)`` under the hood."""

    position: Vec2
    scale: Vec2
    rotation: float

    def __init__(
        self,
        position: Vec2 = ...,
        scale: Vec2 = ...,
        rotation: float = 0.0,
    ) -> None: ...

    def compose(self, child: "Transform") -> "Transform":
        """Returns the world transform of ``child`` expressed in this
        transform's frame. Scales component-wise, sums rotations, and
        rotates+scales the child's local position before adding it to this
        transform's position."""
        ...
