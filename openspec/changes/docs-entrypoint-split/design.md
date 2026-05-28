## Context

Estado atual:
- `CLAUDE.md` (377 linhas) mistura decision log (invariantes, convenções), runbook (comandos Gradle, controles in-game, caveat macOS, configuração de IDE) e tutorial de scripting (contratos Python + Lua com ~150 linhas de snippets, exemplos de `scene.json`, hooks, signals).
- `README.md` (67 linhas) está enxuto mas desatualizado: anuncia LWJGL como planejado (já é segundo backend ativo via `runLwjgl` em `:games:demos`); lista Snake como planejado (já existe e roda).
- A spec ativa `project-conventions` (`openspec/specs/project-conventions/spec.md`) prescreve em detalhes o conteúdo obrigatório do `CLAUDE.md` — inclui requirements explícitos sobre módulos com comandos, caveat macOS, contrato Python com exemplo, roadmap visível, seção "Para rodar Demos" com `runLwjgl`. Editar o `CLAUDE.md` sem atualizar essa spec quebra `openspec validate` e deixa a fonte de verdade mentindo sobre o real.

Restrições:
- Nenhum conhecimento documental pode se perder na migração. Cada bloco que sai do `CLAUDE.md` precisa ter um destino com paridade verificada antes da reescrita.
- `ROADMAP.md` é fonte da verdade do roadmap e fica intocado.
- Artefatos OpenSpec em português (alinhamento com `project-conventions` e `ROADMAP.md`).

## Goals / Non-Goals

**Goals:**
- `CLAUDE.md` vira decision log perene para IA: invariantes, convenções, mapa de módulos/games/pastas, modelo de scripting resumido, workflow OpenSpec.
- `README.md` vira runbook propositivo para humanos: proposta, capacidades atualizadas, quickstart Gradle, demos resumidas, controles globais, configuração de IDE.
- Conteúdo mutável migra para as specs técnicas que já existem (`python-scripting`, `lua-scripting`, `engine-core`, `demos-sample`, jogos-sample), sem perda de informação.
- `project-conventions` é atualizada para refletir o novo contrato dos dois entrypoints.

**Non-Goals:**
- Renomear ou criar specs novas — todas as receptoras já existem em `openspec/specs/`.
- Alterar comportamento runtime, código fonte (Kotlin, Python, Lua) ou estrutura de módulos Gradle.
- Mudar o `ROADMAP.md` ou o histórico em `openspec/changes/archive/`.
- Introduzir lint automático de "o que pode estar no CLAUDE" — fica como disciplina manual via spec.
- Decidir o conteúdo "ideal" das specs receptoras além do que é necessário para receber o que sai do `CLAUDE.md` — paridade, não expansão.

## Decisions

### 1. CLAUDE.md como decision log puro (vs. híbrido)

Optamos por uma cisão estrita: `CLAUDE.md` contém **só** material perene (invariantes, padrões, mapa de pastas). Tudo que é instrucional ou mutável sai. A alternativa híbrida (manter quickstart curto + invariantes) foi descartada porque criava sobreposição com o `README.md` — mesma informação em dois lugares se desincroniza. A regra "zero code snippets" no `CLAUDE.md` é proxy operacional dessa cisão: snippet quase sempre indica conteúdo mutável.

**Trade-off aceito**: leitor IA precisa fazer um hop adicional para ler contratos detalhados de scripting (vai para `openspec/specs/python-scripting/spec.md`). Mitigação: `CLAUDE.md` mantém o esqueleto de 5 linhas do modelo Godot-style + ponteiro explícito.

### 2. Specs receptoras absorvem o conteúdo migrado (vs. criar arquivos novos)

Cada bloco que sai do `CLAUDE.md` vai para uma spec **já existente**:

| Bloco no CLAUDE                                  | Spec receptora               |
| ------------------------------------------------ | ---------------------------- |
| Scripting contract (Python)                      | `python-scripting`           |
| Scripting contract (Lua)                         | `lua-scripting`              |
| "Camera2D define o mundo virtual"                | `engine-core`                |
| "Performance Notes" (cache de world / `Node.tree`)| `engine-core`               |
| Descrição das 6 cenas de Demos                   | `demos-sample`               |
| Controles e gameplay de Pong                     | `pong-sample`                |
| Controles e gameplay de Velha                    | `tictactoe-sample`           |
| Controles e gameplay de Snake                    | `snake-sample`               |
| Controles e gameplay de Hello-World              | `hello-world-sample`         |

A alternativa de criar arquivos novos (ex.: `claude-md-contract`) foi descartada — multiplicaria specs sem ganho e quebraria o princípio de uma capability = uma fonte.

### 3. Validação de paridade antes de reescrever

A reescrita do `CLAUDE.md` é a **última** tarefa, executada apenas depois de todas as specs receptoras terem absorvido o conteúdo migrado e a paridade ter sido confirmada (cada bloco que sai do `CLAUDE.md` tem cobertura equivalente em uma Requirement/Scenario na spec receptora). A alternativa "cortar primeiro, ajustar specs depois" foi descartada — janela de risco onde conhecimento documental ficaria orphan.

### 4. project-conventions usa deltas MODIFIED + ADDED + REMOVED

A spec `project-conventions` hoje tem 6 requirements sobre `CLAUDE.md` e 1 sobre `ROADMAP.md`. O delta vai:
- **MODIFIED** os requirements de módulos, convenções e workflow para relaxar o que era exigido no `CLAUDE.md` (sem comandos Gradle, sem caveat macOS, sem contrato detalhado de Python, sem roadmap visível).
- **REMOVED** os requirements/cenários que ficam sem propósito (ex.: scenario "Demos documents the LWJGL alternate entrypoint" no CLAUDE; "Python scripting contract is documented" no CLAUDE).
- **ADDED** novos requirements descrevendo `README.md` como entrypoint humano com proposta/capacidades/quickstart/demos/controles globais/IDE/caveat macOS.

### 5. Linguagem dos artefatos

Todos os artefatos (`proposal.md`, `design.md`, `tasks.md`, deltas de spec) ficam em português, alinhando com `project-conventions` e `ROADMAP.md` atuais. Identificadores técnicos (nomes de módulo, classes, comandos) permanecem em inglês.

## Risks / Trade-offs

- **Risco**: Migrar conteúdo do `CLAUDE.md` para uma spec receptora pode duplicar com material já existente lá. → **Mitigação**: tarefa de paridade lê cada spec receptora antes de adicionar Requirement; se já cobre, reescrita do CLAUDE pode simplesmente apagar o bloco; se não cobre, delta ADICIONA o que falta.
- **Risco**: `openspec validate` quebra durante a sequência (estado intermediário inconsistente). → **Mitigação**: validar apenas no fim da change, depois de todos os deltas e da reescrita estarem concluídos. Estado intermediário é aceito enquanto a change está aberta.
- **Risco**: Leitores externos (humanos abrindo o repo pela primeira vez via GitHub) podem perder contexto se o `README.md` ficar pesado. → **Mitigação**: alvo de ~100 linhas para `README.md`; cenas de Demos resumidas a 1 linha cada; controles específicos linkam para as spec dos jogos em vez de listar tudo.
- **Trade-off**: o `CLAUDE.md` perde a função de "primer de scripting" — uma IA fresca precisa abrir mais um arquivo para escrever scripts. Aceitamos: o ganho de coesão e a remoção de drift compensam.
- **Trade-off**: o `README.md` cresce ~50% em tamanho. Aceitamos: ele passa a refletir o estado real do projeto (LWJGL ativo, Snake shipped) e absorve quickstart + IDE setup que viviam no CLAUDE.
