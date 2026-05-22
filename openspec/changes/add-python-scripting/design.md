## Context

A engine roda hoje scripts `.nengine.kts` compilados por Kotlin Scripting embarcado. O modelo é "script = classe Kotlin que herda de `Node`" (vide `KotlinScriptingHost` em `:engine-bundle`). Funciona, mas amarra a engine a JVM e impõe um cache de bytecode complexo (round-robin + SHA-256). Mais grave: o roadmap aponta para um editor visual e para evolução em direção a Kotlin Multiplatform. Kotlin Scripting é JVM-only; o editor visual exige gameplay editável sem rebuild Kotlin; e o modelo "subclasse direta" é hostil a hot reload e a stubs estáticos para LSP.

A change introduz **scripting como capability abstrata** (`script-host`) e usa **Python (GraalPy)** como primeira implementação, no estilo Godot — o script é anexado a um Node Kotlin via slot único, não substitui a classe. O capability `scripting` antigo morre.

Stakeholder único é o usuário (irineu).

## Goals / Non-Goals

**Goals:**

- Substituir Kotlin Scripting por uma SPI agnóstica de linguagem (`ScriptHost`) com Python como primeira implementação concreta (`:engine-bundle-python` via GraalPy 24.x / Python 3.11).
- Manter `:engine` puro e sem dependência de GraalPy ou de qualquer runtime de scripting.
- Manter `:engine-bundle` agnóstico — descobre scripts via tree-walk no `scene.json` e despacha pelo `ScriptHostRegistry` registrado por extensão de arquivo, sem conhecer Python.
- Adotar modelo Godot: Node continua sendo a instância Kotlin nativa; o script é um **adornment anexado** via slot `Node.scriptInstance`.
- `@export` descoberto **estaticamente** (sem rodar o script) por inspeção AST do módulo Python. Tipos suportados são restritos ao conjunto serializável pela engine.
- Hooks fixos (`on_enter`, `on_update`, `on_render`, `on_collide`) — sem reflexão sobre métodos arbitrários.
- Migrar Pong inteiro pra Python como prova de fogo, em fatias.
- Mensagens de erro propagam fail-fast — bug em script crasha o processo.

**Non-Goals:**

- Hot reload (qualquer nível). Editar `.py` exige reiniciar o jogo.
- Sandboxing. Um script Python tem o mesmo poder que código Kotlin.
- Segunda implementação de `ScriptHost` nesta change. A SPI nasce abstrata, mas só Python entra. Lua/DSL/MicroPython ficam para changes futuras.
- Multiplataforma além de JVM Desktop. GraalPy não roda em Android/iOS. Estratégia documentada como evolução.
- Editor visual. A change prepara o terreno (`@export` estaticamente descoberto, `extends` resolvido contra `NodeRegistry`), mas o editor é trabalho de outra change.
- Distribuição standalone do runtime GraalPy. O usuário consome via dependência Gradle e ponto.
- Migração de `:games:tictactoe` e `:games:demos`. Eles continuam Kotlin puro e validam que scripting Python é estritamente opt-in.

## Decisions

### Decisão 1: SPI primeiro, implementação depois — `script-host` é um capability separado

A change cria dois capabilities novos: `script-host` (SPI agnóstica em `:engine-bundle`) e `python-scripting` (impl GraalPy em `:engine-bundle-python`). O capability `scripting` antigo é deletado integralmente do diretório `openspec/specs/`.

**Alternativa considerada:** colocar Python direto em `:engine-bundle` sem SPI. Rejeitada — enraíza GraalPy no módulo agnóstico, sabotando o objetivo declarado de poder trocar a impl depois.

**Alternativa considerada:** manter o capability `scripting` modificando-o para descrever a SPI. Rejeitada — o capability antigo descreve `.nengine.kts` e Kotlin Scripting; aproveitar o nome levaria a um spec confuso. Mais limpo deletar e nomear o novo apropriadamente.

### Decisão 2: Modelo "script anexado", Node continua Kotlin

O Node permanece sendo a instância Kotlin (Node2D, BoxCollider, etc.). Um script Python é anexado via slot único `Node.scriptInstance: ScriptInstance?`. Os hooks de lifecycle do Node delegam para `scriptInstance` quando presente:

```kotlin
open class Node {
    internal var scriptInstance: ScriptInstance? = null

    open fun onUpdate(dt: Float) {
        scriptInstance?.onUpdate(dt)
    }
    // mesma forma para onEnter, onRender, onCollide
}
```

O ScriptInstance recebe o Node hospedeiro na criação (`PythonScriptHost.attach(node, script)`), e o `self` dentro do script Python é o próprio Node — exposto via Polyglot binding.

**Alternativa considerada:** script Python "herda" Node — i.e., `class Paddle(Node2D)` em Python, com Kotlin chamando métodos da subclasse via reflexão polyglot a cada frame. Rejeitada por duas razões: (1) força a engine a respeitar a hierarquia de classes Python no scene tree, complicando o `SceneLoader`; (2) inverte o modelo Godot que o usuário escolheu — em Godot o script é adornment, não substituição.

**Alternativa considerada:** múltiplos scripts por Node (lista de scripts). Rejeitada — quebra o invariante "comportamento por herança" da engine; introduz problema de ordem de execução; e Godot adotou slot único justamente para evitar isso.

### Decisão 3: `@export` por type-hints no top-level + descoberta estática via AST

Um script declara exports como atribuições anotadas top-level:

```python
# extends Node2D

speed: float = 360.0
ai: bool = False
target: NodeRef = NodeRef("")

def on_update(self, dt: float):
    ...
```

A descoberta dos exports é **estática**: `PythonScriptHost.inspect(path)` parseia o `.py` com `ast.parse` (rodando via GraalPy, mas sem **executar** o módulo) e extrai as `AnnAssign` top-level cujo tipo é um dos suportados. O resultado é uma `List<ExportedProperty>`.

Tipos suportados em `@export`:

| Python type    | Engine type          | Notas                              |
|----------------|----------------------|------------------------------------|
| `int`          | `Int`                |                                    |
| `float`        | `Float`              |                                    |
| `bool`         | `Boolean`            |                                    |
| `str`          | `String`             |                                    |
| `Vec2`         | `math.Vec2`          | import implícito                   |
| `Color`        | `render.Color`       | import implícito                   |
| `Rect`         | `math.Rect`          | import implícito                   |
| `NodeRef`      | `serialization.NodeRef` | import implícito                |
| `Key`          | `input.Key`          | enum                               |
| `Optional[T]`  | `T?` para qualquer T acima | `T \| None` também aceito       |

Tudo o que não estiver nessa tabela é **ignorado** pelo inspetor — não vira `ExportedProperty`. Padrões dinâmicos (loop populando `globals()`) não são suportados. Limitação documentada.

**Alternativa considerada:** decorator `@export` explícito (`@export speed: float = 360.0`). Rejeitada — decorators em Python só se aplicam a `def`/`class`, não a atribuições. Funcionaria como wrapper de função (`speed = export(360.0)`), mas o type-hint top-level é mais idiomático e dispensa wrapper.

**Alternativa considerada:** ler exports rodando o módulo e introspectando `module.__annotations__`. Rejeitada — exige execução do script (efeitos colaterais, dependências em runtime já carregado), e o editor visual quer descobrir exports **sem** rodar o jogo.

### Decisão 4: `extends <NodeType>` declarado em docstring/comentário convencional

A primeira linha não vazia do módulo precisa ser ou um docstring ou um comentário no formato:

```python
"""extends Node2D"""
```

ou

```python
# extends Node2D
```

O `<NodeType>` é resolvido contra o `NodeRegistry` (tipos Node nativos da engine). Permite ao editor validar onde o script pode ser anexado; permite ao `BundleLoader` checar coerência entre `_type` (Node nativo) e o `extends` do script no momento de instanciar.

**Alternativa considerada:** declarar via classe (`class _Script(Node2D): pass`). Rejeitada — força o autor a entender semântica de classes Python que não usaremos para nada.

**Alternativa considerada:** inferir do uso (analisar AST para ver quais métodos do Node o script chama). Rejeitada — inferência frágil; o autor pode chamar métodos de `Node` em todos os subtipos e a inferência erra.

### Decisão 5: GraalPy 24.x como runtime, com `Context` único por bundle

O `:engine-bundle-python` cria **um** `org.graalvm.polyglot.Context` por carregamento de bundle (`BundleLoader.fromResources` / `fromPath`). Esse contexto é cacheado pelo `PythonScriptHost` durante a vida da `Scene`. Cada script carrega-se nesse contexto como módulo Python (via `Context.eval(Source.named(...).build())`), e cada `attach(node, script)` cria uma nova **instância** Python (não um novo `Context`).

O `Context` é configurado:

- `allowAllAccess(true)` durante a fase de design da change (simplifica), com a opção de tightening depois.
- `allowExperimentalOptions(true)` se necessário.
- `option("python.PosixModuleBackend", "java")` para evitar dep nativa.

Bindings injetados no Context:

| Binding name | Kotlin source                                |
|--------------|----------------------------------------------|
| `Vec2`       | `com.neoutils.engine.math.Vec2`              |
| `Color`      | `com.neoutils.engine.render.Color`           |
| `Rect`       | `com.neoutils.engine.math.Rect`              |
| `NodeRef`    | `com.neoutils.engine.serialization.NodeRef`  |
| `Key`        | `com.neoutils.engine.input.Key`              |
| `BoxCollider`| `com.neoutils.engine.physics.BoxCollider`    |

Os tipos de Node ficam disponíveis via `NodeRegistry`; o script raramente os instancia explicitamente — o framework cria a hierarquia a partir do `scene.json`.

**Alternativa considerada:** um `Context` global da JVM. Rejeitada — múltiplos bundles carregados simultaneamente (ex.: testes paralelos) compartilhariam estado.

**Alternativa considerada:** um `Context` por script. Rejeitada — overhead de criação e impede que scripts referenciem uns aos outros (caso evolutivo).

### Decisão 6: Roteamento por extensão no `BundleLoader`

`SceneLoader` parseia `scene.json` em `NodeEntry`s. `NodeEntry` ganha campos opcionais:

```kotlin
data class NodeEntry(
    val type: String,            // tipo Node nativo, ex.: "engine.Node2D"
    val script: String? = null,  // path do script, ex.: "scripts/paddle.py"
    val props: JsonObject? = null,
    // ... campos existentes
)
```

`BundleLoader` faz tree-walk no `scene.json` antes de instanciar e coleta o conjunto `scriptPaths = entries.mapNotNull { it.script }`. Os paths são passados ao `ScriptHostRegistry.loadAll(scriptPaths)`, que despacha cada path para o `ScriptHost` registrado para sua extensão (`.py` → `PythonScriptHost`). Resultado: `Map<String, Script>`.

Durante a instanciação, para cada `NodeEntry` com `script != null`:

1. Cria o Node nativo via `NodeRegistry.create(type)`.
2. Recupera o `Script` em `scripts[entry.script]`.
3. Chama `host.attach(node, script)`, que retorna o `ScriptInstance`.
4. Aplica `entry.props` nos exports da instância (via `ScriptInstance.setExport(name, value)`).
5. Armazena `node.scriptInstance = instance`.

**Alternativa considerada:** detectar script por algum prefixo no `type`. Rejeitada — herda o defeito da v1 (Kotlin Scripting), onde `type` carregava duas semânticas distintas. Separar em `type` (Node nativo) + `script` (path) é mais honesto.

### Decisão 7: AST parsing via GraalPy, não via parser Java

A descoberta estática de `@export` e `extends` usa o módulo `ast` do próprio Python rodando no `Context`. Especificamente, há um script utilitário Python interno ao `:engine-bundle-python` (`inspector.py`) que importa `ast`, recebe o source via binding, e retorna uma estrutura serializável (lista de tuplas `(name, type_str, default_repr)`) para o lado Kotlin.

**Alternativa considerada:** parser Python escrito em Kotlin/Java (ex.: ANTLR + grammar Python). Rejeitada — manter uma gramática Python atualizada é trabalho ingrato; o `ast` do Python é a fonte autoritativa.

**Alternativa considerada:** executar o módulo em modo sandbox e introspectar `__annotations__`. Rejeitada — executar o script causa efeitos colaterais e exige que os imports do script resolvam, dificultando o editor (que quer inspetar scripts sem todo o resto do bundle carregado).

### Decisão 8: Conversão `props` JSON ↔ Python via tipos engine

Quando `scene.json` traz `"speed": 360.0`, o `BundleLoader` lê isso como `JsonElement` Kotlin e converte para o tipo da `ExportedProperty` correspondente:

- Primitivos → boxed Kotlin (`Int`, `Float`, `Boolean`, `String`).
- `Vec2`/`Color`/`Rect`/`NodeRef` → instância Kotlin (já serializável).
- Enums (`Key`) → `Key.valueOf(string)`.

A injeção no Python é feita via `Value.putMember(name, kotlinValue)` no objeto-instância. GraalPy traduz transparentemente — `instance.speed` no Python lê o `Float` Kotlin.

**Alternativa considerada:** converter para tipos Python nativos (`int`, `float`, `str`, tuplas) e perder a tipagem rica do engine. Rejeitada — o autor quer escrever `self.target.resolve(self)` chamando o método Kotlin do `NodeRef`, não reimplementar.

### Decisão 9: Stubs `.pyi` distribuídos como resources do `:engine-bundle-python`

`:engine-bundle-python` publica um conjunto de stubs Python (`.pyi`) em `src/main/resources/stubs/`:

- `engine/__init__.pyi`
- `engine/math.pyi` (Vec2, Rect)
- `engine/render.pyi` (Color, Renderer)
- `engine/input.pyi` (Key, Input)
- `engine/scene.pyi` (Node, Node2D)
- `engine/physics.pyi` (BoxCollider)
- `engine/serialization.pyi` (NodeRef)

O autor configura seu IDE para resolver esses stubs (via Pyright/Pylance `extraPaths` ou similar). A engine **não** monta automaticamente o ambiente de IDE — a responsabilidade do `:engine-bundle-python` é só publicar os stubs num lugar onde o autor consegue encontrá-los. Documentado em `CLAUDE.md`.

**Alternativa considerada:** gerar stubs em build time via KSP/reflection sobre as classes Kotlin. Útil mas escopo extra; manter manual nesta change.

### Decisão 10: Migração de Pong em fatias E0–E7 com gates manuais

Mesma cadência da change `add-scripting`. Cada fatia termina num "rode o Pong e jogue 30 segundos". Gates explícitos em `tasks.md`.

**Alternativa considerada:** big-bang. Rejeitada pelos mesmos motivos da change anterior.

### Decisão 11: Fail-fast em qualquer erro de script

Igual ao contrato anterior. Script não encontrado, erro de parse Python, `extends` apontando para tipo desconhecido, exception num hook — tudo propaga até o `Main.kt`. Sem placeholder visual.

## Risks / Trade-offs

- **[GraalPy adiciona dezenas de MB ao classpath do Pong]** → mitigação: dep opt-in em `:engine-bundle-python`; só Pong paga; medir o aumento e documentar em `CLAUDE.md`. Aceito como preço de scripting.

- **[Cold start do `Context` Polyglot pode custar 200–500ms no primeiro `attach`]** → mitigação: criar o `Context` ansiosamente durante `BundleLoader.fromResources` (antes de instanciar Nodes), não preguiçosamente no primeiro `attach`. Documentar timing esperado.

- **[GraalPy não roda em Android/iOS]** → fora de escopo. A SPI agnóstica preserva a opção de trocar impl no futuro (MicroPython/Pyodide). Documentado em "evolution" da spec.

- **[AST inspector não cobre padrões dinâmicos]** → limitação intencional, igual ao que a Godot faz. Documentado.

- **[Bindings via `allowAllAccess(true)` expõem todo o Kotlin ao Python]** → trade-off didático: simplifica a vida do autor, mas um script pode chamar `System.exit()`. Aceitável dado que scripts são código de jogo, não código não-confiável. Tightening fica para uma change futura caso necessário.

- **[Slot único `Node.scriptInstance` introduz estado nullable que toda subclasse de Node carrega]** → custo de memória trivial (uma referência por Node). Trade-off contra benefício da composição "Node + script" Godot-like.

- **[Cache de bytecode Python (`.pyc`)]** → GraalPy gerencia internamente. Não precisamos do equivalente ao SHA-256 cache do Kotlin Scripting. Bug class inteiro desaparece.

- **[`@export NodeRef` precisa resolver caminhos de cena que mudam de nome]** → mesmo problema que já existe hoje; resolução é em runtime via `NodeRef.resolve(host)`. Sem mudança.

- **[Migração de Pong em fatias deixa estados intermediários onde nem tudo é Python]** → durante as fatias E2–E6, parte do Pong é Python e parte é Kotlin compilado. A SPI suporta isso porque um Node sem `scriptInstance` simplesmente roda o comportamento Kotlin tradicional. Gates manuais validam cada fatia.

## Migration Plan

Execução em fatias com gate manual. Sumário (detalhe em `tasks.md`):

- **E0**: criar `:engine-bundle-python` vazio + dep GraalPy. Smoke test: contexto Python sobe e executa `1 + 1`. Pong ainda 100% Kotlin Scripting.
- **E1**: introduzir `ScriptHost` + `Script` + `ScriptInstance` + `ExportedProperty` + `ScriptHostRegistry` em `:engine-bundle`. `Node.scriptInstance` slot e despacho de hooks. Sem implementação ainda registrada — APIs só.
- **E2**: implementar `PythonScriptHost` (carregar `.py` no Context, AST inspector, hooks). `:engine-bundle-python` registra-se no `ScriptHostRegistry` ao ser carregado. Smoke test: script Python trivial extends Node2D, exporta `speed`, é anexado a um Node2D criado manualmente.
- **E3**: estender `NodeEntry` com `script: String?` e `props: JsonObject?`. `BundleLoader` faz tree-walk dos scripts, chama `ScriptHostRegistry.loadAll`, atacha durante instanciação. Pong ainda Kotlin Scripting; teste com bundle dedicado de fixture.
- **E4**: migrar primeira folha do Pong (`CenterLine` ou `Score`) de `.nengine.kts` para `.py`. Atualizar `pong/scene.json` para o novo formato (somente esse nó). Gate: visual idêntico.
- **E5**: migrar `Walls`, `Goal`, `Ball`. Gate: jogabilidade idêntica.
- **E6**: migrar `Paddle`. Gate: W/S, AI e colisão funcionam.
- **E7**: migrar `PongScene`. Pong inteiro em Python. Gate: jogo completo idêntico.
- **E8**: deletar `KotlinScriptingHost`, `NEngineScript`, `ScriptSource`, `CyclicScriptDependencyError`, deps `kotlin-scripting-*` de `:engine-bundle`. Deletar capability `scripting`. Atualizar `CLAUDE.md`. Smoke test: build limpa do projeto.

Rollback parcial é possível até E7 (cada nó Python pode reverter a `.nengine.kts` se necessário). Após E8 o caminho Kotlin Scripting some — rollback exige reverter a change inteira.

## Open Questions

Nenhuma bloqueia a abertura. Resolvíveis durante apply:

- Versão exata do GraalPy: 24.0.0, 24.0.1, etc. Decidir no E0 olhando a estabilidade atual.
- Como expor o `Context` Polyglot para testes — provavelmente `internal` em `:engine-bundle-python` + abertura controlada via friend module.
- Local exato dos stubs `.pyi` (`src/main/resources/stubs/` é o palpite; pode virar artefato separado se ficar grande).
- Sintaxe do `extends` — docstring vs comentário convencional. Tendência: docstring (mais idiomática Python).
- Se `:engine-bundle` ainda precisa hospedar `.nengine.kts` durante a migração ou se sai logo em E8 com tudo migrado de uma vez.
- Comportamento quando `scene.json` tem `script` mas o `ScriptHostRegistry` não tem host para essa extensão — provavelmente lançar `UnsupportedScriptExtensionException` com mensagem clara.
