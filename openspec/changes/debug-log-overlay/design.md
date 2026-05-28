## Context

A engine tem dois pilares de debug desacoplados:

- **`dx-tooling`** (`com.neoutils.engine.dx`): `Log.d/i/w/e(tag, msg)` com
  gating por nível/tag via `Log.config`, despachando para **um único**
  `Log.sink: LogSink` (`@Volatile var`, default `ConsoleLogSink`).
- **`debug-overlay`** (`com.neoutils.engine.debug`): `DebugWidget`s
  per-`SceneTree` roteados por espaço (`ScreenDebugWidget` /
  `WorldDebugWidget`), com 4 built-ins na `DebugRegistry` e toggle por HUD.

Para um overlay on-screen das últimas entradas de log, um `LogSink` precisa
alimentar um `ScreenDebugWidget`. Duas tensões guiam o design:

1. **Sink único.** Setar `Log.sink = overlay` desloca o `ConsoleLogSink` —
   perde-se o log no terminal. `Log` precisa multiplexar.
2. **Singleton process-wide vs widget per-tree.** `Log` é global; o widget
   é por árvore. O acoplamento tem que ser uma *subscrição* (o widget
   entra/sai do conjunto de sinks), não uma posse.

Restrição de invariante #4 e da spec `debug-overlay`: o `GameHost` não toca
em debug por frame e não desenha nada fora de `tree.render`. O overlay
herda isso de graça por ser um `ScreenDebugWidget` no scene graph.

Consumidores atuais de `Log.sink` (swap-and-restore): `LogTest`,
`RigidBodyTeleportTest`, `PhysicsSystemTest` — todos in-repo, migráveis.

## Goals / Non-Goals

**Goals:**

- `Log` multiplexa para um conjunto de sinks; `ConsoleLogSink` permanece
  por padrão e o gating por nível/tag de `Log.config` é preservado.
- Built-in `LogOverlayWidget` que faz tail das últimas N entradas na tela,
  cor por nível, subscrição como `LogSink` gated por `enabled`.
- Publicação segura entre a thread que chama `Log.*` e a thread de render.
- Integração transparente com o HUD (mais uma row togglável) e a
  `DebugRegistry` (campo `log`, 5 built-ins).

**Non-Goals:**

- Filtro por tag na UI do overlay (escopo futuro; o `Log.config` já filtra
  por tag no nível de produção do log).
- Scroll/histórico paginado, busca, copy-to-clipboard, persistência em
  arquivo. O overlay é um *tail* das últimas N linhas, nada mais.
- Atrelar logs a um `SceneTree` específico. Logs são globais por design.
- Qualquer mudança no formato do `ConsoleLogSink` ou na API `Log.d/i/w/e`.

## Decisions

### D1 — `Log` passa a ter um conjunto de sinks (`CopyOnWriteArrayList`)

`Log` mantém `private val sinks = CopyOnWriteArrayList<LogSink>()`
inicializado com `ConsoleLogSink`. API nova:

- `fun addSink(sink: LogSink)` — idempotente (não duplica a mesma instância).
- `fun removeSink(sink: LogSink)`.

`log()` itera `sinks` e chama `emit` em cada um (após o gate de nível/tag).

**Por que `CopyOnWriteArrayList`:** `Log.*` pode ser chamado de qualquer
thread; o fan-out itera o conjunto enquanto `addSink`/`removeSink` (raros,
disparados por toggle de UI) podem mutar concorrentemente. CoW dá iteração
livre de lock e snapshot consistente sem sincronizar o caminho quente do
log. Escritas raras tornam o custo de cópia irrelevante.

**Alternativa rejeitada — decorator/wrapping:** o overlay capturaria
`Log.sink`, setaria `Log.sink = this` e encaminharia para o capturado.
Rejeitado: com múltiplas árvores o aninhamento e a ordem de restauração
ficam frágeis, e "qual é o sink atual" deixa de ter resposta única.

### D2 — Remover o `var sink` público em favor da lista

`Log.sink` deixa de existir como propriedade pública. O único padrão de uso
hoje é swap-and-restore em três testes, que migram para:

```kotlin
// antes:  previousSink = Log.sink; Log.sink = mySink; ...; Log.sink = previousSink
// depois: Log.addSink(mySink); ...; Log.removeSink(mySink)
```

**Por quê:** um único conceito (conjunto de sinks) é mais claro que dois
(um primário + adicionais), e a spec `dx-tooling` não normatiza `sink` —
era detalhe de implementação. Migração trivial e local.

**Trade-off:** testes que querem *isolar* a saída (não ver `ConsoleLogSink`)
agora convivem com ele. Onde isso importa, o teste pode `removeSink`
temporariamente o console — mas nenhum teste atual precisa: eles afirmam
sobre o próprio sink capturado, indiferentes ao console.

### D3 — `LogOverlayWidget` é o sink e dono do ring buffer

`class LogOverlayWidget : ScreenDebugWidget(), LogSink`. Mantém um ring
buffer de capacidade fixa `N` (default **12** linhas visíveis) de entradas
imutáveis `data class LogEntry(timestampMillis, level, tag, message)`,
armazenadas num array circular com `head`/`size`.

- `emit(...)` (impl de `LogSink`, **thread do caller**): grava a entrada no
  buffer.
- `drawDebug(renderer)` (**thread de render**): lê um snapshot e desenha.

**Subscrição gated por `enabled`** (mesmo molde do `MomentumWidget`):
override do setter de `enabled` —

- `false → true`: `Log.addSink(this)` e **limpa** o buffer (`size = 0`,
  `head = 0`), evitando linhas stale de uma sessão anterior.
- `true → false`: `Log.removeSink(this)`.

Consequência aceita e documentada: enquanto desabilitado o overlay não
grava, então ao abrir ele começa vazio e faz tail das mensagens novas
("live tail"), não do passado.

### D4 — Publicação segura via `synchronized` no buffer

`emit` e o snapshot de `drawDebug` sincronizam num lock dedicado do widget.
Contenção é desprezível (no máximo alguns logs/frame vs um snapshot/frame),
e a clareza didática supera um ring lock-free. `drawDebug` copia para uma
lista local sob o lock e desenha fora dele.

**Por que não `volatile`/lock-free:** ganho nulo nesse volume; um array
circular lock-free correto é desproporcionalmente sutil para o propósito.

### D5 — Layout: canto inferior-esquerdo, mais nova embaixo

FPS e Momentum ocupam o superior-esquerdo; o HUD, o superior-direito. O
overlay ancora no **inferior-esquerdo**, re-ancorado por frame a
`owningTree.size` (segue resize, como o HUD), desenhando as últimas linhas
empilhadas para cima com a mais recente na base.

**Cor por nível:** Debug/Info em cinza/branco neutro, `Warn` âmbar, `Error`
vermelho — reusa as cores de `DebugColors` quando aplicável, senão define
as três ali. Cada linha: `[tag] message` (timestamp omitido na tela para
poupar largura; permanece no `ConsoleLogSink`).

### D6 — Filtro de exibição por nível mínimo

`var minLevel: LogLevel = LogLevel.Debug`. `drawDebug` pula entradas com
`level.ordinal < minLevel.ordinal`. É filtro **de exibição**, ortogonal ao
`Log.config` (que decide o que chega a *qualquer* sink). Entradas abaixo de
`Log.config.effectiveLevel(tag)` nunca chegam ao `emit`, então o overlay só
pode restringir além do já filtrado, nunca recuperar o que foi gateado.

### D7 — Registro como 5º built-in

`DebugRegistry` ganha `val log = LogOverlayWidget()` e `bindLayer`
registra os cinco na ordem: `fps, colliders, momentum, log, hud`. O
`DebugHud` já enumera `tree.debug.widgets` excluindo a si mesmo, então a
row do overlay aparece sem mudança no HUD. Ordem mantém o `hud` por último
(convenção atual).

## Risks / Trade-offs

- **[Múltiplas árvores espelham o mesmo log global]** → Dois
  `LogOverlayWidget` habilitados recebem o stream global inteiro (logs não
  têm dono de árvore). Comportamento documentado no spec; aceitável porque
  cenário multi-árvore-simultânea é raro e o log é conceitualmente global.
- **[Overlay esquecido habilitado mantém subscrição]** → Enquanto
  `enabled`, o widget é sink ativo e grava a cada log. Custo é um write em
  array sincronizado por entrada; desprezível. Ao desabilitar, desubscreve.
- **[Migração de `Log.sink` quebra os 3 testes se esquecida]** → A change
  inclui a migração desses testes para `addSink`/`removeSink` como tarefa
  explícita; o build falha alto e cedo se faltar.
- **[Thread do log diferente da de render]** → Coberto por D4
  (`synchronized`). Sem isso haveria leitura/escrita concorrente do array.
- **[`removeSink(ConsoleLogSink)` acidental]** → API permite remover o
  console. Aceito: é poder legítimo (um teste pode querer silenciar). Não
  protegemos contra isso; o default re-seeda só na inicialização do `Log`.
