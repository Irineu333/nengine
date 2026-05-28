## 1. Validação de paridade antes do corte

- [ ] 1.1 Auditar `openspec/specs/python-scripting/spec.md` contra o bloco "Scripting contract (Python)" do `CLAUDE.md` atual; identificar gaps (estrutura de script, hooks, exports/signals, scene.json properties, configuração de stubs `.pyi`).
- [ ] 1.2 Auditar `openspec/specs/lua-scripting/spec.md` contra o bloco "Scripting contract (Lua)" do `CLAUDE.md` atual; identificar gaps (extends/exports/signals tabela, namespace `nengine.*`, `self`/`self.node`, sandbox `require`, configuração de stubs LuaCATS).
- [ ] 1.3 Auditar `openspec/specs/engine-core/spec.md` contra os blocos "Camera2D define o mundo virtual" e "Performance Notes" do `CLAUDE.md` atual; identificar gaps.
- [ ] 1.4 Auditar `openspec/specs/pong-sample`, `tictactoe-sample`, `snake-sample`, `hello-world-sample` contra as seções "Para rodar Pong/Velha/Snake/Hello World" do `CLAUDE.md` atual; identificar gaps em controles e gameplay específico.
- [ ] 1.5 Para cada gap identificado em 1.1–1.4, decidir: (a) gap pequeno → adicionar `ADDED Requirements` no delta correspondente da change; (b) gap grande → registrar como tarefa de seguimento, NÃO desta change.

## 2. Deltas de spec (apply order: receptoras antes de project-conventions)

- [ ] 2.1 Aplicar delta `specs/demos-sample/` (cobre cenas 1–6) — já criado nesta change.
- [ ] 2.2 Se 1.1 identificou gaps em `python-scripting`, criar delta `specs/python-scripting/` com `ADDED Requirements` cobrindo o gap.
- [ ] 2.3 Se 1.2 identificou gaps em `lua-scripting`, criar delta `specs/lua-scripting/` com `ADDED Requirements` cobrindo o gap.
- [ ] 2.4 Se 1.3 identificou gaps em `engine-core`, criar delta `specs/engine-core/` com `ADDED Requirements` cobrindo o gap.
- [ ] 2.5 Se 1.4 identificou gaps em algum `<jogo>-sample`, criar delta correspondente em `specs/<jogo>-sample/`.
- [ ] 2.6 Aplicar delta `specs/project-conventions/` (MODIFIED + ADDED + REMOVED) — já criado nesta change.

## 3. Reescrita do README.md

- [ ] 3.1 Escrever seção "Proposta" (overview propositivo, expandindo as 2-3 linhas atuais).
- [ ] 3.2 Atualizar tabela "Backends de render" — LWJGL passa de `planejado` para `segundo backend ativo (NanoVG + GLFW + OpenGL 3.3 core)`, módulo `:engine-lwjgl`.
- [ ] 3.3 Atualizar tabela "Scripting" — manter as três linhas (Kotlin native, Python default GraalPy, Lua suportado LuaJ).
- [ ] 3.4 Atualizar tabela "Jogos shipped" — adicionar Snake; atualizar função-sentinela de cada jogo; remover "Snake planejado" da seção "O que pretendemos ter".
- [ ] 3.5 Escrever seção "Quickstart" com os 6 comandos Gradle (incluindo `runLwjgl`).
- [ ] 3.6 Adicionar caveat macOS `-XstartOnFirstThread` próximo ao bloco de comandos LWJGL.
- [ ] 3.7 Escrever seção "Demos" com resumo de uma linha por cena (1–6); pontar para `openspec/specs/demos-sample/` para detalhe.
- [ ] 3.8 Escrever seção "Controles globais" listando `F1`/`F2`/`F3`; explicar que controles por jogo vivem em `openspec/specs/<jogo>-sample`.
- [ ] 3.9 Escrever seção "Configurando o IDE" cobrindo stubs Python (Pyright `extraPaths` para `engine-bundle-python/src/main/resources/stubs/`) e stubs Lua (sumneko-lua `workspace.library` para `engine-bundle-lua/src/main/resources/stubs/`).
- [ ] 3.10 Escrever seção "Saber mais" linkando `CLAUDE.md`, `ROADMAP.md`, `openspec/specs/`, `openspec/changes/archive/`.
- [ ] 3.11 Verificar que o `README.md` final fica em torno de ~100 linhas; revisar redundâncias com `CLAUDE.md`.

## 4. Reescrita do CLAUDE.md

- [ ] 4.1 Manter seção "Purpose" com até 3 linhas (mencionar Kotlin, Skiko default, LWJGL como segundo backend, Godot-style scene graph).
- [ ] 4.2 Manter seção "Architectural Invariants" com os 5 invariantes (incluindo subseção "RigidBody2D vs CharacterBody2D"); remover qualquer referência ao Camera2D como "Foundations" — o assunto vira ponteiro para `engine-core`.
- [ ] 4.3 Remover seção "Performance Notes" inteira.
- [ ] 4.4 Substituir seção "Module Structure & How to Run" por uma seção "Module Layout" enxuta — tabela `módulo → responsabilidade`, sem comandos, sem controles, sem caveats.
- [ ] 4.5 Adicionar seção "Games" com tabela `jogo → backend + scripting + função-sentinela`.
- [ ] 4.6 Manter seção "Coding Conventions" em bullets curtos; remover subseção "Camera2D define o mundo virtual" (vai pra `engine-core`); remover blocos extensos de "Scripting contract (Python)" e "Scripting contract (Lua)".
- [ ] 4.7 Adicionar seção "Scripting Model" com até 5 linhas resumindo: Godot-style, hooks underscore-prefixed, factory `signal()`, fail-fast. Ponteiro explícito para `openspec/specs/python-scripting` e `openspec/specs/lua-scripting`.
- [ ] 4.8 Manter seção "OpenSpec Workflow" listando os passos (`explore`, `propose`, `apply`, `verify`, `archive`).
- [ ] 4.9 Remover seção "Roadmap" inteira.
- [ ] 4.10 Adicionar seção final "Where to find more" linkando `ROADMAP.md`, `openspec/specs/`, `openspec/changes/archive/`, `README.md`.
- [ ] 4.11 Verificar que o `CLAUDE.md` final fica em torno de ~120 linhas e contém zero code snippets de scripting.

## 5. Validação final

- [ ] 5.1 Rodar `openspec validate docs-entrypoint-split --strict` e confirmar que passa.
- [ ] 5.2 Conferir a olho que cada bloco que saiu do `CLAUDE.md` tem cobertura equivalente em uma spec (paridade verificada na seção 1).
- [ ] 5.3 Conferir que o `README.md` renderiza bem no GitHub (tabelas, listas, blocos de código).
- [ ] 5.4 Rodar `./gradlew :games:pong:run` (sanity check de que as instruções de quickstart no `README.md` continuam corretas após a reescrita).
- [ ] 5.5 Atualizar `ROADMAP.md` apenas se houver mudança real de status — esta change não toca o roadmap por design.
