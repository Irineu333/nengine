## 1. Staging no PhysicsContactBuffer

- [x] 1.1 Adicionar ao `PhysicsContactBuffer` uma área de staging: `stage(point, normal)` (enfileira um `ContactRecord` no staging) e `takeStaged()` (move tudo do staging para `records`, esvaziando o staging). Manter `records`/`append`/`clear`/`recording` como estão.
- [x] 1.2 Testes: `stage` + `takeStaged` move o par para `records` e esvazia o staging; `takeStaged` chamado após popular `records` (via consolidação no início do step) deixa só os staged (precedido de `clear`).

## 2. moveAndCollide grava o contato

- [x] 2.1 Em `CharacterBody2D.moveAndCollide`, no ramo de hit (`bestHit != null`), se `tree.debug.contacts.recording`, chamar `tree.debug.contacts.stage(point, normal)` com o `point`/`normal` do `KinematicCollision2D` retornado. Miss (`null`) não grava; early-out quando a gravação está off.
- [x] 2.2 Testes: hit com gravação on → staging com um par igual ao `KinematicCollision2D`; hit com gravação off → staging vazio; miss com gravação on → staging vazio.

## 3. Consolidação no PhysicsSystem.step

- [x] 3.1 Em `PhysicsSystem.step`, no ponto onde hoje limpa o buffer (gravação on), trocar `clear()` por `clear()` + `takeStaged()` (ou um único método que faça os dois) para dobrar os contatos staged em `records` antes de gravar os contatos de `RigidBody2D` deste step. Sem gravação, nada de clear/fold (early-out preservado).
- [x] 3.2 Testes: kinematic (staged) + rigid coexistem em `records` após um `step`; cenário só-kinematic (sem `RigidBody2D`) popula `records`; substep sem contato nenhum deixa `records` vazio.

## 4. Validação ponta-a-ponta

- [x] 4.1 Teste de integração: `CharacterBody2D` batendo em `StaticBody2D` via `moveAndCollide` no `_physics_process` seguido de `PhysicsSystem.step`, com gravação on → `records` contém o contato (o caso do Pong).
- [x] 4.2 Rodar a suíte do `:engine`; garantir verde (incluindo regressão dos contatos de `RigidBody2D` de `debug-physics-gizmos` — o fold não pode alterar o resultado do step).
- [x] 4.3 `openspec validate debug-kinematic-contacts --strict` e revisar coerência specs↔implementação.
