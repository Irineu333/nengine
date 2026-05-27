# project-conventions Specification

## Purpose

Documento `CLAUDE.md` na raiz consolidando propósito do projeto, invariantes arquiteturais, estrutura de módulos, convenções de código, workflow OpenSpec e roadmap. Funciona como decision log perene para contribuidores humanos ou agentes.

## Requirements

### Requirement: CLAUDE.md exists at repository root

The repository SHALL contain a `CLAUDE.md` file at the project root. The file MUST be kept under version control and updated when foundational decisions change. The file MUST be written in a way that lets a new contributor (human or AI) get oriented without reading the entire codebase first.

#### Scenario: Fresh checkout includes the document

- **WHEN** a developer clones the repository
- **THEN** `CLAUDE.md` is present at the root

### Requirement: CLAUDE.md states the project purpose

The `CLAUDE.md` SHALL include a section explicitly describing the project's purpose: a 2D game engine built for learning, starting code-only with sample games and evolving toward a visual editor.

#### Scenario: Purpose section is present

- **WHEN** `CLAUDE.md` is opened
- **THEN** a "Purpose" (or equivalently titled) section appears near the top
- **AND** it explains the project as a 2D game engine for learning

### Requirement: CLAUDE.md enumerates invariant architectural decisions

The `CLAUDE.md` SHALL list the architectural invariants that any change must respect, including at minimum: (1) scene graph style Godot (inheritance, no Unity-style components), (2) `:engine` has no dependency on any UI or render framework (no `androidx.compose.*`, `org.jetbrains.compose.*`, `org.jetbrains.skia.*`, `org.jetbrains.skiko.*`, no Swing/AWT widget types, no future LWJGL/OpenGL/Vulkan bindings), (3) collision uses `Collider`-as-node with a central `PhysicsSystem`, (4) `Renderer`, `Input` and `GameHost` are SPIs; Skiko is the only render backend currently active (`:engine-skiko`), no other module is allowed to leak Skiko types into `:engine`, and an LWJGL-based experimental backend is planned to revalidate the SPI, (5) **the live tree is owned by a `SceneTree` that is not a `Node` and not `@Serializable`; a `Scene` class no longer exists in `:engine`; nodes reach the tree via the cached `Node.tree` property (set on attach, cleared on detach); `SceneTree` is not subclassable for setup — a root `Node` with `onEnter()` populates the tree; `SceneLoader.load` and `BundleLoader` return `Node` (root-type free); the host wraps the root in `SceneTree(root = ...)` before `run(...)`**.

#### Scenario: Invariants section enumerates the core decisions

- **WHEN** `CLAUDE.md` is opened
- **THEN** the invariants section lists at least the five decisions above with one-line rationale each
- **AND** invariant (2) is worded generically over UI/render frameworks (not Compose-specific)
- **AND** invariant (4) explicitly names `GameHost` as an SPI alongside `Renderer` and `Input`
- **AND** invariant (4) identifies Skiko as the only render backend currently active
- **AND** invariant (4) explicitly forbids `:engine` from referencing Skiko types
- **AND** invariant (4) names LWJGL as the planned experimental second backend that will revalidate the SPI
- **AND** invariant (5) is present and explicitly states that `SceneTree` is not a `Node` and that `Scene` has been removed
- **AND** invariant (5) names `Node.tree` as the cached access path from any live node
- **AND** invariant (5) prescribes the host-wraps-root pattern (`Host.run(SceneTree(root = ...), config)`)

#### Scenario: No mention of Compose as second backend remains

- **WHEN** `CLAUDE.md` is opened
- **THEN** no paragraph in the invariants section names Compose Multiplatform as a second render backend
- **AND** no paragraph references `:engine-compose` as a current module

### Requirement: CLAUDE.md describes module structure and how to run

O `CLAUDE.md` SHALL descrever o layout de módulos do projeto (`:engine`, `:engine-bundle`, `:engine-bundle-python`, `:engine-bundle-lua`, `:engine-skiko`, `:games:<name>`) e o comando para rodar um módulo de jogo (`./gradlew :games:<name>:run`). O documento MUST esclarecer que todos os jogos rodam em Skiko (Pong, Demos, Hello-World, Tic Tac Toe). O documento MUST NOT listar `:engine-compose` na seção de módulos — esse módulo foi removido. A remoção dos módulos `:desktopApp` e `:shared` do template MUST permanecer registrada. O módulo `:engine-scripting` MUST NÃO aparecer no documento — foi absorvido por `:engine-bundle` e em seguida substituído por `:engine-bundle-python` como local de scripting. A linha do `:engine-bundle` MUST descrever-lo como hospedeiro do `BundleLoader` e da **SPI `ScriptHost`** (agnóstica de linguagem), sem mencionar Kotlin Scripting. A linha do `:engine-bundle-python` MUST descrevê-lo como implementação de `ScriptHost` para Python via GraalPy. A linha do `:engine-bundle-lua` MUST descrevê-lo como implementação de `ScriptHost` para Lua via LuaJ. A descrição de `:games:tictactoe` MUST identificá-lo como "jogo Velha (humano vs humano), roda em Skiko com scripting Lua — sentinela do segundo backend de scripting" — não mais como sentinela do segundo backend de render.

#### Scenario: Module structure section is accurate

- **WHEN** um desenvolvedor compara a seção com `settings.gradle.kts`
- **THEN** os módulos listados batem com o grafo real do projeto
- **AND** `:engine-bundle` aparece ao lado de `:engine` e `:engine-skiko`
- **AND** `:engine-bundle-python` aparece listado separadamente
- **AND** `:engine-bundle-lua` aparece listado separadamente
- **AND** `:engine-compose` NÃO aparece em nenhum lugar do documento como módulo ativo
- **AND** `:engine-scripting` NÃO aparece nem na seção de módulos nem no roadmap como módulo ativo

#### Scenario: All games run on Skiko

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção de módulos nomeia Skiko como backend usado por `:games:pong`, `:games:demos`, `:games:hello-world` e `:games:tictactoe`
- **AND** NÃO menciona Compose como backend de nenhum jogo

#### Scenario: Tictactoe role is described as Lua scripting sentinel

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a descrição de `:games:tictactoe` identifica seu papel como sentinela do segundo backend de scripting (Lua)
- **AND** NÃO o identifica como sentinela do segundo backend de render

#### Scenario: engine-bundle responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle` descreve sua responsabilidade como "carregar cena via bundle (scene.json + scripts/) e hospedar a SPI `ScriptHost` agnóstica de linguagem"
- **AND** não menciona Kotlin Scripting nem `.nengine.kts` como mecanismo vigente

#### Scenario: engine-bundle-python responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-python` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Python `.py`, usando GraalPy"
- **AND** indica que jogos que usam Python scripting declaram dependência neste módulo

#### Scenario: engine-bundle-lua responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle-lua` descreve sua responsabilidade como "implementação concreta de `ScriptHost` para scripts Lua `.lua`, usando LuaJ"
- **AND** indica que jogos que usam Lua scripting declaram dependência neste módulo

#### Scenario: Run instructions work as written

- **WHEN** um desenvolvedor executa o comando mostrado para Pong, Tic Tac Toe, Demos ou Hello-World
- **THEN** o jogo inicia sem passos adicionais

### Requirement: CLAUDE.md states coding conventions

The `CLAUDE.md` SHALL state the project's coding conventions, including at minimum: comments are added only for non-obvious "why" (never for what is already self-evident from naming); public API of `:engine` is documented with KDoc; engine identifiers are in English while in-game text in sample games MAY be in Portuguese. The conventions section SHALL also describe the **Python scripting contract** for the new world:

- Scripts são módulos `.py` em `bundle/scripts/`.
- A primeira linha não-vazia declara `extends <NodeType>` (docstring ou comentário).
- Exports são atribuições anotadas top-level com tipos do conjunto suportado (primitivos, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `Optional[T]`).
- Hooks são funções nomeadas `on_enter`, `on_update`, `on_render`, `on_collide` (snake_case).
- Estado runtime-only vive em `self._private = ...` dentro de hooks, não no top-level.

A seção sobre o contrato `@Inspect` / `@Transient` (mundo Kotlin Scripting) MAY ser mantida para classes Kotlin compiladas que ainda precisam aparecer em `scene.json` (ex.: tipos da engine), mas MUST esclarecer que `.nengine.kts` deixou de existir.

#### Scenario: Conventions section is present

- **WHEN** `CLAUDE.md` is opened
- **THEN** a "Conventions" (or equivalently titled) section enumerates the rules above

#### Scenario: Python scripting contract is documented

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção de convenções (ou subseção dedicada) descreve as cinco regras do contrato Python: extends, exports, hooks, estado interno via `self._private`, tipos suportados
- **AND** dá ao menos um exemplo curto de script Python válido

#### Scenario: Kotlin scripting contract is no longer the active mechanism

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção "Scripting contract" (atual) NÃO descreve `.nengine.kts` como mecanismo vigente
- **AND** se houver referência histórica, deixa claro que foi substituída por Python via GraalPy nesta change

### Requirement: CLAUDE.md describes the OpenSpec workflow and roadmap

O `CLAUDE.md` SHALL explicar que mudanças materiais (arquitetura, API pública, novos módulos, novas capabilities) passam por proposta OpenSpec antes da implementação, e SHALL incluir um roadmap visível apontando para changes ativas e planejadas. O roadmap MUST listar cada change arquivada com status `Archived`, incluindo `engine-foundation`, `add-tictactoe`, `engine-consistency`, `add-skiko-runtime`, `prepare-for-serialization`, `add-scripting`, `drop-pong-tag-only-scripts`, `add-bundle-loader` e `add-python-scripting` após esta change ser arquivada. O roadmap MUST ser atualizado quando uma change ativa avança.

#### Scenario: Workflow section refers contributors to OpenSpec

- **WHEN** um contribuidor quer propor uma feature
- **THEN** a seção de workflow direciona a criar uma change OpenSpec em vez de abrir um PR direto

#### Scenario: Roadmap reflects current state

- **WHEN** `CLAUDE.md` é lido ao final desta change
- **THEN** o roadmap inclui uma linha para `add-python-scripting` com status `Archived`
- **AND** o resumo da linha menciona que a SPI `ScriptHost` agnóstica foi introduzida em `:engine-bundle`, que `:engine-bundle-python` é a primeira impl (GraalPy), e que Pong migrou de `.nengine.kts` para `.py`
