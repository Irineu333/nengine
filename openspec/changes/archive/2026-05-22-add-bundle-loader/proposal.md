## Why

O carregamento de cena hoje é verboso e ergonomicamente ruim: o `Main.kt` do Pong precisa registrar tipos da engine na mão, instanciar um `KotlinScriptingHost` com manifesto ordenado topologicamente, registrar o host na global `ScriptHosts`, e ainda buscar o JSON pelo `classLoader` antes de chamar `SceneLoader.load`. Além disso, a abstração de "scripting" como módulo independente perdeu sentido: scripts só fazem sentido no contexto de um pacote de jogo (cena + scripts + futuros assets). A fronteira correta é o **bundle**, não o "scripting".

## What Changes

- **Novo módulo `:engine-bundle`** com a API `BundleLoader.fromResources(name)` e `BundleLoader.fromPath(file)`, retornando uma `Scene` pronta para entregar ao `GameHost`.
- **Formato "scene bundle"**: pasta convencional com `scene.json` (raiz), `scripts/*.nengine.kts` (opcional) e `assets/` (reservado para o futuro). Paths de scripts no JSON passam a ser relativos ao bundle.
- **BREAKING**: módulo `:engine-scripting` é **deletado**. Sua funcionalidade migra para `:engine-bundle`.
- **BREAKING**: SPI `ScriptHost` / `ScriptHosts` é removida de `:engine`. Vira detalhe interno de `:engine-bundle`.
- **BREAKING**: `NodeRegistry` vira bidirecional (`identifier ↔ KClass + factory`). Tanto tipos compilados (FQN) quanto scripts (`scripts/foo.nengine.kts`) são registrados pelo mesmo mecanismo. `SceneLoader.load`/`save` perdem o ramo especial `endsWith(".kts")`.
- **Descoberta de scripts via tree-walk** no JSON antes da instanciação: o loader coleciona todo `type` que termina em `.nengine.kts`, compila apenas o necessário, e registra no `NodeRegistry`.
- **Compilação round-robin / fixed-point** dentro do `KotlinScriptingHost`: a ordem manual do manifesto desaparece. O host tenta compilar cada script com os símbolos já resolvidos; defere os que falham por símbolo desconhecido (de outro script); itera até estabilizar; detecta ciclo com erro claro.
- **Cache de bytecode robusto**: chave passa a incluir `SHA256(source) ⊕ SHA256(importSet ordenado) ⊕ engineVersion`. Bytecode órfão (scripts deletados) é limpo no bootstrap.
- **API aceita `types: List<KClass<out Node>>`** para tipos custom de jogo, instanciados via reflection sobre construtor no-args. `NodeRegistry.registerEngineTypes()` é chamado idempotentemente dentro do loader.
- **Migração do Pong**: `pong.scene.json` + `scripts/` viram bundle `pong/scene.json` + `pong/scripts/` (em `resources/`). `Main.kt` colapsa para ~6 linhas usando `BundleLoader.fromResources("pong")`.
- **`SceneLoader.load(text)`/`save(scene)` permanecem em `:engine`** como API low-level orientada a texto (testes, REPL, casos avançados).

## Capabilities

### New Capabilities

- `bundle-loading`: módulo `:engine-bundle`, formato de bundle (`scene.json` + `scripts/` + `assets/`), API `BundleLoader.fromResources` / `fromPath`, descoberta de scripts via tree-walk, compilação round-robin com fixed-point, cache de bytecode bug-proof, registro idempotente de tipos da engine, e aceitação de tipos custom via `List<KClass>`.

### Modified Capabilities

- `scripting`: a SPI `ScriptHost` / `ScriptHosts` deixa de viver em `:engine`. O módulo `:engine-scripting` é removido. O contrato de "compilar `.nengine.kts` para `KClass<out Node>`" permanece, agora interno a `:engine-bundle`. Cenário de pública-SPI-em-`:engine` cai.
- `scene-serialization`: `NodeRegistry` ganha mapeamento bidirecional (`identifierFor(klass)`), `SceneLoader.load`/`save` deixam de discriminar `.kts` e passam a usar exclusivamente o `NodeRegistry`. `SceneLoader.save` deixa de consultar `ScriptHosts`.
- `pong-sample`: cena passa a ser servida como bundle (`resources/pong/scene.json`, `resources/pong/scripts/`). `Main.kt` usa `BundleLoader.fromResources("pong")`. Manifesto manual e registro manual de tipos são removidos.
- `project-conventions`: tabela de módulos em `CLAUDE.md` troca `:engine-scripting` por `:engine-bundle`; roadmap registra a change como Active → Archived ao concluir.

## Impact

- **Código afetado**: `engine/src/main/kotlin/com/neoutils/engine/scripting/` (deletado); `engine/src/main/kotlin/com/neoutils/engine/serialization/SceneLoader.kt` e `NodeRegistry.kt` (refator); `engine-scripting/` (módulo inteiro deletado); novo módulo `engine-bundle/`; `games/pong/src/main/kotlin/.../Main.kt` (colapsa); `games/pong/src/main/resources/` (rearranjo para layout de bundle).
- **APIs públicas**: introduz `com.neoutils.engine.bundle.BundleLoader`; remove `com.neoutils.engine.scripting.ScriptHost` / `ScriptHosts` de `:engine`; `NodeRegistry` ganha `identifierFor(klass)` e `register` com identifier explícito.
- **Build**: `settings.gradle.kts` perde `:engine-scripting`, ganha `:engine-bundle`; `games/pong/build.gradle.kts` troca a dependência correspondente.
- **Documentação**: `CLAUDE.md` (tabela de módulos, roadmap) e specs OpenSpec.
- **Testes**: `KotlinScriptingHostTest` migra para `:engine-bundle` e ganha casos de cross-refs, ciclo e invalidação de cache por mudança de import set; novo `BundleLoaderTest` (classpath + filesystem); `SceneLoaderTest` simplifica (sem caminho `.kts`); `ScriptHostsTest` (em `:engine`) é removido.
- **Risco**: cache stale entre runs caso a invalidação por import-set não seja implementada corretamente — coberto por teste dedicado. Edge case do round-robin: distinguir "erro real do autor" de "símbolo ainda não resolvido" pode produzir mensagens confusas se mal implementado — design.md detalha o critério.
