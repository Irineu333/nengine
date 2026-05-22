## MODIFIED Requirements

### Requirement: CLAUDE.md describes module structure and how to run

O `CLAUDE.md` SHALL descrever o layout de módulos do projeto (`:engine`, `:engine-bundle`, `:engine-compose`, `:engine-skiko`, `:games:<name>`) e o comando para rodar um módulo de jogo (`./gradlew :games:<name>:run`). O documento MUST esclarecer qual jogo roda em qual backend após a migração para Skiko-as-default (Pong e Demos em Skiko; Tic Tac Toe em Compose). A remoção dos módulos `:desktopApp` e `:shared` do template MUST permanecer registrada. O módulo `:engine-scripting` MUST NÃO aparecer no documento — foi absorvido por `:engine-bundle`. A linha do `:engine-bundle` MUST mencionar que ele hospeda o `BundleLoader` e a compilação interna de scripts `.nengine.kts`.

#### Scenario: Module structure section is accurate

- **WHEN** um desenvolvedor compara a seção com `settings.gradle.kts`
- **THEN** os módulos listados batem com o grafo real do projeto
- **AND** `:engine-bundle` aparece ao lado de `:engine`, `:engine-skiko`, `:engine-compose`
- **AND** `:engine-scripting` NÃO aparece nem na seção de módulos nem no roadmap como módulo ativo

#### Scenario: Backend per game is stated

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a seção de módulos nomeia Skiko como backend usado por `:games:pong` e `:games:demos`
- **AND** nomeia Compose como backend usado por `:games:tictactoe`

#### Scenario: engine-bundle responsibilities are described

- **WHEN** `CLAUDE.md` é aberto
- **THEN** a linha do `:engine-bundle` (ou seção equivalente) descreve sua responsabilidade como "carregar cena via bundle (scene.json + scripts/) e hospedar a compilação interna de scripts `.nengine.kts`"

#### Scenario: Run instructions work as written

- **WHEN** um desenvolvedor executa o comando mostrado para Pong
- **THEN** o jogo inicia sem passos adicionais

### Requirement: CLAUDE.md describes the OpenSpec workflow and roadmap

O `CLAUDE.md` SHALL explicar que mudanças materiais (arquitetura, API pública, novos módulos, novas capabilities) passam por proposta OpenSpec antes da implementação, e SHALL incluir um roadmap visível apontando para changes ativas e planejadas. O roadmap MUST listar cada change arquivada com status `Archived`, incluindo `engine-foundation`, `add-tictactoe`, `engine-consistency`, `add-skiko-runtime`, `prepare-for-serialization`, `add-scripting`, `drop-pong-tag-only-scripts`, e `add-bundle-loader` após esta change ser arquivada. O roadmap MUST ser atualizado quando uma change ativa avança.

#### Scenario: Workflow section refers contributors to OpenSpec

- **WHEN** um contribuidor quer propor uma feature
- **THEN** a seção de workflow direciona a criar uma change OpenSpec em vez de abrir um PR direto

#### Scenario: Roadmap reflects current state

- **WHEN** `CLAUDE.md` é lido ao final desta change
- **THEN** o roadmap inclui uma linha para `add-bundle-loader` com status `Archived`
- **AND** o resumo da linha menciona que `:engine-bundle` substituiu `:engine-scripting` e que `BundleLoader.fromResources`/`fromPath` é a nova API de carregamento de cena
