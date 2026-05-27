## 1. Module scaffolding

- [ ] 1.1 Criar diretório `games/pool8/` com `build.gradle.kts` (Kotlin JVM application; mainClass = `com.neoutils.pool8.MainKt`); dependências: `projects.engine`, `projects.engineSkiko`, `projects.engineBundle`, `projects.engineBundleLua`. Sem `engineCompose`, sem `engineBundlePython`.
- [ ] 1.2 Adicionar `include(":games:pool8")` em `settings.gradle.kts`.
- [ ] 1.3 Criar `games/pool8/src/main/kotlin/com/neoutils/pool8/Main.kt` com `main()` thin: instancia `LuaScriptHost.create()`, chama `BundleLoader.fromResources("pool8", scripting = lua)`, envolve em `SceneTree(root = ...)`, entrega a `SkikoHost().run(tree, GameConfig(...))` com window title e size sensatos.
- [ ] 1.4 Rodar `./gradlew :games:pool8:run` e validar que abre uma janela vazia (bundle ainda não existe — esperado falhar com mensagem clara do BundleLoader; serve para confirmar wiring).

## 2. Bundle scaffold e scene.json mínimo (smoke)

- [ ] 2.1 Criar diretório `games/pool8/src/main/resources/pool8/` com `scene.json` mínimo: root `Node2D` (sem script ainda), `Camera2D` (`bounds = 0,0,2000,1000`, `current = true`), e um `ColorRect` 2000×1000 verde (cloth).
- [ ] 2.2 Rodar e validar: janela 2000×1000 escala via FIT, mostra retângulo verde, F1/F2 funcionam mesmo sem nada pra exibir.

## 3. Geometria estática: cushions e pockets

- [ ] 3.1 Adicionar 4 `StaticBody2D` filhos sob um `Node2D` "Cushions": topo, base, esquerda, direita; cada um com `RectangleShape2D` filho dimensionado para formar um anel fechado ao redor de 2000×1000 (espessura visual ≈ 30, mas posicionados de modo que a face interna delimite exatamente o playable area). Setar `restitution = 0.85`, `friction = 0.10` em `properties`.
- [ ] 3.2 Adicionar 6 `Area2D` filhos sob um `Node2D` "Pockets": 4 cantos + 2 mid-rails laterais. Cada um com `CircleShape2D` filho de `radius = 44`.
- [ ] 3.3 Rodar com F2 ligado e validar visualmente que cushions e pockets estão posicionados onde a expectativa visual diz (esquinas e meios das laterais longas).

## 4. Ball script e instâncias estáticas

- [ ] 4.1 Criar `games/pool8/src/main/resources/pool8/scripts/ball.lua` com `extends = "RigidBody2D"`, exports `number: int`, `ballColor: Color`, `isCueBall: bool`, e `_draw` desenhando: círculo cheio `r=20` na cor; se `9 ≤ number ≤ 15`, retângulo branco horizontal centrado (`width=2r`, `height=0.7r`); se `not isCueBall`, numeral preto centralizado. Para `Ball8`, número rendered em branco para contraste.
- [ ] 4.2 Adicionar 16 `RigidBody2D` filhos sob um `Node2D` "Balls" no `scene.json`: `CueBall` (number=0, ballColor=white, isCueBall=true) em (500, 500) com `CircleShape2D r=20`, e `Ball1..Ball15` em posições do rack triangular (apex em (1500, 500), spacing = 41, 8 no centro da 3ª fileira). Cores canônicas em `ballColor` por número.
- [ ] 4.3 Setar em todas as bolas (`properties`): `mass = 1.0`, `restitution = 0.95`, `friction = 0.15`, `linearDamping = 0.45`, `angularDamping = 0.50`.
- [ ] 4.4 Rodar e validar: as 16 bolas aparecem nas posições corretas, com cores e números, sem overlap, sem se mover (todas paradas).

## 5. Pocket script (sink behavior)

- [ ] 5.1 Criar `scripts/pocket.lua` com `extends = "Area2D"`. No `_ready`, conectar `self.body_entered` a um handler interno que: (a) verifica se a ball já foi notificada neste shot (set local), (b) re-emite um signal `ball_pocketed(ball)` (declarado em `signals`).
- [ ] 5.2 No mesmo script, declarar método `reset_shot()` que limpa o set de notificadas; será chamado pelo `table.lua` no início de cada `SIMULATING`.

## 6. Table script — esqueleto e signals

- [ ] 6.1 Criar `scripts/table.lua` com `extends = "Node2D"` e armazenar refs cacheadas em `_ready`: `self._cueBall`, `self._balls` (array de Ball1..15), `self._pockets` (array dos 6 Area2D), `self._cueStick`, `self._status`.
- [ ] 6.2 Declarar `signals = { turn_changed = "int", game_ended = "int", aim_enabled = "bool" }`.
- [ ] 6.3 Conectar `ball_pocketed` de cada pocket a `on_ball_pocketed(ball)`; o handler apena ball/flag em `pocketedThisShot`, distinguindo cue/8/outras.
- [ ] 6.4 Inicializar estado: `self._state = "AIMING"`, `self._currentPlayer = 1`, emitir `turn_changed(1)` e `aim_enabled(true)`.

## 7. Cue stick script — input e mira

- [ ] 7.1 Criar `scripts/cue.lua` com `extends = "Node2D"` e exports `maxDragPixels: float = 400`, `minPower: float = 20`, `forceK: float = 3.0`. Declarar `signals = { shot = "Vec2" }`.
- [ ] 7.2 Em `_ready`, ouvir `aim_enabled` do `table.lua` (parent) e armazenar `self._aimEnabled = true/false`.
- [ ] 7.3 Em `_process`, se `self._aimEnabled` e mouse pressed → registra `self._aiming = true`; se aiming e released → emite `shot(impulse)` ou cancela.
- [ ] 7.4 Em `_draw`, se `self._aiming`: desenhar (a) linha cue ball → mouse position (taco), (b) seta tracejada atrás opposite, (c) projeção pontilhada via ray cast: ray-vs-circle contra cada Ball (raio 20) + ray-vs-aabb contra cada cushion; menor t positivo define o endpoint.
- [ ] 7.5 Em `cue.lua`, implementar utilitários locais `ray_vs_circle(origin, dir, center, radius)` e `ray_vs_aabb(origin, dir, rect)` retornando t ou nil.

## 8. FSM e quiescência no table.lua

- [ ] 8.1 Em `table.lua`, ouvir o signal `shot(impulse)` do `CueStick` (filho) e: aplicar `cueBall:apply_impulse(impulse)`, transitar para `SIMULATING`, emitir `aim_enabled(false)`, resetar `pocketedThisShot` e flags, chamar `pocket:reset_shot()` em cada pocket, zerar `restCounter`.
- [ ] 8.2 Em `_physics_process`, se state == `SIMULATING`: somar `Σ|v| + 5 * Σ|ω|` em todas as bolas vivas; se `< 5.0`, `restCounter += 1`; senão `restCounter = 0`. Quando `restCounter >= 3`, transitar para `RESOLVING`.

## 9. RESOLVING — regras Nível 1

- [ ] 9.1 No início de `RESOLVING`, varrer `pocketedThisShot` e separar: cuePocketed, eightPocketed, outras.
- [ ] 9.2 Remover do tree (`Balls:remove_child(b)`) cada bola em "outras". A engine usa `pendingRemove` deferido — seguro chamar dentro do handler.
- [ ] 9.3 Se `eightPocketed`: state = `GAME_OVER`, emitir `game_ended(currentPlayer)`. Não emitir `turn_changed`.
- [ ] 9.4 Senão se `cuePocketed`: reposicionar `CueBall.position = Vec2(500, 500)`, zerar `linearVelocity` e `angularVelocity`. `currentPlayer = 3 - currentPlayer`. Voltar para `AIMING` + `turn_changed(currentPlayer)` + `aim_enabled(true)`.
- [ ] 9.5 Senão se "outras" não-vazias: manter `currentPlayer`. Voltar para `AIMING` + `turn_changed(currentPlayer)` + `aim_enabled(true)`.
- [ ] 9.6 Senão: `currentPlayer = 3 - currentPlayer`. Voltar para `AIMING` + `turn_changed(currentPlayer)` + `aim_enabled(true)`.
- [ ] 9.7 Sempre: limpar `pocketedThisShot` e flags.

## 10. HUD: status label

- [ ] 10.1 Criar `scripts/status.lua` com `extends = "Label"`. Em `_ready`, achar `self.tree.root` (Table), conectar a `turn_changed` setando `self.text = "Player N's turn"`, e a `game_ended` setando `self.text = "Player N wins! Click to restart."`.
- [ ] 10.2 Adicionar `Label` filho do root no `scene.json` chamado `Status`, com `script = "scripts/status.lua"`, posicionado em (1000, 50) com font sensata.

## 11. Click-to-restart

- [ ] 11.1 Em `table.lua._process`, se state == `GAME_OVER` e mouse left clicked: chamar método interno `restart()`.
- [ ] 11.2 `restart()` repõe todas as bolas (cue em (500,500), Ball1..15 nas posições originais armazenadas em `_initialPositions` durante `_ready`), zera velocidades, re-adiciona via `addChild` quaisquer bolas removidas (manter referências em `_balls` para reuse), reseta state para `AIMING`, currentPlayer=1, emite `turn_changed(1)` + `aim_enabled(true)`.
- [ ] 11.3 Garantir que o clique de restart NÃO é processado pelo `CueStick` como início de novo aim (cue_stick respeita `aim_enabled` que ainda está false até `restart()` re-emitir true — verificar ordem de emissão).

## 12. Calibração e validação manual

- [ ] 12.1 Rodar `./gradlew :games:pool8:run` e disparar uma tacada média na 8 no rack. Validar: 15 bolas se espalham, restitution alta produz transferência de energia visível, damping leva todas a pararem em alguns segundos.
- [ ] 12.2 Se "demora demais pra parar" → aumentar `linearDamping` (ex.: 0.6). Se "para rápido demais" → reduzir (ex.: 0.3). Atualizar `scene.json` com os valores tunados.
- [ ] 12.3 Validar caçapadas: tacada direta na lateral, bola entra na caçapa, some da tela. F2 mostra que o `Area2D` do pocket cobre a região esperada.
- [ ] 12.4 Validar branca encaçapada: tacada que joga branca na caçapa direto. Branca volta no head spot, turno passa.
- [ ] 12.5 Validar 8 encaçapada: força tacada que mete 8 numa caçapa. Game over com winner = player atual. Mensagem aparece.
- [ ] 12.6 Validar restart: click pós game-over. Bolas voltam ao rack, jogo recomeça.
- [ ] 12.7 Validar mira: durante AIMING, drag mostra taco + projeção pontilhada terminando na primeira bola/cushion. Drag tiny não dispara tacada.

## 13. Documentação

- [ ] 13.1 Em `CLAUDE.md`, na seção "Module Structure & How to Run", adicionar entrada de `:games:pool8` no bloco de módulos com descrição one-liner.
- [ ] 13.2 Em `CLAUDE.md`, adicionar uma seção "Para rodar Pool 8-ball" análoga às outras (com command `./gradlew :games:pool8:run`, descrição, controles: mouse drag-and-release, F1, F2).
- [ ] 13.3 Em `ROADMAP.md`, marcar o jogo como entregue (entrada nova ou linha em backlog → done conforme convenção do arquivo).

## 14. Validações finais

- [ ] 14.1 `openspec validate game-pool8 --strict` passa.
- [ ] 14.2 Rodar `/opsx:verify game-pool8` (ou inspecionar manualmente) e confirmar que cada requirement da spec tem evidência observável em código ou comportamento de runtime.
- [ ] 14.3 Confirmar que `git diff` mostra mudanças apenas em `games/pool8/`, `settings.gradle.kts`, `CLAUDE.md`, `ROADMAP.md`, e nada em `engine/`, `engine-bundle/`, `engine-bundle-lua/`, `engine-skiko/`. Se houver mudança nesses, abrir change separada e tirar do escopo deste.
