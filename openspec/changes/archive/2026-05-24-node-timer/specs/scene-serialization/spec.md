## ADDED Requirements

### Requirement: PropCoercion supports enum target types via string name

`PropCoercion` (in `:engine-bundle`) SHALL coerce JSON string values into Kotlin `enum class` targets when an `@Inspect` property or a script export declares an enum type. The coercion MUST call `<EnumClass>.valueOf(jsonString)` using the exact string as written in the JSON. If `valueOf` throws (string does not match any enum entry), the coercion MUST re-throw with a message that includes the offending property name, the offending string value, the enum class name, and the comma-separated list of valid enum entry names. The rule MUST be generic — any `enum class` reachable through reflection on the target type is supported — not specialized to a single enum.

#### Scenario: Enum coerced from string

- **GIVEN** an `@Inspect var mode: TimerMode = TimerMode.PHYSICS` on a Node
- **AND** a `scene.json` `properties` bag containing `"mode": "IDLE"`
- **WHEN** `SceneLoader` routes the property through `PropCoercion`
- **THEN** the property is set to `TimerMode.IDLE`

#### Scenario: Unknown enum value fails fast with actionable message

- **GIVEN** an `@Inspect var mode: TimerMode` on a Node named `"MyTimer"`
- **AND** a `scene.json` `properties` bag containing `"mode": "physics"` (wrong case)
- **WHEN** `SceneLoader` routes the property through `PropCoercion`
- **THEN** loading fails with a message that mentions `"MyTimer"`, `"mode"`, `"physics"`, `TimerMode`, and lists `PHYSICS, IDLE` as valid values

#### Scenario: Coercion is generic over enum classes

- **GIVEN** any `enum class E { A, B }` declared on a Node `@Inspect var` or a script export
- **WHEN** a JSON string `"A"` is coerced for that property
- **THEN** the result is `E.A` without `PropCoercion` requiring a hardcoded case for `E`
