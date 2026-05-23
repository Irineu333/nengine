"""Stubs for engine serialization types: NodeRef."""

from __future__ import annotations
from typing import Optional, TypeVar, Generic, TYPE_CHECKING

if TYPE_CHECKING:
    from engine.scene import Node

_T = TypeVar("_T", bound="Node")


class NodeRef(Generic[_T]):
    """Typed, path-based reference to another node in the scene graph.

    The path is relative to the resolving caller:
    - ``""``  →  the caller itself
    - ``".."`` →  the parent node
    - ``"../ball"`` →  sibling named ``ball``
    - ``"paddle/collider"`` →  a grandchild

    Declare as a top-level annotated assignment to expose it as an export::

        # extends Node2D
        target: NodeRef = NodeRef("")   # resolves to any Node by default

    Resolve at runtime inside on_update::

        node = self.target.resolve(self)
        if node is not None:
            ...
    """

    path: str

    def __init__(self, path: str = "") -> None: ...

    def resolve(self, from_: "Node") -> Optional[_T]:
        """Walk the path starting from *from_* and return the target, or None.

        The result is cached until the bearer node re-attaches to the live tree.
        Kotlin: resolve(from)
        """
        ...

    def invalidate(self) -> None:
        """Drop any cached resolution. Kotlin: invalidate()"""
        ...
