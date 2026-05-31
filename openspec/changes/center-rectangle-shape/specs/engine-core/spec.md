## MODIFIED Requirements

### Requirement: CollisionShape2D holds a Shape2D resource

The engine SHALL provide a `CollisionShape2D : Node2D` class with `@Inspect var shape: Shape2D? = null` and `@Inspect var disabled: Boolean = false`. `CollisionShape2D` MUST be `@Serializable` and instantiable with no args. The `Shape2D` type MUST be a `@Serializable sealed class` with at least two concrete subtypes: `RectangleShape2D(@Inspect var size: Vec2 = Vec2(10f, 10f))` and `CircleShape2D(@Inspect var radius: Float = 5f)`. `Shape2D` MUST expose a method `bounds(world: Transform, localOffset: Vec2): Rect` returning the axis-aligned bounding box in world space.

`RectangleShape2D` SHALL be **centered on its local origin**: its extent is `[-size/2, +size/2]` in the local frame, so under a world transform it occupies `[world.position - size/2·scale, world.position + size/2·scale]` (before rotation). This matches `CircleShape2D` (centered on `radius`) and `Node2D.localBounds()` (`Rect(-size/2, size)`), so for any `RectangleShape2D` a `CollisionShape2D`'s inherited `worldBounds()` and its `broadPhaseBounds()` agree. The local origin is the geometric center, NOT a corner.

`CollisionShape2D` is meaningful only as a direct child of a `CollisionObject2D`; placing one elsewhere SHALL NOT crash but SHALL be ignored by `PhysicsSystem`.

#### Scenario: CollisionShape2D defaults

- **WHEN** code evaluates `CollisionShape2D()`
- **THEN** `shape` is `null`, `disabled` is `false`

#### Scenario: RectangleShape2D is centered on its local origin

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10f, 6f)`
- **WHEN** its local extent is evaluated at identity transform
- **THEN** it spans `[-5f, 5f]` on x and `[-3f, 3f]` on y (centered), NOT `[0f, 10f] × [0f, 6f]`

#### Scenario: RectangleShape2D bounds reflect transform scale

- **GIVEN** a `RectangleShape2D` with `size = Vec2(10f, 20f)`
- **WHEN** `bounds(Transform(position = Vec2(50f, 50f), scale = Vec2(2f, 2f), rotation = 0f), Vec2.ZERO)` is computed
- **THEN** the resulting `Rect` has `origin = Vec2(40f, 30f)` and `size = Vec2(20f, 40f)` — the AABB is centered on `world.position`, with half-extents `Vec2(10f, 20f)`

#### Scenario: CircleShape2D bounds are square

- **GIVEN** a `CircleShape2D` with `radius = 10f`
- **WHEN** `bounds(Transform(position = Vec2(0f, 0f), scale = Vec2(1f, 1f), rotation = 0f), Vec2.ZERO)` is computed
- **THEN** the resulting `Rect` has `size.x == 20f` and `size.y == 20f`

#### Scenario: Shape2D supports polymorphic serialization

- **WHEN** code round-trips a `RectangleShape2D(size = Vec2(8f, 4f))` through `kotlinx.serialization` JSON
- **THEN** the JSON contains a polymorphic discriminator identifying the subtype
- **AND** deserialization produces an instance whose `size` equals `Vec2(8f, 4f)`

### Requirement: Rectangle-rectangle overlap is exact under rotation

The pure function `overlap(a: Shape2D, aWorld: Transform, b: Shape2D, bWorld: Transform): Boolean` defined in `com.neoutils.engine.physics` MUST return `true` if and only if the two oriented rectangles described by `(a, aWorld)` and `(b, bWorld)` geometrically intersect in world space, when both `a` and `b` are `RectangleShape2D` and at least one of `aWorld.rotation` or `bWorld.rotation` is non-zero.

When **both** rotations are exactly `0f`, the implementation MAY take a faster axis-aligned path (intersecting `bounds()` AABBs) — that path is equivalent to the rotated test for axis-aligned inputs.

The exact test MUST be implemented via the Separating Axis Theorem on the four candidate axes (two per OBB, perpendicular to their sides). Both rectangles are **centered on their local origin** (see "CollisionShape2D holds a Shape2D resource"), so each OBB's half-extent along its own edge normal is `size/2·scale` (the apothem). `RectangleShape2D.bounds(world, localOffset)` continues to return the axis-aligned envelope of the rotated corners — this requirement does NOT change the `bounds()` contract; it only changes the `overlap()` semantics for the rect-rect rotated case.

`PhysicsSystem` MAY continue to use `bounds()` AABB intersection as a cheap broad-phase rejection step before calling `overlap()`.

#### Scenario: Two rotated rectangles whose AABB envelopes overlap but whose OBBs do not are reported as not overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(15f, 15f), rotation = π/4)`
- **AND** because both squares are centered, along their shared edge normal `(1, 1)/√2` the center separation `~21.21` exceeds the combined apothems `10 + 10 = 20`, so the OBBs are separated
- **AND** the AABB envelopes (each `~28.28 × 28.28`, A spanning `[-14.14, 14.14]²` and B `[0.86, 29.14]²`) still overlap on the rectangle `[0.86, 14.14] × [0.86, 14.14]`
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `false`

#### Scenario: Two rotated rectangles whose OBBs touch are reported as overlapping

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(0f, 0f), rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(position = Vec2(10f, 10f), rotation = π/4)`
- **AND** the rotated rectangles geometrically overlap (center separation `~14.14` is below the combined apothems `20`)
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `true`

#### Scenario: Two axis-aligned rectangles preserve the existing AABB behavior

- **GIVEN** `RectangleShape2D` A with `size = Vec2(10f, 10f)` at `world = Transform(position = Vec2(0f, 0f), rotation = 0f)`
- **AND** `RectangleShape2D` B with `size = Vec2(10f, 10f)` at `world = Transform(position = Vec2(5f, 5f), rotation = 0f)`
- **WHEN** `overlap(A, aWorld, B, bWorld)` is computed
- **THEN** the result is `true` (A spans `[-5, 5]²`, B spans `[0, 10]²` — they intersect)
- **AND** when B is moved to `Vec2(100f, 100f)` the result is `false`

#### Scenario: One rectangle rotated, the other axis-aligned uses the OBB path

- **GIVEN** `RectangleShape2D` A with `size = Vec2(20f, 20f)` at `world = Transform(rotation = π/4)`
- **AND** `RectangleShape2D` B with `size = Vec2(20f, 20f)` at `world = Transform(rotation = 0f)`
- **WHEN** their positions place the OBBs apart but their AABB envelopes overlap
- **THEN** `overlap(A, aWorld, B, bWorld)` returns `false`
