## Why

A engine já tem todas as peças físicas pra simular bilhar — `RigidBody2D` com restitution, friction, `linearDamping`/`angularDamping`, sweep CCD elástico e `Area2D` como sensor — mas nenhum jogo até hoje exercita o caso "tacada única transfere energia entre vários corpos rolando com atrito de mesa e parando por damping". Pool 8-ball é o exemplar canônico desse padrão e, ao mesmo tempo, introduz três mecânicas que nenhum jogo existente cobre: (a) input com **vetor de força modulado** ("puxar e soltar"), (b) **turn-gate por quiescência** (esperar todas as bolas pararem antes do próximo turno), e (c) **caçapas como sumidouros** via `Area2D` removendo corpos da árvore em runtime. Bom exercício pra fixar damping calibrado, sweep multi-corpo elástico, e o protocolo Lua de manipular a árvore viva durante o gameplay.

## What Changes

- Adicionar módulo `:games:pool8` (executável `:games:pool8:run`) com bundle Lua em `src/main/resources/pool8/` (scene.json + scripts/).
- Mesa virtual 2000×1000 unidades, bola raio 20, renderizada via `Camera2D` (FIT) em `SkikoHost`.
- Geometria: 4 segmentos de `StaticBody2D` formando o retângulo da mesa + 6 `Area2D` posicionados sobre as caçapas (anel contínuo, opção A do explore).
- 16 bolas `RigidBody2D` (1 branca + 15 numeradas) configuradas com `restitution=0.95`, `friction=0.15`, `linearDamping≈0.45`, `angularDamping≈0.50`. Rack triangular com a bola 8 no centro.
- Render por bola via `_draw` Lua: círculo colorido (lookup canônico de cor por número) + numeral preto centralizado; bolas 9–15 ganham faixa branca horizontal.
- Input "puxar e soltar": mouseDown → drag visualiza vetor → mouseUp aplica impulso oposto na branca. Magnitude do arrasto vira força (clamp em `maxDragPixels`).
- Mira: vetor de força + linha pontilhada projetando a trajetória inicial da branca via ray cast Lua contra bolas e cushions (sem física, primeira interseção).
- FSM de turno (`AIMING → SIMULATING → RESOLVING`): durante `SIMULATING` o input fica bloqueado; transita pra `RESOLVING` quando `Σ|v|` + `Σ|ω|` permanecem abaixo de threshold por 3 frames consecutivos.
- Regras Nível 1 (mecânico, sem grupos solid/stripe): encaçapou qualquer bola não-branca não-8 → joga de novo; encaçapou branca → respawn no head spot, passa turno; encaçapou 8 a qualquer hora → vence; nenhuma bola → passa turno.
- HUD básica: `Label` de turno atual + mensagem de vitória ao final.
- Atalhos `F1` (FPS overlay) e `F2` (colliders overlay) continuam disponíveis via `GameConfig`.

Sem `BREAKING`: change é puramente aditiva, nenhuma capability existente muda contrato.

## Capabilities

### New Capabilities
- `pool8-sample`: jogo Pool 8-ball como prova viva de damping calibrado, sweep multi-corpo elástico com transferência de momento real, FSM de turno por quiescência, input com vetor de força modulado, e remoção dinâmica de nodes durante traversal via `Area2D` sumidouro. Roda em Skiko com scripting Lua.

### Modified Capabilities
<!-- Nenhuma. Sinuca consome APIs estáveis (RigidBody2D, Area2D, Camera2D, Lua scripting, bundle loading) sem mudar o contrato delas. -->

## Impact

- Novo módulo `:games:pool8` em `games/pool8/` com `build.gradle.kts` dependendo de `:engine`, `:engine-skiko`, `:engine-bundle`, `:engine-bundle-lua` (mesmo template do `:games:tictactoe`).
- Novo bundle `games/pool8/src/main/resources/pool8/` com `scene.json` (16 bolas + 4 cushions + 6 caçapas + Table + CueStick + HUD) e `scripts/{table,ball,pocket,cue,status}.lua`.
- `Main.kt` instancia `LuaScriptHost.create()` e chama `BundleLoader.fromResources("pool8", scripting = lua)`.
- `settings.gradle.kts` inclui o módulo.
- Atualização de `CLAUDE.md` documentando `./gradlew :games:pool8:run`, controles e o que a cena exercita (paralelo aos blocos de Pong/TTT/Demos/Hello).
- Atualização do `ROADMAP.md` marcando o jogo.
- Nenhum efeito em `:engine`, `:engine-bundle`, `:engine-bundle-lua`, `:engine-skiko`. Se durante implementação aparecer necessidade de API nova (improvável), abrir change separada.
- Mesa de teste: damping precisará de calibração empírica (rodar e ajustar até "tacada média atravessa a mesa ~3 vezes antes de parar"). Isso fica documentado no design e iterado em runtime.
