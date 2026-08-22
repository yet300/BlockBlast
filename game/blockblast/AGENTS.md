# Block Blast MiniApp

This module owns Block Blast rules, models, save format, audio resources,
Playing/Result/Revive navigation, Compose UI and its retained Metro child graph.
The host sees only `BlockBlastPlugin`, the graph/app binding bridges and stable
MiniApp contracts.

## Boundaries

- Depend inward on `:miniapp:*` contracts and approved core contracts. Never
  depend on a feature, `:composeApp`, a native app or `:monetization:ads`.
- Keep engine, component, result and UI implementation details `internal`.
  `BlockBlastSession` is public only as the graph's unique Metro binding type;
  its constructor and state remain internal.
- Create session-owned components through `BlockBlastSessionGraph`; app-scoped
  repositories and resource providers belong in `BlockBlastAppBindings`.
- Consume `MiniAppVisibilitySource` and typed capabilities supplied at session
  creation. Do not add host navigation, Back, Settings, catalog-card or banner
  behavior to the plugin.
- Replay remains a future host action and is not part of the public API.

## Persistence

Use `MiniAppId("game.blockblast").storageKey(localName)` for every new key.
These existing keys are compatibility exceptions and must retain their values:

- `blockblast.game_save`
- `blockblast.best_score`
- `blockblast.tutorial_seen`

Do not change the save key, JSON schema/version handling, or clear/load/save
semantics without an explicit migration and compatibility tests.

## Verification

```bash
./gradlew :game:blockblast:validateMiniAppDependencies
./gradlew :game:blockblast:allTests
./gradlew :miniapp:bundle:verifyMiniAppBundle
```
