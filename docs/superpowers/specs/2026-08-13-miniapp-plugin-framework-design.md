# Compile-Time Mini-App Plugin Framework Design

## Context

Logica is evolving from a single Block Blast game into a catalog application
that ships multiple trusted mini-apps, initially games. A contributor creates a
Gradle module such as `:game:snake`; after that module is reviewed and added to
the production allowlist, it is compiled into the next Android and iOS release
and appears in the catalog.

This is not a remote app store and does not load executable code at runtime.
There is no server-side catalog, downloaded plugin, dynamic feature delivery,
binary plugin compatibility, or sandbox. Every accepted plugin is source code
in the repository, runs in the application process, and is reviewed and built
with the host application.

The repository has already moved Block Blast rules, persistence, components,
resources, and UI into `:game:blockblast`, but the application architecture is
still specific to that game:

- `:feature:root` directly depends on Block Blast component, state, result, and
  factory types;
- `:composeApp` renders Block Blast UI directly and installs a Block Blast
  theme;
- `:feature:home` models one game's `Continue` and `New Game` actions;
- the app graph exposes unqualified, single-game bindings such as the common
  save-status API and Block Blast factories;
- result, revive, review qualification, and game-session navigation are owned
  by the host instead of by the game module.

Replacing Home with a hard-coded Catalog first would only move these concrete
dependencies. The migration must first introduce a stable plugin boundary and
move Block Blast behind it, then switch the host to a generic catalog.

## Decisions

The following product and architecture decisions are fixed for the first
framework version:

- The base abstraction is `MiniApp`, not `Game`, so future non-game tools do
  not require a breaking rename.
- Mini-app UI is Compose Multiplatform only.
- Plugins are discovered at compile time through Metro set multibinding.
- A single Gradle allowlist decides which plugins ship in production.
- The catalog has one `Play` action. It has no `Continue` or `New Game`
  knowledge.
- On `Play`, a game resumes its unfinished session when one exists and starts a
  new session otherwise.
- The host always starts at the catalog after process death or a cold launch.
- At most one mini-app session is active at a time.
- A common host-owned frame renders Back, Settings, and a bottom banner around
  every active mini-app.
- With no overlay open, both toolbar Back and system Back close the current
  mini-app and return to the catalog. When Settings or Review is open, system
  Back dismisses that topmost overlay first.
- Settings is a global host-owned overlay and does not destroy the active
  session.
- Settings and Review obscure the retained session. The host exposes this as a
  read-only visibility capability so real-time games can pause user-affecting
  simulation while obscured.
- The top bar and banner occupy layout space; they never overlay the plugin's
  viewport.
- Result, revive, resume selection, and any future replay behavior belong to
  the concrete game.
- A separate Metro child graph exists for every active mini-app session.
- Global services enter a session graph as narrow typed capabilities. The
  plugin API does not expose the app graph or a capability service locator.
- A second non-production reference plugin is an architectural test of the
  framework.
- Contributor automation uses a convention plugin, a scaffold task, and a
  contract testkit. A separate KSP/compiler plugin is not introduced.

## Goals

- Remove every Block Blast type and factory from Root and application UI.
- Make adding an accepted game require no Root, Catalog, or application UI
  changes.
- Keep plugin metadata and catalog presentation deterministic and host-owned.
- Give every active plugin an isolated DI session lifetime while preserving
  app-lifetime persistence.
- Centralize navigation chrome, system insets, settings, review policy, and
  banner advertising in the host.
- Prevent plugin modules from depending on application features or native SDK
  implementations.
- Minimize contributor boilerplate through generated scaffolding and
  compile-time/default contract checks.
- Preserve existing Block Blast saves across the migration.

## Non-goals

- Downloading, installing, updating, or removing plugins at runtime.
- A server, remote catalog, remote configuration, or executable content.
- Sandboxing or security isolation between accepted plugins.
- Supporting native Android Views, UIKit, or SwiftUI as plugin UI contracts.
- Restoring an active mini-app automatically after process death.
- Exposing `Replay` or other game-specific commands in the initial generic API.
- Allowing plugins to replace host-owned catalog cards, toolbar layout and
  controls, settings UI, or advertisements. A session may contribute only the
  narrow optional center-content slot defined below.
- Designing binary compatibility for independently compiled third-party
  artifacts.
- Introducing a custom annotation processor before real plugin implementations
  demonstrate that the scaffold and Metro generation are insufficient.

## Target Module Structure

```text
:miniapp:api
    Stable Compose-free IDs, lifecycle values, and host request contracts

:miniapp:compose
    Compose Resources manifest, plugin/session contracts, registry contract

:miniapp:metro
    Metro registry implementation, set aggregation, session scope support

:miniapp:testkit
    Contract validators, fake host, lifecycle and rendering test utilities

:miniapp:integration-test
    Non-shipping graph/host that integrates the Counter plugin end to end

:miniapp:bundle
    Build-only aggregation of production-allowed plugin modules

:miniapp:samples:counter
    Minimal reference plugin used only by non-shipping integration

:feature:catalog
    Uniform host-rendered catalog backed by MiniAppRegistry

:feature:root
    Catalog/RunningMiniApp navigation, Settings and Review ownership

:game:blockblast
    Block Blast plugin, child graph, internal flow, persistence and UI

:composeApp
    Final AppGraph, LogicaTheme, MiniAppFrame and platform capability adapters
```

No additional `app-shell:component` or `app-shell:ui` modules are introduced.
The existing Root and `:composeApp` already own those responsibilities.

### Dependency direction

```text
:game:blockblast ---------> :miniapp:api / :miniapp:compose / :miniapp:metro
          |                 allowed capability APIs, Decompose, MVIKotlin,
          +---------------> Compose and :core:uikit

:feature:catalog ---------> MiniAppRegistry contract
:feature:root ------------> catalog/settings/review + mini-app contracts
:miniapp:metro -----------> mini-app contracts + Metro
:composeApp --------------> root + miniapp bundle + concrete capabilities
:androidApp / iosApp -----> composeApp only
```

A plugin must not depend on:

- `:feature:*`;
- `:composeApp`;
- `:androidApp` or the iOS application target;
- another concrete `:game:*` plugin;
- `:monetization:ads` or any other native SDK adapter;
- a concrete app graph implementation.

Allowed shared dependencies include the mini-app framework, stable `core`
APIs, `:core:uikit`, `:monetization:core`, Compose, Decompose, MVIKotlin, and
Metro. The convention plugin validates project dependency boundaries. A plugin
may depend on an additional external library only through normal code review;
the term "plugin" does not grant isolation from arbitrary trusted source code.

## Public Contract

The following shapes express the ownership boundary. Exact Kotlin names and
package placement may be refined during implementation, but their semantics
must not change without updating this design.

### Identity

```kotlin
@JvmInline
value class MiniAppId(val value: String)
```

IDs are stable, non-localized, globally unique, and use a namespaced lowercase
format such as `game.blockblast`. An ID is persistence and analytics identity;
it must not be changed as part of a display-name rebrand.

The production registry validates all IDs eagerly. Duplicate or malformed IDs
fail integration tests and application graph creation in development rather
than silently selecting one plugin.

### Manifest

```kotlin
data class MiniAppManifest(
    val id: MiniAppId,
    val title: StringResource,
    val description: StringResource,
    val icon: DrawableResource,
    val cover: DrawableResource?,
    val category: MiniAppCategoryId,
    val sortPriority: Int,
)
```

The manifest is static and available without creating a session. Text and
images are public Compose Resources owned by the plugin module. The host owns
card layout, typography, accessibility semantics, the `Play` label, and
interaction behavior. A plugin cannot supply an arbitrary composable catalog
card or host-chrome colors.

The initial catalog may be a flat ordered collection even though category is
present in metadata. Ordering is deterministic by `sortPriority`, then
`MiniAppId`. Search, favorites, badges, previews, and category navigation are
future catalog features rather than plugin-session concerns.

### Plugin and session

```kotlin
interface MiniAppPlugin {
    val manifest: MiniAppManifest

    fun createSession(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: MiniAppSessionHost,
    ): MiniAppSession
}

interface MiniAppSession {
    @Composable
    fun TopBarContent() = Unit

    @Composable
    fun Content(modifier: Modifier)
}

interface MiniAppSessionHost {
    fun close()
    fun requestReview(opportunity: MiniAppReviewOpportunity)
}

enum class MiniAppVisibility {
    ACTIVE,
    OBSCURED,
    BACKGROUND,
}

interface MiniAppVisibilitySource {
    val visibility: StateFlow<MiniAppVisibility>
}
```

`MiniAppPlugin` is an app-lifetime descriptor and session factory. It must not
create game state merely to expose its manifest. `MiniAppSession` is one active
instance retained by the Root child for the complete session lifetime.

The session content receives a `Modifier` for its root viewport. The plugin
must apply it to the root layout and must not add assumptions about system
insets, top-bar height, or banner height. Those are already resolved by the
host frame. `TopBarContent` is an optional center-content slot: the host still
owns the toolbar row, sizing, controls and accessibility, and renders the slot
under the host theme. The default is empty so contributors incur no boilerplate.

`MiniAppSessionHost` deliberately stays small:

- `close()` requests returning to the catalog;
- `requestReview(...)` reports a qualified opportunity to the app-wide review
  policy.

It does not expose `openSettings()`: the host already owns the Settings button
and navigation. It does not expose telemetry, audio, storage, ads, or the app
graph. Those are independent typed dependencies inherited by the child graph.

`MiniAppVisibilitySource` is a host-owned read-only session input, not a
general service locator. It reports overlay and platform lifecycle visibility
without letting the plugin control host navigation.

`MiniAppReviewOpportunity` contains only a stable trigger ID and the minimum
typed qualification facts required by the app-wide policy. It does not contain
`MiniAppId`: the session-bound host attaches the authoritative active plugin ID
and prevents a plugin from misattributing another plugin's review opportunity.
Game-specific eligibility is calculated inside the game before the request;
app-wide rate limits, suppression, analytics, and the store prompt remain owned
by `:feature:review` and Root.

### Registry

`MiniAppRegistry` is a read-only contract exposed to Catalog and Root. It
provides the deterministically ordered manifest collection and resolves a
plugin by exact `MiniAppId`. It exposes neither the Metro set nor mutable
registration operations.

The Metro implementation receives `Set<MiniAppPlugin>`, validates it once, and
builds an immutable ID-indexed map. There is no manually maintained runtime
`when` statement, renderer registry, `Any` component, or unchecked component
cast.

## Host-Owned Presentation

Every running session is rendered by a shared slot-based frame:

```text
LogicaTheme
└── MiniAppFrame
    ├── Top bar
    │   ├── Back
    │   ├── optional MiniAppSession.TopBarContent
    │   └── Settings
    ├── Mini-app viewport
    │   └── optional plugin-local theme and MiniAppSession.Content
    └── Bottom banner slot
```

Conceptually:

```kotlin
@Composable
fun MiniAppFrame(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
)
```

The frame, not the plugin:

- applies window and safe-area insets;
- owns top-bar layout, sizing, controls and accessibility while accepting only
  optional center content from the session;
- maps toolbar Back and system Back to the same Root action;
- presents global Settings without destroying the session;
- reserves space for the bottom banner when it is eligible;
- removes the banner container completely when advertising is not eligible, so
  the viewport expands into the released space.

Banner policy, consent, entitlement, ad unit configuration, SDK initialization,
and rendering stay in the host and monetization adapter. No advertising SDK
type crosses the plugin boundary. Catalog advertising, if ever desired, is a
separate host product decision; this design places the common banner in the
running mini-app frame.

The application uses a host-level `LogicaTheme` for Catalog, Back, Settings,
Review, error states, and the banner area. A plugin may wrap only its viewport
in a local theme such as `BlockBlastGameTheme`. Plugin theming cannot alter the
common chrome. Root renders one ambient background behind both its child stack
and sheets, so child transitions never duplicate or restart that background.

## Navigation and Product Behavior

Root has only two primary child states:

```kotlin
sealed interface Child {
    data class Catalog(/* ... */) : Child
    data class RunningMiniApp(
        val id: MiniAppId,
        val state: RunningMiniAppState,
    ) : Child
}

sealed interface RunningMiniAppState {
    data class Content(val session: MiniAppSession) : RunningMiniAppState
    data class Unavailable(val id: MiniAppId) : RunningMiniAppState
}
```

Settings and Review are host-owned overlays associated with Root rather than
primary mini-app destinations. `Unavailable` is an internal presentation state
inside the same running destination, not a third primary Root destination.

The flow is:

```text
cold launch / process restoration
              |
              v
           Catalog
              |
           Play(id)
              |
              v
     create one child graph/session
              |
              v
       RunningMiniApp
          |        |
     Settings     Back/system Back/host.close
          |        |
          +--------+----> destroy session -> Catalog
```

Root does not persist a `RunningMiniApp` configuration across process death.
Normal recomposition, configuration changes, foreground/background transitions,
and opening Settings do not recreate the Root child and therefore keep the
session alive. A cold launch or restored process starts in Catalog by design.
On Android, the host must retain the live Root/session across configuration
changes through Decompose's instance-retention mechanism while deliberately
omitting the running destination from process-restorable state. These are two
different lifetimes and must not be implemented with one serialized flag.

Repeated `Play` events while navigation is already creating a session are
coalesced or ignored so only one active child graph exists. If an ID cannot be
resolved, Root stays or returns to Catalog and records a host telemetry event.
A synchronous session-factory failure produces a host-owned unavailable state
with a path back to Catalog. This framework does not claim to isolate arbitrary
exceptions thrown later by trusted plugin code.

Host callbacks are bound to the concrete Root child that created them. A late
`close()` or review request from an already destroyed session is ignored and
cannot affect a subsequently opened session. This invariant does not require a
public session-ID type in the plugin API. Root uses an internal unique session
key, serializes host callbacks on the UI dispatcher, treats repeated close as
idempotent, and cancels pending host work when that child is destroyed.

When Settings or Review is open, system Back dismisses the topmost overlay and
does not close the retained session. Toolbar Back and system Back are equivalent
only while no overlay is visible. Neither overlay is restored over Catalog
after process death.

The host also supplies a read-only session visibility capability with at least
`ACTIVE`, `OBSCURED`, and `BACKGROUND` states. Settings and Review produce
`OBSCURED`; platform backgrounding produces `BACKGROUND`. The session remains
allocated in both states, but interactive games must suspend user-affecting
simulation and timers until visibility returns to `ACTIVE`.

## Metro DI and Session Lifetime

There are two graph lifetimes:

```text
AppGraph / AppScope
├── immutable MiniAppRegistry
├── Set<MiniAppPlugin>
├── global typed capability implementations
└── plugin persistence repositories and other app-lifetime state
             |
             | Play(id)
             v
Plugin-specific @GraphExtension / MiniAppSessionScope
├── runtime ComponentContext
├── runtime MiniAppSessionHost
├── plugin root component and internal navigation
├── session-scoped stores/coordinators/state
└── MiniAppSession
```

Each production plugin contributes exactly one descriptor to the app-scoped
set, conceptually:

```kotlin
@Inject
@ContributesIntoSet(AppScope::class)
class BlockBlastPlugin(
    private val sessionGraphFactory: BlockBlastSessionGraph.Factory,
) : MiniAppPlugin {
    override fun createSession(
        componentContext: ComponentContext,
        visibility: MiniAppVisibilitySource,
        host: MiniAppSessionHost,
    ): MiniAppSession {
        val graph = sessionGraphFactory.create(componentContext, visibility, host)
        return BlockBlastSessionHandle(graph)
    }
}
```

Each plugin owns a Metro child graph, conceptually:

```kotlin
@GraphExtension(MiniAppSessionScope::class)
interface BlockBlastSessionGraph {
    val session: MiniAppSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun create(
            @Provides componentContext: ComponentContext,
            @Provides visibility: MiniAppVisibilitySource,
            @Provides host: MiniAppSessionHost,
        ): BlockBlastSessionGraph
    }
}

private class BlockBlastSessionHandle(
    private val graph: BlockBlastSessionGraph,
) : MiniAppSession {
    private val session = graph.session

    @Composable
    override fun Content(modifier: Modifier) {
        session.Content(modifier)
    }
}
```

The actual returned session handle must retain a strong reference to the child
graph until the Root child is removed. Returning a bare `graph.session` is
forbidden because it does not express or guarantee graph retention. The
app-scoped plugin must not cache child graphs; the active Root child is their
sole owner. On removal, the Decompose child lifecycle is destroyed and the
graph reference is released. Metro scope caching is not a disposal mechanism:
resource-owning session dependencies must attach cleanup to the provided
`ComponentContext.lifecycle`. The scaffold and testkit generate and verify this
retained-handle pattern.

`:miniapp:metro` also declares an empty-capable set binding, conceptually
`@Multibinds(allowEmpty = true) val plugins: Set<MiniAppPlugin>`, so framework
and isolated test graphs remain valid without a production plugin.

Persistent saves, best scores, tutorials, and long-lived statistics do not
belong in `MiniAppSessionScope`. They remain plugin-internal app-lifetime
dependencies. New plugin storage keys are namespaced by `MiniAppId`; shared
storage helpers generated or provided by the framework make the namespace the
default. Existing Block Blast keys and serialized data remain readable to
avoid destroying user progress.

## Typed Capabilities

Metro graph extensions technically inherit every accessible non-private parent
binding, not a DI-level capability whitelist. The architecture exposes only
narrow contracts such as settings observation, telemetry, reusable audio,
session visibility, and SDK-neutral interstitial policy. Concrete
implementations remain in `:core:data`, `:core:telemetry`,
`:monetization:ads`, `:composeApp`, or native shells as appropriate. Review is
not a DI capability: it has the single session-host request path defined above.

Rules:

- a plugin injects only capabilities it uses;
- capability interfaces live in stable inward-facing API modules;
- SDK implementation types never cross into a plugin;
- parent implementation bindings that must not leak into child graphs use
  Metro `@GraphPrivate`;
- Gradle dependency boundaries prevent a plugin from importing parent
  implementation types that Metro could otherwise resolve;
- no `MiniAppCapabilities` bag, service locator, `Map<String, Any>`, or app
  graph accessor is allowed;
- the common bottom banner is not a capability because the host renders it;
- the plugin-specific child graph may adapt an inherited capability to an
  internal game interface.

This keeps Metro an implementation detail of composition rather than embedding
DI operations in `MiniAppSession`.

## Production Allowlist and Bundle

Metro can aggregate only modules on the final graph's compile classpath. Gradle
project discovery and production shipping are therefore separate concerns:

- the repository settings plugin discovers mini-app projects under controlled
  roots such as `game/*` and `miniapp/samples/*`, making a newly scaffolded
  module independently buildable and testable;
- discovery does not add that project to an application dependency or package
  it in a release;
- one authoritative production allowlist decides which discovered projects
  become dependencies of the shipping bundle.

The production declaration records both project path and expected stable ID in
one entry:

```kotlin
miniApps {
    include(
        projectPath = ":game:blockblast",
        expectedId = "game.blockblast",
    )
    include(
        projectPath = ":game:snake",
        expectedId = "game.snake",
    )
}
```

The settings plugin owns an ordered immutable shipping model. The bundle
project plugin consumes that model through configuration-cache-safe Gradle
providers after its KMP plugin is applied. It uses each entry to:

1. validate that the already discovered Gradle project exists;
2. add it as `commonMain.api(project(projectPath))` in `:miniapp:bundle` so its
   Metro contribution and Compose resources reach the final graph classpath;
3. generate/provide the expected ID set for production registry validation;
4. register production contract validation;
5. make the entry itself the explicit release-acceptance decision.

The allowlist is the sole production source of truth. Adding a module directory
only makes that project addressable by Gradle; it does not ship it. The
`expectedId` is intentionally independent of the module path so moving a module
does not change persistence or analytics identity. No duplicate dependency edit
in Root, Catalog, or `:composeApp` is required. The build-logic implementation
must fail if it cannot preserve this single-declaration property; it must not
silently fall back to a second manually synchronized shipping list.

`:miniapp:bundle` contains no UI and no hand-written runtime registry. It is a
build aggregation boundary whose public/transitive project dependencies make
accepted plugins visible to Metro and Compose resource packaging.

Before the rest of the framework is built, a narrow Android/iOS proof spike
must demonstrate `plugin -> commonMain.api bundle -> composeApp` aggregation:
Metro sees the contribution on both targets, plugin resources resolve at
runtime, and the discovered-but-not-allowlisted Counter is absent from the
production registry and release packaging.

## Contributor Workflow and Boilerplate

### Convention plugin

A plugin module applies one repository convention plugin:

```kotlin
plugins {
    id("logica.miniapp")
}
```

It configures the standard KMP targets, Compose, Compose Resources, Decompose,
Metro, mini-app contracts, testkit, source sets, and boundary validation. A
module declares only exceptional third-party dependencies itself.

For the current Kotlin Multiplatform Android library plugin, the convention
enables Android resource processing, assigns every module a unique Compose
Resources package, and keeps the generated `Res` accessor module-internal. The
plugin constructs its manifest inside its own module and returns typed
`StringResource`/`DrawableResource` handles, so Catalog never imports another
module's `Res` class. The bundle's `commonMain.api` edges are what preserve
transitive metadata and resource packaging; Android APK and iOS framework/app
runtime lookups are mandatory integration checks rather than an assumption.

Conceptually, the convention applies:

```kotlin
compose.resources {
    publicResClass = false
    packageOfResClass = derivedUniquePackage
}

kotlin {
    android {
        androidResources {
            enable = true
        }
    }
}
```

Metro-facing plugin and graph bridge types are public across module boundaries
for the first implementation, while game engine, component, store, and UI
implementation types remain internal. This avoids depending on a hidden
compiler option for internal contribution visibility. The convention explicitly
uses `@Inject` on contributed plugin implementations and tests aggregation on
both targets.

### Scaffold task

The repository provides a task such as:

```text
./gradlew createMiniApp -PminiAppId=game.snake -PminiAppName=Snake
```

It creates a buildable module containing:

```text
game/snake/
├── build.gradle.kts
├── src/commonMain/.../SnakePlugin.kt
├── src/commonMain/.../SnakeSessionGraph.kt
├── src/commonMain/.../SnakeComponent.kt
├── src/commonMain/.../SnakeContent.kt
├── src/commonMain/composeResources/...
├── src/commonTest/.../SnakePluginContractTest.kt
└── AGENTS.md
```

Generated code demonstrates the expected component/UI split, applies the
viewport modifier correctly, retains the child graph for the session lifetime,
and binds cleanup to Decompose lifecycle. It is ordinary source that a
contributor can inspect and change.

The contributor workflow is:

```text
run createMiniApp
        -> on the next Gradle invocation, project discovery makes it buildable,
           not shipped
        -> implement rules, state, persistence and UI inside the module
        -> pass module and framework contract tests
        -> submit for review
        -> maintainer adds one production allowlist entry
        -> next APK/IPA contains the plugin and Catalog lists it
```

No Root branch, Catalog card, renderer binding, or application composition edit
is part of this workflow.

### Contract validation

The convention plugin and `:miniapp:testkit` cover complementary checks:

- stable ID syntax and uniqueness in the assembled registry;
- manifest completeness and resolvable Compose resources;
- one declared plugin under each module's reusable contract test, plus exact
  equality between the assembled production ID set and allowlist IDs;
- successful child graph/session creation with a fake host;
- Compose smoke rendering within the constrained viewport;
- Back closes and destroys the session lifecycle exactly once;
- stale callbacks from a destroyed session cannot close or attribute review to
  a newer session;
- Settings overlay does not destroy or recreate the session;
- system Back dismisses an overlay before closing a session;
- visibility changes `ACTIVE -> OBSCURED/BACKGROUND -> ACTIVE` without session
  recreation;
- forbidden project dependency detection;
- accepted allowlist entries appear in the production registry;
- the reference plugin works through the same Gradle, Metro, resources,
  navigation, frame, and lifecycle path.

Compilation itself validates strongly typed manifest resources and Metro graph
completeness. A custom processor is reconsidered only after several real
plugins identify repetitive source that cannot be eliminated safely by the
convention and scaffold.

## Reference Plugin

`:miniapp:samples:counter` is a minimal but real plugin module with:

- its own manifest and internally referenced typed resources;
- a plugin-specific Metro session graph;
- a tiny state holder/component;
- Compose content;
- lifecycle cleanup evidence;
- integration through the real registry and frame.

It is absent from the production allowlist and therefore absent from release
Catalog and production packaging. `:miniapp:integration-test` is a separate
non-shipping KMP integration host with its own final Metro graph; it depends on
Counter through the same public/transitive aggregation shape, exercises the
real registry and frame, and compiles/links target-specific resource lookup for
Android and iOS. Production `:composeApp` never depends on this integration
host or Counter. A separate production-registry assertion verifies Counter's
absence. A fake class inside a unit test is insufficient because it cannot
validate project wiring, transitive Compose Resources, Metro aggregation, or
the child graph boundary.

The framework is not considered complete until both Block Blast and this
independent plugin pass through the same public contract without special Root
or registry code.

## Block Blast Ownership After Migration

`:game:blockblast` gains an internal root session component:

```text
BlockBlastSessionComponent
├── Playing
└── Result
    └── Revive -> Playing within the same session
```

It absorbs all Block Blast-specific behavior currently located in Root:

- checking for an unfinished save when the session starts;
- choosing resume versus a new game;
- Playing-to-Result navigation;
- terminal result snapshots;
- revive and return to Playing;
- game-specific review eligibility;
- any future replay/reset action.

The `Play` action therefore behaves as:

```text
unfinished Block Blast save -> resume it
no unfinished save          -> create a new game
```

The common `GameSaveApi` is removed after Home and Root no longer consume it.
Block Blast keeps an internal save repository and its existing persistence
format. Future `Replay` behavior, if users require it, will reset Block Blast's
session internally. A later host-rendered contextual Settings action may expose
that operation through a generic action extension, but no replay-specific API
is added now.

Block Blast also stops importing `:monetization:ads`. If it needs an
interstitial at a qualified transition, it injects an SDK-neutral typed
capability; the adapter and consent policy remain outside the plugin.

## Migration Sequence

### 1. Framework foundation

- Run the Android/iOS bundle, Metro aggregation, and Compose Resources proof
  spike before committing to the remaining build-logic shape.
- Add `:miniapp:api`, `:miniapp:compose`, `:miniapp:metro`, and
  `:miniapp:testkit`.
- Define IDs, manifest, plugin/session/host contracts, and registry behavior.
- Add `MiniAppSessionScope` and the child-graph lifecycle pattern.
- Add convention, allowlist, bundle, scaffold, and boundary checks.
- Add focused framework tests without changing current user-visible flow.

### 2. Reference plugin

- Generate `:miniapp:samples:counter` through the scaffold.
- Add `:miniapp:integration-test` with its own final graph; do not attach
  Counter to a production source set or graph.
- Prove Metro aggregation, resources, session graph creation, frame rendering,
  Back, Settings retention, and destruction behavior.

### 3. Block Blast plugin

- Introduce `BlockBlastPlugin` and `BlockBlastSessionGraph`.
- Move Playing/Result/Revive navigation and resume selection into an internal
  Block Blast session root.
- Preserve the save schema and move SDK-facing dependencies behind typed
  capabilities.
- Render the complete game behind `MiniAppSession.Content`.

### 4. Generic Root and common frame

- Replace Home/Game/Result Root states with Catalog/RunningMiniApp.
- Add non-restored active-session navigation semantics.
- Add `LogicaTheme` and the host-owned `MiniAppFrame`.
- Centralize Back, system Back, Settings, Review, insets, and bottom-banner UI.
- Remove direct Block Blast rendering from `:composeApp`.

### 5. Catalog

- Add `:feature:catalog` using `MiniAppRegistry` manifests.
- Render uniform cards and a single `Play` action.
- Resolve and launch selected IDs through Root.
- Add production Block Blast to the allowlist and verify it appears
  automatically.

### 6. Legacy removal

Only after the end-to-end catalog-to-Block-Blast path works:

- remove `:feature:home`;
- remove Home bindings, UI, and tests;
- remove the shared `GameSaveApi` if no remaining consumer needs it;
- remove `Continue` and `New Game` callbacks;
- remove Block Blast models and factories from Root public contracts;
- remove direct Root and `:composeApp` dependencies on `:game:blockblast`;
- update `AGENTS.md`, repository maps, and standard verification commands.

There is no intermediate hard-coded multi-game catalog.

## Failure Handling

- Malformed and duplicate IDs fail registry construction and CI integration.
- An allowlisted path that is missing, not a mini-app module, or absent from the
  assembled registry fails the build/test gate.
- A missing selected ID returns to Catalog and records a host telemetry event.
- A synchronous factory failure renders a host-owned unavailable state; it does
  not leave a half-active session.
- A failed save must follow the concrete game's existing recovery policy and
  must not corrupt the catalog or registry.
- Banner ineligibility removes the banner container. Once policy marks a banner
  eligible, loading and retry may retain its stable reserved height to prevent
  viewport jumps; an SDK failure never blocks game navigation.
- Review suppression or store API failure leaves the game session unchanged.
- A callback carrying an inactive internal session key is ignored; repeated
  close requests are idempotent.
- Session cleanup is driven by Decompose lifecycle. Metro is never assumed to
  invoke destructors for scoped objects.

## Testing and Verification Strategy

### Framework tests

- `MiniAppId` validation and deterministic ordering;
- duplicate registration failure;
- exact ID resolution and missing-ID behavior;
- production allowlist-to-registry completeness;
- one-active-session navigation invariants;
- session creation, retention, and lifecycle destruction;
- Settings overlay retention;
- host Back and system Back equivalence when no overlay is open;
- overlay-first system Back behavior;
- stale-session callback rejection and close idempotency;
- active/obscured/background visibility transitions;
- frame layout with visible and absent banners;
- plugin viewport constraints and safe-area ownership;
- host theme isolation from plugin-local themes.

### Plugin contract tests

Both Block Blast and Counter must pass the same reusable suite for manifest,
graph creation, rendering, close, and lifecycle behavior. Block Blast retains
its rule, store, persistence, result, revive, and migration tests.

### Build verification

Implementation planning will resolve the exact Gradle task names after modules
exist. At minimum, verification must include:

- all tests for mini-app framework modules;
- Counter and Block Blast plugin tests;
- Android/iOS integration-host compilation/linking and resource lookup;
- Catalog and Root tests;
- `:composeApp:compileAndroidMain`;
- `:androidApp:assembleDebug`;
- `:composeApp:linkDebugFrameworkIosSimulatorArm64`;
- complete iOS simulator host validation when graph or native capability
  packaging changes;
- an integration assertion that the production registry contains exactly the
  allowlisted IDs and excludes Counter.

## Acceptance Criteria

- A cold launch opens Catalog, not an active game.
- Catalog renders Block Blast solely from its static manifest and has one
  `Play` action.
- `Play` resumes an unfinished Block Blast game or creates a new one.
- Back and system Back close the one active session and return to Catalog.
- With Settings or Review open, system Back dismisses that overlay before the
  session can close.
- Settings opens over the session without destroying or recreating it, and the
  visibility capability reports the session as obscured.
- Common top chrome and the eligible bottom banner occupy host-owned layout
  space outside the plugin viewport.
- Root and `:composeApp` contain no Block Blast model, component, result,
  renderer, or factory reference.
- `:feature:root` has no dependency on a concrete `:game:*` module.
- Block Blast owns Playing, Result, Revive, persistence decisions, and future
  replay behavior.
- Block Blast has no dependency on a feature module, application shell, or
  advertising SDK adapter.
- Every active plugin uses its own `MiniAppSessionScope` child graph, while
  persistent state survives session destruction.
- The registry is immutable, deterministic, and fails on duplicate IDs.
- One production allowlist entry containing project path and expected stable ID
  is the only composition edit needed to ship a reviewed plugin.
- Counter proves the complete framework path but is absent from production
  packaging and Catalog.
- A freshly scaffolded plugin builds and passes contract tests without manual
  Root, Catalog, renderer, or AppGraph code.
- Existing Block Blast saves remain readable after migration.
