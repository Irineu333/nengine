## REMOVED Requirements

### Requirement: A script defines exactly one top-level Node subclass

**Reason**: O backend de Kotlin Scripting (`.nengine.kts`) é eliminado. Scripts deixam de ser classes Kotlin top-level e passam a ser módulos Python anexados a Nodes nativos via slot único. O contrato "uma classe top-level que estende Node" não tem análogo no novo modelo Godot-like.

**Migration**: O contrato comportamental equivalente vive agora em `script-host` ("Script declares which Node type it extends"). Cada `Script` declara `extendsType: KClass<out Node>` e os exports são descobertos estaticamente sem rodar o módulo. A "única classe top-level" vira "um módulo `.py` por script com declaração `extends` na primeira linha".

### Requirement: ScriptDefinition pre-imports the engine API

**Reason**: `ScriptDefinition` é um mecanismo específico de Kotlin Scripting que não tem análogo direto em Python. Pré-imports continuam existindo, mas via bindings injetados no `Context` Polyglot em vez de via configuração do script-definition Kotlin.

**Migration**: Pré-imports do engine para Python estão em `python-scripting` ("Engine types are pre-bound in the Polyglot Context"). Os mesmos tipos (`Vec2`, `Color`, `Rect`, `NodeRef`, `Key`, `BoxCollider`, `Node2D`) ficam disponíveis no namespace global de qualquer `.py`.

### Requirement: Script errors crash the process fail-fast

**Reason**: O capability `scripting` é deletado integralmente. Esta semântica é central ao novo modelo e migra para o capability `script-host`.

**Migration**: Veja `script-host` → "Script errors propagate fail-fast". O contrato é idêntico em espírito (qualquer falha — não encontrado, erro de parse, hook explode — propaga até o caller sem placeholder) e agora se aplica a qualquer implementação de `ScriptHost`, não apenas à de Kotlin Scripting.
