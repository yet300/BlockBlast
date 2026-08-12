# Game Store Single-State Architecture

## Goal

Remove the stateful, application-scoped `GameEngine` and make each retained
`GameStore` the only owner of observable gameplay state.

## State ownership

`GameStore` uses `GameState` directly as its MVIKotlin state type. The
`GameStoreState` wrapper is deleted. This is intentionally narrower than
introducing a new `Loading/Playing/Finished` hierarchy: the current UI contract
already renders `GameState`, and a phase hierarchy would expand the migration
without improving the single-owner guarantee.

`GameState` remains the serializable game-session snapshot. It gains the small
amount of deterministic generator state that previously lived as mutable fields
inside `GameEngine`: the last allocated piece ID and the next deterministic
seed. Persisting those values prevents identifier reuse and makes state
transitions reproducible after restore.

## Pure domain transitions

`GameSessionReducer` is an injected, stateless domain collaborator. It depends
only on `ShapeGenerator` and `ScoreCalculator`. Its public operations receive a
complete `GameState` and return either a new state plus immutable facts or an
explicit rejection. It performs no persistence, coroutine launch, analytics,
audio, or event-stream publication.

The reducer owns:

- starting and restoring rounds;
- placement validation;
- move resolution, line clearing, scoring and combo rules;
- tray generation and piece identifiers;
- game-over calculation;
- rewarded revive transitions;
- review-prompt marking.

The existing `GameEvent` values become transition facts returned directly from
the reducer. They are no longer emitted through `SharedFlow`, so a fact cannot
be dropped or observed with a mismatched state snapshot.

## Store orchestration

`GameStoreFactory` injects `GameSessionReducer` instead of `GameEngine`.
Its executor captures `state()` once for every intent, invokes a pure reducer
operation, dispatches the resulting snapshot, and then performs side effects
for the matching facts.

Initialization returns a complete state from `GameInitializer`; there is no
state collector. Move, restart and revive intents update Store state only via
`Msg.Snapshot`. Music, voice, analytics, review qualification and navigation
remain Store-executor responsibilities.

## Persistence

`GameSaveCoordinator` is created per Store executor. It has no application
scope. The executor supplies its lifecycle-bound scope when scheduling a
debounced save. Explicit terminal and revive saves cancel and join the pending
autosave, then serialize the requested snapshot through a mutex.

Persistence policy:

- routine transitions schedule a 300 ms debounced save;
- terminal state is flushed before `GameCompleted` is published;
- terminal-save failure is logged but does not block Result navigation;
- revive is optimistic in state, but failure to flush restores the exact
  terminal snapshot and publishes `ReviveFailed`;
- `CancellationException` is always rethrown;
- best-score persistence remains monotonic through `SettingsRepository`.

## Compatibility and migration

The public `GameComponent.Model` continues to expose `GameState`, and Result
navigation continues to pass a defensive terminal snapshot. Existing transient
animation fields (`lastClearedCells`, `lastFeedback`, `lastPointsAwarded`) stay
in `GameState` in this migration. Moving them to presentation effects is a
separate change.

The old `GameEngine`, its `StateFlow`, `SharedFlow`, application scope, internal
autosave job, and DI lifecycle are deleted after domain and Store tests cover
the replacement behavior.

## Verification

- `./gradlew :core:domain:allTests`
- `./gradlew :feature:game:allTests`
- `./gradlew :feature:root:allTests`
- `./gradlew :composeApp:compileAndroidMain`
