# Contributing a MiniApp

This repository accepts games as independent Kotlin Multiplatform MiniApp
modules. The scaffold creates reviewable source; it does not authorize a game
to ship. Discovery, review, merge and production allowlisting are separate
steps.

The stable policy is recorded in
[ADR-0001](docs/adr/0001-miniapp-contribution-and-shipping.md). AI agents must
also follow the [AI contributor protocol](docs/miniapp/AI_CONTRIBUTOR_PROTOCOL.md).

## 1. Describe the game

Before creating source, prepare the fields from
[`submission.schema.json`](docs/miniapp/submission.schema.json):

- a unique lowercase MiniApp ID such as `game.snake`;
- display name, category, authors and a short description;
- rules, controls and the session flow;
- supported device classes and accessibility behavior;
- visual and audio style, including references;
- storage values and requested host capabilities;
- code, art, audio, font, license and AI provenance;
- deterministic acceptance scenarios and known limitations.

Original mechanics are welcome. Existing or licensed intellectual property
requires an approved proposal issue and verifiable rights evidence before
implementation begins. A claim that an asset is AI-generated is not, by itself,
proof that it is original or distributable.

## 2. Generate the module

Use the repository scaffold from the root:

```bash
./gradlew createMiniApp \
  -PminiAppId=game.snake \
  -PminiAppName=Snake
```

For a game, use the optional game profile:

```bash
./gradlew createMiniApp \
  -PminiAppId=game.snake \
  -PminiAppName=Snake \
  -PminiAppProfile=game
```

The game profile adds immutable `GameState`, typed `GameAction`, a pure
`GameEngine` reducer seam and focused engine/component tests. Its `Tick`
action is deliberately a placeholder: contributors must define the actual
rules instead of inheriting mechanics that may not fit their game. Omitting
the profile keeps the smaller basic scaffold.

Only direct `:game:<name>` and `:miniapp:samples:<name>` projects are accepted.
The command must not overwrite an existing project.

The generated module contains the plugin, retained session, Metro child graph,
minimal Compose content, resources and a contract test. Keep the generated
framework wiring unless the game requires a documented extension.

For lifecycle and contract tests, use the shared
`miniapp:testkit` `withMiniAppSession` helper. It supplies a test context,
recording host, mutable visibility source, storage and guaranteed teardown.

## 3. Implement within the boundaries

Game code owns rules, game-specific state, persistence policy, components and
UI. The host owns catalog navigation, Back, Settings, toolbar, banners, safe
areas and session visibility.

Use:

- `MiniAppSessionContext.storage` for persistence;
- local snake-case storage names, never physical keys;
- `MiniAppSessionContext.audio` and public audio presets for new audio;
- `AdaptiveGameScaffold` where the game fits its layout model;
- Compose resources for user-visible text and localized strings.

Do not depend on feature modules, application modules, another game/sample,
native ad adapters, platform audio APIs or raw Multiplatform Settings. Do not
add catalog cards, host controls, Replay actions or a second navigation host.

## 4. Verify before review

Run the generated module gate:

```bash
./gradlew :game:snake:verifyMiniApp
```

It checks the dependency boundary, tests, Android compilation and iOS
Simulator compilation. Also run the focused tests for the engine, persistence,
lifecycle, accessibility and acceptance scenarios. Run `git diff --check`.

Before requesting review, confirm:

- the game has no borrowed code, assets, music, names or distinctive branding;
- all persistent values are namespaced through the MiniApp storage facade;
- session creation, backgrounding, destruction and recreation are safe;
- UI works on compact and wide layouts;
- important actions have accessibility semantics;
- visible text and resources have a localization path;
- provenance and license evidence are included;
- the change is explicitly marked **NOT ALLOWLISTED**.

## 5. Shipping decision

Contributors and automated agents must not edit the root `miniApps` allowlist.
Only a maintainer may add a reviewed module to
[`settings.gradle.kts`](settings.gradle.kts). Being discoverable or merged does
not imply that the game is included in a release.
