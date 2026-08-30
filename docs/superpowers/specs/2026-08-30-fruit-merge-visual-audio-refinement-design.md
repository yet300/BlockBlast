# Fruit Merge Visual and Audio Refinement Design

## Status

Approved in chat on 2026-08-30. This document refines the existing Fruit Merge implementation and
the earlier UX polish specification. The supplied game screenshot is a composition and interaction
reference only. Its branding, artwork, text, assets, and distinctive expression must not be copied.

## Goals

- Make the compact portrait hierarchy immediately readable: score, consumables, next fruit, glass,
  and evolution strip.
- Bring every surface into Logica's warm editorial cream-and-coral design system.
- Replace the current flat fruit rendering with original glossy-kawaii Canvas art that remains
  efficient on low-end devices.
- Make shake a visible, multi-second game action with synchronized UI and deterministic physics.
- Replace the weak drop and clear sounds with original, tactile, fruit-specific procedural SFX.
- Make the first-launch tutorial feel integrated with the actual board rather than like a generic
  modal overlay.
- Keep Android and iOS behavior equivalent and preserve existing MiniApp/Decompose boundaries.

## Non-goals

- Copying the supplied screenshot's fruit art, fonts, branded controls, or exact color palette.
- Adding recorded audio files, third-party music, a new audio engine, or platform audio calls.
- Adding new monetization rules, currencies, leaderboards, multiplayer, or navigation actions.
- Replacing the shared adaptive scaffold or moving game UI into the host application.

## Screen Composition

### Compact portrait

The play screen uses a single vertical hierarchy inside the MiniApp viewport:

1. A compact centered score capsule at the top, split into current score and best score.
2. A control row below it. Bomb and Vibration sit on the left as warm cream icon tiles with coral
   pressed/active treatment and small free-use or `AD` badges. A larger Next fruit capsule sits on
   the right and renders the actual queued fruit.
3. The preview fruit and dotted drop guide sit directly above the glass.
4. The glass occupies all remaining primary height and reaches close to the bottom support strip.
5. The complete ten-fruit evolution strip remains the final element.

No regular-play instructional copy is added. Semantics continue to expose score, best score,
consumable state, advertisements, next fruit, danger state, and board summary.

### Medium, expanded, and compact-height windows

The existing `AdaptiveGameScaffold` remains the single window-first layout policy. On two-pane
layouts, the glass remains the dominant primary pane. The score capsule, action row, Next fruit,
and evolution strip move together into the supporting pane in the same reading order. Controls
remain reachable through the supporting scroll container. Game state, tutorial state, and shake
progress do not reset when the layout changes.

### Geometry ownership

The viewport owns tap/drag drop gestures. The glass owns only fruit-clear hit testing. Board bounds
are measured once per layout change and reused by the tutorial; no frame-rate size state is written
back from layout to composition.

## System-bar and Full-frame Background

`FruitMergeSession.Background` is the source of the full-frame color behind host chrome and system
bar regions. It derives the active visual background from the retained Decompose child:

- Playing uses the warm game canvas.
- Result uses the exact Result base color.

The Result composable uses that same base color for its ambient background. Decorative gradients
stay inside the content layer, while their edge color resolves to the shared base so the status and
navigation regions do not form visible bands. This changes no Activity-level inset ownership and
does not duplicate safe-area padding.

## Visual Language

### Palette and surfaces

- Base canvas: UIKit warm cream/background tokens.
- Primary emphasis: UIKit terracotta/coral tokens.
- Cards and capsules: existing surface-container tokens with hairline contrast.
- Shadows: restrained and limited to floating tutorial/preview emphasis.
- No new purple, saturated-blue, or arbitrary game-local surface family.

### Score and controls

The score capsule is visually compact and uses hierarchy rather than large type. Labels are small
and muted; values are medium-sized and high contrast. A subtle divider separates Score and Best.

Bomb and Vibration use equal square/circular tile geometry instead of generic floating Material
buttons. Badges are anchored consistently at the top end and never overlap the icon. Disabled shake
uses a clearly desaturated state while retaining readable semantics.

The Next fruit capsule includes a small localized label and one glossy fruit preview. It is larger
than either consumable button, because queued-fruit planning is more frequent than power-up use.

## Fruit Art

All ten fruits remain original runtime Canvas drawings. Each level receives:

- an unmistakable silhouette;
- a multi-stop body gradient or layered radial shading;
- one restrained specular highlight and soft lower-edge occlusion;
- fruit-specific surface detail such as seeds, seams, segments, pores, lattice, or stripes;
- fruit-specific leaf/stem/crown geometry;
- a face scaled to the available radius with varied eye, mouth, and blush character;
- blink, impact, danger, and merge expression variants that respect reduced motion.

Small fruits prioritize silhouette and high-contrast details over texture density. Large fruits may
use more detail, but drawing remains bounded and allocation-free inside the hot Canvas path.
Evolution-strip fruit use the same renderer with detail automatically reduced by radius.

## Tutorial

The tutorial remains persisted, two-step, and pass-through:

1. Tap: spotlight the real preview fruit and show a short downward touch demonstration.
2. Drag: spotlight the preview rail and demonstrate horizontal positioning before release.

The overlay uses measured board geometry, a real glossy preview fruit, a thin dotted guide, a
smaller hand, and one compact coral instruction capsule. The scrim is lighter than the current
implementation and preserves the glass as the visual focus. Only Skip consumes input. Completing
the drag step produces a small bounded particle burst and fades the overlay. Reduced motion shows
the final static gesture pose and omits looping movement and particles.

## Shake Behavior

Shake becomes a deterministic timed phase lasting 2.25 seconds.

- The first accepted free or paid request enters shake-active state and consumes exactly one use.
- While active, additional free/paid shake requests are rejected and the Vibration control is
  disabled.
- Fixed simulation time schedules several alternating horizontal/upward impulses rather than one
  initial impulse. Random values come only from the persisted deterministic random state.
- The glass receives a draw-layer translation/rotation sequence synchronized to normalized shake
  progress. The rest of the screen and system bars do not move.
- Ordinary collision, merge, danger grace, and fixed-step caps continue throughout the phase.
- The final fixed step clears shake-active state and returns the glass transform to identity.
- Reduced motion keeps the repeated physical impulses but replaces the visual glass oscillation
  with a short static coral outline emphasis.

Shake phase/progress belongs to immutable game state so UI enablement, persistence, tests, and
physics cannot disagree. Snapshot restoration sanitizes impossible or non-finite shake values.

## Procedural Audio

The existing session-owned immutable audio program and public MiniApp audio API remain unchanged.
Only original declarations and tests inside `:game:fruitmerge` are modified.

### Drop SFX

The drop sound communicates soft fruit mass rather than a UI click:

- a short low-mid sine/triangle body thump;
- a quieter, slightly delayed elastic bounce;
- a filtered seeded-noise leaf/rind flick;
- fast decay, conservative gain, and enough headroom for music and rapid merges.

### Clear SFX

The clear sound communicates a cute fruit slice without harshness:

- a very short descending filtered-noise slice/whoosh;
- a rounded juicy pop immediately after the cut;
- a tiny bright release accent;
- no explosion preset, distortion-heavy transient, or realistic blade recording.

The current music and merge hierarchy remain unless acoustic tests show masking. Offline render
tests assert determinism, finite samples, audibility, peak headroom, temporal separation of the
drop layers, and a brief high-frequency slice followed by a lower pop for clear.

## State and Data Flow

1. UI sends `requestShakeGate` once.
2. The component resolves free/ad gating as today.
3. The Store accepts the authorized shake intent.
4. The engine starts the timed shake phase, consumes the use, and publishes `ShakeApplied` once.
5. Fixed `Frame` intents advance shake progress, apply scheduled impulses, and eventually finish
   the phase.
6. UI derives button enabled state and glass transform from immutable game state.
7. Audio reacts once to `ShakeApplied`; it does not retrigger for each physics impulse.

Drop and clear keep their current Store labels; the audio adapter maps those labels to the revised
SFX declarations. Result background derives from Decompose child state and does not introduce an
imperative platform callback.

## Performance Constraints

- Keep one Canvas for the live glass and one bounded Canvas for the evolution strip.
- Do not create a composable, coroutine, animation object, or image bitmap per fruit.
- Precompute or remember static paths/brush parameters when safe; frame-rate state is read in draw
  or graphics-layer blocks.
- Bound shake impulses and keep the existing maximum body count, fixed-step cap, and spatial grid.
- Avoid blur, runtime shader requirements, full-screen per-frame offscreen layers, and particle
  counts that scale with fruit count.
- Tutorial offscreen compositing exists only while the first-launch overlay is visible.

## Testing and Acceptance

### Engine and persistence

- Shake lasts 2.25 seconds of fixed simulation time within one fixed-step tolerance.
- Multiple scheduled impulses affect bodies during the phase.
- A second shake request is rejected without consuming another use or advancing random state.
- Completion resets shake state and glass progress.
- Snapshot round-trip preserves a valid active phase and sanitizes invalid values.

### Store and component

- One accepted shake publishes one label even though it contains several impulses.
- Shake button/model remains disabled while active and re-enables on completion.
- Paid tokens cannot start a duplicate shake during the active phase.

### UI

- Compact portrait semantics follow Score, Best, Bomb, Vibration, Next fruit, Board, Evolution.
- Score/actions/Next are outside the board bounds.
- Medium/expanded layouts retain the same reading order in the supporting pane.
- Tutorial remains pass-through except for Skip and uses measured board bounds.
- Result and its full-frame session background expose the same base color contract.
- Reduced-motion paths contain no looping tutorial or glass oscillation.

### Audio

- Drop and Clear SFX compile, render deterministically, stay finite/audible, and preserve headroom.
- Drop contains separated body/bounce energy.
- Clear contains an early bright slice and later rounded pop.
- Rapid SFX over active music stay within existing mobile render budgets.

### End-to-end verification

- `:game:fruitmerge:allTests`
- `:game:fruitmerge:validateMiniAppDependencies`
- Android and iOS simulator compilation for `:game:fruitmerge`
- `:miniapp:bundle:verifyMiniAppBundle`
- `:composeApp:compileAndroidMain`
- `:androidApp:assembleDebug`
- `:composeApp:linkDebugFrameworkIosSimulatorArm64`

## Provenance

The supplied screenshot informs only broad hierarchy: score above play, consumables and queued fruit
near the top, a dominant glass, and an evolution strip below. All code, visual geometry, fruit
expression, motion timing, music, and SFX parameters remain original to this repository. The module
provenance and submission record will be updated with the final implemented behavior.
