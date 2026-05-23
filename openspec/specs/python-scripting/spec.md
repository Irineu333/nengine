# python-scripting Specification

## Purpose

Implementação concreta de `ScriptHost` para scripts Python `.py` no módulo `:engine-bundle-python`, usando GraalPy 24.x. É a primeira impl da SPI definida em `script-host`. Encapsula tipos `org.graalvm.polyglot.*` — esses não vazam para `:engine`, `:engine-bundle` nem para os jogos. Jogos que dependem desse módulo herdam o custo do runtime GraalPy; jogos que não dependem (ex.: `:games:tictactoe`) ficam livres dele.

## Requirements

### Requirement: engine-bundle-python module hosts the Python ScriptHost

O projeto SHALL prover um módulo Gradle `:engine-bundle-python` que depende de `:engine`, `:engine-bundle` e de GraalPy 24.x (`org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:python`). Esse módulo MUST ser o único local que conhece tipos de `org.graalvm.polyglot.*` no projeto. O módulo MUST NOT ser dependência de `:engine`, `:engine-bundle`, `:engine-skiko`, `:engine-compose`, ou de jogos que não usem scripting Python.

#### Scenario: engine-bundle-python exists with the right dependencies

- **WHEN** a build configuration de `:engine-bundle-python` é inspecionada
- **THEN** declara dependência em `:engine`
- **AND** declara dependência em `:engine-bundle`
- **AND** declara dependência em GraalPy (polyglot + python language)

#### Scenario: engine modules do not depend on engine-bundle-python

- **WHEN** a configuração de build de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-compose` é inspecionada
- **THEN** nenhum deles declara `:engine-bundle-python` como dependência

#### Scenario: GraalPy is contained in engine-bundle-python

- **WHEN** o classpath compilação de `:engine`, `:engine-bundle`, `:engine-skiko` e `:engine-compose` é resolvido
- **THEN** nenhum artefato `org.graalvm.polyglot:*` está presente

### Requirement: PythonScriptHost implements ScriptHost for .py files

`:engine-bundle-python` SHALL expor a classe `PythonScriptHost : ScriptHost` cuja `extension` é `.py`. Ao ser carregada (inicialização do módulo / call explícita pelo jogo), `PythonScriptHost` SHALL auto-registrar uma instância em `ScriptHostRegistry`. Cada `PythonScriptHost` MUST manter um `org.graalvm.polyglot.Context` único compartilhado entre todos os scripts que carrega.

#### Scenario: PythonScriptHost registers itself for .py

- **WHEN** `PythonScriptHost.install()` (ou equivalente) é chamado
- **THEN** `ScriptHostRegistry.hostFor("scripts/foo.py")` retorna essa instância
- **AND** `extension` é `.py`

#### Scenario: Multiple scripts share one Polyglot Context per host

- **GIVEN** um `PythonScriptHost` registrado e dois scripts `a.py` e `b.py` no bundle
- **WHEN** ambos são carregados pelo mesmo host
- **THEN** o `Context` Polyglot criado pelo host é exatamente um
- **AND** ambos os scripts são avaliados nesse contexto

### Requirement: extends declaration in Python module

Todo script Python carregado por `PythonScriptHost` MUST declarar o tipo Node que estende como **primeira linha não-vazia** do módulo, em um dos dois formatos: como docstring (`"""extends <NodeType>"""`) ou como comentário (`# extends <NodeType>`). `<NodeType>` MUST ser o nome simples (`Node2D`, `BoxCollider`, etc.) ou FQN (`com.neoutils.engine.scene.Node2D`) de um tipo registrado no `NodeRegistry`. Scripts sem declaração `extends` MUST falhar no `load`. O resolvedor MUST consultar `NodeRegistry` por nome simples primeiro (varrendo tipos registrados) e cair para FQN se houver match.

#### Scenario: Docstring extends form is accepted

- **GIVEN** um script começando com `"""extends Node2D"""`
- **WHEN** `PythonScriptHost.load(path, bundle)` é chamado
- **THEN** `Script.extendsType` é `Node2D::class`

#### Scenario: Comment extends form is accepted

- **GIVEN** um script começando com `# extends Node2D`
- **WHEN** `load` é chamado
- **THEN** `Script.extendsType` é `Node2D::class`

#### Scenario: Missing extends fails fast

- **GIVEN** um script sem `extends` na primeira linha não-vazia
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia o path do script e indica a falta da declaração `extends`

#### Scenario: Unknown extends type fails fast

- **GIVEN** um script com `# extends BananaNode` e `BananaNode` não registrado
- **WHEN** `load` é chamado
- **THEN** uma exceção é lançada
- **AND** a mensagem nomeia `BananaNode` e o path do script

### Requirement: AST inspector discovers @export via top-level type annotations

`PythonScriptHost.load` MUST descobrir `Script.exports` parseando o source do script com o módulo `ast` do Python (rodando dentro do Context Polyglot, mas sem executar o módulo do script). Cada nó `ast.AnnAssign` no top-level do módulo cujo target é um `Name`, cuja anotação resolve para um dos tipos suportados, e cujo `value` é uma expressão estaticamente avaliável (literal numérico, string, booleano, `None`, ou chamada simples de um tipo conhecido como `Vec2(0, 0)`) MUST virar um `ExportedProperty`.

#### Scenario: Top-level annotated assignment becomes an export

- **GIVEN** script Python com `speed: float = 360.0` no top-level
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="speed", type=Float::class, default=360.0)`

#### Scenario: Nested annotated assignment is ignored

- **GIVEN** script com `def foo(): x: int = 1`
- **WHEN** `load` é chamado
- **THEN** `exports` NÃO contém entrada para `x`

#### Scenario: Vec2 default literal is parsed

- **GIVEN** script com `size: Vec2 = Vec2(16.0, 96.0)`
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="size", type=Vec2::class, default=Vec2(16f, 96f))`

#### Scenario: Optional type is detected as nullable

- **GIVEN** script com `up_key: Optional[Key] = None`
- **WHEN** `load` é chamado
- **THEN** `exports` contém `(name="up_key", type=Key::class, default=null)`
- **AND** o ExportedProperty é tratado como nullable na injeção de props

### Requirement: ScriptInstance attaches self as the host Node

`PythonScriptHost.attach(node, script)` MUST instanciar o módulo Python no Context (executando seu top-level uma única vez se ainda não foi executado), depois injetar `node` como `self` para as chamadas de hook. A injeção MUST garantir que dentro de um hook `on_update(self, dt)` a expressão `self.transform` chama o getter Kotlin `Node2D.transform` (não cria atributo Python). Atributos `@export` MUST ser visíveis em `self` (leitura e escrita), correspondendo aos campos do Node ou a um proxy.

#### Scenario: self references the host Node

- **GIVEN** um script Python que dentro de `on_update` faz `self.transform.position.y += 1.0`
- **AND** o script anexado a um `Node2D` com `position = (0, 0)`
- **WHEN** `on_update(dt=1.0)` é chamado
- **THEN** o `Node2D.transform.position.y` agora é `1.0` (mutação refletida no Node Kotlin)

#### Scenario: Exported props readable via self

- **GIVEN** um script com `speed: float = 360.0` e `props: {"speed": 480.0}` no scene.json
- **WHEN** `on_update` lê `self.speed`
- **THEN** o valor lido é `480.0` (override do scene.json)

### Requirement: Hooks delegate from Node to ScriptInstance

`PythonScriptHost.attach` MUST retornar um `ScriptInstance` cujos métodos (`onEnter`, `onUpdate`, `onRender`, `onCollide`) invocam os métodos Python correspondentes (`on_enter`, `on_update`, `on_render`, `on_collide`) no objeto-instância. Métodos Python ausentes MUST resultar em no-op no `ScriptInstance` (não exceção). A conversão de nomes MUST ser fixa: `onUpdate ↔ on_update` (snake_case no Python, camelCase no Kotlin/SPI).

#### Scenario: on_update is dispatched

- **GIVEN** um script Python que define `on_update(self, dt)` que incrementa um contador
- **WHEN** o Node anexado passa por 60 ticks de `onUpdate(dt=0.016f)`
- **THEN** o contador foi incrementado 60 vezes

#### Scenario: Missing hook is a no-op

- **GIVEN** um script Python que NÃO define `on_collide`
- **WHEN** o Node anexado recebe uma colisão
- **THEN** nenhuma exceção é lançada
- **AND** o frame continua normalmente

### Requirement: Engine types are pre-bound in the Polyglot Context

`PythonScriptHost` MUST injetar os seguintes nomes como bindings no Context Polyglot, disponíveis sem `import` em qualquer script:

- `Vec2` → `com.neoutils.engine.math.Vec2`
- `Color` → `com.neoutils.engine.render.Color`
- `Rect` → `com.neoutils.engine.math.Rect`
- `NodeRef` → `com.neoutils.engine.serialization.NodeRef`
- `Key` → `com.neoutils.engine.input.Key`
- `BoxCollider` → `com.neoutils.engine.physics.BoxCollider`
- `Node2D` → `com.neoutils.engine.scene.Node2D`

Esses bindings MUST refletir as classes Kotlin (instanciar `Vec2(0.0, 0.0)` no Python cria um `Vec2` Kotlin).

#### Scenario: Vec2 is usable without import

- **GIVEN** um script Python que contém `v = Vec2(3.0, 4.0)`
- **WHEN** o script é executado
- **THEN** `v` é uma instância de `com.neoutils.engine.math.Vec2` cuja `x=3f` e `y=4f`

#### Scenario: Key enum is usable without import

- **GIVEN** um script Python que contém `if self.input.is_key_down(Key.W): ...`
- **WHEN** o script é executado
- **THEN** `Key.W` resolve para o enum constant `com.neoutils.engine.input.Key.W`

### Requirement: PyI stubs are published as module resources

`:engine-bundle-python` SHALL publicar arquivos `.pyi` (PEP 561 stubs) em `src/main/resources/stubs/engine/` cobrindo no mínimo: `Node`, `Node2D`, `BoxCollider`, `Renderer`, `Input`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`. Os stubs MUST refletir a API Kotlin pública de cada um desses tipos.

#### Scenario: Stubs resource directory exists

- **WHEN** o jar de `:engine-bundle-python` é construído
- **THEN** contém o diretório `stubs/engine/` com pelo menos `__init__.pyi`, `scene.pyi`, `math.pyi`, `render.pyi`, `input.pyi`, `physics.pyi`, `serialization.pyi`

#### Scenario: Stubs reflect public Kotlin API

- **GIVEN** o stub `engine/math.pyi`
- **WHEN** ele é inspecionado
- **THEN** declara `class Vec2: x: float; y: float; def __init__(self, x: float, y: float) -> None: ...`
- **AND** as assinaturas batem com as propriedades públicas da `Vec2` Kotlin

### Requirement: Polyglot Context is eagerly initialized

Para evitar cold start visível durante o primeiro frame, `PythonScriptHost` MUST inicializar seu `Context` Polyglot **antes** de o primeiro `load` ser chamado pelo `BundleLoader`. A inicialização ansiosa ocorre quando o `PythonScriptHost` é instanciado / registrado.

#### Scenario: Context is ready when first load runs

- **WHEN** o tempo entre `PythonScriptHost.install()` e a primeira chamada de `load` é medido
- **THEN** o Context já está construído
- **AND** o `load` em si não inclui custo de boot do Context (apenas parse + eval do módulo)
