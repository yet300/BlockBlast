# DI-Free Domain Architecture

## Goal

Make `core:domain` a plain Kotlin Multiplatform module with no dependency on
Metro. Domain models, rules, strategies, and tests must compile without a DI
plugin or DI annotations.

## Dependency ownership

`core:domain` continues to own `GameSessionReducer`, `ScoreCalculator`, the
`ShapeGenerator` contract, and the internal `WeightedShapeGenerator`
implementation. It does not own their application-level lifecycle or wiring.

The default generator remains hidden behind the `ShapeGenerator` API. A small
DI-independent factory on that API creates the default implementation. This
avoids making `WeightedShapeGenerator` public solely for composition.

## Composition

`feature:game` is the first application layer that consumes the complete game
rules, so `GameBindings` becomes their composition owner. Explicit Metro
providers construct:

- the app-scoped default `ShapeGenerator`;
- `ScoreCalculator`;
- `GameSessionReducer` from those two collaborators.

Provider return types remain explicit. Existing component factory bindings stay
in the same `GameBindings` container.

## Removed coupling

The migration removes:

- `core/domain/.../di/DomainBindings.kt`;
- Metro annotations and imports from domain classes;
- the Metro Gradle plugin from `core:domain`;
- `DomainBindings` imports and registrations from Android and iOS app graphs.

No domain behavior, public game-state contract, persistence, or Store lifecycle
changes as part of this refactor.

## Verification

The change is complete when:

- no production source or build file under `core/domain` references Metro;
- Android and iOS Metro graphs compile without `DomainBindings`;
- domain, game, and root tests pass;
- the shared Android source set compiles.

Commands:

- `./gradlew :core:domain:allTests`
- `./gradlew :feature:game:allTests`
- `./gradlew :feature:root:allTests`
- `./gradlew :composeApp:compileAndroidMain`
