## 1. Center the rectangle in the physics core

- [ ] 1.1 In `Shape2D.kt`, change `obbCorners` to pass centered local points to `Transform.apply` — `(-w/2,-h/2),(w/2,-h/2),(-w/2,h/2),(w/2,h/2)` — preserving its documented order (TL, TR, BL, BR)
- [ ] 1.2 In `Shape2D.kt`, change `RectangleShape2D.worldCorners` to centered local points, preserving its documented loop order (TL, TR, BR, BL)
- [ ] 1.3 In `sweepRectRect`, shift each rect's effective origin by `-size/2·scale` (`ax0 = position.x - aw/2`, etc. for A and B); leave all penetration/normal/slab math untouched
- [ ] 1.4 In `sweepCircleRect`, shift the rect's effective origin by `-size/2·scale` (`rx0 = position.x - rw/2`, `ry0 = position.y - rh/2`); leave nearest-point/corner-refine math untouched
- [ ] 1.5 Confirm by inspection that `bounds`, `obbVsObbOverlap`, `rectCircleOverlap`, `sweepRotatedRectRotatedRect`, `sweepCircleRotatedRect`, `sweepRectVsCircle`, `sweepRotatedRectVsCircle` need NO change (they flow through 1.1–1.4); and that `RigidBody2D` inertia and `Node2D`/`Shape2D.localBounds` already assume centered — no edits there

## 2. Recalibrate engine tests to centered anchoring

- [ ] 2.1 `RectangleWorldCornersTest`: update the unrotated-corners assertion to centered corners; confirm the "centroid at world center" test still passes unchanged
- [ ] 2.2 `Shape2DOverlapTest`: update the rotated-envelope assertion and the corner-anchored comment; verify rect-rect/rect-circle overlap cases
- [ ] 2.3 `PhysicsSystemTest`: update `RectangleShape2D bounds reflect transform scale` to `origin = Vec2(40f, 30f)`
- [ ] 2.4 `SweepTest`: recompute rect-rect layouts (axis-aligned TOI and starting-overlap depth) for centered rects
- [ ] 2.5 `CharacterBody2DTest`: recalibrate any sweep expectation that hardcodes a corner-anchored rect position
- [ ] 2.6 `ShapeLocalBoundsTest`: confirm it still passes unchanged (already centered)
- [ ] 2.7 Run `:engine:test` green

## 3. Reposition rectangle colliders in games

- [ ] 3.1 `games/pong` `scene.json`: shift each `RectangleShape2D` collider (paddles, top/bottom walls, left/right goals) by `+size/2` so the centered collider lands where the old corner-anchored one did; leave the ball circle untouched
- [ ] 3.2 `games/snake`: audit `scripts/snake.py` (and scene.json) for rectangle colliders created/positioned by script; add `+size/2` where a corner was assumed
- [ ] 3.3 `games/tictactoe`: audit `scripts/board.lua` (and scene.json) for rectangle colliders; adjust if any assume a corner

## 4. Reposition rectangle colliders in demos

- [ ] 4.1 `BoundaryWalls.kt`: recompute the four wall `position`s in `relayout()` for centered colliders (`+size/2` per wall)
- [ ] 4.2 `RotatingBoxDemo.kt`: adjust rectangle collider placement so boxes sit where intended under centered anchoring
- [ ] 4.3 `CollisionStressDemo.kt`: adjust rectangle collider placement
- [ ] 4.4 `SpawnerDemo.kt`: adjust spawned rectangle collider placement
- [ ] 4.5 `TumblingSwarmDemo.kt`: remove the now-unnecessary `position = -size/2` offset on the `CollisionShape2D` (centered shape no longer needs it); keep the visual aligned

## 5. Sweep for stray scene fixtures

- [ ] 5.1 Grep all `scene.json` under `:engine*` test resources and `games/*` for `RectangleShape2D` and verify none relies on corner anchoring (e.g. `scene-serialization` fixtures); adjust positions if found

## 6. Verification — tests + living games/demos

- [ ] 6.1 Run the full test suite (`./gradlew test`) green across `:engine`, backends, and games
- [ ] 6.2 Launch each game on Skiko (Pong, Snake, TicTacToe) and confirm gameplay is identical to before (paddles deflect the ball, ball respects walls/goals, board cells register clicks)
- [ ] 6.3 Launch the `:games:demos` Skiko entrypoint and step through all 6 scenes; confirm walls contain bodies, rotating-arena and tumbling boxes collide correctly
- [ ] 6.4 Launch the `:games:demos` LWJGL entrypoint (`runLwjgl`) and confirm the same (invariant #4 sentinel)
- [ ] 6.5 Verify the divergence is closed: for a `RectangleShape2D` collider, `CollisionShape2D.worldBounds()` (inherited) equals `broadPhaseBounds()` — add a regression test asserting agreement
- [ ] 6.6 Update the risk note in `openspec/changes/node-local-bounds/design.md` (or the archived spec) to record that the divergence is resolved by this change
- [ ] 6.7 `openspec validate center-rectangle-shape --strict` passes
