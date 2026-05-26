## Why

Os `Node2D` visuais shipped por `:engine` (`Polygon2D`, `Line2D`, `Circle2D`, `ColorRect`, `Label`) hoje desenham somando `world().position` aos seus pontos/origem dentro do próprio `onDraw`, e o `Renderer.pushTransform` aceita apenas `(translation, scale)`. Isso ignora `world().rotation` e `world().scale`: nenhum desses nós pode aparecer rotacionado em tela mesmo quando o `Transform` da árvore diz que deveria. O caminho Godot-canônico é o oposto — `onDraw` desenha em **local space** (origem 0,0) e o engine aplica a `world transform` (incluindo rotação) via `pushTransform`/`popTransform` em volta de cada `onDraw`. A change `game-asteroids` precisa da nave girando para validar a `collision-overhaul`; antes de embutir lógica de rotação em script Python, evoluímos o pipeline de desenho para Godot-style.

## What Changes

- **BREAKING**: `Renderer.pushTransform(translation: Vec2, scale: Vec2)` passa a aceitar rotação. Assinatura final decidida no `design.md` (`pushTransform(transform: Transform)` ou `pushTransform(translation, rotation, scale)`); o efeito é compor `translate ∘ rotate ∘ scale` sobre o top da stack.
- **BREAKING**: `SceneTree.render(renderer)` envolve cada `Node2D.onDraw` num par `pushTransform(node.world()) ... popTransform()`. Nós não-`Node2D` (e.g. `Node` puro) não recebem push. O push do `Camera2D` (view transform) continua no topo, inalterado conceitualmente — view se compõe acima das world transforms por nó.
- **BREAKING**: nós visuais shipped passam a desenhar em **local space**:
  - `Polygon2D.onDraw` chama `renderer.drawPolygon(points, color)` direto, sem somar `world().position`.
  - `Line2D.onDraw` itera `points` em local space.
  - `Circle2D.onDraw` desenha em `Vec2.ZERO` com `radius`.
  - `ColorRect.onDraw` desenha `Rect(Vec2.ZERO, size)`.
  - `Label.onDraw` desenha em local space; alinhamento (center/etc.) baseado em `measureText` permanece, mas referenciado à origem local.
- **BREAKING**: `SkikoRenderer.pushTransform` e `ComposeRenderer.pushTransform` ganham rotação. Skiko via `canvas.rotate(degrees)` entre `translate` e `scale`; Compose via `DrawScope.rotate(degrees, pivot = Offset.Zero)` na ordem equivalente.
- **BREAKING**: overlay de debug de colliders no `GameHost` (em `:engine-compose` e `:engine-skiko`) é revisado: como cada `CollisionShape2D` agora desenha em local space, o overlay aplica o mesmo push da `world()` do shape antes de desenhar o AABB (ou recomputa em world coords, decisão no design).
- Atualiza KDoc do `Renderer.pushTransform` para refletir a nova semântica e a convenção "draw em local space" passa a viver na seção Performance Notes / Coding Conventions do `CLAUDE.md`.
- Adiciona testes unitários: (a) `Polygon2D` rotacionado via `transform.rotation = π/4` renderiza vértices em coords world rotacionados quando submetido a um `Renderer` fake que registra as transforms aplicadas; (b) `Line2D` herda translação+rotação do ancestral; (c) `SceneTree.render` empilha/desempilha exatamente uma vez por `Node2D` visitado.
- Validação cruzada manual: rodar `:games:pong`, `:games:tictactoe`, `:games:demos` (#1–#6), `:games:snake` (se já archived) e `:games:hello-world` — todos devem renderizar visualmente idênticos ao estado atual.

## Capabilities

### New Capabilities
- nenhuma.

### Modified Capabilities
- `engine-core`: redefine o contrato do `Renderer.pushTransform` para incluir rotação; redefine `SceneTree.render` para empilhar a `world()` por `Node2D`; redefine `onDraw` dos nós visuais shipped (`Polygon2D`, `Line2D`, `Circle2D`, `ColorRect`, `Label`) para operar em local space.
- `skiko-runtime`: redefine `SkikoRenderer.pushTransform` para incluir rotação via `canvas.rotate`.
- `compose-runtime`: redefine `ComposeRenderer.pushTransform` para incluir rotação via `DrawScope.rotate`.

## Impact

- **`:engine`**: `Renderer` SPI; `SceneTree.render`; `Polygon2D`, `Line2D`, `Circle2D`, `ColorRect`, `Label` (todos os `onDraw`).
- **`:engine-skiko`**: `SkikoRenderer.pushTransform` ganha rotação; overlay de debug de colliders no `SkikoHost`/`GameHost` revisado.
- **`:engine-compose`**: `ComposeRenderer.pushTransform` ganha rotação; overlay de debug equivalente revisado.
- **`:games:*`**: nenhum jogo precisa de mudança de código — todos devem continuar rodando visualmente idênticos. Será validação cruzada.
- **`:engine-bundle` / `:engine-bundle-python`**: nenhum impacto. Scripts não usam `pushTransform` direto; o efeito chega via `world().rotation` do `Node2D` que o script controla.
- **`CLAUDE.md`**: seção sobre `Camera2D` e Performance Notes ganham parágrafo sobre "draw em local space".
- **Pré-requisito de**: `game-asteroids` (nave rotaciona via `Polygon2D` + `world().rotation` sem código de rotação em script).
