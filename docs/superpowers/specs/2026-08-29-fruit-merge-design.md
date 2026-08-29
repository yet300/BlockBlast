# Fruit Merge MiniApp Design

**Date:** 2026-08-29  
**Status:** Approved for implementation planning  
**Shipping status:** NOT ALLOWLISTED

## Objective

Create an original Kotlin Multiplatform Fruit Merge MiniApp for Logica. The
game uses the established drop-and-merge rules of the fruit-merging puzzle
genre, an original visual identity, deterministic lightweight circle physics,
adaptive Compose Multiplatform UI, and two consumable board-management actions.
It must remain responsive on low-end Android and iOS devices.

## Research Basis and Rights Classification

The gameplay contract is based only on genre-level rules documented by official
sources:

- Aladdin X describes colliding identical fruit to evolve them while preventing
  the box from overflowing: <https://suikagame.jp/>.
- The official mobile listing describes horizontal placement, release-to-drop,
  score on evolution, and game over on overflow:
  <https://apps.apple.com/us/app/suika-game-aladdin-x/id6469114836>.
- Nintendo describes preventing overflow by colliding the same kind of fruit:
  <https://www.nintendo.com/au/games/nintendo-switch/suika-game/>.

The implementation is classified as `new_expression`. It will not copy Suika
Game code, names, fruit ordering, character designs, art, audio, UI layout,
branding, numerical balance, or distinctive presentation. All runtime art is
original Canvas drawing authored for this repository. No third-party image,
font, or audio asset is required.

## Submission

- **Schema version:** 1
- **MiniApp ID:** `game.fruitmerge`
- **Project path:** `:game:fruitmerge`
- **Display name:** Fruit Merge
- **Category:** `game`
- **Authors:** Logica contributors, with implementation assistance from OpenAI
  Codex
- **Summary:** Drop expressive fruit, combine matching pairs, manage the pile,
  and chase a high score without crossing the danger line.
- **Platforms:** Android and iOS; phone and tablet; portrait and landscape
- **Capabilities:** interstitial

## Rules

### Fruit progression

The original progression contains ten levels:

1. Blueberry
2. Cherry
3. Strawberry
4. Plum
5. Mandarin
6. Apple
7. Pear
8. Peach
9. Pineapple
10. Melon

Only the first five levels can be selected as a new drop, using deterministic
weighted selection biased toward smaller fruit. Two contacting fruit of the
same level merge once into the next level at their weighted midpoint. The new
body conserves bounded linear and angular momentum. Each source body may
participate in at most one merge per fixed step.

Two melons disappear and grant the maximum merge award, creating space for the
run to continue. Scores come from merges, not drops, clears, or shakes. Score
values increase monotonically by level and are defined as named game-balance
constants covered by tests.

### Drop and terminal state

The player moves the preview fruit horizontally inside the playable bounds and
releases it to drop. A second drop is accepted only after the previous drop has
entered the simulation. The next preview is deterministic from the persisted
random seed.

A fruit contributes to overflow when its bounds remain above the danger line
for 1.5 continuous seconds. Temporary bounce, an active merge chain, and the
short post-shake grace period do not immediately end the run. The run ends when
the grace-free timer reaches the threshold.

### Consumable actions

Each new run starts with five free clears and three free shakes. Their remaining
counts are part of the run snapshot and therefore survive recreation and relaunch.

Clear enters a targeting mode. The player taps an existing fruit to remove it
without score. Cancelling targeting does not consume a use. Once the five free
uses are exhausted, the interstitial gate completes before one clear targeting
opportunity is granted.

Shake applies deterministic, bounded horizontal and upward impulses plus small
angular impulses to all settled fruit. It is available only when the board is
stable and no other action is pending. Once the three free uses are exhausted,
the interstitial gate completes before one shake is executed.

When advertising is disabled by entitlement, denied by policy, unavailable, or
not ready, the existing capability completes immediately and the requested
action proceeds without an advertisement. An ad callback is bound to a unique
session, run, and pending-action token; stale or repeated callbacks have no
effect.

The shared `MiniAppInterstitialPlacement` contract requires two semantically
explicit placements for this game: `FRUIT_MERGE_CLEAR` and
`FRUIT_MERGE_SHAKE`. The game continues to depend only on
`MiniAppInterstitialCapability`, never on AdMob, monetization adapters, or
platform SDKs. This small host-contract extension was explicitly included in
the reviewed design because using the existing game-over placement would be
misleading.

## Architecture

The module is generated with:

```bash
./gradlew createMiniApp \
  -PminiAppId=game.fruitmerge \
  -PminiAppName="Fruit Merge" \
  -PminiAppProfile=game
```

The generated Metro graph, retained `MiniAppSession`, plugin contract, resources,
and lifecycle wiring are preserved.

### Engine

`FruitMergeEngine` is a pure common Kotlin reducer over game-owned immutable
state and typed actions. It owns spawning, fixed-step physics, broad-phase
collision detection, narrow-phase circle resolution, merge arbitration, score,
danger timing, game over, clear, and shake. It has no Compose, lifecycle,
storage, advertisement, or platform dependency.

The external state is immutable. Internally, a fixed step uses preallocated or
reused primitive buffers so collision work does not allocate per contact. A
uniform spatial grid restricts candidate pairs. The simulation enforces a hard
body limit and a hard contact/effect budget.

### Store and session

The MVIKotlin Store owns the authoritative run state and accepts user intents,
frame time, lifecycle commands, and advertisement completion. It accumulates
elapsed frame time and executes 1/60-second fixed steps, with at most three
catch-up steps per rendered frame. Excess lag is dropped rather than causing an
unbounded catch-up loop.

The Decompose session component owns Playing and Result children, visibility,
checkpoint timing, and stale-safe callback tokens. The Metro session graph
injects `MiniAppSessionContext`, storage, and the interstitial capability.

### Persistence

All persistence uses the context-bound `MiniAppStorage` facade and local keys:

- `best_score`: long, default `0`
- `game_snapshot`: versioned snapshot, absent by default

The snapshot contains simulation bodies, velocities, score, next-fruit state,
random seed, danger timing, free-action counts, run ordinal, and schema version.
It excludes transient drawing particles, face-expression timers, and active ad
callbacks. Invalid numeric values, out-of-bounds bodies, unknown fruit levels,
duplicate body IDs, unsupported versions, and impossible counts invalidate the
snapshot. An invalid snapshot is discarded while a valid best score is retained.
Resetting game data removes both values through the host namespace reset.

## Adaptive UI and Design System

The game renders inside the host-owned frame and uses the existing Material 3
design system. `AdaptiveGameScaffold` owns compact, two-pane, and compact-height
arrangements without recreating the primary game surface.

- In compact portrait layouts, the primary field occupies the centered playable
  area, followed by compact score, next-fruit, and action controls.
- In wide or compact-height layouts, the field remains in the primary pane;
  score, progression, and actions move to the supporting pane.
- The physics world uses normalized game coordinates. Layout changes affect only
  world-to-viewport projection, never simulation positions or balance.
- The host owns Back, Settings, toolbar, safe areas, and ad containers.

Material theme colors and typography are used for chrome and labels. The field
uses an original warm orchard palette with a neutral container, clear danger
line, and accessible contrast. Every action has at least a 48 dp target.

## Fruit Rendering and Motion

Fruit are rendered in one Compose `Canvas` from original vector primitives.
Each level has a distinct palette, silhouette accent, leaf or crown detail, soft
matte gradient, restrained highlight, and bounded shadow. Faces use eyes, brows,
mouth, and blush rather than bitmap sprites.

Expression is derived from compact simulation events:

- falling fruit squint;
- a strong collision produces a brief surprised face;
- proximity to the danger line produces a worried face;
- a merge produces a smile and bounded pop effect;
- an idle fruit blinks on a deterministic low-frequency schedule.

Landing uses bounded squash-and-stretch. Merge uses a short scale pop, glow, and
pooled particles. No body owns an independent coroutine or `Animatable`. One
frame clock and compact event timestamps drive all rendering. Reduced-motion
mode disables squash, camera displacement, glow expansion, and particles while
retaining state changes and gameplay physics.

## Accessibility

- Localized labels describe score, best score, next fruit, free uses, paid gate,
  unavailable actions, and result state.
- The game field exposes a concise description of fill level, next fruit, score,
  and whether clear targeting is active.
- Entering and leaving clear mode, successful merge chains, exhausted free uses,
  and game over produce short accessibility announcements.
- Clear targeting exposes each selectable fruit by level and approximate board
  region through semantic hit targets without creating a full composable render
  tree for every fruit.
- Focus remains on the initiating action after adaptive rearrangement, and moves
  predictably to the result action on game over.
- Visual meaning never depends on color alone.

## Performance Contract

- Fixed physics rate: 60 Hz.
- Maximum catch-up: three steps per frame.
- Maximum active bodies and visual effects: named bounded constants.
- Broad phase: uniform spatial grid; no unconditional all-pairs scan in the
  production step.
- Narrow phase: reusable buffers; no blocking I/O, storage, logging, or coroutine
  launch inside frame-sensitive code.
- Rendering: one Canvas traversal for bodies and one bounded traversal for
  effects.
- Snapshot writes occur after meaningful actions and lifecycle checkpoint, not
  every tick.
- Background or invisible sessions stop frame production and checkpoint once.

If a frame gap exceeds the catch-up budget, the engine advances only the bounded
steps and discards excess accumulated time. This prioritizes responsiveness and
prevents the simulation from monopolizing a weak device.

## Failure Handling

- Invalid snapshots start a new run and preserve best score.
- Invalid physics values are rejected at the engine boundary and covered by
  invariant tests.
- Drop, clear, and shake are ignored while their preconditions are false.
- Duplicate advertisement completion is idempotent.
- Advertisement completion for a destroyed session or earlier run is ignored.
- The danger timer is paused only for the bounded merge/shake grace period.
- Session destruction stops ticking, cancels pending jobs, checkpoints once, and
  cannot mutate state afterward.

## Test Strategy

Implementation follows red-green-refactor. Focused tests cover:

1. deterministic generation and weighted spawn-level bounds;
2. gravity, walls, floor, friction, stable resting, and circle separation;
3. one merge per body per step, merge midpoint, momentum, score, chain reaction,
   and two-melon removal;
4. danger timing, temporary bounce, merge grace, shake grace, and game over;
5. five free clears, cancel-without-consume, target removal, ad-gated extra clear,
   three free shakes, stability precondition, and ad-gated extra shake;
6. unavailable-ad immediate completion, duplicate completion, and stale token;
7. snapshot round trip, validation failures, recreation, relaunch, and reset;
8. MiniApp graph retention, backgrounding, visibility, and idempotent teardown;
9. compact, wide, and compact-height Compose arrangements, semantics, touch
   targets, focus, and reduced motion;
10. maximum-body stress simulation with asserted broad-phase and effect budgets.

Final verification runs:

```bash
./gradlew :game:fruitmerge:allTests
./gradlew :game:fruitmerge:validateMiniAppDependencies
./gradlew :game:fruitmerge:compileAndroidMain
./gradlew :game:fruitmerge:compileKotlinIosSimulatorArm64
./gradlew :game:fruitmerge:verifyMiniApp
./gradlew :miniapp:compose:allTests
./gradlew :composeApp:compileAndroidMain
git diff --check
```

## Acceptance Scenarios

### Matching fruit merge

Given two equal fruit with a valid contact, when a fixed physics step resolves
the contact, then they are replaced once by the next level and the score rises
by that level's award.

### Chain reaction

Given a merge result touching the same next-level fruit, when subsequent fixed
steps resolve, then the chain continues deterministically without reusing a
removed body.

### Graceful overflow

Given a fruit briefly bouncing above the danger line, when it returns below the
line before 1.5 seconds, then the run continues. When any eligible fruit remains
above for 1.5 seconds without grace, the result state opens.

### Free and gated clear

Given five remaining free clears across a new run, when five valid targets are
removed, then the free count reaches zero. On the next clear request, the
interstitial gate completes before one target opportunity is granted; if no ad
will show, completion is immediate.

### Free and gated shake

Given three remaining free shakes and a stable board, when three shakes execute,
then the count reaches zero and every impulse remains within its bound. On the
next request, the interstitial gate completes before the shake; if no ad will
show, completion is immediate.

### Weak-device frame gap

Given a frame gap longer than three simulation steps, when the Store advances,
then no more than three fixed steps execute, excess lag is discarded, UI input
remains responsive, and state invariants hold.

### Recreation

Given a live run with score, bodies, next-fruit seed, and remaining actions, when
the session checkpoints and is recreated, then the validated run resumes with
the same authoritative values and no pending ad callback.

## Known Limitations

- The first version uses circle collision geometry even when decorative leaves
  make a fruit look non-circular.
- The initial release has no online leaderboard or multiplayer mode.
- The initial release has no game-owned audio; it relies on visual and haptic-free
  feedback and therefore introduces no audio asset or engine dependency.
- Cross-platform floating-point trajectories may diverge slightly after long
  chaotic runs, while each platform remains deterministic for the same build and
  input sequence within tested tolerances.
- The contributor change remains NOT ALLOWLISTED until independent maintainer
  review and a separate production shipping decision.

## Provenance Deliverables

The generated module will include a schema-valid submission record and a
`PROVENANCE.md` that records:

- original Kotlin and Compose code authored in this repository with Codex;
- original procedural Canvas art, with no imported asset;
- no audio or font asset;
- the three official genre-rule references listed above;
- no copied source, branding, character design, layout, or numerical balance;
- the user request and this design document as the AI prompt archive.
