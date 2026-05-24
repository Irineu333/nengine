"""Stubs for engine physics types: BoxCollider."""

from engine.math import Vec2, Rect
from engine.scene import Node2D


class BoxCollider(Node2D):
    """Axis-aligned box collider node.

    Scripts that implement collision-driven behaviour extend BoxCollider::

        # extends BoxCollider

    BoxCollider is also frequently created as a child node inside _ready::

        self._collider = BoxCollider(size=Vec2(10.0, 10.0))
        self.add_child(self._collider)
    """

    size: Vec2

    def __init__(self, size: Vec2 = ...) -> None: ...

    def bounds(self) -> Rect:
        """Axis-aligned bounding rect in world space. Kotlin: bounds()"""
        ...
