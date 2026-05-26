## Context

A engine tem três tipos de body conceituais hoje (`StaticBody2D`, `CharacterBody2D`, `Area2D`) e sabe fazer **detecção** de colisão com TOI swept correto, incluindo OBBs rotacionados (changes archived `kinematic-move-and-collide`, `kinematic-rotated-sweep`). O que falta é **resposta** com semântica física real — massa, inércia, restituição, fricção — e o conceito de body **integrado pela engine** (não pelo script).

Hoje a única demo com resposta angular correta é `TumblingSwarmDemo`, que carrega ~200 linhas de matemática de impulso inline, com um truque de `identityHashCode` pra deduplicar contatos (porque `moveAndCollide` é chamado pelos dois lados do par). `CollisionStressDemo` faz `vel.reflect(normal)` puro — equivalente a "ambas as massas são infinitas e iguais" — e perde transferência de momento entre bolas. Não há diagnóstico de conservação.

Esta change introduz `RigidBody2D` como o terceiro modo de `PhysicsBody2D` — onde a **engine** é dona de `position`, `linearVelocity`, `angularVelocity`, e o script controla via `applyForce`/`applyImpulse` e leitura/escrita de velocidades. O contrato vira **Godot-canônico**.

## Goals / Non-Goals

**Goals:**

- Introduzir `RigidBody2D` com solver de impulso (linear + angular) que conserve momento por design.
- Restituição default = 0 (inelástico, Godot/Unity-like) — colisões "param" relativamente na normal por default; bouncing requer `restitution > 0` explícito.
- Fricção de Coulomb no contato (tangencial), capada em `μ·|jn|`, ativa por default (sliding ↔ rolling).
- Gravidade global por-`PhysicsSystem`, escalada por-body via `gravityScale`.
- Diagnósticos didáticos de conservação: `Σp` linear, `ΣL` angular, `ΣKE`, com overlay de drift em `F3`.
- `SweepResult.point` passa a ser o ponto geométrico real de contato (canto/face/closest-point), substituindo o fallback "OBB center".
- Migrar `CollisionStressDemo` e `TumblingSwarmDemo` pro novo path — demos perdem matemática inline.
- Manter `CharacterBody2D` + `moveAndCollide` intactos. Pong, Tic, Snake, Asteroids, Hello-World inalterados.
- Bindings Python: `RigidBody2D` no `Context`, stubs `.pyi`, properties/métodos.

**Non-Goals:**

- `PhysicsMaterial2D` (recurso compartilhado de bounce/friction). Props ficam por-body nesta change; material vira change futura.
- Sub-stepping interno do solver (rodar N passes com dt/N). Single-pass com TOI iterativo cobre bouncing CCD; sub-step só importa pra pilhas/contatos persistentes, sem demo pedindo hoje.
- Joints, constraints, springs, motors.
- Sleep islands (corpos quase parados deixando de ser integrados pra economizar CPU). Performance prematura.
- `Area2D` aplicando gravidade local (override). Vira change separada se um jogo pedir.
- Multi-body solver Gauss-Seidel iterativo pra resolver redes de contato simultâneos. Resolvemos par-a-par no TOI loop; estável pra bouncing, não-ideal pra empilhamento profundo (não é caso de uso aqui).

## Decisions

### D1. Tipo separado `RigidBody2D` ao lado de `CharacterBody2D`, não substituição

`CharacterBody2D` continua sendo o caminho pra controle manual (paddles, snake head, player). `RigidBody2D` é o novo caminho pra simulação dinâmica. Os dois coexistem no mesmo tree e podem colidir entre si.

**Resposta cruzada:**

- `RigidBody2D` ↔ `RigidBody2D`: solver bilateral aplica impulso recíproco; ambos mudam `linearVelocity` e `angularVelocity`.
- `RigidBody2D` ↔ `StaticBody2D`: solver trata Static como `m = ∞`, `I = ∞`; só Rigid recebe impulso.
- `RigidBody2D` ↔ `CharacterBody2D`: solver trata Character como `m = ∞`, `I = ∞` **pela perspectiva do Rigid** (Rigid recebe impulso; Character não). Isso preserva o controle do script sobre o Character e dá feel "paddle empurra bola, paddle não recua". Documentado explicitamente — em Godot é configurável (`linear_velocity` do CharacterBody como influência), aqui simplificamos.
- `CharacterBody2D.moveAndCollide` invocado contra `RigidBody2D`: o sweep do Character ainda enxerga o Rigid; o Character para no TOI normalmente; o Rigid **não** recebe impulso direto desse sweep (a resposta vai pela rota do solver no próximo passo, quando o Rigid já estiver sobreposto). Pra "kick", o script pode chamar `rigid.applyImpulse(...)` manualmente dentro de `_on_body_entered`.

**Alternativa rejeitada:** unificar tudo em um `PhysicsBody2D` com flag `mode = STATIC | KINEMATIC | DYNAMIC`. Mais Godot-canon (lá é assim), mas perde clareza de tipo Kotlin (`when (body)` deixa de ser exaustivo) e mistura responsabilidades. Mantemos a herança.

### D2. Ordem dentro do physics tick: script primeiro, engine depois

```
SceneTree.physicsStep(dt):
  1. tree.beginPhysicsPhase()
  2. para cada Node v: v.onPhysicsProcess(dt)
        ← scripts leem v.linearVelocity (estado pós-solver do frame anterior)
          e acumulam forças/impulsos via applyForce / applyImpulse / set linearVelocity
  3. PhysicsSystem.integrate(dt)
        ← para cada RigidBody2D r:
              r.linearVelocity += (gravity * r.gravityScale + r.appliedForce / r.mass) * dt
              r.angularVelocity += r.appliedTorque / r.inertia * dt
              r.linearVelocity *= (1 - r.linearDamping * dt).coerceAtLeast(0f)
              r.angularVelocity *= (1 - r.angularDamping * dt).coerceAtLeast(0f)
              r.clearAccumulators()
  4. PhysicsSystem.advanceAndResolve(dt)
        ← para cada RigidBody2D r (ordem deterministic: pre-order do tree-walk):
              motion = r.linearVelocity * dt
              repeat até R=4:
                sweep r contra todos os PhysicsBody2D no mesmo parent frame
                se contato em t ∈ [0, 1]:
                  r.position += motion * t + depenetration
                  resolveImpulse(r, other, normal, contactPoint, e_combined, μ_combined)
                  motion = motion * (1 - t)  com a nova velocity refletida implicit pelo impulso
                  se |motion| < ε: break
                senão:
                  r.position += motion; break
        ← para cada RigidBody2D r: r.transform.rotation += r.angularVelocity * dt
  5. PhysicsSystem.dispatchSignals()
        ← mesmo computeOverlapping + enter/exit do step atual
  6. tree.endPhysicsPhase()
```

**Por que script-primeiro:** intuitivo (script descreve intenção, engine consuma), e match Godot. Scripts que precisam **reagir** a estado pós-solver podem ler `linearVelocity` no `onProcess` (frame-step, depois do physics-step) ou no `_on_body_entered` (dispatch é depois do solver).

**Alternativa rejeitada:** engine-primeiro com `_integrate_forces(state)` no estilo Godot RigidBody. Mais poderoso (script pode interceptar o estado mid-step) mas API mais complexa pra didática. Adiamos.

### D3. Resolução por par, uma vez — solver é dono do laço

Hoje `moveAndCollide` é chamado pelos dois lados do par no `_physics_process` do script. Com `RigidBody2D`, o **solver** itera sobre rigid bodies e resolve cada par naturalmente uma vez (o impulso recíproco mexe nos dois bodies no mesmo passo). Truque de `identityHashCode` no demo morre.

Ordem de iteração é determinística: pre-order do tree-walk a partir da raiz. Determinismo importa pra reprodutibilidade (mesmo seed → mesma trajetória) e pra testes de regressão.

### D4. Equação de impulso unificada (linear + angular)

Para um contato no ponto `P` com normal `n`, lever arms `rA = P - centroA`, `rB = P - centroB`, velocidades nos pontos de contato `vAP = vA + ω_A × rA` e `vBP = vB + ω_B × rB`:

```
v_rel = vAP - vBP
vRelN = v_rel · n
se vRelN >= 0: já separando → return (early-out, não aplica impulso)

denom_N = 1/mA + 1/mB + (rA × n)² / IA + (rB × n)² / IB
jn = -(1 + e) · vRelN / denom_N

vA += (jn / mA) · n
vB -= (jn / mB) · n
ω_A += (rA × n) · jn / IA
ω_B -= (rB × n) · jn / IB
```

Para Static / Character na perspectiva do Rigid, usamos `1/m_other = 0` e `1/I_other = 0` (massa/inércia infinitas) → todo o impulso vai no Rigid. Mesma fórmula, sem branch.

**Fricção tangencial (Coulomb):**

```
v_tang = v_rel - vRelN · n        (componente tangencial)
|v_tang| < FRICTION_EPS → skip (corpo já não desliza)
t = v_tang / |v_tang|             (unit vetor tangente, na direção do slide)
denom_T = 1/mA + 1/mB + (rA × t)² / IA + (rB × t)² / IB
jt_brake = |v_tang| / denom_T     (impulso que zeraria a velocidade tangencial)
jt = min(jt_brake, μ · jn)        (cap de Coulomb)
aplica -jt · t da mesma forma que jn · n
```

**Combine rules:**

- `e = max(e_A, e_B)` (Godot, "qualquer um dos dois resta o bounce, escolhe o maior").
- `μ = sqrt(μ_A · μ_B)` (Box2D — geometric mean preserva 0 quando qualquer um é 0).

**Alternativa rejeitada:** Box2D-style split velocity+position iterations (solve velocity constraints até convergir, depois solve position constraints). Mais estável pra empilhamento, mas overkill pra bouncing. Single-pass por par é didaticamente mais claro.

### D5. `SweepResult.point` vira ponto geométrico real

Hoje `point` na maioria dos pares devolve o **centro do OBB do shape sweep** (suficiente pra `KinematicCollision2D` decorativo, insuficiente pra `r × n` correto). Esta change refina por par:

- **circle-vs-circle**: `point = centro_A + n · radius_A` (ponto na superfície de A na direção da normal). Trivial.
- **circle-vs-rect (rect axis-aligned)**: closest-point do centro do circle sobre a AABB do rect (clamp das coordenadas locais a `[-half, +half]`), rotacionado de volta se rect tem rotação.
- **rect-vs-rect axis-aligned**: face média do par sobreposto após o sweep. Pra TOI > 0 (não-starting-overlap), é a face da rect-stationary que o leading vertex do rect-móvel toca. Pra starting-overlap, é o midpoint do SAT minimum-translation segment.
- **rotated-rect-vs-rotated-rect (SAT)**: vertex-mais-penetrado do par na direção `-n` (igual ao `leadingOffset` que TumblingSwarmDemo inventou, agora promovido pra `Shape2D.sweepOverlap`). Tie-break por epsilon → midpoint (face-vs-face produz `r × n = 0` corretamente).

Mudança é local a `Shape2D.kt`. Testes existentes que checavam `point` por valor exato precisam ser revisados — viraram especificações de geometria de contato. O contrato do `KinematicCollision2D` no `kinematic-move-and-collide` spec não muda (o campo já existe), só o significado fica mais forte.

### D6. Auto-derivação de inércia

`inertia: Float = 0f` na declaração; `0f` é sentinela "auto-derive". A derivação acontece preguiçosamente na primeira leitura de `effectiveInertia()` (cacheada `@Transient`), e é invalidada quando shapes são adicionados/removidos.

```
para cada CollisionShape2D filho ativo s do body:
  I_local = quando s.shape é:
    CircleShape2D    → mass * r² / 2
    RectangleShape2D → mass * (w² + h²) / 12
  I_offset = mass * |s.transform.position|²   (paralelo dos eixos)
  total += I_local + I_offset
```

Se o autor sobrescreve `inertia` com valor != 0, esse valor é usado verbatim — escape pra ajuste de design.

**Alternativa rejeitada:** auto-derivar no `onEnter` e travar. Menos flexível; troca de shape em runtime fica inconsistente. Lazy + invalidação ganha.

### D7. Gravidade

`PhysicsSystem.gravity: Vec2` com setter público; default `Vec2.ZERO`. Não há `engine.config` ainda — vive como property do system. Each body multiplica por `gravityScale: Float = 1f`. Setando `gravityScale = 0f` num body o faz ignorar gravidade global ("flutua").

Demos podem ligar gravidade no `onEnter` do root: `tree.physicsSystem.gravity = Vec2(0f, 980f)`. Pra esta change as duas demos migradas mantêm `gravity = Vec2.ZERO` (bolinhas/quadrados continuam em movimento livre sem queda — é o cenário que demonstra conservação melhor).

### D8. Diagnósticos de conservação

`SceneTree` (ou módulo separado `Diagnostics`) expõe:

```kotlin
fun SceneTree.totalLinearMomentum(): Vec2 =
    sum(forEach RigidBody2D r in tree) { r.mass * r.linearVelocity }

fun SceneTree.totalAngularMomentum(): Float =
    sum(forEach RigidBody2D r in tree) {
        r.inertia * r.angularVelocity +
        r.mass * (r.position.x * r.linearVelocity.y - r.position.y * r.linearVelocity.x)
    }

fun SceneTree.totalKineticEnergy(): Float =
    sum(forEach RigidBody2D r in tree) {
        0.5f * r.mass * r.linearVelocity.lengthSquared() +
        0.5f * r.inertia * r.angularVelocity * r.angularVelocity
    }
```

**Overlay `F3`** (`GameConfig.toggleMomentumOverlayKey`): canto inferior esquerdo, 3 linhas + 3 sparklines (60 últimas amostras):

```
Σp = ( 123.4, -45.6 ) ▁▂▃▃▃▂▁▂▃▄▃▂▁  conservado
ΣL = 12.78           ▁▂▃▃▃▂▁▂▃▄▃▂▁  conservado
ΣKE = 8459.2 J        █▇▅▄▃▃▃▃▃▃▃▃▃  decrescente (inelástico)
```

Live no `GameHost` ao lado do FPS overlay. Demos podem opcionalmente sobrepor info própria.

### D9. Mid-frame `position =` setter em RigidBody2D

Permite (teleporte). Implementação: o setter custom de `Node2D.transform` já invalida cache de world transform e descendentes — comportamento livre. O override em `RigidBody2D` adiciona um log `Log.w(TAG, "...")` na primeira vez por body, e seta uma flag `@Transient warnedAboutTeleport = true` pra suprimir as próximas. Sem rate-limiting por frame — uma vez por instância pela vida do body. Documentado que a engine não tenta resolver overlaps causados por teleporte; o solver do próximo tick lidará via depenetration.

### D10. Bindings Python

Adicionados ao `Context`:

```python
# extends RigidBody2D

mass: float = 2.0
restitution: float = 0.5
friction: float = 0.4
gravity_scale: float = 1.0
linear_damping: float = 0.0
angular_damping: float = 0.0

def _physics_process(self, dt):
    self.apply_central_impulse(Vec2(100, 0))
    # self.linear_velocity é writable e readable
    if self.linear_velocity.x > MAX_SPEED:
        self.linear_velocity = Vec2(MAX_SPEED, self.linear_velocity.y)
```

Métodos expostos: `apply_force`, `apply_impulse`, `apply_central_force`, `apply_central_impulse`, `apply_force_at` (com world point), `apply_impulse_at`, `apply_torque`. Properties: `linear_velocity`, `angular_velocity` (rw); `mass`, `inertia` (rw via export; rw nativo via setter).

Stubs `.pyi` atualizados em `engine-bundle-python/src/main/resources/stubs/engine/__init__.pyi`.

### D11. Migração das demos

`CollisionStressDemo` (4):
- `Ball : CharacterBody2D` → `Ball : RigidBody2D`.
- Remove `vx`/`vy` fields → usa `linearVelocity` (engine-owned).
- Remove `onPhysicsProcess` com `moveAndCollide`+reflect → vazio (engine integra).
- Setup: `restitution = 1f`, `friction = 0f` (elástico + sem fricção = comportamento de hoje).
- Mantém `flashTimer` em `onProcess` (cosmético; agora flash dispara via signal `body_entered` em vez de retorno de `moveAndCollide`).

`TumblingSwarmDemo` (6):
- `TumblingSquare : CharacterBody2D` → `TumblingSquare : RigidBody2D`.
- Remove `vx`/`vy`/`angularVel` fields → `linearVelocity`, `angularVelocity`.
- Remove `onPhysicsProcess` inteiro (era ~10 linhas de chamada + ~60 de fórmulas suporte).
- Remove `resolveSquareWall`, `resolveSquareSquare`, `leadingOffset` (~120 linhas) — toda matemática move pro solver.
- Setup: `restitution = 1f`, `friction = 0.4f` (mantém o feel de Coulomb das walls).
- Pair-hit sem fricção que TumblingSwarm hoje usa (`MU=0` entre quadrados, `MU=0.4` contra walls) **se perde** — solver usa um `μ` por par via combine rule. Trade-off aceito: o pair `μ = sqrt(0.4 · 0.4) = 0.4` é diferente de "0" mas o comportamento visual continua expressivo (squares ainda spinam em hits glancing). Documentado no design.

## Risks / Trade-offs

- **[Risk]** Solver single-pass não converge pra empilhamento profundo (pilha de 10 caixas em gravity). → Mitigação: não temos demo de pilha; design.md documenta limitação. Se aparecer caso de uso, sub-stepping vira change separada.
- **[Risk]** Mudança em `SweepResult.point` quebra usos diretos do campo. → Mitigação: grep prévio mostra que só `KinematicCollision2D` (que repassa pro script) e debug overlay consomem. Demos migradas não usam mais o campo. Spec update no `kinematic-move-and-collide` documenta o novo contrato.
- **[Risk]** `μ` pair entre squares no Tumbling difere do hardcoded 0.0 atual → comportamento visual sutilmente diferente. → Mitigação: ajustar `friction` per-square pra recuperar o feel se necessário; tunagem de demo, não de engine.
- **[Risk]** RigidBody2D ↔ CharacterBody2D com Character como "m=∞" pode surpreender — colocar um Rigid no caminho de um Character paddle e esperar que o paddle desacelere. → Mitigação: documentar no KDoc do `RigidBody2D` e no CLAUDE.md. Match Godot kinematic semantics.
- **[Risk]** Combine rule `e = max(e_A, e_B)` faz qualquer body bouncy contaminar pares. Bola `e=0.9` vs parede `e=0` resulta `e=0.9` — mais bouncy do que intuitivo. → Mitigação: documentar; é Godot canon. Quem quiser absorvedor preciso usa `min` localmente (`PhysicsMaterial` futuro vai expor escolha).
- **[Risk]** Performance: overlay de momentum percorre todos os RigidBody2D do tree por frame. Pra N pequeno (≤100 nas demos) é nada; pra N grande pode aparecer. → Mitigação: overlay é opt-in (default off). Documentar.
- **[Trade-off]** Determinismo de iteração ordem (pre-order do tree). Performance fica O(N²) na broad phase como hoje (intencional). Tree-walks repetidos no advanceAndResolve podem cachear (futura otimização).
- **[Trade-off]** Não temos sleeping → bodies "parados" continuam sendo integrados (gasto CPU). Não importa pras demos atuais. Vira issue se um demo simular dezenas de bodies que rapidamente entram em repouso (não é o caso).

## Migration Plan

1. **Foundation primeiro:** `SweepResult.point` real é a primeira tarefa. Todos os testes existentes em `kinematic-move-and-collide` que checam `point` por valor exato passam a esperar o novo ponto (alguns precisam atualizar valores esperados).
2. **`RigidBody2D` esqueleto:** classe + properties + Acumuladores + registry. Sem solver ainda; integra apenas (forças → velocity → position direto, sem sweep). Validar via teste de queda livre (gravity → velocity coerente).
3. **Sweep + impulse resolver:** liga o solver no `PhysicsSystem.step(dt)`. Validar com par de bodies elásticos: troca momento perfeita, KE conservada.
4. **Friction tangencial:** liga Coulomb. Validar com body deslizando em static body horizontal: spin up correto.
5. **Diagnósticos:** `totalLinearMomentum`/Angular/KE methods + overlay no GameHost.
6. **Bindings Python + stubs.**
7. **Migração de demos:** primeiro Stress (mais simples), depois Tumbling (mais complexa). Tests visuais via run em paralelo.
8. **CLAUDE.md update:** seção "RigidBody2D vs CharacterBody2D".

Rollback: change é additiva (CharacterBody2D inalterado). Reverter = git revert do branch; nenhum jogo pre-existente quebra.

## Open Questions

- **Combine rule de fricção:** `sqrt(a·b)` (Box2D) vs `min(a, b)` vs `(a+b)/2`. Adotamos sqrt; revisitar se ficar contraintuitivo nas demos.
- **Overlay sparkline:** quantas amostras (60? 120?) e taxa (a cada physics tick? a cada N frames?). Default 60 amostras a cada physics tick (= 1 segundo a 60Hz). Ajustável depois.
- **`Σp` no overlay incluir CharacterBody2D?** Eles têm `velocity` mas não `mass`. → Não incluímos nesta change (overlay foca em RigidBody, onde a métrica é well-defined).
- **`Camera2D` view transform afeta debug overlay de colliders?** Já sim hoje (CLAUDE.md). Overlay de momentum é UI screen-space, não world-space — não passa por view.