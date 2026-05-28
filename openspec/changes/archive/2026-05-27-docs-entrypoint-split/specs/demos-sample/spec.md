## ADDED Requirements

### Requirement: Each demo scene has a documented role exercising specific invariants

A spec `demos-sample` SHALL incluir uma descrição por cena (`1`–`6`) explicando o que ela exercita do ponto de vista da engine — quais invariantes valida, qual sistema põe sob carga, qual diagnóstico visual oferece. Essa documentação MUST viver na spec (não em `CLAUDE.md` nem em `README.md`), de modo que o `README.md` possa fazer apenas o resumo de uma linha por cena e o `CLAUDE.md` possa permanecer livre de descrição cena-a-cena.

As descrições MUST cobrir, no mínimo:

- **Cena `1` Solar system**: Sol amarelo no centro com 8 planetas (Mercúrio→Netuno) e luas conhecidas (Lua na Terra; Io, Europa, Ganimedes, Calisto em Júpiter; Titã em Saturno; Tritão em Netuno) orbitando seus pais. Saturno carrega um `SaturnRing` (anel achatado via scale não-uniforme). Exercita o invariante de composição aninhada de transform (`Transform composition by ancestry` em `engine-core`) em até 4 níveis (Sol → órbita-planeta → planeta → órbita-lua → lua), validando que `world()` cacheia corretamente sob mutação simultânea de múltiplos ancestrais por frame.
- **Cena `2` Scale hierarchy**: Pai com `scale` oscilando faz o filho crescer e encolher. Exercita composição de scale via `Shape.onRender` ao longo da cadeia de ancestrais.
- **Cena `3` Spawner**: Clique do mouse adiciona bolinhas durante `onUpdate`; um trap central (`Area2D`) remove durante `onAreaEntered`. Exercita mutação durante traversal (`Safe mutation during scene traversal` em `engine-core`). `F2` mostra que o overlay de colliders sai do `GameHost` e usa cores distintas para `Area2D` vs `PhysicsBody2D`.
- **Cena `4` Collision stress**: 30 `RigidBody2D` bolinhas (`restitution=1f`, `friction=0f`) dentro de uma arena `BoundaryWalls` (4 `StaticBody2D` que acompanham `tree.size` no resize). O engine solver integra cada bolinha (sem `moveAndCollide` no script), sweep com TOI loop, e aplica impulso bilateral (linear + angular) em cada contato — bola pesada empurra bola leve (transferência de momento), sem tunneling estrutural mesmo em alta velocidade. `F2` mostra os AABBs das `CollisionShape2D` (vermelho para Bodies). `F3` mostra `Σp`, `ΣL`, `ΣKE` com sparklines: KE permanece constante (elástico).
- **Cena `5` Rotating box**: 12 `CharacterBody2D` bolinhas vivem como filhas de um `Node2D` "caixa" que rotaciona **e** translada a cada frame (envelope AABB quicando nas paredes da scene). 4 `StaticBody2D` paredes são filhas do mesmo wrapper rotativo, em coordenadas locais. `moveAndCollide` opera no parent frame compartilhado (= local da caixa), de modo que o sweep continua axis-aligned mesmo com a caixa girando em world — bolinhas batem corretamente em paredes e em siblings sem tunelar. Exercita o invariante de invalidação por mutação de ancestral sob carga real de colisão e em frame rotativo não-estacionário. `F2` mostra os AABBs envelopados dos `CollisionShape2D` rotacionados em world.
- **Cena `6` Tumbling swarm**: 16 quadrados `RigidBody2D` (`restitution=1f`, `friction=0.4f`) com velocidade linear e angular iniciais, dentro de `BoundaryWalls` (paredes acompanham `tree.size` no resize). O engine solver resolve cada contato pelo caminho rotated do sweep (`sweepRotatedRectRotatedRect`) com leading-corner contact point, impulso normal + Coulomb tangencial — squares quicam elasticamente contra paredes e entre si, com spin perceptível em hits glancing. `F2` mostra os OBBs rotacionados envelope. `F3` mostra `ΣL` (angular momentum) conservado em hits elásticos frictionless e drift sob fricção.

#### Scenario: Spec describes all six scenes

- **WHEN** `openspec/specs/demos-sample/spec.md` é aberto
- **THEN** existe uma seção (Requirement) cobrindo as cenas `1` Solar system, `2` Scale hierarchy, `3` Spawner, `4` Collision stress, `5` Rotating box, `6` Tumbling swarm
- **AND** cada cena tem ao menos um parágrafo descrevendo o invariante ou sistema que exercita
- **AND** o conteúdo dessa Requirement não duplica detalhe de implementação (esses ficam nas specs `engine-core`, `rigid-body-2d`, `kinematic-move-and-collide`)

#### Scenario: README.md can summarize without losing detail

- **WHEN** o `README.md` resume a cena em uma única linha
- **THEN** o leitor que quer o detalhe completo (invariantes exercitados, parâmetros de física, what F-keys mostram) encontra o material em `openspec/specs/demos-sample/spec.md`
- **AND** o `CLAUDE.md` pode permanecer livre de descrição cena-a-cena
