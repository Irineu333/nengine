## Why

A engine ainda não tem nó nativo para cadenciar eventos em intervalo — qualquer gameplay que precise "fazer X a cada N segundos" tem que reimplementar acumulação de `dt` no script. Snake é o primeiro jogo que exige isso (tick lógico ~8Hz sobre `_physics_process` 60Hz), mas a necessidade é genuinamente reusável (Pong poderia cronometrar power-ups; futuros jogos terão `AnimationPlayer`, `AudioPlayer`, etc.).

Esta change introduz um `Timer` Node estilo Godot — primeira capability nativa que estende `Node` puro (não `Node2D`), validando o precedente arquitetural para nós lógicos não-visuais. Também é o primeiro signal **criado em Kotlin** que cruza a ponte para Python (Pong só tem signals nascidos em Python via `signal()`).

## What Changes

- Adiciona `com.neoutils.engine.scene.Timer` (estende `Node`, não `Node2D`):
  - `@Inspect waitTime: Float` (default `1.0`)
  - `@Inspect autostart: Boolean` (default `false`)
  - `@Inspect oneShot: Boolean` (default `false`)
  - `@Inspect processCallback: TimerMode` (enum `{PHYSICS, IDLE}`, default `PHYSICS`)
  - `@Transient timeLeft: Float` (somente leitura para debug; não persiste)
  - `val timeout: Signal<Unit>` (emite quando o intervalo completa)
  - `fun start(override: Float? = null)` (override opcional do `waitTime` para essa contagem)
  - `fun stop()`
  - `val isStopped: Boolean`
- Registra `Timer` no `NodeRegistry` (string-tag `engine.Timer`) para uso em `scene.json`.
- Serialização do enum `TimerMode` como string (`"PHYSICS"`/`"IDLE"`) via `kotlinx-serialization`; `PropCoercion` aceita string para enums.
- `Timer` em modo `PHYSICS` decrementa em `onPhysicsProcess(dt)`; em modo `IDLE`, em `onUpdate(dt)`. `processCallback` é re-amostrado a cada tick (permite mudar em runtime).
- `autostart=true` agenda o primeiro `timeout` após `waitTime` segundos (não dispara imediatamente). Início efetivo é em `onEnter`.
- Ao sair da árvore (`onExit`), o Timer para automaticamente — o `timeLeft` interno zera; um `start()` futuro reinicia limpo.
- O signal `timeout` é exposto ao Python via `script_of(node).timer.timeout.connect(callback)`; a ponte Python-Kotlin para signals **criados em Kotlin** é o primeiro caso desse fluxo.
- `:games:demos` ganha cena de demonstração mostrando dois Timers lado-a-lado (um `PHYSICS`, outro `IDLE`) piscando labels.

## Capabilities

### New Capabilities
- `timer-node`: nó nativo `Timer` para agendar eventos repetidos ou one-shot, conectável via signal `timeout`.

### Modified Capabilities
- `engine-core`: registra o novo nó `Timer` no `NodeRegistry`; documenta o precedente de `Node` puro como base para nós lógicos não-visuais.
- `scene-serialization`: serialização de enums (`TimerMode`) no `properties` bag e coerção via `PropCoercion`.
- `python-scripting`: documenta o caminho de signals **criados em Kotlin** sendo conectados em Python (`timer.timeout.connect(handler)`).

## Impact

- **`:engine`**: adiciona `scene/Timer.kt`, `scene/TimerMode.kt`; registra o tipo no `NodeRegistry`. Sem impacto em `Node2D` ou no lifecycle existente.
- **`:engine-bundle`**: nenhuma mudança de API esperada — `BundleLoader` já trata `Node` genérico via `NodeRegistry`.
- **`:engine-bundle-python`**: stub `.pyi` para `Timer` em `resources/stubs/engine/`; documentação do padrão "signal Kotlin conectado em Python".
- **`:games:demos`**: nova cena de demonstração consumindo `Timer`.
- **Sem mudanças** em `:engine-skiko`, `:engine-compose`, `:games:pong`, `:games:tictactoe`.
- **Pré-requisito da change `game-snake`**: o tick lógico da cobra depende de `Timer`.
