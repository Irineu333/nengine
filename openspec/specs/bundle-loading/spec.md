# bundle-loading Specification

## Purpose

Carregamento de cena via **bundle**: uma pasta autocontida com `scene.json` na raiz e (opcionalmente) `scripts/*.nengine.kts`, resolvida tanto via classpath JVM (`fromResources`) quanto via filesystem (`fromPath`). Centraliza compilação de scripts Kotlin (`.nengine.kts`) atrás de `BundleLoader`, dispensando manifesto manual: scripts são descobertos por tree-walk no `scene.json` e cross-references são resolvidas via algoritmo round-robin / fixed-point. Substitui o módulo `:engine-scripting` (absorvido) e remove a SPI `ScriptHost` de `:engine`.

## Requirements

### Requirement: engine-bundle module hosts bundle loading and scripting

O projeto SHALL prover um módulo Gradle `:engine-bundle` que depende de `:engine` e de `org.jetbrains.kotlin:kotlin-scripting-jvm-host`. Esse módulo MUST ser o único local que conhece a compilação de scripts `.nengine.kts` e a API pública `BundleLoader`. O módulo MUST NOT ser dependência de `:engine`, `:engine-skiko`, ou `:engine-compose`. Apenas jogos que carregam cena via bundle dependem dele. O módulo `:engine-scripting` MUST deixar de existir nesta change; seu conteúdo é absorvido por `:engine-bundle`.

#### Scenario: engine-bundle exists with the right dependencies

- **WHEN** a build configuration de `:engine-bundle` é inspecionada
- **THEN** ela declara dependência em `:engine`
- **AND** declara dependência em `org.jetbrains.kotlin:kotlin-scripting-jvm-host`
- **AND** não declara nenhuma outra dependência de módulo do projeto

#### Scenario: engine-scripting module no longer exists

- **WHEN** `settings.gradle.kts` é lido
- **THEN** não há `include(":engine-scripting")`
- **AND** o diretório `engine-scripting/` não existe no workspace

#### Scenario: engine modules do not depend on engine-bundle

- **WHEN** a configuração de build de `:engine`, `:engine-skiko` e `:engine-compose` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle` como dependência, direta ou transitiva

#### Scenario: Games without bundles do not pull engine-bundle transitively

- **WHEN** o runtime classpath de `:games:tictactoe` ou `:games:demos` é resolvido
- **THEN** nenhum artefato de `:engine-bundle` ou de `kotlin-scripting-jvm-host` está presente

### Requirement: Scene bundle layout convention

Um **scene bundle** SHALL ser uma pasta autocontida com a forma:

```
<bundle>/
  scene.json          (obrigatório, raiz)
  scripts/            (opcional)
    *.nengine.kts
  assets/             (reservado; ignorado pela engine nesta change)
```

`scene.json` MUST estar diretamente sob a raiz do bundle e MUST ser o JSON serializado por `SceneLoader.save` (formato `SceneFile`). Os paths internos em `scene.json` para scripts SHALL ser **relativos ao bundle** (ex.: `"type": "scripts/paddle.nengine.kts"`). A pasta `assets/` MAY estar ausente; quando presente, esta change não exige que a engine a leia.

#### Scenario: Bundle root has scene.json

- **WHEN** uma pasta é tratada como bundle
- **AND** não há `scene.json` na raiz
- **THEN** o `BundleLoader` lança exceção cuja mensagem nomeia o bundle e indica que `scene.json` está faltando

#### Scenario: Script paths in scene.json are bundle-relative

- **GIVEN** um bundle `pong/` com `scripts/paddle.nengine.kts`
- **WHEN** `scene.json` referencia esse script
- **THEN** o campo `type` correspondente é a string exata `scripts/paddle.nengine.kts`
- **AND** NÃO é uma string que inclui o nome do bundle (ex.: `pong/scripts/paddle.nengine.kts`)

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

`fromResources(name)` MUST resolver o bundle como um diretório lógico relativo à raiz do classpath JVM (ex.: `fromResources("pong")` carrega `pong/scene.json` via `ClassLoader.getResource`). `fromPath(bundleDir)` MUST resolver via filesystem. Ambas as funções MUST retornar uma `Scene` destacada (mesmo contrato de `SceneLoader.load`: `isLive == false`). O argumento `types` MUST aceitar tipos `Node` compilados em Kotlin que o jogo precisa expor para o `NodeRegistry` (factory derivada por reflection sobre construtor no-args).

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

O `BundleLoader` MUST descobrir quais scripts compilar percorrendo o JSON parseado: para cada `NodeEntry`, se `type` termina com `.nengine.kts`, esse path é adicionado ao conjunto de scripts a compilar. Scripts presentes na pasta `scripts/` mas NÃO referenciados pela árvore MUST NOT ser compilados. Scripts referenciados MUST ser passados para o `KotlinScriptingHost`. Os paths coletados MUST ser usados exatamente como aparecem no JSON (bundle-relative).

#### Scenario: Only referenced scripts compile

- **GIVEN** um bundle com `scripts/used.nengine.kts` e `scripts/orphan.nengine.kts`
- **AND** `scene.json` referencia apenas `scripts/used.nengine.kts`
- **WHEN** código chama `BundleLoader.fromResources(name)`
- **THEN** `used.nengine.kts` é compilado
- **AND** `orphan.nengine.kts` NÃO é compilado

#### Scenario: Scripts referenced multiple times compile once

- **GIVEN** um `scene.json` em que dois nós distintos referenciam o mesmo `scripts/paddle.nengine.kts`
- **WHEN** código chama `BundleLoader.fromResources(name)`
- **THEN** a compilação de `paddle.nengine.kts` ocorre uma única vez
- **AND** os dois nós são instâncias distintas da mesma classe

### Requirement: Round-robin fixed-point compilation resolves cross-references

O `KotlinScriptingHost` interno a `:engine-bundle` MUST compilar a coleção de scripts sem exigir ordem manual. O algoritmo MUST ser:

1. Mantém-se um conjunto `resolved` (path → KClass) e uma lista `pending` (paths não compilados).
2. Em cada iteração, para cada path pendente, tenta compilar com `defaultImports` = nomes das classes em `resolved`.
3. Se compila, move para `resolved`.
4. Se falha **apenas** por referências não-resolvidas a símbolos publicados por outros paths ainda pendentes, mantém o path em `pending` para próxima iteração.
5. Se falha por qualquer outro motivo, propaga o erro imediatamente.
6. Se uma iteração inteira não move nenhum path para `resolved`, lança `CyclicScriptDependencyError` listando os paths em ciclo.

A heurística "referência não-resolvida a símbolo de outro pending" MUST ser determinada pelo conjunto de nomes de classes top-level publicados por cada script (extraídos uma vez por script via inspeção lexical do source). Erros do compilador que mencionem símbolos não presentes nesse conjunto MUST ser propagados imediatamente como erro real do autor.

#### Scenario: Cross-references compile regardless of input order

- **GIVEN** dois scripts `a.nengine.kts` (define `class A` que usa `B`) e `b.nengine.kts` (define `class B`)
- **WHEN** o host recebe a lista `[a, b]` (ordem "errada")
- **THEN** após a primeira iteração `b` está em `resolved`
- **AND** após a segunda iteração `a` está em `resolved`
- **AND** o resultado é o mesmo de receber `[b, a]`

#### Scenario: Genuine compilation errors fail fast

- **GIVEN** um script com erro de sintaxe ou referência a símbolo inexistente (que NÃO é nome de outra classe top-level pendente)
- **WHEN** o host tenta compilá-lo
- **THEN** o erro é propagado imediatamente
- **AND** a mensagem contém o diagnóstico do compilador Kotlin
- **AND** o erro NÃO é re-tentado em iterações subsequentes

#### Scenario: Cyclic dependency is detected

- **GIVEN** dois scripts `a.nengine.kts` e `b.nengine.kts` em que `A` referencia `B` e `B` referencia `A`
- **WHEN** o host tenta compilá-los
- **THEN** uma exceção `CyclicScriptDependencyError` (ou subtipo equivalente) é lançada
- **AND** a mensagem nomeia ambos os paths

### Requirement: Bytecode cache is keyed by source, import set, and engine version

O cache de bytecode dos scripts MUST ser persistido com uma chave que seja o SHA-256 da concatenação ordenada e separada por delimitador de: (a) o source bruto do script, (b) o conjunto ordenado de FQN das wrapper-classes passadas como `defaultImports` para aquela compilação específica, (c) uma string `engineVersion` derivada de um recurso `META-INF/nengine.version` do módulo `:engine` (ou equivalente estável). Cache hit MUST resultar em pular a invocação do compilador Kotlin e usar o bytecode salvo. Cache miss MUST recompilar e gravar atomicamente o resultado.

#### Scenario: Same source with different import sets yields different cache entries

- **GIVEN** um mesmo source de script
- **WHEN** ele é compilado uma vez com `defaultImports = [A]` e outra com `defaultImports = [A, B]`
- **THEN** dois arquivos de cache distintos são produzidos
- **AND** nenhuma das compilações reutiliza o bytecode da outra

#### Scenario: Engine version change invalidates cache

- **GIVEN** o cache foi populado em uma versão da engine
- **WHEN** o recurso `META-INF/nengine.version` muda
- **THEN** uma chamada subsequente de `compile(path)` produz novo bytecode
- **AND** a entrada antiga do cache não é usada

#### Scenario: Identical source and import set hits cache

- **GIVEN** o cache foi populado por uma compilação prévia
- **AND** o source e o conjunto de imports não mudaram
- **WHEN** o host é instanciado em uma nova JVM e tenta compilar o mesmo path com o mesmo import set
- **THEN** o bytecode é carregado do cache
- **AND** o compilador Kotlin NÃO é invocado

### Requirement: Cache cleanup removes orphan bytecode at bootstrap

A cada inicialização do `KotlinScriptingHost`, o diretório de `classes/` (onde o `URLClassLoader` lê o bytecode) MUST ser reconstruído a partir das entradas válidas do cache para os scripts atualmente solicitados. Bytecode de scripts que não pertencem mais ao bundle MUST não permanecer no `classes/` após o bootstrap.

#### Scenario: Removed script leaves no stale bytecode

- **GIVEN** o `classes/` contém bytecode de `obsoleto.nengine.kts` de um run anterior
- **WHEN** o host é instanciado para um bundle que não referencia mais `obsoleto.nengine.kts`
- **THEN** após o bootstrap, o `classes/` não contém artefatos de `obsoleto.nengine.kts`

### Requirement: Cache location depends on bundle source

A localização do cache MUST seguir a origem do bundle:

- Para `fromResources(name)`: `build/scripting-cache/<name>/` (relativo ao working directory do processo).
- Para `fromPath(dir)`: `<dir>/.nengine-cache/` (dentro da própria pasta do bundle).

#### Scenario: fromResources writes cache under build/

- **WHEN** código chama `BundleLoader.fromResources("pong")`
- **THEN** o cache de bytecode é gravado sob `build/scripting-cache/pong/`

#### Scenario: fromPath writes cache inside the bundle

- **GIVEN** um diretório `/tmp/foo/` usado como bundle
- **WHEN** código chama `BundleLoader.fromPath(File("/tmp/foo"))`
- **THEN** o cache de bytecode é gravado sob `/tmp/foo/.nengine-cache/`

### Requirement: ScriptSource abstracts classpath and filesystem reads

O `KotlinScriptingHost` interno a `:engine-bundle` MUST ler o source dos scripts através de uma abstração `ScriptSource` com pelo menos duas variantes: `Classpath(bundleRoot: String)` que resolve cada path relativo via `ClassLoader.getResource("$bundleRoot/$relativePath")`, e `Directory(bundleDir: File)` que resolve via `File(bundleDir, relativePath).readText()`. O path relativo MUST ser idêntico ao que aparece no JSON da cena.

#### Scenario: Same JSON loads from classpath and from disk

- **GIVEN** um `scene.json` cujo conteúdo é idêntico em duas localizações (`pong/` no classpath e `/tmp/pong/` no disco)
- **WHEN** código chama `BundleLoader.fromResources("pong")` e `BundleLoader.fromPath(File("/tmp/pong"))`
- **THEN** ambas as chamadas produzem `Scene` com a mesma forma de árvore e os mesmos valores de propriedades
