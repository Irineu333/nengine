## Why

Pong validou movimento contínuo + colisão básica. Velha validou o segundo backend + input por mouse. Snake validou gameplay discreto + mutação dinâmica do scene graph. As demos #4–#6 validaram `moveAndCollide` em corpos rotados sob carga. Falta o validador que junta tudo:

- **Rotação visual em tempo real** de um wireframe (`Polygon2D` triangular) usando o pipeline Godot-style que a change `canvas-item-transform` entrega.
- **Cascade de spawn durante signal handler**: bala (`Area2D`) bate em asteróide (`PhysicsBody2D`), o handler do asteróide remove ele próprio e instancia 2 filhos siblings com velocidades divergentes — tudo dirigido por script Python, sem código novo na engine.
- **N corpos kinematic em gameplay real** (não em cena demo): nave + 4–8 asteróides simultâneos, todos com `moveAndCollide`, sem tunneling.

Asteroids é o vetor canônico para isso. É o validador final da fundação Godot-style antes de virarmos o foco para `editor-visual`.

## What Changes

- Adiciona módulo executável `:games:asteroids` (Skiko) com `Main.kt` análogo a Pong/Snake.
- Adiciona bundle `asteroids/` em `src/main/resources/asteroids/` com `scene.json` (versão `2`, properties Godot-style) e `scripts/*.py`.
- Cena Asteroids:
  - `Camera2D` `current=true`, `bounds = Rect(0, 0, 800, 600)`.
  - `Ship` (`CharacterBody2D`, script `ship.py`) com filhos:
    - `CollisionShape2D` carregando um `CircleShape2D` `radius ≈ 12`.
    - `Polygon2D` triangular em local-space (e.g. `[(-10, 8), (0, -12), (10, 8)]`), branco.
  - `AsteroidsRoot` (`Node2D` container) — asteróides são spawnados como filhos em runtime.
  - `BulletsRoot` (`Node2D` container) — balas são spawnadas como filhas em runtime.
  - `ScoreLabel` (`Label`, texto inicial `"Score: 0"`), `LivesLabel` (`Label`, `"Lives: 3"`), `GameOverLabel` (`Label`, escondido até game-over).
  - `GameRoot` (`Node2D` root, script `game.py`) coordena o estado global: spawn inicial de asteróides, contagem de score/lives, restart, conexão com signals.
- Mecânica:
  - Input no `Ship` (`_process`): `Key.ArrowLeft` rotaciona contra-horário (`rotation -= turnRate * dt`), `Key.ArrowRight` horário, `Key.ArrowUp` aplica thrust no `velocity` (`velocity += direction * thrustAccel * dt`, sem fricção — Asteroids canônico), `Key.Space` dispara bala respeitando cooldown.
  - `_physics_process` da nave aplica `velocity * dt` à posição via `moveAndCollide` (ou direto, em wraparound; ver design).
  - Wraparound contínuo via `Camera2D.bounds` em `Ship`, asteróides e balas: `pos = ((pos - bounds.origin) mod bounds.size) + bounds.origin`.
  - Cada asteróide carrega `size: str ∈ {"big", "medium", "small"}` e `velocity: Vec2` como exports do script.
  - Cascade de quebra: o `bullet.py` no `_on_area_entered(area)` detecta se `area` é asteróide (via grupo `"asteroids"` ou tipo); aciona `asteroid.hit()`. O handler `asteroid.hit()` decide pelo `size`: `"big"` → spawna 2 médios siblings na mesma posição com velocidades rotacionadas `±30°` da velocidade atual, remove self; `"medium"` → spawna 2 small; `"small"` → só remove. Em todos os casos a bala também é removida e `score_changed.emit(delta)` é emitido pelo asteróide (delta = `20|50|100`).
  - Game over: `ship.py` no `_on_body_entered(body)` se `body` pertence ao grupo `"asteroids"` decrementa `lives_changed.emit(-1)`. Quando `lives == 0`, `game.py` ativa `_paused = True`, esconde a nave, mostra `GameOverLabel`.
  - Restart: `game.py` em `_process` checa `wasKeyPressed(Key.Enter)` quando `_paused`; limpa `AsteroidsRoot` e `BulletsRoot`, reposiciona ship no centro com `velocity = Vec2.ZERO`, recria 4 asteróides grandes em posições aleatórias longe do centro, reseta score/lives e esconde `GameOverLabel`.
- Atualiza `CLAUDE.md` com seção "Para rodar Asteroids" listando controles.
- Atualiza `ROADMAP.md`:
  - Remove `game-asteroids` de **Planned** (passa a **Active** enquanto roda).
  - Edita a descrição: drop da frase "múltiplas shapes por objeto" — decisão consolidada no explore: uma `CircleShape2D` por objeto é canônico Atari'79 e suficiente como validador.
- Não há mudança em `:engine`, `:engine-skiko`, `:engine-bundle` ou `:engine-bundle-python` por esta change. **Pré-requisito hard**: `canvas-item-transform` precisa estar archived antes do apply de `game-asteroids` (a nave gira via `world().rotation` visualmente).

## Capabilities

### New Capabilities
- `asteroids-sample`: jogo Asteroids jogável como módulo executável `:games:asteroids`, validador end-to-end de Godot-style transform Composição (canvas-item-transform), wraparound contínuo, cascade de spawn durante signal handler, e N corpos `CharacterBody2D` com `moveAndCollide` sob gameplay real.

### Modified Capabilities
- nenhuma.

## Impact

- **Novo módulo**: `:games:asteroids` (~análogo a `:games:snake` em estrutura). Adicionar entrada no `settings.gradle.kts`.
- **Dependência hard** em `canvas-item-transform` archived. Esta change SOMENTE pode entrar em apply depois.
- **Sem mudança em `:engine`**. Se durante o apply aparecer necessidade de API nova (e.g. ergonomia de spawn de sub-árvore via Python), pausa e abre change separada. NÃO inflar `game-asteroids`.
- **Possível descoberta**: instanciar um asteróide (uma sub-árvore com `CharacterBody2D` + `CollisionShape2D` + `Polygon2D` + script) via Python pode ficar verboso comparado a `PackedScene.instantiate()` do Godot. Se ficar grotesco no apply, é sinal de que `packed-scene` deveria virar uma change própria — registrado como Open Question no design, NÃO assumido como certo aqui.
- **`:games:demos`** não é afetado. `:games:snake` (se ainda em curso) não é afetado.
- **Pré-requisito de testes manuais**: rodar `./gradlew :games:asteroids:run`, controlar a nave, atirar, ver os asteróides quebrarem em pedaços, validar wraparound, restart.
