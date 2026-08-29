# Logica — Block Puzzle

<img src="picture/app_icon.png" width="180" alt="App icon" />

A Kotlin Multiplatform MiniApp catalog for Android and iOS. The production
bundle currently includes Block Blast and 2048; additional games and apps are
independent Gradle modules reviewed and allowlisted at build time.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-blue.svg)
![Compose](https://img.shields.io/badge/Compose-1.11.1-green.svg)
![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS-orange.svg)

## Download

<a href="https://apps.apple.com/us/app/logica-block-puzzle-2027/id6765924581">
  <img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" alt="Download on the App Store" height="80"/>
</a>

<a href="https://play.google.com/store/apps/details?id=ge.yet.blokblast">
  <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play (closed testing)" height="80"/>
</a>

## Screenshots

<p>
  <img src="picture/screen1_en.png" width="200" />
  <img src="picture/screen2_en.png" width="200" />
  <img src="picture/screen3_en.png" width="200" />
  <img src="picture/screen4_en.png" width="200" />
</p>

## Features

- 🧩 Classic block-puzzle gameplay with smooth animations
- 🎨 Polished Material 3 UI tuned for the Block Blast feel
- 📱 Single codebase for Android & iOS via Compose Multiplatform
- 💾 Persistent settings and best-score tracking
- 🎉 Confetti effects on big clears
- 🎵 Rotating background music across multiple tracks
- 📴 Fully offline — no account required
- ⭐ In-app review prompts on Android
- 📊 Firebase Analytics & Crashlytics
- 🧱 Compile-time MiniApp plugin framework with a uniform catalog and host frame
- 🔌 Contributor games discovered locally and shipped only through reviewable allowlisting

## Tech Stack

- **Kotlin Multiplatform** 2.4.10 — shared business logic
- **Compose Multiplatform** 1.11.1 — declarative UI for Android & iOS
- **Material 3** — design system
- **Decompose** + **Essenty** — navigation & lifecycle
- **MVIKotlin** — predictable state management (MVI)
- **Metro DI** — compile-time dependency injection
- **Kotlinx Coroutines / Serialization / Datetime**
- **Multiplatform Settings** — cross-platform key/value storage
- **Firebase** (GitLive SDK) — Analytics, Crashlytics
- **Google Mobile Ads** + **User Messaging Platform** (Android)
- **ConfettiKit** — celebratory effects
- **Baseline Profiles** — Android startup performance

## Project Structure

```
BlockBlast/
├── androidApp/      # Android entry point (Activity, manifest, ads, Firebase)
├── iosApp/          # iOS entry point (SwiftUI host)
├── composeApp/      # Shared host UI, MiniApp frame and platform composition
├── core/
│   ├── common/      # Shared utilities
│   ├── domain/      # Reusable domain contracts
│   ├── data/        # Settings and shared repositories
│   └── telemetry/   # Analytics and crash facade
├── feature/
│   ├── catalog/     # Registry-backed MiniApp catalog
│   ├── root/        # Catalog/running navigation and host lifecycle
│   ├── review/      # App review policy
│   └── settings/    # Host settings
├── game/
│   ├── blockblast/       # Allowlisted Block Blast MiniApp
│   └── twentyfortyeight/ # Allowlisted 2048 MiniApp
├── miniapp/
│   ├── api/         # Stable Compose-free contracts
│   ├── compose/     # Plugin, manifest, session and frame contracts
│   ├── metro/       # Registry and retained child-graph foundation
│   ├── storage/     # Namespaced persistence and safe all-game-data reset
│   ├── audio/       # Portable procedural Music/SFX API and runtime
│   ├── audio-presets/ # Original reusable instruments, soundscapes and SFX
│   ├── bundle/      # Production allowlisted plugins
│   ├── testkit/     # Contributor contract fixtures
│   └── samples/     # Discovered but unshipped examples
├── build-logic/     # MiniApp discovery, scaffold and convention plugins
└── fastlane/        # Store metadata & changelogs
```

## Getting Started

### Prerequisites
- **JDK 17+**
- **Android Studio** Ladybug or later
- **Xcode 15+** (iOS, macOS only)
- Your own Firebase project — see below

### Firebase setup

This repo does **not** ship Firebase config files. Create your own Firebase project and drop in:

- `androidApp/google-services.json`
- `iosApp/iosApp/GoogleService-Info.plist`

### Build and Run

#### Android

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

#### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run.

## Development

### Tests

```bash
./gradlew test
```

### Create a game / MiniApp

If you are a human contributor or an AI agent and the request is to create,
add or port a game, start with the official scaffold workflow below. Do not
create an arbitrary Gradle module and do not add the game to the production
allowlist automatically.

```bash
./gradlew createMiniApp -PminiAppId=game.snake -PminiAppName=Snake
./gradlew :game:snake:verifyMiniApp
```

The first command creates reviewable source. The next Gradle invocation
discovers `game/*` and `miniapp/samples/*`, but discovery does not ship a
plugin. `verifyMiniApp` checks the module boundary, tests and Android/iOS
compilation. After review, a maintainer adds exactly one matching entry to the
root `miniApps` allowlist. The catalog is compiled into the app; there is no
server, runtime download or remote code loading.

The stable policy and rationale are recorded in
[ADR-0001: MiniApp Contribution and Shipping Workflow](docs/adr/0001-miniapp-contribution-and-shipping.md).
Use the [human MiniApp contributor guide](CONTRIBUTING_MINIAPP.md) for the
end-to-end workflow and the [AI contributor protocol](docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md)
for deterministic agent behavior. The current agent-level architecture and
verification rules are in [AGENTS.md](AGENTS.md). The guides implement the
approved [contributor pipeline design](docs/superpowers/specs/2026-08-23-miniapp-contributor-pipeline-design.md).

Plugins depend only on MiniApp contracts, approved inward core contracts and
typed host capabilities. Root owns Catalog/Running navigation, Back,
Settings/Review, visibility and stale-callback protection. The common frame
owns catalog cards, toolbar controls and ad containers; Replay is intentionally
not part of the initial plugin API.

Use the session-bound `MiniAppStorage` from `MiniAppSessionContext` for new
persistence. Games supply only local snake-case names; the host owns physical
namespacing, snapshot migrations, legacy aliases and the safe all-game-data
reset. MiniApp modules must not depend on Multiplatform Settings directly.
Block Blast's legacy physical keys remain unchanged for save compatibility.

### Create procedural music and SFX

MiniApps can declare original, asset-free Music and SFX in shared Kotlin for
Android and iOS. Start with the
[procedural audio getting-started guide](docs/miniapp/audio/getting-started.md),
then use the [Kotlin DSL reference](docs/miniapp/audio/kotlin-dsl.md),
[shared presets](docs/miniapp/audio/instruments.md), and
[mobile budgets](docs/miniapp/audio/performance-budgets.md).

AI agents must use the repo-local
[MiniApp procedural audio skill](.agents/skills/miniapp-procedural-audio/SKILL.md).
It prioritizes preset reuse, original declarations, deterministic seeds,
session lifecycle correctness and compiled examples. Engine maintainers should
read the approved
[architecture design](docs/superpowers/specs/2026-08-23-kotlin-pattern-audio-design.md)
and [implementation plan](docs/superpowers/plans/2026-08-23-miniapp-procedural-audio.md);
MiniApp authors must not depend on internal DSP or platform sinks.

Block Blast deliberately keeps its original bundled MP3 music and voice clips
through the app-owned legacy `AudioRepository`. This is not a reusable MiniApp
capability or a contributor pattern; new MiniApps use the procedural API unless
a separate architecture decision introduces a general asset-audio contract.

## Contributing

Contributions are welcome. MiniApps generated by the command above remain
unshipped until their source, dependency boundary and allowlist change are
reviewed.

## Support Me
- **ton**: UQCi1XMdZP2fBfTK-O6rsAX3fXEm5iBpjO1D6FDekdUDQnaw
- **btc**: bc1qv2m03vg23227yfnlu0c0jx2ps5yg8v8kvy748s
- **eth**: 0xdF196759E996Fe684c33416282F30d6B9A0b325e
- **usdt(erc20)**: 0xdF196759E996Fe684c33416282F30d6B9A0b325e
- **bnb**: 0xdF196759E996Fe684c33416282F30d6B9A0b325e
- **usdt(trc20)**: TYrBMc4yN4k8im2Qq2G17hv9VdmcYFngpT

## License

This project is open source and available under the MIT License.

## Learn More

- [Kotlin Multiplatform](https://www.jetbrains.com/kotlin-multiplatform/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [MVIKotlin](https://github.com/arkivanov/MVIKotlin)
- [Decompose](https://github.com/arkivanov/Decompose)
- [Metro](https://github.com/ZacSweers/metro)
