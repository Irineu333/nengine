## MODIFIED Requirements

### Requirement: engine-bundle module hosts bundle loading and the ScriptHost SPI

O projeto SHALL prover um módulo Gradle `:engine-bundle` que depende de `:engine` (e **nada mais** do ecossistema de scripting de runtime). Esse módulo MUST hospedar a API pública `BundleLoader` e a SPI `ScriptHost` / `Script` / `ScriptInstance` / `ExportedProperty` / `ScriptHostRegistry`. O módulo MUST NOT declarar dependência em `org.jetbrains.kotlin:kotlin-scripting-*` (a infraestrutura Kotlin Scripting some integralmente). O módulo MUST NOT declarar dependência em GraalPy nem em qualquer outro runtime de scripting concreto. Apenas jogos que carregam cena via bundle dependem dele. O módulo MUST NOT ser dependência de `:engine`, `:engine-skiko`, ou `:engine-compose`.

#### Scenario: engine-bundle has minimal dependencies

- **WHEN** a build configuration de `:engine-bundle` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** NÃO declara nenhuma dependência `org.jetbrains.kotlin:kotlin-scripting-*`
- **AND** NÃO declara nenhuma dependência `org.graalvm.polyglot:*`
- **AND** NÃO declara nenhuma outra dependência de módulo do projeto

#### Scenario: engine modules do not depend on engine-bundle

- **WHEN** a configuração de build de `:engine`, `:engine-skiko` e `:engine-compose` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle` como dependência, direta ou transitiva

#### Scenario: Games without bundles do not pull engine-bundle transitively

- **WHEN** o runtime classpath de `:games:tictactoe` ou `:games:demos` é resolvido
- **THEN** nenhum artefato de `:engine-bundle` está presente

### Requirement: Scene bundle layout convention

Um **scene bundle** SHALL ser uma pasta autocontida com a forma:

```
<bundle>/
  scene.json          (obrigatório, raiz)
  scripts/            (opcional)
    *.py              (Python via :engine-bundle-python; outras extensões via outros hosts)
  assets/             (reservado; ignorado pela engine nesta change)
```

`scene.json` MUST estar diretamente sob a raiz do bundle e MUST ser o JSON serializado por `SceneLoader.save` (formato `SceneFile`). Os paths internos em `scene.json` para scripts SHALL ser **relativos ao bundle** (ex.: `"script": "scripts/paddle.py"`). A pasta `scripts/` MAY conter arquivos de várias extensões, desde que exista um `ScriptHost` registrado no `ScriptHostRegistry` para cada extensão usada por algum `script` referenciado em `scene.json`. A pasta `assets/` MAY estar ausente; quando presente, esta change não exige que a engine a leia.

#### Scenario: Bundle root has scene.json

- **WHEN** uma pasta é tratada como bundle
- **AND** não há `scene.json` na raiz
- **THEN** o `BundleLoader` lança exceção cuja mensagem nomeia o bundle e indica que `scene.json` está faltando

#### Scenario: Script paths in scene.json are bundle-relative

- **GIVEN** um bundle `pong/` com `scripts/paddle.py`
- **WHEN** `scene.json` referencia esse script via campo `script`
- **THEN** o valor é a string exata `scripts/paddle.py`
- **AND** NÃO é uma string que inclui o nome do bundle (ex.: `pong/scripts/paddle.py`)

#### Scenario: assets/ directory is reserved and ignored

- **GIVEN** um bundle com `assets/` populada
- **WHEN** `BundleLoader.fromResources` ou `fromPath` é chamado para esse bundle
- **THEN** o carregamento ocorre normalmente
- **AND** nenhum erro é lançado pela presença de `assets/`

### Requirement: BundleLoader provides fromResources and fromPath

O módulo `:engine-bundle` SHALL expor um objeto `BundleLoader` com a seguinte API pública:

```kotlin
object BundleLoader {
    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene

    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene
}
```

`fromResources(name)` MUST resolver o bundle como um diretório lógico relativo à raiz do classpath JVM (ex.: `fromResources("pong")` carrega `pong/scene.json` via `ClassLoader.getResource`). `fromPath(bundleDir)` MUST resolver via filesystem. Ambas as funções MUST retornar uma `Scene` destacada (mesmo contrato de `SceneLoader.load`: `isLive == false`). O argumento `types` MUST aceitar tipos `Node` compilados em Kotlin que o jogo precisa expor para o `NodeRegistry` (factory derivada por reflection sobre construtor no-args). Internamente, ambas as funções MUST:

1. Ler `scene.json` via a `BundleSource` correspondente (classpath ou filesystem).
2. Coletar o conjunto de paths de script referenciados na árvore (todo `NodeEntry.script` não nulo).
3. Chamar `ScriptHostRegistry.loadAll(scriptPaths, bundle)` para obter `Map<String, Script>`.
4. Instanciar a árvore via `SceneLoader.load(jsonText, scripts)` (ou equivalente), o qual cria Nodes nativos e atacha `ScriptInstance` aos Nodes cujo `script` foi declarado.

#### Scenario: fromResources returns a detached scene from classpath bundle

- **GIVEN** o classpath contém `pong/scene.json` na raiz dos recursos
- **WHEN** código chama `BundleLoader.fromResources("pong")`
- **THEN** a função retorna uma `Scene` cuja `isLive == false`
- **AND** a árvore reflete o conteúdo de `pong/scene.json`

#### Scenario: fromPath returns a detached scene from a directory

- **GIVEN** uma pasta `/tmp/foo/` com `scene.json` e `scripts/`
- **WHEN** código chama `BundleLoader.fromPath(File("/tmp/foo"))`
- **THEN** a função retorna uma `Scene` cuja `isLive == false`
- **AND** a árvore reflete o conteúdo de `/tmp/foo/scene.json`

#### Scenario: Custom types parameter registers compiled Node classes

- **GIVEN** uma classe custom `class FooNode : Node2D()` em Kotlin compilado
- **AND** `scene.json` contém uma entrada com `type` igual ao FQN de `FooNode`
- **WHEN** código chama `BundleLoader.fromResources("bundle", types = listOf(FooNode::class))`
- **THEN** o nó correspondente é instanciado como `FooNode`
- **AND** se `types` fosse vazio, a chamada falharia com `UnknownNodeTypeException`

#### Scenario: Engine types are auto-registered idempotently

- **GIVEN** `NodeRegistry.clear()` foi chamado antes
- **WHEN** código chama `BundleLoader.fromResources("bundle")` cujo `scene.json` referencia `com.neoutils.engine.physics.BoxCollider`
- **THEN** o carregamento ocorre sem o chamador ter registrado tipos da engine manualmente
- **AND** múltiplas chamadas consecutivas de `BundleLoader.from*` não duplicam registros nem falham

#### Scenario: Missing bundle fails with clear message

- **WHEN** código chama `BundleLoader.fromResources("inexistente")` e não há `inexistente/scene.json` no classpath
- **THEN** uma exceção é lançada cuja mensagem nomeia o argumento `inexistente`

### Requirement: Scripts are discovered by tree-walk on the scene JSON

O `BundleLoader` MUST descobrir quais scripts carregar percorrendo o JSON parseado: para cada `NodeEntry`, se o campo `script` é não-nulo, esse path é adicionado ao conjunto de scripts a carregar. Scripts presentes na pasta `scripts/` mas NÃO referenciados pela árvore MUST NOT ser carregados. Scripts referenciados MUST ser passados para `ScriptHostRegistry.loadAll`. Os paths coletados MUST ser usados exatamente como aparecem no JSON (bundle-relative). A heurística baseada na extensão do `type` (`endsWith(".nengine.kts")`) MUST NOT existir mais — o gatilho é sempre o campo `script`, nunca o `type`.

#### Scenario: Only referenced scripts are loaded

- **GIVEN** um bundle com `scripts/used.py` e `scripts/orphan.py`
- **AND** `scene.json` referencia apenas `scripts/used.py` (via campo `script` em algum nó)
- **WHEN** código chama `BundleLoader.fromResources(name)`
- **THEN** `used.py` é carregado pelo `PythonScriptHost`
- **AND** `orphan.py` NÃO é carregado

#### Scenario: Scripts referenced multiple times load once

- **GIVEN** um `scene.json` em que dois nós distintos têm `script = "scripts/paddle.py"`
- **WHEN** código chama `BundleLoader.fromResources(name)`
- **THEN** o carregamento de `paddle.py` (parse + análise estática) ocorre uma única vez
- **AND** os dois nós recebem `ScriptInstance` distintas do mesmo `Script`

#### Scenario: type field with .py is no longer a script trigger

- **GIVEN** um `scene.json` em que algum nó tem `type = "scripts/something.py"` (uso ilegítimo do campo `type`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** o carregamento falha como tipo desconhecido em `NodeRegistry`
- **AND** o caminho que tratava `.nengine.kts` em `type` não existe mais

## ADDED Requirements

### Requirement: NodeEntry supports script and props fields

O formato `scene.json` SHALL aceitar dois campos opcionais em cada `NodeEntry` além dos existentes:

- `script: String?` — caminho bundle-relative para o arquivo de script a anexar ao Node.
- `props: JsonObject?` — mapa de nome de export para valor; os valores aqui sobrescrevem os defaults declarados nas `ExportedProperty` do script.

Quando `script` é não-nulo, o `BundleLoader` MUST:

1. Instanciar o Node nativo via `NodeRegistry.create(type)`.
2. Atachar o `ScriptInstance` via `ScriptHostRegistry.hostFor(script).attach(node, script)`.
3. Aplicar cada par de `props` chamando `scriptInstance.setExport(name, value)` após coerção de tipo (JSON → tipo Kotlin do export).
4. Armazenar `node.scriptInstance = instance`.

Quando `script` é nulo, o Node se comporta como antes — apenas o tipo nativo, sem `ScriptInstance`. `props` sem `script` MUST ser erro de carregamento.

#### Scenario: Node with script slot is instantiated and attached

- **GIVEN** `scene.json` contém um nó `{ "_type": "engine.Node2D", "script": "scripts/paddle.py", "props": { "speed": 360.0 } }`
- **AND** o `PythonScriptHost` está registrado
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é não-nulo
- **AND** `scriptInstance.setExport("speed", 360.0f)` foi chamado

#### Scenario: Node without script slot has no scriptInstance

- **GIVEN** `scene.json` contém um nó `{ "_type": "engine.Node2D" }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** um `Node2D` é instanciado
- **AND** `node.scriptInstance` é nulo

#### Scenario: Props without script is rejected

- **GIVEN** `scene.json` contém um nó `{ "_type": "engine.Node2D", "props": { "speed": 360.0 } }` (sem `script`)
- **WHEN** `BundleLoader` carrega o bundle
- **THEN** uma exceção é lançada
- **AND** a mensagem indica que `props` exige `script` não-nulo

#### Scenario: Prop type mismatch fails fast

- **GIVEN** um script declara `speed: float = 360.0` e `scene.json` traz `"props": { "speed": "fast" }`
- **WHEN** `BundleLoader` tenta aplicar o prop
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `speed`, o tipo esperado e o valor recebido

## REMOVED Requirements

### Requirement: Round-robin fixed-point compilation resolves cross-references

**Reason**: Algoritmo de compilação round-robin / fixed-point é específico a Kotlin Scripting (necessário porque a compilação Kotlin precisa de tipos de outros scripts no classpath). Python carrega módulos dinamicamente e resolve cross-references em runtime através de imports normais, dispensando o algoritmo inteiro.

**Migration**: Scripts Python que referenciam outros scripts Python o fazem via `from scripts.other import Foo`. O `BundleSource` da SPI expõe os scripts como módulos dentro do `Context` Polyglot. Não há algoritmo de ordem; a resolução é a do próprio interpretador Python.

### Requirement: Bytecode cache is keyed by source, import set, and engine version

**Reason**: Cache de bytecode é específico a Kotlin Scripting. Python não precisa de cache equivalente porque o boot do módulo é barato e GraalPy gerencia seus próprios `.pyc` internamente. A complexidade do cache (chave SHA-256, invalidação por import set, invalidação por engineVersion) desaparece.

**Migration**: Nenhuma. GraalPy não exige cache controlado pela engine para performance aceitável. Se hot-reload virar necessário no futuro (escopo de outra change), o tema do cache será reaberto.

### Requirement: Cache cleanup removes orphan bytecode at bootstrap

**Reason**: Mesma razão da entrada anterior — o cache Kotlin Scripting sai integralmente.

**Migration**: Nenhuma. Não há mais diretório `classes/` em `:engine-bundle`.

### Requirement: Cache location depends on bundle source

**Reason**: Mesma razão — cache não existe mais.

**Migration**: Nenhuma. Os caminhos `build/scripting-cache/<name>/` e `<dir>/.nengine-cache/` deixam de ser criados.

### Requirement: ScriptSource abstracts classpath and filesystem reads

**Reason**: O conceito é renomeado para `BundleSource` e migra para o capability `script-host` (a SPI usa essa abstração para que implementações de `ScriptHost` em qualquer módulo possam consumir arquivos do bundle sem conhecer o mecanismo de resolução). As implementações concretas (`ClasspathBundleSource`, `DirectoryBundleSource`) continuam vivendo em `:engine-bundle`.

**Migration**: Veja `script-host` → "BundleSource abstracts script file resolution". O comportamento é equivalente; apenas o nome e o local da interface mudam.
