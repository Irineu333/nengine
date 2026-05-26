## MODIFIED Requirements

### Requirement: Physics primitives are Godot-style nodes

The engine SHALL provide collision support via a `CollisionObject2D` hierarchy rather than a single `Collider` class. The hierarchy MUST be:

```
CollisionObject2D (abstract, : Node2D)
├── Area2D                                    (trigger; does not block)
└── PhysicsBody2D (abstract)
    ├── StaticBody2D                          (solid, position moved by script)
    ├── CharacterBody2D                       (solid, exposes velocity slot)
    └── RigidBody2D                           (solid, engine-integrated dynamic body)
```

Every concrete subclass (`Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`) MUST be `@Serializable` and instantiable with a public no-args constructor. `CollisionObject2D` MUST expose `@Inspect var disabled: Boolean = false`. `CharacterBody2D` MUST expose `@Inspect var velocity: Vec2 = Vec2.ZERO`. The engine MUST NOT integrate `CharacterBody2D.velocity` automatically — integration is the script's responsibility (Godot-style).

`RigidBody2D` is integrated by the engine (see `rigid-body-2d` capability) and is the third path alongside Static (immovable) and Character (script-moved). The properties, integrator, impulse solver, and conservation diagnostics for `RigidBody2D` are specified in `rigid-body-2d`.

#### Scenario: Each collision class is instantiable with no args

- **WHEN** code evaluates `Area2D()`, `StaticBody2D()`, `CharacterBody2D()`, `RigidBody2D()`
- **THEN** each call returns a valid instance assignable to `CollisionObject2D`

#### Scenario: CharacterBody2D velocity slot exists and is mutable

- **WHEN** code creates `CharacterBody2D()` and sets `body.velocity = Vec2(100f, 0f)`
- **THEN** reading `body.velocity` returns `Vec2(100f, 0f)`
- **AND** the engine does NOT automatically integrate `transform.position` from `velocity` between ticks

#### Scenario: PhysicsBody2D is not directly instantiable

- **WHEN** code attempts to call `PhysicsBody2D()`
- **THEN** the compiler rejects the call (`abstract`)

#### Scenario: RigidBody2D is the third concrete PhysicsBody2D

- **WHEN** code inspects the `:engine` source tree
- **THEN** `RigidBody2D` exists alongside `StaticBody2D` and `CharacterBody2D` under `com.neoutils.engine.physics`
- **AND** `RigidBody2D` extends `PhysicsBody2D` (and transitively `CollisionObject2D`)

### Requirement: PhysicsSystem detects overlaps between CollisionObjects

The engine SHALL provide a `PhysicsSystem` whose `step(tree: SceneTree, dt: Float)` operation:

1. **Integrates and resolves RigidBody2D motion** (see `rigid-body-2d` capability): applies accumulated forces and gravity to `linearVelocity` / `angularVelocity`, runs the swept TOI loop with bilateral impulse resolution against other `PhysicsBody2D`, then commits `position` and `transform.rotation`. This stage produces no enter/exit signals — only motion.
2. Enumerates every `CollisionObject2D` with `disabled == false` in the live scene tree.
3. Collects each object's active `CollisionShape2D` children (those whose `shape != null` and `disabled == false`).
4. For every unordered pair `(A, B)` of objects (A ≠ B), tests whether **any** pair `(shapeA, shapeB)` overlaps. Overlap MUST be exact for axis-aligned cases (rect-rect AABB, circle-circle distance, rect-circle closest-point) and for rotated rect-rect pairs (SAT); other rotated combinations MAY be approximated by their AABB.
5. Maintains an internal `Set<UnorderedPair<CollisionObject2D>>` of currently overlapping pairs. Pairs new this step → dispatch enter. Pairs gone this step → dispatch exit.
6. Filters out pairs whose endpoints are no longer in the live scene before dispatching (cleanup of detached nodes).

The `step` method also exposes a public mutable `gravity: Vec2` property on `PhysicsSystem`, defaulting to `Vec2.ZERO`, consumed in stage 1.

Order: enter dispatches MUST run after exit dispatches within the same step. Both MUST run after the per-pair overlap test (no interleaving). Integration + impulse resolution (stage 1) MUST run before overlap detection (stages 2-6), so dispatched signals reflect post-resolution positions. The system MUST NOT crash if a hook removes a node from the scene mid-dispatch — mutation deferral applies.

#### Scenario: PhysicsSystem.step accepts dt and runs integration before dispatch

- **WHEN** the source of `PhysicsSystem.kt` is inspected
- **THEN** the public signature is `fun step(tree: SceneTree, dt: Float)`
- **AND** within `step`, the RigidBody2D integration + impulse resolution stage runs before `computeOverlapping` and the enter/exit dispatch

#### Scenario: PhysicsSystem.gravity defaults to zero

- **WHEN** code constructs a new `PhysicsSystem()` (or accesses the tree's `PhysicsSystem`)
- **THEN** `system.gravity` equals `Vec2.ZERO`
- **AND** the property is mutable (`var`)

#### Scenario: Detached nodes are removed from pair set

- **GIVEN** two `CollisionObject2D` A and B that were overlapping last step
- **WHEN** A is detached from the scene (via `parent.removeChild(A)`) before the next step
- **THEN** the next `step(tree, dt)` does NOT invoke `B.onBodyExited(A)` for A
- **AND** the pair (A, B) is no longer tracked

#### Scenario: Multiple shapes per object — overlap is union

- **GIVEN** a `CollisionObject2D` with two `CollisionShape2D` children at different positions
- **AND** another `CollisionObject2D` whose single shape overlaps only the second of the first object's shapes
- **WHEN** a physics step runs
- **THEN** the pair is treated as overlapping
- **AND** exactly one `*Entered` event is dispatched per side (not one per shape pair)

#### Scenario: Step runs after physicsProcess each fixed step

- **WHEN** `GameLoop.tick` runs one physics step (configured in `godot-style-foundation`)
- **THEN** `tree.physicsProcess(dt)` (dispatching `onPhysicsProcess` to every Node) runs before `physics.step(tree, dt)` in that step
- **AND** the engine drains pending mutations before each phase
