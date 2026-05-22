## MODIFIED Requirements

### Requirement: Pong is an executable standalone module

O projeto SHALL prover um módulo `:games:pong` que depende de `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`, e contém um entry point `main()` que abre uma janela hospedando Pong via `SkikoHost`. O módulo MUST ser executável via `./gradlew :games:pong:run`. O módulo MUST NOT depender de nenhum outro módulo de jogo. O `Main.kt` SHALL carregar a cena via `BundleLoader.fromResources("pong")` por padrão e MAY aceitar um path opcional via argumento de programa para carregar via `BundleLoader.fromPath(File(args[0]))` (cenário de editor / verificação de disco). O `Main.kt` NÃO SHALL instanciar manualmente nenhum host de scripting, nem registrar tipos da engine no `NodeRegistry`, nem declarar manifesto de scripts. A instalação do `PythonScriptHost` MUST ocorrer automaticamente pelo simples fato de `:engine-bundle-python` estar no classpath (via inicialização ansiosa do módulo ou call estática trivial no `main`).

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
- **THEN** todas as interações com engine passam por tipos exportados por `:engine`, `:engine-skiko`, `:engine-bundle` e `:engine-bundle-python`
- **AND** nenhuma API interna/privada desses módulos é referenciada

#### Scenario: Pong depends on engine-bundle and engine-bundle-python

- **WHEN** o build configuration de `:games:pong` é inspecionado
- **THEN** declara dependência em `:engine-bundle`
- **AND** declara dependência em `:engine-bundle-python`
- **AND** NÃO declara dependência em `:engine-scripting` (que não existe)
- **AND** NÃO declara dependência em `kotlin-scripting-*`

#### Scenario: Main.kt is concise

- **WHEN** o source de `:games:pong/src/main/kotlin/.../Main.kt` é inspecionado
- **THEN** o corpo de `main()` se resume a escolher entre `BundleLoader.fromResources("pong")` e `BundleLoader.fromPath(File(args[0]))` (a escolha é o único condicional admissível) seguido de uma única chamada a `SkikoHost().run(...)`
- **AND** NÃO contém referência a `KotlinScriptingHost`, `ScriptHosts` (formato antigo), `NodeRegistry.registerEngineTypes()`, `classLoader.getResource`, nem manifesto de scripts

### Requirement: Pong nodes have no-args constructors and Python @export semantics

Todo arquivo `.py` em `pong/scripts/` SHALL declarar exports como atribuições anotadas no top-level do módulo, no formato `<name>: <Type> = <default>`. As classes/módulos NÃO usam `@Inspect`/`@Transient` (esses são conceitos do mundo Kotlin que sai). Estado em runtime usado apenas internamente SHALL ser declarado dentro de hooks (variáveis locais ou `self._private = ...` em `on_enter`) — não no top-level. Como Python tem snake_case, **os nomes de exports usam snake_case** (`up_key`, `ai_max_speed`); a engine não exige correspondência com camelCase Kotlin.

#### Scenario: Each Pong script has @export-able top-level annotations

- **WHEN** cada `.py` em `pong/scripts/` é inspecionado pelo AST inspector do `PythonScriptHost`
- **THEN** todos os valores de configuração inicial aparecem como `AnnAssign` no top-level
- **AND** cada um vira um `ExportedProperty` na lista `Script.exports`

#### Scenario: Runtime-only state is not exported

- **WHEN** uma variável de estado puramente interno (ex.: o `BoxCollider` filho criado em `on_enter`) é declarada
- **THEN** ela aparece como `self._collider = ...` dentro de `on_enter`, NÃO como atribuição anotada top-level
- **AND** consequentemente não aparece em `Script.exports`

### Requirement: Pong ships gameplay scripts in Python

O módulo `:games:pong` SHALL servir um bundle de cena sob `src/main/resources/pong/` contendo `scene.json` na raiz e `scripts/` com um `.py` por tipo de gameplay com comportamento próprio. No mínimo o diretório `scripts/` SHALL conter: `paddle.py`, `ball.py`, `goal.py`, `score.py`, `center_line.py`, e `pong_scene.py`. Subclasses tag-only NÃO SHALL existir — entradas em `scene.json` que precisam apenas de `BoxCollider` referenciam o tipo por FQN no campo `_type`. Cada `.py` SHALL declarar `extends <NodeType>` na primeira linha não-vazia (docstring ou comentário). O comportamento de gameplay (movimento, colisão, IA, scoring) SHALL ser idêntico ao build anterior em `.nengine.kts`.

#### Scenario: Bundle directory exists with expected Python scripts

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/` é listado
- **THEN** há um arquivo `scene.json` na raiz do bundle
- **AND** há um diretório `scripts/` contendo `paddle.py`, `ball.py`, `goal.py`, `score.py`, `center_line.py`, `pong_scene.py`
- **AND** NÃO há arquivos `.nengine.kts` no bundle

#### Scenario: Bundle directory excludes tag-only scripts

- **WHEN** o conteúdo de `:games:pong/src/main/resources/pong/scripts/` é listado
- **THEN** nenhum arquivo `paddle_collider.py` ou `walls.py` (ou variantes em qualquer extensão) está presente

#### Scenario: Every script declares extends on first non-empty line

- **WHEN** cada arquivo em `pong/scripts/` é inspecionado
- **THEN** a primeira linha não-vazia é uma docstring `"""extends <NodeType>"""` ou comentário `# extends <NodeType>`
- **AND** `<NodeType>` está registrado no `NodeRegistry`

#### Scenario: Loaded Pong matches the previous behavior

- **WHEN** a janela do Pong é lançada após a migração para Python
- **THEN** o layout inicial (paddles, ball, walls, goals, HUD) corresponde ao layout produzido pela versão anterior
- **AND** a resposta a input é idêntica
- **AND** o comportamento da IA é idêntico
- **AND** scoring e reset da bola são idênticos

### Requirement: pong/scene.json uses script slot and props

O arquivo `pong/scene.json` (raiz do bundle Pong) SHALL adotar o novo schema com campos `_type` (Node nativo), `script` (path do `.py` quando o nó carrega comportamento próprio) e `props` (overrides de exports). Nenhum campo `_type` ou `type` em `scene.json` SHALL ser um path terminando em `.nengine.kts` ou `.py`. Nodes cujo comportamento é puramente do tipo da engine (ex.: walls como `BoxCollider`) SHALL referenciar esse tipo por FQN em `_type` (`"_type": "com.neoutils.engine.physics.BoxCollider"`) sem campo `script`. Caminhos de scripts MUST ser relativos ao bundle (sem prefixo `pong/`).

#### Scenario: Pong-owned behavior uses script slot

- **WHEN** `pong/scene.json` é parseado e todos os campos `_type` e `script` coletados
- **THEN** todo nó cuja lógica era previamente em Kotlin tem `_type` apontando para o tipo Node nativo da engine (ex.: `com.neoutils.engine.scene.Node2D`)
- **AND** tem `script` apontando para um arquivo em `scripts/*.py` (sem prefixo `pong/`)
- **AND** nenhum valor de `_type` ou `type` termina em `.nengine.kts` nem em `.py`

#### Scenario: Wall nodes use engine BoxCollider by FQN without script

- **WHEN** `pong/scene.json` é parseado
- **THEN** as entradas nomeadas `topWall` e `bottomWall` têm `_type` igual a `com.neoutils.engine.physics.BoxCollider`
- **AND** o campo `script` é ausente ou nulo nessas entradas

#### Scenario: pong/scene.json round-trips

- **WHEN** código chama `BundleLoader.fromResources("pong")` e então `SceneLoader.save(scene)`
- **THEN** o JSON resultante é equivalente ao original (após canonicalização)
- **AND** os campos `script` e `props` são preservados

## REMOVED Requirements

### Requirement: pong.scene.json references scripts by path

**Reason**: O schema do `scene.json` muda — paths de script saem do campo `type` e vão para um campo `script` dedicado, e o `_type` passa a ser exclusivamente o tipo Node nativo. A requirement antiga, que falava de `type` terminando em `.nengine.kts`, deixa de descrever o estado da arte.

**Migration**: Veja a nova requirement "pong/scene.json uses script slot and props" acima. O mapeamento é direto: `"type": "scripts/paddle.nengine.kts"` vira `"_type": "engine.Node2D"` (ou o Node nativo apropriado) + `"script": "scripts/paddle.py"`. Os valores de configuração vão para o objeto `props`.
