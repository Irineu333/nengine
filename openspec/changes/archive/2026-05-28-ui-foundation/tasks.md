## 1. Engine — CanvasLayer + render passes

- [x] 1.1 Adicionar `CanvasLayer : Node` em `engine/src/main/kotlin/com/neoutils/engine/scene/CanvasLayer.kt` com `@Inspect var layer: Int = 0`, `@Serializable`, e public no-args constructor.
- [x] 1.2 Registrar `CanvasLayer` em `NodeRegistry` (FQN `com.neoutils.engine.scene.CanvasLayer`, mesma convenção dos outros nodes shipped).
- [x] 1.3 Refatorar `SceneTree.render(renderer)` para executar dois passes: (a) world pass que pula CanvasLayer subtrees inteiras, (b) UI pass que coleta CanvasLayers em DFS pre-order, sort por `(layer asc, dfs-order asc)`, e walka cada subtree a partir de identity transform.
- [x] 1.4 Garantir que `Renderer.pushTransform`/`popTransform` continuam sendo emitidos para cada `Node2D` descendente de `CanvasLayer`.
- [x] 1.5 Testes unitários em `:engine` cobrindo: (a) world pass pula CanvasLayer; (b) UI pass desenha CanvasLayer em screen-space (sem view transform); (c) ordering `(layer, dfs-order)` é estável; (d) nested CanvasLayer renderiza uma vez na ordem global.

## 2. Engine — Input consumed flag + UI hit-test phase

- [x] 2.1 Adicionar `var mouseClickConsumed: Boolean` e método `wasMouseClickedRaw(button: MouseButton): Boolean` à interface `Input`.
- [x] 2.2 Atualizar `wasMouseClicked(button)` para retornar `false` quando `mouseClickConsumed = true` (left button MVP) — implementado como default na interface.
- [x] 2.3 Resetar `mouseClickConsumed = false` no início de `SceneTree.hitTestUI` (centralizado na engine, não no backend — simplifica os hosts).
- [x] 2.4 Adicionar método `SceneTree.hitTestUI(input: Input)` que: se `wasMouseClickedRaw(Left)`, coleta CanvasLayers em ordem reversa de `(layer, dfs-order)`, walka cada um em reverse DFS, e para o primeiro `Button` habilitado cujo rect screen-space contém `input.pointerPosition` arma o press e seta `mouseClickConsumed = true`.
- [x] 2.5 Atualizar `GameLoop.tick(...)` para invocar `tree.hitTestUI(input)` entre `tree.input = input` e o loop de physics/process.
- [x] 2.6 Testes unitários em `:engine` cobrindo: (a) click consumido inverte retorno de `wasMouseClicked`; (b) `wasMouseClickedRaw` sempre vê o bruto; (c) `mouseClickConsumed` reseta a cada tick; (d) top-most CanvasLayer ganha em overlap; (e) drag-out cancela; (f) disabled suprime.

## 3. Engine — Panel and Button nodes

- [x] 3.1 Criar `Panel : Node2D` em `engine/src/main/kotlin/com/neoutils/engine/scene/Panel.kt` com `@Inspect var size: Vec2`, `color: Color`, `border: Border?`; `Border` é `data class Border(val color: Color, val width: Float)`; registrar em `NodeRegistry`.
- [x] 3.2 Implementar `Panel.onDraw` desenhando `drawRect(rect, color, filled = true)` e, se `border != null`, `drawRect(rect, border.color, filled = false)` em seguida.
- [x] 3.3 Criar `Button : Node2D` em `engine/src/main/kotlin/com/neoutils/engine/scene/Button.kt` com `@Inspect var size: Vec2`, `text: String`, `textSize`, `textColor`, `normalColor`/`hoverColor`/`pressedColor`/`disabledColor`, `disabled: Boolean`; `@Transient` internal state (`hovered`, `armed`); built-in `val pressed = Signal<Unit>()`; registrar em `NodeRegistry`.
- [x] 3.4 Implementar `Button.onProcess(dt)`: ler `input.pointerPosition`, atualizar `hovered` via `screenRect().contains(...)`; gerenciar `armed` quando mouse-up dentro emite `pressed`, fora cancela. Respeitar `disabled`.
- [x] 3.5 Implementar `Button.onDraw`: cor por estado (`disabledColor`/`pressedColor`/`hoverColor`/`normalColor`); `text` centralizado via `measureText`.
- [x] 3.6 `SceneTree.hitTestUI` chama `button.armPress()` quando encontra hit — `Button.onProcess` resolve no release.
- [x] 3.7 Testes unitários cobrindo: (a) click cycle emite uma vez; (b) drag-out cancela; (c) `disabled` ignora; (d) consumed semantics; (e) no re-emit after release.

## 4. Engine — tree.debug + DebugOverlayLayer + auto-insert

- [x] 4.1 Criar `DebugFlags` em `engine/src/main/kotlin/com/neoutils/engine/tree/DebugFlags.kt` com `var showFps`, `showColliders`, `showMomentum`, `currentFps: Float`, defaults `false`/`0f`. Não-`Serializable`, não-`Node`.
- [x] 4.2 Adicionar `val debug: DebugFlags = DebugFlags()` a `SceneTree`.
- [x] 4.3 Criar `DebugOverlayLayer : CanvasLayer` em `engine/src/main/kotlin/com/neoutils/engine/scene/DebugOverlayLayer.kt` com `layer = Int.MAX_VALUE - 1`, nome estável `"__debug"`.
- [x] 4.4 Implementar nodes filhos `FpsLabel` (lê `tree.debug.currentFps`), `ColliderOverlay` (walk world desenhando AABBs de `CollisionObject2D` com camera view transform local), `MomentumOverlayNode` (delega para singleton `MomentumOverlay.renderOverlay`).
- [x] 4.5 Fazer `SceneTree` auto-inserir `DebugOverlayLayer` na construção (idempotente).
- [x] 4.6 Canal definido: `tree.debug.currentFps: Float` — host escreve, `FpsLabel` lê.
- [x] 4.7 Testes unitários cobrindo: (a) auto-insert presente e idempotente; (b) defaults false; (c) zero draws quando flags off; (d) `showFps=true` emite texto FPS; (e) `showColliders=true` desenha Area2D em verde; (f) Button ignorado (não é CollisionObject); (g) ColliderOverlay aplica camera view transform local. Removido `DebugOverlayTest.kt` (testava função apagada).

## 5. Engine — GameConfig + invariantes de host

- [x] 5.1 `toggleMomentumOverlayKey` já existe em `GameConfig` (default `Key.F3`).
- [x] 5.2 Spec engine-core delta já reflete `tree.debug.*`.
- [ ] 5.3 (Adiar) Teste arquitetural de invariante `renderer.draw*` fora de `tree.render` nos hosts — manter como verificação manual via grep. Quando `code-review` rodar, captura.

## 6. Skiko backend — esvaziar host render

- [x] 6.1 Removido qualquer `renderer.draw*` no `onRender` fora de `loop.tick`.
- [x] 6.2 Removido import + uso de `renderDebugOverlay`; função em si deletada do `:engine`.
- [x] 6.3 `onRender`: `input.beginTick` → `tree.debug.currentFps = fps.record(...)` → `tree.resize` → poll de F1/F2/F3 flippando `tree.debug.*` (com `MomentumOverlay.reset()` no ligar) → `renderer.bind` → `clear` + `loop.tick` → `unbind` → `needRedraw`.
- [x] 6.4 (Adiar) Test arquitetural — manter como verificação manual.

## 7. LWJGL backend — esvaziar host render

- [x] 7.1 Removido qualquer `renderer.draw*` no main loop fora de `loop.tick`.
- [x] 7.2 Removido import + uso de `renderDebugOverlay`.
- [x] 7.3 Main loop: `beginTick` → `glfwPollEvents` → `tree.debug.currentFps = fps.record(...)` → `tree.resize` → poll dos 3 toggle keys flippando `tree.debug.*` → `glViewport` → `renderer.bind` → `clear` + `loop.tick` → `unbind` → `glfwSwapBuffers`.
- [x] 7.4 `MomentumOverlay.reset()` continua sendo chamado no host quando a flag flipa para `true` (responsabilidade pequena suficiente para ficar no host por enquanto).

## 8. Demos — cena 7 (UI playground)

- [x] 8.1 `UiPlaygroundDemo : Node` em `games/demos/.../UiPlaygroundDemo.kt`: background `ColorRect` world-space; HUD `CanvasLayer` `layer=0` com Panel translúcido + Score + Lives Labels; Menu `CanvasLayer` `layer=10` com 3 Buttons centralizados (Start, Settings disabled, Quit).
- [x] 8.2 Handlers Kotlin via `pressed.connect { Log.i("UiPlayground", "...") }` — substituto pragmático aos scripts Python (demos é all-Kotlin code-only; o caminho Python é coberto por outros jogos shipped).
- [x] 8.3 Wirear no `DemoSwitcherRoot`: tecla `7` → `Slot.UiPlayground`. HudOverlay texto atualizado pra mencionar "7. UI playground".
- [x] 8.4 Validação manual Skiko OK: tela renderiza, 7 cenas alternam via 1–7, hover/press/disabled visíveis na cena 7, HUD bottom-left e botões centralizados sobrevivem ao resize (re-layout em `onProcess`).
- [x] 8.5 Validação manual LWJGL OK (`runLwjgl`).

## 9. Migração — hello-world

- [x] 9.1 `:games:hello-world/Main.kt` agora monta `CanvasLayer { addChild(CenteredLabel { ... }) }` como root.
- [x] 9.2 `CenteredLabel.onDraw` continua funcionando — usa `tree?.size`, sem mudança necessária (UI pass entrega identity transform pro CanvasLayer).
- [x] 9.3 Validação manual OK.

## 10. Migração — pong

- [x] 10.1 `pong/scene.json`: leftScore e rightScore movidos pra dentro de novo `CanvasLayer` `Hud` (`layer=0`); posições preservadas.
- [x] 10.2 `pong_scene.py._wire_scoring` ajustado pra buscar scores via `Hud` (`self._node.findChild("Hud").findChild("leftScore")`).
- [x] 10.3 Validação manual OK.

## 11. Migração — snake

- [x] 11.1 `snake/scene.json`: ScoreLabel e GameOverLabel agora filhos de `CanvasLayer` `Hud`.
- [x] 11.2 `score.py` usa `self._node.parent.parent.findChild("Snake")` (sobe dois níveis: Hud → root); `gameover.py` idem + remove dependência de `Camera2D.bounds` (centraliza via `tree.size`).
- [x] 11.3 Validação manual OK.

## 12. Migração — tictactoe

- [x] 12.1 `tictactoe/scene.json`: `status` Label dentro de `CanvasLayer` `Hud` (`layer=0`).
- [x] 12.2 `board.lua` usa `NodeRef("Hud/status"):resolve(self.node)` (path slash-separated).
- [x] 12.3 Validação manual OK.

## 13. Stubs Python e Lua

- [x] 13.1 `engine-bundle-python/.../stubs/engine/scene.pyi` ganha `CanvasLayer`, `Panel`, `Border`, `Button` com seus campos + `pressed: Signal`. `__init__.pyi` reexporta.
- [x] 13.2 `engine-bundle-lua/.../stubs/engine/nengine.lua` ganha `CanvasLayer`, `Panel`, `Button` como fields da tabela `nengine`.
- [x] 13.3 Criado `engine-bundle-lua/.../stubs/engine/ui.lua` com `---@class CanvasLayer`, `---@class Panel`, `---@class Button` (incluindo `---@field pressed Signal`).
- [x] 13.4 Verificação manual em IDE OK.

## 14. Bindings Python e Lua no host

- [x] 14.1 `PythonScriptHost.installNengineSurface` (init) registra `CanvasLayer`, `Panel`, `Button` no Polyglot Context.
- [x] 14.2 `LuaScriptHost.installNengine` adiciona `put("CanvasLayer", ...)`, `put("Panel", ...)`, `put("Button", ...)`.
- [x] 14.3 `NodeRegistry.registerEngineTypes` registra os 3 com FQN — `# extends Button` resolve via `findBySimpleName`.

## 15. Documentação

- [x] 15.1 `CLAUDE.md` adiciona invariante #6 sobre UI/CanvasLayer (com nota sobre `tree.debug` + auto-insert do `DebugOverlayLayer`); invariante #4 ganha sentença "GameHost.render não desenha".
- [ ] 15.2 (Adiar) Lista de Nodes shipped em CLAUDE.md — não está numa lista enumerada hoje; pode atualizar ad-hoc quando alguém precisar.
- [x] 15.3 `ROADMAP.md`: `ui-foundation` em Active; entradas Planned para `ui-controls-base`, `ui-anchors`, `ui-layout`, `ui-focus`, `ui-theme`, `ui-input-events`.
- [ ] 15.4 (Adiar) `README.md` — alteração pode entrar junto com docs de outras changes futuras.

## 16. Validação cruzada e fechamento

- [x] 16.1 Compilação OK em todos os módulos + validação manual de execução de todos os jogos shipped em Skiko.
- [x] 16.2 Validação manual de `:games:demos:runLwjgl` OK.
- [x] 16.3 `openspec validate ui-foundation` → "Change is valid".
- [x] 16.4 `/opsx:verify ui-foundation` rodado — 0 críticos, 5 warnings opcionais (cobertura de teste adicional + 1 nota de docs); aceitos como follow-ups não-bloqueantes.
