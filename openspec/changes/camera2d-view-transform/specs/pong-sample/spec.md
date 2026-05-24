## MODIFIED Requirements

### Requirement: Pong reads viewport from the scene, not a prop

The `playFieldHeight` prop on `paddle.py` SHALL remain removed. The paddle clamping logic SHALL continue to read `self.rootScene().viewport.size.y` (or equivalent) at clamp time. The Pong `scene.json` SHALL include a `Camera2D` node with `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` and `current = true`, declaring the play field as world bounds. The `Camera2D` node SHALL additionally declare `aspectMode = AspectMode.FIT` so the 800×600 world maps onto any surface size with letterbox-style preserved aspect ratio. Because `Scene.render` now applies the camera as a view transform, all positions in `pong/scene.json` (paddles, walls, goals, ball, scores, center line) SHALL be authored in the fixed 800×600 world coordinate system and SHALL NOT be repositioned by scripts at runtime.

#### Scenario: paddle.py has no playFieldHeight export

- **WHEN** `paddle.py` is inspected
- **THEN** no top-level `playFieldHeight: float = ...` declaration exists
- **AND** the clamp logic reads `self.rootScene().viewport.size.y` (or an equivalent accessor on `Scene` returning the viewport height)

#### Scenario: Pong scene declares a Camera2D with FIT aspect

- **WHEN** `pong.scene.json` is inspected
- **THEN** the root contains a child of type `com.neoutils.engine.scene.Camera2D` with `current: true`, `bounds: Rect(Vec2.ZERO, Vec2(800f, 600f))`, and `aspectMode: "FIT"`

#### Scenario: Paddle reaches the full play field height on any surface size

- **GIVEN** a Pong instance running with `Camera2D.bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))` on a surface of any size (e.g. `1280×900`)
- **WHEN** the left paddle is moved continuously downward until its clamp triggers
- **THEN** the paddle's bottom edge reaches `viewport.size.y == 600` in world coordinates
- **AND** when rendered, the paddle visually touches the bottom edge of the play field (the camera projection respects the surface, the letterbox bars do not eat into the play field)

### Requirement: Pong scene composition

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), and a HUD subtree with two `Score` text nodes and an optional center-line decoration. Each `Paddle` MUST carry a child `BoxCollider` whose `size` mirrors the paddle's `size`. The `Ball` MUST itself extend `BoxCollider` (the ball **is** its collider, not a node that contains one) — no anonymous `BoxCollider` subclass MAY be used in the Pong codebase. The wall and paddle-child colliders SHALL be plain `com.neoutils.engine.physics.BoxCollider` instances; Pong MUST NOT declare empty `BoxCollider` subclasses (e.g. `Wall`, `PaddleCollider`) in scripts or Kotlin source.

The scene file SHALL author every node position in the 800×600 world coordinate system declared by the `Camera2D`. Specifically the paddles SHALL be placed at fixed `transform.position` values that center them vertically and offset them horizontally by `PADDLE_MARGIN` from each goal; the top wall SHALL sit at `Vec2(0, 0)` with full play-field width; the bottom wall SHALL sit at `Vec2(0, 600 - WALL_THICKNESS)`; the goals SHALL bracket the play field at `x = -GOAL_THICKNESS` and `x = 800`; the score labels SHALL sit at fixed positions near the top center of the world; the ball SHALL be authored with `fieldCenter = Vec2(400, 300)`. The Pong `pong_scene.py` script SHALL NOT contain a `_layout(width, height)` function or any equivalent runtime reposition routine — the world is fixed and the camera handles surface mapping. The script MAY retain a `_ready` for signal wiring (e.g. connecting the ball's `scored` signal to the scoreboards) and nothing else.

#### Scenario: Scene contains the expected nodes after construction

- **WHEN** a new `PongScene` is instantiated
- **THEN** its tree contains exactly: two paddles, one ball, four boundary colliders, two score texts

#### Scenario: Ball is a BoxCollider directly

- **WHEN** the `Ball` class is inspected
- **THEN** it extends `com.neoutils.engine.physics.BoxCollider`
- **AND** no separate child `BoxCollider` node is added to the ball

#### Scenario: Paddle child collider is a plain BoxCollider

- **WHEN** a `Paddle` instance is constructed and `onEnter` runs
- **THEN** it has exactly one child of type `com.neoutils.engine.physics.BoxCollider`
- **AND** the child's runtime class is `BoxCollider` itself, not a subclass

#### Scenario: No anonymous BoxCollider subclasses exist in Pong

- **WHEN** the `:games:pong` source tree is searched for occurrences of `object : BoxCollider`
- **THEN** no matches are found

#### Scenario: No empty BoxCollider subclasses exist in Pong

- **WHEN** the `:games:pong/src/main/resources/scripts/` directory and `:games:pong/src/main/kotlin` source tree are searched
- **THEN** no file declares an empty subclass of `BoxCollider`

#### Scenario: All Pong nodes have fixed world-space positions in scene.json

- **WHEN** `pong/scene.json` is inspected
- **THEN** every node (paddles, walls, goals, ball, scores, center line) carries a `transform.position` value in the 800×600 world coordinate system
- **AND** no node's position is `Vec2(0, 0)` placeholder waiting to be filled by a script

#### Scenario: pong_scene.py does not reposition nodes at runtime

- **WHEN** `pong/scripts/pong_scene.py` is inspected
- **THEN** no function named `_layout` (or equivalent reposition routine that reads `scene.size`/`scene.width`/`scene.height` to assign `transform`) exists
- **AND** no read of `self._node.width`, `self._node.height`, `scene.size`, or `scene.width`/`scene.height` appears in the script
- **AND** if `_process` exists at all, it does not assign `transform` on any node based on surface size
