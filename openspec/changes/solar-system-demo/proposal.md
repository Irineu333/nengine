## Why

O slot 1 de `:games:demos` (`TransformOrbitDemo`) é o caso degenerado mais simples possível de composição de transform: um pivô gira, dois círculos em raio fixo orbitam. Isso fecha o invariante A1 do engine, mas subexplora a pedagogia — composição de `Transform` em árvore aceita profundidade arbitrária, e o demo só exercita um nível. Um sistema solar é o mesmo mecanismo escalado: Sol → órbita-do-planeta → planeta → órbita-da-lua → lua. Substituir o demo por essa versão mantém o slot focado em "transform composition" e dramatiza a recursão da composição sem inventar capabilities novas no engine.

## What Changes

- **BREAKING**: `TransformOrbitDemo` é substituído por `SolarSystemDemo` no slot 1 de `:games:demos`. O nome de classe, arquivo, slot enum e label do HUD mudam.
- `Rotator` perde a dependência da constante global `TransformOrbitDemo.ANGULAR_VELOCITY` e ganha uma `var angularVelocity: Float = 1f` configurável por instância (necessário para os 16+ pivôs orbitais).
- `SolarSystemDemo` adiciona um nó visual novo `SaturnRing : Node2D()` (escopado ao próprio arquivo do demo, não vai pro `:engine`).
- Cena contém: Sol + 8 planetas (Mercúrio→Netuno) + 7 luas conhecidas (Lua; Io, Europa, Ganimedes, Calisto; Titã; Tritão) + anel de Saturno.
- HUD do switcher e parágrafo `1.` da seção "Para rodar Demos" em `CLAUDE.md` reescritos para refletir o novo demo.
- Sem mudanças no `:engine`, no `:engine-skiko`, em outros demos, ou em outros jogos. Não muda nenhum invariante arquitetural.

## Capabilities

### New Capabilities

- `solar-system-demo`: comportamento e estrutura do slot 1 de `:games:demos` como demonstração de composição hierárquica de transform via órbitas aninhadas.

### Modified Capabilities

_(nenhuma)_

## Impact

**Código no `:games:demos`:**
- `TransformOrbitDemo.kt` → renomeado para `SolarSystemDemo.kt`; conteúdo reescrito.
- `DemoSwitcherRoot.kt` → enum `Slot.Orbit` → `Slot.SolarSystem`; factory map e HUD label atualizados.
- `Rotator` extraído para arquivo próprio (`Rotator.kt`) com `angularVelocity` parametrizável (continua no mesmo package).

**Documentação:**
- `CLAUDE.md` → item `1` da lista de demos reescrito.

**Sem impacto em:**
- `:engine`, `:engine-skiko`, `:engine-compose`, `:engine-bundle`, `:engine-bundle-python`.
- `:games:pong`, `:games:tictactoe`, `:games:hello-world`.
- Outros slots do `:games:demos` (`ScaleHierarchyDemo`, `SpawnerDemo`, `CollisionStressDemo`, `RotatingBoxDemo`, `TumblingSwarmDemo`).
- Testes existentes (a change não adiciona testes — é visual; segue o padrão dos demais demos que também não têm cobertura unitária).
