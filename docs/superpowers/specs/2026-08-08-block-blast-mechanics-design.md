# Block Blast Mechanics Redesign

**Date:** 2026-08-08  
**Status:** Approved design, pending written-spec review  
**Scope:** Classic Block Blast gameplay in `core:domain`, `core:data`, and `feature:game`

## Problem

The current implementation diverges from the intended Block Blast-style loop in four connected areas:

1. Every generated tray contains one small and one medium shape, while the third shape is small another 30% of the time. This makes trays feel consistently undersized.
2. Every fresh round starts from `Grid()`. There is no varied, partially populated opening board after the first-launch tutorial.
3. The score and combo model resets the combo after every non-clearing placement and applies a fractional multiplier from the first clear. It does not support the three-move grace period expected from the target loop.
4. Runtime audio identifiers do not match the packaged resources. Code requests `block_place`, `line_clear_1..4`, and `voice_combo_2..10`, but the bundle contains `block.mp3` and five fixed voice clips. Missing combo clips fall back to `voice_amazing`, causing repeated and overlapping “Amazing” playback.

These are not independent presentation defects. Shape selection, opening-board construction, move resolution, scoring, combo state, feedback selection, and audio playback must agree on one domain result for each move.

## Goals

- Produce varied trays whose size distribution adapts to the current board without constantly issuing small pieces.
- Start approximately half of post-tutorial fresh rounds from interesting, safe partial layouts.
- Preserve a deliberately empty first-launch tutorial round.
- Make score and combo progression predictable, testable, and consistent with the agreed Block Blast-style rules.
- Select at most one voice response per move and never substitute a missing clip with an unrelated phrase.
- Keep gameplay decisions in `core:domain`, orchestration in `feature:game`, platform playback in `core:data`, and rendering in Compose.
- Preserve deterministic seeded games, autosave behavior, and backward compatibility with existing saves.

## Non-goals

- Reproducing proprietary, undocumented probabilities or audio assets from Hungry Studio.
- Adding Adventure Mode, daily puzzles, bombs, rotating pieces, online leaderboards, or remote gameplay configuration.
- Introducing runtime text-to-speech or a bundled neural speech model.
- Adding A/B-test infrastructure for gameplay parameters.
- Rewriting the existing Decompose, MVIKotlin, Metro, save, analytics, or Compose architecture.

## Chosen Approach

Use a domain-centered redesign. `GameEngine` remains the single source of truth and resolves a placement atomically. Pure collaborators handle starter layouts, adaptive tray selection, score calculation, and feedback selection. The engine publishes one move-resolution event after updating `GameState`. `GameStore` serially maps that event to analytics and audio. Compose observes durable state and nonce-bearing presentation data; recomposition does not replay audio.

This approach is preferred over a minimal patch because the reported defects share the same move lifecycle. It is preferred over a fully configurable engine because runtime tuning infrastructure is not required by the current product.

## Domain Model and Move Resolution

A successful placement follows this order:

1. Validate that the selected piece exists and fits at the requested origin.
2. Stamp its cells onto the grid.
3. Detect complete rows and columns and construct the deduplicated set of cleared cells.
4. Clear those cells and determine whether the resulting board is empty.
5. Resolve combo state and score.
6. Remove the used tray piece and generate a replacement tray only when all three pieces have been consumed.
7. Determine whether any remaining piece fits.
8. Select zero or one voice response for the move.
9. Publish the new `GameState` once.
10. Publish one immutable move-resolution event and schedule autosave.

The move-resolution event contains exactly the gameplay outputs needed by its consumers:

- placed piece identifier and number of placed cells;
- cleared cells and number of completed lines;
- whether rows and columns were cleared together;
- whether the board became empty;
- placement, clear, all-clear, and total awarded points;
- resulting combo level and number of misses remaining before reset;
- optional voice response;
- game-over result.

The event is the authoritative input for placement SFX, clear SFX, voice, per-move analytics, and one-shot visual effects. Consumers must not independently reconstruct the result from multiple snapshots.

## Shape Catalog

All catalog entries must be connected orthogonal polyominoes with unique local cell coordinates and a normalized origin.

The catalog will:

- add a true one-cell `1x1` piece;
- retain horizontal and vertical lines, `2x2` and `3x3` squares, `L/J`, `T`, `S/Z`, `U`, plus, and rectangular pieces;
- retain fixed orientations as distinct catalog entries where the player cannot rotate pieces;
- remove the disconnected two-cell and three-cell diagonal entries;
- classify pieces by occupied-cell count:
  - compact: 1–2 cells;
  - medium: 3–4 cells;
  - large: 5–9 cells.

The revive tray remains deliberately compact. Its documented contents must match the actual catalog entries: `1x1`, horizontal `1x2`, and vertical `1x2`.

## Adaptive Tray Generation

Tray generation accepts deterministic randomness and a snapshot of the current grid. It evaluates a bounded set of candidate trays rather than selecting one piece from each hard-coded category.

Candidate scoring rewards:

- immediate placeability on the current board;
- category diversity;
- shape diversity;
- medium and large pieces on open boards;
- compact pieces on dense or fragmented boards.

Candidate scoring rejects:

- duplicate shape identifiers in one tray;
- three pieces from the same size category;
- a tray with no immediately placeable piece when any catalog piece can legally fit.

Generation behavior:

- On an open or moderately occupied board, prefer trays containing medium and large shapes.
- As density and fragmentation rise, progressively increase compact-piece weight.
- Do not guarantee that every piece fits or that every future placement remains safe.
- Guarantee at least one immediately placeable piece if the catalog contains a piece that fits the current board.
- Evaluate 48 seeded candidate trays per refill. If no candidate satisfies every preference, return the highest-scoring valid candidate.
- Preserve seed reproducibility across tray refills by advancing the deterministic seed exactly once per accepted tray.

Game over remains possible while pieces are still present in the tray and none fits. The adaptive guarantee applies only when a new tray is created; it does not regenerate the tray in response to a bad player placement.

## Starter Layouts

The first-launch tutorial round always starts with an empty grid. After the tutorial has been persisted as seen, each explicitly requested fresh round has a 50% seeded chance to use a starter layout. Continue and result-restore flows never replace their saved grid with a starter layout.

The starter-layout catalog contains 12 hand-authored base templates. Each template:

- occupies 14–22 of the 64 cells;
- contains no already complete row or column;
- uses only valid in-bounds positions;
- is transformed by a seeded rotation and/or reflection;
- receives piece colors independently from geometry.

The engine validates the transformed grid together with the initial tray. Validation searches piece orders and placements to depth three, applies line clears after each simulated placement, and succeeds only when all three initial pieces can be placed in at least one sequence. Each layout candidate is limited to 25,000 expanded simulation states.

If a transformed template fails validation, the generator tries another seeded transformation or template. It tries at most 12 layout candidates. When that budget is exhausted, the round starts from an empty grid with the already selected valid tray. Starter-layout failure must never block round creation.

The selected template identifier and transformation are diagnostic metadata for analytics, not persistent gameplay state. The resulting grid and tray are persisted normally.

## Scoring and Combo Rules

Placement score equals the number of occupied cells in the placed piece.

For `n` simultaneously completed rows and columns, the base clear reward is:

```text
baseClear(0) = 0
baseClear(1) = 10
baseClear(n) = 10 * n * (n - 1), for n >= 2
```

This produces the agreed sequence:

| Lines | Base reward |
|---:|---:|
| 1 | 10 |
| 2 | 20 |
| 3 | 60 |
| 4 | 120 |
| 5 | 200 |
| 6 | 300 |

`comboLevel` is the number of successful clears in the active chain, including the current clear:

- a round starts at `0`;
- the first clearing move sets it to `1` and multiplies the base reward by `1`;
- the second clearing move sets it to `2` and multiplies by `2`;
- subsequent clearing moves continue linearly without an artificial cap;
- every clearing move resets `movesWithoutClear` to `0`;
- a non-clearing move increments `movesWithoutClear`;
- the first and second consecutive non-clearing moves preserve `comboLevel`;
- the third consecutive non-clearing move resets both `comboLevel` and `movesWithoutClear` to `0`.

The awarded score is:

```text
placementPoints + baseClear(lines) * comboLevel + allClearBonus
```

`allClearBonus` is `300` when at least one line was cleared and the resulting board is empty; otherwise it is `0`.

The UI displays combo celebration beginning at `COMBO x2`. A first clear may receive line-clear feedback but is not presented as a combo.

## Voice and Sound Design

Runtime text-to-speech is explicitly excluded. Short celebration phrases must remain pre-generated, trimmed, normalized audio clips so latency, pronunciation, emotion, and timbre remain consistent across Android and iOS. `TextToSpeechKt` and CopiloTTS were evaluated but are not dependencies of this design.

The existing voice vocabulary is:

- `AMAZING`;
- `GOOD`;
- `GREAT`;
- `EXCELLENT`;
- `UNBELIEVABLE`.

At most one voice response is selected for a move, using this priority:

1. resulting board empty after a clear → `UNBELIEVABLE`;
2. row/column cross-clear or at least four lines → `EXCELLENT`;
3. exactly three lines → `GREAT`;
4. exactly two lines → `GOOD`;
5. combo level becomes exactly three and no higher-priority response applies → `AMAZING`;
6. otherwise → no voice.

Later combo milestones use visual, haptic, and tonal emphasis only. `Amazing` is not repeated at levels 4, 5, and beyond.

Audio playback rules:

- `block.mp3` is the placement SFX resource.
- Clear SFX reuses the short block sample with a distinct volume and a playback-rate increase derived from the line count. No nonexistent `line_clear_N` resource names are requested.
- SFX may overlap when they communicate separate parts of one move.
- Voice has a dedicated single stream. Starting an accepted voice stops the previous voice stream before playback.
- Missing or not-yet-ready resources are silently skipped. There is no fallback to `Amazing` or any other phrase.
- The domain-selected voice response is emitted once through the move-resolution event. Compose never initiates voice playback.

## State, Persistence, and Compatibility

`GameState` gains the minimum durable combo-grace state needed to resume a round consistently. New serialized fields have defaults so older saves decode without migration failure.

Starter metadata is not required to resume gameplay and is not added to the persisted state. The transformed grid, current tray, score, combo level, and grace counter are sufficient.

Existing guarantees remain intact:

- lifetime best score is monotonic;
- terminal state is saved before result navigation;
- stale autosaves cannot overwrite explicit terminal or revive snapshots;
- deterministic tests can reproduce trays and starter selection;
- result restore does not emit a false game-start event;
- revive preserves the grid and score, resets combo state, and supplies the corrected compact tray.

## MVIKotlin and UI Responsibilities

`GameStoreFactory` continues to collect engine state into `GameStoreState`. It also collects move-resolution events in one lifecycle-bound coroutine and processes their effects in narrative order:

1. placement SFX;
2. clear SFX;
3. optional voice;
4. analytics.

The store does not recalculate scoring, feedback priority, starter selection, or tray quality. The reducer remains pure and only applies state snapshots.

Compose continues to render durable nonce-bearing fields for floating score, cleared cells, feedback text, and combo motion. Effects key on explicit event identity rather than the whole state object. Recomposition, configuration changes, and returning from a sheet must not replay audio or previously consumed animation.

## Analytics

Existing event names remain unless their payload is extended. The redesign records enough data to validate balance without logging cell-by-cell player history.

Fresh-round analytics include:

- empty or starter-layout source;
- starter template identifier and transformation when used;
- initial occupied-cell count;
- initial tray shape identifiers and size categories.

Move analytics include:

- piece identifier and size;
- number of lines and cleared cells;
- cross-clear and all-clear flags;
- placement, clear, all-clear, and total points;
- resulting combo level and grace state;
- selected voice response or `none`;
- whether the move ended the round.

## Error Handling

- Starter and tray searches are bounded and return safe fallbacks.
- A starter-layout validation failure produces an empty opening board, not a failed round.
- Audio initialization or playback failure cannot alter game state or score.
- Cancellation is propagated through store and persistence coroutines rather than converted to a gameplay failure.
- Unsupported or missing saved fields use documented defaults.
- Failed autosave or settings persistence is logged through the existing analytics failure path and does not duplicate move effects.

## Test Strategy

Implementation follows red-green-refactor TDD with focused tests before production changes.

### Shape catalog

- Every shape has unique cells, a normalized origin, and orthogonal connectivity.
- The catalog contains a true `1x1`.
- Disconnected diagonal shapes are absent.
- Revive returns exactly `1x1`, `1x2` horizontal, and `1x2` vertical.

### Adaptive generator

- Seeded generation is deterministic.
- A tray contains three distinct shape identifiers.
- A tray never contains three shapes from one category.
- Open-board fixtures favor medium/large candidates over compact-only trays.
- Dense-board fixtures raise compact-piece selection.
- At least one generated piece fits when a catalog piece can fit.
- Candidate search examines exactly 48 trays and returns its defined fallback.

Distribution assertions use a fixed seed range and broad invariant thresholds; they must not depend on unseeded randomness or flaky single samples.

### Starter layouts

- All 12 templates have 14–22 occupied cells and no complete line.
- Rotation and reflection stay in bounds and preserve occupancy.
- Known seeds select empty and populated starts according to the deterministic 50% branch.
- Every accepted populated start permits all first-tray pieces in at least one order.
- Exhausted validation attempts fall back to an empty grid.
- Tutorial, continue, and result-restore flows never inject a starter grid.

### Scoring and combo

- Placement points equal piece size.
- Base rewards match `10, 20, 60, 120, 200, 300` for one through six lines.
- First, second, and later clears use multipliers `1`, `2`, and the current combo level.
- One and two misses preserve the chain; the third resets it.
- A clear resets the miss counter.
- An empty resulting board awards exactly 300 extra points.
- Restored legacy state defaults the grace counter safely.

### Feedback and audio

- Feedback priority covers all-clear, cross/four-plus, three-line, two-line, combo-three, and silent cases.
- Each move contains at most one voice response.
- Combo levels above three do not repeat `Amazing` without a stronger line result.
- `GameStore` invokes placement and clear SFX in order and invokes voice at most once.
- Store/UI recreation does not replay a consumed move.
- Platform resource resolution uses `block`, never nonexistent `block_place`, `line_clear_N`, or `voice_combo_N` identifiers.
- Missing/unready voice resources do not trigger another phrase.

### Integration and regression

- The tutorial begins on an empty grid.
- Post-tutorial new-game seeds cover both empty and populated starts.
- Continue, restart, game over, result restore, revive, autosave, and best-score tests remain green.
- Relevant module tests run before Android compilation and packaging.

## Verification Commands

Run the narrowest tests first, then broaden:

```bash
./gradlew :core:domain:allTests
./gradlew :core:data:allTests
./gradlew :feature:game:allTests
./gradlew :feature:root:allTests
./gradlew :composeApp:allTests
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Changes to native audio playback also require an Android device/emulator smoke test and an iOS simulator or device smoke test. Gradle framework linking alone does not validate actual audio focus, playback rate, latency, or interruption behavior.

## Acceptance Criteria

- The first tutorial round has an empty grid.
- Across the fixed balance-test range of 10,000 post-tutorial seeds, 49–51% take the populated-start branch. Individual populated attempts may still fall back to empty when validation exhausts its 12-candidate budget.
- Populated starts contain 14–22 cells, no completed line, and a solvable first tray.
- Normal trays are no longer forced to contain a small piece and adapt to board density without becoming compact-only.
- The catalog contains valid connected shapes and a real `1x1`.
- Score calculations follow the approved base table, combo multiplier, three-miss grace, and 300-point all-clear bonus.
- One move produces at most one voice phrase with the documented priority.
- `Amazing` occurs only when the combo first reaches three and no stronger feedback applies.
- Placement and clear SFX use resources that actually exist.
- Existing saves restore, autosave remains race-safe, and the complete verification suite passes.
