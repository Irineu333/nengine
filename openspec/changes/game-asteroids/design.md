## Context

A engine chega em `game-asteroids` com: scene graph Godot-style; `Camera2D.bounds` como mundo virtual; `CharacterBody2D.moveAndCollide` com sweep CCD-correto em circles e rects rotados; `Area2D` sensor com `area_entered`/`body_entered` signals built-in; scripts Python com hooks `_process`/`_physics_process` e signals user-declared; `Node.addToGroup`/`isInGroup` para identificação de classes lógicas no runtime; mutação segura da árvore durante traversal (pending queues); `BundleLoader` + `PythonScriptHost`; e — com `canvas-item-transform` archived antes do apply — `Polygon2D` que rotaciona visualmente sob `world().rotation`.

Asteroids é o **quinto jogo** do repositório, posicionado como o validador final da fundação antes de virar foco para o editor visual.

```
Jogo        Backend   Tick      Input              Validador central de
─────       ───────   ─────     ─────              ────────────────────
Pong        Skiko     contínuo  W/S (hold)         física básica, signals py
Velha       Compose   n/a       mouse              segundo backend
Snake       Skiko     discreto  setas (edge)       Timer, mutação por script, Kotlin→py signal
Demos       Skiko     contínuo  teclas numéricas   smoke tests por feature
Asteroids   Skiko     contínuo  setas (hold)+space rotação visual, cascade spawn, N kinematic em real
```

Mundo: 800×600 (mesma proporção das demos, mais largo do que Snake/Pong para acomodar movimento livre).

## Goals / Non-Goals

**Goals:**
- Jogo Asteroids jogável `./gradlew :games:asteroids:run` com restart por tecla.
- Nave triangular (`Polygon2D`) rotaciona visualmente em tempo real **sem código de rotação em Python** — apenas `self.rotation += turnRate * dt`. A engine cuida do resto.
- Cascade de quebra estável: `big → 2 medium → 4 small → 0`, sem leaks (todo asteróide queue-removido), sem corrupção da árvore (mutações deferidas durante o physics walk).
- 4–8 asteróides simultâneos + 0–8 balas + 1 nave todos resolvendo `moveAndCollide` no mesmo frame sem tunneling visível.
- Wraparound contínuo em três tipos de nó (ship, asteróides, balas) com a mesma fórmula.
- Asteroids **inteiramente em Python**. `Main.kt` é cosmético, igual Pong/Snake.

**Non-Goals:**
- Sons, música, animações.
- Power-ups (escudo, bala dupla, etc.). Asteroids "puro" só.
- IA / oponente. Single-player puro.
- Persistência de high score.
- Dificuldade progressiva por wave. Restart sempre devolve 4 grandes.
- Suporte ao backend Compose (Asteroids é Skiko-only, igual Pong/Snake/Demos).
- `PolygonShape2D` (forma triangular real para a nave). Continua sendo aproximação por círculo — canônico Atari'79.
- Multi-shape por objeto (cluster de circles cobrindo polígono). Decidido no explore: foge do canônico e não adiciona validação além do que demos #4–#6 já validaram.
- `PackedScene.instantiate()`. Spawn de sub-árvore é construído em Python à mão (`add_child` de cada nó). Se ficar grotesco, levanta change separada — não inflar Asteroids.

## Decisions

### Asteróide e bala como sub-árvore construída em Python

Cada asteróide é, ao spawn:

```python
def spawn_asteroid(parent, size, world_pos, world_velocity):
    body = CharacterBody2D()
    body.add_to_group("asteroids")
    body.position = world_pos
    body.velocity = world_velocity     # vive no scriptInstance via export

    shape_node = CollisionShape2D()
    circle = CircleShape2D()
    circle.radius = ASTEROID_RADIUS[size]
    shape_node.shape = circle
    body.add_child(shape_node)

    visual = Polygon2D()
    visual.points = ASTEROID_POINTS[size]  # local-space, irregular
    visual.color = Color.WHITE
    body.add_child(visual)

    # Anexa script asteroid.py com exports size e velocity
    attach_script(body, "scripts/asteroid.py", { "size": size, "velocity": world_velocity })

    parent.add_child(body)
    return body
```

**Por quê:** mais verboso que `PackedScene.instantiate()`, mas usa só o que já existe. A função vive em `game.py` como helper local. A bala segue padrão idêntico mas com `Area2D` em vez de `CharacterBody2D` e `Circle2D` em vez de `Polygon2D`.

**Risco aberto:** se `attach_script` em runtime (não a partir de `scene.json` no load) ainda não está pronto / ergonômico via `:engine-bundle-python`, esse caminho precisa ser validado no início do apply. Se exigir API nova, **pausa Asteroids e abre change separada** (registrado em Open Questions).

**Alternativa rejeitada:** asteróides como `ColorRect` rotacionados sem script, com lógica de quebra centralizada em `game.py`. Mais simples, mas perde o validador "signal handler dispara spawn cascateado" — que é metade da razão dessa change existir.

### Identificação de classes lógicas via `Group`

`asteroid.py` faz `self._node.add_to_group("asteroids")` em `_ready`. `bullet.py` faz `self._node.add_to_group("bullets")`. A nave faz `self._node.add_to_group("ship")`. Handlers como `bullet._on_area_entered(area)` checam `area.is_in_group("asteroids")` antes de chamar o handler de quebra. Idem para a nave: `ship._on_body_entered(body)` checa `body.is_in_group("asteroids")` antes de decrementar vidas.

**Por quê:** o `NodeRegistry` permite `node.is_in_group("name")` em O(1) sobre um `Set<String>` mantido em `Node`. Mais robusto do que checar tipo (que quebra se uma sub-classe aparece) e mais simples do que `script_of(node)` + match por path do script.

### Wraparound de coordenadas: depois do `_physics_process`

A regra é a mesma para ship/asteroid/bullet, encapsulada num helper no script base ou inline:

```python
def wrap(self):
    bounds = self.scene_bounds()  # cacheado em _ready a partir do Camera2D
    p = self.position
    size = bounds.size
    origin = bounds.origin
    x = ((p.x - origin.x) % size.x) + origin.x
    y = ((p.y - origin.y) % size.y) + origin.y
    self.position = Vec2(x, y)
```

Aplicada no final de `_physics_process` depois do movimento (e depois de `moveAndCollide` se aplicável). Python `%` é positivo para argumentos negativos, então o wrap funciona em ambas as direções sem branch.

**Por quê depois do `_physics_process` e não dentro:** queremos que a colisão veja a posição "verdadeira" pré-wrap; só depois corrige. Senão, um asteróide atravessando uma borda pode ser detectado em dois lugares simultaneamente pela física.

### Ship rotaciona via `self.rotation`, não via vetor `forward` discreto

A nave guarda apenas `velocity: Vec2` como estado dinâmico. Direção do thrust deriva de `self.rotation` (que a engine usa para a transform e que `canvas-item-transform` faz o `Polygon2D` honrar visualmente):

```python
def _process(self, dt):
    if is_key_down(Key.ArrowLeft):  self.rotation -= TURN_RATE * dt
    if is_key_down(Key.ArrowRight): self.rotation += TURN_RATE * dt
    if is_key_down(Key.ArrowUp):
        forward = Vec2(sin(self.rotation), -cos(self.rotation))   # nose-up = -Y em screen coords
        self._velocity = self._velocity + forward * (THRUST_ACCEL * dt)
    if was_key_pressed(Key.Space) and self._cooldown <= 0:
        self.fire_bullet()
        self._cooldown = FIRE_COOLDOWN
    self._cooldown = max(0.0, self._cooldown - dt)
```

**Por quê `forward = (sin(rot), -cos(rot))`:** convenção: rotation = 0 aponta para cima (nariz da nave em `(0, -12)` no `Polygon2D`); rotação positiva é horária (consistente com `+x` à direita e `+y` para baixo na convenção de screen coords da engine). Asteroids canônico tem nariz para cima ao iniciar.

### Cascade de quebra: handler chama método, não emite mais signal

```python
# bullet.py
def _on_area_entered(self, area):
    if area.is_in_group("asteroids"):
        script_of(area).hit()   # método direto, não signal
        self.queue_free()
```

```python
# asteroid.py
def hit(self):
    if self.size == "big":
        self._spawn_children(2, "medium", deflection_deg=30)
    elif self.size == "medium":
        self._spawn_children(2, "small", deflection_deg=30)
    # small: só remove
    score_changed.emit(SCORE_VALUES[self.size])
    self.queue_free()
```

**Por quê chamada direta em vez de signal cascade:** signal seria `bullet.hit_asteroid.connect(asteroid.hit)` — mas a bala não conhece o asteróide ao build-time, e conectar dinamicamente no momento da colisão é mais cara que chamar `script_of(area).hit()` direto. Signal `score_changed` é onde o handler indireto importa (`game.py` escuta para atualizar o `ScoreLabel`).

**Mutação durante traversal:** `area_entered` é disparado pelo `PhysicsSystem.step(tree)`, que executa **dentro** do walk de física. `addChild`/`removeChild` durante esse window são tratados via pending queues (invariante já documentado em `engine-core/Safe mutation during scene traversal`). Os 2 médios + remoção do big ficam pending; drenam no fim do step. Isso é exatamente o caminho exercitado por demos #3 (spawner) — Asteroids amplifica.

### Game over: paused flag em `game.py`, ship não desativa colisão

Quando `lives == 0`:
1. `game.py` seta `self._paused = True`.
2. `game.py` esconde a nave (`ship.visible = false` — se `Node2D.visible` existe; senão usa `color.a = 0` no `Polygon2D` filho).
3. Mostra `GameOverLabel`.
4. Em `_physics_process` dos asteróides e da nave, eles checam `game.is_paused()` no início e early-return se `True`. Mesmo o `_process` da ship ignora input nesse estado, exceto `Enter` que `game.py` consome para restart.

**Por quê não simplesmente `tree.stop()`:** restart precisa reconstruir, não restart de processo. Manter a árvore viva mas pausada simplifica e é o padrão Godot (`get_tree().paused = true`).

### Restart: `game.py` é dono do reset

`game.py` em `_process` quando `_paused and wasKeyPressed(Key.Enter)`:

1. Itera `AsteroidsRoot.get_children()` e `removeChild` de cada um.
2. Itera `BulletsRoot.get_children()` e `removeChild` de cada um.
3. Reset ship: `position = bounds.center()`, `rotation = 0`, internal `_velocity = Vec2.ZERO`, torna visível de novo.
4. Reset HUD: `ScoreLabel.text = "Score: 0"`, `LivesLabel.text = "Lives: 3"`, `GameOverLabel.visible = false`.
5. Spawn 4 asteróides grandes em posições aleatórias **fora** de um raio `SAFE_SPAWN_RADIUS = 120` do centro (para não nascer encostado na nave).
6. `_paused = False`, `_score = 0`, `_lives = 3`.

### TTL de bala via contador, não via Timer

Bala não usa `Timer` — usa contador inline em `_process`:

```python
def _ready(self):
    self._ttl = BULLET_TTL  # seconds

def _process(self, dt):
    self._ttl -= dt
    if self._ttl <= 0:
        self.queue_free()
```

**Por quê:** `Timer` é overkill para o caso. Snake usa `Timer` porque o tick é o coração do gameplay e abstrair via signal vale. Aqui é uma vida útil simples.

**Cooldown de tiro:** mesma técnica, mantido em `ship.py`.

## Risks / Trade-offs

- **[Risco]** `attach_script` em runtime para nós criados via Python pode não estar suficientemente exercitado / ergonômico. **Mitigação:** primeira tarefa do apply é validar essa rota com um spike isolado (asteroide hardcoded spawnado num `_ready`). Se quebrar, pausa e abre change separada de `runtime-script-attach` ou `packed-scene`.

- **[Risco]** Cascade de quebra dispara N `area_entered` simultâneos no mesmo frame se uma bala tangenciar 2 asteróides — `bullet.queue_free()` no primeiro impacto deveria evitar (a bala sai da árvore via pending remove), mas é uma window de race conceitual. **Mitigação:** `bullet.py` mantém flag `self._consumed: bool = False`; o handler só processa se `not self._consumed`, depois seta `True`. Inelegante mas explícito.

- **[Risco]** Wraparound + `moveAndCollide` interagem mal: se o asteróide está prestes a sair pela direita e outro entra pela esquerda no mesmo frame, o sweep test não vê a aproximação. **Aceitação:** Asteroids canônico não tem essa interação (asteróides não colidem entre si na versão Atari'79 — só com balas e com nave). No `:games:asteroids` vamos **não** ativar colisão asteroide-asteroide; cada asteróide colide só contra ship + bullets. Simplifica o modelo.

- **[Trade-off]** Sem fricção significa que o jogador pode acumular velocidade arbitrariamente e quase nunca parar — exatamente como o Atari original. Cap em `MAX_SHIP_SPEED` para evitar tunelar via velocidade extrema; documentar como decisão consciente.

- **[Trade-off]** Spawn de asteróides longe do centro (`SAFE_SPAWN_RADIUS = 120`) pode falhar se nenhuma posição válida sair sob retries razoáveis. **Mitigação:** loop com 50 retries; em caso de falha (improvável em campo 800×600 com raio 120), fallback para coordenadas determinísticas em volta dos cantos.

## Migration Plan

Ordem de implementação:

1. Spike de runtime script attach + `add_child` de sub-árvore em Python: cria um asteróide hardcoded a partir de um `_ready` de um nó dummy. Confirma viabilidade. Se falhar, pausa e abre change separada.
2. Adiciona módulo `:games:asteroids` no `settings.gradle.kts` + `build.gradle.kts` análogo a `:games:snake`.
3. Cria `Main.kt` (cópia de `snake/Main.kt` com `"asteroids"`).
4. Cria `scene.json` esqueleto com Camera2D, GameRoot + script `game.py`, AsteroidsRoot, BulletsRoot, Ship + script `ship.py` + filhos (CollisionShape2D + CircleShape2D, Polygon2D triangular), HUD Labels.
5. Implementa `ship.py`: rotação, thrust, wraparound, fire.
6. Implementa `bullet.py`: TTL, wraparound, `_on_area_entered`.
7. Implementa `asteroid.py`: `hit()`, `_spawn_children`, wraparound, sem `moveAndCollide` (asteróides não colidem entre si — só recebem hit).
8. Implementa `game.py`: spawn inicial de 4 grandes, listener de `score_changed`, listener de `lives_changed`, restart.
9. Validação manual: jogar, atirar, quebrar asteróides, morrer, restart.
10. Atualiza `CLAUDE.md` + `ROADMAP.md`.

Rollback: nenhuma migração de schema. Remover o módulo do `settings.gradle.kts` desfaz tudo. Outros jogos não dependem de Asteroids.

## Open Questions

- **Q1**: `attach_script` em runtime para nós criados via Python está pronto? Se não, é gatilho para change separada antes (não inflar Asteroids). Tarefa de spike no início do apply resolve.
- **Q2**: `Node2D.visible: Boolean` existe na engine? Se sim, usar; se não, esconder a ship via `Polygon2D.color.a = 0`. Decisão no apply ao consultar o código.
- **Q3**: Devemos exigir asteróide-vs-asteróide collision (Asteroids Deluxe behavior)? Decisão: **não** — fora do canônico Atari'79 e expande superfície de validação sem ganho proporcional. Anotar como possível extensão futura.
- **Q4**: O cap de velocidade da nave deve ser duro (clamp linear) ou suave (drag aplicada acima do cap)? Decisão: clamp duro inicialmente; se ficar com cara ruim, evolui no apply.
