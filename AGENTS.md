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
├── iosApp/                     SwiftUI host, iOS ads and consent integration
├── composeApp/                 Shared Compose app, resources and UI platform code
├── core/
│   ├── common/                 Reusable shared app utilities and infrastructure
│   ├── domain/                 Game rules, models and domain contracts
│   ├── data/                   Repository implementations and settings persistence
│   └── telemetry/              Firebase analytics and Crashlytics abstraction
├── feature/
│   ├── root/                   Top-level navigation component
│   ├── home/                   Home screen feature
│   ├── game/                   Gameplay, result flow and review prompt
│   └── settings/               Settings feature
├── build-logic/convention/     Local Kotlin Multiplatform Gradle convention plugin
├── gradle/libs.versions.toml   Central versions, libraries, bundles and plugins
├── fastlane/                   Store metadata and release automation
└── .agents/skills/             Repository-local guidance for agents
```

### Module Responsibilities and Dependencies

| Module | Responsibility | Important dependencies |
|---|---|---|
| `:androidApp` | Android app entry point, packaging and Android SDK integration | `:composeApp` |
| `iosApp` | Native SwiftUI application host and iOS SDK bridges | Imports the `ComposeApp` framework |
| `:composeApp` | Shared Compose UI, resources, Android/iOS UI adapters and app composition | core modules and all feature modules |
| `:core:domain` | Platform-neutral game engine, models and contracts | no project dependency declared |
| `:core:common` | Shared reusable utilities and common infrastructure | no project dependency declared |
| `:core:data` | Settings-backed persistence and repository implementations | `:core:domain`, `:core:common` |
| `:core:telemetry` | Shared analytics and crash-reporting facade | `:core:domain` |
| `:feature:settings` | Settings components and stores | `:core:domain`, `:core:common` |
| `:feature:home` | Home components and stores | `:core:domain`, `:core:common`, `:feature:settings` |
| `:feature:game` | Game components, stores, result and review flow | `:core:domain`, `:core:common`, `:feature:settings` |
| `:feature:root` | Top-level navigation and feature composition | core modules, `:feature:home`, `:feature:game` |
| `build-logic:convention` | Shared KMP setup for library modules | included Gradle build, not runtime code |

Keep dependencies flowing inward. In particular, core modules must not depend on
features or UI, and feature modules must not depend on `:composeApp` or either
native application shell. Treat a new cross-feature dependency as an
architecture decision, not a convenience import.

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
- Do not bypass the consent gate when changing advertising. Android and iOS ad
  requests must occur only after the relevant consent flow permits them.

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
./gradlew :feature:game:allTests

# Verify shared Android compilation and package the Android app
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug

# Verify the Compose framework for the iOS simulator
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
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
