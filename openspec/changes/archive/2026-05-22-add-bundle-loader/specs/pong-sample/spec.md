## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Pong manifest declares script compilation order

**Reason**: O `BundleLoader` agora descobre os scripts via tree-walk no `scene.json` e o `KotlinScriptingHost` interno resolve cross-references via round-robin / fixed-point (vide capability `bundle-loading`). Não há mais necessidade — nem oportunidade — de declarar um manifesto manual no `Main.kt` do Pong.

**Migration**: A lista literal `manifest = listOf(...)` em `Main.kt` é deletada. Nenhum código de jogo precisa declarar ordem de compilação.
