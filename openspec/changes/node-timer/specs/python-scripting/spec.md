## ADDED Requirements

### Requirement: Python scripts can connect to Kotlin-declared Signal fields

When a Kotlin `Node` subclass exposes a public `val` of type `Signal<T>` (e.g. `Timer.timeout: Signal<Unit>`), the Python wrapper for that node SHALL expose the field as an attribute readable from a Python script. Reading the attribute MUST return a Python-side proxy whose `.connect(handler)` and `.disconnect(handler)` methods route to the underlying Kotlin `Signal<T>`. The proxy's `.connect` MUST accept any Python callable; the proxy MUST adapt the callable so that emission from the Kotlin side calls into the Python callable with the emitted value (or with no arguments when `T == Unit`). The lookup of `Signal<*>` fields MUST be reflection-based — Kotlin code SHALL NOT be required to annotate signals with a `@PythonExposed` (or similar) marker for them to be visible. Errors during handler invocation (Python exceptions) MUST propagate out of `Signal.emit` so the engine fails fast at the call site, consistent with current Python script error policy.

#### Scenario: Python connects to Timer.timeout

- **GIVEN** a `Timer` Kotlin instance in a live scene with `waitTime = 0.1f`, `autostart = true`
- **AND** a Python script attached to the parent node calling `timer.timeout.connect(my_handler)` in `_ready`
- **WHEN** the timer emits `timeout`
- **THEN** `my_handler` is invoked with no arguments

#### Scenario: Python disconnect removes the handler

- **GIVEN** a Python script that connected `my_handler` to `timer.timeout`
- **WHEN** the script later calls `timer.timeout.disconnect(my_handler)`
- **THEN** subsequent emissions do NOT invoke `my_handler`

#### Scenario: Reflection discovers Signal fields without annotation

- **WHEN** the Python wrapper for a `Timer` instance is constructed
- **THEN** the attribute `timeout` is available without any `@PythonExposed` (or equivalent) annotation on the Kotlin `val timeout`
- **AND** reading `timer.timeout` returns the proxy bound to the same `Signal<Unit>` instance as `kotlinTimer.timeout`

#### Scenario: Python handler exception propagates

- **GIVEN** a Python handler connected to `timer.timeout` that raises `ValueError("boom")`
- **WHEN** the timer emits `timeout`
- **THEN** the exception propagates out of `Signal.emit` and ultimately crashes the loop with a Python traceback visible to the user

### Requirement: PyI stubs include Timer and TimerMode

The `.pyi` stubs published by `:engine-bundle-python` SHALL include a `Timer` class with the attributes `wait_time: float`, `autostart: bool`, `one_shot: bool`, `process_callback: TimerMode`, `time_left: float`, `is_stopped: bool`, the method `start(override: Optional[float] = None) -> None`, `stop() -> None`, and the attribute `timeout: Signal[None]`. The stubs SHALL include a `TimerMode` enum-like type with the entries `PHYSICS` and `IDLE`. Property names on the Python side MUST follow Python conventions (`snake_case`); the bridge MUST map `snake_case` Python names to the `camelCase` Kotlin property names (`wait_time` ↔ `waitTime`, etc.) consistent with the existing convention used by other native node properties.

#### Scenario: Timer stub is published

- **WHEN** the `:engine-bundle-python` JAR is inspected at `resources/stubs/engine/`
- **THEN** a stub file declaring `class Timer` exists
- **AND** the stub declares `wait_time`, `autostart`, `one_shot`, `process_callback`, `time_left`, `is_stopped`, `timeout`, `start`, `stop`

#### Scenario: TimerMode stub is published

- **WHEN** the same stub source is inspected
- **THEN** a `TimerMode` type with members `PHYSICS` and `IDLE` is declared
