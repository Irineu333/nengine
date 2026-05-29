## ADDED Requirements

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
