# Fruit Merge UX, Tutorial, Audio, and Session Flow Design

**Date:** 2026-08-30  
**MiniApp:** `game.fruitmerge`  
**Project:** `:game:fruitmerge`

## Goal

Polish Fruit Merge into a clear, tactile, adaptive game experience while preserving its bounded fixed-step physics and Canvas-based rendering. The change addresses fruit readability, full-viewport gestures, icon-only actions, first-launch onboarding, procedural music and SFX, Decompose-owned result navigation, board layout, shake correctness, and drop spam prevention.

The attached tutorial screenshots are visual references only. The implementation uses the repository's design system and the existing Block Blast tutorial language; it does not copy reference-game assets, branding, code, or audio.

## Session Architecture

`FruitMergeSessionComponent` owns a Decompose `ChildStack` with two configurations:

- `Playing(runOrdinal)` renders the active game and owns interaction, tutorial state, and frame updates.
- `Result(runOrdinal)` renders a full-screen result destination.

The retained `FruitMergeStore` remains the single owner of game rules and runtime state. When the Store publishes `ResultReached`, the session component replaces the stack with `Result`. Requesting a new game dispatches the new-run intent; after the committed run ordinal changes, the stack is replaced with `Playing`.

The session exposes `MiniAppFrameMode.Standard` for Playing and `MiniAppFrameMode.ContentOnly` for Result so the result is visually a destination rather than a dialog over the board. Back handling first cancels clear targeting or tutorial state when applicable; otherwise it delegates to the host contract.

Compose observes Decompose `Value` instances through `subscribeAsState()` and does not own business state, persistence, navigation, ad decisions, or audio routing.

## Playing Layout and Interaction

The existing `AdaptiveGameScaffold` remains the shared adaptive primitive. Window policy stays centralized in `:core:uikit`; Fruit Merge uses local constraints only to size its board and supporting content.

### Compact layout

The viewport is arranged vertically:

1. A compact HUD row with score, best score, Bomb, and Vibration actions.
2. Flexible empty/preview space that allows aiming from anywhere above the board.
3. The largest square board that fits, anchored toward the bottom.
4. A compact horizontal fruit-evolution strip below the board.

The board gains priority over decorative whitespace and is larger than the current implementation. Safe areas remain host-owned.

### Expanded layout

The primary pane contains the large bottom-aligned board and evolution strip. The supporting pane contains the compact HUD/action cluster and next-fruit preview without instructional prose. The same gesture mapper covers the complete plugin viewport.

### Full-viewport gestures

A pointer-input layer covers the complete Playing viewport rather than only the Canvas. The board reports its bounds in viewport coordinates. Pointer X is clamped and translated into normalized board X before reaching the Store. Tap releases the fruit at that X; dragging updates the preview continuously and releases at the final X.

During clear targeting, the same viewport layer hit-tests fruit centers in board coordinates. Pointer input uses updated-state callbacks so gesture coroutines do not restart on every frame.

## Icon Actions and Accessibility

Visible instructional labels and action button text are removed from normal play. Score and best score remain numeric. Actions use circular design-system surfaces:

- Bomb uses the user-provided `BombFilled` UIKit vector.
- Shake uses the existing UIKit `Vibration` vector.

Each action has a small numeric badge while free attempts remain. After free attempts reach zero, the badge changes to a compact ad marker when advertising is enabled by the host capability. Bomb toggles clear targeting; pressing it again cancels targeting. The active state uses the design-system primary/coral treatment.

Icon-only presentation does not remove meaning from accessibility. Each button exposes a localized content description, remaining-attempt state, ad disclosure when applicable, enabled/disabled state, and stable test tag. Touch targets remain at least 48 dp.

## Fruit Readability and Motion

Fruit levels differ through silhouette and surface details, not hue alone:

- Blueberry: circular blue body with a small crown/dimple.
- Strawberry: tapered heart-like silhouette, leafy crown, and visible seeds.
- Cherry: twin-lobe/cherry silhouette with a characteristic stem and leaf treatment.
- Later levels receive distinct citrus dimples, apple cleft, peach seam, melon texture, or related original botanical details.

Faces retain blinking, impact squash, blush, eye highlights, and anxious/impact expressions. Shape detail is drawn in the existing Canvas pass to avoid a composable per physics body. Reduced-motion mode freezes decorative loops while retaining gameplay state changes and clear visual feedback.

The evolution strip is a separate single Canvas that draws every `FruitLevel` in order at a readable relative scale. It has one localized accessibility description for the ordered progression.

## Tutorial

The tutorial appears only on the first Fruit Merge session until completed or skipped. A local `tutorial_seen` value is persisted through `MiniAppStorage`; no raw Settings or physical key is used.

The tutorial follows the existing Block Blast `GestureTutorial` visual language:

- a translucent scrim rendered in an offscreen layer;
- `BlendMode.Clear` spotlights around the relevant interaction area;
- a design-system pill caption;
- an animated hand with press ripple;
- a ghost preview fruit following the demonstrated gesture;
- reduced-motion static presentation;
- a short fade and small confetti completion effect.

It has two steps:

1. Tap anywhere in the game viewport to position and drop.
2. Drag horizontally to choose a position, then release to drop.

Touches pass through the overlay. A real successful tap advances to step two; a real drag-and-drop completes onboarding. `Skip` is available at the top. Skip and completion both persist `tutorial_seen`; an incomplete tutorial remains available after lifecycle recreation. Tutorial text is localized and is one of the few places where explanatory copy remains visible.

## Result Destination

Game over is a full Decompose destination, not a dialog or overlay. The screen uses the app theme and presents:

- final score;
- best score;
- largest fruit reached, drawn with its face;
- one primary New Game action.

The result screen is scroll-safe at large font scales and compact heights. The transition uses a small Decompose fade/scale animation and respects reduced motion. Starting a new game returns to Playing only after the Store commits the new run.

## Drop Cooldown

The Store/engine owns a 450 ms drop cooldown. A drop is accepted only while Playing, outside clear targeting, and when the cooldown is ready. Accepted drops reset the timer; rejected gestures do not mutate the body list or random state.

Cooldown is transient runtime state and is not persisted. Frame stepping decrements it deterministically. The preview remains visible while cooling down at reduced alpha; when readiness returns it gives one restrained scale/vertical settle animation. This prevents fruits from being spammed into the spawn area before earlier bodies can separate.

## Shake Correctness

Shake no longer rejects the action merely because one or more bodies are moving. If the run is Playing and the board is non-empty, a bounded deterministic impulse is applied to every body:

- horizontal impulse with seeded left/right variation;
- upward impulse capped below destabilizing velocities;
- bounded angular impulse;
- short danger grace period.

The free attempt or paid entitlement is consumed only when an impulse is actually applied. Physics remains responsible for subsequent contacts and merges. The action produces immediate visual screen shake and a matching SFX. `Vibration` names the UIKit action icon; the MiniApp does not import platform vibration APIs or expand the host contract.

## Procedural Audio

Audio is an immutable game-owned declaration using only the public `MiniAppAudio` API and approved presets. A session-scoped adapter starts music once, routes Store labels to typed SFX names, updates only meaningfully changed controls, consumes every accepted/rejected command result, and never retries in a loop.

### Fruit crate groove

The music evokes manually rocking a wooden box of fruit rather than placing a conventional melody over ambient sound:

- a low, short, filtered tonal transient suggests the crate's wooden knock;
- deterministic, filtered noise transients suggest fruit rolling and brushing the box;
- sparse `GlassBell` accents provide a cute tonal identity;
- seeded stereo movement alternates the apparent weight from left to right;
- a low-density original pattern avoids constant noise and leaves headroom for SFX;
- a danger/fullness control modestly increases density without changing the program every frame.

No recorded sample, commercial melody, third-party demo, or copied parameter sequence is used. Seeds and pattern choices are game-owned and deterministic. The program stays within documented mobile voice/effect budgets.

### SFX

Typed effects cover:

- drop/placement;
- low, mid, and high merge tiers;
- bomb clear;
- shake;
- game over;
- tutorial completion.

Preset fragments such as `PlacementClick`, `PowerUp`, and `SuccessSweep` are renamed and tuned first. Game-owned synthesis is used only for crate/rolling roles not expressible by presets. Rapid triggering is gain-limited and tested over active music.

## Data Flow and Failure Handling

User gestures and action clicks enter the Playing component, which dispatches typed Store intents. Pure rules return an updated immutable `FruitMergeState` or an explicit rejection. Store labels describe committed effects such as DropAccepted, MergeResolved, ClearApplied, ShakeApplied, and ResultReached. A session adapter owns audio and navigation consequences of those labels.

Ad tokens remain session- and run-bound. A stale completion cannot mutate a new run. Audio rejection, unavailable ads, or absent haptic capability never blocks gameplay. Persistence is best-effort through the existing coordinator; tutorial completion is saved independently from transient cooldown and animation state.

## Performance Constraints

- Keep fixed-step 60 Hz simulation with the existing maximum steps per rendered frame.
- Keep the existing body limit and spatial broad phase.
- Draw bodies and the evolution strip in Canvas passes; do not create one composable per body.
- Avoid allocations in per-body physics and draw loops where practical.
- Read frame-rate animation state inside draw/layout lambdas rather than rebuilding the UI tree.
- Start frame and tutorial loops only while the session is visible, Playing, and motion policy permits them.
- Use stable callbacks and immutable UI models to keep HUD and result content skippable.

## Testing and Acceptance

### Rules and Store

- A second drop inside 450 ms is rejected without consuming RNG or adding a body.
- A drop becomes available after deterministic frame advancement.
- Shake applies to moving and stationary bodies and consumes exactly one attempt only on success.
- Clear and paid-action behavior remains session/run safe.
- ResultReached is published once and persistence checkpoints the final state.

### Components and navigation

- Initial restored Playing/Result state selects the matching child.
- ResultReached replaces Playing with Result.
- New Game returns to Playing only after a committed new run.
- Back cancels clear targeting before delegating.
- Tutorial advances on a real tap, completes on a real drag, persists seen state, and does not reappear after recreation.
- Audio labels map once to their exact typed commands; rejected commands are consumed safely.

### Compose UI

- Compact and expanded layouts keep the board, HUD actions, and evolution strip reachable.
- Pointer gestures beginning outside the board still position/drop correctly.
- Icon actions expose localized accessibility semantics and 48 dp targets.
- Fruit levels remain distinguishable without relying on color alone.
- Tutorial honors active visibility and reduced motion.
- Result content remains reachable at 200% font scale.

### Verification commands

Run from the repository root:

```bash
./gradlew :game:fruitmerge:allTests
./gradlew :game:fruitmerge:verifyMiniApp
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
git diff --check
```

## Scope and Ownership

Changes remain inside `:game:fruitmerge` except for using the user-provided UIKit Bomb vector and any narrowly required tests of an existing public contract. No platform audio or vibration API, raw Settings, feature module, native ads module, or application module is imported.

The root `miniApps` allowlist is maintainer-owned and is not modified by this implementation. Any existing uncommitted allowlist change remains outside the agent-authored commits.
