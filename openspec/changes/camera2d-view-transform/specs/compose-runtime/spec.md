## MODIFIED Requirements

### Requirement: Compose-based Renderer implementation

The `:engine-compose` module SHALL provide a concrete `Renderer` implementation, `ComposeRenderer`, that translates engine drawing calls into Compose `DrawScope` operations. `ComposeRenderer` MUST implement every method declared by the `Renderer` SPI in `:engine`, including `drawLine`, `measureText`, `pushTransform`, and `popTransform`. `ComposeRenderer` MUST NOT expose `DrawScope` or any other Compose type through the `Renderer` interface surface. `measureText` MUST use Compose's `TextMeasurer` so the reported width and height match what `drawText` will actually rasterize.

`ComposeRenderer.pushTransform(translation, scale)` MUST apply the `translate` + `scale` to the underlying `DrawScope` (e.g. via the `DrawScope.translate(...)` / `DrawScope.scale(...)` builders, or by manipulating the active `DrawTransform`) so subsequent `draw*` calls render under the composed transform. `ComposeRenderer.popTransform()` MUST restore the transform that was active before the matching push, using `DrawScope`'s native save/restore semantics. The implementation MAY track a depth counter for `IllegalStateException` on empty-stack pop. The `DrawScope` lifecycle MUST end with an empty transform stack — every `pushTransform` matched by `popTransform` — so the next frame starts from a clean identity transform.

#### Scenario: drawRect issues a Compose draw call

- **WHEN** `composeRenderer.drawRect(rect, color, filled = true)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a filled rectangle of matching position, size, and color

#### Scenario: drawText renders text at the requested position

- **WHEN** `composeRenderer.drawText("42", Vec2(100f, 50f), size = 24f, color)` is called
- **THEN** the rendered output displays "42" with its baseline-anchored position near `(100, 50)` and approximate point size 24

#### Scenario: drawLine issues a Compose stroke

- **WHEN** `composeRenderer.drawLine(Vec2(10f, 20f), Vec2(110f, 120f), thickness = 3f, color = Color.WHITE)` is called inside a frame
- **THEN** the underlying `DrawScope` receives a line segment between the two points with stroke width approximately 3 pixels and the requested color

#### Scenario: measureText matches drawText output

- **WHEN** `composeRenderer.measureText("score", size = 18f)` is called and `composeRenderer.drawText("score", position, 18f, color)` runs in the same frame
- **THEN** the returned `Vec2` width and height equal the rendered glyph run's width and height in pixels (modulo subpixel rounding)

#### Scenario: pushTransform translates and scales draws via DrawScope transform

- **WHEN** `composeRenderer.pushTransform(Vec2(100f, 50f), Vec2(2f, 2f))` is called and then `composeRenderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` runs
- **THEN** the underlying `DrawScope` renders the rect under the composed transform
- **AND** the rasterized rectangle occupies surface area equivalent to a rect at `(100, 50)` with size `(20, 20)`

#### Scenario: popTransform restores the previous DrawScope transform

- **WHEN** `composeRenderer.popTransform()` is called after a matching `pushTransform`
- **THEN** the `DrawScope` returns to the transform that was active before the matching `pushTransform`
- **AND** subsequent draws use that previous transform
