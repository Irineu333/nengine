## Context

`:games:demos` é um módulo Skiko que hospeda 6 demonstrações visuais dos invariantes do `:engine`. O slot 1 (`TransformOrbitDemo`) exercita o invariante A1 (composição de transform por ancestralidade): um único pivô (`Rotator`) gira a uma velocidade angular constante e dois `Circle2D` filhos, posicionados em `(±RADIUS, 0)` no espaço local do pivô, são vistos orbitando o centro porque `world()` compõe a rotação do pai na posição dos filhos.

O demo prova o invariante em sua forma minimal — **um** nível de composição entre o `Rotator` e os filhos. O engine, porém, suporta composição em árvore arbitrariamente profunda; a invalidação do cache de `world()` propaga eagerly por descendentes (CLAUDE.md, seção "Performance Notes", item 3). Um sistema solar — Sol → órbita-do-planeta → planeta → órbita-da-lua → lua — é o mesmo mecanismo iterado em N níveis: cada órbita é uma instância do mesmo `Rotator` parametrizado com `ω` diferente, e cada lua vive em coordenadas locais do planeta cuja órbita já está rotacionando em coordenadas locais do Sol.

O demo atual também tem o atributo de "convenção sem Camera2D" comum aos demais demos (`DemoSwitcherRoot` documenta a escolha): visuais dimensionam em função de `tree.size`, então redimensionar a janela escala o demo. Substituir o slot 1 sem introduzir Camera2D mantém essa coerência.

## Goals / Non-Goals

**Goals:**
- Substituir o slot 1 por uma cena de sistema solar que exercite composição de transform em até 3 níveis (Sol → órbita-planeta → planeta → órbita-lua → lua = 4 níveis de `Rotator`/Node2D efetivos).
- Demonstrar visualmente que `world()` cacheia corretamente sob mutação de múltiplos ancestrais por frame (cada órbita muta sua `rotation` em `onProcess`).
- Manter o demo **somente** sobre composição afim de transforms — sem física, sem scripting, sem Camera2D.
- Cena inteira parametrizada em frações de `min(W, H)` para responder a redimensionamento da janela como os outros demos.

**Non-Goals:**
- Realismo astronômico. Raios e velocidades são comprimidos para legibilidade visual; nenhum valor pretende reproduzir UA, períodos de Kepler reais, ou inclinação orbital verdadeira.
- Rotação própria dos planetas (own-axis spin). A pedagogia já é fechada por composição orbital; spin próprio seria redundante e mais ruidoso visualmente. Não cobrir.
- Órbitas elípticas. Exigiriam `position = f(t)` paramétrico em cada planeta, fugindo da pureza-composição que o demo testa.
- Cinturão de asteroides ou cometas. Cabe num demo futuro de spawner/quantidade; este aqui é sobre topologia da árvore.
- Trails, occlusion ordering controlado, ou outros efeitos visuais. Ordem de desenho é a ordem-de-árvore default.
- Mover ou refatorar o `Rotator` para `:engine`. Ele permanece no package `com.neoutils.engine.games.demos`, mas em arquivo dedicado por ergonomia (tem >1 uso agora).
- Adicionar testes unitários. Demos não têm cobertura unitária por padrão; o smoke é o demo abrindo e rodando.

## Decisions

### D1 — Cena fixa em Kotlin (sem `scene.json`, sem scripting)

`:games:demos` é code-only por convenção do módulo; nenhum slot atual usa bundle/scripting. Manter o padrão: `SolarSystemDemo` constrói a árvore em `init { buildTree() }` exatamente como `TransformOrbitDemo` faz hoje. Não há motivo para introduzir `:engine-bundle`/`:engine-bundle-python` no módulo `:games:demos` apenas para esta cena.

**Alternativa considerada:** mover o demo para um bundle `.json` + scripts Python. Rejeitada porque adicionaria dependência transitiva ao `:games:demos` (GraalPy boot ~segundos) só para um slot, contaminando todos os outros demos.

### D2 — `Rotator` ganha `angularVelocity` por instância

Hoje `Rotator.onProcess` lê `TransformOrbitDemo.ANGULAR_VELOCITY` (constante global). A cena solar precisa de pelo menos 16 `Rotator`s com `ω` distintas (8 órbitas planetárias + 7 órbitas lunares + a do anel não conta porque o anel não gira). Soluções possíveis:

| Opção | Avaliação |
|---|---|
| (a) Criar 16 subclasses de `Rotator` | escala mal, ruidoso |
| (b) `Rotator(var angularVelocity: Float = 1f)` | uma linha de mudança, casa com o estilo "Node2D shipped pelo engine são `open` por default" |
| (c) Inverso do problema: cada planeta-pivô vira sua própria subclasse com `ω` hardcoded | mistura responsabilidades (orbit + visual) |

Escolhido **(b)**. `Rotator` migra para um arquivo próprio `Rotator.kt` no mesmo package (single-purpose, reutilizável por demos futuros).

**Compatibilidade:** o único caller hoje é o próprio `TransformOrbitDemo`, que está sendo substituído. Não há call sites externos a preservar.

### D3 — Topologia da árvore (4 níveis máximos)

```
SolarSystemDemo (Node2D)
└── Center (Node2D) — reposicionado em onProcess se tree.size mudar
    ├── Sun (Circle2D)
    ├── <PlanetName>Orbit (Rotator com ω específica)
    │   └── <PlanetName> (Circle2D)
    │       └── <MoonName>Orbit (Rotator)  ← só para planetas com luas
    │           └── <MoonName> (Circle2D)
    └── ... (8× planetas)
```

- **Centro de revolução = Center**, não o `Sun`. O `Sun` é um `Circle2D` irmão das órbitas (mesmo pai `Center`), em posição `(0,0)` local. Por quê: se as órbitas fossem filhas de `Sun`, qualquer transform aplicado ao `Sun` (futuramente, p. ex. pulse de scale) afetaria as órbitas. Separar deixa o Sol puramente visual e as órbitas geometricamente independentes.
- **Profundidade máxima do tree-walk relevante**: `Center → JupiterOrbit → Jupiter → IoOrbit → Io` = 5 `Node2D` na cadeia. Exercita 5 pushes/pops de transform por frame em cada lua de Júpiter.

### D4 — Mapeamento de raios orbitais em frações de `min(W, H)`

Demos seguem `tree.size` (sem Camera2D). Para evitar que Netuno saia da tela em janelas pequenas e que Mercúrio colapse no Sol, raios são frações de `unit = min(tree.width, tree.height)`:

| Corpo | Fração | Raio em 800×600 |
|---|---|---|
| Mercúrio | 0.06 | 36 px |
| Vênus | 0.10 | 60 px |
| Terra | 0.15 | 90 px |
| Marte | 0.20 | 120 px |
| Júpiter | 0.27 | 162 px |
| Saturno | 0.33 | 198 px |
| Urano | 0.39 | 234 px |
| Netuno | 0.45 | 270 px |

Frações lunares (relativas ao raio do planeta-pai, NÃO em `unit`):

| Lua | Raio orbital | Notas |
|---|---|---|
| Moon (Terra) | 18 px | absoluto, planeta pequeno |
| Io, Europa, Ganimedes, Calisto (Júpiter) | 22, 30, 40, 52 px | escada simples; Calisto a mais distante |
| Titã (Saturno) | 35 px | fora do anel (anel ~20 px) |
| Tritão (Netuno) | 20 px | |

`SolarSystemDemo.onProcess` recalcula a posição do `Center` (e implicitamente a unit) só quando `tree.size` muda — mesmo idiom do `TransformOrbitDemo` atual. **Raios orbitais não são recalculados dinamicamente** numa primeira versão: são fixados em `buildTree()` em função do `tree.size` inicial. Aceita-se o trade-off de que redimensionar a janela em runtime move o `Center` mas mantém os raios. Justificativa: recalcular raios dinamicamente exigiria atribuir `transform` de todos os 8 planetas + 7 luas a cada frame de resize, o que mexeria com o cache de `world()` em ancestrais (D5 do CLAUDE.md), virando ruído pedagógico em vez de demo do invariante A1.

**Alternativa considerada:** recalcular raios em resize. Rejeitada pelo motivo acima. Pode ser revisitada num demo futuro de "live layout".

### D5 — Velocidades angulares heterogêneas

Períodos reais (anos): Mer 0.24, Ven 0.62, Ter 1.0, Mar 1.88, Jup 11.86, Sat 29.5, Ura 84, Net 165. Razão Mer:Net ≈ 690:1. Em pixels/segundo isso é visualmente impossível — Mercúrio borrão, Netuno parado.

Comprimir via `ω_relativo = (T_terra / T_planeta)^0.6`, com `ω_terra = 0.6 rad/s`. Tabela resultante (rad/s):

| Planeta | ω |
|---|---|
| Mercúrio | 1.45 |
| Vênus | 0.83 |
| Terra | 0.60 |
| Marte | 0.39 |
| Júpiter | 0.13 |
| Saturno | 0.08 |
| Urano | 0.04 |
| Netuno | 0.02 |

Luas usam `ω` maior que o planeta-pai (luas orbitam mais rápido que ano-planetário sempre): Lua=4.0, Io=5.0, Europa=4.0, Ganimedes=3.0, Calisto=2.0 (Galileanas em ordem decrescente — corretamente, Io é a interna mais rápida), Titã=2.5, Tritão=3.5. Números são chute calibrado e ficam todos numa companion object `Speeds`. Ajustáveis ao ver na tela.

### D6 — Anel de Saturno como `SaturnRing : Node2D()` (local ao arquivo)

`Renderer` tem `drawCircle(filled=false, thickness=...)`, e `Node2D` suporta scale não-uniforme. Receita:

```
SaturnRing : Node2D() {
    transform = Transform(scale = Vec2(1f, 0.4f))  // achatamento de "anel visto de perfil"
    override fun onDraw(renderer) {
        renderer.drawCircle(Vec2.ZERO, radius = 20f, color = Color(...alpha=0.6), filled = false, thickness = 1.5f)
    }
}
```

O `SceneTree.render` aplica o `pushTransform(scale = Vec2(1f, 0.4f))` antes do `onDraw`, e o `drawCircle` desenha um círculo verdadeiro em coordenadas locais — o resultado projetado é uma elipse achatada verticalmente. O anel é filho de `Saturn` (não da `SaturnOrbit`), então herda automaticamente a translação orbital de Saturno.

`SaturnRing` é um detalhe puramente visual deste demo; mora no mesmo arquivo `SolarSystemDemo.kt` como classe top-level no package `com.neoutils.engine.games.demos`. Não vai pro `:engine` porque (a) não é uma primitiva genérica, é decoração específica; (b) o engine não tem nó "Ellipse" como classe de primeira ordem ainda — quando tiver, o anel migra para usá-la.

**Alternativa considerada:** desenhar o anel via `Polygon2D` com pontos pré-computados de elipse. Rejeitada porque introduziria um cálculo trigonométrico no `buildTree()` por ganho zero comparado a `drawCircle` + scale não-uniforme.

### D7 — Cores

Cores escolhidas a olho, todas em `Color(r, g, b)` (alpha=1, exceto anel):

| Corpo | r,g,b |
|---|---|
| Sol | 1.0, 0.85, 0.3 |
| Mercúrio | 0.6, 0.6, 0.6 |
| Vênus | 0.95, 0.85, 0.6 |
| Terra | 0.3, 0.5, 0.95 |
| Lua | 0.85, 0.85, 0.85 |
| Marte | 0.85, 0.35, 0.2 |
| Júpiter | 0.85, 0.7, 0.5 |
| Io | 1.0, 0.95, 0.4 |
| Europa | 0.95, 0.9, 0.85 |
| Ganimedes | 0.7, 0.6, 0.5 |
| Calisto | 0.5, 0.45, 0.4 |
| Saturno | 0.9, 0.8, 0.55 |
| Anel | 0.9, 0.85, 0.7, **alpha 0.6** |
| Titã | 0.9, 0.7, 0.3 |
| Urano | 0.6, 0.85, 0.9 |
| Netuno | 0.25, 0.4, 0.85 |
| Tritão | 0.8, 0.8, 0.95 |

Cores moram numa companion object `Palette` para facilitar tunagem.

### D8 — Tamanhos (raios em pixels, absolutos)

Planetas escalam pouco com janela na prática; usar pixels absolutos é mais simples e mais legível:

| Corpo | radius (px) |
|---|---|
| Sol | 28 |
| Mercúrio | 3 |
| Vênus | 5 |
| Terra | 5 |
| Lua | 2 |
| Marte | 4 |
| Júpiter | 12 |
| Io / Europa / Ganimedes / Calisto | 2 / 2 / 3 / 3 |
| Saturno | 10 |
| Anel (raio do `drawCircle`) | 20 |
| Titã | 3 |
| Urano | 8 |
| Netuno | 8 |
| Tritão | 2 |

### D9 — Renomeação de identificadores

| Antes | Depois |
|---|---|
| `TransformOrbitDemo.kt` | `SolarSystemDemo.kt` |
| `class TransformOrbitDemo` | `class SolarSystemDemo` |
| `Slot.Orbit` (em `DemoSwitcherRoot`) | `Slot.SolarSystem` |
| `Slot.Orbit to ::TransformOrbitDemo` | `Slot.SolarSystem to ::SolarSystemDemo` |
| HUD label `"1. Transform orbit (rotation -> position)"` | `"1. Solar system (nested transform composition)"` |
| `companion object { const val RADIUS, ANGULAR_VELOCITY }` | removido (substituído por `Speeds`/`Radii`/`Palette` companion no novo arquivo) |

`Rotator` muda de arquivo (`Rotator.kt`) e ganha `var angularVelocity: Float = 1f`. O construtor sem-args é preservado (necessário para futuro suporte a `@Serializable` no demos, embora hoje não use).

## Risks / Trade-offs

- **[Visual: luas pequenas sobrepostas em Júpiter]** → As 4 luas Galileanas têm raio 2-3 px e raios orbitais 22-52 px. Em frames próximos do conjunção visual entre duas órbitas, as luas vão se sobrepor por 1-2 frames. Aceitar — é um artefato esperado de cena 2D plana sem 3D. Mitigação opcional futura: espaçar mais (sair de 22/30/40/52 para 25/40/55/70), custaria sair do tamanho de Júpiter na tela.
- **[Visual: Mercúrio às vezes ocluído pelo Sol]** → Mercúrio raio orbital 36 px, Sol raio 28 px. Margem de 8 px. Em frames de oposição visual, Mercúrio aparece fora; em conjunção, sobreposto. Idem: artefato esperado.
- **[Performance: 17 nós a mais por frame]** → Tree-walk passa de ~5 nós (TransformOrbitDemo) para ~30 nós (SolarSystemDemo: 1 root + 1 center + 1 sun + 8 orbits + 8 planets + 7 moon-orbits + 7 moons + 1 ring = 34). Cada nó faz push/pop de transform e `onDraw`. Custo trivial em qualquer máquina dos últimos 15 anos — Skiko mantém 60 fps com facilidade. Não exige medição.
- **[Pedagogia: spectator pode achar que é "demo de astronomia" e não "demo de transform composition"]** → A label do HUD ("Solar system (nested transform composition)") nomeia explicitamente o invariante exercitado. O texto de CLAUDE.md no item `1.` reforça. Sem mitigação adicional.
- **[Substituição vs. preservação]** → Substituir o demo perde o caso minimal (2 orbiters em raio fixo) como referência didática isolada. O novo demo contém o caso minimal como subset (qualquer planeta sem lua sob seu `*Orbit` é exatamente o demo antigo). Considera-se que a redução é nula em pedagogia.
- **[Resize parcial]** → Redimensionar a janela em runtime move o `Center` mas não escala os raios orbitais (D4). O demo continua funcional; apenas a composição visual fica menos "preenchida" em janelas grandes ou planetas saindo da tela em janelas muito menores que a inicial. Documentado em D4.
