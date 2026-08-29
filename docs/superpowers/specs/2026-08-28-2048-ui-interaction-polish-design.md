# 2048 UI and Interaction Polish Design

**Date:** 2026-08-28

## Goal

Simplify the 2048 presentation, make swipe input easier to use, and make tile merges feel more tactile without expanding shared MiniApp or adaptive-layout APIs. The game Store remains authoritative, existing game statistics remain persisted for analytics and result computation, and all animation work stays bounded for a 4×4 board and weak devices.

## Scope

This change is confined to `:game:twentyfortyeight`. It does not change Root, the shared contributor API, `AdaptiveGameScaffold`, or another game. The existing user-owned `settings.gradle.kts` change is not part of this work.

## Statistics Overlay Removal

Remove the Statistics bottom sheet as a product surface, including:

- `TwentyFortyEightStore.Intent.OpenStatistics`;
- `OverlayState.Statistics`;
- the corresponding serializable `OverlayConfig` entry;
- `PlayingComponent.onStatisticsRequested()`;
- the Statistics overlay component/model and rendering branch;
- the score-card click target and statistics-specific strings;
- tests whose only purpose is opening, restoring, dismissing, or rejecting stale callbacks from the Statistics overlay.

Do not remove `GameStatistics`, its engine updates, persistence records, diagnostics, analytics use, or integrity tests. Statistics remain domain data even though they no longer have a dedicated bottom sheet.

## Unified Score Card

Replace the two independent Score and Best surfaces with one non-clickable presentation. It has three visual states:

1. **No prior record:** show only the current formatted score.
2. **Existing record:** show a row containing current score, crown icon, and best score, without visible `Score` or `Best` labels.
3. **Record improved in this run:** animate the current-score side into the crown/best side, then keep only the crown and best score visible for the remainder of the run.

The third state is irreversible within a run. Undo must not separate the card again. Starting a new run resets the state, after which a persisted best score again produces the existing-record row.

Accessibility semantics continue to expose the current score and best score even when visible labels are absent. The card is not focusable as an action and must not imply that statistics can be opened.

### Authoritative record state

Add `bestImprovedInRun: Boolean` to `RunFacts`. `GameRules.acceptChanged()` sets it when `move.scoreAfter` is strictly greater than the authoritative best score before that move. Once true, it stays true through later moves and Undo. `GameRules.newGame()` resets it through a fresh `RunFacts`.

Persist the flag in `current_game`. The serialized field has a default of `false` so older version-1 payloads remain readable. Validation rejects impossible states where the flag is true but the best score is zero. The UI reads the flag through the Store model; it does not own a parallel `rememberSaveable` record state.

The visual transition uses one persistent transparent container with no card fill, shape, or elevation. Its content is centered in the available width. Content changes use bounded Compose value/content animation: the score side contracts and fades toward the crown side, while the crown receives a short pulse. Reduced Motion uses an alpha-only transition.

## UI Package Boundaries

Keep the 2048 Compose layer organized by responsibility rather than in one flat package:

- `ui/board`: board composition, tiles, palette policy, and move/Undo transitions;
- `ui/common`: shared UI-only text formatting;
- `ui/gameplay`: the playing composition, score presentation, actions, and swipe input;
- `ui/motion`: shared bounded motion policy and entry animation primitives;
- `ui/overlay`: victory/restart sheets and the tutorial overlay;
- `ui/result`: the compact Game Over presentation;
- `ui/screen`: session routing, focus, announcements, and top-level effect consumption.

Common tests mirror the same package layout. All types remain module-internal, and the package split does not change Store, component, session, or public MiniApp contracts.

## Supporting Hint Removal

Remove the visible `Swipe anywhere in the game area.` supporting text and its resource. Do not remove the viewport-wide gesture behavior or tutorial accessibility guidance.

## Compact Game Over Screen

Replace the two-pane result composition with one centered, vertically scrollable, width-bounded Surface. It contains:

1. `Game Over` heading and focus target;
2. a prominent final score;
3. one compact summary row for crown/best score and highest tile;
4. an optional persistence error;
5. the full-width New Game action.

The result screen does not show cumulative wins, moves, merges, or undo statistics. `ResultComponent.Model` therefore contains only the score, best score, and highest tile required by the UI. Domain statistics remain in the Store.

The layout uses local constraints, a maximum content width, safe padding, and vertical scrolling. It does not introduce a new width class or layout mode. Compact-height devices can scroll to the action; large screens receive the same focused card rather than an unnecessary empty supporting pane.

## Easier Viewport-Wide Swipe Input

The gesture detector remains attached to the whole gameplay viewport. It keeps one move per pointer gesture and uses a smaller adaptive distance threshold:

- derive the threshold from approximately 5% of the shorter viewport edge;
- clamp it above system touch slop and below approximately 28–32 dp;
- lower the flick velocity threshold from 650 dp/s to approximately 480 dp/s;
- allow a fast flick after touch slop even before the full distance threshold;
- lock an axis only when displacement or velocity has meaningful dominance, so perpendicular finger noise does not flip direction;
- keep an ambiguous diagonal pending until one axis wins;
- once locked, emit exactly one direction and consume the remainder of that gesture.

A gesture beginning in the supporting scroll region delegates a vertically locked gesture to scrolling. A horizontally locked gesture remains a game move. Disabled, cancelled, consumed, or multi-pointer gestures never emit a move.

The resolver remains a pure arithmetic function over bounded `Offset`, `Velocity`, and configuration values. Pointer handling does not allocate an unbounded history or launch per-frame coroutines.

## Bounded Liquid Merge Motion

Extend the existing move transition rather than adding a second renderer. Normal motion uses one shared transition progress and these phases:

1. Merge sources move toward the target while stretching slightly along their travel axis and compressing slightly across it.
2. During the final approach, a rounded bridge appears between each source and the target center.
3. At collision, sources squash and fade.
4. The result tile appears below scale 1, overshoots slightly, and settles at scale 1.
5. One bounded halo ring expands and fades behind the result.

The bridge uses cached geometry plus `drawLine` with a round cap and `drawCircle`. The halo uses one stroked circle. Effect colors derive from the result tile palette. Do not use blur, `RenderEffect`, runtime shader compilation, offscreen bitmaps, or a coroutine per tile.

The board permits only a bounded number of simultaneous merges. Merge effect descriptions are computed once per transition; draw-time work iterates only that bounded collection and updates primitive numeric values. Animation progress is read in layout/draw phases rather than causing unrelated composition work.

Reduced Motion keeps the existing bounded crossfade and does not draw stretch, bridge, overshoot, or halo effects.

## Testing

### Engine and persistence

- a strict new best sets `bestImprovedInRun`;
- tying the prior best does not set it;
- Undo does not clear it;
- New Game clears it;
- current-game round trip preserves it;
- an older payload without the field restores it as false;
- invalid persisted combinations are rejected without destroying valid metadata recovery.

### Compose UI

- unified score card renders all three states with no visible labels;
- score and best semantics remain available;
- the card has no statistics click action;
- the record-improved visual state remains stable when the score later decreases;
- the supporting hint is absent;
- the result screen contains only the approved summary and action;
- compact width and compact height retain access to New Game through scrolling.

### Gestures

- short distance and fast flick inputs resolve correctly;
- perpendicular noise does not change the intended direction;
- an ambiguous diagonal remains pending until an axis wins;
- supporting vertical gestures delegate to scroll;
- supporting horizontal gestures move;
- one pointer gesture emits at most one move;
- cancelled, consumed, disabled, and multi-pointer input emits nothing.

### Merge motion

- bridge and halo are absent outside their phase windows;
- source scale remains finite, positive, and bounded;
- stretch orientation follows the movement axis;
- result animation finishes at alpha 1 and scale 1;
- all draw effect collections respect the 4×4 board bound;
- Reduced Motion produces no liquid effect descriptors.

## Verification

Run focused tests first, followed by:

```bash
rtk ./gradlew \
  :game:twentyfortyeight:allTests \
  :game:twentyfortyeight:validateMiniAppDependencies \
  :game:twentyfortyeight:compileAndroidMain \
  :game:twentyfortyeight:compileKotlinIosSimulatorArm64 \
  --rerun-tasks
rtk git diff --check
rtk git status --short --branch
```

Manual device work remains necessary for 60/120 Hz frame traces, allocations, touch feel, Reduced Motion, accessibility services, and physical Android/iOS rendering.
