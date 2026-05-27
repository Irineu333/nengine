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
| LWJGL   | planejado | —                | —                  |

### Scripting

| Linguagem     | Status     | Módulo                  | Notas                                   |
|---------------|------------|-------------------------|-----------------------------------------|
| Kotlin        | native     | `:engine`               | biblioteca entra como dependência       |
| Python        | default    | `:engine-bundle-python` | via GraalPy 24.x; stubs `.pyi` inclusos |
| Lua           | suportado  | `:engine-bundle-lua`    | via LuaJ 3.0.x; stubs LuaCATS inclusos  |

### Jogos

| Jogo          | Backend | Scripting | Função na engine                                               |
|---------------|---------|-----------|----------------------------------------------------------------|
| Pong          | Skiko   | Python    | prova da fundação (loop, física, scripts, signals, `Camera2D`) |
| Jogo da Velha | Skiko   | Lua       | sentinela do segundo backend de scripting; também roda sob `Camera2D` |
| Demos         | Skiko   | Kotlin    | 6 cenas exercitando invariantes (transform, colisão)           |
| Hello World   | Skiko   | —         | exemplo code-only mínimo (um `Label` centralizado)             |

## O que pretendemos ter

| Item          | Categoria | Estado                                                 |
|---------------|-----------|--------------------------------------------------------|
| LWJGL backend | runtime   | planejado — segundo backend de render para revalidar a SPI |
| Snake         | jogo      | planejado — validador de wraparound em `Camera2D.bounds`, signals e fixed-step |
| Editor visual | tooling   | planejado — vai dirigir decisões de serialização       |

Detalhe completo em [`ROADMAP.md`](./ROADMAP.md).

## Como rodar

```sh
./gradlew :games:pong:run          # Skiko + Python
./gradlew :games:tictactoe:run     # Skiko + Lua
./gradlew :games:demos:run         # cenas de demonstração visual
./gradlew :games:hello-world:run   # exemplo code-only mínimo
```

## Usando a engine

Há dois caminhos previstos:

1. **Por código** — disponível hoje. Subclasses de `Node` / `Node2D` em Kotlin, ou scripts Python anexados via `scene.json` (modelo Godot-like).
2. **Por editor visual** — planejado. Vai produzir o mesmo `scene.json` que o pipeline de bundle já carrega.

## Saber mais

- [`CLAUDE.md`](./CLAUDE.md) — invariantes arquiteturais, convenções, contrato de scripting, workflow OpenSpec
- [`ROADMAP.md`](./ROADMAP.md) — changes ativas e planejadas
- [`openspec/changes/archive/`](./openspec/changes/archive/) — histórico de decisões arquivadas
- [`openspec/specs/`](./openspec/specs/) — especificações do projeto