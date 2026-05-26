## Why

A engine hoje detecta colisão (CCD swept correto via `CharacterBody2D.moveAndCollide`) mas não **resolve** colisão: não existe massa, restituição, fricção, inércia ou velocidade angular. Todo bouncing/transferência de momento vive na demo — `TumblingSwarmDemo` carrega ~200 linhas de matemática de impulso linear + angular + fricção de Coulomb + dedup-por-`identityHashCode`, e `CollisionStressDemo` faz reflect-pela-normal sem transferência (bola pesada não empurra bola leve). Não há nada que demonstre **conservação de momento** explicitamente.

Para evoluir a engine de "kinematic-only" para "rigid body capaz" — e ganhar transferência e conservação de momento como propriedades de primeira classe verificáveis — precisamos de um `RigidBody2D` canônico (Godot/Unity-like) integrado e resolvido pela engine.

## What Changes

- **NEW** `RigidBody2D` em `engine/physics/`: terceiro tipo de `PhysicsBody2D` ao lado de `StaticBody2D` e `CharacterBody2D`. A engine é dona de `position` e velocidades — script aplica forças/impulsos.
  - Slots `@Inspect`: `mass: Float = 1f`, `inertia: Float = 0f` (0 = auto-derivar do shape), `restitution: Float = 0f` (inelástico default, Godot-canon), `friction: Float = 1f`, `gravityScale: Float = 1f`, `linearDamping: Float = 0f`, `angularDamping: Float = 0f`.
  - Slots `@Transient`: `linearVelocity: Vec2`, `angularVelocity: Float`, acumuladores `appliedForce`, `appliedTorque`.
  - Métodos: `applyForce`, `applyImpulse`, `applyForceAt(force, worldPoint)`, `applyImpulseAt(impulse, worldPoint)`, `applyTorque`.
- **NEW** integrator + impulse solver dentro de `PhysicsSystem.step(tree, dt)`:
  - Após `_physics_process` (scripts aplicam forças), engine integra `v += (gravity·gravityScale + F/m)·dt` e `ω += τ/I·dt`, depois sweep CCD pelo TOI loop (até R=4 iterações por body por frame), resolvendo impulso linear+angular em cada contato.
  - Equação: `j = -(1+e)·(v_rel·n) / (1/mA + 1/mB + (rA × n)²/IA + (rB × n)²/IB)`. Aplica `j·n` em ambos os bodies no `contactPoint` real, mexendo simultaneamente em linear e angular.
  - Restituição combinada `e = max(e_A, e_B)` (Godot-like). Fricção combinada `μ = sqrt(μ_A · μ_B)` (Box2D-like).
  - Resolução **por par, uma vez** — chega de truque de `identityHashCode` no script.
- **NEW** `SweepResult.point` agora é o **ponto geométrico real** de contato (canto/face/ponto-mais-próximo conforme par de shapes), não o centro do OBB. Pré-requisito do solver angular.
- **NEW** `PhysicsSystem.gravity: Vec2` (default `Vec2.ZERO`) e diagnósticos: `tree.totalLinearMomentum()`, `tree.totalAngularMomentum()`, `tree.totalKineticEnergy()`.
- **NEW** Overlay didático `F3`: gráfico de drift de `Σp`, `ΣL`, `ΣKE` ao longo do tempo. Visualiza conservação (elástico → reta) vs dissipação (inelástico → degrau a cada contato).
- **NEW** Python: tipo `RigidBody2D` exposto no `Context`, com `# extends RigidBody2D`, properties `linear_velocity`, `angular_velocity`, `mass`, `restitution`, etc. Stubs `.pyi` atualizados.
- **BREAKING — demos:** `CollisionStressDemo` (4) e `TumblingSwarmDemo` (6) migram pra `RigidBody2D`. `Ball` e `TumblingSquare` perdem toda a matemática de resposta inline (~150 linhas no total). `Ball` precisa `restitution=1f` explícito pra continuar bouncy (era implícito antes); `TumblingSquare` vira ~5 linhas de setup.
- **NÃO BREAKING:** `CharacterBody2D` e `moveAndCollide` permanecem intactos. Pong, TicTacToe, Snake, Asteroids, Hello-World não são tocados.
- **Mid-frame `self.position = X` em RigidBody2D**: permitido (teleporte), com warning único por body no log na primeira ocorrência.

## Capabilities

### New Capabilities

- `rigid-body-2d`: Define `RigidBody2D` (massa, inércia, restituição, fricção, gravidade), o integrador + impulse solver no `PhysicsSystem.step(tree, dt)`, e os diagnósticos de momento (`totalLinearMomentum`, `totalAngularMomentum`, `totalKineticEnergy`) com overlay didático.

### Modified Capabilities

- `kinematic-move-and-collide`: O requisito "Demos module ships a rotated-sweep visualization scene" muda de "resposta de impulso inline na demo" para "resposta resolvida pela engine via `RigidBody2D`". As demos migradas (4 e 6) deixam de exigir a matemática de impulso no demo — passa a ser propriedade do solver. O requisito sobre `SweepResult.point` muda de "OBB center suficiente" para "ponto geométrico real de contato".
- `engine-core`: Adiciona `PhysicsSystem.gravity` global, mudança no contrato de ordem de `_physics_process` vs integração (script roda antes do solver no mesmo tick), e o slot `RigidBody2D` no `NodeRegistry`.
- `python-scripting`: `RigidBody2D` adicionado aos bindings implícitos no `Context`; novos métodos `apply_force`/`apply_impulse`/etc expostos.

## Impact

- **Código afetado:**
  - `engine/src/main/kotlin/com/neoutils/engine/physics/`: novo `RigidBody2D.kt`; mudanças em `PhysicsSystem.kt` (integrator + solver + gravidade), `Shape2D.kt` (contact point real no `SweepResult`).
  - `engine/src/main/kotlin/com/neoutils/engine/tree/SceneTree.kt`: hooks `totalLinearMomentum`/`totalAngularMomentum`/`totalKineticEnergy`; ordem de `_physics_process` vs `PhysicsSystem.step`.
  - `engine/src/main/kotlin/com/neoutils/engine/runtime/`: registry do tipo no `NodeRegistry`.
  - `engine-bundle-python/src/main/kotlin/.../PythonScriptHost.kt` (+ `Context`): bindings.
  - `engine-bundle-python/src/main/resources/stubs/engine/`: stubs `.pyi`.
  - `games/demos/src/main/kotlin/.../CollisionStressDemo.kt`, `TumblingSwarmDemo.kt`: migração.
  - `engine-skiko/src/main/kotlin/.../DebugOverlay.kt` (ou equivalente): overlay `F3`.
  - `GameConfig`: `toggleMomentumOverlayKey: Key = Key.F3`.
- **Testes:**
  - Conservação linear (elástico, m1≠m2): testa `Σp_antes ≈ Σp_depois` por float epsilon.
  - Inelástico stop relativo: pós-contato `(v_A - v_B)·n ≈ 0`.
  - Conservação angular em pair-hit oblíquo (lever-arm balanceado).
  - KE não-cresce nunca (monotonicamente não-crescente em qualquer contato).
  - Teleporte mid-frame dispara warning único por body (não por frame).
- **Documentação:** `CLAUDE.md` ganha uma seção "RigidBody2D vs CharacterBody2D" (quem usa quando) e o invariante #3 é estendido para mencionar o solver. ROADMAP marca rigid-body como entregue.
- **Não-objetivos desta change** (ficam pra futuro):
  - `PhysicsMaterial2D` compartilhado (props seguem por-body).
  - Sub-stepping (single-pass por frame com TOI loop é suficiente pras demos).
  - Joints/constraints.
  - Sleep islands.
  - Per-area gravity overrides.