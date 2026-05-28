## 1. Log multi-sink (dx-tooling)

- [ ] 1.1 Em `Log`, substituir o `@Volatile var sink` por `private val sinks = CopyOnWriteArrayList<LogSink>()` semeado com `ConsoleLogSink`.
- [ ] 1.2 Adicionar `fun addSink(sink: LogSink)` idempotente (não duplica a mesma instância) e `fun removeSink(sink: LogSink)` (no-op se ausente).
- [ ] 1.3 Reescrever `log(...)` para aplicar o gate de nível/tag **uma vez** e então iterar `sinks` chamando `emit` em cada um.
- [ ] 1.4 Migrar `LogTest`, `RigidBodyTeleportTest` e `PhysicsSystemTest` do padrão `Log.sink = ...; ... = previous` para `Log.addSink(...)` / `Log.removeSink(...)`.

## 2. Testes de multi-sink

- [ ] 2.1 Teste: fan-out entrega a mesma entrada (level/tag/msg) a todos os sinks registrados.
- [ ] 2.2 Teste: `ConsoleLogSink` registrado por padrão (entry acima do gate produz saída no console).
- [ ] 2.3 Teste: `addSink` idempotente (registrar duas vezes → um `emit` por log).
- [ ] 2.4 Teste: `removeSink` para a entrega ao sink removido.
- [ ] 2.5 Teste: gate de nível aplica antes do fan-out (Debug suprimido com global=Info; Info passa a todos).

## 3. LogOverlayWidget (debug-overlay)

- [ ] 3.1 Criar `data class LogEntry(timestampMillis: Long, level: LogLevel, tag: String, message: String)` em `com.neoutils.engine.debug`.
- [ ] 3.2 Criar `LogOverlayWidget : ScreenDebugWidget(), LogSink` com `title = "Log"`, ring buffer de capacidade `N = 12` (array circular `head`/`size`) e `var minLevel: LogLevel = LogLevel.Debug`.
- [ ] 3.3 Implementar `emit(...)` gravando no ring buffer sob lock dedicado (escrita thread-segura); sobrescreve a mais antiga quando cheio.
- [ ] 3.4 Sobrescrever o setter de `enabled`: `false→true` chama `Log.addSink(this)` e limpa o buffer; `true→false` chama `Log.removeSink(this)`.
- [ ] 3.5 Implementar `drawDebug(renderer)`: snapshot do buffer sob lock, ancorar no canto inferior-esquerdo de `owningTree.size` (re-ancorado por frame), mais recente embaixo, pulando entradas abaixo de `minLevel`, com cor por nível (neutro / âmbar Warn / vermelho Error). Adicionar as cores a `DebugColors` se necessário.

## 4. Registro como 5º built-in

- [ ] 4.1 Adicionar `val log: LogOverlayWidget = LogOverlayWidget()` em `DebugRegistry`.
- [ ] 4.2 Em `DebugRegistry.bindLayer`, registrar os cinco na ordem `fps, colliders, momentum, log, hud`.

## 5. Testes do overlay e integração

- [ ] 5.1 Teste: habilitar subscreve como sink e limpa o buffer; desabilitar desubscreve e para de gravar.
- [ ] 5.2 Teste: ring buffer mantém só as últimas `N` entradas, em ordem de emissão (wrap em `N + k`).
- [ ] 5.3 Teste: cor por nível (Warn âmbar, Error vermelho) via `Renderer` gravador.
- [ ] 5.4 Teste: `minLevel` filtra a exibição (só Warn+Error desenhados com `minLevel = Warn`).
- [ ] 5.5 Teste: overlay desabilitado emite zero draws.
- [ ] 5.6 Teste: `tree.debug.log` não-nulo após `start()`, presente em `widgets`, e `parent == ScreenDebugCanvas`.
- [ ] 5.7 Teste: o `DebugHud` lista a row do overlay (uma row por widget em `widgets` exceto o próprio HUD → cinco→ inclui "Log").

## 6. Fechamento

- [ ] 6.1 Rodar a suíte de testes do `:engine` e garantir verde.
- [ ] 6.2 `openspec validate debug-log-overlay --strict` e revisar coerência specs↔implementação.
