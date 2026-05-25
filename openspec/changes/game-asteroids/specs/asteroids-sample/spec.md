## ADDED Requirements

### Requirement: Asteroids is an executable standalone module

The project SHALL provide a Gradle module `:games:asteroids` that depends on `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python`, and `kotlinx-serialization`, with a `Main.kt` entry point that opens a desktop window hosting Asteroids via `SkikoHost`. The module MUST be executable via `./gradlew :games:asteroids:run`. The module MUST NOT depend on any other game module. The `Main.kt` SHALL construct a single `PythonScriptHost` via `PythonScriptHost.create()` and inject it into the `BundleLoader` via the `scripting` parameter, then load the scene via `BundleLoader.fromResources("asteroids", scripting = python)` and hand it to `SkikoHost().run(...)`. The body of `main` MUST be as concise as Pong's and Snake's — no manual `NodeRegistry` registration, no script manifest.

#### Scenario: Asteroids runs from Gradle

- **WHEN** a developer runs `./gradlew :games:asteroids:run` from the repository root
- **THEN** a desktop window opens displaying the Asteroids scene
- **AND** the game responds to keyboard input

#### Scenario: Asteroids uses only public engine API

- **WHEN** the `:games:asteroids` source tree is inspected
- **THEN** every engine interaction goes through public types exported by `:engine`, `:engine-skiko`, `:engine-bundle`, and `:engine-bundle-python`
- **AND** no internal/private API is referenced

#### Scenario: Asteroids bundle lives in resources

- **WHEN** the `:games:asteroids/src/main/resources/asteroids/` directory is inspected
- **THEN** it contains `scene.json` at the root
- **AND** a `scripts/` subdirectory containing the Python script files

#### Scenario: Main.kt is concise

- **WHEN** `:games:asteroids/src/main/kotlin/.../Main.kt` is inspected
- **THEN** `main` constructs `PythonScriptHost.create()`, calls `BundleLoader.fromResources("asteroids", scripting = python)`, and starts `SkikoHost()` — nothing more
- **AND** the file does not reference `NodeRegistry`, script manifest manipulation, or any manual type registration

### Requirement: Asteroids scene composition

The Asteroids scene SHALL contain: one `Camera2D` with `current = true` and `bounds = Rect(Vec2(0f, 0f), Vec2(800f, 600f))`; one `Node2D` named `GameRoot` whose script is `scripts/game.py` and whose children are the gameplay sub-roots and the ship; one `Node2D` named `AsteroidsRoot` (initially with zero children — populated at runtime by `game.py`); one `Node2D` named `BulletsRoot` (initially with zero children — populated at runtime by `ship.py`); one `CharacterBody2D` named `Ship` whose script is `scripts/ship.py`, containing a child `CollisionShape2D` carrying a `CircleShape2D` with `radius = 12f`, plus a sibling `Polygon2D` whose `points` define a triangular nose-up shape in local space (specifically `[Vec2(-10f, 8f), Vec2(0f, -12f), Vec2(10f, 8f)]`) and `color = Color.WHITE`; one `Label` named `ScoreLabel` (initial text `"Score: 0"`); one `Label` named `LivesLabel` (initial text `"Lives: 3"`); one `Label` named `GameOverLabel` (initial text including the restart hint, initially hidden). The scene file MUST declare `"version": 2`.

#### Scenario: Scene contains expected nodes after loading

- **WHEN** the scene is loaded via `BundleLoader.fromResources("asteroids", scripting = python)`
- **THEN** `scene.findChild("Camera2D")` resolves to a `Camera2D` with `current == true` and `bounds == Rect(Vec2(0f, 0f), Vec2(800f, 600f))`
- **AND** `scene.findChild("GameRoot")` resolves to a `Node2D` whose script is `scripts/game.py`
- **AND** `scene.findChild("AsteroidsRoot")` and `scene.findChild("BulletsRoot")` both resolve to `Node2D` containers
- **AND** `scene.findChild("Ship")` resolves to a `CharacterBody2D` whose script is `scripts/ship.py`
- **AND** the `Ship` has a `CollisionShape2D` child whose `shape` is a `CircleShape2D` with `radius == 12f`
- **AND** the `Ship` has a `Polygon2D` child with `points.size == 3`
- **AND** `scene.findChild("ScoreLabel")`, `scene.findChild("LivesLabel")`, `scene.findChild("GameOverLabel")` all resolve to `Label` nodes

#### Scenario: Bundle version is 2

- **WHEN** the JSON content of `scene.json` is parsed
- **THEN** the top-level key `"version"` is the integer `2`

### Requirement: Ship rotates and thrusts under continuous input

The script `ship.py` SHALL apply input in `_process(dt)`: while `Key.ArrowLeft` is held, decrement `self.rotation` by `TURN_RATE * dt`; while `Key.ArrowRight` is held, increment by `TURN_RATE * dt`; while `Key.ArrowUp` is held, increment an internal `self._velocity: Vec2` by `forward * (THRUST_ACCEL * dt)` where `forward = Vec2(sin(self.rotation), -cos(self.rotation))` (rotation 0 points up because the polygon's nose is at local `(0, -12)`). The script MUST clamp the magnitude of `self._velocity` to a maximum of `MAX_SHIP_SPEED` to prevent unbounded acceleration. In `_physics_process(dt)`, the script SHALL apply `self.position = self.position + self._velocity * dt` (no automatic drag — Asteroids canonical) and then apply wraparound (see "Continuous wraparound").

#### Scenario: Left arrow rotates counter-clockwise

- **GIVEN** a `Ship` with `rotation == 0f` and `Key.ArrowLeft` held down
- **WHEN** one frame of `_process(dt = 0.016f)` runs
- **THEN** `self.rotation` is less than `0f` (decreased by `TURN_RATE * 0.016`)

#### Scenario: Up arrow accelerates in the nose direction

- **GIVEN** a `Ship` with `rotation == 0f` (nose pointing up) and `self._velocity == Vec2.ZERO`
- **WHEN** `Key.ArrowUp` is held and `_process(dt = 0.1f)` runs
- **THEN** `self._velocity.x` is approximately `0f` (within tolerance)
- **AND** `self._velocity.y` is less than `0f` (moving up in screen coords)

#### Scenario: Velocity is capped at the maximum

- **GIVEN** a `Ship` whose `_velocity` is already at magnitude `MAX_SHIP_SPEED`
- **WHEN** thrust is applied for one more frame
- **THEN** `self._velocity` magnitude does not exceed `MAX_SHIP_SPEED` after the clamp

#### Scenario: Position integrates velocity in _physics_process

- **GIVEN** a `Ship` with `self.position == Vec2(400f, 300f)` and `self._velocity == Vec2(100f, 0f)`
- **WHEN** `_physics_process(dt = 0.05f)` runs once and the wraparound does not apply
- **THEN** `self.position` equals `Vec2(405f, 300f)` within floating-point tolerance

### Requirement: Bullets are Area2Ds with TTL and consume on impact

The script `bullet.py` SHALL initialize `self._ttl = BULLET_TTL` and `self._consumed = False` in `_ready`. In `_process(dt)`, the script SHALL decrement `self._ttl -= dt` and call `self.queue_free()` (or equivalent removal path) when `self._ttl <= 0f`. In `_physics_process(dt)`, the script SHALL move the bullet by `self._velocity * dt` (where `velocity` is an export set by `ship.py` at spawn) and then apply wraparound. In `_on_area_entered(area)`, if `self._consumed == False` AND `area.is_in_group("asteroids")`, the script MUST set `self._consumed = True`, call `script_of(area).hit()` to trigger the cascade, then call `self.queue_free()`. The `_consumed` flag prevents double-trigger when a bullet tangentially overlaps two asteroids in the same frame.

#### Scenario: Bullet self-removes after TTL expires

- **GIVEN** a `Bullet` with `self._ttl = 1.0f` and not yet consumed
- **WHEN** enough frames of `_process(dt = 0.1f)` accumulate to drive `_ttl` to `<= 0`
- **THEN** the bullet is no longer present in the scene tree at the next applyPending point

#### Scenario: Bullet triggers asteroid hit on first contact only

- **GIVEN** a `Bullet` whose path overlaps two `Asteroid` instances in a single physics step
- **WHEN** the engine dispatches `area_entered` for both asteroids in sequence
- **THEN** `script_of(asteroid).hit()` is called exactly once (on the first reported asteroid)
- **AND** the second `area_entered` is observed but produces no further `hit` call because `self._consumed == True`
- **AND** the bullet is queue-removed exactly once

### Requirement: Asteroids cascade-break on hit

The script `asteroid.py` SHALL declare exports `size: str` (one of `"big"`, `"medium"`, `"small"`) and `velocity: Vec2`. In `_ready`, the script MUST add the node to the group `"asteroids"`. It MUST expose a method `hit()` that:

1. If `size == "big"`, spawns 2 sibling asteroids of `size = "medium"` at the same world position, with velocities derived by rotating `self.velocity` by `+30°` and `-30°` respectively (preserving magnitude).
2. If `size == "medium"`, spawns 2 sibling asteroids of `size = "small"` with the same `±30°` deflection rule.
3. If `size == "small"`, spawns no children.
4. In all three cases, emits the global `score_changed` signal (declared in `game.py`) with the size-appropriate delta: `20` for big, `50` for medium, `100` for small.
5. Calls `self.queue_free()`.

In `_physics_process(dt)`, the asteroid SHALL move by `self.velocity * dt` and apply wraparound. Asteroids MUST NOT collide with other asteroids — the collision shape participates only in being hit by bullets (`Area2D` triggers) and by the ship (`_on_body_entered`).

#### Scenario: Big asteroid breaks into two medium asteroids

- **GIVEN** an `Asteroid` with `size = "big"` at world position `Vec2(400f, 300f)` with `velocity = Vec2(50f, 0f)`
- **WHEN** `asteroid.hit()` is called
- **THEN** at the next applyPending point, the original `Asteroid` is no longer in the tree
- **AND** the parent (`AsteroidsRoot`) contains 2 new `CharacterBody2D` children whose script is `asteroid.py` and whose `size` export equals `"medium"`
- **AND** the two new asteroids' velocities have magnitude approximately `50f` and are rotated by approximately `+30°` and `-30°` from `Vec2(50f, 0f)`

#### Scenario: Medium asteroid breaks into two small asteroids

- **GIVEN** an `Asteroid` with `size = "medium"`
- **WHEN** `asteroid.hit()` is called
- **THEN** the parent gains 2 children of `size = "small"` with the same `±30°` deflection rule

#### Scenario: Small asteroid disappears without spawning children

- **GIVEN** an `Asteroid` with `size = "small"`
- **WHEN** `asteroid.hit()` is called
- **THEN** the asteroid is queue-removed
- **AND** no new asteroid is added to the parent

#### Scenario: Score signal is emitted with size-appropriate delta

- **WHEN** `asteroid.hit()` is called for `size` in `{"big", "medium", "small"}`
- **THEN** the global `score_changed` signal is emitted exactly once with payload `20`, `50`, or `100` respectively

#### Scenario: Asteroids do not collide with each other

- **GIVEN** two `Asteroid` instances whose paths cross within a physics frame
- **WHEN** physics resolves the frame
- **THEN** neither asteroid emits a `body_entered` or `area_entered` referencing the other
- **AND** both pass through each other unchanged

### Requirement: Continuous wraparound uses Camera2D bounds

The scripts `ship.py`, `asteroid.py`, and `bullet.py` SHALL apply wraparound at the end of `_physics_process(dt)` (after movement and any `moveAndCollide` calls). The wraparound formula MUST be:

```
bounds = current Camera2D bounds   # cached in _ready from the scene's Camera2D
p = self.position
size = bounds.size
origin = bounds.origin
self.position = Vec2(((p.x - origin.x) % size.x) + origin.x,
                    ((p.y - origin.y) % size.y) + origin.y)
```

Python's `%` semantics MUST handle negative values correctly (a position `bounds.origin.x - 1` MUST wrap to `bounds.origin.x + bounds.size.x - 1`). The wraparound MUST NOT trigger a `gameOver` or any other side-effect by itself.

#### Scenario: Ship crossing right edge wraps to left edge

- **GIVEN** a `Ship` at `position = Vec2(801f, 300f)` after a `_physics_process` movement, with bounds `Rect(Vec2(0f, 0f), Vec2(800f, 600f))`
- **WHEN** the wraparound applies
- **THEN** `self.position` equals `Vec2(1f, 300f)`

#### Scenario: Asteroid crossing top edge wraps to bottom

- **GIVEN** an `Asteroid` at `position = Vec2(400f, -1f)` after a movement, same bounds as above
- **WHEN** the wraparound applies
- **THEN** `self.position` equals `Vec2(400f, 599f)`

#### Scenario: Negative coordinates wrap into the positive range

- **GIVEN** any subject node at `position.x = -1f` with bounds `origin.x = 0f` and `size.x = 800f`
- **WHEN** the wraparound applies
- **THEN** the resulting `position.x` equals `799f` (NOT `-1f` and NOT `0f`)

### Requirement: Ship visually rotates without script-level vertex transformation

The `Polygon2D` child of `Ship` SHALL be rendered rotated visually whenever `Ship.rotation` (the `CharacterBody2D` node's local rotation) is nonzero, **without** the ship script computing rotated vertices. The Polygon2D's `points` field SHALL contain the fixed local-space triangle and SHALL NOT be mutated by `ship.py` to achieve visual rotation. This requirement validates that the `canvas-item-transform` change (the engine's per-`Node2D` `pushTransform(world.position, world.rotation, world.scale)` around each `onDraw`) is functioning end-to-end via gameplay.

#### Scenario: Ship rotates visually as rotation changes

- **GIVEN** a `Ship` with `rotation = 0f` whose `Polygon2D` child has `points = [Vec2(-10f, 8f), Vec2(0f, -12f), Vec2(10f, 8f)]`
- **WHEN** `Ship.rotation` is set to `PI / 2f` (90° clockwise) and a frame renders against a recording `Renderer`
- **THEN** the recorded sequence around the `Polygon2D.onDraw` includes a `pushTransform` whose `rotation` parameter is approximately `PI / 2f`
- **AND** the `drawPolygon` is called with the unmodified local-space `points` (NOT pre-rotated by the script)

#### Scenario: Ship script does not mutate Polygon2D.points

- **WHEN** any code path in `ship.py` is inspected
- **THEN** there is no assignment to `polygon.points`, `self.points`, or any equivalent that would rewrite the visual vertex list

### Requirement: Game over decreases lives and freezes physics

The script `ship.py` SHALL implement `_on_body_entered(body)` such that when `body.is_in_group("asteroids")` and the game is not already paused, it emits the global `lives_changed` signal (declared in `game.py`) with payload `-1`. The script `game.py` SHALL listen to `lives_changed`, decrement an internal `_lives` counter starting at `3`, update the `LivesLabel` text to `"Lives: {n}"`, and when `_lives` reaches `0` MUST: set `self._paused = True`; hide the `Ship` (either via `Node2D.visible = false` if available, or by setting the ship's `Polygon2D.color.a = 0f`); make the `GameOverLabel` visible; expose `is_paused() -> bool` returning `True`. While `_paused == True`, `ship.py`, `asteroid.py`, and `bullet.py` SHALL early-return from `_physics_process` and skip thrust/fire/movement logic in `_process`, except that `game.py` itself continues to read `wasKeyPressed(Key.Enter)` to allow restart.

#### Scenario: Colliding with an asteroid decrements lives

- **GIVEN** a `Ship` and an `Asteroid` in the same tree, with `_lives = 3` and `_paused = False`
- **WHEN** the physics step reports a `body_entered` from the ship's perspective referencing the asteroid
- **THEN** `lives_changed` is emitted once with payload `-1`
- **AND** `game.py`'s internal `_lives` becomes `2`
- **AND** the `LivesLabel` text becomes `"Lives: 2"`

#### Scenario: Reaching zero lives triggers game over

- **GIVEN** a game state with `_lives = 1` and `_paused = False`
- **WHEN** the ship collides with an asteroid and `lives_changed(-1)` is processed
- **THEN** `game.py._paused == True`
- **AND** the `Ship` is hidden
- **AND** the `GameOverLabel` is visible

#### Scenario: Physics freezes when paused

- **GIVEN** a game state with `_paused = True` and at least one `Asteroid` in `AsteroidsRoot`
- **WHEN** a `_physics_process(dt)` frame runs
- **THEN** the asteroid's `position` is unchanged from the prior frame
- **AND** the ship does NOT respond to thrust input

### Requirement: Restart reconstructs the game without process restart

The script `game.py` SHALL, while `_paused == True`, read `wasKeyPressed(Key.Enter)` in `_process` and on true:

1. Remove every child of `AsteroidsRoot` via `removeChild`.
2. Remove every child of `BulletsRoot` via `removeChild`.
3. Reset `Ship`: `position = Vec2(bounds.size.x / 2 + bounds.origin.x, bounds.size.y / 2 + bounds.origin.y)`, `rotation = 0f`, internal `_velocity = Vec2.ZERO`, restore visibility (set `visible = true` or `color.a = 1.0` on the `Polygon2D`).
4. Reset HUD: `ScoreLabel.text = "Score: 0"`, `LivesLabel.text = "Lives: 3"`, `GameOverLabel` hidden.
5. Reset internal counters: `_score = 0`, `_lives = 3`, `_paused = False`.
6. Spawn 4 big asteroids in random positions outside a circle of radius `SAFE_SPAWN_RADIUS` around the scene center. Each gets a random initial velocity of magnitude between `MIN_ASTEROID_SPEED` and `MAX_ASTEROID_SPEED` in a random direction.

#### Scenario: Enter after game over restarts the game

- **GIVEN** a game state with `_paused = True`, score `120`, `GameOverLabel` visible, `AsteroidsRoot` containing 7 child asteroids, `BulletsRoot` containing 2 child bullets
- **WHEN** `wasKeyPressed(Key.Enter)` reports true in `game.py._process`
- **THEN** `_paused` becomes `False`
- **AND** `AsteroidsRoot` ends up with exactly 4 children, all of `size = "big"`
- **AND** `BulletsRoot` ends up with exactly 0 children
- **AND** `ScoreLabel.text == "Score: 0"` and `LivesLabel.text == "Lives: 3"`
- **AND** `GameOverLabel` is hidden
- **AND** the `Ship` is visible at the scene center with `_velocity == Vec2.ZERO` and `rotation == 0f`

#### Scenario: Initial asteroids spawn outside the safe radius

- **GIVEN** a freshly restarted scene
- **WHEN** the 4 big asteroids are spawned
- **THEN** each of their initial positions has distance > `SAFE_SPAWN_RADIUS` from the scene center

### Requirement: Score signal updates the ScoreLabel

The script attached to `ScoreLabel` (or `game.py` directly) SHALL listen to the `score_changed` signal and, on emission, increment an internal counter by the payload value, then set `ScoreLabel.text = "Score: {n}"` where `n` is the new counter value. On `restart` (or equivalent), the counter MUST be reset to `0` and the label MUST display `"Score: 0"`.

#### Scenario: Score increments on each asteroid break

- **GIVEN** a fresh game with `Score: 0` displayed
- **WHEN** the player destroys one big asteroid (yielding 2 medium) and then both mediums (yielding 4 small) and then all 4 smalls
- **THEN** the label displays `Score: 20 + 2*50 + 4*100 = 520` → `"Score: 520"`

#### Scenario: Score resets on restart

- **GIVEN** a game where the label displays `Score: 280` and `_paused == True`
- **WHEN** the player presses Enter and restart runs
- **THEN** the label displays `Score: 0`

### Requirement: CLAUDE.md and ROADMAP.md are updated

The `CLAUDE.md` file SHALL include a "Para rodar Asteroids" subsection under "Module Structure & How to Run", listing the controls (← rotate CCW, → rotate CW, ↑ thrust, Space fire, Enter restart on game-over, F1/F2 debug toggles). The `ROADMAP.md` file SHALL list `game-asteroids` as **Active** (not **Planned**) during the change's lifetime, with the existing one-line summary updated to drop the phrase "múltiplas shapes por objeto" — replaced by the actual validation axes (rotation visual, cascade spawn, N kinematic bodies).

#### Scenario: CLAUDE.md documents Asteroids controls

- **WHEN** the contents of `CLAUDE.md` are read
- **THEN** a subsection mentioning Asteroids exists with `./gradlew :games:asteroids:run`
- **AND** the subsection lists at minimum: ← rotate, → rotate, ↑ thrust, Space fire, Enter restart

#### Scenario: ROADMAP.md reflects current status

- **WHEN** the contents of `ROADMAP.md` are read
- **THEN** `game-asteroids` appears under the **Active** section
- **AND** the description does NOT contain the phrase "múltiplas shapes por objeto"
- **AND** the description mentions canvas-item-transform OR rotation visual OR cascade spawn as the validation axis
