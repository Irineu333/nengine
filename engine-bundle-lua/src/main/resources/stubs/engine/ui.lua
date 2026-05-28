---@meta

-- UI nodes shipped by the engine: CanvasLayer (screen-space scope), Panel
-- (rectangle widget with optional border), Button (pushable widget with
-- built-in `pressed` signal). All extend `Node`/`Node2D` and are accessible
-- as `nengine.CanvasLayer`, `nengine.Panel`, `nengine.Button`.

---@class CanvasLayer : Node
---@field layer integer
CanvasLayer = {}

---@class Border
---@field color Color
---@field width number
Border = {}

---@class Panel : Node2D
---@field size Vec2
---@field color Color
---@field border Border|nil
Panel = {}

---@class Button : Node2D
---@field size Vec2
---@field text string
---@field textSize number
---@field textColor Color
---@field normalColor Color
---@field hoverColor Color
---@field pressedColor Color
---@field disabledColor Color
---@field disabled boolean
---@field pressed Signal Built-in signal; emits exactly once per click cycle
Button = {}
