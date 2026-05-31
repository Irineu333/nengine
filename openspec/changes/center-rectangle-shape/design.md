## Context

Na física da engine, `RectangleShape2D` é **corner-anchored**: a origem local do retângulo é o canto superior-esquerdo, e ele ocupa `[position, position + size·scale]`. Isso está cravado em `obbCorners`/`worldCorners` (cantos locais `(0,0),(w,0),(0,h),(w,h)`) e nos dois sweeps axis-aligned (`sweepRectRect`, `sweepCircleRect`) que tratam `world.position` como canto. `CircleShape2D`, por outro lado, é centrado (`bounds` centra em `position`), e a fórmula de inércia do retângulo (`mass·(w²+h²)/12`) **já assume centrado**.

A change `node-local-bounds` introduziu `Node2D.localBounds()`, com `RectangleShape2D.localBounds() = Rect(-size/2, size)` (centrado). Resultado: para o mesmo retângulo, `worldBounds()`/`treeBounds()` (derivados do `localBounds` centrado) e `broadPhaseBounds()` (física corner-anchored) discordam em `size/2` — bug latente que dispara no inspector/gizmo futuro. Esta change centra a física do retângulo para fechar a divergência na raiz.

Restrições: invariante #3 (colisão via `CollisionObject2D` + `Shape2D` + `PhysicsSystem`) — muda só a convenção de origem do retângulo, não a arquitetura; clareza didática acima de performance; jogos e demos são sentinelas vivas (devem rodar com gameplay idêntico).

## Goals / Non-Goals

**Goals:**
- Centrar `RectangleShape2D` na origem local (`[position - size/2·scale, position + size/2·scale]`), igualando a `CircleShape2D` e a `localBounds()`.
- Concentrar a mudança no menor número de pontos possível (idealmente os dois geradores de cantos + dois sweeps axis-aligned), deixando o resto fluir por composição.
- Preservar **gameplay 1:1** em todos os jogos e demos, reposicionando colliders em `+size/2`.
- Manter a suíte de testes verde, recalibrando apenas asserções de coordenada absoluta.

**Non-Goals:**
- Mudar `CircleShape2D` (já centrado), a fórmula de inércia, ou o contrato de `Shape2D.bounds(world, localOffset)` (continua devolvendo AABB world-space — só o conteúdo reflete o retângulo centrado).
- Introduzir um campo de "anchor" configurável por shape (centro é a convenção única, como Godot).
- Mexer em `Node2D.localBounds()` / `worldBounds()` / `broadPhaseBounds()` — já corretos; esta change os faz **concordar**.
- Otimizações de broad-phase ou novas formas de shape.

## Decisions

### D1 — Centrar nos dois geradores de cantos, deixar o resto herdar

`obbCorners(world, size, localOffset)` e `RectangleShape2D.worldCorners(world)` são os únicos pontos que materializam os cantos do retângulo a partir do `size`. Centrá-los (de `(0,0),(w,0),(0,h),(w,h)` para os quatro `(±w/2, ±h/2)`, preservando a ordem documentada de cada um) propaga automaticamente para:

- `RectangleShape2D.bounds` (usa `obbCorners`),
- `sweepRotatedRectRotatedRect` e `obbVsObbOverlap` (usam `obbCorners` → eixos SAT e projeções já relativos),
- `sweepCircleRotatedRect`/`sweepRotatedRectVsCircle` (resolvem no frame local do rect via `sweepCircleRect`),
- `rectCircleOverlap` (usa `bounds`),
- gizmos de debug (`ShapeGizmoWidget` via `worldCorners`).

Após a change `node-local-bounds`, ambos já foram reescritos sobre `Transform.apply`, então a mudança é trocar os quatro pontos locais passados a `apply`. Alternativa considerada: um campo `anchor` por shape — rejeitado por adicionar superfície/condicional sem caso de uso (a engine quer uma convenção única).

### D2 — Deslocamento de origem nos dois sweeps axis-aligned

`sweepRectRect` e `sweepCircleRect` **não** passam por `obbCorners`: codificam o retângulo diretamente como `[position, position + size·scale]` (ex.: `ax0 = aWorld.position.x`, expansão de Minkowski `bx0 - aw .. bx0 + bw`, `coerceIn(rx0, rx1)`). A correção é deslocar a origem efetiva do(s) retângulo(s) por `-size/2·scale` no topo da função (`ax0 = position.x - aw/2`, idem para B / para o rect em circle-rect). Toda a lógica de penetração, normal, slab e refino de canto é **relativa** a essa origem e permanece idêntica — só os pontos de entrada mudam. É o passo de maior risco (caminho quente, fórmulas inline) e fica isolado, coberto pela suíte de sweep recalibrada.

### D3 — Reposicionar colliders em `+size/2`, não compensar na engine

Para preservar gameplay, cada collider retangular que hoje assume canto deve mover seu `CollisionShape2D`/corpo em `+size/2·scale` (o centro novo coincide com o antigo canto deslocado). Isso é feito nos dados/código dos jogos (scene.json, demos Kotlin, scripts), **não** com um shim de compatibilidade na engine — a engine fica limpa com a convenção única, e os jogos ficam explícitos sobre onde o collider está. `TumblingSwarmDemo` já usa `position = -size/2` (compensando o canto antigo): com o retângulo centrado, esse offset deixa de ser necessário e o demo serve de caso de comparação ("antes precisava de offset, agora não").

### D4 — Recalibrar testes, distinguindo "já-centrado" de "precisa mudar"

Testes que asseram coordenada absoluta sob ancoragem de canto são atualizados para a centrada (`RectangleWorldCornersTest` cantos não-rotacionados; `Shape2DOverlapTest` envelope rotacionado e comentário; `PhysicsSystemTest` `bounds origin`; `SweepTest` layouts rect-rect; sweeps de `CharacterBody2DTest`). Os que já esperam centrado **não mudam** e viram regressões de confirmação: `RectangleWorldCornersTest` "centroid at world center", `ShapeLocalBoundsTest` inteiro. O cenário SAT de `engine-core` (linha ~577, que separa "porque a origem é o canto") é reescrito para a geometria centrada.

## Risks / Trade-offs

- **Regressão sutil em sweep/overlap rotacionado** (caminho quente, SAT/temporal) → Mitigação: D1 não toca a lógica, só os pontos de canto; manter o passo isolado; rodar a suíte de física completa + os demos rotacionais (`RotatingBoxDemo`, `TumblingSwarmDemo`) como prova viva.
- **Gameplay deslocado meio-tamanho se algum collider for esquecido** → Mitigação: o relatório de blast radius enumera todos os call-sites; rodar cada jogo/demo e conferir visualmente (paddles batem na bola, bola não atravessa parede, X/O no lugar).
- **Colliders criados dinamicamente por script** (Snake, demos com spawner) podem ter offset implícito → Mitigação: revisar cada script/spawner; onde o collider coincide com o centro do visual, a mudança simplifica (remove offset); onde assume canto, somar `size/2`.
- **Specs de sample (`pong-sample`, `demos-sample`) descrevem gameplay, não coordenadas de collider** → não precisam de delta; o comportamento observável é idêntico. Só `engine-core` ganha delta (a convenção de origem é contrato dele).

## Migration Plan

1. Centrar `obbCorners` + `worldCorners` (D1); rodar suíte de física — esperam-se falhas só nos testes de coordenada absoluta.
2. Deslocar origem em `sweepRectRect`/`sweepCircleRect` (D2).
3. Recalibrar testes (D4) até verde.
4. Reposicionar colliders de jogos/demos (D3); rodar cada um e validar gameplay.
5. Sincronizar o delta de `engine-core` e arquivar; atualizar a nota de risco em `node-local-bounds` apontando que foi resolvida.

Rollback: a mudança é coesa e isolada em `Shape2D.kt` + dados de jogo; reverter o commit restaura a ancoragem de canto.

## Open Questions

- Algum bundle/scene de exemplo fora dos jogos shipped (ex.: fixtures de teste de `scene-serialization`) referencia posição de collider retangular dependente de canto? Verificar na fase de apply ao varrer os `scene.json`.
