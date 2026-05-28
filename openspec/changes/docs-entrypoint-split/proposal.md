## Why

`CLAUDE.md` cresceu para 377 linhas misturando três papéis distintos: decision log arquitetural (invariantes, convenções), runbook de desenvolvedor (comandos Gradle, caveat macOS, controles in-game) e tutorial de scripting (contratos Python+Lua com snippets e exemplo de `scene.json`). O resultado é um arquivo poluído cujo conteúdo mutável (cenas de Demos, controles de cada jogo, detalhes de cache de transform, sintaxe de scripts) se desatualiza junto da engine e compete com as specs técnicas em `openspec/specs/<capability>/`. O `README.md` por sua vez está enxuto, **mas desatualizado** — anuncia LWJGL como "planejado" quando já é segundo backend ativo, e omite Snake da lista de jogos shipped.

Esta change redefine as responsabilidades dos dois entrypoints para alinhar com o público de cada um: `CLAUDE.md` como decision log perene para contribuidores IA (invariantes, padrões, mapa de pastas), `README.md` como runbook propositivo para humanos (quickstart, capacidades, demos). Conteúdo mutável migra para as specs técnicas que já existem.

## What Changes

- **BREAKING (spec contract)** — `CLAUDE.md` deixa de ser também runbook e tutorial; passa a ser exclusivamente decision log + invariantes + convenções + mapa de pastas + workflow OpenSpec.
- `README.md` reescrito como entrypoint humano: proposta, capacidades atualizadas (LWJGL ativo, Snake shipped, `runLwjgl`), quickstart Gradle, resumo das 6 cenas de Demos, controles globais, configuração de IDE (stubs Python/Lua), caveat macOS.
- Conteúdo migrado **de** `CLAUDE.md` **para** specs técnicas (preservando paridade — nada se perde):
  - Bloco "Scripting contract (Python)" → `python-scripting`
  - Bloco "Scripting contract (Lua)" → `lua-scripting`
  - Parágrafo "Camera2D define o mundo virtual" → `engine-core`
  - "Performance Notes" (cache de world transform, `Node.tree`) → `engine-core`
  - Descrição detalhada das 6 cenas de Demos → `demos-sample`
  - Controles in-game e descrição de gameplay de cada jogo → spec do respectivo `<jogo>-sample`
- Sem code snippets em `CLAUDE.md` (zero); modelo de scripting reduzido a 5 linhas + ponteiros para `python-scripting` e `lua-scripting`.
- Sem roadmap pointer em `CLAUDE.md` (vive em `ROADMAP.md` e é referenciado pelo `README.md`).
- `project-conventions` é a spec central da mudança: seus requirements sobre `CLAUDE.md` são relaxados (remove obrigação de listar comandos Gradle, caveat macOS, contrato Python com exemplo, roadmap visível) e novos requirements sobre `README.md` são adicionados.

## Capabilities

### New Capabilities

Nenhuma. A change reformula contratos documentais existentes.

### Modified Capabilities

- `project-conventions`: relaxa requirements sobre conteúdo de `CLAUDE.md` (sem runbook embutido, sem contrato detalhado de scripting, sem roadmap visível, sem code snippet de Python); adiciona requirements sobre `README.md` como entrypoint humano (proposta, capacidades, quickstart, demos resumidas, IDE setup, caveat macOS); mantém requirement sobre `ROADMAP.md` inalterado.
- `python-scripting`: absorve o contrato Python que sai do `CLAUDE.md` (estrutura de script, hooks, exports, signals, scene.json properties, configuração de stubs `.pyi`).
- `lua-scripting`: absorve o contrato Lua que sai do `CLAUDE.md` (estrutura de script com `extends`/`exports`/`signals`, namespace `nengine.*`, `self`/`self.node`, sandbox `require`, configuração de stubs LuaCATS).
- `engine-core`: absorve "Camera2D define o mundo virtual" (rationale do espaço de coordenadas do mundo virtual, `aspectMode`, fallback identity) e "Performance Notes" (invariante de invalidação do cache `cachedWorld` e `Node.tree`).
- `demos-sample`: absorve a descrição detalhada das 6 cenas (Solar system, Scale hierarchy, Spawner, Collision stress, Rotating box, Tumbling swarm).
- `pong-sample`, `tictactoe-sample`, `snake-sample`, `hello-world-sample`: cada um absorve os controles in-game e a descrição de gameplay específica do seu jogo que hoje vive no `CLAUDE.md`.

## Impact

- Arquivos editados: `CLAUDE.md` (reescrito, ~120 linhas), `README.md` (reescrito, ~100 linhas).
- Specs editadas (deltas): `project-conventions`, `python-scripting`, `lua-scripting`, `engine-core`, `demos-sample`, `pong-sample`, `tictactoe-sample`, `snake-sample`, `hello-world-sample`.
- Validação obrigatória antes de cortar: paridade entre conteúdo que sai do `CLAUDE.md` e o que está nas specs receptoras — qualquer lacuna vira novo Requirement nas specs antes da reescrita do `CLAUDE.md`, garantindo que nenhum conhecimento documental seja perdido.
- Sem impacto em código fonte (Kotlin, Python, Lua) ou em comportamento runtime.
- `ROADMAP.md` permanece inalterado.
- `openspec validate` precisa passar ao final.
