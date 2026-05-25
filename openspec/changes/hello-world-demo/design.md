## Context

A engine hoje tem três jogos/samples (`pong`, `tictactoe`, `demos`). Cada um deles é útil — mas nenhum cumpre o papel de "hello world canônico":

- **Pong**: usa bundle Python (`scene.json` + `scripts/*.py`), requer `:engine-bundle` + `:engine-bundle-python` + GraalPy. Inacessível como primeiro exemplo.
- **Tic-Tac-Toe**: roda em Compose (backend secundário). Não representa o caminho default da engine (Skiko).
- **Demos**: cinco slots de stress-test focados em invariantes (cache de `worldTransform`, mutação durante traversal, etc.). Cada slot já assume domínio de scene graph + colliders.

Falta um sample que mostre o **mínimo absoluto** para um leitor que abriu o repo pela primeira vez: como abrir uma janela e desenhar um texto. As primitivas necessárias (`Label`, `SkikoHost`, `SceneTree`, `Renderer.drawText`, `Renderer.measureText`) já existem — esta change só monta o exemplo.

## Goals / Non-Goals

**Goals:**

- Um módulo `:games:hello-world` executável em uma linha de comando (`./gradlew :games:hello-world:run`).
- Cena com **um único nó** como root: um `CenteredLabel` que estende `Label` e centraliza o próprio texto.
- `Main.kt` enxuto (idealmente <20 linhas) que monte a cena inline e chame `SkikoHost.run`.
- Servir como referência viva da API mínima da engine — algo que possa ser linkado em docs de onboarding ("comece por aqui").

**Non-Goals:**

- `Camera2D`, animação, input, colisão, áudio, scripting, múltiplos nós. Esses pertencem aos outros samples.
- Suportar Compose. Skiko é o backend default; introduzir Compose aqui dilui o ponto pedagógico.
- Adicionar APIs novas à engine. Se algo no exemplo se prove repetitivo, isso vira uma change separada (não esta).

## Decisions

### Decisão 1: Módulo separado `:games:hello-world`, não um slot dentro de `:games:demos`

**Alternativa considerada**: adicionar um slot "Hello" no `DemoSwitcherRoot` (tecla `6`).

**Por que separado**: O `:games:demos` tem propósito explícito documentado no `CLAUDE.md` — "cenas de demonstração visual das melhorias da engine". Cada slot lá assume que o leitor já conhece o scene graph e está olhando um aspecto específico. Hello World tem o propósito oposto: ser o primeiro contato. Mergulhá-lo num switcher com 5 outras coisas avançadas derrota o objetivo. Como módulo separado, o `Main.kt` fica isolado, mínimo e linkável.

### Decisão 2: Cena 100% code-only — sem `BundleLoader`, sem scripts, sem JSON

**Alternativa considerada**: bundle `hello/scene.json` com um único nó `Label`.

**Por que code-only**: o objetivo didático é mostrar **como a engine se compõe a partir das peças primárias**. Bundle/JSON/Python são camadas adicionais e cada uma tem custo cognitivo (versionamento de schema, registro de tipos, instanciação de `PythonScriptHost`). Para um leitor que ainda não viu nada, ver `Label` + `apply { ... }` em Kotlin é instantâneo; ver um `scene.json` exige primeiro entender o que é um bundle. Code-only também garante que `:games:hello-world` continue compilando sem precisar de `:engine-bundle*` mesmo se a SPI desses módulos mudar.

### Decisão 3: Sem `Camera2D` — usar `tree.size` direto

**Alternativa considerada**: adicionar um `Camera2D` filho com `bounds = Rect(Vec2.ZERO, Vec2(800f, 600f))`, `aspectMode = FIT`.

**Por que sem câmera**: introduzir `Camera2D` num "primeiro contato" obriga o leitor a entender mundo virtual + projeção + `AspectMode` antes de ver o primeiro pixel. A engine documenta no `CLAUDE.md` que árvores sem `Camera2D` caem no fallback identity — coordenadas mundiais são pixels da surface. Para um texto centralizado, isso é exatamente o que queremos: lendo `tree.size` no `onDraw` e dividindo por 2, o texto recentraliza sozinho quando a janela é redimensionada. Sem letterbox, sem invariante extra, sem peça que não está pagando seu próprio peso. `Camera2D` continua sendo demonstrado por Pong e Demos — não precisa aparecer aqui.

### Decisão 4: Subclasse nomeada `CenteredLabel : Label()`, não classe anônima

**Alternativa considerada**: `object : Label() { override fun onDraw(...) { ... } }` inline no `main`.

**Por que nomeada**: classe anônima é mais sintaxe (escopo capturado, conflito potencial entre `this.size` e outras `size` no escopo, leitor precisa decompor o `object :` para entender o que está sendo herdado). Uma classe top-level com nome diz o que ela é em letras de fôrma (`CenteredLabel`) e o `Main.kt` fica com uma só responsabilidade: configurar e rodar. Trade-off é um arquivo a mais — aceitável; um arquivo de 12 linhas com nome claro custa menos cognitivamente que um `object` anônimo embutido.

### Decisão 5: `Label` como root direto da `SceneTree`, sem `Node` wrapper

**Alternativa considerada**: `Node` raiz contendo um `Label` filho.

**Por que raiz direta**: `Label` é um `Node2D`, então a `SceneTree` aceita ele como root sem cerimônia. Pong e Demos usam wrappers porque a cena tem múltiplos filhos a coordenar; Hello World tem **um** nó, então adicionar um pai vazio é overhead pedagógico. A linha `SceneTree(root = label)` mostra de forma honesta que a árvore pode ter um único nó — útil para o leitor saber.

### Decisão 6: Centralização via `Renderer.measureText`, não hard-coded

**Alternativa considerada**: posicionar via `transform.position = Vec2(WORLD_WIDTH/2 - 60, WORLD_HEIGHT/2 - 8)`.

**Por que `measureText`**: o `Renderer` já expõe `measureText(text, size): Vec2` exatamente para esse caso. Um sample didático que usa um magic number 60 transmite o anti-pattern errado — o leitor vai copiar o magic number e quebrar quando trocar o texto ou o tamanho. Usar `measureText` mostra a forma correta. Como o `Label` base desenha em `worldPosition()`, `CenteredLabel` sobrescreve `onDraw` por inteiro (não chama `super.onDraw`) e usa `tree.size` para descobrir a surface — o texto se recentraliza automaticamente sob resize.

### Decisão 7: Valores inline em vez de constantes top-level

**Alternativa considerada**: `private const val TEXT = "Hello, world!"`, `FONT_SIZE = 32f`, etc.

**Por que inline**: três literais escritos in-place no `apply { ... }` são mais legíveis que três `const val` declarados no topo do arquivo + três referências símbólicas. O leitor vê onde mexer sem precisar saltar entre topo e `main()`. Constantes só pagam o próprio peso quando há reuso ou semântica não-óbvia — não é o caso aqui.

## Risks / Trade-offs

- **[`Renderer.measureText` pode retornar um bounding box que inclui ascenders/descenders, não apenas o stroke visível]** → o "centro óptico" do texto pode ficar levemente acima do centro geométrico calculado. Mitigação: aceitar essa imprecisão; se um leitor reclamar, a discussão é sobre a SPI do `Renderer`, não sobre o sample.
- **[Sem `Camera2D`, redimensionar a janela faz o texto se reposicionar — não há "mundo fixo"]** → é o comportamento desejado para Hello World (recentralização contínua). Se o leitor confundir esse comportamento com "engine sem mundo virtual", a documentação aponta para Pong/Demos onde `Camera2D` aparece.
- **[Adicionar um módulo a mais aumenta o tempo de configuração inicial do Gradle]** → impacto desprezível; já temos quatro módulos `:games:*` e dois `:engine-bundle*`.
- **[Risco de o sample virar dump de features no futuro]** → mitigação por convenção documentada na `proposal.md` e neste `design.md`: qualquer expansão (input, animação, câmera) deve virar um novo sample ou ir para `:games:demos`. `:games:hello-world` permanece minimal-by-policy.

## Migration Plan

Não há migração — é uma adição greenfield. Steps:

1. Adicionar `include(":games:hello-world")` em `settings.gradle.kts`.
2. Criar `games/hello-world/build.gradle.kts` (plugin `kotlinJvm` + `application`, dependências em `:engine` e `:engine-skiko`).
3. Criar `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/CenteredLabel.kt` com a subclasse de `Label` que centraliza no `onDraw`.
4. Criar `games/hello-world/src/main/kotlin/com/neoutils/engine/games/helloworld/Main.kt` que instancia `CenteredLabel`, configura `text`/`size`/`color` inline e chama `SkikoHost().run(...)`.
5. Atualizar `CLAUDE.md` (seção "Module Structure & How to Run") listando `:games:hello-world` e o comando `./gradlew :games:hello-world:run`.
6. Executar `./gradlew :games:hello-world:run` localmente para confirmar visualmente que a janela abre e o texto está centralizado.
7. Executar `./gradlew :games:pong:run`, `:games:tictactoe:run` e `:games:demos:run` para confirmar que os samples existentes continuam intactos.

Rollback: reverter o commit. Não há estado persistido a desfazer.

## Open Questions

- A subclasse `CenteredLabel` justifica eventualmente subir para `:engine` como `Label.Anchor.CENTER` ou `Label.align`? Possivelmente, mas é mudança de API pública e merece sua própria change OpenSpec. Para Hello World, a subclasse local resolve sem tocar no `:engine`.
- Vale também publicar um screenshot do sample na documentação? Fora do escopo desta change — pode virar follow-up.
