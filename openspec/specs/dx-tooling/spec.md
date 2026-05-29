# dx-tooling Specification

## Purpose

Ferramentas de developer experience embutidas na engine restritas a
**logging**: log estruturado com tags por subsistema, configurável por
tag e globalmente. Toda visualização de debug — FPS, colliders, momentum,
HUD com checkboxes — vive em `com.neoutils.engine.debug.*` e é coberta
pela capability `debug-overlay`; este pacote (`com.neoutils.engine.dx`)
não carrega mais flags de visualização, nem um objeto `Debug`
consolidador, nem helpers de overlay.

## Requirements

### Requirement: Structured log with per-subsystem tags

The engine SHALL provide a logging facility usable from `:engine` and dependent modules, supporting four levels (`Debug`, `Info`, `Warn`, `Error`) and a `tag: String` per call site to identify the subsystem. The logger MUST emit timestamped output. The logger MUST allow the minimum effective level to be configured per tag and globally via `Log.config` (a `LogConfig` instance), without going through a separate `Debug` surface.

The `com.neoutils.engine.dx` package SHALL contain **only** logging utilities (`Log`, `LogConfig`, `LogLevel`, `LogSink`, `ConsoleLogSink`) after this change. All other DX surfaces (FPS counters, collider visualization, momentum overlay) live in `com.neoutils.engine.debug` and are covered by the `debug-overlay` capability.

#### Scenario: Log entries include tag, level, and timestamp

- **WHEN** code calls `Log.d(tag = "Physics", "step took 0.4ms")`
- **THEN** the emitted line contains the tag, the level, a timestamp, and the message

#### Scenario: Level filtering hides lower-priority entries

- **WHEN** `Log.config.globalLevel = LogLevel.Info` is set
- **THEN** subsequent `Log.d(...)` calls produce no output
- **AND** subsequent `Log.i(...)` calls produce output

#### Scenario: Per-tag filter overrides global level

- **WHEN** `Log.config.setTagLevel("Physics", LogLevel.Debug)` is set while `Log.config.globalLevel = LogLevel.Warn`
- **THEN** `Log.d(tag = "Physics", ...)` produces output
- **AND** `Log.d(tag = "Render", ...)` produces no output

#### Scenario: No standalone Debug object exists

- **WHEN** the source under `engine/src/main/kotlin/com/neoutils/engine/dx/` is grep'd for `object Debug`
- **THEN** no match SHALL be returned
- **AND** the only configuration surface for logging SHALL be `Log.config`

### Requirement: Log multiplexes to a registered set of sinks

`Log` SHALL deliver each emitted entry to a set of registered `LogSink`s
rather than to a single sink. The engine SHALL provide:

- `fun addSink(sink: LogSink)` — registers a sink; SHALL be idempotent (the
  same instance SHALL NOT be added twice).
- `fun removeSink(sink: LogSink)` — unregisters a sink; a sink not present
  SHALL be a no-op.

`ConsoleLogSink` SHALL be registered by default so console output is
preserved unless a caller explicitly removes it. The sink set SHALL be
safe for concurrent iteration during `Log.*` emission and mutation via
`addSink`/`removeSink` (e.g. a copy-on-write structure), since `Log.*`
MAY be called from any thread.

The per-tag/global level gate of `Log.config` SHALL be applied **once**,
before fan-out: an entry below `effectiveLevel(tag)` SHALL reach **none**
of the sinks; an entry at or above it SHALL reach **every** registered
sink. The public `var sink` property SHALL NOT exist — the only surface
for managing delivery targets SHALL be `addSink`/`removeSink`.

#### Scenario: Entry fans out to every registered sink

- **GIVEN** two sinks `a` and `b` registered via `Log.addSink`
- **WHEN** `Log.i(tag = "X", "hello")` is called at a level that passes the gate
- **THEN** both `a` and `b` SHALL receive an `emit` with the same level, tag, and message

#### Scenario: ConsoleLogSink is registered by default

- **WHEN** the process starts and no sink management has occurred
- **THEN** `ConsoleLogSink` SHALL be among the registered sinks
- **AND** `Log.i(...)` above the gate SHALL produce console output

#### Scenario: addSink is idempotent

- **GIVEN** a sink `a`
- **WHEN** `Log.addSink(a)` is called twice
- **THEN** a single gated `Log.*` call SHALL invoke `a.emit` exactly once

#### Scenario: removeSink stops delivery

- **GIVEN** a sink `a` registered via `Log.addSink(a)`
- **WHEN** `Log.removeSink(a)` is called and then `Log.i(...)` is emitted above the gate
- **THEN** `a.emit` SHALL NOT be invoked

#### Scenario: Level gate applies before fan-out

- **GIVEN** `Log.config.globalLevel = LogLevel.Info` and a sink `a` registered
- **WHEN** `Log.d(tag = "X", ...)` is called
- **THEN** `a.emit` SHALL NOT be invoked
- **AND** a subsequent `Log.i(tag = "X", ...)` SHALL invoke `a.emit`

#### Scenario: No public sink property exists

- **WHEN** the source under `engine/src/main/kotlin/com/neoutils/engine/dx/` is grep'd for `var sink`
- **THEN** no match SHALL be returned
- **AND** the only surfaces for managing delivery targets SHALL be `addSink` and `removeSink`
