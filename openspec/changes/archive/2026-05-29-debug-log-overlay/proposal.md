## Why

Os dois pilares de debug da engine não se falam: `dx-tooling` emite log
estruturado (`Log.d/i/w/e` com tag e nível) que só sai no console, e
`debug-overlay` desenha gizmos/HUD na tela sem nenhuma noção de log. Ao
rodar um jogo — especialmente via Skiko/LWJGL em janela, sem terminal à
vista — não há como ver as últimas mensagens de log sem alt-tab para o
console. Um overlay on-screen das entradas recentes fecha essa lacuna e é
o item mais barato do bloco de ferramentas de debug planejado, reusando a
infra de `ScreenDebugWidget` + `LogSink` que já existe.

## What Changes

- **`Log` passa a suportar múltiplos sinks.** Hoje `Log.sink` é um único
  `var`; instalar um overlay como sink deslocaria o `ConsoleLogSink`. A
  change introduz `Log.addSink(sink)` / `Log.removeSink(sink)` com
  fan-out para todos os sinks registrados. `ConsoleLogSink` permanece
  registrado por padrão; `Log.sink` é mantido como atalho do sink
  primário para compatibilidade (ver design para a decisão exata).
- **Novo built-in `LogOverlayWidget`** em `com.neoutils.engine.debug`,
  extends `ScreenDebugWidget`. Mantém um ring buffer das últimas N
  entradas, registra-se como `LogSink` quando `enabled` vira `true` e se
  desregistra quando vira `false`. Desenha as linhas num canto da tela,
  com cor por `LogLevel` (Debug/Info neutro, Warn âmbar, Error vermelho).
- **`DebugRegistry` ganha o campo `log: LogOverlayWidget`** e passa a
  auto-registrar **cinco** built-ins (FPS, Colliders, Momentum, Log, HUD)
  em vez de quatro. O `LogOverlayWidget` aparece como mais uma row
  togglável no `DebugHud`.
- **Filtro de exibição por nível mínimo** no overlay (independente do
  `Log.config`, que continua gateando o que chega aos sinks). Filtro por
  tag fica como menção de escopo futuro, não MVP.

Nenhuma mudança quebra API existente: `Log.d/i/w/e`, `Log.config` e o
comportamento do `ConsoleLogSink` permanecem idênticos.

## Capabilities

### New Capabilities
<!-- Nenhuma. O overlay é mais um built-in da capability debug-overlay existente,
     especificado no mesmo molde de ColliderWidget/MomentumWidget. -->

### Modified Capabilities

- `dx-tooling`: `Log` deixa de ter um único `sink` e passa a multiplexar
  para um conjunto de sinks via `addSink`/`removeSink`, preservando
  `ConsoleLogSink` por padrão e o gating por nível/tag de `Log.config`.
- `debug-overlay`: novo built-in `LogOverlayWidget` (ring buffer,
  subscrição como `LogSink` gated por `enabled`, cor por nível, filtro de
  nível mínimo de exibição); `DebugRegistry` expõe `val log` e
  auto-registra cinco built-ins; o `DebugHud` lista o overlay como row.

## Impact

- **Código afetado:**
  - `:engine` `com.neoutils.engine.dx.Log` — multi-sink (lista
    thread-safe de sinks; `addSink`/`removeSink`; fan-out no `log`).
  - `:engine` `com.neoutils.engine.debug` — novo `LogOverlayWidget`;
    `DebugRegistry.log` + auto-registro; ajuste de `DebugHud` é
    automático (já enumera `tree.debug.widgets`).
- **Threading:** `Log` pode ser chamado de qualquer thread; o ring buffer
  do overlay é escrito no `emit` (thread do caller) e lido no `drawDebug`
  (thread de render). Precisa de publicação segura — detalhe no design.
- **Per-tree vs process-wide:** logs são globais (não atrelados a um
  `SceneTree`); com múltiplas árvores, cada overlay habilitado espelha o
  mesmo stream global. Comportamento aceitável, documentado no spec.
- **Testes:** multi-sink fan-out e remoção; subscrição/desubscrição do
  overlay gated por `enabled`; ring buffer com wrap; cor por nível; gating
  do filtro de exibição. Sem novas dependências externas.
