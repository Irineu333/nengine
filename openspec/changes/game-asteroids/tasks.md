## 1. Pré-requisitos e spike

- [ ] 1.1 Confirmar que `canvas-item-transform` está archived em `openspec/changes/archive/`. Se não estiver, **pausa**.
- [ ] 1.2 Spike: criar um teste rápido (ou um Main.kt descartável) que, a partir do `_ready` de um nó Python, construa via `add_child` uma sub-árvore `CharacterBody2D` + `CollisionShape2D` (com `CircleShape2D.radius` setado) + `Polygon2D` e anexe um script `.py` ao `CharacterBody2D` em runtime. Validar que o script anexado dispara `_ready` e que os exports são respeitados.
- [ ] 1.3 Se o spike falhar (rota de runtime `attach_script` não existe ou está grosseira), **pausa** e abre change separada (e.g. `packed-scene` ou `runtime-script-attach`). NÃO inflar `game-asteroids`.

## 2. Estrutura do módulo

- [ ] 2.1 Adicionar `:games:asteroids` em `settings.gradle.kts`.
- [ ] 2.2 Criar `games/asteroids/build.gradle.kts` análogo a `games/snake/build.gradle.kts` (dependências em `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-python`, `kotlinx-serialization`; plugin `application` + `mainClass`).
- [ ] 2.3 Criar `games/asteroids/src/main/kotlin/com/neoutils/games/asteroids/Main.kt` análogo a `games/snake/.../Main.kt`: constrói `PythonScriptHost.create()`, chama `BundleLoader.fromResources("asteroids", scripting = python)`, embrulha em `SceneTree`, chama `SkikoHost().run(...)`.

## 3. Esqueleto do bundle

- [ ] 3.1 Criar `games/asteroids/src/main/resources/asteroids/scene.json` com `"version": 2` e a hierarquia descrita na spec: `Camera2D` (current, bounds 800×600), `GameRoot` (script `game.py`) contendo `AsteroidsRoot`, `BulletsRoot`, `Ship` (script `ship.py`) + filhos (`CollisionShape2D` com `CircleShape2D radius=12`, `Polygon2D` com pontos triangulares nose-up), `ScoreLabel`, `LivesLabel`, `GameOverLabel`.
- [ ] 3.2 Criar `games/asteroids/src/main/resources/asteroids/scripts/` (vazio por enquanto).
- [ ] 3.3 Rodar `./gradlew :games:asteroids:run` — deve abrir a janela com a nave imóvel desenhada no centro, sem erros. Mesmo sem scripts ainda, a cena estática deve carregar.

## 4. Ship script

- [ ] 4.1 Criar `scripts/ship.py` com `# extends CharacterBody2D` e exports `TURN_RATE: float`, `THRUST_ACCEL: float`, `MAX_SHIP_SPEED: float`, `FIRE_COOLDOWN: float`.
- [ ] 4.2 Implementar `_ready`: `self._velocity = Vec2(0, 0)`, `self._cooldown = 0.0`, `self.add_to_group("ship")`, cachear `self._bounds = scene.find_child("Camera2D").bounds`.
- [ ] 4.3 Implementar `_process(dt)`: rotação por hold de ← / →, thrust por hold de ↑ (atualiza `self._velocity` clampando a magnitude em `MAX_SHIP_SPEED`), tiro por `wasKeyPressed(Space)` se `_cooldown <= 0` (chama `self._fire_bullet()`), decremento de `_cooldown`. Early-return se `game.is_paused()`.
- [ ] 4.4 Implementar `_physics_process(dt)`: `self.position = self.position + self._velocity * dt`; aplicar wraparound. Early-return se paused.
- [ ] 4.5 Implementar `_fire_bullet()`: cria sub-árvore `Area2D + CollisionShape2D(CircleShape2D radius~3) + Circle2D(radius~3, white)`, anexa script `bullet.py` com export `velocity = forward * BULLET_SPEED + self._velocity`, posiciona na ponta da nave (`self.position + forward * BULLET_SPAWN_OFFSET`), adiciona em `BulletsRoot`.
- [ ] 4.6 Implementar `_on_body_entered(body)`: se `body.is_in_group("asteroids")` e não paused, emite `game.lives_changed(-1)`.

## 5. Asteroid script

- [ ] 5.1 Criar `scripts/asteroid.py` com `# extends CharacterBody2D`, exports `size: str = "big"` e `velocity: Vec2 = Vec2(0, 0)`.
- [ ] 5.2 Implementar `_ready`: `self.add_to_group("asteroids")`, cachear bounds.
- [ ] 5.3 Implementar `_physics_process(dt)`: `self.position = self.position + self.velocity * dt`; wraparound. Early-return se paused.
- [ ] 5.4 Implementar `hit()`: branch em `self.size`. Para `big`/`medium`, computa duas velocidades `rotate(self.velocity, +30°)` e `rotate(self.velocity, -30°)`, chama `_spawn_sibling(new_size, new_velocity)` para cada. Emite `game.score_changed(SCORE_VALUES[self.size])`. `self.queue_free()`.
- [ ] 5.5 Implementar `_spawn_sibling(new_size, new_velocity)`: idem ao spawn inicial em `game.py`, mas garantindo que o pai é `self.get_parent()` (que é `AsteroidsRoot`).

## 6. Bullet script

- [ ] 6.1 Criar `scripts/bullet.py` com `# extends Area2D`, exports `velocity: Vec2 = Vec2(0, 0)`.
- [ ] 6.2 Implementar `_ready`: `self._ttl = BULLET_TTL`, `self._consumed = False`.
- [ ] 6.3 Implementar `_process(dt)`: decrementa `_ttl`; se `_ttl <= 0`, `queue_free`. Early-return se paused.
- [ ] 6.4 Implementar `_physics_process(dt)`: move por `self.velocity * dt`; wraparound. Early-return se paused.
- [ ] 6.5 Implementar `_on_area_entered(area)`: ignora se `_consumed`. Senão, se `area.is_in_group("asteroids")`, seta `_consumed = True`, chama `script_of(area).hit()`, `self.queue_free()`.

## 7. Game script (controlador)

- [ ] 7.1 Criar `scripts/game.py` com `# extends Node2D` e declarar signals: `score_changed: Signal = signal(int)`, `lives_changed: Signal = signal(int)`.
- [ ] 7.2 Implementar `_ready`: cachear `_bounds`, conectar `score_changed` ao handler que atualiza `ScoreLabel`, conectar `lives_changed` ao handler que decrementa lives e atualiza `LivesLabel` (e dispara `_trigger_game_over` quando chega a zero). Chamar `_spawn_initial_asteroids()`.
- [ ] 7.3 Implementar `_spawn_initial_asteroids()`: 4 asteróides `big`, posições aleatórias fora de `SAFE_SPAWN_RADIUS` do centro, velocidades aleatórias entre `MIN_ASTEROID_SPEED` e `MAX_ASTEROID_SPEED`.
- [ ] 7.4 Implementar `is_paused() -> bool` retornando `self._paused`.
- [ ] 7.5 Implementar `_trigger_game_over()`: seta `_paused = True`, esconde ship (preferir `Node2D.visible = false`; se não existir, `polygon.color = Color(1, 1, 1, 0)`), torna `GameOverLabel` visível.
- [ ] 7.6 Implementar `_process(dt)`: se `_paused` e `wasKeyPressed(Enter)`, chama `_restart()`.
- [ ] 7.7 Implementar `_restart()`: limpar children de `AsteroidsRoot` e `BulletsRoot`, resetar ship (posição, rotação, velocity, visibilidade), resetar HUD, resetar `_score`/`_lives`/`_paused`, chamar `_spawn_initial_asteroids()`.

## 8. Constantes e ajustes

- [ ] 8.1 Definir constantes consistentes nos scripts (ou em `scripts/constants.py` se Python via GraalPy suporta `from constants import *`): `TURN_RATE = 3.5` (rad/s), `THRUST_ACCEL = 240` (px/s²), `MAX_SHIP_SPEED = 320` (px/s), `FIRE_COOLDOWN = 0.2`, `BULLET_SPEED = 480`, `BULLET_TTL = 1.2`, `ASTEROID_SPEEDS = (40, 110)`, `SAFE_SPAWN_RADIUS = 120`.
- [ ] 8.2 Tunar visualmente: nave deve sentir responsiva mas não fácil demais; tiros não devem viajar a tela inteira em <0.5s; asteróides grandes não devem cruzar a tela em <4s.

## 9. Validação manual

- [ ] 9.1 `./gradlew :games:asteroids:run` — nave aparece no centro, 4 asteróides grandes circulando, HUD mostra "Score: 0" / "Lives: 3".
- [ ] 9.2 ← e → giram a nave **visualmente** (Polygon2D rotaciona com o `Node2D.rotation`).
- [ ] 9.3 ↑ acelera; soltar mantém a velocidade (sem fricção). Cap de velocidade aparente.
- [ ] 9.4 Space dispara bala visível com cooldown perceptível.
- [ ] 9.5 Bala atinge grande → vira 2 médios divergentes. Bala em médio → 2 small. Bala em small → desaparece. Score atualiza.
- [ ] 9.6 Nave bate em asteróide → "Lives" decrementa. Aos 0, "Game Over" aparece e nave some.
- [ ] 9.7 Enter no game-over reinicia tudo: 4 grandes, nave centrada, score zero.
- [ ] 9.8 Wraparound visível: nave/asteróide/bala saindo pela direita reentra pela esquerda; cima ↔ baixo.
- [ ] 9.9 F1 mostra FPS. F2 mostra colliders (1 círculo por asteróide/nave/bala).
- [ ] 9.10 Jogar por ~2 minutos sem crash; cascade de quebra estável; árvore não acumula órfãos (verificar via F2 ou contagem se possível).

## 10. Documentação

- [ ] 10.1 Adicionar seção "Para rodar Asteroids" em `CLAUDE.md` listando comando + controles.
- [ ] 10.2 `ROADMAP.md`: mover `game-asteroids` de Planned para Active; editar a descrição para remover "múltiplas shapes por objeto" e listar os 3 eixos de validação (rotação visual via canvas-item-transform, cascade spawn, N kinematic em gameplay real).
