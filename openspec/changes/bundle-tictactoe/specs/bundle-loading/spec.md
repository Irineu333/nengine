## MODIFIED Requirements

### Requirement: BundleLoader root accepts typed Scene with script

The root entry of a `scene.json` MAY declare a `type` field referencing a class registered in `NodeRegistry`. When `root.type == "com.neoutils.engine.scene.Scene"`, the `BundleLoader` MUST instantiate a plain `Scene`, apply `root.name` and `root.properties` if present, attach `root.script` to that `Scene` instance (when declared), and use the result as the loaded scene. When `root.type` is absent, the `BundleLoader` MUST fall back to instantiating an anonymous `Scene` (existing behavior for legacy bundles).

The `NodeRegistry` MUST register `com.neoutils.engine.scene.Scene` so that `BundleLoader` and `PythonScriptHost` can both resolve it. Although `Scene` is conceptually only valid as a root, the registry MAY register it without a runtime guard; placing a `Scene` as a child node is programmer error not detected by the loader.

#### Scenario: Root with type Scene and script attaches the script to the Scene

- **GIVEN** a bundle whose `scene.json` declares `root.type = "com.neoutils.engine.scene.Scene"` and `root.script = "scripts/orchestrator.py"`
- **WHEN** `BundleLoader.fromResources` is called
- **THEN** the returned `Scene` instance has a non-null `scriptInstance` whose `Script.path` is `scripts/orchestrator.py`

#### Scenario: Legacy bundle without root.type still loads

- **GIVEN** a bundle whose `scene.json` omits `root.type` entirely (legacy format)
- **WHEN** `BundleLoader.fromResources` is called
- **THEN** the loader instantiates a plain `Scene` and applies `root` children as before
- **AND** no error is raised by the absence of `root.type`

### Requirement: Bundle loading is backend-agnostic

The `Scene` produced by `BundleLoader.fromResources(name)` or `BundleLoader.fromPath(dir)` MUST be consumable by any `GameHost` implementation without ajuste — specifically, both `SkikoHost` (in `:engine-skiko`) and `ComposeHost` (in `:engine-compose`) MUST be able to receive the produced `Scene` and run it via their normal `run(scene, config)` entry point. The bundle pipeline MUST NOT depend on backend-specific types or assumptions.

#### Scenario: Pong scene runs in SkikoHost

- **GIVEN** the Pong bundle at `games/pong/src/main/resources/pong/`
- **WHEN** `BundleLoader.fromResources("pong")` is consumed by `SkikoHost().run(scene, config)`
- **THEN** the game runs as expected (existing behavior, regression sentinel)

#### Scenario: Tic-tac-toe scene runs in ComposeHost

- **GIVEN** the Tic-tac-toe bundle at `games/tictactoe/src/main/resources/tictactoe/`
- **WHEN** `BundleLoader.fromResources("tictactoe")` is consumed by `ComposeHost().run(scene, config)`
- **THEN** the game runs as expected
- **AND** the consumer (`:games:tictactoe`) does not call into any Skiko-specific or backend-specific code path

#### Scenario: No backend-specific imports in BundleLoader

- **WHEN** the `:engine-bundle` source tree is inspected
- **THEN** no class references `androidx.compose.*`, `org.jetbrains.compose.*`, or `org.jetbrains.skiko.*`
