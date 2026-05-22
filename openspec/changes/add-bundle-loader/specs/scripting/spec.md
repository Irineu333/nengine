## REMOVED Requirements

### Requirement: ScriptHost SPI in engine core

**Reason**: A SPI `ScriptHost` / `ScriptHosts` em `:engine` existia apenas para permitir que o `SceneLoader` (em `:engine`) atravessasse a fronteira de módulo via global. Com a introdução de `:engine-bundle`, o `BundleLoader` é responsável por compilar scripts e registrá-los como tipos de primeira classe no `NodeRegistry`. O `SceneLoader` deixa de conhecer scripts (ramo `endsWith(".kts")` é removido). A SPI vira detalhe interno de `:engine-bundle`.

**Migration**: Código que dependia de `ScriptHosts.register(...)` deve migrar para `BundleLoader.fromResources(name)` ou `BundleLoader.fromPath(file)`, fornecidos por `:engine-bundle`. Implementações custom de `ScriptHost` em `:engine` não são suportadas; o backend de scripting passa a ser detalhe interno do bundle loader.

### Requirement: engine-scripting module provides the Kotlin scripting backend

**Reason**: O módulo `:engine-scripting` foi absorvido por `:engine-bundle`. Manter dois módulos para a mesma cadeia (compilar `.nengine.kts` + carregar como cena) duplica fronteiras sem benefício. O conceito de "bundle" é a fronteira certa.

**Migration**: Game modules que dependiam de `:engine-scripting` devem trocar a dependência para `:engine-bundle`. As classes públicas `KotlinScriptingHost` e `NEngineScript` deixam de ser API pública — viram detalhes internos do novo módulo.

### Requirement: Manifest-ordered compilation for inter-script references

**Reason**: O manifesto manual com ordem topológica era fricção desnecessária para o autor. A nova implementação resolve cross-references via algoritmo round-robin / fixed-point, eliminando a necessidade de o jogo declarar ordem.

**Migration**: O parâmetro `manifest: List<String>` é removido do construtor público. A coleção de scripts a compilar passa a ser derivada do tree-walk do `scene.json` pelo `BundleLoader`.

### Requirement: Compilation cache persists between runs

**Reason**: A versão anterior do cache usava chave `SHA256(source) + version` apenas, sem considerar o conjunto de imports default — o que produzia bytecode stale ao reordenar scripts. A nova requisição (spec `bundle-loading`) define cache key incluindo source, import set ordenado e engine version, com limpeza de bytecode órfão no bootstrap.

**Migration**: O cache antigo (em `build/scripting-cache/`) é incompatível com o novo formato de chave e MUST ser deletado em runs após a migração. A nova localização e estrutura ficam descritas em `bundle-loading`.

### Requirement: Scripts are loaded from the JVM classpath

**Reason**: A versão anterior limitava o source de scripts ao classpath. A nova implementação introduz `ScriptSource` (Classpath | Directory) interno a `:engine-bundle` para suportar tanto o classpath (jogos empacotados) quanto o filesystem (editor visual).

**Migration**: Game modules continuam funcionando inalterados — `BundleLoader.fromResources(name)` usa internamente `ScriptSource.Classpath`. Para uso com filesystem, `BundleLoader.fromPath(dir)` usa `ScriptSource.Directory`.

## MODIFIED Requirements

### Requirement: A script defines exactly one top-level Node subclass

O backend interno de scripting em `:engine-bundle` SHALL compilar um source `.nengine.kts` e inspecionar a saída compilada por classes top-level. A compilação MUST ter sucesso somente se houver exatamente uma classe top-level cuja erasure seja atribuível a `Node`. Zero, duas ou mais classes desse tipo MUST fazer a compilação falhar fail-fast com uma exceção cuja mensagem nomeia o arquivo e a contagem.

#### Scenario: One Node subclass is accepted

- **GIVEN** um arquivo de script contendo `class Paddle : Node2D() { ... }` e nenhuma outra classe top-level
- **WHEN** o `BundleLoader` carrega um bundle cujo `scene.json` referencia `scripts/paddle.nengine.kts`
- **THEN** o carregamento produz uma instância de `Paddle`

#### Scenario: Zero Node subclasses fails fast

- **GIVEN** um arquivo de script contendo apenas top-level statements e nenhuma declaração de classe
- **WHEN** o `BundleLoader` tenta compilá-lo
- **THEN** a chamada lança exceção cuja mensagem nomeia o path do script

#### Scenario: Two Node subclasses fails fast

- **GIVEN** um arquivo de script contendo `class A : Node2D()` e `class B : Node2D()` ambos top-level
- **WHEN** o `BundleLoader` tenta compilá-lo
- **THEN** a chamada lança exceção cuja mensagem nomeia o path do script

### Requirement: ScriptDefinition pre-imports the engine API

O backend interno de scripting em `:engine-bundle` SHALL usar um `ScriptDefinition` custom para a extensão `.nengine.kts` que adiciona os seguintes pacotes aos default imports implícitos de todo script:

- `com.neoutils.engine.scene.*`
- `com.neoutils.engine.math.*`
- `com.neoutils.engine.render.*`
- `com.neoutils.engine.input.*`
- `com.neoutils.engine.serialization.*`
- `com.neoutils.engine.physics.*`

Um script que use apenas esses pacotes e `kotlin.*` SHALL compilar sem nenhum `import` explícito.

#### Scenario: Script using only pre-imported packages compiles without imports

- **GIVEN** um script cujas únicas referências não-`kotlin.*` são `Node2D`, `Vec2`, `Color`, `Renderer`, `Inspect`
- **WHEN** o `BundleLoader` o compila como parte do carregamento de um bundle
- **THEN** a compilação tem sucesso
- **AND** o source file contém zero declarações `import`

### Requirement: Script errors crash the process fail-fast

Qualquer falha durante o tratamento de scripts — arquivo não encontrado, erro de compilação que não seja referência a símbolo ainda pendente, mais de uma classe top-level, classe top-level não atribuível a `Node`, falha de instanciação, exceção em runtime em um hook de lifecycle — SHALL propagar para o caller sem ser capturada ou transformada pela engine em nó placeholder. O backend de scripting MUST NOT emitir warning e continuar; MUST lançar. A engine MUST NOT instalar handler de exceção que engula falhas de script.

#### Scenario: Missing script file crashes load

- **GIVEN** um `scene.json` referencia `scripts/missing.nengine.kts` que não existe no bundle
- **WHEN** código chama `BundleLoader.fromResources(name)` ou `fromPath(dir)`
- **THEN** a chamada lança exceção
- **AND** a mensagem nomeia o path faltando

#### Scenario: Compilation error crashes load

- **GIVEN** `scripts/broken.nengine.kts` contém erro de sintaxe que NÃO é referência a símbolo pendente de outro script
- **WHEN** `BundleLoader` o compila
- **THEN** a chamada lança exceção
- **AND** a mensagem contém o diagnóstico do compilador Kotlin

#### Scenario: Top-level class not extending Node crashes compile

- **GIVEN** um script cuja única classe top-level estende `Any` (não `Node`)
- **WHEN** `BundleLoader` o compila
- **THEN** a chamada lança exceção
- **AND** a mensagem nomeia a classe ofensora e esclarece o supertipo exigido
