# Metro DI Reference

Use this reference when the task needs concrete Metro guidance rather than a generic DI answer.

## 1. Setup and Initialization

Metro is usually enabled through the Gradle plugin:

```kotlin
plugins {
  kotlin("multiplatform") // or jvm, android, etc
  id("dev.zacsweers.metro")
}
```

The plugin normally adds Metro runtime dependencies and wires the compiler plugin automatically.

Use a dependency graph as the entry point:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
  val repository: Repository
}
```

Create the graph with:

```kotlin
val graph = createGraph<AppGraph>()
```

Use `@DependencyGraph.Factory` only when the graph needs runtime inputs such as `@Provides` instances or `@Includes` dependencies.

## 2. Choosing a Binding Strategy

### Prefer explicit local bindings when:

- the project is effectively single-module,
- the graph and implementation live together,
- you do not need aggregation across modules.

Typical tools:

- `@Inject` for constructor-injected classes,
- `@Provides` for third-party or framework-owned types,
- `@Binds` for explicit interface-to-implementation bindings.

### Prefer aggregation when:

- bindings live in feature or library modules,
- the final graph should discover contributions automatically,
- the project already uses a scope-centered DI design.

Typical tools:

- `@ContributesBinding(scope)` for implementation-to-interface bindings,
- `@ContributesTo(scope)` for contributed provider interfaces or binding containers.

The graph scope is the merge point:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph
```

That means contributions to `AppScope` from other modules are merged into the generated graph.

## 3. Multi-module Rules

For multi-module projects:

- define a stable scope shared by the graph and contributing modules,
- put implementations in their owning modules,
- contribute them with `@ContributesBinding` or `@ContributesTo`,
- keep the final `@DependencyGraph` in the app or composition root module.

Prefer this style for api/impl boundaries:

```kotlin
interface Repository

@ContributesBinding(AppScope::class)
@Inject
class RepositoryImpl(...) : Repository

@DependencyGraph(AppScope::class)
interface AppGraph {
  val repository: Repository
}
```

Use explicit `binding = binding<Interface>()` when a class has multiple supertypes or the intended bound type is ambiguous.

## 4. Kotlin Multiplatform Rules

Metro runtime and code generation are platform-agnostic, but source set visibility still matters.

Critical rule:

- If the graph needs platform-specific contributions, the final `@DependencyGraph` must live in the platform source set.

Recommended pattern:

```kotlin
// commonMain
interface AppGraph {
  val httpClient: HttpClient
}

// androidMain
@DependencyGraph
interface AndroidAppGraph : AppGraph {
  @Provides fun provideHttpClient(): HttpClient = HttpClient(OkHttp)
}

// jvmMain
@DependencyGraph
interface JvmAppGraph : AppGraph {
  @Provides fun provideHttpClient(): HttpClient = HttpClient(Netty)
}
```

This keeps the common contract stable while letting each platform graph see its own platform-only bindings.

Do not put the final `@DependencyGraph` in `commonMain` when the graph must consume `androidMain`, `iosMain`, `jvmMain`, or other platform-specific contributions.

## 5. `expect` / `actual` Guidance

Metro is not a reason by itself to introduce `expect` / `actual`. Use it only when the domain genuinely needs platform-specific APIs or implementations.

Prefer this division:

- `commonMain`: interfaces, use cases, graph contracts without final platform-only graph annotations.
- platform source sets: `actual` implementations, platform providers, and final graphs when platform-specific bindings are needed.

Good fit:

- `expect` logger, file system, device info, dispatcher provider, or platform service facade.
- platform `actual` class contributed or provided in the platform source set.

Bad fit:

- `expect fun createGraph()` used only to work around incorrect graph placement.
- a common graph annotation that cannot see platform-only contributions.

When editing code that already uses `expect` / `actual`, verify whether annotations need to exist on the `actual` declaration too before assuming the common declaration is enough.

## 6. Common Mistakes To Avoid

- Treating Metro like a KAPT or KSP DI setup.
- Using `@ContributesBinding` in a local single-module case where `@Binds` is clearer.
- Keeping the graph in `commonMain` while expecting it to discover platform-only bindings.
- Hiding platform ownership by placing providers in the wrong source set.
- Over-abstracting graph creation when `createGraph()` or an existing factory already works.

## 7. Fast Decision Table

- Simple app module only: use `@Inject`, `@Provides`, `@Binds`, `createGraph()`.
- Multiple modules with shared scope: use `@ContributesBinding` / `@ContributesTo` and a scoped `@DependencyGraph`.
- KMP with platform-only implementations: keep common contracts in `commonMain`, put final graph in each platform source set.
- KMP with `expect` / `actual`: use it for platform APIs and implementations, not as a substitute for correct Metro graph placement.
