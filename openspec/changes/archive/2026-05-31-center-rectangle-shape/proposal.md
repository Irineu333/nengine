## Why

`RectangleShape2D` é **corner-anchored** na física (ocupa `[position, position + size·scale]`, origem local no canto superior-esquerdo), enquanto `CircleShape2D` é centrado e o novo `Node2D.localBounds()` (change `node-local-bounds`) devolve um retângulo **centrado** `Rect(-size/2, size)`. Essa divergência — registrada como bug latente no `design.md` de `node-local-bounds` — faz `worldBounds()`/`treeBounds()` e `broadPhaseBounds()` discordarem em `size/2` para o mesmo collider retangular, e vai estourar visualmente no inspector/gizmo futuro (highlight do collider meio-tamanho fora do collider real). Centrar o retângulo elimina a divergência na raiz, alinha as duas shapes (`Circle` e `Rectangle` ambas centradas), e segue a convenção Godot (onde `RectangleShape2D` é centrado no dono).

## What Changes

- **BREAKING (semântica de física):** `RectangleShape2D` passa a ser **centrado** na origem local — ocupa `[position - size/2·scale, position + size/2·scale]`. A origem local do retângulo deixa de ser o canto e passa a ser o centro geométrico, casando com `CircleShape2D` e com `localBounds()`.
- Centrar os cantos locais em **um único ponto**: `obbCorners` e `RectangleShape2D.worldCorners` passam de `(0,0),(w,0),(0,h),(w,h)` para `(±w/2, ±h/2)`. Todo o resto da física flui por esses dois (sweeps rotacionados, SAT, overlap, `bounds`, broad-phase) — exceto os dois sweeps axis-aligned (`sweepRectRect`, `sweepCircleRect`), que codificam `world.position` como canto e recebem um deslocamento de origem de `-size/2·scale`.
- **Sem mudança** em: fórmula de inércia do retângulo (`mass·(w²+h²)/12` já assume centrado), `Shape2D.localBounds()` (já centrado), `RigidBody2D`, `ColliderWidget`/`ShapeGizmoWidget` (desenham via `bounds`/`worldCorners`, ajustam-se sozinhos).
- **Reposicionar todos os colliders retangulares** dos jogos e demos em `+size/2` para preservar gameplay 1:1 (paddles/paredes/gols de Pong, paredes de demos, e quaisquer corpos retangulares criados por script em Snake/TicTacToe). `TumblingSwarmDemo` já usa offset `-size/2` e filtra como caso de comparação.
- **Recalibrar testes** que assertam coordenadas absolutas sob a ancoragem antiga (`RectangleWorldCornersTest`, `Shape2DOverlapTest`, `PhysicsSystemTest`, `SweepTest`, e os sweeps de `CharacterBody2DTest`).

## Capabilities

### New Capabilities
<!-- Nenhuma capability nova: é modificação de comportamento existente. -->

### Modified Capabilities
- `engine-core`: a convenção de origem local de `RectangleShape2D` muda de **top-left corner** para **centro**. Os cenários de `bounds` e de `overlap` rect-rect que dependem explicitamente do canto (incl. o cenário SAT que justifica a separação "porque a origem local é o canto superior-esquerdo") passam a refletir a ancoragem centrada. O contrato de `bounds(world, localOffset)` (AABB world-space) permanece, mas o AABB resultante reflete o retângulo centrado.

## Impact

- **`:engine`**: `physics/Shape2D.kt` (`obbCorners`, `RectangleShape2D.worldCorners`, `sweepRectRect`, `sweepCircleRect`; demais funções herdam via esses). Nenhuma mudança em `RigidBody2D`, `CollisionShape2D`, `debug/ColliderWidget`, `debug/ShapeGizmoWidget`.
- **Jogos/demos**: `games/pong` (scene.json), `games/demos` (`BoundaryWalls`, `RotatingBoxDemo`, `CollisionStressDemo`, `SpawnerDemo`, `TumblingSwarmDemo`), e scripts de `games/snake`/`games/tictactoe` se criarem colliders retangulares — reposicionamento `+size/2`.
- **Testes**: recalibração de asserções absolutas (≈4–6 arquivos de teste). `ShapeLocalBoundsTest` e o cenário "centroid at world center" de `RectangleWorldCornersTest` já esperam centrado e **passam sem mudança**.
- **Prova viva**: a suíte completa de testes verde + **todos os jogos e demos** (Pong, Snake, TicTacToe, as 6 cenas de demos em Skiko e o entrypoint LWJGL) rodando com gameplay idêntico ao anterior, como sentinelas da mudança.
- **Invariante #3** (colisão via `CollisionObject2D`+`Shape2D`+`PhysicsSystem`) preservado; muda só a convenção de origem do retângulo, não a arquitetura.
- **Resolve** o risco documentado em `openspec/changes/node-local-bounds/design.md`: após esta change, `worldBounds()`/`treeBounds()` e `broadPhaseBounds()` concordam para retângulos.
