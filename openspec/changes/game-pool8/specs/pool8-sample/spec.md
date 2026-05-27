## ADDED Requirements

### Requirement: Pool 8-ball is an executable standalone module

The project SHALL provide a `:games:pool8` module that depends on `:engine`, `:engine-skiko`, `:engine-bundle`, and `:engine-bundle-lua`, and contains a `main()` entry point that:

1. Constructs a `LuaScriptHost` via `LuaScriptHost.create()`.
2. Loads the bundle via `BundleLoader.fromResources("pool8", scripting = lua)`, which returns the detached root `Node`.
3. Wraps the root in `SceneTree(root = ...)` and passes the resulting `SceneTree` to `SkikoHost().run(tree, config)`.

The module MUST be runnable via `./gradlew :games:pool8:run`. The module MUST NOT depend on any other game module. The module MUST NOT depend on `:engine-bundle-python` (Lua-only). The module MUST NOT depend on `:engine-compose` (Skiko-only, consistent with `:games:pong` and `:games:demos`).

#### Scenario: Pool 8-ball runs from Gradle

- **WHEN** a developer runs `./gradlew :games:pool8:run` from the project root
- **THEN** a desktop window opens displaying the pool table with the cue ball, the rack of 15 numbered balls, and 6 pockets
- **AND** the game is responsive to mouse input

#### Scenario: Main.kt is a thin wiring entry point

- **WHEN** `games/pool8/src/main/kotlin/.../Main.kt` is inspected
- **THEN** `main()` calls `LuaScriptHost.create()`, then `BundleLoader.fromResources("pool8", scripting = lua)`, then wraps the result in `SceneTree(root = ...)` and passes the tree to `SkikoHost().run(...)`
- **AND** `main()` does not import any game-specific class
- **AND** `main()` does not import `PythonScriptHost`

#### Scenario: Build wires Skiko backend with Lua scripting

- **WHEN** `games/pool8/build.gradle.kts` is inspected
- **THEN** the dependencies include `projects.engine`, `projects.engineSkiko`, `projects.engineBundle`, and `projects.engineBundleLua`
- **AND** the dependencies do NOT include `projects.engineCompose`
- **AND** the dependencies do NOT include `projects.engineBundlePython`

#### Scenario: Module is registered in settings.gradle

- **WHEN** `settings.gradle.kts` is inspected after this change
- **THEN** the module `:games:pool8` is included alongside the other game modules

### Requirement: Pool table scene composition

The Pool 8-ball scene SHALL be loaded from `src/main/resources/pool8/scene.json`, with all orchestrator logic in Lua scripts under `src/main/resources/pool8/scripts/`. The scene MUST include, as descendants of the root:

- A `Camera2D` with `current: true` and `bounds = Rect(Vec2.ZERO, Vec2(2000f, 1000f))`.
- A green `ColorRect` covering the full table area (cloth visual).
- Exactly 4 cushion segments, each a `StaticBody2D` with a `RectangleShape2D` child, forming a closed rectangle enclosing the cloth (no gaps).
- Exactly 6 `Area2D` pockets, each with a `CircleShape2D` child of radius at least 2.2 × ball radius, positioned at the 4 corners and 2 mid-rail center points of the table.
- Exactly 16 `RigidBody2D` ball nodes: 1 named `CueBall` (number 0) and 15 numbered 1 through 15 (named `Ball1`, `Ball2`, …, `Ball15`).
- A `Node2D` named `CueStick` with `scripts/cue.lua` attached.
- A `Label` named `Status` with `scripts/status.lua` attached.

The root SHALL have `scripts/table.lua` attached.

#### Scenario: Bundle directory layout

- **WHEN** `games/pool8/src/main/resources/pool8/` is inspected
- **THEN** the directory contains `scene.json` at its root
- **AND** the directory contains `scripts/table.lua`, `scripts/ball.lua`, `scripts/pocket.lua`, `scripts/cue.lua`, and `scripts/status.lua`

#### Scenario: Scene declares 16 ball nodes

- **WHEN** `scene.json` is inspected
- **THEN** the tree contains exactly one node named `CueBall` of type `RigidBody2D`
- **AND** the tree contains exactly 15 nodes named `Ball1` through `Ball15`, each of type `RigidBody2D`
- **AND** every ball node has `scripts/ball.lua` attached

#### Scenario: Scene declares 6 pockets as Area2D

- **WHEN** `scene.json` is inspected
- **THEN** the tree contains exactly 6 `Area2D` nodes under a parent named `Pockets`
- **AND** each pocket has a `CircleShape2D` child whose radius is at least 44 (i.e. ≥ 2.2 × ball radius 20)
- **AND** each pocket has `scripts/pocket.lua` attached

#### Scenario: Cushions form a closed rectangle

- **WHEN** `scene.json` is inspected
- **THEN** the tree contains exactly 4 `StaticBody2D` nodes under a parent named `Cushions`
- **AND** the union of their `RectangleShape2D` collision rectangles forms a closed rectangle enclosing the 2000×1000 cloth area

#### Scenario: Camera projects the table

- **WHEN** the scene is loaded
- **THEN** the active `Camera2D` has `bounds = Rect(Vec2(0,0), Vec2(2000, 1000))`
- **AND** the cloth occupies the full visible region under `AspectMode.FIT`

### Requirement: Ball rendering via local-space _draw

Each ball SHALL render itself in `ball.lua._draw(self, renderer)` using local-space coordinates (origin at the ball center). Each ball script attaches to a `RigidBody2D` and declares the exports `number: int`, `ballColor: Color`, `isCueBall: bool`. The `_draw` MUST issue:

1. A filled circle of radius equal to the ball's `CollisionShape2D` circle radius, with `fill = self.ballColor`.
2. If `self.number` is between 9 and 15 inclusive, a horizontal white rectangle centered on the ball (representing the stripe) with width = 2r and height ≈ 0.7r.
3. If `self.isCueBall` is false, the text representation of `self.number` rendered centered at the local origin in black.

The script MUST NOT add `Circle2D` or `Label` children to the ball; rendering is `_draw`-only.

#### Scenario: Cue ball renders white with no number

- **WHEN** the scene is loaded
- **THEN** the `CueBall` ball draws a solid white filled circle of radius 20 at its position
- **AND** no numeral is rendered on the cue ball

#### Scenario: Solid ball renders color plus number

- **WHEN** the scene is loaded
- **THEN** any ball with `number` between 1 and 7 inclusive draws a solid filled circle in its canonical color (1=yellow, 2=blue, 3=red, 4=purple, 5=orange, 6=green, 7=brown)
- **AND** the numeral is rendered in black at the center

#### Scenario: 8-ball renders black

- **WHEN** the scene is loaded
- **THEN** `Ball8` draws a solid black filled circle with the numeral "8" rendered in a contrasting color (white) at the center

#### Scenario: Stripe ball renders color plus white band plus number

- **WHEN** the scene is loaded
- **THEN** any ball with `number` between 9 and 15 inclusive draws a solid colored circle, a horizontal white band across its middle, and its numeral in black
- **AND** the white band's vertical extent is approximately 0.7 × the ball radius centered on the ball

### Requirement: Initial rack and cue ball positioning

The scene SHALL position the 16 balls deterministically before the first shot:

- The `CueBall` SHALL start on the head spot at approximately (500, 500) in world coordinates.
- The 15 numbered balls SHALL form a triangular rack with its apex at the foot spot at approximately (1500, 500), with rows of 1, 2, 3, 4, and 5 balls.
- `Ball8` SHALL occupy the center of the rack (the middle position of the 3rd row).
- Inter-ball spacing in the rack SHALL be approximately 2 × ball radius + 1 (no overlap, near-touching).
- All ball velocities and angular velocities SHALL be zero at scene load.

#### Scenario: Cue ball starts on head spot

- **WHEN** the scene is loaded
- **THEN** the `CueBall` position is approximately (500, 500)
- **AND** the `CueBall` linear and angular velocities are zero

#### Scenario: 8-ball sits at center of rack

- **WHEN** the scene is loaded
- **THEN** `Ball8` position is at the center of the triangular rack (row 3, column 2 of the 5-row triangle), approximately (1571, 500)

#### Scenario: Rack apex aligns with foot spot

- **WHEN** the scene is loaded
- **THEN** the first ball of the rack (row 1) sits at the foot spot, approximately (1500, 500)

#### Scenario: Rack balls do not overlap

- **WHEN** the scene is loaded
- **THEN** for every pair of distinct balls in the rack, the distance between centers is at least 2 × ball radius

### Requirement: Cue stick input with drag-and-release

The `CueStick` node SHALL implement "pull and release" input via `cue.lua` while the game is in the `AIMING` state:

1. On left mouse button press, the cue stick enters an active aiming sub-state and records the press position.
2. On each frame while aiming, the cue stick computes `aimVector = mousePosition_world - cueBall.position`, clamps its magnitude to a maximum (`maxDragPixels`, default 400 world units), and stores it.
3. The cue stick renders during `_draw`:
   - A line from the cue ball center to the current mouse world position (the visual cue stick).
   - An arrow tail behind the cue ball, opposite to `aimVector`, scaled by the clamped drag magnitude (visual feedback for force).
   - A dashed projection line starting at the cue ball center, traveling in the shot direction (`-aimVector.normalized`), terminating at the first intersection with any other ball or with a cushion.
4. On left mouse button release, if `|aimVector|` is at least `minPower` (default 20), the cue stick emits a shot signal with the impulse `(-aimVector.normalized) × (|aimVector| × forceK)`; otherwise the aim is cancelled with no impulse applied.

The cue stick MUST NOT process input or render projection while the game is not in `AIMING`.

#### Scenario: Drag visualizes the force vector and projection

- **WHEN** the game is in `AIMING` and the player presses the left mouse button and drags
- **THEN** a line from the cue ball to the current mouse position is rendered as the cue stick
- **AND** a dashed projection line from the cue ball in the opposite drag direction is rendered, ending at the first ball or cushion intersection

#### Scenario: Release applies impulse to cue ball

- **WHEN** the player releases the left mouse button after dragging with magnitude at least `minPower`
- **THEN** the cue ball receives an impulse opposite to the drag direction, with magnitude proportional to the (clamped) drag length
- **AND** the cue ball begins moving in the direction the drag pointed away from

#### Scenario: Tiny drag cancels the shot

- **WHEN** the player releases the left mouse button after dragging with magnitude less than `minPower`
- **THEN** the cue ball does not move
- **AND** the game remains in `AIMING`

#### Scenario: Input is ignored during simulation

- **WHEN** the game is in `SIMULATING` or `RESOLVING`
- **THEN** mouse press, drag, and release do not modify aim state and do not render the cue stick

### Requirement: Turn FSM with quiescence detection

The `Table.lua` SHALL implement a finite state machine with the states `AIMING`, `SIMULATING`, `RESOLVING`, and `GAME_OVER`. Transitions:

- Scene start → `AIMING` for player 1.
- On shot release with valid impulse → `AIMING` → `SIMULATING`.
- While `SIMULATING`, the table SHALL compute `kineticActivity = Σ|v_i| + κ · Σ|ω_i|` over all active ball `RigidBody2D` nodes each `_physics_process` step. When `kineticActivity < ε` for at least 3 consecutive physics steps, transition `SIMULATING` → `RESOLVING`.
- In `RESOLVING`, the table SHALL resolve pocketing outcomes and decide turn alternation, then transition to either `AIMING` or `GAME_OVER`.
- Once `GAME_OVER`, no further shots are accepted; only a restart triggers transition back to `AIMING`.

The default constants SHALL be `ε = 5.0`, `κ = 5.0`, `restFrames = 3`, tunable as Lua locals at the top of `table.lua`.

#### Scenario: Game starts in AIMING for player 1

- **WHEN** the scene loads
- **THEN** the table's state is `AIMING`
- **AND** the current player is 1

#### Scenario: Shot transitions to simulating

- **WHEN** the cue stick emits a valid shot signal in `AIMING`
- **THEN** the table transitions to `SIMULATING` before the next physics step

#### Scenario: Quiescence transitions to resolving

- **WHEN** the table is in `SIMULATING` and the kinetic activity remains below `ε` for 3 consecutive physics steps
- **THEN** the table transitions to `RESOLVING` in the step after the third quiet step

#### Scenario: Resolving returns to aiming when game continues

- **WHEN** the table is in `RESOLVING` and no game-ending condition was met
- **THEN** the table transitions to `AIMING` with the appropriate player set
- **AND** the cue stick becomes responsive to input again

### Requirement: Pocket sink removes balls and notifies table

Each pocket SHALL listen for `body_entered` and re-emit a per-instance signal to `table.lua` carrying the entering ball node. When a non-cue, non-8 ball is pocketed during `SIMULATING`, the ball SHALL be removed from the scene tree before the next physics step (using the engine's deferred `removeChild` semantics from within the signal handler). When the cue ball or the 8-ball is pocketed, the ball SHALL NOT be removed immediately; instead the table SHALL record the event in `pocketedThisShot` and handle removal/respawn during `RESOLVING`.

A pocket SHALL ignore repeated `body_entered` events for the same ball within a single shot (idempotent during a single `SIMULATING` window).

#### Scenario: Numbered ball enters a pocket and is removed

- **WHEN** during `SIMULATING`, a numbered ball (other than `Ball8`) enters a pocket's `Area2D`
- **THEN** the ball is removed from the scene tree by the start of the next physics step
- **AND** the ball is added to the table's `pocketedThisShot` list

#### Scenario: Cue ball enters a pocket and is queued for respawn

- **WHEN** during `SIMULATING`, the `CueBall` enters a pocket's `Area2D`
- **THEN** the cue ball is recorded in `pocketedThisShot` with a `cuePocketed` flag set true
- **AND** the cue ball is NOT removed from the tree

#### Scenario: 8-ball enters a pocket and is queued for game-over evaluation

- **WHEN** during `SIMULATING`, `Ball8` enters a pocket's `Area2D`
- **THEN** the table records the event with an `eightPocketed` flag set true
- **AND** the 8-ball remains in the tree until `RESOLVING` decides whether to remove it

#### Scenario: Repeated body_entered for the same ball is idempotent

- **WHEN** a pocket's `body_entered` signal fires twice for the same ball within a single `SIMULATING` window
- **THEN** the ball is added to `pocketedThisShot` exactly once

### Requirement: Level-1 rule resolution in RESOLVING

In `RESOLVING`, the table SHALL apply Level-1 rules (no solid/stripe groups) in this order:

1. If `eightPocketed` is true, transition to `GAME_OVER` with the winner equal to the current player. (No "must clear group" precondition at Level 1.)
2. Else if `cuePocketed` is true, reposition the cue ball on the head spot, zero its linear and angular velocities, mark the cue ball as not pocketed, then switch the current player. Transition to `AIMING`.
3. Else if `pocketedThisShot` contains at least one ball, keep the current player. Transition to `AIMING`.
4. Else, switch the current player. Transition to `AIMING`.

After applying rules, `pocketedThisShot` and its flags SHALL be cleared in preparation for the next shot.

#### Scenario: Pocketing a numbered ball keeps the turn

- **WHEN** in `RESOLVING` with one or more numbered balls (not cue, not 8) in `pocketedThisShot` and no cue/8 flags
- **THEN** the current player does not change
- **AND** the table transitions to `AIMING`

#### Scenario: Pocketing the cue ball passes the turn and respawns

- **WHEN** in `RESOLVING` with `cuePocketed` true and `eightPocketed` false
- **THEN** the cue ball is positioned on the head spot (≈ (500, 500))
- **AND** its linear and angular velocities are zero
- **AND** the current player is the opposite of who took the shot
- **AND** the table transitions to `AIMING`

#### Scenario: Pocketing the 8-ball ends the game

- **WHEN** in `RESOLVING` with `eightPocketed` true
- **THEN** the table transitions to `GAME_OVER`
- **AND** the winner is the player who took the shot, regardless of any other pocketing in the same shot

#### Scenario: Missing all pockets passes the turn

- **WHEN** in `RESOLVING` with `pocketedThisShot` empty (no flags either)
- **THEN** the current player switches to the opposite player
- **AND** the table transitions to `AIMING`

### Requirement: HUD reflects turn and winner

The `Status` `Label` SHALL display the current turn during play and the winner when the game ends. The text SHALL be updated by `status.lua` in reaction to signals emitted by `table.lua`:

- `turn_changed(playerIndex)` — emitted in `AIMING` transitions; updates the text to `"Player <N>'s turn"`.
- `game_ended(winnerIndex)` — emitted on entering `GAME_OVER`; updates the text to `"Player <N> wins! Click to restart."`.

`status.lua` MUST NOT poll table state; it SHALL react only via signal connections established in its `_ready`.

#### Scenario: Status label updates on turn change

- **WHEN** the table transitions to `AIMING` with current player 2
- **THEN** the `Status` label text reads `"Player 2's turn"`

#### Scenario: Status label announces winner

- **WHEN** the table transitions to `GAME_OVER` with player 1 winning
- **THEN** the `Status` label text reads `"Player 1 wins! Click to restart."`

### Requirement: Click-to-restart after game over

When the game state is `GAME_OVER`, the next left mouse click anywhere in the window SHALL reset the game: all numbered balls return to their initial rack positions, the cue ball returns to the head spot, all velocities zero, the current player resets to 1, and the state transitions to `AIMING`. The restart click MUST NOT also act as a shot.

#### Scenario: Click restarts after a win

- **WHEN** the table is in `GAME_OVER` and the player left-clicks
- **THEN** all balls return to their initial positions
- **AND** the current player is 1
- **AND** the state is `AIMING`
- **AND** no impulse is applied to the cue ball as a side effect of the restart click

### Requirement: Physics calibration values live in the bundle

The physics constants for pool dynamics SHALL be configured on the ball and cushion `RigidBody2D`/`StaticBody2D` nodes via `properties` in `scene.json`, NOT hardcoded in Kotlin. The initial calibration values SHALL be:

| node kind | property | initial value |
|---|---|---|
| Ball (`RigidBody2D`) | `restitution` | 0.95 |
| Ball | `friction` | 0.15 |
| Ball | `linearDamping` | 0.45 |
| Ball | `angularDamping` | 0.50 |
| Ball | `mass` | 1.0 |
| Cushion (`StaticBody2D`) | `restitution` | 0.85 |
| Cushion | `friction` | 0.10 |

These values MAY be tuned during implementation; the final values SHALL be reflected in the committed `scene.json`.

#### Scenario: scene.json declares ball physics properties

- **WHEN** `scene.json` is inspected
- **THEN** each ball `RigidBody2D` has `restitution`, `friction`, `linearDamping`, `angularDamping`, and `mass` set in its `properties` bag
- **AND** the values are positive finite numbers within the documented initial ranges (or final tuned values)

#### Scenario: scene.json declares cushion physics properties

- **WHEN** `scene.json` is inspected
- **THEN** each cushion `StaticBody2D` has `restitution` and `friction` set in its `properties` bag

### Requirement: No new engine, bundle, or scripting APIs

The implementation of `:games:pool8` SHALL consume only existing APIs from `:engine`, `:engine-bundle`, `:engine-bundle-lua`, and `:engine-skiko`. No new public types, methods, scene types, or Lua bindings SHALL be added to those modules as part of this change. If during implementation a missing API is identified, a separate OpenSpec change SHALL be opened before proceeding.

#### Scenario: Engine modules are unchanged

- **WHEN** the diff for this change is inspected
- **THEN** no source file under `engine/`, `engine-bundle/`, `engine-bundle-lua/`, or `engine-skiko/` is added or modified
- **AND** the only changes outside `games/pool8/` are documentation updates (`CLAUDE.md`, `ROADMAP.md`) and `settings.gradle.kts`

### Requirement: Documentation reflects the new sample

The project documentation SHALL be updated to surface `:games:pool8`:

- `CLAUDE.md` SHALL include a new section, parallel to the existing Pong/Tic-tac-toe/Demos/Hello-world entries, describing how to run pool8 (`./gradlew :games:pool8:run`), the controls (mouse drag-and-release, F1 FPS overlay, F2 colliders overlay), and a brief description of what the scene exercises.
- `CLAUDE.md`'s module structure block SHALL list `:games:pool8` with a one-line description.
- `ROADMAP.md` SHALL note the addition of the pool8 sample.

#### Scenario: CLAUDE.md describes how to run pool8

- **WHEN** `CLAUDE.md` is inspected after this change
- **THEN** a section describes the command `./gradlew :games:pool8:run`
- **AND** the controls section enumerates mouse drag-and-release, F1, and F2 behavior
- **AND** the module structure block lists `:games:pool8`

#### Scenario: ROADMAP.md mentions pool8

- **WHEN** `ROADMAP.md` is inspected after this change
- **THEN** an entry references the pool8 sample
