## MODIFIED Requirements

### Requirement: NodeRegistry maps type names to factories

A engine SHALL prover um `NodeRegistry` que mantém um mapeamento **bidirecional** entre um identificador `String` e o par `(KClass<out Node>, factory: () -> Node)`. O identificador é o que aparece no campo `type` do JSON: para tipos compilados em Kotlin, é o FQN; para scripts, é o path do script relativo ao bundle (ex.: `scripts/paddle.nengine.kts`). A API MUST incluir, no mínimo:

- `register(identifier: String, klass: KClass<out Node>, factory: () -> Node)` — registro explícito do mapeamento bidirecional.
- `create(identifier: String): Node` — invoca a factory; lança `UnknownNodeTypeException` se `identifier` não está registrado.
- `identifierFor(klass: KClass<out Node>): String?` — devolve o identificador associado à classe, ou `null` se a classe nunca foi registrada.
- `registerEngineTypes()` — idempotente; registra todos os tipos `Node` concretos publicados por `:engine` usando seus FQN.
- `clear()` — descarta todos os registros (apenas para uso em testes).

Tipos com identificador terminando em `.kts` MUST ser tratados pelo registry como qualquer outro tipo — não há mais ramo especial. O `BundleLoader` (em `:engine-bundle`) MUST popular o registry com mapeamentos `script-path → (class, factory)` antes de chamar `SceneLoader.load`.

#### Scenario: Registered type is instantiable by name

- **GIVEN** código chamou `NodeRegistry.register("com.foo.Paddle", Paddle::class) { Paddle() }`
- **WHEN** o loader encontra uma entrada com `type = "com.foo.Paddle"`
- **THEN** o registry devolve uma instância fresh de `Paddle` via a factory

#### Scenario: Unknown type fails loud

- **GIVEN** nenhum registro feito para `com.example.Mystery`
- **WHEN** o loader encontra uma entrada com `type = "com.example.Mystery"`
- **THEN** o loader lança `UnknownNodeTypeException` cuja mensagem nomeia `com.example.Mystery`

#### Scenario: Script path is a first-class identifier

- **GIVEN** o `BundleLoader` registrou `NodeRegistry.register("scripts/paddle.nengine.kts", PaddleScriptClass::class) { ... }`
- **WHEN** o loader encontra uma entrada com `type = "scripts/paddle.nengine.kts"`
- **THEN** o registry devolve uma instância fresh via a factory
- **AND** o `SceneLoader` não consulta nenhuma SPI externa (nenhum `ScriptHosts`)

#### Scenario: identifierFor recovers the identifier from a KClass

- **GIVEN** `NodeRegistry.register("scripts/paddle.nengine.kts", PaddleScriptClass::class) { ... }`
- **WHEN** código chama `NodeRegistry.identifierFor(PaddleScriptClass::class)`
- **THEN** o resultado é `"scripts/paddle.nengine.kts"`

#### Scenario: identifierFor returns null for unregistered classes

- **WHEN** código chama `NodeRegistry.identifierFor(Node2D::class)` e `Node2D::class` nunca foi registrado
- **THEN** o resultado é `null`

#### Scenario: registerEngineTypes is idempotent

- **WHEN** código chama `NodeRegistry.registerEngineTypes()` duas vezes seguidas
- **THEN** a segunda chamada não lança exceção
- **AND** o estado do registry é equivalente ao de uma única chamada

### Requirement: SceneLoader round-trips a scene to JSON

A engine SHALL prover um `SceneLoader` com duas operações: `save(scene: Scene): String` devolve a representação JSON da cena; `load(json: String): Scene` parseia JSON e devolve uma `Scene` destacada cujo árvore espelha o arquivo. O documento JSON MUST seguir esta forma:

```json
{
  "version": 1,
  "root": {
    "type": "<identificador registrado no NodeRegistry>",
    "name": "<string>",
    "properties": { "<inspect-property-name>": <value>, ... },
    "children": [ <node entry>, ... ]
  }
}
```

O campo `type` MUST ser **um identificador registrado em `NodeRegistry`** — seja um FQN (para tipos compilados) ou um path de script relativo ao bundle (para tipos de script). O `SceneLoader` MUST resolver o tipo exclusivamente por `NodeRegistry.create(type)`; ele MUST NOT discriminar `.kts` nem consultar qualquer SPI externa. Se o tipo não está registrado, o loader MUST lançar `UnknownNodeTypeException`.

A factory invocada produz a instância do nó, depois `name` e `properties` são aplicados via reflection como antes. O `properties` map MUST conter exatamente os valores das propriedades anotadas com `@Inspect`, serializadas via `kotlinx.serialization` JSON. O array `children` MUST preservar a ordem de `parent.children`. Carregar MUST instanciar cada nó, aplicar suas `properties`, e em seguida anexar seus filhos em ordem via `addChild`. Carregar MUST NOT chamar `Scene.start()`; o caller decide quando tornar a cena viva. Save/load do mesmo cena MUST ser idempotente: `save(load(save(scene)))` SHALL ser equivalente a `save(scene)` após canonicalização (whitespace-insensitive, key-ordered).

Quando `SceneLoader.save` serializa um nó, o campo `type` salvo MUST ser obtido por `NodeRegistry.identifierFor(node::class)`. Se o registry não conhece a classe, `save` MUST cair de volta para `node::class.qualifiedName` como último recurso. `save` MUST NOT consultar nenhuma SPI externa.

#### Scenario: save produces well-formed JSON with version and root

- **WHEN** código chama `SceneLoader.save(scene)`
- **THEN** a string devolvida parseia como JSON
- **AND** o objeto top-level tem campos `version` (inteiro 1) e `root` (objeto)
- **AND** `root` tem campos `type`, `name`, `properties`, `children`

#### Scenario: load produces a detached scene

- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** a `Scene` devolvida tem `isLive == false`
- **AND** a cena não está registrada em nenhum `GameLoop`

#### Scenario: load preserves tree shape and inspect properties

- **GIVEN** um documento JSON descrevendo uma cena com três filhos em ordem específica, cada com propriedades `@Inspect`
- **WHEN** código chama `SceneLoader.load(json)` seguido de `Scene.start()`
- **THEN** os filhos aparecem na mesma ordem
- **AND** cada filho tem suas propriedades `@Inspect` com os valores do JSON

#### Scenario: Round-trip is stable

- **GIVEN** uma cena `scene`
- **WHEN** código computa `json1 = SceneLoader.save(scene)` então `scene2 = SceneLoader.load(json1)` então `json2 = SceneLoader.save(scene2)`
- **THEN** `json1` e `json2` são documentos JSON equivalentes

#### Scenario: Loading does not invoke onEnter until start

- **GIVEN** um tipo de nó cujo `onEnter` incrementa um contador
- **WHEN** código chama `SceneLoader.load(json)` em uma cena contendo esse nó
- **THEN** o contador NÃO foi incrementado
- **AND** após chamada subsequente de `scene.start()`, o contador foi incrementado exatamente uma vez

#### Scenario: SceneLoader does not discriminate .kts identifiers

- **WHEN** o source de `SceneLoader.load` e `SceneLoader.save` é inspecionado
- **THEN** não há checagem `endsWith(".kts")`
- **AND** não há import de nenhuma SPI de scripting (`ScriptHost`, `ScriptHosts`)

#### Scenario: Script-typed entry resolves via NodeRegistry

- **GIVEN** o `NodeRegistry` foi previamente populado com `register("scripts/paddle.nengine.kts", ...)` pelo `BundleLoader`
- **WHEN** código chama `SceneLoader.load(json)` para JSON que contém esse identificador
- **THEN** o nó é instanciado via a factory registrada
- **AND** o caminho de código é o mesmo de qualquer outro identificador

#### Scenario: Unknown type fails fast regardless of suffix

- **GIVEN** um JSON com `type = "scripts/foo.nengine.kts"` e nenhum registro prévio para esse identificador
- **WHEN** código chama `SceneLoader.load(json)`
- **THEN** o loader lança `UnknownNodeTypeException` cuja mensagem nomeia o identificador

#### Scenario: save round-trips script-typed nodes by script path via identifierFor

- **GIVEN** uma cena live cuja raiz é um nó cuja `KClass` foi registrada com identificador `"scripts/pong.nengine.kts"`
- **WHEN** código chama `SceneLoader.save(scene)`
- **THEN** o JSON devolvido tem `type = "scripts/pong.nengine.kts"` na raiz
- **AND** NÃO o FQN runtime da classe gerada pelo script

## REMOVED Requirements

### Requirement: ScriptHost exposes a reverse path lookup for save

**Reason**: A responsabilidade de mapear `KClass → identificador` migra para o `NodeRegistry`, agora bidirecional. O `SceneLoader.save` consulta `NodeRegistry.identifierFor(node::class)` em vez de `ScriptHosts.current()?.pathFor(klass)`. A SPI `ScriptHost` deixa de existir em `:engine` (vide capability `scripting`).

**Migration**: Código que dependia de `ScriptHost.pathFor` para descobrir o path de um script deve consultar `NodeRegistry.identifierFor(klass)`. O `BundleLoader` é responsável por popular o registry com o identifier correto na hora de registrar tipos de script.

### Requirement: Pong scene file ships as proof of concept

**Reason**: Este requisito é absorvido e reescrito pela capability `pong-sample` (vide delta correspondente nesta change), que passa a descrever o formato de bundle (`pong/scene.json`, `pong/scripts/`) em vez do antigo `pong.scene.json` na raiz dos resources.

**Migration**: Substituído pelos novos requisitos em `pong-sample`. A cena Pong continua sendo prova viva, mas agora servida como bundle.
