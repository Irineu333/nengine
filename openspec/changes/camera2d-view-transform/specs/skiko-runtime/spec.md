## MODIFIED Requirements

### Requirement: Skiko-based Renderer implementation

The `:engine-skiko` module SHALL provide a concrete `Renderer` implementation, `SkikoRenderer`, that translates engine drawing calls into `org.jetbrains.skia.Canvas` operations. `SkikoRenderer` MUST implement every method declared by the `Renderer` SPI in `:engine`, including `drawLine`, `measureText`, `pushTransform`, and `popTransform`. `SkikoRenderer` MUST NOT expose `Canvas`, `Paint`, or any other Skia type through the `Renderer` interface surface. `measureText` MUST use Skia's `Font` + `TextLine` so the reported width and height match what `drawText` will actually rasterize in the same frame.

The renderer MUST follow the same `bind(canvas) / unbind()` pattern as `ComposeRenderer`, so a single instance can be reused across frames without allocations and the engine's `Renderer.required()` guarantee survives. Color conversion from engine `Color(r, g, b, a)` to packed ARGB `Int` MUST round each channel to its 8-bit representation.

`SkikoRenderer.pushTransform(translation, scale)` MUST issue `canvas.save()` then `canvas.translate(translation.x, translation.y)` then `canvas.scale(scale.x, scale.y)` so the cumulative transform composes with any previously pushed transform on the Skia canvas's own state stack. `SkikoRenderer.popTransform()` MUST issue `canvas.restore()`. The implementation MAY track a depth counter for `IllegalStateException` on empty-stack pop, but MUST otherwise delegate the stack to Skia's `save`/`restore` semantics so backend-native culling and clipping behave correctly. `unbind()` MUST be invoked with the transform stack empty (every `pushTransform` matched by a `popTransform`); if not, the implementation MAY raise `IllegalStateException` to surface the imbalance early.

#### Scenario: drawRect issues a Skia draw call

- **WHEN** `skikoRenderer.drawRect(rect, color, filled = true)` is called inside a bound frame
- **THEN** the underlying Skia `Canvas` receives a filled rectangle of matching position, size, and color

#### Scenario: drawLine issues a stroke

- **WHEN** `skikoRenderer.drawLine(Vec2(10f, 20f), Vec2(110f, 120f), thickness = 3f, color = Color.WHITE)` is called inside a bound frame
- **THEN** the underlying Skia `Canvas` receives a line segment between the two points with stroke width approximately 3 pixels and the requested color

#### Scenario: drawText renders text at the requested position

- **WHEN** `skikoRenderer.drawText("42", Vec2(100f, 50f), size = 24f, color)` is called inside a bound frame
- **THEN** the rendered output displays `"42"` near `(100, 50)` and approximate point size 24

#### Scenario: measureText matches drawText output

- **WHEN** `skikoRenderer.measureText("score", size = 18f)` is called and `skikoRenderer.drawText("score", position, 18f, color)` runs in the same frame
- **THEN** the returned `Vec2.x` equals the rendered glyph run's width
- **AND** the returned `Vec2.y` equals the rendered glyph run's height
- **AND** both values are measured by the same Skia `Font` used by `drawText`

#### Scenario: Using the renderer outside a bound frame fails fast

- **WHEN** any `Renderer` method is called on `skikoRenderer` without a prior `bind(canvas)`
- **THEN** an `IllegalStateException` is raised with a message that names the missing `bind()` precondition

#### Scenario: pushTransform translates and scales draws via Skia save/translate/scale

- **WHEN** `skikoRenderer.pushTransform(Vec2(100f, 50f), Vec2(2f, 2f))` is called and then `skikoRenderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` runs
- **THEN** the underlying Skia `Canvas` has received a `save()` followed by a `translate(100f, 50f)` and `scale(2f, 2f)` before the `drawRect`
- **AND** the rasterized rectangle occupies surface area equivalent to a rect at `(100, 50)` with size `(20, 20)`

#### Scenario: popTransform issues canvas.restore

- **WHEN** `skikoRenderer.popTransform()` is called after a matching `pushTransform`
- **THEN** the underlying Skia `Canvas` receives a `restore()` call
- **AND** subsequent draws use the transform that was active before the matching `pushTransform`
