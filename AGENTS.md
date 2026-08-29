# BlockBlast Agent Guide

## Purpose

BlockBlast (published as **Logica — Block Puzzle**) is a Kotlin Multiplatform
block-puzzle game. Kotlin and Compose Multiplatform provide the shared product
and presentation layers for Android and iOS; the Android and SwiftUI shells
host platform SDK integrations.

`AGENTS.md` is the canonical guide for every coding agent, including Codex.
Read it before inspecting or changing the repository. `CLAUDE.md` and
`GEMINI.md` are intentionally thin pointers so their instructions cannot
diverge.

## Start Here

Before changing code:

1. Read this file and the nearest affected module's `build.gradle.kts`.
2. Inspect `settings.gradle.kts` and `gradle/libs.versions.toml` if the work
   affects modules, platforms, plugins, or dependencies.
3. Prefer the codebase knowledge graph for Kotlin code discovery when it is
   available. Index the repository root first if the graph is missing or stale.
4. Read every relevant skill under `.agents/skills/` before changing Kotlin,
   Compose, Decompose, MVIKotlin, Metro, Android, iOS, analytics, or marketing
   work. Use only skills relevant to the requested change.
5. Verify assumptions against source and tests. Do not infer behavior merely
   from the app name, package names, or the version catalog.

## Repository Map

```text
BlockBlast/
├── androidApp/                 Android application, manifest, Firebase and ads
├── iosApp/                     SwiftUI host, iOS ATT and native SDK packaging
├── composeApp/                 Shared app UI, app-owned resources and composition
├── core/
│   ├── common/                 Reusable shared app utilities and infrastructure
│   ├── domain/                 Reusable domain contracts and app-level models
│   ├── data/                   Repository implementations and settings persistence
│   ├── telemetry/              Firebase analytics and Crashlytics abstraction
│   ├── pattern/                Exact generic temporal patterns and bounded queries
│   └── uikit/                  Shared Compose design system and adaptive game scaffold
├── feature/
│   ├── root/                   Top-level navigation component
│   ├── catalog/                Registry-backed MiniApp catalog and host-owned cards
│   ├── review/                 App review policy and component
│   └── settings/               Settings feature
├── game/
│   ├── blockblast/             Block Blast rules, persistence, resources, components and UI
│   └── twentyfortyeight/       Discovered, unshipped 2048 MiniApp
├── monetization/
│   ├── core/                   SDK-free entitlement and advertising policy
│   └── ads/                    AdMob, UMP, ATT bridge and Compose ad adapter
├── miniapp/
│   ├── api/                    Stable, Compose-free MiniApp domain contracts
│   ├── compose/                Compose-facing plugin, session and manifest contracts
│   ├── metro/                  Immutable Metro registry and session-scope foundation
│   ├── storage/                Namespaced Settings backend and game-data reset coordinator
│   ├── audio/                  Procedural-audio API, shared renderer and platform sinks
│   ├── audio-presets/          Original reusable instruments, SFX and soundscapes
│   ├── testkit/                Reusable MiniApp host, visibility, lifecycle and contract fixtures
│   ├── samples/
│   │   └── counter/            Discovered, unshipped reference MiniApp plugin
│   ├── bundle/                 Production MiniApp shipping bundle derived from the settings allowlist
│   └── integration-test/       Non-shipping Counter aggregation, Root/frame and platform proofs
├── build-logic/
│   ├── convention/             Local Kotlin Multiplatform Gradle convention plugin
│   └── miniapp-settings/       Isolated settings-phase MiniApp discovery Gradle plugin
├── gradle/libs.versions.toml   Central versions, libraries, bundles and plugins
├── fastlane/                   Store metadata and release automation
└── .agents/skills/             Repository-local guidance for agents
```

### Module Responsibilities and Dependencies

| Module | Responsibility | Important dependencies |
|---|---|---|
| `:androidApp` | Android app entry point, packaging, Android SDK integration and Decompose Root retention across configuration changes | `:composeApp` |
| `iosApp` | Native SwiftUI host, iOS ATT lifecycle and SDK packaging | Imports the `ComposeApp` framework |
| `:composeApp` | Shared application UI, app-owned resources, the common MiniApp frame and app composition | core, catalog/root/settings/review, monetization, the production MiniApp bundle and `:miniapp:compose` contracts |
| `:core:domain` | Reusable platform-neutral domain contracts and app-level models | no project dependency declared |
| `:core:common` | Shared reusable utilities and common infrastructure | no project dependency declared |
| `:core:data` | App settings, reusable audio playback and repository implementations | `:core:domain`, `:core:common` |
| `:core:telemetry` | Shared analytics and crash-reporting facade | `:core:domain` |
| `:core:pattern` | Exact, bounded and audio-independent temporal event patterns | no project dependency declared |
| `:core:uikit` | Shared Compose theme, design-system components and the reusable Material 3 adaptive game scaffold | Compose convention, including Material 3 Adaptive |
| `:feature:settings` | Settings components, stores and the confirmation/progress/result state holder for game-data reset | `:core:domain`, `:core:common`, `:miniapp:api` |
| `:feature:catalog` | Registry-backed MiniApp catalog with adaptive host-owned Material 3 list cards and direct Play actions | `:miniapp:compose`, `:core:uikit`, Decompose, Compose resources and Haze |
| `:feature:review` | Reusable app-review policy, prompt persistence, analytics and component | `:core:domain`, `:core:common`, multiplatform-settings |
| `:feature:root` | Decompose Catalog/running-MiniApp navigation and sheet ownership. Its runtime coordinator owns session creation, visibility, stale callbacks, session-bound audio opening/closure, reset/launch serialization and teardown-before-clear ordering. | core modules, `:feature:catalog`, `:feature:review`, `:feature:settings`, `:miniapp:api`, `:miniapp:audio`, `:miniapp:compose` |
| `:game:blockblast` | Block Blast rules, models, persistence, bundled-audio filename mapping, Metro child graph, MiniApp plugin/session, components, tests and Compose UI | `logica.miniapp` convention; MiniApp/core contracts, ConfettiKit and MVIKotlin; no raw Settings, platform-audio or native-ad dependency |
| `:game:twentyfortyeight` | Discovered, unshipped 2048 MiniApp scaffold and future game-owned rules, persistence, session graph, UI and tests | `logica.miniapp` convention; approved inward core and MVI dependencies; absent from the production allowlist and bundle |
| `:monetization:core` | SDK-neutral entitlement state and advertising policy | no project dependency declared |
| `:monetization:ads` | AdMob/UMP integration, ATT bridge, banners and interstitials | `:monetization:core` |
| `:miniapp:api` | Stable Compose-free IDs, storage-key helpers, review/session and visibility contracts | kotlinx serialization, coroutines |
| `:miniapp:compose` | Compose-facing MiniApp plugin, session, audio-bound session context, optional host-toolbar content, manifest, registry and interstitial-capability contracts | `:miniapp:api`, `:miniapp:audio`, Compose, resources, Decompose |
| `:miniapp:metro` | Immutable app-scoped MiniApp registry, empty-capable Metro set bindings, session-scope marker and retained graph handle | `:miniapp:compose`, Metro |
| `:miniapp:storage` | App infrastructure for namespace-bound storage, legacy aliases and best-effort all-game-data reset | `:miniapp:api`, `:core:common`, `:core:domain`, Multiplatform Settings |
| `:miniapp:audio` | Procedural Music/SFX declarations, validation, shared PCM rendering and platform playback. Android uses an app-scoped streaming `AudioTrack` sink with float PCM and PCM16 fallback. iOS renders on a session-owned producer thread into a fixed SPSC stereo PCM ring; `AVAudioSourceNode` only drains prepared PCM through preallocated bridge buffers. | `:core:pattern`, `:miniapp:api`, `:core:common`, `:core:domain`, Essenty lifecycle; no Compose or feature dependency |
| `:miniapp:audio-presets` | Original reusable instrument, SFX and deterministic soundscape fragments authored only through the public audio API | `:miniapp:audio` only |
| `:miniapp:bundle` | Production MiniApp bundle with the generated registry expectation and allowlist verification | `:miniapp:metro`, allowlisted MiniApp projects only |
| `:miniapp:testkit` | Reusable recording host, no-op audio/storage, mutable visibility source, lifecycle harness and plugin-contract assertions | MiniApp API, Compose and Metro contracts, Decompose, Compose resources, kotlin-test |
| `:miniapp:samples:counter` | Generated reference plugin proving component state, runtime session inputs, child-graph scoping, retained sessions and asset-free procedural Music/SFX authoring | `logica.miniapp` convention; discovered automatically and intentionally absent from the shipping allowlist |
| `:miniapp:integration-test` | Non-shipping host proving Counter Metro aggregation, generic Root/session lifecycle, real MiniAppFrame layout and Android/iOS Compose resources | `:miniapp:samples:counter` as `commonMainApi`, `:miniapp:metro`, `:miniapp:testkit`; test-only host composition dependencies |
| `build-logic:convention` | Shared KMP setup for library modules | included Gradle build, not runtime code |
| `build-logic:miniapp-settings` | Settings-phase discovery and typed shipping model for MiniApp projects | isolated Gradle plugin artifact; Gradle API only |

Keep dependencies flowing inward. In particular, core modules must not depend on
features or UI, and feature modules must not depend on `:composeApp` or either
native application shell. Treat a new cross-feature dependency as an
architecture decision, not a convenience import.

Game modules own all rules, game-specific models, persistence, components and
UI for their respective games. `:feature:root` hosts a selected plugin session, while
the game module must not depend on `:composeApp`, `:feature:root` or a native
application module. Block Blast's engine and implementation details stay
`internal`; only the MiniApp plugin, concrete session binding type, child-graph
factory and app binding bridge cross the module boundary. A session binding
type may be public for cross-module Metro aggregation, but its constructor and
state remain internal. Block Blast also owns its transient
Playing/Result, resume, revive and review-opportunity session flow; the common
host remains responsible for leaving the MiniApp.

Games may emit game-specific review opportunities, but `:feature:review` owns
the app-wide prompt limit, persistence, suppression, analytics and store-review request.
`:feature:root` decides when to open the reusable review sheet.

MiniApp Crashlytics context is runtime-scoped and best-effort. Synchronous, caught
MiniApp session-creation failures are recorded as non-fatal exceptions; launch,
close and visibility transitions are diagnostic breadcrumbs and custom values,
not exceptions. Root installs no global exception handler. Normal analytics
remains a separate channel and does not replace Crashlytics context.

Keep monetization policy in `:monetization:core`; it must not depend on Compose,
Firebase, advertising SDKs, or either application shell. Native AdMob and UMP
dependencies belong in `:monetization:ads`. Product configuration, such
as ad unit IDs and the current entitlement, enters through `:composeApp`.

MiniApp dependencies also flow inward. `:miniapp:api` is Compose-free and owns
only stable portable contracts. `:miniapp:compose` depends on that API and owns
the UI-facing plugin/session contracts. `:miniapp:metro` owns the immutable
app-scoped registry and its empty-capable compile-time plugin aggregation; its
session-scope marker is a child lifecycle, not a second game-specific scope.
Block Blast implements `MiniAppPlugin` through a retained Metro child graph;
session-owned components and reducers live in `MiniAppSessionScope`, while save,
best-score and preference repositories remain app-scoped. Game plugins consume
`MiniAppInterstitialCapability` and must not depend on a MiniApp host,
`:feature:root`, `:composeApp`, native application modules or native-ad modules.
`:composeApp` renders every running session inside one host-owned frame. Back,
Settings, toolbar sizing and accessibility, safe-area ownership and the
conditional banner stay host-owned. A session may contribute optional center
content through `MiniAppSession.TopBarContent`; that content renders in the
host theme, outside the plugin viewport. Plugin-local themes therefore remain
confined to `MiniAppSession.Content` and cannot leak into host chrome. Sessions
publish a Decompose `Value<MiniAppFrameMode>` derived from their active internal
child; the default is `Standard`, while screens such as results can select
`ContentOnly` without imperative visibility flags on `MiniAppPlugin` or leaking
game-specific navigation types into the host. Root owns navigation and sheets,
but does not impose a background or color theme on its children. Catalog owns
its ambient background. A MiniApp session may draw its background through the
host's full-frame background layer so it also sits behind common chrome, while
the plugin-local content theme remains confined to the viewport; Root
transitions must preserve those boundaries.

Every plugin receives one `MiniAppSessionContext`, including its lifecycle,
visibility, host callbacks, an ID-bound `MiniAppStorage` and a stale-safe
`MiniAppAudio` facade. Root opens audio before plugin session creation and
closes provisional or destroyed sessions with the same ID/key. MiniApps use
that facade for procedural playback rather than importing platform audio or
reading Settings. Counter is the copyable authoring example. Block Blast is a
legacy exception: its semantic `BlockBlastAudioPlayer` maps game events to its
existing bundled filenames and delegates to the app-owned `AudioRepository`;
its Store still handles neither filenames nor DSP commands. The shared
file-audio pipeline is not a public MiniApp authoring capability.
Persistent values use local snake-case keys and versioned snapshot specs;
MiniApps must not import
`com.russhwolf.settings` or construct physical storage keys. The host-side
`:miniapp:storage` backend owns namespacing and compatibility aliases. When the
user deletes all game data, Root first navigates to Catalog and awaits active
session destruction, keeps the Settings flow visible, and only then performs a
best-effort reset across shipped MiniApp IDs. App preferences, consent,
entitlement and review-policy data are outside those namespaces and survive.

Block Blast drag, grid and tray hit-testing measurements use window
coordinates, reflected by `InWindow` names. Convert points and rectangles only
at viewport-local rendering boundaries through the shared `windowToViewport`
contract; overlay and effect inputs use explicit `InViewport` names.

The root settings `miniApps` allowlist is the sole authoritative shipping path:
the bundle convention consumes its finalized declarations in order, adds exactly
those projects to `commonMainApi` alongside `:miniapp:metro`, and generates the
public `ProductionMiniAppExpectation` contributed to Metro. Block Blast is the
sole current production allowlist entry and shipped MiniApp; Counter and 2048
remain discovered but excluded from the allowlist and unshipped.
`verifyMiniAppBundle` rejects missing,
unexpected or duplicated bundle dependencies, and allowlisted projects that do
not apply `logica.miniapp`.

## MiniApp Contributor Workflow

Create and verify a reviewable contributor project with:

```bash
./gradlew createMiniApp -PminiAppId=game.snake -PminiAppName=Snake
./gradlew :game:snake:allTests :game:snake:validateMiniAppDependencies
```

The task accepts only direct `:game:<name>` and
`:miniapp:samples:<name>` paths (or the matching explicit
`-PminiAppProjectPath`), never overwrites an existing project, and writes
through a sibling staging directory. Discovery makes every project under
`game/*` and `miniapp/samples/*` independently buildable on the next Gradle
invocation. There is no server, runtime catalog download or remote plugin
loading.

Generated projects apply only `logica.miniapp`. That convention supplies KMP, Compose resources, Metro, one direct `:miniapp:metro` framework edge, reusable `:miniapp:audio-presets` as an implementation dependency, and dependency-boundary validation. Contributors can use `MiniAppSessionContext.audio` and shared audio presets without declaring audio dependencies. They may use stable `:miniapp:*` contracts and the allowed inward core contracts, but must not depend on feature, application, concrete game/sample, data/telemetry, native-ad modules, platform audio APIs or external audio engines. Use `:miniapp:compose MiniAppInterstitialCapability` rather than `:monetization:ads`.

Do not inject or call the legacy `AudioRepository` from a generated MiniApp.
Its bundled-file path is retained only for Block Blast and is not part of the
contributor contract.

Generated `@GraphExtension.Factory` methods are namespaced from the complete
MiniApp ID (for example, `createGameSnakeSessionGraph`). Preserve that naming:
Kotlin cannot merge factories that differ only by return type when multiple
MiniApps are aggregated into the same native application graph. Each child
graph exposes its concrete session type instead of a qualified
`MiniAppSession`; this keeps sibling bindings distinct without `@Named` strings.

Discovery and shipping are intentionally separate: a scaffold becomes
discoverable on the next Gradle invocation, but only an exact
`miniApps.include(projectPath, expectedId)` entry ships it. A maintainer adds
that single allowlist entry only after review. Never treat discovery as
production authorization.

Plugins depend inward on MiniApp contracts and typed capabilities, never on
features, application modules or SDK adapters. Every active plugin creates one
`MiniAppSessionScope` child graph retained by its `MiniAppSession` handle. Root
owns Catalog/Running navigation, Settings/Review, Back, visibility and stale
callback rejection. Block Blast owns resume/new-game choice,
Playing/Result/Revive, persistence and any future Replay behavior.

Use `MiniAppSessionContext.storage` for every new persistent value or versioned
snapshot. Block Blast's existing `blockblast.game_save`,
`blockblast.best_score` and `blockblast.tutorial_seen` physical keys are
declared as compatibility aliases and must not be migrated merely to satisfy
the new convention. Plugins cannot provide Catalog
cards, host Back/Settings controls, the host toolbar, ad containers or Replay
actions. Replay is deliberately absent from the initial public MiniApp API.

## MiniApp Procedural Audio Authoring

When creating, changing, reviewing or diagnosing Music/SFX in a MiniApp, read
`.agents/skills/miniapp-procedural-audio/SKILL.md` and follow its routed
references. Human authors start at `docs/miniapp/audio/getting-started.md`.
The complete public-author documentation is under `docs/miniapp/audio/`.

Follow this order: reuse a declaration from `:miniapp:audio-presets`; tune its
public name/seed/gain/density/stereo controls; compose renamed presets; create
an original game-owned declaration only when the required role still cannot be
expressed. Do not transcribe Klang, Strudel, commercial music, game soundtracks
or third-party demo compositions. An 8/16/32/64-bit request describes an
aesthetic implemented with synthesis constraints and bit-crush/sample-rate
effects; platform output remains high-quality PCM.

Audio programs are immutable game-owned declarations outside Compose UI.
Receive the session-bound `MiniAppAudio` through `MiniAppSessionContext`/Metro,
use typed control and SFX names, handle command rejection, and let host
visibility, settings and lifecycle own suppression and teardown. MiniApps must
not import audio `internal` packages, `MiniAppAudioEngine`, platform sinks or
native players. Compile every documented/example declaration in `commonTest`;
new voices and presets should add deterministic acoustic render assertions.

On iOS, scheduling, command consumption, DSP work and any associated allocation
belong exclusively to the session producer side. The native audio callback must
only drain the fixed PCM ring, copy through preallocated channel buffers and
update atomic diagnostics; it must never acquire lifecycle locks or reach the
runtime, scheduler or renderer directly.

The maintainer architecture is recorded in
`docs/superpowers/specs/2026-08-23-kotlin-pattern-audio-design.md`; staged work
and verification live in
`docs/superpowers/plans/2026-08-23-miniapp-procedural-audio.md`. These are
maintainer references, not authorization for contributors to modify the engine.

The catalog uses a centered `Logica` app bar and an adaptive grid: one column
below 840 dp and two columns at or above it, capped at 1200 dp. Each host-owned
card contains one Material 3 `ListItem`, and its trailing Play button launches
the MiniApp. Card details, context menus, Share actions and deep links are
deliberately deferred and must not be exposed as inert catalog UI.

## Source Placement and Architecture

- Put portable Kotlin in `commonMain`; keep tests in the matching `commonTest`
  source set unless a platform API is under test.
- Put Android-specific Kotlin and SDK calls in `androidMain` or `androidApp`.
- Put iOS-specific Kotlin in `iosMain` and native Swift/SwiftUI lifecycle code
  in `iosApp/iosApp`.
- Keep UI rendering in Compose or SwiftUI views. Business rules, persistence,
  service lookup, routing decisions and domain mutation belong outside views.
- Preserve the existing Decompose, MVIKotlin and Metro patterns. Their presence
  in the catalog is not authorization to introduce unrelated infrastructure.
- Keep `expect`/`actual` contracts small and platform-neutral. Prefer a common
  implementation when no platform API is required.
- Do not bypass the consent gate when changing advertising. Android requests
  require UMP permission; iOS runs ATT first and then UMP. Mobile Ads
  initialization and ad loading must remain behind the combined gate.

## Code Discovery

When codebase-memory MCP tools are available, use them in this order:

1. `search_graph` to find functions, classes, variables and entry points.
2. `trace_path` to inspect callers, callees, dependencies and data flow.
3. `get_code_snippet` after resolving the exact qualified name.
4. `query_graph` for structural questions that need a graph query.
5. `get_architecture` for a high-level repository map.

Use `rg` for string literals, error messages, configuration values,
documentation and files not indexed by the graph. Re-index after material
structural changes.

## Dependencies and Build Logic

- Declare versions and external artifacts in `gradle/libs.versions.toml`.
- Declare project dependencies in the consuming module's `build.gradle.kts`.
- Put shared KMP configuration in `build-logic/convention`; keep one-off module
  configuration local.
- The shared Compose convention supplies Material 3 Adaptive to `commonMain` and
  Compose UI testing to `commonTest` for Compose modules.
  Reusable breakpoint and pane policy belongs in `:core:uikit`; games consume the
  shared `AdaptiveGameScaffold` instead of redefining size classes or layout modes.
- Keep settings-phase MiniApp discovery in the isolated
  `build-logic/miniapp-settings` plugin artifact so applying it in root settings
  does not place project convention plugin descriptors on the build classpath.
  Its public shipping-model types are the compileOnly contract for later
  build-logic consumers.
- The convention plugin configures JDK 17, Android and iOS ARM64/simulator
  targets for its KMP library modules. Check a module's build file before
  assuming an additional target exists.
- Build-logic plugin bytecode has a narrower compatibility boundary: the
  settings-only MiniApp contract targets Java 17, while the convention artifact
  targets Java 21 because applying Metro 1.4.1 requires its Java 21 Gradle
  plugin. This does not change the app KMP JDK target.
- Preserve type-safe project accessors and Gradle repository configuration in
  `settings.gradle.kts`.
- Never edit generated build output or local-machine configuration.

## Verification

Run commands from the repository root with the checked-in Gradle wrapper. Run
the narrowest relevant task first, then broaden verification as appropriate.

```bash
# Discover supported module tasks
./gradlew projects
./gradlew :module:tasks --all

# Run a module's multiplatform tests
./gradlew :core:domain:allTests
./gradlew :core:data:allTests
./gradlew :game:blockblast:allTests
./gradlew :game:twentyfortyeight:allTests
./gradlew :game:twentyfortyeight:validateMiniAppDependencies
./gradlew :game:twentyfortyeight:compileAndroidMain
./gradlew :game:twentyfortyeight:compileKotlinIosSimulatorArm64
./gradlew :feature:review:allTests
./gradlew :feature:catalog:allTests
./gradlew :feature:root:allTests
./gradlew :monetization:core:allTests

# Verify settings-phase MiniApp discovery and its typed shipping model
./gradlew -p build-logic :miniapp-settings:test --tests '*MiniAppSettingsPluginTest'

# Verify the authoritative MiniApp shipping bundle and its generated expectation
./gradlew -p build-logic :convention:test --tests '*MiniAppBundlePluginTest'
./gradlew :miniapp:bundle:verifyMiniAppBundle
./gradlew :miniapp:bundle:dependencies --configuration commonMainApi
./gradlew :miniapp:bundle:compileAndroidMain
./gradlew :miniapp:bundle:compileKotlinIosSimulatorArm64

# Verify the reusable MiniApp contract fixtures and the discovered Counter reference plugin
./gradlew :miniapp:testkit:allTests
./gradlew :miniapp:testkit:compileAndroidMain
./gradlew :miniapp:testkit:compileKotlinIosSimulatorArm64
./gradlew :miniapp:samples:counter:allTests
./gradlew :miniapp:samples:counter:compileAndroidMain
./gradlew :miniapp:samples:counter:compileKotlinIosSimulatorArm64

# Verify the non-shipping Counter integration host and real platform resources
./gradlew :miniapp:integration-test:allTests
./gradlew :miniapp:integration-test:testAndroidHostTest
./gradlew :miniapp:integration-test:iosSimulatorArm64Test
./gradlew :miniapp:integration-test:compileAndroidMain :miniapp:integration-test:linkDebugFrameworkIosSimulatorArm64

# Verify contributor conventions and root task registration without generating a project
./gradlew -p build-logic :convention:test --tests '*MiniAppConventionPluginTest' --tests '*MiniAppDependencyBoundaryTest' --tests '*ValidateMiniAppDependenciesTaskTest' --tests '*CreateMiniAppTaskTest'
./gradlew -p build-logic :convention:validatePlugins
./gradlew tasks --all

# Verify the stable MiniApp API and Compose-facing contracts
./gradlew :miniapp:api:allTests
./gradlew :miniapp:compose:allTests
./gradlew :miniapp:compose:compileAndroidMain
./gradlew :miniapp:compose:compileKotlinIosSimulatorArm64
./gradlew :miniapp:metro:allTests
./gradlew :miniapp:metro:compileAndroidMain
./gradlew :miniapp:metro:compileKotlinIosSimulatorArm64

# Verify namespaced MiniApp persistence and best-effort reset
./gradlew :miniapp:storage:allTests
./gradlew :miniapp:storage:compileAndroidMain
./gradlew :miniapp:storage:compileKotlinIosSimulatorArm64

# Verify generic temporal patterns and procedural-audio modules
./gradlew :core:pattern:allTests
./gradlew :core:pattern:compileAndroidMain :core:pattern:compileKotlinIosSimulatorArm64
./gradlew :miniapp:audio:allTests
./gradlew :miniapp:audio:testAndroidHostTest
./gradlew :miniapp:audio:compileAndroidMain :miniapp:audio:compileKotlinIosSimulatorArm64
./gradlew :miniapp:audio-presets:allTests
./gradlew :miniapp:audio-presets:compileAndroidMain :miniapp:audio-presets:compileKotlinIosSimulatorArm64

# Verify shared Android compilation and package the Android app
./gradlew :composeApp:compileAndroidMain
./gradlew :androidApp:assembleDebug

# With an unlocked emulator/device, verify the live Root and MiniApp session survive Activity recreation
./gradlew :androidApp:connectedDebugAndroidTest

# Verify the Compose framework for the iOS simulator
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

For changes to the advertising adapter, also compile its iOS target and verify
the generated SwiftPM linkage package:

```bash
./gradlew :monetization:ads:compileKotlinIosSimulatorArm64
XCODEPROJ_PATH="$PWD/iosApp/iosApp.xcodeproj" \
  ./gradlew :composeApp:integrateLinkagePackage -i
```

For changes to the SwiftUI host, Xcode project, signing, iOS Firebase or iOS
ad SDKs, also validate the appropriate Xcode scheme and simulator. Gradle
framework linking does not validate the complete native iOS application.

## Change Discipline

- Inspect neighboring code and tests before selecting names, packages or
  patterns. Add or update tests for changed behavior.
- Keep changes scoped to the request; do not add speculative abstractions,
  dependencies or feature scaffolding.
- Preserve unrelated working-tree changes.
- Do not modify `.gradle/`, `.idea/`, `.kotlin/`, `build/`, `local.properties`,
  `xcuserdata`, or `.DS_Store` as source work.
- Do not expose, commit, regenerate or casually edit credentials and release
  configuration such as keystores, `google-services.json`, Google service
  plists, signing environment variables or private keys.
- Report the exact verification commands executed and any platform work that
  remains unverified.

## Maintaining This Guide

Update `AGENTS.md` in the same change whenever modules, targets, source
placement, dependency direction, app entry points, architecture conventions or
standard verification commands change. Keep `CLAUDE.md` and `GEMINI.md` as
thin pointers to this file.
