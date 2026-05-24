## Context

A engine hoje tem dois callbacks de tempo no `Node`: `onProcess(dt)` (frame-step, `dt` variável, drenado em `GameLoop.process`) e `onPhysicsProcess(dt)` (fixed-step a `GameConfig.physicsHz`, default 60Hz). Nenhum nó nativo cadencia eventos em intervalo — quem precisa disso (gameplay tick discreto, cooldowns, animações) precisa acumular `dt` manualmente no script.

`Signal<T>` já existe e é o backbone de comunicação inter-nó. Hoje 100% dos signals em uso (Pong) **nascem em Python** via a factory `signal(type)` — exposta como instância `Signal<Any?>` Kotlin que o wrapper Python sombreia no momento do attach. O caminho inverso — signal **declarado em Kotlin** e conectado pelo Python — ainda não foi exercido por nenhum nó nativo. Cargas como `BoxCollider` emitem para o `PhysicsSystem` em Kotlin, não para handlers Python.

`PropCoercion` (em `:engine-bundle`) hoje aceita `Float`, `Int`, `Boolean`, `String`, `Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `Optional[T]`. Não há precedente de **enum** atravessando o JSON → `setExport` / `@Inspect` setter.

O `NodeRegistry` (em `:engine-serialization`) mapeia strings como `engine.Node2D` para construtores polimórficos via `kotlinx-serialization`. Adicionar `engine.Timer` segue o padrão de `engine.Camera2D`, `engine.Label`.

## Goals / Non-Goals

**Goals:**
- Nó nativo `Timer` cobrindo o caso de uso "dispara um evento a cada N segundos" e "dispara uma vez após N segundos".
- API estilo Godot: `waitTime`, `autostart`, `oneShot`, `processCallback`, signal `timeout`, métodos `start`/`stop`.
- Funciona em ambos os modos: `PHYSICS` (determinístico, sobre `_physics_process`) e `IDLE` (sobre `_process`).
- Permite ao Python conectar `timer.timeout` (`Signal` Kotlin → handler Python), validando essa direção da ponte.
- Validar que `Node` puro (sem `Node2D`) é uma base genuinamente utilizável — precedente para `AudioPlayer`, `AnimationPlayer` futuros.

**Non-Goals:**
- `paused: Boolean` (estilo Godot) — fora do escopo; quem quiser pausar chama `stop()` e depois `start()` recomputando.
- API de Timer global na engine (timers que sobrevivem a mudança de cena) — fora.
- Animação tweening ou interpolação de propriedades — domínio de uma futura capability `tweens`.
- Refatorar `_physics_process` para um clock determinístico independente do `dt` — fixed-step atual já é suficiente.
- Editor visual para Timer — virá com a change `editor`.

## Decisions

### Timer estende `Node`, não `Node2D`

`Timer` não tem transform, não desenha, não participa de cena visual. Forçá-lo a ser `Node2D` carregaria um `Transform` morto que nunca seria lido. Estender `Node` cru estabelece o **primeiro precedente** de nó lógico não-visual na engine — algo que `AudioPlayer`, `AnimationPlayer`, `HTTPRequest` (hipoteticamente) farão também.

**Alternativa rejeitada:** estender `Node2D` por consistência com `Camera2D`/`Label`. Rejeitada porque `Camera2D` e `Label` têm posição no mundo; `Timer` não. Carregar `transform` por simetria seria ruído.

### `processCallback: TimerMode` é enum `{PHYSICS, IDLE}`

Espelha o Godot `Timer.TIMER_PROCESS_PHYSICS` vs `TIMER_PROCESS_IDLE`. Persiste como string (`"PHYSICS"`/`"IDLE"`) no `scene.json` — legível humano e compatível com `kotlinx-serialization` defaults para `enum class`.

**Alternativa rejeitada:** dois nós distintos `Timer` (idle) e `PhysicsTimer`. Mais explícito, mas duplica código de countdown e quebra simetria com Godot. Manter um nó com modo enum.

**Alternativa rejeitada:** `Boolean usePhysics`. Mais simples no JSON mas obscurece intenção — `processCallback: "PHYSICS"` é mais legível que `usePhysics: true`.

### `processCallback` é re-amostrado a cada tick

O Timer não memoiza o modo no `onEnter`. A cada `onProcess`/`onPhysicsProcess`, decrementa `timeLeft` apenas se o callback batido for `processCallback`. Permite mudar o modo em runtime (raro, mas legítimo) sem reset.

**Alternativa rejeitada:** snapshot do modo no `onEnter` e ignorar mudanças subsequentes. Mais barato em ciclos mas surpreende.

### `autostart=true` agenda o primeiro `timeout` após `waitTime`, não imediatamente

Em `onEnter`, se `autostart`, `timeLeft = waitTime`. Primeiro `timeout` dispara após o intervalo completo. Espelha Godot.

**Alternativa rejeitada:** dispara `timeout` imediatamente em `onEnter` e depois cadencia. Quebra a invariante "intervalo entre `timeout`s é constante = `waitTime`".

### `start(override: Float? = null)` recomputa `timeLeft`

Sem argumento: `timeLeft = waitTime`. Com argumento positivo: `timeLeft = override` (esse disparo único usa o override; subsequentes voltam a `waitTime`). Argumento `<= 0`: erro (ou clamp para `waitTime`; decidir na implementação — preferência por erro fail-fast).

**Alternativa rejeitada:** `override` persiste para todos os ciclos seguintes (mudaria `waitTime`). Mais perigoso e foge da semântica "ajuste pontual".

### `stop()` zera `timeLeft` e marca `isStopped=true`; emissões pendentes são descartadas

Se um `stop()` é chamado de dentro de um handler de `timeout` (re-entrância), o handler corrente termina, mas nenhum próximo `timeout` dispara até `start()`. `Signal.emit` já faz snapshot, então a iteração corrente não é quebrada.

### Signal `timeout: Signal<Unit>` declarado em Kotlin é exposto ao Python

A ponte Python-Kotlin (`:engine-bundle-python`) precisa expor o atributo `timeout` no wrapper Python do `Timer`. O fluxo é simétrico ao caminho Python→Kotlin já provado: o wrapper de `Node` resolve `__getattr__('timeout')` lendo o campo Kotlin via reflection (ou via um mapeamento explícito de propriedades expostas), e devolve um proxy Python sobre o `Signal<Unit>` que aceita `.connect(handler)` e `.disconnect(handler)`.

**Decisão**: usar reflection sobre `val`s do tipo `Signal<*>` no Node Kotlin para descobrir signals expostos. Mantém o padrão "convenções, não anotações" da engine. Falha-fast se o Python tentar conectar handler com aridade errada (`Unit` requer lambda de zero args; conectar com `lambda x:` deve crashar com mensagem clara).

**Alternativa rejeitada:** exigir `@PythonExposed` ou similar nos signals Kotlin. Padrão Python (Pong) não exige anotação correspondente; manter simétrico.

### `PropCoercion` aceita enums via string `name()`

A coerção para `TimerMode` lê uma string JSON e procura `TimerMode.valueOf(s)`. Falha-fast: string inválida (ex.: `"physics"` minúsculo) explode com mensagem listando os valores válidos.

**Generalização**: a regra para enums (`if (target.isEnum) target.valueOf(string)`) cobre futuros enums genericamente. Implementar generalizado em vez de hardcodar `TimerMode`.

### Cleanup em `onExit`

Quando o Timer sai da live tree, `onExit` chama `stop()` internamente — defesa em profundidade contra um Timer "fantasma" continuar contando se algum dia for re-adicionado. Em re-attach via `addChild`, `onEnter` re-honra `autostart`.

## Risks / Trade-offs

- **[Risco] Reflection para descobrir signals Kotlin pode ser lenta em loop apertado.**
  → Mitigação: o lookup acontece uma vez no attach (Python wrapper construído) e o handler é cacheado. Emit é zero-overhead via `Signal.emit`.

- **[Risco] Re-entrância: handler chamando `start(override)` ou `stop()` dentro do `timeout`.**
  → `Signal.emit` snapshot já blinda a iteração; `Timer` precisa apenas garantir que `timeLeft` mutado durante o handler seja respeitado no próximo tick (não rearmar para `waitTime` depois). Implementação: rearma imediatamente após `emit` apenas se não foi mutado, ou mais simples — sempre lê `timeLeft` no próximo tick e age sobre ele. Decisão na implementação; teste cobrir.

- **[Risco] Drift acumulado em `PHYSICS` mode é zero (dt fixo), mas em `IDLE` mode pode acumular fração de frame.**
  → Aceitável: quem quer determinismo usa `PHYSICS`. Documentar nos stubs `.pyi` e no KDoc.

- **[Trade-off] `oneShot` é flag e não tipo separado.**
  → Menos seguro estatisticamente (não dá pra remover `oneShot=false` do tipo) mas casa com Godot e com a serialização chata (um único Timer node).

- **[Risco] Enum serializado como string quebra se renomearmos `TimerMode.PHYSICS`.**
  → Forever: nomes desse enum são contrato de bundle. Renomear é breaking change e exige migração de cenas. Documentar.

- **[Risco] Ponte Kotlin→Python para signals: handler Python que estoura exception silencia o emit dos demais handlers?**
  → Resposta: `Signal.emit` itera snapshot; uma exception num handler propaga para fora do `emit` e potencialmente até `GameLoop`. Isso é o comportamento atual e desejado (fail-fast). Documentar como expectativa.

## Open Questions

- **`PropCoercion` enum genérico vs hardcoded:** implementar a regra geral `(target.isEnum) → valueOf` já agora, ou só `TimerMode` e generalizar quando o segundo enum aparecer? Recomendação: generalizar agora; o custo é menor e o segundo enum vai aparecer (ex.: `Camera2D.process_callback` futuramente).

- **Erros de `start(override)` com `override <= 0`:** crash ou clamp? Recomendação: crash com mensagem clara (`IllegalArgumentException("Timer.start override must be positive, got X")`).

- **Demo no `:games:demos`:** adicionar uma sexta tecla `6` "Timer demo" ou enxertar num demo existente. Recomendação: tecla nova, scene própria com dois labels piscando.

- **Stub `.pyi`:** o stub do `Timer` precisa do tipo `Signal[None]` para `timeout`. Hoje os stubs do `:engine-bundle-python` têm `Signal` como tipo? Verificar e ajustar.
