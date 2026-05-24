## ADDED Requirements

### Requirement: Node base class supports non-visual logical nodes

The engine SHALL support `Node` subclasses that do NOT extend `Node2D` as first-class scene members. Such nodes MUST receive `onEnter`, `onProcess`, `onPhysicsProcess`, and `onExit` lifecycle callbacks like any other `Node`, MUST be registrable in `NodeRegistry`, and MUST be loadable from `scene.json`. They MUST NOT participate in transform composition, draw traversal, or collision iteration since they carry no spatial state. `Timer` is the first such node introduced; the engine SHALL preserve this capability as a precedent for future logical nodes (e.g. `AudioPlayer`, `AnimationPlayer`).

#### Scenario: A non-Node2D subclass receives lifecycle hooks

- **GIVEN** a `Node` subclass that does NOT extend `Node2D` (such as `Timer`) added as a child of a live scene root
- **WHEN** the game loop runs one frame
- **THEN** the subclass receives `onEnter`, then `onProcess(dt)`, then `onPhysicsProcess(dt)` at the fixed step

#### Scenario: A non-Node2D subclass is skipped by draw traversal

- **GIVEN** a `Timer` instance attached as a child of the scene root
- **WHEN** the engine performs the draw pass for the frame
- **THEN** no `onDraw` invocation happens on the `Timer`
- **AND** no transform composition is attempted for it
