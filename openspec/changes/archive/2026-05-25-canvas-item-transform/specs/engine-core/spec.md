## MODIFIED Requirements

### Requirement: Renderer SPI

The engine SHALL define a `Renderer` interface used by `onDraw` hooks. The interface MUST cover the operations needed by the sample games in this change: clearing the surface, drawing filled and outlined rectangles, drawing filled and outlined circles, drawing line segments, drawing filled polygons via `drawPolygon(points: List<Vec2>, color: Color)`, drawing text, and measuring text. The interface MUST NOT expose types from `androidx.compose.*` or any backend-specific package. The interface MUST be implementable without reflection or service loaders. The `drawLine(from: Vec2, to: Vec2, thickness: Float, color: Color)` operation MUST draw a straight segment between the two points (interpreted under the current transform stack) with the given stroke thickness. The `drawPolygon(points: List<Vec2>, color: Color)` operation MUST fill the polygon described by the vertex list (interpreted under the current transform stack) as a closed path; the implementation MAY assume the polygon is simple (non-self-intersecting) and convex-or-concave-without-holes. The `measureText(text: String, size: Float): Vec2` operation MUST return the bounding box (`Vec2(width, height)`) that `drawText` would produce for the same text and size, allowing callers to align text without backend-specific measurement. The `Color` type used by the renderer MUST be annotated with `@Serializable` (kotlinx.serialization) so it can be embedded as a property value in serialized scene files.

The interface SHALL additionally expose a 2D affine transform stack via two operations:

```kotlin
fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2)
fun popTransform()
```

`pushTransform(translation, rotation, scale)` MUST push a new entry onto an internal LIFO stack representing the composition `translate(translation) ∘ rotate(rotation) ∘ scale(scale)` applied to all subsequent `draw*` calls until the matching `popTransform()`. `rotation` MUST be expressed in radians and applied around the new origin (post-translation). Pushes MUST nest (composition order is parent-then-child: a deeper push composes with the current top). `popTransform()` MUST restore the top to the previous entry and SHALL throw `IllegalStateException` if the stack is empty.

The stack state SHALL start as identity at every backend-defined frame boundary (e.g. when `SkikoRenderer.bind()` runs or when a new `DrawScope` is entered in `ComposeRenderer`). Every `pushTransform` issued during a frame MUST be matched by a `popTransform` before the renderer's frame boundary ends; the engine MUST NOT rely on cross-frame stack state.

#### Scenario: Engine module has no Compose dependency

- **WHEN** the `:engine` module is compiled
- **THEN** its build classpath contains no `androidx.compose.*` artifact

#### Scenario: Renderer is consumed only via the interface

- **WHEN** any class in `:engine` references `Renderer`
- **THEN** it depends only on the `Renderer` interface, not on any concrete backend implementation

#### Scenario: drawLine accepts arbitrary endpoints

- **WHEN** a node calls `renderer.drawLine(Vec2(0f, 0f), Vec2(100f, 100f), thickness = 2f, color = Color.WHITE)`
- **THEN** the backend draws a diagonal stroke between the two points (under the current transform stack) with the requested thickness and color

#### Scenario: drawPolygon fills the polygon described by vertices

- **WHEN** a node calls `renderer.drawPolygon(listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(5f, 10f)), Color.WHITE)`
- **THEN** the backend renders a filled triangle covering those three vertices (under the current transform stack)
- **AND** subsequent calls with different vertex lists produce independent shapes (no state leakage)

#### Scenario: measureText reports the bounding box

- **WHEN** a node calls `renderer.measureText("hello", size = 22f)`
- **THEN** the result is a `Vec2` whose `x` is the rendered width and `y` is the rendered height of `drawText("hello", _, 22f, _)` in the same frame

#### Scenario: Color is serializable

- **WHEN** code serializes `Color(0.5f, 0.25f, 0.125f, 0.75f)` via `kotlinx.serialization` JSON
- **THEN** the resulting JSON document contains the four channel values
- **AND** deserializing yields a `Color` equal (by `equals`) to the original

#### Scenario: pushTransform translates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(100f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(100, 50)` with size `(10, 10)`

#### Scenario: pushTransform scales subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = 0f, scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the rendered rectangle appears at surface position `(0, 0)` with size `(20, 20)`

#### Scenario: pushTransform rotates subsequent draws

- **WHEN** code calls `renderer.pushTransform(translation = Vec2.ZERO, rotation = (PI / 2f).toFloat(), scale = Vec2(1f, 1f))` then `renderer.drawLine(from = Vec2(0f, 0f), to = Vec2(10f, 0f), thickness = 1f, color = Color.WHITE)` then `renderer.popTransform()`
- **THEN** the rendered line endpoint that was `(10, 0)` in local space appears at surface position approximately `(0, 10)` within floating-point tolerance

#### Scenario: pushTransform composes translate, rotate, and scale in order

- **WHEN** code calls `renderer.pushTransform(translation = Vec2(50f, 0f), rotation = (PI / 2f).toFloat(), scale = Vec2(2f, 2f))` then `renderer.drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, filled = true)` then `renderer.popTransform()`
- **THEN** the local origin `(0, 0)` maps to surface position `(50, 0)` (translation only)
- **AND** the local point `(10, 0)` maps to surface position approximately `(50, 20)` (scaled to `(20, 0)`, then rotated 90° around the new origin)

#### Scenario: popTransform restores the previous transform

- **WHEN** code calls `renderer.pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, draws a rect at `(0, 0)`, calls `renderer.popTransform()`, then draws another rect at `(0, 0)`
- **THEN** the first rect appears at surface position `(100, 0)`
- **AND** the second rect appears at surface position `(0, 0)`

#### Scenario: popTransform on empty stack fails fast

- **WHEN** code calls `renderer.popTransform()` without a preceding `pushTransform`
- **THEN** an `IllegalStateException` is raised naming the empty-stack precondition

#### Scenario: Transform stack starts as identity each frame

- **WHEN** a new frame begins on the backend (e.g. `SkikoRenderer.bind(canvas)` or a new `DrawScope` invocation)
- **THEN** a `drawRect(Rect(Vec2(0f, 0f), Vec2(10f, 10f)), Color.WHITE, true)` issued before any `pushTransform` renders at surface position `(0, 0)` with size `(10, 10)`

### Requirement: Visual primitive nodes

The engine SHALL provide concrete `Node2D` subclasses dedicated to common 2D visuals, each `@Serializable` with no-args public primary constructor and configuration via `@Inspect var` properties. Each visual primitive's `onDraw` SHALL operate in **local space** — the node's own coordinate frame, with origin at `(0, 0)` and no manual application of `world().position`, `world().rotation`, or `world().scale`. The world transform is supplied by `SceneTree.render` via `Renderer.pushTransform(node.transform.position, node.transform.rotation, node.transform.scale)` around each `Node2D`'s `onDraw` call (see "SceneTree.render applies Node2D local transform per draw").

- `ColorRect`: `size: Vec2`, `color: Color`. `onDraw` issues a filled `drawRect(Rect(Vec2.ZERO, size), color, filled = true)`.
- `Circle2D`: `radius: Float`, `color: Color`. `onDraw` issues a filled `drawCircle(Vec2.ZERO, radius, color, filled = true)`.
- `Line2D`: `points: List<Vec2>` (local-space), `thickness: Float`, `color: Color`. `onDraw` issues consecutive `drawLine(from = points[i-1], to = points[i], thickness, color)` calls between adjacent points, with NO world translation applied — ancestor rotation/scale now reach the line via the transform stack.
- `Polygon2D`: `points: List<Vec2>` (local-space), `color: Color`. `onDraw` issues `drawPolygon(points, color)` directly, with NO world translation applied.
- `Label`: `text: String`, `size: Float`, `color: Color`. `onDraw` issues `drawText(text, Vec2.ZERO, size, color)`. Alignment computations via `measureText` remain relative to the node's local origin.

All of these nodes SHALL inherit ancestor rotation and scale visually via the transform stack maintained by `SceneTree.render`. The previous limitation that "ancestor `rotation` is not applied visually" is REMOVED by this change.

#### Scenario: ColorRect renders a filled rectangle at local origin

- **WHEN** a `ColorRect` with `size = Vec2(40f, 20f)` and `color = Color.WHITE` is in a live scene at world position `Vec2(10f, 10f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(10f, 10f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawRect(Rect(Vec2.ZERO, Vec2(40f, 20f)), Color.WHITE, filled = true)`

#### Scenario: Circle2D renders a filled circle at local origin

- **WHEN** a `Circle2D` with `radius = 10f` and `color = Color.WHITE` is in a live scene at world position `Vec2(50f, 50f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(50f, 50f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawCircle(center = Vec2.ZERO, radius = 10f, color = Color.WHITE, filled = true)`

#### Scenario: Line2D renders consecutive segments in local space

- **WHEN** a `Line2D` with `points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(10f, 10f))` and `thickness = 2f` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues exactly two `drawLine` calls — one from `Vec2(0f, 0f)` to `Vec2(10f, 0f)`, and one from `Vec2(10f, 0f)` to `Vec2(10f, 10f)`

#### Scenario: Polygon2D renders a filled polygon in local space

- **WHEN** a `Polygon2D` with `points = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 20f))` and `color = Color.WHITE` is in a live scene at world position `Vec2(100f, 100f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(100f, 100f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawPolygon(points = listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(10f, 20f)), Color.WHITE)`

#### Scenario: Polygon2D inherits ancestor rotation visually

- **WHEN** a `Polygon2D` with `points = listOf(Vec2(0f, 0f), Vec2(10f, 0f), Vec2(0f, 10f))` is in a live scene at local position `Vec2(0f, 0f)` with local rotation `(PI / 2f).toFloat()` (no parent rotation)
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), (PI / 2f).toFloat(), Vec2(1f, 1f))` around the node's draw
- **AND** the local vertex `Vec2(10f, 0f)` therefore appears on the surface at approximately `Vec2(0f, 10f)` within floating-point tolerance (i.e. the polygon visually rotates 90°)

#### Scenario: Label renders text at local origin

- **WHEN** a `Label` with `text = "score"`, `size = 24f`, `color = Color.WHITE` is in a live scene at world position `Vec2(0f, 0f)`
- **THEN** `tree.render(renderer)` issues `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` around the node's draw
- **AND** the `onDraw` issues `drawText("score", Vec2(0f, 0f), 24f, Color.WHITE)` exactly once

### Requirement: Camera2D registers as the scene's current camera

When a `Camera2D` has `current = true` and is attached to a live `SceneTree`, the engine MUST make its `bounds` discoverable via `SceneTree.viewport`. The engine MUST resolve "the current camera" on read via pre-order tree-walk of the live tree from `root` picking the first `Camera2D` with `current = true`; the engine MAY cache this lookup but MUST invalidate on tree mutation or on `current` property changes.

`SceneTree.render(renderer)` SHALL consult the current camera at the start of the render traversal. When a current `Camera2D` exists with `bounds.size.x > 0f` and `bounds.size.y > 0f`, `SceneTree.render` MUST compute the view transform from `(camera.bounds, tree.size, camera.aspectMode)` and call `renderer.pushTransform(translation, rotation = 0f, scale)` BEFORE issuing any `_draw` walk, then call `renderer.popTransform()` AFTER the walk finishes (including via the `finally` of any traversal try/finally). When no current camera exists or its bounds are degenerate, `SceneTree.render` MUST NOT push the view transform — the `_draw` walk runs against identity at the view level (preserving the pre-change behavior of `pixels = world` for camera-less trees). The per-`Node2D` transform pushes defined by "SceneTree.render applies Node2D local transform per draw" SHALL still occur regardless of whether a current camera is present.

`SceneTree` SHALL additionally expose two coordinate-conversion conveniences:

```kotlin
fun screenToWorld(screenPosition: Vec2): Vec2
fun worldToScreen(worldPosition: Vec2): Vec2
```

Both methods MUST delegate to the current `Camera2D`'s `screenToWorld` / `worldToScreen`, passing `tree.size` as the surface size argument. When no current camera exists (or its bounds are degenerate), both methods MUST return the input unchanged (identity fallback) — the same condition under which `SceneTree.render` skips its view push, so nodes can read input pointer coordinates uniformly regardless of whether the tree has a camera.

#### Scenario: Toggling current updates viewport

- **GIVEN** a live `SceneTree` with one `Camera2D` whose `current = false`, and `tree.viewport` returns `Rect(Vec2.ZERO, tree.size)`
- **WHEN** code sets `camera.current = true`
- **THEN** `tree.viewport` next read returns `camera.bounds`

#### Scenario: SceneTree.render with current camera pushes a view transform

- **GIVEN** a live `SceneTree` of `size = Vec2(1280f, 900f)` containing a `Camera2D` with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `current = true`, `aspectMode = AspectMode.FIT`, and a single `ColorRect` of `size = Vec2(800f, 600f)` at world `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** the first call observed is `pushTransform(...)` mapping `bounds` onto the surface via FIT (with `rotation = 0f`)
- **AND** the last call observed is `popTransform()` (closing the view push)

#### Scenario: SceneTree.render without a current camera does not push a view transform

- **GIVEN** a live `SceneTree` with no `Camera2D` (or a `Camera2D` with `current = false`) and a single `ColorRect` at the root
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer` implementation
- **THEN** no view-level `pushTransform`/`popTransform` pair is observed at the traversal boundary
- **AND** the per-`Node2D` `pushTransform`/`popTransform` pair around the `ColorRect`'s draw IS observed

#### Scenario: SceneTree.render with degenerate camera bounds falls back to identity

- **GIVEN** a live `SceneTree` with a current `Camera2D` whose `bounds.size` has a zero or negative component
- **WHEN** `tree.render(renderer)` runs
- **THEN** no view-level `pushTransform`/`popTransform` pair is observed at the traversal boundary

#### Scenario: SceneTree.screenToWorld delegates to current camera

- **GIVEN** a live `SceneTree` with `size = Vec2(1280f, 900f)` and a current `Camera2D` whose `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `aspectMode = AspectMode.FIT`
- **WHEN** code calls `tree.screenToWorld(Vec2(640f, 450f))` (the surface center)
- **THEN** the result equals `Vec2(400f, 300f)` (the world center inside `bounds`)
- **AND** `tree.worldToScreen(Vec2(400f, 300f))` round-trips back to `Vec2(640f, 450f)`

#### Scenario: SceneTree.screenToWorld identity without current camera

- **GIVEN** a live `SceneTree` with no current `Camera2D` and `size = Vec2(800f, 600f)`
- **WHEN** code calls `tree.screenToWorld(Vec2(123f, 456f))` and `tree.worldToScreen(Vec2(123f, 456f))`
- **THEN** both calls return `Vec2(123f, 456f)` unchanged

## ADDED Requirements

### Requirement: SceneTree.render applies Node2D local transform per draw

`SceneTree.render(renderer)` SHALL wrap each `Node2D`'s `onDraw` call within a matched `Renderer.pushTransform` / `Renderer.popTransform` pair derived from the node's **local** `Transform` (i.e. `node.transform.position`, `node.transform.rotation`, `node.transform.scale`), so that drawing inside `onDraw` and inside descendant nodes' `onDraw` happens under the composed world transform via stack accumulation. Nodes that are NOT `Node2D` (e.g. `Timer`, the abstract `Node` base) SHALL NOT trigger any push/pop — they only forward to descendants. The push and pop MUST nest correctly around recursion into children: the parent's push remains on the stack for the duration of every descendant's `onDraw` call, and is popped only after all descendants have drawn. The implementation MUST use `try`/`finally` so a thrown exception inside `onDraw` or in a descendant still pops the stack.

When a current `Camera2D` exists with valid bounds, its view-transform push (see "Camera2D registers as the scene's current camera") SHALL be the outermost push, applied once before the walk; the per-`Node2D` pushes nest inside it.

#### Scenario: SceneTree.render pushes and pops local transform around each Node2D's draw

- **GIVEN** a live `SceneTree` whose `root` is a `ColorRect` named `R` with `transform.position = Vec2(50f, 50f)`, no children, and no current camera
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the recorded sequence contains exactly one `pushTransform(translation = Vec2(50f, 50f), rotation = 0f, scale = Vec2(1f, 1f))` and exactly one matching `popTransform()` enclosing R's draw call
- **AND** no other `pushTransform`/`popTransform` calls are observed for the frame

#### Scenario: Nested Node2D pushes compose via the transform stack

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` parent `P` with `transform.position = Vec2(100f, 0f)`, and `P` has one child `C` (a `ColorRect`) with `transform.position = Vec2(0f, 50f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the recorded sequence is: `pushTransform(Vec2(100f, 0f), 0f, Vec2(1f, 1f))`, `P.onDraw`, `pushTransform(Vec2(0f, 50f), 0f, Vec2(1f, 1f))`, `C.onDraw` issuing its `drawRect(...)`, `popTransform()`, `popTransform()`
- **AND** under that composed stack, C's `drawRect(Rect(Vec2.ZERO, ...))` appears on the surface at world position `Vec2(100f, 50f)`

#### Scenario: Non-Node2D nodes do not push a transform

- **GIVEN** a live `SceneTree` whose `root` is a `Node2D` `R`, and `R` has a child `T` which is a `Timer` (a non-`Node2D` `Node`)
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** exactly one `pushTransform`/`popTransform` pair appears (enclosing R's draw and subtree)
- **AND** no `pushTransform` is issued for `T`

#### Scenario: Per-Node2D push happens inside the camera view push

- **GIVEN** a live `SceneTree` of `size = Vec2(800f, 600f)` containing a current `Camera2D` with valid bounds, and a single `ColorRect` `R` at world position `Vec2(0f, 0f)`
- **WHEN** `tree.render(renderer)` runs against a recording `Renderer`
- **THEN** the recorded sequence is: `pushTransform(view, ...)` (camera), `pushTransform(Vec2(0f, 0f), 0f, Vec2(1f, 1f))` (R), `R.onDraw`, `popTransform()` (R), `popTransform()` (camera)

#### Scenario: Pop occurs even when onDraw throws

- **GIVEN** a `Node2D` subclass whose `onDraw` throws `RuntimeException` deterministically
- **WHEN** `tree.render(renderer)` runs and the exception propagates
- **THEN** the recording `Renderer` observes a matching `popTransform()` for every `pushTransform()` issued before the exception
- **AND** the renderer's transform stack is empty at the end of the frame
