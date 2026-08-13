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
│   └── telemetry/              Firebase analytics and Crashlytics abstraction
├── feature/
│   ├── root/                   Top-level navigation component
│   ├── home/                   Home screen feature
│   ├── review/                 App review policy and component
│   └── settings/               Settings feature
├── game/
│   └── blockblast/             Block Blast rules, persistence, resources, components and UI
├── monetization/
│   ├── core/                   SDK-free entitlement and advertising policy
│   └── ads/                    AdMob, UMP, ATT bridge and Compose ad adapter
├── miniapp/
│   ├── api/                    Stable, Compose-free MiniApp domain contracts
│   ├── compose/                Compose-facing plugin, session and manifest contracts
│   ├── metro/                  Framework scaffold; Metro registry work is future work
│   ├── testkit/                Framework scaffold; plugin contract fixtures are future work
│   ├── bundle/                 Framework scaffold; shipping/bundle work is future work
│   └── integration-test/       Framework scaffold; end-to-end integration work is future work
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
| `:androidApp` | Android app entry point, packaging and Android SDK integration | `:composeApp` |
| `iosApp` | Native SwiftUI host, iOS ATT lifecycle and SDK packaging | Imports the `ComposeApp` framework |
| `:composeApp` | Shared application UI, app-owned resources and app composition | core, feature, game and monetization modules |
| `:core:domain` | Reusable platform-neutral domain contracts, including the game-save status API, and app-level models | no project dependency declared |
| `:core:common` | Shared reusable utilities and common infrastructure | no project dependency declared |
| `:core:data` | App settings, reusable audio playback and repository implementations | `:core:domain`, `:core:common` |
| `:core:telemetry` | Shared analytics and crash-reporting facade | `:core:domain` |
| `:feature:settings` | Settings components and stores | `:core:domain`, `:core:common` |
| `:feature:review` | Reusable app-review policy, prompt persistence, analytics and component | `:core:domain`, `:core:common`, multiplatform-settings |
| `:feature:home` | Home components and stores | `:core:domain`, `:core:common` |
| `:feature:root` | Top-level navigation, sheet ownership and feature composition | core modules, `:feature:home`, `:feature:review`, `:feature:settings`, `:game:blockblast` |
| `:game:blockblast` | Block Blast rules, models, save/best-score/tutorial persistence, resources, audio catalog, components, tests and Compose UI | `:core:common`, `:core:domain`, `:core:uikit`, `:monetization:ads`, Compose, Decompose, MVIKotlin, Metro |
| `:monetization:core` | SDK-neutral entitlement state and advertising policy | no project dependency declared |
| `:monetization:ads` | AdMob/UMP integration, ATT bridge, banners and interstitials | `:monetization:core` |
| `:miniapp:api` | Stable Compose-free IDs, storage-key helpers, review/session and visibility contracts | kotlinx serialization, coroutines |
| `:miniapp:compose` | Compose-facing MiniApp plugin, session, manifest, registry and interstitial-capability contracts | `:miniapp:api`, Compose, resources, Decompose |
| `:miniapp:metro`, `:miniapp:testkit`, `:miniapp:bundle`, `:miniapp:integration-test` | Statically included framework scaffolds; responsibilities will be filled by subsequent MiniApp tasks | no framework implementation yet |
| `build-logic:convention` | Shared KMP setup for library modules | included Gradle build, not runtime code |
| `build-logic:miniapp-settings` | Settings-phase discovery and typed shipping model for MiniApp projects | isolated Gradle plugin artifact; Gradle API only |

Keep dependencies flowing inward. In particular, core modules must not depend on
features or UI, and feature modules must not depend on `:composeApp` or either
native application shell. Treat a new cross-feature dependency as an
architecture decision, not a convenience import.

Game modules own all rules, game-specific models, persistence, components and
UI for their respective games. `:feature:root` composes the current game, while
the game module must not depend on `:composeApp`, `:feature:root` or a native
application module. Block Blast's internal engine and implementation types stay
`internal`; only the component and model contracts required by Root, Home and
the application composition are public.

Games may emit game-specific review opportunities, but `:feature:review` owns
the app-wide prompt limit, persistence, suppression, analytics and store-review request.
`:feature:root` decides when to open the reusable review sheet.

Keep monetization policy in `:monetization:core`; it must not depend on Compose,
Firebase, advertising SDKs, or either application shell. Native AdMob and UMP
dependencies belong in `:monetization:ads`. Product configuration, such
as ad unit IDs and the current entitlement, enters through `:composeApp`.

MiniApp dependencies also flow inward. `:miniapp:api` is Compose-free and owns
only stable portable contracts. `:miniapp:compose` depends on that API and owns
the UI-facing plugin/session contracts, but does not implement a registry or
host composition. Concrete games may later implement the plugin contract; they
must not depend on a MiniApp host, `:feature:root`, `:composeApp`, or a native
application module. Host/root migration and the Metro registry remain separate
follow-up work.

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
- Keep settings-phase MiniApp discovery in the isolated
  `build-logic/miniapp-settings` plugin artifact so applying it in root settings
  does not place project convention plugin descriptors on the build classpath.
  Its public shipping-model types are the compileOnly contract for later
  build-logic consumers.
- The convention plugin configures JDK 17, Android and iOS ARM64/simulator
  targets for its KMP library modules. Check a module's build file before
  assuming an additional target exists.
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
./gradlew :feature:review:allTests
./gradlew :feature:root:allTests
./gradlew :monetization:core:allTests

# Verify settings-phase MiniApp discovery and its typed shipping model
./gradlew -p build-logic :miniapp-settings:test --tests '*MiniAppSettingsPluginTest'

# Verify the stable MiniApp API and Compose-facing contracts
./gradlew :miniapp:api:allTests
./gradlew :miniapp:compose:allTests
./gradlew :miniapp:compose:compileAndroidMain
./gradlew :miniapp:compose:compileKotlinIosSimulatorArm64

# Verify shared Android compilation and package the Android app
./gradlew :composeApp:compileAndroidMain
./gradlew :androidApp:assembleDebug

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
