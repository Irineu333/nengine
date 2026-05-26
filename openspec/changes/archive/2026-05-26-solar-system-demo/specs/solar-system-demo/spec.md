## ADDED Requirements

### Requirement: Slot 1 of demos hosts SolarSystemDemo

O módulo `:games:demos` SHALL ter, no slot 1 do `DemoSwitcherRoot` (acionado pela tecla `1`), uma cena instanciada por `SolarSystemDemo` em vez de `TransformOrbitDemo`. O enum interno `DemoSwitcherRoot.Slot` MUST renomear o valor `Orbit` para `SolarSystem`. O factory map MUST mapear `Slot.SolarSystem to ::SolarSystemDemo`. O slot inicial (campo `active`) MUST permanecer `Slot.SolarSystem` (continua sendo o demo padrão na inicialização). A classe `TransformOrbitDemo` MUST NOT existir mais no source tree do módulo após esta change.

#### Scenario: Pressing key 1 selects the solar system demo

- **WHEN** o módulo `:games:demos` é executado e o usuário pressiona a tecla `1` durante a execução (ou na inicialização, pois é o slot default)
- **THEN** a cena ativa é uma instância de `SolarSystemDemo`
- **AND** o HUD overlay exibe a string `"1. Solar system (nested transform composition)"` na primeira linha

#### Scenario: TransformOrbitDemo no longer exists

- **WHEN** o source tree de `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/` é inspecionado
- **THEN** nenhum arquivo contém a declaração `class TransformOrbitDemo`
- **AND** nenhum arquivo do módulo importa ou referencia o identificador `TransformOrbitDemo`

### Requirement: SolarSystemDemo builds a fixed scene of sun, planets, moons and ring

`SolarSystemDemo` SHALL ser uma `class SolarSystemDemo : Node2D()` que, no seu `init`, constrói (via método privado `buildTree()`) uma árvore com exatamente a seguinte topologia e quantidades:

- 1 `Node2D` filho direto chamado `Center` (sem visual próprio), posicionado em `(tree.width / 2f, tree.height / 2f)` quando `tree.size` é conhecido.
- Sob `Center`: 1 `Circle2D` chamado `Sun` em posição local `Vec2.ZERO`.
- Sob `Center`: 8 instâncias de `Rotator` chamadas `MercuryOrbit`, `VenusOrbit`, `EarthOrbit`, `MarsOrbit`, `JupiterOrbit`, `SaturnOrbit`, `UranusOrbit`, `NeptuneOrbit`, cada uma com sua `angularVelocity` distinta.
- Sob cada `*Orbit`: exatamente 1 `Circle2D` planeta (`Mercury`, `Venus`, ..., `Neptune`), posicionado em local `(radius, 0f)`.
- Sob `Earth`: 1 `Rotator` `MoonOrbit` contendo 1 `Circle2D` `Moon`.
- Sob `Jupiter`: 4 `Rotator`s (`IoOrbit`, `EuropaOrbit`, `GanymedeOrbit`, `CallistoOrbit`), cada um contendo 1 `Circle2D` (`Io`, `Europa`, `Ganymede`, `Callisto`).
- Sob `Saturn`: 1 `SaturnRing` (nó visual customizado), e 1 `Rotator` `TitanOrbit` contendo 1 `Circle2D` `Titan`.
- Sob `Neptune`: 1 `Rotator` `TritonOrbit` contendo 1 `Circle2D` `Triton`.

Total: 1 root + 1 Center + 1 Sun + 8 planets + 7 moons + 1 ring + 16 Rotator pivots = 35 nodes (≈30 efetivos + ring + 7 lunar-orbit pivots). Não MUST haver outros nós além desses.

#### Scenario: All eight planets are present under Center

- **WHEN** uma instância de `SolarSystemDemo` é construída e seu `Center` é localizado via `findChild("Center")`
- **THEN** `Center.children` contém exatamente um nó nomeado `Sun` e os oito nós nomeados `MercuryOrbit`, `VenusOrbit`, `EarthOrbit`, `MarsOrbit`, `JupiterOrbit`, `SaturnOrbit`, `UranusOrbit`, `NeptuneOrbit` (em qualquer ordem)

#### Scenario: Earth has exactly one moon (Moon)

- **WHEN** o nó `Earth` é localizado (filho único de `EarthOrbit`)
- **THEN** `Earth.children` contém exatamente um nó `MoonOrbit`
- **AND** `MoonOrbit.children` contém exatamente um nó `Moon`

#### Scenario: Jupiter has exactly four Galilean moons

- **WHEN** o nó `Jupiter` é localizado (filho único de `JupiterOrbit`)
- **THEN** `Jupiter.children` contém exatamente quatro `Rotator`s nomeados `IoOrbit`, `EuropaOrbit`, `GanymedeOrbit`, `CallistoOrbit`
- **AND** cada um contém exatamente um `Circle2D` nomeado `Io`, `Europa`, `Ganymede`, `Callisto` respectivamente

#### Scenario: Saturn has ring and Titan

- **WHEN** o nó `Saturn` é localizado (filho único de `SaturnOrbit`)
- **THEN** `Saturn.children` contém exatamente um nó do tipo `SaturnRing` e um `Rotator` nomeado `TitanOrbit`
- **AND** `TitanOrbit.children` contém exatamente um `Circle2D` nomeado `Titan`

#### Scenario: Neptune has exactly one moon (Triton)

- **WHEN** o nó `Neptune` é localizado
- **THEN** `Neptune.children` contém exatamente um `Rotator` nomeado `TritonOrbit`
- **AND** `TritonOrbit.children` contém exatamente um `Circle2D` nomeado `Triton`

#### Scenario: Mercury, Venus, Mars, Uranus are childless

- **WHEN** cada um dos nós `Mercury`, `Venus`, `Mars`, `Uranus` é localizado
- **THEN** seu `children` é vazio (essas posições orbitais não recebem luas neste demo)

### Requirement: Center repositions on viewport resize

`SolarSystemDemo` SHALL ter um `onProcess(dt: Float)` que, sempre que `tree.size` muda em relação à última observação, reposiciona o nó `Center` para `Vec2(tree.width / 2f, tree.height / 2f)`. A implementação MUST seguir o mesmo idiom de cache de `lastSize` usado pelo `TransformOrbitDemo` atual (campo `@Transient private var lastSize: Vec2 = Vec2.ZERO`, early-return se `tree.size == lastSize`). A implementação MUST NOT recomputar raios orbitais dos planetas nem reposicionar planetas/luas em resize — apenas o `Center` se move.

#### Scenario: Center follows viewport center

- **WHEN** o demo está rodando e `tree.size` é igual a `Vec2(800f, 600f)`
- **THEN** o `transform.position` do nó `Center` é `Vec2(400f, 300f)`

#### Scenario: Center updates only when size changes

- **WHEN** o demo está rodando e `tree.size` permanece constante entre dois frames consecutivos
- **THEN** o `transform` do `Center` NÃO MUST ser reatribuído nesse segundo frame (verificável por instrumentação ou por inspeção do código: a guarda `if (tree.size == lastSize) return` aparece antes de qualquer escrita em transform)

### Requirement: Demo runs without Camera2D

`SolarSystemDemo` MUST NOT instanciar nem adicionar nenhum `Camera2D` à árvore. Os visuais MUST permanecer em coordenadas de surface (pixels), seguindo a convenção documentada em `DemoSwitcherRoot` ("Demos run in raw surface pixels (no Camera2D) by design"). Os raios orbitais MUST ser computados em `buildTree()` em função de `tree.size` capturado naquele momento (ou de um valor default razoável quando `tree` ainda for `null` — ver D4 do design).

#### Scenario: No Camera2D in source

- **WHEN** o source de `SolarSystemDemo.kt` é inspecionado
- **THEN** o arquivo NÃO importa nem instancia `com.neoutils.engine.scene.Camera2D`

### Requirement: Rotator becomes configurable per-instance

A classe `Rotator` em `:games:demos` SHALL declarar `var angularVelocity: Float` (default `1f`) e usar essa variável em seu `onProcess(dt)` (em vez de ler uma constante global como hoje). A classe MUST viver em um arquivo próprio `Rotator.kt` no package `com.neoutils.engine.games.demos`. A constante `TransformOrbitDemo.ANGULAR_VELOCITY` MUST NOT existir mais após esta change.

#### Scenario: Rotator advances rotation by its own angularVelocity

- **WHEN** um `Rotator` é construído com `angularVelocity = 2f` e seu `onProcess(0.5f)` é chamado uma vez
- **THEN** o `transform.rotation` aumenta em `1f` (= `2f * 0.5f`) em relação ao valor anterior

#### Scenario: Rotator lives in its own file

- **WHEN** o source tree de `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/` é inspecionado
- **THEN** existe o arquivo `Rotator.kt` declarando `class Rotator : Node2D` com a property `var angularVelocity: Float`
- **AND** nenhum outro arquivo do módulo declara `class Rotator` (a definição é única)

#### Scenario: Global ANGULAR_VELOCITY constant is removed

- **WHEN** o source do módulo é inspecionado
- **THEN** nenhuma `companion object` declara `const val ANGULAR_VELOCITY`
- **AND** nenhum `Rotator.onProcess` lê uma constante de outro objeto/classe; lê apenas `this.angularVelocity`

### Requirement: SaturnRing draws a flattened hollow ellipse

O arquivo `SolarSystemDemo.kt` SHALL declarar uma classe top-level `class SaturnRing : Node2D()` cujo `transform` local default tem `scale = Vec2(1f, 0.4f)` (ou outro valor entre 0.3 e 0.5 escolhido no design) e cujo `onDraw(renderer)` chama `renderer.drawCircle(center = Vec2.ZERO, radius = R, color = C, filled = false, thickness = T)` com `R`, `C` (alpha < 1.0), e `T` definidos como constantes/properties do próprio arquivo. A classe MUST NOT ser declarada em `:engine`. O `SaturnRing` MUST ser filho do nó `Saturn` (não de `SaturnOrbit`) para herdar a translação orbital de Saturno sem girar com a órbita.

#### Scenario: SaturnRing is local to the demos module

- **WHEN** os arquivos de `:engine` são inspecionados
- **THEN** nenhum arquivo declara `class SaturnRing`
- **AND** o arquivo `games/demos/src/main/kotlin/com/neoutils/engine/games/demos/SolarSystemDemo.kt` declara `class SaturnRing : Node2D()` como classe top-level

#### Scenario: SaturnRing has non-uniform scale

- **WHEN** uma instância de `SaturnRing` recém-construída é inspecionada
- **THEN** seu `transform.scale.x != transform.scale.y` (o `y` é menor, produzindo a aparência de elipse achatada após o push-de-transform do renderer)

#### Scenario: SaturnRing draws hollow

- **WHEN** o método `onDraw(renderer)` de `SaturnRing` é executado
- **THEN** o renderer recebe uma chamada `drawCircle(..., filled = false, ...)` (não preenchido)

### Requirement: Speeds and palette live in companion objects

`SolarSystemDemo` SHALL declarar uma `companion object` (ou múltiplos objetos nested) agrupando todas as velocidades angulares (uma constante por planeta e lua), raios orbitais e cores em um lugar único do arquivo. O `buildTree()` MUST ler dessas constantes; MUST NOT espalhar literais numéricos pelo corpo de `buildTree`. Justificativa: tunagem visual é o caso de uso mais frequente após o demo rodar; centralizar reduz fricção.

#### Scenario: Tuning angular velocity touches only the companion

- **WHEN** um contribuidor quer reduzir a velocidade de Júpiter pela metade
- **THEN** a edição é feita em uma única linha dentro de uma companion object (ex.: `const val JUPITER_OMEGA = 0.065f`), sem alterar o corpo de `buildTree`

### Requirement: HUD label reflects new demo

`DemoSwitcherRoot.HudOverlay` SHALL exibir, quando o slot ativo é o do sistema solar, a string `"1. Solar system (nested transform composition)"` (texto exato) na primeira linha do overlay. As entradas para os demais slots (2-6) MUST permanecer inalteradas.

#### Scenario: HUD reflects solar system label

- **WHEN** o demo de sistema solar está ativo e `HudOverlay.onDraw` é executado
- **THEN** o renderer recebe uma chamada `drawText("1. Solar system (nested transform composition)", ...)` na primeira linha do overlay

### Requirement: CLAUDE.md item 1 of demos list reflects new demo

O arquivo `CLAUDE.md` (raiz do repo) SHALL ter o primeiro item da lista numerada da seção "Para rodar Demos" reescrito para descrever o `SolarSystemDemo` (Sol, planetas, luas conhecidas, anel de Saturno, e a pedagogia de composição de transform aninhada). O texto MUST mencionar que o demo continua exercitando o invariante de composição (A1), agora em até 4 níveis (Sol → órbita-planeta → planeta → órbita-lua → lua). Os itens `2` a `6` MUST permanecer inalterados.

#### Scenario: CLAUDE.md describes the new demo

- **WHEN** o arquivo `CLAUDE.md` é lido
- **THEN** o item `1` da seção "Para rodar Demos" contém as palavras "Solar system" (ou equivalente em português) e menciona a composição aninhada de transform
- **AND** os itens `2` a `6` permanecem com o mesmo texto que tinham antes desta change
