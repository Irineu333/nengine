## 1. Log multi-sink (dx-tooling)

- [x] 1.1 Em `Log`, substituir o `@Volatile var sink` por `private val sinks = CopyOnWriteArrayList<LogSink>()` semeado com `ConsoleLogSink`.
- [x] 1.2 Adicionar `fun addSink(sink: LogSink)` idempotente (não duplica a mesma instância) e `fun removeSink(sink: LogSink)` (no-op se ausente).
- [x] 1.3 Reescrever `log(...)` para aplicar o gate de nível/tag **uma vez** e então iterar `sinks` chamando `emit` em cada um.
- [x] 1.4 Migrar `LogTest`, `RigidBodyTeleportTest` e `PhysicsSystemTest` do padrão `Log.sink = ...; ... = previous` para `Log.addSink(...)` / `Log.removeSink(...)`.

## 2. Testes de multi-sink

- [x] 2.1 Teste: fan-out entrega a mesma entrada (level/tag/msg) a todos os sinks registrados.
- [x] 2.2 Teste: `ConsoleLogSink` registrado por padrão (entry acima do gate produz saída no console).
- [x] 2.3 Teste: `addSink` idempotente (registrar duas vezes → um `emit` por log).
- [x] 2.4 Teste: `removeSink` para a entrega ao sink removido.
- [x] 2.5 Teste: gate de nível aplica antes do fan-out (Debug suprimido com global=Info; Info passa a todos).

## 3. LogOverlayWidget (debug-overlay)

- [x] 3.1 Criar `data class LogEntry(timestampMillis: Long, level: LogLevel, tag: String, message: String)` em `com.neoutils.engine.debug`.
- [x] 3.2 Criar `LogOverlayWidget : ScreenDebugWidget(), LogSink` com `title = "Log"`, ring buffer de capacidade `N = 12` (array circular `head`/`size`) e `var minLevel: LogLevel = LogLevel.Debug`.
- [x] 3.3 Implementar `emit(...)` gravando no ring buffer sob lock dedicado (escrita thread-segura); sobrescreve a mais antiga quando cheio.
- [x] 3.4 Sobrescrever o setter de `enabled`: `false→true` chama `Log.addSink(this)` e limpa o buffer; `true→false` chama `Log.removeSink(this)`.
- [x] 3.5 Implementar `drawDebug(renderer)`: snapshot do buffer sob lock, ancorar no canto inferior-esquerdo de `owningTree.size` (re-ancorado por frame), mais recente embaixo, pulando entradas abaixo de `minLevel`, com cor por nível (neutro / âmbar Warn / vermelho Error). Adicionar as cores a `DebugColors` se necessário.

## 4. Registro como 5º built-in

- [x] 4.1 Adicionar `val log: LogOverlayWidget = LogOverlayWidget()` em `DebugRegistry`.
- [x] 4.2 Em `DebugRegistry.bindLayer`, registrar os cinco na ordem `fps, colliders, momentum, log, hud`.

## 5. Testes do overlay e integração

- [x] 5.1 Teste: habilitar subscreve como sink e limpa o buffer; desabilitar desubscreve e para de gravar.
- [x] 5.2 Teste: ring buffer mantém só as últimas `N` entradas, em ordem de emissão (wrap em `N + k`).
- [x] 5.3 Teste: cor por nível (Warn âmbar, Error vermelho) via `Renderer` gravador.
- [x] 5.4 Teste: `minLevel` filtra a exibição (só Warn+Error desenhados com `minLevel = Warn`).
- [x] 5.5 Teste: overlay desabilitado emite zero draws.
- [x] 5.6 Teste: `tree.debug.log` não-nulo após `start()`, presente em `widgets`, e `parent == ScreenDebugCanvas`.
- [x] 5.7 Teste: o `DebugHud` lista a row do overlay (uma row por widget em `widgets` exceto o próprio HUD → cinco→ inclui "Log").

## 6. Fechamento

- [x] 6.1 Rodar a suíte de testes do `:engine` e garantir verde.
- [x] 6.2 `openspec validate debug-log-overlay --strict` e revisar coerência specs↔implementação.

## 7. Bugs corrigidos pós-implementação

- [x] 7.1 **Overflow na borda inferior** (visto na validação manual via `:games:demos:runLwjgl`). `position.y` do `drawText` é a borda **superior** do texto em ambos os backends (Skiko/LWJGL), então ancorar a linha mais recente em `size.y - PADDING` fazia o glifo descer abaixo da tela. Fix: recuar a base em uma `LINE_HEIGHT` (`newestTop = size.y - PADDING - LINE_HEIGHT`) para a linha mais recente caber inteira acima do padding. Cobre com teste de regressão `lines stay within the bottom edge of the screen`.
