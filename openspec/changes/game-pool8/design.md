## Context

A engine `nengine` já tem fundação para um simulador de bilhar funcional: `PhysicsSystem` integra `RigidBody2D` via TOI loop com impulso bilateral (linear + angular + Coulomb), `linearDamping`/`angularDamping` aplicados a cada passo, `Area2D` como sensor puro com signals `body_entered`/`body_exited`, `Camera2D` com `bounds` + `aspectMode` e scripting Lua via `LuaScriptHost` (segundo backend, vivo via `:games:tictactoe`). Hoje a engine roda em paralelo `:games:demos/4` (30 `RigidBody2D` quicando elasticamente) e `:games:demos/6` (16 quadrados com spin), provando que o solver aguenta o caso multi-corpo elástico que sinuca exige.

Pool 8-ball como sample didático foi proposto em explore (`/opsx:explore "jogo de bilhar (sinuca)"`) com as escolhas: variante 8-ball, regras "Nível 1" (sem grupos solid/stripe), input "puxar e soltar", caçapas como `Area2D` em parede contínua, scripting Lua, HUD básica, render por bola com cor + número (e faixa branca em 9–15), mira com vetor de força + linha pontilhada projetada por ray cast script-side. Há dois jogos in-progress (`game-snake`, `game-asteroids`); este change roda em paralelo a eles, não substitui.

Stakeholder único: contribuidor (humano ou agente) que estuda arquitetura de engine. Restrição: **nenhuma API nova de engine, bundle, ou scripting deve ser exigida**. Se durante implementação aparecer necessidade, abre-se change separada antes de prosseguir.

## Goals / Non-Goals

**Goals:**

- Provar que damping calibrado em `RigidBody2D` produz comportamento "bola de sinuca" convincente (rola, desacelera, para) sem nenhum hack script-side.
- Provar que o solver elástico multi-corpo cobre o break shot (15 bolas em rack triangular contra a branca) sem perda visível de energia nem tunneling.
- Provar que uma FSM de gameplay com gating por quiescência cabe em Lua puro sobre as APIs estáveis (sem novos hooks na engine).
- Provar que `Area2D` + `body_entered` é suficiente como "sumidouro" — bola entra na caçapa, script remove da árvore viva durante traversal sem corromper o frame.
- Manter `:games:pool8` 100% paralelo a `:games:tictactoe` no eixo de stack (Skiko render + Lua scripting), reforçando o segundo backend de scripting.

**Non-Goals:**

- Regras estrita do 8-ball (grupos solid/stripe, ball-in-hand, cushion rule no break, foul calls). Tudo isso fica explícito como "Nível 2/3" pra uma change futura.
- IA adversária. 8-ball é hot-seat 2 jogadores.
- Networked / multiplayer.
- Replays, save/load.
- Sons. Engine não tem audio SPI; bilhar sem som é tolerável pra didático.
- "English" avançado (top spin / back spin / side spin) escolhido pelo jogador antes da tacada. A branca recebe só impulso linear; o spin emerge dos impactos via solver angular. Aceitável pra Nível 1.
- Calibração perfeita das constantes físicas (damping, restitution, mass). Documenta-se valores iniciais e ajusta-se empiricamente durante implementação.
- Stripe pattern fiel (faixa equatorial circular). Usar retângulo branco horizontal sobre a cor é suficiente.
- Editor visual ou inspecionável de scene.json em runtime. `scene.json` é arquivo de texto, edita-se à mão.

## Decisions

### D1. Bundle Lua puro — sem código Kotlin de gameplay

A regra do projeto já estabelecida em `:games:tictactoe` é "um único `Main.kt` que instancia o host e chama `BundleLoader`; toda lógica em scripts". Mantemos. Cinco scripts:

```
scripts/
├── table.lua    Orquestra FSM (AIMING, SIMULATING, RESOLVING, GAME_OVER),
│                contabiliza caçapadas, alterna turno, gerencia GAME_OVER.
├── ball.lua     Por-instância. Exporta number + ballColor + isCueBall.
│                Desenha círculo + número + (se 9-15) faixa branca.
├── pocket.lua   Por-instância. Escuta body_entered e re-emite signal
│                tipado pro table.
├── cue.lua      Lê input do mouse durante AIMING; computa aimVector,
│                renderiza taco/seta/projeção pontilhada; emite shot
│                signal no release.
└── status.lua   Label do HUD; escuta turn_changed e game_ended do table.
```

**Alternativa considerada**: lógica em Kotlin com `Node` subclasses (`Ball : RigidBody2D`, `Table : Node2D`). Rejeitada — `:games:tictactoe` provou que Lua puro é viável e este change é o terceiro reforço do segundo backend de scripting (após TTT e qualquer outro futuro). Quanto mais cobertura Lua, melhor o stress test do `LuaScriptHost`.

### D2. FSM no `Table.lua`, não distribuída

Estado de jogo (state, currentPlayer, pocketedThisShot, restCounter) vive todo em `table.lua` como atributos do `self` da instância. `cue.lua` e `pocket.lua` emitem signals com dados crus (`shot(impulse)`, `ball_pocketed(ballNode)`); `table.lua` é o único reduzidor.

**Alternativa**: FSM distribuída (cada nó com responsabilidade local) ou state objects. Rejeitada — pra um jogo single-screen com 1 FSM global, centralizar é mais legível e cabe num único arquivo de ~150 linhas. Nada justifica fragmentar.

### D3. Quiescência por threshold acumulado + contador de frames

Detectar "todas paradas" via:

```
sumKineticActivity = Σ |v_i| + κ · Σ |ω_i|  (i sobre RigidBody2D ativos)
if sumKineticActivity < ε:
    restCounter += 1
else:
    restCounter = 0
if restCounter >= 3:
    state ← RESOLVING
```

Threshold inicial `ε = 5.0` (soma de velocidades em unit/s), `κ = 5.0` (conversor de rad/s pra equivalente linear — ω·r aproximadamente), `restCounter ≥ 3 frames` pra evitar flicker em near-stop.

**Alternativa**: snapshot de posição entre frames (`|pos_now - pos_last| < δ` por bola). Rejeitada — mais ruidoso porque depende de dt, mais cálculo, e damping já garante decay exponencial monótono. Threshold de velocidade é o caminho natural.

**Iterado em runtime**: se "tacada light demora 4s pra ir pra RESOLVING" → reduz ε ou aumenta damping. Se "bola visível ainda movendo já transita" → aumenta ε.

### D4. Caçapa = `Area2D` em parede contínua, sem segmentação da cushion

As 4 paredes (`StaticBody2D`) formam um retângulo fechado **contínuo** (sem aberturas). Sobre os 6 cantos/laterais, posiciona-se um `Area2D` (raio ~2.2× raio da bola) — quando uma bola entra, conta como caçapada.

```
   Pocket (Area2D r=44)
       ●  ← bola entra aqui, gera body_entered
       │
   ───┴─────────── StaticBody2D cushion (continua por baixo)
```

Visualmente desenha-se o círculo preto da caçapa; a parede continua "por trás" da bola até o instante em que ela é removida da árvore.

**Alternativa**: 6 segmentos de `StaticBody2D` com aberturas reais nos cantos/laterais (opção B do explore). Rejeitada para o Nível 1 — mais realismo de "entrada física pela boca da caçapa", mas exige sweep CCD garantindo que a bola não escape sem entrar no `Area2D` (precisa que o `Area2D` cubra exatamente a abertura). Adiciona fricção de geometria sem benefício pedagógico imediato.

### D5. Remoção da bola caçapada — defer to next frame

Quando `Pocket.body_entered(ball)` dispara durante `PhysicsSystem.step`, o `Table.on_ball_pocketed(ball)` apenas marca a bola na lista `pocketedThisShot` **sem** chamar `removeChild` síncrono. A remoção (`ball:queue_free()` equivalente, isto é, mover pra `Balls` removal no `_process` do próximo frame) acontece após o step do physics atual completar — usa o protocolo já documentado em `CLAUDE.md`: `addChild`/`removeChild` em meio a traversal usa `pendingAdd`/`pendingRemove` aplicados no início do próximo step. Idioma Lua será `self.tree.root:find_child("Balls"):remove_child(ball)` no handler — engine garante o defer.

**Alternativa**: desativar a bola via flag `disabled` no `CollisionObject2D` e ocultá-la (continua na árvore, sem colidir). Mais simples, mas suja a árvore com nodes-fantasma. Rejeitada — `removeChild` deferido já é seguro pela engine.

### D6. Cue ball respawn — não-bloqueante

Quando a branca é caçapada (Nível 1: passa turno + respawn no head spot), em `RESOLVING` o `table.lua` reposiciona a branca via `self.cueBall.position = headSpot` e zera `linearVelocity` e `angularVelocity`. A bola **não** sai da árvore — só some visualmente durante a "viagem pra caçapa" (ela é caçapada e some no `RESOLVING`), e reaparece com transform reset no início do próximo `AIMING`.

Detalhe: para "sumir" antes de respawn, o `pocket.lua` faz mesma coisa que com qualquer outra bola (apenda em `pocketedThisShot`), e o `RESOLVING` distingue: se for a branca, respawn; senão, `remove_child`.

**Alternativa**: ball-in-hand (jogador reposiciona com o mouse antes da próxima tacada). Rejeitada — Nível 1 não tem isso. Head spot fixo é determinístico.

### D7. Mira: ray cast Lua puro contra bolas + cushions

A "linha pontilhada projetando a trajetória inicial" é calculada em `cue.lua._draw` enquanto state == AIMING:

```
origin    = cueBall.position
direction = -aimVector.normalized()
tMin = +∞
for each ball in Balls (except cueBall):
    t = ray_vs_circle(origin, direction, ball.position, r=20)
    if 0 < t < tMin: tMin = t
for each cushion (4 rects):
    t = ray_vs_aabb_segment(origin, direction, cushion.rect)
    if 0 < t < tMin: tMin = t
draw_dashed(origin, origin + direction * tMin)
```

`ray_vs_circle`: clássico (quadrática). `ray_vs_aabb`: slab method. 15 + 4 = 19 testes por frame em AIMING, ~negligível.

**Alternativa**: usar o sweep API da engine (`sweepCircleVsCircle`). Rejeitada — não exposto a Lua, e exporia-se algo só pra isso. Math em Lua é trivial e mantém engine intocada.

### D8. Render por bola via `_draw` Lua, não Circle2D filho

Cada `RigidBody2D` que representa uma bola tem `script = scripts/ball.lua` e exports `number: int`, `ballColor: Color`, `isCueBall: bool`. O `_draw` desenha:

```lua
function self._draw(self, renderer)
    local r = 20
    renderer:circle(nengine.Vec2(0,0), r, fill=self.ballColor)
    if self.number >= 9 and self.number <= 15 then
        renderer:rect(nengine.Rect(-r, -r*0.35, 2*r, r*0.7), fill=Color.WHITE)
    end
    if not self.isCueBall then
        renderer:text(tostring(self.number), nengine.Vec2(0,0), color=Color.BLACK)
    end
end
```

Tudo em local space (engine pusha a transform local antes do `_draw`). O texto gira com a bola — aceito, é o que sinuca real faz.

**Alternativa**: `Circle2D` filho + `Label` filho. Adiciona dois nodes por bola (× 16 = 32 nodes extras), aumenta latência de leitura do JSON, e divide a representação visual entre 3 nodes que precisam concordar no raio/cor. Rejeitada — `_draw` único centraliza.

### D9. `scene.json` estático com 16 bolas pré-posicionadas

O rack triangular é determinístico e cabe inteiro em 16 entradas hardcoded no JSON. Não há geração runtime de posições.

```
rack apex em footSpot = (1500, 500)
spacing = 2r + 1 = 41
for row in 0..4:
    for col in 0..row:
        x = footSpot.x + row * 41 * sqrt(3)/2  ≈ row * 35.5
        y = footSpot.y + (col - row * 0.5) * 41
```

Posição da 8: centro do rack (linha 3, col 1 do diagrama 5-fileiras), com as outras 14 bolas distribuídas em ordem fixa ao redor.

**Alternativa**: gerar o rack em `Table._ready` via Lua, instanciando `RigidBody2D` em runtime. Rejeitada — `scene.json` estático é a fonte de verdade canônica do projeto; gerar runtime quebra o invariante de inspectability/portability do bundle.

### D10. Linguagem do bundle: Lua via `LuaScriptHost`

Já discutido em explore. Lua reforça o segundo backend de scripting (após `:games:tictactoe`). Stubs LuaCATS de `:engine-bundle-lua/src/main/resources/stubs/` cobrem `nengine.Vec2`, `nengine.Color`, `Node2D`, `Signal`, etc.

### D11. Calibração inicial das constantes

Valores de partida (a ajustar empiricamente em runtime):

| parâmetro | valor inicial | racional |
|---|---|---|
| `ball.radius` | 20 | mesa 2000×1000, ~50 raios de comprimento |
| `ball.mass` | 1.0 | uniforme |
| `ball.restitution` | 0.95 | bola-bola elástica realista |
| `ball.friction` | 0.15 | Coulomb tangencial discreto |
| `ball.linearDamping` | 0.45 | tacada média atravessa ~3 mesas |
| `ball.angularDamping` | 0.50 | spin morre um pouco mais rápido |
| `cushion.restitution` | 0.85 | cushion absorve mais |
| `cushion.friction` | 0.10 | |
| `maxDragPixels` | 400 | clamp visual do arrasto |
| `minPower` | 20 | abaixo cancela |
| `forceK` | 3.0 | drag 400 → impulso 1200 unit/s |
| `restThreshold ε` | 5.0 (Σ\|v\|+κΣ\|ω\|) | |
| `κ (angular weight)` | 5.0 | converte rad/s pra equivalente linear |
| `restFrames` | 3 | evita flicker |

## Risks / Trade-offs

- **Sweep CCD entre bolas em alta velocidade** → bola pode passar dentro de outra entre frames? **Mitigação**: `PhysicsSystem` já tem TOI loop com sweep circle-vs-circle exato. Demo 4 prova com 30 bolas a alta velocidade. Risco mitigado por validação visual durante implementação.

- **Damping inicial mal calibrado** → tacada light tarda demais ou tacada forte para rápido demais. **Mitigação**: calibração empírica iterativa. Documentar o valor final em `pool8/scripts/table.lua` como `-- calibrated against [scenario]`. Não bloqueia o change.

- **Ray cast Lua chamado 60 vezes/s durante AIMING** → 19 testes × 60Hz = 1140 testes/s em LuaJ. **Mitigação**: trivial pra LuaJ; mas se aparecer hitching, cap em 30Hz (atualizar projeção em frames ímpares).

- **Bola encaçapada gerando múltiplos `body_entered`** → `Area2D` pode disparar várias vezes pra uma bola que entrou? **Mitigação**: contrato documentado em `CLAUDE.md` diz "exatamente uma vez por par-transição". Mas se a bola sair e voltar (movimento residual), pode disparar de novo antes da remoção. Solução: `pocket.lua` mantém um `Set` de balls já notificadas e ignora repetições; ou `table.lua` ignora pocketed events durante `RESOLVING`.

- **Mouse fora da área da janela durante drag** → libera mouse sem `mouseUp` recebido → state preso em AIMING_ACTIVE. **Mitigação**: tratar `tree.input.wasMouseReleased` mesmo fora da área (verificar se a engine entrega esse evento; se não, tratar perda de foco como cancel implícito).

- **Bola escapando entre cushion e caçapa em ângulos rasos** → como caçapa é `Area2D` por cima de parede contínua, bola não pode escapar; pior caso é não detectar caçapada e a bola continuar quicando. **Mitigação**: dimensionar `Area2D.radius ≥ 2.2 × ball.radius` garante que mesmo em ângulo raso o centro da bola entra na área antes de bater na cushion.

- **`removeChild` síncrono durante `body_entered`** → corrompe traversal? **Mitigação**: já documentado em `CLAUDE.md` que engine usa `pendingAdd`/`pendingRemove` aplicados no início do próximo step. `removeChild` em handler é seguro.

- **Cue ball respawn em head spot ocupada por outra bola** → overlap inicial, físicas estranhas. **Mitigação**: em `RESOLVING`, antes de respawn, varrer bolas próximas ao head spot; se ocupado, deslocar uma fração do raio. (Pra Nível 1, raro o suficiente pra aceitar overlap transiente; pode-se ignorar até virar problema.)

## Open Questions

- **Mouse hit-test pra iniciar AIMING**: clique em qualquer lugar inicia drag, ou exige clique dentro de uma região do cueBall? Explore deixou "qualquer lugar"; design adota isso, mas pode mudar se sentir mal-intuitive em runtime.
- **Visual de "GAME_OVER"**: só Label "Player N wins!", ou também freeze do mundo + algo mais? Default: freeze + label.
- **Restart pós-vitória**: clique reinicia (igual TTT), ou exige tecla? Default: clique esquerdo em qualquer lugar reinicia (consistente com TTT).
