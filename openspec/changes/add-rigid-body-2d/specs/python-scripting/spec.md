## MODIFIED Requirements

### Requirement: Engine types are pre-bound in the Polyglot Context

`PythonScriptHost` MUST registrar bindings no Polyglot Context para que scripts referenciem tipos da engine sem `import`. Os bindings MUST incluir, no mínimo: `Vec2`, `Rect`, `Color`, `Transform`, `Key`, `MouseButton`, `NodeRef`, `Signal`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D`. Os bindings de física antigos (`BoxCollider`, `Collider`) MUST NOT estar presentes. Adicionalmente, MUST expor uma factory function `signal(typeHint=None)` que constrói uma instância `Signal` (typeHint é apenas documentação, ignorada runtime). Os bindings MUST estar disponíveis dentro do top-level dos scripts (para AnnAssign `points: Polygon2D = ...` ou declarações `my_signal: Signal = signal(str)`) e dentro dos hooks (para uso em runtime).

#### Scenario: Binding RigidBody2D is exposed in the Context

- **WHEN** o `PythonScriptHost` é inicializado e um script declara `# extends RigidBody2D`
- **THEN** o parse do `# extends` resolve `RigidBody2D` contra o `NodeRegistry` com sucesso
- **AND** dentro do corpo do script, referenciar `RigidBody2D` como tipo (e.g. em annotated assignment) não levanta `NameError`

#### Scenario: Other pre-bound types remain available

- **WHEN** o `PythonScriptHost` é inicializado
- **THEN** os bindings `Vec2`, `Rect`, `Color`, `Transform`, `Key`, `MouseButton`, `NodeRef`, `Signal`, `signal`, `Node2D`, `Camera2D`, `Label`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `CollisionObject2D`, `Area2D`, `PhysicsBody2D`, `StaticBody2D`, `CharacterBody2D`, `CollisionShape2D`, `Shape2D`, `RectangleShape2D`, `CircleShape2D` continuam todos disponíveis no Context

### Requirement: Extends declarations support new collision types

A primeira linha não-vazia, não-comentário de um script Python que **não** seja `# extends <Type>` MUST falhar com mensagem clara nomeando o erro e o path do script. O tipo após `# extends` MUST resolver via `NodeRegistry`. Tipos suportados incluem `Node2D`, `Area2D`, `StaticBody2D`, `CharacterBody2D`, `RigidBody2D`, `CollisionShape2D`, `Camera2D`, `ColorRect`, `Circle2D`, `Line2D`, `Polygon2D`, `Label`, `Timer`, e qualquer subclasse de `Node` registrada.

#### Scenario: A script extending RigidBody2D loads and attaches

- **GIVEN** um script `ball.py` cuja primeira linha é `# extends RigidBody2D` e que define exports e hooks legítimos
- **WHEN** o `PythonScriptHost.load` parseia o script e o `BundleLoader` o anexa a um Node `RigidBody2D` carregado de `scene.json`
- **THEN** o attach completa sem erros
- **AND** os hooks `_ready`, `_physics_process`, `_on_body_entered`, etc. ficam disponíveis para a engine invocar

#### Scenario: A script with unknown extends type fails fast

- **GIVEN** um script cuja primeira linha é `# extends UnknownType123`
- **WHEN** `PythonScriptHost.load` é chamado
- **THEN** o load falha com mensagem que nomeia `UnknownType123` e o path do script

## ADDED Requirements

### Requirement: RigidBody2D Python scripts expose force, impulse, and velocity APIs

Quando o tipo nativo do Node anexado é `RigidBody2D`, o script Python MUST poder:

- Ler e escrever `self.linear_velocity: Vec2`.
- Ler e escrever `self.angular_velocity: float`.
- Ler e escrever `self.mass: float`, `self.inertia: float`, `self.restitution: float`, `self.friction: float`, `self.gravity_scale: float`, `self.linear_damping: float`, `self.angular_damping: float`.
- Chamar `self.apply_force(force: Vec2)`, `self.apply_impulse(impulse: Vec2)`, `self.apply_force_at(force: Vec2, world_point: Vec2)`, `self.apply_impulse_at(impulse: Vec2, world_point: Vec2)`, `self.apply_torque(torque: float)`. Aliases `apply_central_force` (= `apply_force`) e `apply_central_impulse` (= `apply_impulse`) MUST ser expostos para conformidade nominal com Godot.

Escrever `self.linear_velocity.x = X` deve falhar com `AttributeError` (consistência com o invariante de `Vec2.x` ser `val` Kotlin); o idioma correto é `self.linear_velocity = Vec2(X, self.linear_velocity.y)`. Hooks `_on_body_entered(self, body)` e `_on_area_entered(self, area)` continuam sendo disparados via `PhysicsSystem` enter/exit dispatch normalmente.

#### Scenario: A Python RigidBody2D script applies an impulse

- **GIVEN** um Node `RigidBody2D` com script Python que em `_physics_process(self, dt)` chama `self.apply_central_impulse(Vec2(10.0, 0.0))`
- **WHEN** o physics tick é executado
- **THEN** após o tick, `self.linear_velocity.x` aumenta em `10.0 / self.mass`

#### Scenario: Reading and writing linear_velocity from Python

- **GIVEN** um Node `RigidBody2D` com script Python
- **WHEN** o script executa `vel = self.linear_velocity` e em seguida `self.linear_velocity = Vec2(0.0, 0.0)`
- **THEN** `vel` reflete o valor antes da escrita
- **AND** `self.linear_velocity` após a escrita é `Vec2(0.0, 0.0)`

#### Scenario: Component assignment to linear_velocity raises AttributeError

- **GIVEN** um Node `RigidBody2D` com script Python
- **WHEN** o script executa `self.linear_velocity.x = 5.0`
- **THEN** Python levanta `AttributeError` (consistência com o protocolo de imutabilidade de `Vec2`)

### Requirement: PyI stubs include RigidBody2D and its force API

Os arquivos `.pyi` publicados em `engine-bundle-python/src/main/resources/stubs/engine/` MUST incluir uma declaração de classe `RigidBody2D` herdando de `PhysicsBody2D`, com as properties `linear_velocity`, `angular_velocity`, `mass`, `inertia`, `restitution`, `friction`, `gravity_scale`, `linear_damping`, `angular_damping` e os métodos `apply_force`, `apply_impulse`, `apply_central_force`, `apply_central_impulse`, `apply_force_at`, `apply_impulse_at`, `apply_torque`. Tipos devem refletir as assinaturas Kotlin (`Vec2` para vetores, `float` para escalares).

#### Scenario: PyI stubs declare RigidBody2D

- **WHEN** o arquivo de stub `engine/__init__.pyi` (ou equivalente) é inspecionado
- **THEN** ele contém uma classe `RigidBody2D(PhysicsBody2D)` com pelo menos os métodos `apply_force`, `apply_impulse`, `apply_central_impulse`, `apply_torque`
- **AND** as properties `linear_velocity: Vec2`, `angular_velocity: float`, `mass: float`, `restitution: float`, `friction: float` aparecem na classe
