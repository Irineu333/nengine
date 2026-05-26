## MODIFIED Requirements

### Requirement: Demos module ships a rotated-sweep visualization scene

The `:games:demos` module SHALL include a scene that exercises rotated swept collision visually in runtime (currently `TumblingSwarmDemo`, selectable via the `DemoSwitcherRoot` digit key). The scene MUST host multiple bodies with non-zero `transform.rotation` (so every pair sweep routes through the rotated path, not the axis-aligned fast paths), arranged inside walls built from `StaticBody2D`.

Starting with the `add-rigid-body-2d` change, the bodies in this demo MUST be `RigidBody2D` instances (not `CharacterBody2D`), and the demo MUST NOT carry inline impulse math (no `resolveSquareSquare`, no `resolveSquareWall`, no `leadingOffset` helper). Linear AND angular response (including Coulomb friction tangential impulse) MUST be produced by the engine's solver from each body's `mass`, `inertia`, `restitution`, and `friction` configuration. The squares MUST have `restitution = 1f` (perfectly elastic — preserves the visible KE of the original demo) and `friction > 0f` (preserves the rolling-on-walls behavior of the original demo). Initial `linearVelocity` and `angularVelocity` MUST be set directly on the body (not on private `vx`/`vy`/`angularVel` fields).

Similarly, `CollisionStressDemo` MUST host `RigidBody2D` balls. Each ball MUST set `restitution = 1f` and `friction = 0f` to preserve the visible elastic-bouncing behavior of the original demo. The demo MUST NOT call `moveAndCollide` or apply `vel.reflect(normal)` in `onPhysicsProcess` — the engine integrates and resolves.

#### Scenario: Tumbling squares are RigidBody2D after the change

- **WHEN** the `:games:demos` source tree is inspected after the `add-rigid-body-2d` change
- **THEN** `TumblingSquare` extends `RigidBody2D`, not `CharacterBody2D`
- **AND** `TumblingSwarmDemo.kt` no longer contains a `resolveSquareSquare`, `resolveSquareWall`, or `leadingOffset` helper
- **AND** `TumblingSquare.onPhysicsProcess` either does not exist or contains no contact-resolution math

#### Scenario: Stress balls are RigidBody2D with restitution 1

- **WHEN** the `:games:demos` source tree is inspected after the `add-rigid-body-2d` change
- **THEN** `Ball` extends `RigidBody2D`, not `CharacterBody2D`
- **AND** `Ball`'s constructor or `init` sets `restitution = 1f` and `friction = 0f`
- **AND** `Ball.onPhysicsProcess` either does not exist or does not call `moveAndCollide`

#### Scenario: A rotated body's corner hit against a wall induces spin (engine-resolved)

- **GIVEN** a `RigidBody2D` square at rotation `π / 6` moving frontally into a `StaticBody2D` wall (so contact is along an OBB corner, not a flat face)
- **WHEN** the engine's physics tick processes the collision
- **THEN** the body's `angularVelocity` after the collision differs from before by a non-zero amount (lever arm of the contact corner relative to the body's center is non-zero; the impulse produces angular change)

#### Scenario: A rotated body sliding along a wall picks up rolling (engine-resolved)

- **GIVEN** a `RigidBody2D` square with `friction > 0f` in tangential motion along a wall (velocity component parallel to the wall is non-zero at the contact)
- **WHEN** the engine's solver applies the Coulomb-friction tangential impulse
- **THEN** the body's `angularVelocity` after the contact has changed in a sense consistent with rolling (sliding direction transfers to spin), bounded by the `μ * |jn|` cap

#### Scenario: Pair contact between two RigidBody2D squares conserves angular momentum locally

- **GIVEN** two `RigidBody2D` squares with `restitution = 1f`, `friction = 0f`, colliding in a glancing offset hit (the contact normal is not aligned with the line between centers)
- **WHEN** the engine's solver applies the elastic impulse with the real contact point from `SweepResult.point`
- **THEN** the angular impulse magnitudes applied to the two bodies are of comparable order (one body's lever arm does not dominate the other by an unbounded factor)
- **AND** the per-collision update preserves total linear + angular momentum and total kinetic energy within float precision (verifiable via `tree.totalLinearMomentum()`, `tree.totalAngularMomentum()`, `tree.totalKineticEnergy()`)

### Requirement: Shape2D supports sweepOverlap for axis-aligned shape pairs

The engine SHALL provide a top-level function `fun sweepOverlap(a: Shape2D, aWorld: Transform, motion: Vec2, b: Shape2D, bWorld: Transform): SweepResult?` in `com.neoutils.engine.physics`. `SweepResult` MUST be a data class with `toi: Float` (in `[0f, 1f]`), `point: Vec2`, `normal: Vec2`, and `depenetration: Vec2` (defaulting to `Vec2.ZERO`). The function MUST return a non-null result with the smallest valid TOI when shape `a` swept by `motion` from `aWorld` would intersect shape `b` at `bWorld`, for any of the following pairs: (a) `CircleShape2D` vs `CircleShape2D`; (b) `CircleShape2D` vs `RectangleShape2D` (rect axis-aligned OR rotated); (c) `RectangleShape2D` vs `RectangleShape2D` (any combination of rotations on both transforms).

When the pair is `RectangleShape2D` vs `RectangleShape2D` and at least one transform has `rotation != 0f`, the function MUST use a temporal SAT (Separating Axis Theorem with motion-projection per axis) on the four candidate axes (two normals per OBB). When the pair is `CircleShape2D` vs `RectangleShape2D` (in either order) with the rect rotated, the function MUST transform the problem into the rect's local frame (inverse-rotate circle position and motion), reuse the axis-aligned circle-vs-rect math, and rotate the resulting `point` and `normal` back into the original frame.

For shapes returning `null`, the call MUST not panic — it MUST simply indicate "no swept contact found in `[0, 1]`". Callers (e.g. `CharacterBody2D.moveAndCollide`, `RigidBody2D` solver) interpret `null` as "advance the full motion".

`SweepResult.point` MUST be the **geometric contact point** at the moment of contact in the same frame as `aWorld`/`bWorld`, with these refinements per pair:

- **Circle vs Circle**: the point lies on circle A's surface at the moment of contact along the contact normal.
- **Circle vs Rectangle**: the point is the closest-point on the rectangle's surface to the circle center at the moment of contact (clamping circle-local coordinates inside the rect's local frame and rotating back when the rect is rotated).
- **Rectangle vs Rectangle**: the point is the vertex of the swept (moving) rect that is most penetrated along `-normal` at the moment of contact; vertices tied within a small epsilon are averaged so face-vs-face contacts collapse to the face midpoint.

`SweepResult.point` MUST NOT be the geometric center of either shape (which was an acceptable approximation in the prior contract but is insufficient for angular contact resolution).

#### Scenario: Swept circle-vs-circle returns the analytic TOI

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(20f, 0f)`, circle B radius `5f` at `(12f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)`
- **THEN** the result's `toi` is approximately `0.1f` (i.e. A travels `2f` of the `20f` motion before contact)
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept circle-vs-rect axis-aligned returns the analytic TOI

- **GIVEN** circle A radius `3f` at `(0f, 0f)`, motion `(10f, 0f)`, rect B `size=(4f, 4f)` at `(8f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with both transforms at `rotation=0f`
- **THEN** the result's `toi` is approximately `0.5f`
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept rect-vs-rect axis-aligned returns the analytic TOI

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)`
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with both transforms at `rotation=0f`
- **THEN** the result's `toi` is approximately `0.3f`
- **AND** the result's `normal` is approximately `Vec2(-1f, 0f)`

#### Scenario: Swept shape returns null when motion does not intersect

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(10f, 0f)`, circle B radius `5f` at `(0f, 100f)`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns `null`

#### Scenario: Swept circle-vs-rotated-rect returns the analytic TOI in the rect's local frame

- **GIVEN** circle A radius `2f` at `(0f, 0f)`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)` with `bWorld.rotation = π / 2` (B rotated 90° — local x becomes world y)
- **AND** in B's local frame the relevant face of B that A approaches is the local-bottom face at world `(10f, 0f)`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns a non-null `SweepResult`
- **AND** the `normal` corresponds to B's local-bottom-face normal rotated back to world

#### Scenario: Swept rotated-rect-vs-rotated-rect returns the analytic TOI on aligned faces

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)` with `aWorld.rotation = π / 4`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(10f, 0f)` with `bWorld.rotation = π / 4` (same rotation)
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns a non-null `SweepResult`
- **AND** the `toi` is consistent with the geometric contact distance in the rotated frame

#### Scenario: Swept rotated-rect-vs-rotated-rect returns null when motion misses

- **GIVEN** rect A `size=(4f, 4f)` at `(0f, 0f)` with `aWorld.rotation = π / 4`, motion `(20f, 0f)`, rect B `size=(4f, 4f)` at `(0f, 100f)` with `bWorld.rotation = π / 4`
- **WHEN** code calls `sweepOverlap(...)`
- **THEN** the function returns `null`

#### Scenario: Starting-overlap on rotated pair returns toi=0 with depenetration

- **GIVEN** rect A `size=(10f, 10f)` at `(0f, 0f)` with `aWorld.rotation = π / 6` and rect B `size=(10f, 10f)` at `(5f, 0f)` with `bWorld.rotation = π / 6` (same rotation; deep overlap)
- **WHEN** code calls `sweepOverlap(a, aWorld, motion, b, bWorld)` with any `motion`
- **THEN** the function returns a non-null `SweepResult` with `toi == 0f`
- **AND** `depenetration` is a non-zero vector along the SAT axis of least overlap (the minimum-translation vector pointing from B toward A)

#### Scenario: SweepResult.point is on A's surface for circle-vs-circle, not at A's center

- **GIVEN** circle A radius `5f` at `(0f, 0f)`, motion `(20f, 0f)`, circle B radius `5f` at `(12f, 0f)`
- **WHEN** code calls `sweepOverlap(...)` and receives a non-null result
- **THEN** `result.point` lies on A's surface at the contact moment (its position is consistent with `centroA_at_contact + n * radiusA`)
- **AND** `result.point` is NOT equal to A's center at the contact moment (`(2f, 0f)`)

#### Scenario: SweepResult.point is the leading corner for rotated rect-vs-rect

- **GIVEN** rect A `size = (4f, 4f)` at `(0f, 0f)` with `rotation = π / 6` swept by motion `(20f, 0f)` against axis-aligned rect B in the path
- **WHEN** code calls `sweepOverlap(...)` and receives a non-null result
- **THEN** `result.point` corresponds to A's vertex with the smallest projection onto `n` (the leading corner — the vertex that penetrates first)
- **AND** `result.point` is NOT A's center
