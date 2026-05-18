# nengine

2D game engine code-only com scene graph estilo Godot, escrita em Kotlin. Primeiro runtime: Compose Multiplatform Desktop (JVM).

## Módulos

- `:engine` — núcleo da engine em Kotlin puro (sem dependência de Compose). Scene graph, lifecycle, math, SPI de `Renderer`/`Input`, `Collider`/`PhysicsSystem`, `GameLoop`.
- `:engine-compose` — backend Compose Multiplatform da engine: `ComposeRenderer`, `ComposeInput`, composable `GameSurface` que dirige o loop via `withFrameNanos`.
- `:games:pong` — jogo Pong executável (humano vs IA), usado como prova de aceitação da fundação.

## Como rodar

```sh
./gradlew :games:pong:run
```

## Documentação

Veja [`CLAUDE.md`](./CLAUDE.md) para propósito do projeto, invariantes arquiteturais, convenções, workflow OpenSpec e roadmap.
