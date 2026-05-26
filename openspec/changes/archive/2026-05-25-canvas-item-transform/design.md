## Context

A engine cresceu Godot-style: `Node2D` tem `Transform` com posição, rotação e escala; `Camera2D` aplica view transform via `Renderer.pushTransform(translation, scale)`. Mas o tree-walk de `_draw` ainda mistura paradigmas:

- `Camera2D` faz **um push** no início de `SceneTree.render` e **um pop** no fim — corretamente Godot-style (transform aplicada pelo engine).
- Cada `Node2D` visual shipped (`Polygon2D`, `Line2D`, `Circle2D`, `ColorRect`, `Label`) faz o oposto dentro do próprio `onDraw`: lê `world().position` e soma manualmente aos pontos. Rotação e escala do mundo são **ignoradas**. Comentário em `engine-core` spec admite isso explicitamente ("rotation/scale of ancestors NOT applied to points in this change — known limitation").

Hoje funciona porque Pong, Velha, Snake e Demos sobrevivem sem rotar nada visualmente. Mas `game-asteroids` introduz a primeira nave que **gira em tempo real**. Antes de embutir lógica de rotação no script (rodar vértices a cada frame em Python), evoluímos o pipeline para o canonical Godot:

```
Antes                                Depois
─────                                ──────
SceneTree.render(r):                 SceneTree.render(r):
  push(cameraView)                     push(cameraView)
  walk root:                           walk root:
    node.onDraw(r) {                     pushIfNode2D(node.world())
      // soma world().position           node.onDraw(r) {
      r.drawPolygon(points + pos)          // pontos em LOCAL space
    }                                      r.drawPolygon(points)
  pop()                                  }
                                         popIfNode2D()
                                       pop()
```

A consequência é que `world().rotation` e `world().scale` passam a aparecer visualmente "de graça" para todos os nós shipped, e para qualquer subclasse de jogo que opere em local space.

## Goals / Non-Goals

**Goals:**
- `Renderer.pushTransform` aceita rotação. Composição: `translate ∘ rotate ∘ scale`.
- `SkikoRenderer` e `ComposeRenderer` implementam a composição via primitivas nativas dos respectivos backends (Skia `canvas.translate/rotate/scale`; Compose `DrawScope.translate/rotate/scale`).
- `SceneTree.render` empilha a `world()` de cada `Node2D` ao redor do seu `onDraw`. Nós não-`Node2D` (e.g. `Node` puro, `Timer`) não recebem push.
- Todos os `Node2D` visuais shipped passam a desenhar em local space.
- Pong, Velha, Demos #1–#6, Snake (se já archived na hora) e Hello-world continuam visualmente idênticos.
- Polygon2D + `rotation` no Transform local agora renderiza rotacionado em tela. Isso passa a ser exercitado por testes unitários com um `Renderer` fake gravador.

**Non-Goals:**
- Não introduz `PolygonShape2D` ou qualquer mudança em `Shape2D` / `CollisionShape2D`. Física é eixo separado.
- Não cria um `Renderer.withTransform { }` higher-order helper. O par push/pop direto continua sendo a API. Se o jogo quiser ergonomia, escreve sua própria.
- Não otimiza nada (sem matrix stack cache, sem dirty-pivot, etc.). É refactor de contrato, não de performance.
- Não toca em scripts Python — eles veem o efeito via `world().rotation` do Node2D que dirigem; a engine cuida do desenho.

## Decisions

### D1. Assinatura: `pushTransform(translation, rotation, scale)`, não `pushTransform(transform: Transform)`

Mantemos parâmetros nomeados em vez de empacotar num `Transform`:

```kotlin
fun pushTransform(translation: Vec2, rotation: Float, scale: Vec2)
fun popTransform()
```

**Por quê:** simétrico ao existente, mais legível em chamadas isoladas (`pushTransform(Vec2.ZERO, 0f, Vec2(2f, 2f))` lê mais natural do que construir um `Transform`), e evita o atrito de criar um `Transform` só para empurrar uma view do `Camera2D` (que hoje calcula `(translation, scale)` derivados, sem `rotation` natural).

**Alternativa rejeitada:** `pushTransform(transform: Transform)`. Mais idiomática para tree-walk (`push(node.world())` é curto), mas obriga callers que não têm `Transform` à mão (e.g. `DebugOverlay`, view do `Camera2D`) a construir `Transform(position=t, rotation=0, scale=s)` para usar. Optamos por ergonomia simétrica em vez de uniformidade sintática.

**Observação para callers comuns:** o tree-walk vai chamar `pushTransform(world.position, world.rotation, world.scale)` — três acessos a um único `Transform`. Não é uma alocação a mais; o `world()` já é cacheado (ver Performance Notes no `CLAUDE.md`).

### D2. Composição: `translate ∘ rotate ∘ scale` em torno da origem local

`pushTransform(t, r, s)` aplica `translate(t)` primeiro, depois `rotate(r)` em torno do **novo** origem (i.e. `t`), depois `scale(s)`. Isso significa que um ponto local `(x, y)` desenhado depois do push aparece em `t + R(r) · (s.x · x, s.y · y)`.

**Por quê:** é o que o `Transform.compose` da engine já faz pelo `world()` cache; o renderer apenas espelha o que o math primitive define. Skia e Compose ambos implementam essa composição nativamente (Skia: `canvas.translate; canvas.rotate; canvas.scale`; Compose: `DrawScope.translate { DrawScope.rotate(...) { DrawScope.scale(...) { } } }`).

**Sobre escala não-uniforme + rotação:** o resultado é uma transformação afim genérica (cisalhamento sob composições). Aceitamos esse comportamento — é o que Godot faz. Subir para 2D shear independente é overkill para o validador.

### D3. SceneTree.render empilha por `Node2D`, não por todo `Node`

```kotlin
// pseudo-código do novo walk
fun walk(node: Node, renderer: Renderer) {
    val isNode2D = node is Node2D
    if (isNode2D) {
        val w = node.world()
        renderer.pushTransform(w.position, w.rotation, w.scale)
    }
    node.onDraw(renderer)
    for (child in node.children) walk(child, renderer)
    if (isNode2D) renderer.popTransform()
}
```

`Node` puros (e.g. `Timer`) não recebem push — não têm `Transform`, e empilhar identity seria desperdício. O push do `Camera2D` continua sendo aplicado **uma vez antes do walk começar**, então a composição ao tempo do `onDraw` de um nó interno é `view ∘ ancestor1.world ∘ ancestor2.world ∘ ... ∘ self.world` — mas perceba que `self.world` já é a composição de ancestrais (via `world()` cacheado da engine). **Isso causa double-composition se empilhamos um push por nó na hierarquia.**

**Subdecisão D3a:** apesar do problema acima, empilhamos `self.world()` (não `self.transform` local). Por quê? Porque o `view` push é `pushTransform(translation, scale)` em coordenadas de **screen ∘ world**, e o `world()` está em coordenadas world. Então ao push do nó, queremos sair de world para local **rebaixando** a transform, não recompondo. O caminho correto:

```
view: screen-space ← (camera projection)
                  ↑
  push(node.world)        ← aplica world transform DO NÓ (já composta com ancestrais)
                          ← NÃO empilhamos os ancestrais antes; world() já contém eles
  draw em local space
  pop
```

Ou seja: **apenas o push do `Camera2D` e o push do `Node2D` específico cuja `onDraw` está rodando estão na stack durante aquele draw**. NÃO empilhamos um push por ancestral.

Reformulando o walk:

```kotlin
fun walk(node: Node, renderer: Renderer) {
    if (node is Node2D) {
        val w = node.world()
        renderer.pushTransform(w.position, w.rotation, w.scale)
        try {
            node.onDraw(renderer)
            for (child in node.children) walk(child, renderer)
        } finally {
            renderer.popTransform()
        }
    } else {
        node.onDraw(renderer)
        for (child in node.children) walk(child, renderer)
    }
}
```

Espera — isso ainda double-composição! Se `node = parent`, fizemos `push(parent.world)`. Depois recursão em `child`, que faz `push(child.world)` — e `child.world` já inclui `parent.world`. Resultado final: `view ∘ parent.world ∘ child.world`, com `child.world = parent.world ∘ child.local`, então efetivamente: `view ∘ parent.world ∘ parent.world ∘ child.local`. **Errado.**

**Decisão final (D3 reformulada):**

- Opção A: empilhar `world()` mas NÃO recursar com mais pushes — sequência plana, pop antes do recurse.
- Opção B: empilhar `local transform` (não `world()`), recursar normalmente — composição vem dos pushes acumulados.

Optamos por **Opção B**: empilhamos `node.transform` (local), não `node.world()`. Pushes compõem naturalmente via stack do renderer:

```kotlin
fun walk(node: Node, renderer: Renderer) {
    if (node is Node2D) {
        val t = node.transform   // LOCAL
        renderer.pushTransform(t.position, t.rotation, t.scale)
        try {
            node.onDraw(renderer)
            for (child in node.children) walk(child, renderer)
        } finally {
            renderer.popTransform()
        }
    } else {
        node.onDraw(renderer)
        for (child in node.children) walk(child, renderer)
    }
}
```

Resultado: ao tempo do `onDraw` de um nó profundo, a stack é `view → root.transform → ... → self.transform`, equivalente a `view ∘ self.world` (porque pushes acumulam pela ordem na stack). Composição correta, sem double-counting.

**Por quê escolhemos B:** alinhada com Godot orthodoxo (cada CanvasItem empilha SUA local transform, herdando a do pai via stack). É mais simples de implementar e de raciocinar. A única "desvantagem" é que callers que precisem de "world transform agora" durante `onDraw` ainda usam `node.world()` (cacheado), não a stack. Isso já é o padrão atual; nada muda.

### D4. Visual nodes desenham em local space

Cada `Node2D` visual shipped reescreve `onDraw` para usar coordenadas local-space:

```kotlin
// Polygon2D — antes
override fun onDraw(renderer: Renderer) {
    if (points.size >= 3) {
        val origin = world().position
        val translated = points.map { Vec2(origin.x + it.x, origin.y + it.y) }
        renderer.drawPolygon(translated, color)
    }
}

// Polygon2D — depois
override fun onDraw(renderer: Renderer) {
    if (points.size >= 3) renderer.drawPolygon(points, color)
}
```

Análogo para `Line2D` (itera `points` direto, sem somar origem), `Circle2D` (desenha em `Vec2.ZERO` com `radius`), `ColorRect` (desenha `Rect(Vec2.ZERO, size)`), `Label` (desenha em `Vec2.ZERO`; cálculos de alinhamento via `measureText` continuam relativos à origem local).

### D5. Camera2D view push continua intacto conceitualmente

A view do `Camera2D` é uma transform que vai de **world** para **screen**. Ela continua sendo o push do topo, aplicado uma vez antes do walk. Importante: ela já não tem rotação na engine atual (camera não rotaciona), e mesmo assim continuamos usando a assinatura nova: `pushTransform(translation, rotation = 0f, scale)`. Isso evita ter duas APIs.

### D6. Debug overlay: ajusta para nova assinatura, semântica preservada

`renderDebugOverlay` em `:engine/dx/DebugOverlay.kt` hoje:
1. Push da view transform do `Camera2D` (assinatura antiga `(translation, scale)`).
2. Itera `collectActiveCollisionShapes(tree)`, lê `shape.worldBounds()` (já world coords), `drawRect`.
3. Pop.

Mudança: o push da view passa a usar `(translation, rotation = 0f, scale)`. **Nada mais muda**. O overlay continua operando em world coords (não usa o per-Node2D push porque está fora do walk de `SceneTree.render`). Os AABBs continuam alinhados ao mundo projetado.

### D7. Compatibilidade dos testes existentes

Os scenarios em `engine-core/spec.md` para visual primitives (`ColorRect`, `Line2D`, `Polygon2D`, `Label`) hoje declaram saídas em **world coords**:

```
Polygon2D em world (100, 100): drawPolygon([Vec2(100, 100), Vec2(120, 100), Vec2(110, 120)], WHITE)
```

Após a change, o `drawPolygon` chamado pelo `onDraw` recebe `[Vec2(0, 0), Vec2(20, 0), Vec2(10, 20)]` (local), e o push do per-Node2D faz a translation. Os scenarios precisam ser **MODIFIED** para refletir a nova semântica:

```
Polygon2D em world (100, 100):
  pushTransform(translation=(100,100), rotation=0, scale=(1,1))
  drawPolygon([Vec2(0, 0), Vec2(20, 0), Vec2(10, 20)], WHITE)
  popTransform()
```

Os testes existentes de Pong/Velha/etc. NÃO assertam contra essa granularidade. Eles assertam visualmente (rodar manualmente). Para fins de regressão automatizada, vamos adicionar testes unitários novos com um `RecordingRenderer` que captura a sequência completa de chamadas (`push`, `draw*`, `pop`).

## Risks / Trade-offs

- **[Risco]** Algum jogo subclasse `Polygon2D` ou `Line2D` e sobrescreve `onDraw` somando `world().position` por conta própria → desenharia em "double world" (push + soma manual). **Mitigação:** grep das subclasses existentes nos `:games:*` para confirmar que nenhuma faz isso hoje; adicionar nota no `CLAUDE.md` sob "Coding Conventions" indicando o padrão local-space.

- **[Risco]** Backends Skiko/Compose discordam em ordem de composição entre `translate/rotate/scale` (ambíguo sob notações diferentes) → bug visual sutil sob rotação composta com pai rotacionado. **Mitigação:** testes unitários no `:engine` com `RecordingRenderer` que NÃO testam o backend nativo (testam a sequência de calls), mais um teste de integração visual no `:engine-skiko` e `:engine-compose` que renderiza um polígono em offscreen e compara pixels com tolerância. *Decisão:* manter teste de pixel-compare como **opcional** nesta change; se o golden falhar nos jogos existentes ao rodar manualmente, é sinal de bug; o teste de pixel-compare entraria como follow-up se aparecer flakiness.

- **[Trade-off]** A engine agora paga uma chamada `pushTransform`/`popTransform` por `Node2D` desenhado por frame, em vez de só pelo `Camera2D` — overhead pequeno mas mensurável em árvores densas. **Aceitação:** game-asteroids tem dezenas, snake centenas. Sem evidência de gargalo até ECS chegar. Performance prematura é anti-meta.

- **[Trade-off]** A composição `translate ∘ rotate ∘ scale` na ORDEM canônica significa que `scale.x = -1` (flip horizontal) faz tudo se desenhar invertido — incluindo subárvores filhas. É o que Godot faz; é o que queremos. Mas se algum jogo dependia (mesmo acidentalmente) de "scale só afeta posição e não escala renderizada" do estado antigo, vai parecer regressão. **Mitigação:** os jogos atuais (Pong, Velha, etc.) não tocam em `scale` exceto para `(1, 1)`; verificar com grep nos `scene.json` e nos `*.py`.

## Migration Plan

Refactor coordenado em ordem:

1. Estender `Renderer.pushTransform` na SPI (`:engine`) — adicionar `rotation: Float` como 2º parâmetro. Compilação quebra em todos os call sites.
2. Atualizar `SkikoRenderer.pushTransform` e `ComposeRenderer.pushTransform` para a nova assinatura, implementando rotação nativa.
3. Atualizar `Camera2D` computeViewTransform / call site em `SceneTree.render` para passar `rotation = 0f`.
4. Atualizar `DebugOverlay.renderDebugOverlay` para a nova assinatura (`rotation = 0f`).
5. Implementar o per-Node2D push em `SceneTree.render` (D3 Opção B). Verificar que jogos antigos ainda rodam (vão renderizar "double-deslocados" até o passo 6).
6. Reescrever `onDraw` de `Polygon2D`, `Line2D`, `Circle2D`, `ColorRect`, `Label` para local space.
7. Rodar manualmente `pong`, `tictactoe`, `demos`, `snake` (se already archived), `hello-world`. Diff visual = nenhum.
8. Adicionar testes unitários com `RecordingRenderer`.
9. Atualizar specs (engine-core, skiko-runtime, compose-runtime) com as deltas.
10. Atualizar `CLAUDE.md`: nova seção "Drawing in local space" sob Coding Conventions; nota sobre rotation no Camera2D / Renderer.

Rollback: reverter o commit; nenhum jogo persistiu nada novo no `scene.json`, então não há schema migration.

## Open Questions

- **Q1**: Devemos exigir que subclasses de `Node2D` que escrevam `onDraw` próprio respeitem o contrato local-space, ou aceitar que game code possa misturar paradigmas? *Inclinação:* documentar no `CLAUDE.md` ("subclasses de Node2D desenham em local space; o engine aplica a transform"). Não policiar via teste — é convenção, não invariante mecanicamente verificável.

- **Q2**: O nome `Renderer.pushTransform` ainda é o melhor? `pushCanvasItemTransform` seria mais Godot-like, mas mais longo. *Inclinação:* manter `pushTransform`.

- **Q3**: `DebugOverlay.computeViewTransform` em `Camera2D` retorna `Pair<Vec2, Vec2>` (translation, scale). Vale promover para `Transform` agora que pushTransform aceita rotação? *Inclinação:* não nesta change — out of scope (o helper continua só com translation+scale porque câmera não rotaciona). Se evoluir camera-rotation no futuro, é outra change.
