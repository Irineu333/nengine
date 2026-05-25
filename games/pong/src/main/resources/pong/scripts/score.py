# extends Node2D

textSize: float = 48.0
color: Color = Color(1.0, 1.0, 1.0, 1.0)


def _ready(self):
    self._value = 0


def _draw(self, renderer):
    renderer.drawText(str(self._value), self.world().position, self.textSize, self.color)


def increment(self):
    self._value = self._value + 1
