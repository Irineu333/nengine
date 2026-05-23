"""Stubs for engine scene types: Node and Node2D."""

from __future__ import annotations
from typing import Optional, List, TYPE_CHECKING

from engine.math import Vec2
from engine.render import Renderer

if TYPE_CHECKING:
    from engine.scene import Scene


class Node:
    """Base class for all scene graph nodes.

    In Python scripts you never instantiate Node directly — the engine creates
    nodes from ``scene.json`` and attaches your script via the slot pattern.
    ``self`` inside your script hooks IS the Kotlin Node instance.
    """

    name: str
    parent: Optional["Node"]
    children: List["Node"]
    is_live: bool
    scene: Optional["Scene"]

    def add_child(self, child: "Node") -> None:
        """Kotlin: addChild(child)"""
        ...

    def remove_child(self, child: "Node") -> None:
        """Kotlin: removeChild(child)"""
        ...

    def root_scene(self) -> Optional["Scene"]:
        """Returns the owning Scene in O(1) when live. Kotlin: rootScene()"""
        ...

    def find_child(self, name: str) -> Optional["Node"]:
        """Single-level lookup of a direct child by name. Kotlin: findChild(name)"""
        ...

    # Lifecycle hooks — override these in your script, not here.
    def on_enter(self) -> None: ...
    def on_update(self, dt: float) -> None: ...
    def on_render(self, renderer: Renderer) -> None: ...
    def on_exit(self) -> None: ...


class Node2D(Node):
    """Node with 2D transform (position, rotation, scale).

    Most gameplay scripts extend this type::

        # extends Node2D
    """

    from engine.math import Vec2  # noqa: F811 — re-import inside class for stub clarity

    # transform is a Transform object; we describe its most-used fields inline.
    # Full Transform stub is omitted since scripts typically access .transform.position etc.

    def world_transform(self) -> object:
        """Composed world-space Transform. Kotlin: worldTransform()"""
        ...

    def world_position(self) -> Vec2:
        """Shortcut for worldTransform().position. Kotlin: worldPosition()"""
        ...


class Scene(Node2D):
    """Root of the scene graph. Obtain via node.root_scene()."""
    ...
