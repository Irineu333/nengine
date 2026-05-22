## Why

O scripting atual (`.nengine.kts` compilado via Kotlin Scripting embarcado) é **JVM-only**: trava a evolução da engine em direção a Kotlin Multiplatform e a cobertura "todo Node é uma classe Kotlin" não escala para o editor visual no roadmap, onde gameplay precisa ser editável sem recompilar Kotlin. Lua descartado por sintaxe; DSL própria infla o escopo; **Python (via GraalPy)** dá familiaridade ampla, runtime estável e mantém a opção de trocar o runtime depois sem mexer nos scripts. Para não enraizar Python na engine, a change introduz primeiro uma **SPI agnóstica de linguagem** (`ScriptHost`) e usa GraalPy como primeira implementação — mantendo a porta aberta para Lua, DSL ou implementações alternativas (MicroPython quando mobile entrar em escopo) sem refator estrutural.

## What Changes

- **Modelo Godot-like de "script anexado"**: o Node continua sendo Kotlin nativo (Node2D, BoxCollider, etc.). O script Python fica num **slot único** do Node, declara `extends <NodeType>` e expõe `@export`s. A subclasse Python deixa de "ser" o Node — passa a estar **anexada** a ele.
- **Novo módulo `:engine-bundle-python`** com a implementação `PythonScriptHost` baseada em GraalPy (Polyglot Context). Vive separado de `:engine-bundle` para não obrigar quem não usa Python a pagar o footprint do runtime.
- **SPI `ScriptHost` agnóstica de linguagem** em `:engine-bundle`: `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`. Sem vazamento de tipos GraalPy (`Value`, `Context`) fora do módulo de implementação. Registro por extensão de arquivo (`.py` → `PythonScriptHost`).
- **Hooks fixos**: `on_enter`, `on_update(dt)`, `on_render(renderer)`, `on_collide(other)`. Sem subclasse virtual arbitrária — a interface é uma estrutura conhecida pela SPI.
- **`@export` declarativo no topo do módulo Python** com type-hints (`speed: float = 360.0`). Tipos suportados: primitivos (`Int`, `Float`, `Bool`, `String`), engine (`Vec2`, `Color`, `Rect`, `NodeRef`), enums (`Key`) e nullable de cada. Descoberta **estática** via `ast.parse` (a engine **não** roda o script para descobrir exports).
- **`extends <NodeType>` declarado por convenção** (ex.: docstring de módulo ou comentário convencional) — usado pelo editor (futuro) para validar onde o script pode ser anexado. Resolvido contra o `NodeRegistry`.
- **Novo formato de `scene.json`**: campo `script` (path do `.py`) + `props` (overrides dos exports) anexados a um `_type` de Node nativo. `_type: "scripts/paddle.nengine.kts"` deixa de existir.
- **Stubs `.pyi`** publicados pelo `:engine-bundle-python` para Node, Node2D, Renderer, Input, Vec2, Color, Rect, NodeRef, Key, BoxCollider — usados por IDE/LSP no diretório de scripts.
- **BREAKING**: `.nengine.kts` deixa de existir como contrato. Toda a infraestrutura Kotlin Scripting (`KotlinScriptingHost`, round-robin, cache `.bin`) sai. `:engine-bundle` deixa de declarar `kotlin-scripting-*`.
- **BREAKING**: `NodeRegistry` volta a ser **só de tipos Node nativos** — não registra mais paths de script. A resolução de script vira responsabilidade do `ScriptHost` correspondente.
- **Migração do Pong como prova de fogo**: `pong/scripts/*.nengine.kts` viram `pong/scripts/*.py`; `pong/scene.json` é regravado no novo formato; `Main.kt` permanece com `BundleLoader.fromResources("pong")` (a API exterior do bundle não muda).
- **Fora de escopo** (declarado explicitamente): hot reload, editor visual, segunda implementação de `ScriptHost` (Lua/DSL), execução fora de JVM Desktop, distribuição standalone do runtime GraalPy.

## Capabilities

### New Capabilities

- `script-host`: SPI agnóstica de linguagem para scripting (`ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`, `ScriptHostRegistry` por extensão). Define o contrato comportamental que qualquer implementação (Python, futuro Lua, futuro DSL) deve cumprir: descobrir exports estaticamente, anexar a um Node Kotlin sem subclassá-lo, invocar hooks fixos, propagar erros fail-fast.
- `python-scripting`: implementação concreta `PythonScriptHost` em `:engine-bundle-python` usando GraalPy 24.x (Python 3.11). Cobre: contexto Polyglot, parsing AST para `@export`, bridge entre Node Kotlin e instância Python, hooks, stubs `.pyi` distribuídos com o módulo.

### Modified Capabilities

- `scripting`: o capability hoje descreve `.nengine.kts` + Kotlin Scripting. Vai ser **removido** integralmente como artefato vivo. A change deleta o spec do diretório `openspec/specs/scripting/`, sem substituir requirements — quem precisar do contrato comportamental genérico (uma classe top-level, hooks, fail-fast) lê `script-host`.
- `bundle-loading`: `scene.json` deixa de tratar `type: scripts/*.nengine.kts` como nó scriptado. Passa a reconhecer o par `_type` (Node nativo) + `script` (path do .py) + `props` (overrides dos exports). Descoberta de scripts no tree-walk muda de "type termina com `.nengine.kts`" para "nó tem campo `script` não-nulo". Round-robin e cache de bytecode saem. `:engine-bundle` perde a dependência `kotlin-scripting-*`. `:engine-bundle-python` é o módulo a ser declarado como dependência por jogos que carregam bundles com scripts Python.
- `pong-sample`: scripts migrados para `.py`; `pong/scene.json` regravado com o novo schema (`_type` + `script` + `props`); `:games:pong` ganha dependência em `:engine-bundle-python`.
- `project-conventions`: tabela de módulos em `CLAUDE.md` inclui `:engine-bundle-python`; seção "Scripting contract" reescrita para Python; roadmap registra a change como Active → Archived ao concluir.

## Impact

- **Código afetado**:
  - `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/scripting/` — todo o subdiretório Kotlin Scripting (`KotlinScriptingHost`, `NEngineScript`, `ScriptSource`, `CyclicScriptDependencyError`) é **deletado**.
  - `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/BundleLoader.kt` — refator: detecta `script` em vez de extensão `.kts`, delega ao `ScriptHostRegistry`, remove invocação direta de `KotlinScriptingHost`.
  - `engine-bundle/src/main/kotlin/com/neoutils/engine/bundle/script/` — **novo**: `ScriptHost`, `Script`, `ScriptInstance`, `ExportedProperty`, `ScriptHostRegistry`.
  - `engine/src/main/kotlin/com/neoutils/engine/serialization/SceneLoader.kt` — `NodeEntry` ganha campos opcionais `script: String?` e `props: JsonObject?`; lógica de instanciação consulta `ScriptHostRegistry` para anexar script após instanciar o Node nativo.
  - `engine/src/main/kotlin/com/neoutils/engine/serialization/NodeRegistry.kt` — volta a registrar **apenas** tipos Node nativos; remove o ramo `endsWith(".kts")`.
  - `engine/src/main/kotlin/com/neoutils/engine/scene/Node.kt` — ganha slot `internal var scriptInstance: ScriptInstance?` e despacho dos hooks (`onEnter`, `onUpdate`, `onRender`, `onCollide`) para a instância de script quando presente.
  - `engine-bundle-python/` — **novo módulo** com `PythonScriptHost`, parser AST de `@export`, bridge GraalPy.
  - `games/pong/src/main/resources/pong/scripts/*.nengine.kts` — **deletados**; substituídos por `*.py`.
  - `games/pong/src/main/resources/pong/scene.json` — regravado no novo schema.
  - `games/pong/src/main/kotlin/.../Main.kt` — permanece praticamente idêntico (a API `BundleLoader.fromResources` não muda).
- **APIs públicas**:
  - Introduz `com.neoutils.engine.bundle.script.{ScriptHost, Script, ScriptInstance, ExportedProperty, ScriptHostRegistry}`.
  - Introduz `com.neoutils.engine.bundle.python.PythonScriptHost` (em `:engine-bundle-python`).
  - Remove tudo em `com.neoutils.engine.bundle.scripting.*` (Kotlin Scripting interno).
  - `NodeEntry` (schema do `scene.json`) ganha campos `script` e `props`.
- **Build**:
  - `settings.gradle.kts` ganha `include(":engine-bundle-python")`.
  - `:engine-bundle` perde `kotlin-scripting-common`, `kotlin-scripting-jvm`, `kotlin-scripting-jvm-host`.
  - `:engine-bundle-python` declara `org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:python` (versão GraalPy estável, 24.x).
  - `:games:pong` troca dependência: sai `:engine-bundle` puro (que vai continuar existindo) e entra também `:engine-bundle-python`.
- **Documentação**: `CLAUDE.md` (tabela de módulos, seção "Scripting contract", roadmap) e specs OpenSpec.
- **Testes**:
  - `KotlinScriptingHostTest` (em `:engine-bundle`) é **removido**.
  - `BundleLoaderTest` é atualizado: cenários de `.nengine.kts` viram cenários de `.py`; cobertura de descoberta de script via campo `script`.
  - Novo `PythonScriptHostTest` em `:engine-bundle-python`: carga, AST inspector, hooks, fail-fast.
  - Novo `ScriptHostRegistryTest` em `:engine-bundle`: dispatch por extensão.
  - `SceneLoaderTest` ganha cenários para o novo schema (`script` + `props`).
- **Risco**:
  - **Footprint**: GraalPy adiciona dezenas de MB ao classpath do `:games:pong:run`. Aceitável para a engine em fase didática, mas anotado no design.
  - **Cold start do Polyglot Context**: primeiro `attach` pode custar centenas de ms. Mitigado por inicialização ansiosa do contexto durante `BundleLoader.fromResources`.
  - **AST estática vs dinâmica**: a descoberta de `@export` por `ast.parse` cobre o caso comum (atribuições anotadas no top-level do módulo). Padrões dinâmicos (`for k in ...: globals()[k] = ...`) não são suportados — limitação intencional, alinhada com o que a Godot faz.
  - **Não-aderência a Multiplataforma real**: GraalPy não roda em Android/iOS. Riscos do roadmap mobile permanecem; a SPI agnóstica preserva a opção de trocar a impl (MicroPython, Pyodide) sem mexer nos scripts.
