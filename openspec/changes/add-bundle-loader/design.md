## Context

Hoje carregar uma cena no Pong exige um ritual de inicialização espalhado por `Main.kt`: registrar tipos da engine no `NodeRegistry`, instanciar `KotlinScriptingHost` com um manifesto ordenado topologicamente à mão, registrar o host em `ScriptHosts` (global mutável), buscar o JSON por `classLoader.getResource` e finalmente chamar `SceneLoader.load(text)`. O resultado é ~30 linhas de boilerplate que misturam três responsabilidades distintas (assets/IO, scripting, parsing de cena) e expõem detalhes que o jogo não deveria precisar saber.

Em paralelo, a separação atual entre `:engine-scripting` e `:engine` virou artificial: o consumidor de scripts é sempre o `SceneLoader`, e a SPI `ScriptHost` em `:engine` só serve para inversão de dependência via global — um anti-padrão que dificulta testes e produz a estranha condição `entry.type.endsWith(".kts")` dentro do loader. Para o futuro editor visual, queremos que um jogo seja uma pasta autocontida (`scene.json` + `scripts/` + `assets/`) — o "bundle". O módulo que carrega isso deve falar de bundle, não de scripting.

O `KotlinScriptingHost` atual também tem três armadilhas práticas:

1. **Manifesto manual com ordem topológica** — quem escreve `Main.kt` precisa saber quais scripts dependem de quais e listar na ordem correta.
2. **Cache key incompleto** — hoje é `SHA256(source) + version`. O conjunto de imports default cresce conforme scripts anteriores compilam, então o bytecode efetivo depende da posição do script no manifesto, mas o cache não sabe disso. Reordenar o manifesto pode servir bytecode stale.
3. **Diretório `classes/` cresce indefinidamente** — bytecode de scripts removidos permanece.

## Goals / Non-Goals

**Goals:**

- `Main.kt` de um jogo com bundle deve caber em ~6 linhas, sem registrar tipos manualmente, sem instanciar host de scripting, sem manifesto.
- Mesma cena JSON funciona em classpath (jogo empacotado) e disco (cenário de editor visual), sem alteração.
- Compilação de scripts robusta a cross-refs em qualquer ordem, com detecção determinística de ciclo.
- Cache de bytecode correto sob qualquer reordenação ou alteração de cross-refs.
- Eliminar `:engine-scripting` como módulo público; remover `ScriptHost`/`ScriptHosts` de `:engine`.
- Manter `SceneLoader.load(text)` e `save(scene)` como API low-level (testes, REPL, snapshots inline).

**Non-Goals:**

- Sub-sistema de assets (texturas, sons, fontes) — `assets/` é reservado mas não implementado nesta change.
- Hot-reload de scripts em runtime — fora do escopo (a change futura é responsabilidade do editor).
- Suporte a múltiplos bundles ativos simultaneamente — assume-se um bundle por processo de jogo; o registry e o host de scripts são substituídos a cada `from*`.
- Suporte a scripts em outras linguagens além de Kotlin (`.nengine.kts`).
- API pública de descoberta de bundles (catálogo). Quem chama sabe o nome/path.

## Decisions

### 1. Estrutura de bundle convencional

Um **scene bundle** é uma pasta com a forma:

```
<bundle>/
  scene.json
  scripts/
    *.nengine.kts
  assets/        (reservado, ainda sem leitura nesta change)
```

`scene.json` é a raiz e seu path passa a ser **relativo ao bundle**. Toda referência `"type": "scripts/foo.nengine.kts"` dentro do JSON é resolvida relativa ao diretório do bundle, o que garante portabilidade entre classpath e disco. O JSON gerado pela `save` segue a mesma convenção.

**Alternativa considerada**: arquivos espalhados na raiz dos resources (sem `<bundle>/` envolvente). Rejeitado porque o editor visual precisa de uma unidade portátil — uma pasta — e porque permite múltiplos bundles convivendo em um mesmo classpath sem colidir.

### 2. Módulo `:engine-bundle` substitui `:engine-scripting`

```
:engine                 ← scene graph, serialização primitiva, NodeRegistry, SceneLoader (low-level)
:engine-bundle  ✦ NEW   ← BundleLoader, ScriptSource, KotlinScriptingHost, cache
:engine-skiko           ← backend Skiko
:engine-compose         ← backend Compose
```

A SPI `ScriptHost`/`ScriptHosts` em `:engine` é deletada. O `KotlinScriptingHost` (renomeado internamente se necessário) passa a viver em `:engine-bundle/.../bundle/scripting/`. O `BundleLoader` é a única superfície pública.

**Alternativa considerada**: manter SPI em `:engine` e só mover impl. Rejeitado porque a única razão da SPI era atravessar a fronteira via global; ao colocar `BundleLoader` no mesmo módulo que o host, a indireção some.

### 3. `NodeRegistry` bidirecional, `SceneLoader` sem ramo `.kts`

`NodeRegistry` passa a guardar mapeamentos bidirecionais:

```kotlin
register(identifier: String, klass: KClass<out Node>, factory: () -> Node)
create(identifier: String): Node
identifierFor(klass: KClass<out Node>): String?
```

- Para tipos da engine: `identifier = FQN`, factory criada por `registerEngineTypes()`.
- Para scripts: `identifier = "scripts/foo.nengine.kts"` (relativo ao bundle), `klass` vem do `KotlinScriptingHost`, factory invoca o construtor no-args.
- Para tipos custom de jogo passados como `types: List<KClass>`: `identifier = FQN`, factory criada por reflection (`klass.java.getDeclaredConstructor().newInstance()`).

`SceneLoader.load` chama `NodeRegistry.create(entry.type)` sem distinguir nada. `SceneLoader.save` chama `NodeRegistry.identifierFor(node::class)`. Zero conhecimento sobre scripts dentro do loader.

**Alternativa considerada**: deixar `SceneLoader` continuar consultando `ScriptHosts.current()` mas com `ScriptHosts` movido para o novo módulo. Rejeitado porque mantém o reach global em `:engine` e quebra a invariante "`:engine` não conhece scripting".

### 4. Descoberta de scripts por tree-walk no JSON

O `BundleLoader` parseia o `scene.json` (estrutura `SceneFile` → `NodeEntry`) e percorre a árvore coletando todo `entry.type` que termine em `.nengine.kts`. Só esses scripts vão para o host. Scripts órfãos (presentes na pasta, não referenciados) **não** são compilados.

**Alternativa considerada**: scan da pasta `scripts/`. Rejeitado pelo usuário em favor de tree-walk — preserva a invariante "só pago o que uso" e mantém a cena como fonte da verdade.

### 5. Compilação round-robin / fixed-point

O host recebe a lista de scripts a compilar (coletada no tree-walk) e executa o algoritmo:

```
pending  ← lista de scripts
resolved ← {} (path → KClass)
while pending não vazio:
    progressed ← false
    for path em copy(pending):
        try:
            klass ← compile(path, defaultImports = resolved.values)
            resolved[path] ← klass
            pending.remove(path)
            progressed ← true
        catch CompilationFailed(errors):
            if every error é "unresolved reference: X"
               AND X é uma classe de outro path ainda em pending:
                continue (defer)
            else:
                rethrow com mensagem contextualizada
    if not progressed:
        raise CyclicScriptDependencyError(pending)
```

A heurística "todo erro é unresolved reference para classe de outro pending" é o ponto delicado. Para reduzir falso-positivo de "erro real do autor parecendo dependência":

- Extrai-se uma vez (lazy, regex sobre cada source) o conjunto de nomes de classe top-level publicados por cada script pendente: `^class\s+(\w+)\s*[:(]`. Se a referência não-resolvida não bate com nenhum desses nomes, **é erro real**.
- O erro real é propagado **imediatamente** com a mensagem original do compilador, sem aguardar próxima iteração.

**Alternativa considerada**: cabeçalho `// @requires Foo, Bar`. Rejeitado por exigir convenção que autor pode esquecer (modo silencioso de falha). A regex top-level é um auxiliar interno, não uma API contratual.

**Alternativa considerada**: dois passes (1: compila cada em isolamento; 2: recompila quem falhou com tudo importado). Rejeitado: se todos os scripts cross-refs, passe 1 falha em todos e o passe 2 não tem o que importar. O fixed-point é monotônico.

### 6. Cache key bug-proof

```
cacheKey = SHA256(
    source 
    || "\n---\n" 
    || importSet.sortedBy { it }.joinToString("\n")
    || "\n---\n"
    || engineVersion
)
```

- `source`: conteúdo textual do `.nengine.kts`.
- `importSet`: lista ordenada de FQN dos wrapper classes que estão sendo passados como `defaultImports` para esta compilação específica.
- `engineVersion`: lido de um recurso `META-INF/nengine.version` produzido pelo build do `:engine`. Reduz risco de bytecode incompatível após upgrade da engine.

Bytecode é gravado em `<cacheRoot>/<cacheKey>.bin`. O `classesDir` (onde `URLClassLoader` lê) é **reconstruído do zero** a cada bootstrap a partir das entradas válidas — bytecode órfão de scripts deletados some sozinho.

**Alternativa considerada**: incluir só `SHA256(source)` (status quo). Rejeitado: comprovadamente sujeito a stale bytecode quando o conjunto de imports muda.

### 7. Localização do cache

- `fromResources(name)`: `build/scripting-cache/<name>/` (JAR é read-only; cache fora).
- `fromPath(dir)`: `<dir>/.nengine-cache/` (portátil, junto do bundle — desejável para editor).

`.nengine-cache/` é mencionado em `.gitignore` recomendado (ajustar no próprio bundle do Pong se necessário, mas como ele roda via `fromResources`, não cria essa pasta no repo).

### 8. API do `BundleLoader`

```kotlin
object BundleLoader {
    fun fromResources(
        name: String,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene

    fun fromPath(
        bundleDir: File,
        types: List<KClass<out Node>> = emptyList(),
    ): Scene
}
```

`types` aceita tipos compilados custom de jogo (raros — Pong não usa nenhum). Factory é derivada por reflection sobre construtor no-args; falhar nessa derivação é erro de inicialização clara.

Internamente, ambas `from*` convergem em uma função pivot `load(scriptSource: ScriptSource, sceneJson: String, types: ...)` que:

1. Chama `NodeRegistry.registerEngineTypes()` (idempotente).
2. Registra `types` no `NodeRegistry` via reflection.
3. Tree-walk no `sceneJson` para coletar paths de scripts.
4. Cria `KotlinScriptingHost(scriptSource, cacheRoot)` e invoca round-robin compile para esses paths.
5. Registra os tipos resolvidos no `NodeRegistry` com identifier = path relativo.
6. Devolve `SceneLoader.load(sceneJson)`.

**Alternativa considerada**: `SceneLoader.fromResources` (como o usuário primeiro sugeriu). Rejeitado para preservar a invariante "`:engine` não depende de `:engine-bundle`". A API de bundle vive no módulo que tem o direito de compilar scripts.

### 9. Múltiplas chamadas de `from*` no mesmo processo

Comportamento: cada chamada **substitui** o estado do `NodeRegistry` para tipos não-engine (scripts e custom). Os tipos da engine persistem (registro idempotente). Caso de uso múltiplo é incomum no jogo final, mas é frequente em testes — cada teste começa com um bundle limpo via `NodeRegistry.clear()` interno antes de re-registrar.

**Alternativa considerada**: tornar bundles aditivos (acumular). Rejeitado: gera identifier collisions difíceis de diagnosticar.

### 10. `ScriptSource` interno

```kotlin
internal sealed interface ScriptSource {
    fun read(relativePath: String): String  // lê texto bruto do .kts

    data class Classpath(val bundleRoot: String) : ScriptSource
    data class Directory(val bundleDir: File) : ScriptSource
}
```

`relativePath` é exatamente o que aparece em `scene.json` (`"scripts/foo.nengine.kts"`). `Classpath` resolve via `classLoader.getResource("$bundleRoot/$relativePath")`; `Directory` via `File(bundleDir, relativePath).readText()`.

## Risks / Trade-offs

- **Heurística do round-robin pode mascarar erro real do autor** → o filtro por "nome de classe top-level extraído via regex" garante que apenas referências a símbolos *publicados por outro script pendente* são consideradas dependências. Erros que mencionem qualquer outro símbolo (typo em método, parâmetro errado, etc.) propagam na hora.
- **Regex de extração de classe top-level é frágil** (comentários, strings multilinhas com `class X` dentro) → como é só uma heurística interna que pode produzir falso-positivos benignos (script será adiado mais uma rodada e re-tentado), e não falsos-negativos (não vai esconder um símbolo legítimo), aceitamos a fragilidade. Não é API contratual.
- **Cache em `<bundle>/.nengine-cache/` polui a pasta do bundle no cenário disco** → custo aceito; é convencional (cf. `.git/`, `.gradle/`). Documentado.
- **`NodeRegistry` global mutável** → mesmo padrão que já existia. Não piora. Para testes, o `NodeRegistry.clear()` é chamado em setup.
- **Compilação iterativa pode disparar O(N²) compilações no pior caso** → na primeira run sem cache; runs subsequentes são O(N) graças ao cache. Para Pong (6 scripts), N² é desprezível mesmo na primeira run.
- **Reflection para `types: List<KClass>`** → marginalmente mais lento na criação, mas a criação acontece poucas vezes (uma por instância na hora do load). Trade-off vale a ergonomia.
- **Quebra de API pública**: `:engine-scripting` desaparece, `ScriptHost`/`ScriptHosts` somem de `:engine` → coberto explicitamente no proposal como BREAKING. Nenhum game externo conhecido depende disso fora do `:games:pong` (que migra na mesma change).

## Migration Plan

1. Criar `:engine-bundle` e migrar `KotlinScriptingHost`/`NEngineScript` para lá; aplicar o refator (round-robin, cache key, classes/limpa). Manter `:engine-scripting` temporariamente em paralelo? **Não** — é mais limpo cortar de uma vez já que `:games:pong` é o único consumidor.
2. Atualizar `NodeRegistry` para bidirecional. Atualizar `SceneLoader.load`/`save` para usar exclusivamente o registry.
3. Remover `engine/src/main/kotlin/com/neoutils/engine/scripting/`.
4. Criar `BundleLoader` em `:engine-bundle`.
5. Mover `games/pong/src/main/resources/pong.scene.json` para `games/pong/src/main/resources/pong/scene.json`. Mover `games/pong/src/main/resources/scripts/` para `games/pong/src/main/resources/pong/scripts/`. Os paths internos do JSON continuam relativos ao bundle (`scripts/foo.nengine.kts`) — sem mudança.
6. Atualizar `games/pong/build.gradle.kts`: remover dependência de `:engine-scripting`, adicionar `:engine-bundle`.
7. Reescrever `games/pong/src/main/kotlin/com/neoutils/engine/games/pong/Main.kt` para o formato curto.
8. Atualizar `settings.gradle.kts`: remover `:engine-scripting`, adicionar `:engine-bundle`.
9. Atualizar `CLAUDE.md`: tabela de módulos e linha do roadmap.
10. Atualizar specs OpenSpec (`scripting`, `scene-serialization`, `pong-sample`, `project-conventions`) via deltas; criar `bundle-loading`.
11. Mover testes do `:engine-scripting` para `:engine-bundle`. Adicionar `BundleLoaderTest`. Simplificar `SceneLoaderTest`. Remover `ScriptHostsTest`.
12. Validar: `./gradlew build` e `./gradlew :games:pong:run` (game roda, FPS normal).

## Open Questions

- **Versão da engine para o cache key**: lemos de um recurso `META-INF/nengine.version` que o build do `:engine` produz? Ou usamos `Properties` derivado do `gradle.properties`? Sugestão: arquivo em `src/main/resources/META-INF/nengine.version` com a string atual de versão; build pode injetar via tarefa Gradle, mas inicialmente constante hard-coded é suficiente. **Decidir durante a apply.**
- **`SceneLoader.save` em bundle**: a change não trata save para bundle. `save(scene)` continua retornando o texto JSON e o caller grava onde quiser. Bundle save (gravar JSON + copiar/sincronizar scripts) é trabalho do editor visual, fora desta change. Documentar no spec.
