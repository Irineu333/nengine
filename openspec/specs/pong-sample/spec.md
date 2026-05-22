# pong-sample Specification

## Purpose

Jogo de Pong jogável (humano vs IA) como módulo executável `:games:pong`, servindo como teste de aceitação vivo da fundação da engine. Exercita scene graph, lifecycle, renderer, input, colisão e game loop fim a fim.
## Requirements
### Requirement: Pong is an executable standalone module

O projeto SHALL prover um módulo `:games:pong` que depende de `:engine`, `:engine-skiko` e `:engine-bundle`, e contém um entry point `main()` que abre uma janela hospedando Pong via `SkikoHost`. O módulo MUST ser executável via `./gradlew :games:pong:run`. O módulo MUST NOT depender de nenhum outro módulo de jogo. O `Main.kt` SHALL carregar a cena via `BundleLoader.fromResources("pong")` por padrão e MAY aceitar um path opcional via argumento de programa para carregar via `BundleLoader.fromPath(File(args[0]))` (cenário de editor / verificação de disco). O `Main.kt` NÃO SHALL instanciar manualmente nenhum host de scripting, nem registrar tipos da engine no `NodeRegistry`, nem declarar manifesto de scripts.

#### Scenario: Pong runs from Gradle

- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run` da raiz do projeto
- **THEN** uma janela desktop abre exibindo a cena Pong
- **AND** o jogo é responsivo a input de teclado

#### Scenario: Pong loads from a filesystem bundle when a path argument is provided

- **GIVEN** uma pasta `<dir>` que é um bundle Pong válido (`scene.json` + `scripts/`)
- **WHEN** um desenvolvedor executa `./gradlew :games:pong:run --args="<dir>"`
- **THEN** o `Main.kt` resolve o bundle via `BundleLoader.fromPath(File(<dir>))`
- **AND** o jogo abre com a mesma cena que `fromResources("pong")` produziria sobre o mesmo conteúdo

#### Scenario: Pong uses only public engine API

- **WHEN** o source de `:games:pong` é inspecionado
- **THEN** todas as interações com engine passam por tipos exportados por `:engine`, `:engine-skiko` e `:engine-bundle`
- **AND** nenhuma API interna/privada desses módulos é referenciada

#### Scenario: Pong depends on engine-bundle, not engine-scripting

- **WHEN** o build configuration de `:games:pong` é inspecionada
- **THEN** declara dependência em `:engine-bundle`
- **AND** NÃO declara dependência em `:engine-scripting`

#### Scenario: Main.kt is concise

- **WHEN** o source de `:games:pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** o corpo de `main()` se resume a escolher entre `BundleLoader.fromResources("pong")` e `BundleLoader.fromPath(File(args[0]))` (a escolha é o único condicional admissível) seguido de uma única chamada a `SkikoHost().run(...)`
- **AND** NÃO contém referência a `KotlinScriptingHost`, `ScriptHosts`, `NodeRegistry.registerEngineTypes()`, `classLoader.getResource`, nem manifesto de scripts

### Requirement: Pong scene composition

The Pong scene SHALL contain the following node tree: two `Paddle` nodes (left labeled "left", right labeled "right"), a `Ball` node, four wall/goal `Collider` nodes (top, bottom, left goal, right goal), and a HUD subtree with two `Score` text nodes and an optional center-line decoration. Each `Paddle` MUST carry a child `BoxCollider` whose `size` mirrors the paddle's `size`. The `Ball` MUST itself extend `BoxCollider` (the ball **is** its collider, not a node that contains one) — no anonymous `BoxCollider` subclass MAY be used in the Pong codebase. The wall and paddle-child colliders SHALL be plain `com.neoutils.engine.physics.BoxCollider` instances; Pong MUST NOT declare empty `BoxCollider` subclasses (e.g. `Wall`, `PaddleCollider`) in scripts or Kotlin source.

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
- **THEN** no file declares an empty subclass of `BoxCollider` (i.e. a class whose body is empty or only contains property defaults with no overrides)

### Requirement: Player paddle responds to keyboard input

The left paddle SHALL move vertically in response to keyboard input: a configured "up" key moves it up, a "down" key moves it down. Default bindings MUST be `W` for up and `S` for down. Movement MUST be frame-rate independent using `dt`. The paddle MUST be clamped to remain within the play field (between top and bottom walls).

#### Scenario: Holding W moves the left paddle up

- **WHEN** the user holds the `W` key for one second at default speed
- **THEN** the left paddle's vertical position decreases by `speed * 1.0` units (Y axis grows downward)
- **AND** does not pass the top wall

#### Scenario: Releasing key stops paddle

- **WHEN** the user releases both `W` and `S`
- **THEN** the left paddle's position remains constant in subsequent ticks

### Requirement: AI paddle tracks the ball

The right paddle SHALL be controlled by an AI routine that, each tick, moves the paddle vertically toward the ball's current vertical position, capped at a configurable maximum speed. The AI MUST be intentionally imperfect (max speed strictly less than ball max vertical speed, or with a tolerance band) so the human can score. The paddle SHALL identify the ball via a `NodeRef<Node2D>` property (e.g. `target`) so that the relationship is declarative and survives scene serialization; lambdas-in-constructor MUST NOT be used to express this dependency.

#### Scenario: AI paddle moves toward ball

- **WHEN** the ball's center is below the right paddle's center by more than the AI tolerance
- **THEN** on the next tick the right paddle moves downward (limited by its max speed)

#### Scenario: AI paddle does not exceed max speed

- **WHEN** the ball is far from the right paddle
- **THEN** the paddle's displacement per tick does not exceed `aiMaxSpeed * dt`

#### Scenario: AI target is a NodeRef, not a lambda

- **WHEN** the `Paddle` class is inspected
- **THEN** the property used to point at the ball is of type `NodeRef<Node2D>` (or a subtype)
- **AND** no `() -> Float`, `() -> Vec2`, or similar lambda property is declared for that purpose

### Requirement: Ball physics

The Ball SHALL move with a constant-magnitude velocity vector each tick (`position += velocity * dt`). The Ball MUST reflect on the X or Y axis when its collider overlaps with a paddle or wall: collisions with top/bottom walls reflect Y; collisions with paddles reflect X. The Ball's speed MAY increase modestly on each paddle hit, capped to a configured maximum.

#### Scenario: Ball reflects off top wall

- **WHEN** the ball overlaps the top wall while moving upward
- **THEN** the Y component of its velocity is negated
- **AND** the X component is unchanged

#### Scenario: Ball reflects off a paddle

- **WHEN** the ball overlaps either paddle's collider
- **THEN** the X component of its velocity is negated
- **AND** the ball does not pass through the paddle in the next tick

### Requirement: Score tracking and ball reset

When the ball's collider overlaps a goal collider, the opposite side's score MUST increment by one and the ball MUST reset to the field center with a randomized direction. Score values MUST be reflected in the HUD `Score` text nodes within the same frame. The scoring event SHALL be communicated from the `Ball` to the `Score` nodes (or to `PongScene`) via a `Signal<Goal.Side>` exposed by the ball; lambdas-in-constructor MUST NOT be used to wire the scoring callback. The `Ball.onCollide` dispatch SHALL identify collision categories by scene structure — `other is Goal`, `other.parent is Paddle`, or fall-through to plain `BoxCollider` (wall) — and MUST NOT branch on `::class.simpleName` string literals for the paddle and wall cases.

#### Scenario: Ball crossing right goal scores for left

- **WHEN** the ball reaches the right goal
- **THEN** the left `Score` text displays an incremented value
- **AND** the ball is positioned at the field center on the next tick

#### Scenario: Score persists across multiple goals

- **WHEN** the left player has scored 3 goals
- **THEN** the left `Score` text displays "3"
- **AND** is not reset by subsequent ball resets

#### Scenario: Ball exposes a scoring Signal

- **WHEN** the `Ball` class is inspected
- **THEN** it exposes a public `Signal<Goal.Side>` (e.g. `onScore`) that is emitted whenever a goal collision occurs
- **AND** no `(Goal.Side) -> Unit` parameter exists on its constructor

#### Scenario: Ball dispatch uses structural checks for paddle and wall

- **WHEN** the body of `Ball.onCollide` is inspected
- **THEN** the paddle case is selected by an `is Paddle` check on `other.parent` (or equivalent structural test)
- **AND** the wall case is reached as the fall-through for `BoxCollider` instances that are neither `Goal` nor a paddle child
- **AND** no `when` branch compares `other::class.simpleName` (or `.java.simpleName`) to the strings `"Wall"` or `"PaddleCollider"`

### Requirement: Pong validates the engine surface end to end

The Pong module SHALL exercise all of the following engine capabilities: `Node` lifecycle (`onEnter`/`onUpdate`/`onRender`/`onExit`), `Transform`-based positioning, `Renderer` primitives (rect, circle, text), `Input` queries (keys), `Collider` + `PhysicsSystem` with at least one `onCollide` handler per moving node, `GameLoop` driving via `GameHost`, the `scene-serialization` primitives (`Signal<T>`, `NodeRef<T>`, `@Inspect`-annotated properties), the `SceneLoader` round-trip on `PongScene`, and the `scripting` capability (every gameplay node type with non-trivial behavior is defined in a `.nengine.kts` script). Pure tag-only subclasses of engine types MUST NOT exist as scripts or Kotlin classes; engine types (e.g. `BoxCollider`) SHALL be used directly when a gameplay node carries no behavior beyond what the engine type already provides.

#### Scenario: Every engine capability has at least one usage in Pong

- **WHEN** the Pong source is reviewed against the `engine-core`, `skiko-runtime`, `scene-serialization`, and `scripting` capability lists
- **THEN** at least one usage of each listed feature is present

#### Scenario: Pong uses Signal for ball-to-score communication

- **WHEN** the Pong source is inspected for the wiring between `Ball` and `Score` nodes
- **THEN** the wiring is expressed via `Signal<T>` registrations (e.g. inside `onEnter`)

#### Scenario: Pong uses NodeRef for AI-to-ball reference

- **WHEN** the Pong source is inspected for the wiring between the AI paddle and the ball
- **THEN** the wiring is expressed via a `NodeRef<Node2D>` declared on the paddle and resolved at update time

#### Scenario: Pong exposes @Inspect properties

- **WHEN** the public configurable properties of the script-defined `Paddle`, `Ball`, `Score`, `Goal`, `CenterLine`, and `PongScene` are inspected
- **THEN** every property intended as initial configuration is annotated with `@Inspect`
- **AND** every property holding transient runtime state is annotated with `@Transient`

#### Scenario: Every Pong gameplay node with behavior is defined by a script

- **WHEN** the Pong source tree under `:games:pong/src/main/kotlin` is inspected
- **THEN** no class extending `com.neoutils.engine.scene.Node` (directly or transitively) is declared in Kotlin source
- **AND** every node type referenced from `pong.scene.json` either resolves through `ScriptHost` (gameplay scripts) or maps to a built-in registered in `NodeRegistry.registerEngineTypes()` (engine types used as-is, e.g. `BoxCollider`)

### Requirement: Pong nodes have no-args constructors

Every script `.nengine.kts` file that defines a `Node` subclass used by Pong SHALL declare a primary no-args constructor (either explicitly or implicitly via the default constructor of an open subclass). All initial configuration SHALL be expressed as `var` properties on the class, each annotated with `@Inspect` (serialized contract) or `@Transient` (runtime-only state). The class itself MAY OR MAY NOT carry `@Serializable` from `kotlinx.serialization`; the `SceneLoader` does not depend on the class-level annotation, only on per-property reflection.

#### Scenario: Each Pong script class can be instantiated with no arguments

- **GIVEN** the active `ScriptHost` has compiled every script listed in Pong's manifest
- **WHEN** code calls `host.factoryFor(path)()` for each script path in the manifest
- **THEN** each call returns a valid instance with default property values

#### Scenario: Pong script vars carry @Inspect or @Transient

- **WHEN** each Pong script's top-level class is inspected
- **THEN** every `var` property is annotated either with `@Inspect` or with `@Transient`

### Requirement: Pong ships gameplay nodes as scripts under resources

O módulo `:games:pong` SHALL servir um bundle de cena sob `src/main/resources/pong/` contendo `scene.json` na raiz e `scripts/` com um `.nengine.kts` por tipo de gameplay com comportamento próprio. No mínimo o diretório `scripts/` SHALL conter: `paddle.nengine.kts`, `ball.nengine.kts`, `goal.nengine.kts`, `score.nengine.kts`, `center-line.nengine.kts`, e `pong-scene.nengine.kts`. Subclasses tag-only (`paddle-collider.nengine.kts`, `walls.nengine.kts`) MUST NOT existir — as entradas correspondentes em `scene.json` referenciam `com.neoutils.engine.physics.BoxCollider` por FQN. Cada script SHALL definir exatamente uma classe top-level estendendo `Node` (ou subclasse). As classes SHALL implementar o comportamento de gameplay previamente em Kotlin (movimento, colisão, IA, scoring) sem diferença observável em relação ao build anterior.

#### Scenario: Bundle directory exists with expected layout

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/` é listado
- **THEN** há um arquivo `scene.json` na raiz do bundle
- **AND** há um diretório `scripts/` contendo cada um dos arquivos `.nengine.kts` esperados
- **AND** cada arquivo é não-vazio e parseável como script Kotlin válido

#### Scenario: Bundle directory excludes tag-only files

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/scripts/` é listado
- **THEN** nenhum arquivo `paddle-collider.nengine.kts` está presente
- **AND** nenhum arquivo `walls.nengine.kts` está presente

#### Scenario: Old flat layout is removed

- **WHEN** o conteúdo de `:games:pong/src/main/resources/` é listado
- **THEN** não há arquivo `pong.scene.json` na raiz dos resources
- **AND** não há diretório `scripts/` na raiz dos resources

#### Scenario: Loaded Pong matches the previous Kotlin-only behavior

- **WHEN** a janela do Pong é lançada após a migração para bundle
- **THEN** o layout inicial (paddles, ball, walls, goals, HUD) corresponde ao layout produzido pela construção anterior de `PongScene`
- **AND** a resposta a input é idêntica
- **AND** o comportamento da IA é idêntico
- **AND** scoring e reset da bola são idênticos

### Requirement: pong.scene.json references scripts by path

O arquivo `pong/scene.json` (raiz do bundle Pong) SHALL referenciar cada nó de gameplay que carrega comportamento próprio do Pong pelo seu path de script sob `scripts/` relativo ao bundle (ex.: `"type": "scripts/paddle.nengine.kts"`). Nenhum campo `type` em `scene.json` SHALL ser o FQN de uma classe `:games:pong`-owned. Nodes cujo comportamento é fornecido por um tipo da engine (ex.: walls como `BoxCollider`) SHALL referenciar esse tipo pelo seu FQN (`"type": "com.neoutils.engine.physics.BoxCollider"`), resolvido pelo `NodeRegistry`. Caminhos de scripts MUST ser relativos ao bundle (sem prefixo `pong/`).

#### Scenario: All Pong-owned types in scene.json are script paths

- **WHEN** `pong/scene.json` é parseado e todos os campos `type` coletados
- **THEN** todo `type` cuja classe origina-se de `:games:pong` é uma string terminando em `.nengine.kts`
- **AND** todo `type` de script começa com `scripts/` (relativo ao bundle, sem prefixo `pong/`)

#### Scenario: Wall nodes use engine BoxCollider by FQN

- **WHEN** `pong/scene.json` é parseado
- **THEN** as entradas nomeadas `topWall` e `bottomWall` têm `type` igual a `com.neoutils.engine.physics.BoxCollider`

#### Scenario: pong/scene.json round-trips

- **WHEN** código chama `BundleLoader.fromResources("pong")` e então `SceneLoader.save(scene)`
- **THEN** o JSON resultante é equivalente ao original (após canonicalização)

