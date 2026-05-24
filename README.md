# nengine

2D game engine com scene graph estilo Godot, escrita em Kotlin, com backend de renderização e linguagem de scripting agnósticos.

## Objetivo

Construir uma engine 2D do zero para aprender arquitetura de engine — clareza didática acima de performance prematura, evolução incremental guiada por jogos de exemplo que viram prova viva de cada capacidade.

A meta de longo prazo é cobrir o ciclo completo: do scene graph mínimo até um editor visual, passando por backends e linguagens de scripting trocáveis sem tocar no núcleo.

## O que temos hoje

### Backends de renderização

| Backend | Status   | Módulo            | Jogo sentinela     |
| ------- | -------- | ----------------- | ------------------ |
| Skiko   | default  | `:engine-skiko`   | `:games:pong`      |
| Compose | mantido  | `:engine-compose` | `:games:tictactoe` |

### Scripting

| Linguagem     | Status     | Módulo                  | Notas                                  |
| ------------- | ---------- | ----------------------- | -------------------------------------- |
| Python        | default    | `:engine-bundle-python` | via GraalPy 24.x; stubs `.pyi` inclusos |
| Kotlin Script | deprecated | —                       | substituído por bundle + Python        |

### Jogos

| Jogo          | Backend | Scripting | Função na engine                                     |
| ------------- | ------- | --------- | ---------------------------------------------------- |
| Pong          | Skiko   | Python    | prova da fundação (loop, física, scripts, signals)   |
| Jogo da Velha | Compose | Kotlin\*  | sentinela do segundo backend                         |
| Demos         | Skiko   | Kotlin    | 5 cenas exercitando invariantes (transform, colisão) |

## O que pretendemos ter

| Item          | Categoria | Estado                                                 |
| ------------- | --------- | ------------------------------------------------------ |
| Lua scripting | runtime   | planejado — prova que a SPI é genuinamente agnóstica   |
| Snake         | jogo      | planejado — validador de Camera2D, signals, fixed-step |
| Editor visual | tooling   | planejado — vai dirigir decisões de serialização       |

Detalhe completo em [`ROADMAP.md`](./ROADMAP.md).

## Como rodar

```sh
./gradlew :games:pong:run         # Skiko + Python
./gradlew :games:tictactoe:run    # Compose
./gradlew :games:demos:run        # cenas de demonstração visual
```

## Usando a engine

Há dois caminhos previstos:

1. **Por código** — disponível hoje. Subclasses de `Node` / `Node2D` em Kotlin, ou scripts Python anexados via `scene.json` (modelo Godot-like).
2. **Por editor visual** — planejado. Vai produzir o mesmo `scene.json` que o pipeline de bundle já carrega.

## Saber mais

- [`CLAUDE.md`](./CLAUDE.md) — invariantes arquiteturais, convenções, contrato de scripting, workflow OpenSpec
- [`ROADMAP.md`](./ROADMAP.md) — changes ativas e planejadas
- [`openspec/changes/archive/`](./openspec/changes/archive/) — histórico de decisões arquivadas
