# Fruit Merge Market Identity Redesign

**Date:** 2026-08-31  
**Status:** Approved in conversation; pending implementation plan  
**Scope:** `:game:fruitmerge`, narrowly required reusable UIKit pieces, and existing MiniApp host integration tests

## Objective

Give Fruit Merge a recognizable identity beyond a generic Suika-like presentation while preserving its immediate drop-and-merge loop, deterministic simulation, MiniApp boundaries, and performance on weak mobile devices.

The game becomes a small warm fruit-market stall. Its primary differentiator is not a cosmetic skin: each fruit tier has a deterministic physical character that affects placement decisions.

## Product principles

1. **Silhouette before face.** A fruit must be identifiable without facial features or color.
2. **The UI belongs to the world.** Score, next preview, tools, container, and evolution strip look like parts of the market stall rather than floating mobile-game widgets.
3. **Readable physics, not randomness.** Every fruit type always has the same behavior.
4. **Juice must follow simulation.** Landing, merge, shake, danger, and audio feedback are driven by committed game events.
5. **Mobile budgets are hard limits.** The redesign keeps circle colliders, the spatial grid, bounded bodies/effects, and one Canvas-based board renderer.

## Visual world

### Market scene

- The full session background is a warm fair-stall scene using the existing cream, coral, and warm-ink design-system roles.
- The play container is a light wooden produce crate with rounded inner corners and restrained board joints.
- The crate occupies most of the available width and is aligned as low as the adaptive viewport permits.
- A narrow market shelf below the crate contains the complete fruit evolution chain.
- The score is a compact paper price tag in the host top-bar center content.
- The next fruit rests in a small woven basket near the upper-right edge of the crate.
- The clear tool is a friendly fruit slicer attached to the left outer edge of the crate.
- The shake tool is the crate's physical handle attached to the right outer edge.
- The permanent host-owned banner remains below the MiniApp viewport when monetization policy permits it. The game scene ends cleanly above the reserved banner slot; the ad itself is neither masked nor visually restyled.

### Art bible

- One warm dark outline with the same relative thickness for every fruit.
- One light source from the upper left.
- One base color, one shadow level, and one highlight level per fruit.
- A bounded shared palette; no one-off cool whites or inconsistent gradients.
- Faces are secondary and event-driven. Resting fruits use minimal eyes or a neutral character mark, without mandatory smiles and pink cheeks.
- Full expressions appear for falling, impact, merge, shake, and danger states.
- Fruit silhouettes deliberately vary in width, height, crown, lobes, and balance.

The first four tiers are explicitly redesigned:

| Tier | Public identity | Silhouette rule |
|---|---|---|
| 1 | Blueberry | Small, slightly flattened berry with a star-shaped crown |
| 2 | Raspberry | Visible large drupelets forming an irregular outline |
| 3 | Strawberry | Angular heart shape with a strong leafy crown |
| 4 | Lime | Oval body with pointed ends and a small peel/cut accent |

The final tier is presented publicly as Watermelon. Existing internal/persisted names are migrated as described below.

## Gameplay identity

### Deterministic fruit profiles

Every tier owns an immutable `FruitPhysicsProfile`. The profile is applied by the existing circle-contact solver and never changes randomly between bodies or runs.

| Fruit | Player-readable physical character | Intended simulation expression |
|---|---|---|
| Blueberry | Light and bouncy | Higher restitution with strictly bounded speed |
| Raspberry | Rough and stable | Higher tangential/contact damping; settles quickly |
| Strawberry | Briefly grips a wall | Temporary wall-grip interval, then guaranteed release |
| Lime | Agile spinner | Converts more tangential impact into angular velocity |
| Mandarin | Smooth roller | Lower rolling resistance and slower horizontal decay |
| Apple | Dense and forceful | Higher effective mass, especially against lower tiers |
| Pear | Awkward balance | Deterministic balance bias producing a small side torque/impulse |
| Peach | Soft stabilizer | Lower restitution and stronger impact absorption |
| Pineapple | Neighbor grip | Higher bounded tangential friction against contacting bodies |
| Watermelon | Heavy shock | One bounded radial impulse when created by a merge or on a qualifying first heavy impact |

Pair coefficients are computed only for contacts already returned by the spatial grid. No polygon, compound, leaf, or pixel collider is introduced.

### Fairness constraints

- Strawberry wall grip lasts approximately `0.35–0.5 s` and cannot permanently suspend a fruit.
- Watermelon shock is edge-triggered, clamped, and cannot fire continuously on resting contact.
- Trait impulses cannot move a body outside the container or exceed existing maximum-speed policies.
- A falling body does not show danger feedback until it has joined the pile through a floor or fruit contact.
- The existing drop cooldown remains authoritative. Direction-guide fading communicates the cooldown without explanatory text.
- Trait behavior is deterministic for identical state, input, seed, and fixed-step timing.

### Motion language

Landing follows:

`fall -> squash -> rebound -> wobble -> rest`

Merge presentation follows:

`two bodies compress toward contact -> 120–150 ms intermediate squeeze -> new tier expands -> tier-specific settle`

The intermediate merge shape is presentation-only. The engine still commits one unambiguous replacement body.

Motion derives from body velocity, impact, angle, trait, and short bounded visual-event records. The implementation must not create one `Animatable` or one composable per body.

## Shake and clear tools

### Fruit slicer

- Replaces the bomb metaphor while preserving the existing clear economy.
- Five free uses remain consumable; later uses continue through rewarded advertising when enabled.
- Targeting mode highlights valid fruits and makes cancellation visually explicit.
- The removal presentation is a quick blade arc, a soft split/squeeze, and a small bounded juice/leaf effect.

### Crate handle

- Replaces the generic vibration-button metaphor while preserving the shake economy.
- Shake lasts approximately `2.2 s` and contains several impulses with a decaying envelope.
- Physics impulses, crate transform, handle motion, and SFX pulses share the same phase source.
- The control is disabled from accepted request until the final shake step.
- Reduced-motion mode keeps gameplay impulses but reduces the screen-space crate transform.

## Screen composition and input

### Playing state

- Host Back and Settings controls remain host-owned.
- The price-tag score uses `MiniAppSession.TopBarContent`; values format as `950`, `1.2K`, `3.4M`, and so on.
- The next basket, suspended preview, guide marks, crate, tools, and evolution shelf are one coherent market composition.
- Guide marks use hand-drawn short strokes and wooden side arrows rather than a generic dotted UI line.
- After a committed drop, the basket visibly empties and the next fruit travels along a short arc to the suspended preview position.

### Whole-viewport gestures

- Tap and horizontal drag are accepted throughout the game viewport, including the space above the crate.
- Horizontal input maps to the crate's legal normalized drop range and is clamped by the preview fruit radius.
- Interactive controls consume their pointer sequence and never cause a drop.
- Clear targeting remains board-specific and takes precedence over dropping.

### Adaptive layout

- Compact portrait prioritizes crate height and stacks auxiliary content vertically.
- Wider viewports cap the crate width and attach tools to side rails.
- All interactive targets remain at least `48 dp`.
- The banner's host-reserved height is stable before and after ad content loads.
- The game continues to use the shared adaptive game scaffold and existing UIKit components where they are genuinely reusable; market-specific props remain game-owned.

## Tutorial

The tutorial follows the visual pacing of Block Blast without copying its artwork.

1. Dim the market scene except the basket, guide, and crate; an animated hand demonstrates tap and drag.
2. Highlight two matching fruits and demonstrate the squeeze-to-next-tier merge.
3. Demonstrate a wall-gripping strawberry, an apple moving a berry, and the crate handle.

There are no large instructional text cards. The tutorial uses gesture animation, pictograms, focus lighting, a three-step indicator, and a compact skip icon. Accessibility descriptions remain complete even when visible copy is minimal. Reduced-motion mode substitutes discrete staged poses for long gesture loops.

## Game-over state

Game over is a state of the same game screen, not a separate result child or dialog.

- `FruitMergeScreenState.Playing` and `FruitMergeScreenState.GameOver` derive from the store's `RunPhase`.
- `DefaultFruitMergeSessionComponent` remains the single Decompose lifecycle component and retained Store owner.
- The internal `ChildStack<Playing, Result>` and duplicate result background are removed.
- `MarketScene` remains mounted once. On game over, the crate settles and dims while a result price tag enters from above.
- The session may derive `MiniAppFrameMode.ContentOnly` from the store phase to hide host toolbar chrome, but the same session background continues behind status and navigation bars.
- Starting a new game changes store state back to Playing without recreating a Decompose child.
- Back from GameOver delegates to the host instead of resurrecting a terminal run.

## Procedural audio

Audio stays within the public MiniApp audio API. The immutable game-owned declaration remains outside Compose; the session-bound facade continues to own lifecycle and settings suppression.

### Music

- Original `82–86 BPM` crate groove.
- Low layer: short wooden knocks suggesting fruit touching the crate.
- Middle layer: soft rolling gestures moving across stereo.
- Upper layer: sparse glass-like notes forming an original short market melody.
- Accents are intentionally uneven so the loop feels hand-shaken rather than drum-machine regular.
- Every random/degraded operation uses a stable explicit seed.
- One infrequently updated intensity/danger control may enrich the arrangement on meaningful state transitions; it is never updated per frame.

### SFX event model

- Release: quiet leaf/peel movement.
- First landing: rounded fruit impact selected from small, medium, or heavy mass groups and triggered by `hasJoinedPile: false -> true`.
- Merge: wet compression plus a clean tiered appearance tone.
- Clear: fast blade-air transient, soft juicy cut, and a small upward pluck.
- Shake: short alternating wood/fruit pulses emitted in phase with shake impulses, not one continuous noise burst.
- Danger entry: one restrained warning squeak per danger episode.
- Game over: a settling crate plus two soft descending tones.

Rejected audio commands are consumed without retry loops. Gains leave headroom for music, shake pulses, and merge SFX to overlap.

## State, persistence, and compatibility

- Rename player-facing tier identities to Raspberry, Lime, and Watermelon.
- Persistence decoding accepts explicit legacy aliases:
  - `CHERRY -> RASPBERRY`
  - `PLUM -> LIME`
  - `MELON -> WATERMELON`
- Persist a new schema version only if a gameplay-relevant trait timer/edge flag must survive process death. Pure render timing never enters the snapshot.
- A restored body is treated as already belonging to the pile, preserving the airborne-danger rule.
- Random seeds and all new state remain finite, bounded, and validated during restore.

## Event and state flow

1. Pointer input produces a typed component intent.
2. The Store validates cooldown, targeting, paid-action gate, and phase.
3. The engine advances fixed-step physics and deterministic trait behavior.
4. Committed transitions publish bounded semantic events such as first landing, merge tier, shake pulse, danger entry, and game over.
5. Audio maps semantic events to typed SFX names.
6. Canvas presentation derives bounded transient effects from those same events.
7. Persistence checkpoints only authoritative gameplay state.

No business rules, paid-action decisions, persistence mutation, or audio-program construction move into Compose.

## Performance budgets

- Preserve the existing maximum body count and fixed-step cap.
- Preserve spatial-grid broad phase; no new all-pairs contact pass.
- Trait work is constant-time per existing contact except the rare, bounded Watermelon radial impulse.
- Use one Canvas board and compact immutable visual specifications.
- Bound particles and transient visual events; prune them by age.
- Avoid per-frame control/audio commands and avoid per-body coroutine/animation objects.
- Keep reduced-motion behavior functionally equivalent while reducing large viewport transforms.

## Verification

### Pure engine and persistence

- Every profile has distinct bounded coefficients.
- Each trait produces its intended deterministic behavior.
- Strawberry releases from walls within the configured interval.
- Watermelon shock fires once and stays within speed/container bounds.
- Airborne bodies never enter danger visuals or danger timing.
- Drop cooldown prevents rapid spam.
- Shake duration, decaying impulse envelope, and action rejection while active are exact.
- Legacy tier names restore to the new identities.
- Equivalent seeds and input sequences produce equivalent states.

### Component and UI

- Playing/GameOver transitions do not replace an internal child.
- Restart reuses the retained Store and returns to Playing.
- Back behavior is correct in both states.
- Game background covers host and system-bar regions in both states.
- Whole-viewport tap/drag works; action controls never leak a drop gesture.
- Score formatting, next transfer, tool disabled states, compact/wide layout, stable banner reservation, tutorial progression, and reduced motion are covered.

### Audio

- The program and exact typed control/SFX contract compile in `commonTest`.
- Offline rendering is deterministic, finite, audible, and below clipping.
- Rapid landing/merge/shake overlap leaves headroom.
- Adapter tests verify semantic event-to-SFX mapping without retry behavior.

### Acceptance commands

At minimum:

```bash
./gradlew :game:fruitmerge:allTests
./gradlew :game:fruitmerge:verifyMiniApp
./gradlew :miniapp:bundle:verifyMiniAppBundle
./gradlew :composeApp:compileAndroidMain
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Non-goals

- Polygon or compound collision geometry.
- Random per-body modifiers.
- Market customer orders in this iteration.
- A new ad SDK or game-owned banner rendering.
- Raster fruit asset generation.
- Changes to the procedural-audio engine, platform sinks, or native audio callbacks.
- A new cross-feature design-system dependency for market-specific artwork.

## Success criteria

The redesign succeeds when a player can recognize the game from its crate, market props, silhouettes, and physical fruit behavior without seeing its title; the first four fruits are immediately distinguishable; every important action has synchronized visual and audio feedback; and the full verification matrix passes without relaxing existing simulation or MiniApp performance bounds.
