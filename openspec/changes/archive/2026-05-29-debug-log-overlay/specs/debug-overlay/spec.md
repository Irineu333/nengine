## ADDED Requirements

### Requirement: LogOverlayWidget tails recent log entries on screen

`LogOverlayWidget` SHALL extend `ScreenDebugWidget` and SHALL implement
`LogSink`. It SHALL own a fixed-capacity ring buffer of the last `N`
entries (default capacity `12`), each stored as an immutable
`LogEntry(timestampMillis, level, tag, message)`. As a `LogSink`, its
`emit(...)` SHALL append the entry to the ring buffer (overwriting the
oldest when full); `emit` MAY be invoked from any thread.

`LogOverlayWidget` SHALL subscribe and unsubscribe from `Log` based on
`enabled`, by overriding the `enabled` setter:

- On transition `false → true`: it SHALL call `Log.addSink(this)` and
  SHALL clear the ring buffer (no stale entries from a prior enabled
  window).
- On transition `true → false`: it SHALL call `Log.removeSink(this)`.

While not subscribed (`enabled = false`), the widget SHALL record nothing;
opening it begins a live tail of subsequently emitted entries, not of past
history. `drawDebug` SHALL read a consistent snapshot of the buffer safely
with respect to concurrent `emit` calls (e.g. via synchronization), draw
the entries anchored to the bottom-left corner of `tree.size` with the
most recent at the bottom, re-anchored each frame so it follows
`tree.resize`, and color each line by `LogLevel` (Debug/Info neutral,
`Warn` amber, `Error` red).

`LogOverlayWidget` SHALL expose `var minLevel: LogLevel` (default
`LogLevel.Debug`) as a display-only filter: `drawDebug` SHALL skip entries
whose `level` is below `minLevel`. This filter SHALL be orthogonal to
`Log.config` — it can only restrict beyond what already reached `emit`,
never recover entries gated out by `Log.config`.

#### Scenario: Enabling subscribes and clears the buffer

- **GIVEN** `tree.debug.log.enabled = false` with stale entries from a prior window
- **WHEN** `tree.debug.log.enabled = true` is set
- **THEN** the widget SHALL be a registered `Log` sink
- **AND** the ring buffer SHALL be empty until the next `Log.*` call

#### Scenario: Disabling unsubscribes and stops recording

- **GIVEN** `tree.debug.log.enabled = true`
- **WHEN** `tree.debug.log.enabled = false` is set and then `Log.i(...)` is emitted
- **THEN** the widget SHALL NOT be a registered `Log` sink
- **AND** the emitted entry SHALL NOT appear in the widget's buffer

#### Scenario: Ring buffer keeps only the last N entries

- **GIVEN** `tree.debug.log.enabled = true` and capacity `N = 12`
- **WHEN** `N + 5` entries are emitted via `Log.*` above the gate
- **THEN** the buffer SHALL hold exactly `N` entries
- **AND** they SHALL be the `N` most recent, in emission order

#### Scenario: Lines are colored by level

- **GIVEN** `tree.debug.log.enabled = true` with one `Warn` and one `Error` entry buffered
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** the `drawText` for the `Warn` entry SHALL use the amber color
- **AND** the `drawText` for the `Error` entry SHALL use the red color

#### Scenario: minLevel filters the display

- **GIVEN** `tree.debug.log.enabled = true`, `minLevel = LogLevel.Warn`, and buffered Debug, Info, Warn, Error entries
- **WHEN** a frame is rendered
- **THEN** only the `Warn` and `Error` entries SHALL be drawn

#### Scenario: Disabled overlay emits zero draws

- **GIVEN** `tree.debug.log.enabled = false`
- **WHEN** a frame is rendered against a recording `Renderer`
- **THEN** zero draw calls SHALL be attributed to `LogOverlayWidget`

## MODIFIED Requirements

### Requirement: SceneTree exposes a DebugRegistry

`SceneTree` SHALL expose `val debug: DebugRegistry` instantiated alongside the tree. `DebugRegistry` SHALL provide:

- `fun register(widget: DebugWidget)` — routes by subtype: `WorldDebugWidget` is added to the world container; `ScreenDebugWidget` is added to the screen container; both happen as live `addChild` operations and SHALL appear in the registry's `widgets` list.
- `fun unregister(widget: DebugWidget)` — removes the widget from its container and from the list.
- `val widgets: List<DebugWidget>` — read-only listing of currently registered widgets in registration order.
- `inline fun <reified T : DebugWidget> find(): T?` — first widget of the requested concrete type, or `null`.
- Convenience fields for the five built-ins: `val fps: FpsWidget`, `val colliders: ColliderWidget`, `val momentum: MomentumWidget`, `val log: LogOverlayWidget`, `val hud: DebugHud`. These fields point at the engine-owned instances and exist solely as ergonomic shortcuts to flip `enabled`.

`DebugRegistry` SHALL NOT be a `Node`, SHALL NOT be `@Serializable`, and SHALL NOT persist across `SceneTree` lifetimes — pure runtime state. Each `SceneTree` instance SHALL own its own `DebugRegistry` (no static or singleton sharing across trees).

#### Scenario: Built-ins are accessible via convenience fields

- **WHEN** a `SceneTree` is constructed and `start()` is called
- **THEN** `tree.debug.fps`, `tree.debug.colliders`, `tree.debug.momentum`, `tree.debug.log`, and `tree.debug.hud` SHALL all be non-null
- **AND** `tree.debug.widgets` SHALL contain at least these five instances

#### Scenario: register routes by subtype

- **GIVEN** a `WorldDebugWidget` instance `axes` and a `ScreenDebugWidget` instance `hud2`
- **WHEN** `tree.debug.register(axes)` and `tree.debug.register(hud2)` are called
- **THEN** `axes.parent` SHALL be the world container child of `DebugLayer`
- **AND** `hud2.parent` SHALL be the screen container child of `DebugLayer`
- **AND** `tree.debug.widgets` SHALL include both, in registration order

#### Scenario: find returns instance by type

- **GIVEN** a custom widget `AxesWidget : WorldDebugWidget` registered exactly once
- **WHEN** `tree.debug.find<AxesWidget>()` is called
- **THEN** the registered instance SHALL be returned

#### Scenario: Two SceneTrees do not share registry state

- **GIVEN** two distinct `SceneTree` instances `treeA` and `treeB`
- **WHEN** `treeA.debug.momentum.enabled = true` is set
- **THEN** `treeB.debug.momentum.enabled` SHALL remain `false`
- **AND** `treeA.debug.momentum` and `treeB.debug.momentum` SHALL be distinct instances

### Requirement: Engine auto-inserts DebugLayer with two sub-containers

The engine SHALL auto-insert a `DebugLayer` (a `Node`) as a child of `SceneTree.root` during `SceneTree.start()`, after the root's own `onEnter` has fired. The `DebugLayer` SHALL have a stable name `"__debug"` and SHALL contain exactly two child containers:

- `WorldDebugContainer` (a `Node2D` directly under `DebugLayer`) — hosts `WorldDebugWidget` instances. Participates in the world pass of `SceneTree.render`, receiving the active `Camera2D` view transform.
- `ScreenDebugCanvas` (a `CanvasLayer` with `layer = Int.MAX_VALUE - 1`) — hosts `ScreenDebugWidget` instances. Painted in the UI pass on top of any game UI.

The engine SHALL register the five built-in widgets — `FpsWidget`, `ColliderWidget`, `MomentumWidget`, `LogOverlayWidget`, `DebugHud` — during the auto-insertion, in that order. The engine SHALL additionally insert an internal `DebugToggleNode` inside `ScreenDebugCanvas` that polls input each tick (see "DebugHud opens and closes via debugHudKey").

Re-inserting on a re-attached tree (stop → start) SHALL be idempotent — the engine SHALL skip the addition when a child named `"__debug"` is already present on root.

#### Scenario: DebugLayer is present in every started tree

- **WHEN** `SceneTree(root).start()` has been called on any bundle or programmatic root
- **THEN** `tree.root.findChild("__debug")` SHALL return a `DebugLayer` instance
- **AND** that `DebugLayer` SHALL contain exactly one `WorldDebugContainer` child and one `ScreenDebugCanvas` child

#### Scenario: Auto-insert is idempotent across re-start

- **WHEN** a `SceneTree` is started, stopped, and started again on the same root
- **THEN** root SHALL contain exactly one child named `"__debug"`

#### Scenario: Screen-space built-ins are hosted in the screen container

- **WHEN** the engine has finished auto-inserting `DebugLayer`
- **THEN** `tree.debug.colliders.parent` SHALL be the `WorldDebugContainer` instance
- **AND** `tree.debug.fps.parent`, `tree.debug.momentum.parent`, `tree.debug.log.parent`, and `tree.debug.hud.parent` SHALL all be the `ScreenDebugCanvas` instance
