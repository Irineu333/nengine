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
