# Fruit Merge Visual and Audio Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine Fruit Merge so its layout, fruit art, tutorial, shake behavior, result background, and procedural effects feel cohesive with the Logica design system and remain smooth on low-end devices.

**Architecture:** Keep rules deterministic in the game engine, expose only state-derived UI behavior, and retain the existing Decompose child flow. Extend the shared adaptive scaffold with an optional header slot, render the game as bounded Canvas layers, and author immutable procedural audio declarations through the public MiniApp audio API.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Decompose, MVIKotlin, Metro, kotlinx.serialization, MiniApp procedural audio, kotlin.test.

---

## Task 1: Add deterministic timed shake state

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeState.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngine.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshot.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngineTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshotTest.kt`

- [ ] Write failing tests for a 2.25-second active shake, repeated deterministic impulses, duplicate-action rejection, and snapshot round-trip/default compatibility.
- [ ] Run `rtk ./gradlew :game:fruitmerge:allTests` and confirm the new tests fail for the expected missing state/behavior.
- [ ] Add a bounded `shakeStepsRemaining` state, `SHAKE_ACTIVE` rejection, fixed-step impulse cadence, and persistence validation.
- [ ] Re-run `rtk ./gradlew :game:fruitmerge:allTests` and make the task green.
- [ ] Commit the engine and persistence change.

## Task 2: Gate shake through the session component and store

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponent.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStore.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponentTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreTest.kt`

- [ ] Write failing tests proving an active shake disables the action and consumes only one attempt/event.
- [ ] Implement state-derived gating and preserve one initial shake effect without frame-level audio/haptic spam.
- [ ] Run the focused component/store tests, then `rtk ./gradlew :game:fruitmerge:allTests`.
- [ ] Commit the session behavior change.

## Task 3: Unify the result and system-bar background

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSession.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeResultScreen.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionTest.kt`

- [ ] Add a failing test for the frame-mode-to-background-role mapping.
- [ ] Derive the full-frame background from the current Decompose child and use the same result base color inside the result screen.
- [ ] Run the focused test and module tests.
- [ ] Commit the background fix.

## Task 4: Rebuild the adaptive header and board hierarchy

**Files:**
- Modify: `core/uikit/src/commonMain/kotlin/ge/yet/game/uikit/adaptive/AdaptiveGameScaffold.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Test: `core/uikit/src/commonTest/kotlin/ge/yet/game/uikit/adaptive/AdaptiveGameScaffoldTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeUiPolicyTest.kt`

- [ ] Add failing layout-policy tests for compact header-above-board and expanded supporting-pane placement.
- [ ] Add a backward-compatible header slot to `AdaptiveGameScaffold`.
- [ ] Move score/best, bomb, vibration, and Next preview outside the glass; enlarge and bottom-align the board; retain the evolution strip below it.
- [ ] Make shake visuals state-derived, bounded, and disabled under reduced motion while keeping physics active.
- [ ] Run UIKit and Fruit Merge tests plus Android compilation.
- [ ] Commit the adaptive layout change.

## Task 5: Replace the fruit renderer with distinct glossy-kawaii art

**Files:**
- Add: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitVisualSpec.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitVisualSpecTest.kt`

- [ ] Add failing tests requiring a unique silhouette/detail/face identity for every fruit level.
- [ ] Define stable visual metadata and render layered gradients, highlights, leaves/rinds, cheeks, and expressive faces in the existing single Canvas.
- [ ] Keep draw-time allocations bounded and avoid per-fruit composables.
- [ ] Run Fruit Merge tests and Android compilation.
- [ ] Commit the fruit-art refinement.

## Task 6: Refine the measured pass-through tutorial

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeTutorial.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeTutorialTest.kt`

- [ ] Add failing tests for normal/reduced-motion tutorial policy and exact board-guidance state.
- [ ] Replace the heavy overlay with a light measured spotlight, compact Logica caption, preview-aligned guide, and subtle hand/ripple animation.
- [ ] Preserve pass-through gameplay gestures and keep only Skip explicitly interactive.
- [ ] Run module tests and compile Android.
- [ ] Commit the tutorial refinement.

## Task 7: Author fruit-like drop and cute slice procedural SFX

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudio.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudioTest.kt`

- [ ] Add failing acoustic-shape assertions for a soft impact/bounce drop and a short slice/whoosh plus juicy pop clear.
- [ ] Replace generic preset includes for Drop and Clear with original bounded declarations using the public audio DSL.
- [ ] Verify deterministic, finite, audible, headroom-safe renders and temporal envelopes.
- [ ] Run `rtk ./gradlew :game:fruitmerge:allTests`.
- [ ] Commit the audio refinement.

## Task 8: Complete verification and acceptance record

**Files:**
- Modify: `docs/miniapp/fruit-merge-acceptance.md`

- [ ] Update the acceptance record with the new hierarchy, shake timing, tutorial, fruit art, audio intent, and performance constraints.
- [ ] Run `rtk ./gradlew :game:fruitmerge:verifyMiniApp`.
- [ ] Run `rtk ./gradlew :miniapp:bundle:verifyMiniAppBundle`.
- [ ] Run `rtk ./gradlew :composeApp:compileAndroidMain`.
- [ ] Run `rtk ./gradlew :androidApp:assembleDebug`.
- [ ] Run `rtk ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`.
- [ ] Run `rtk git diff --check` and inspect the scoped diff/status without touching unrelated user changes.
- [ ] Commit the acceptance record and any verification-only adjustments.
