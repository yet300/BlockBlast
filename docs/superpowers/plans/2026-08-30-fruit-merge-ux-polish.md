# Fruit Merge UX Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved Fruit Merge interaction, onboarding, audio, Decompose result flow, visual readability, and drop pacing improvements without weakening the game's mobile performance bounds.

**Architecture:** Keep `FruitMergeStore` retained and authoritative, add explicit effect labels, and place a Decompose `ChildStack` above Playing and Result child components. Rendering remains Canvas-first; a session-scoped audio adapter consumes committed Store labels, and tutorial persistence uses the existing `MiniAppStorage` facade.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Decompose `ChildStack`, MVIKotlin, Metro DI, MiniApp procedural audio, Compose Resources, `kotlin.test` and Compose UI tests.

---

## File Map

**Create:**

- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudio.kt` — immutable music/SFX program and typed names.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudioAdapter.kt` — session-bound command routing and rejection consumption.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponent.kt` — Decompose Playing/Result stack and effect collection.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergePlayingComponent.kt` — Playing model, gestures, gates, tutorial state, and frame ownership.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeResultComponent.kt` — immutable result presentation contract.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeFruitArt.kt` — shared Canvas fruit silhouettes, faces, and palette.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeTutorial.kt` — two-step Block-Blast-style onboarding overlay.
- `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeResultScreen.kt` — full-screen result destination.
- Matching focused common tests for audio, session navigation, tutorial persistence, gesture mapping, and result UI.

**Modify:**

- Engine state/rules/tests for the 450 ms cooldown and moving-body shake.
- Store/tests for explicit committed effect labels.
- Persistence/tests for `tutorial_seen`.
- Session graph bindings and session rendering for the new component and audio adapter.
- Playing screen/board/tests for full-viewport input, icon HUD, bottom board, evolution strip, and distinct fruit art.
- Compose resources, README, and provenance for localized copy and original procedural audio disclosure.

## Task 1: Deterministic Drop Cooldown and Reliable Shake

**Files:**

- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeState.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngine.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngineTest.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeStressTest.kt`

- [ ] **Step 1: Write failing cooldown and shake tests**

Add tests that assert the public rule seam rather than implementation details:

```kotlin
@Test
fun `accepted drop starts cooldown and second drop does not advance rng`() {
    val first = engine.drop(FruitMergeState()).state
    val second = engine.drop(first)

    assertEquals(ActionRejection.DROP_COOLDOWN, second.rejection)
    assertEquals(first, second.state)
    assertEquals(FruitMergeEngine.DROP_COOLDOWN_SECONDS, first.dropCooldownSeconds)
}

@Test
fun `fixed steps make drop ready after cooldown`() {
    val dropped = engine.drop(FruitMergeState()).state
    val ready = generateSequence(dropped) { engine.step(it, 1f / 60f) }
        .drop(27)
        .first()

    assertEquals(0f, ready.dropCooldownSeconds)
    assertNull(engine.drop(ready).rejection)
}

@Test
fun `shake applies to moving bodies and consumes one free use`() {
    val moving = FruitMergeState(bodies = listOf(body(id = 1, velocity = Vec2(0.2f, 0.1f))))
    val result = engine.shake(moving)

    assertNull(result.rejection)
    assertEquals(moving.freeShakes - 1, result.state.freeShakes)
    assertNotEquals(moving.bodies, result.state.bodies)
}
```

- [ ] **Step 2: Run the focused engine tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeEngineTest*'`

Expected: failure because `DROP_COOLDOWN`, `DROP_COOLDOWN_SECONDS`, and `dropCooldownSeconds` do not exist and moving bodies are rejected.

- [ ] **Step 3: Add transient cooldown state and rules**

Add `dropCooldownSeconds: Float = 0f` to `FruitMergeState`, add `DROP_COOLDOWN` to `ActionRejection`, and expose:

```kotlin
const val DROP_COOLDOWN_SECONDS: Float = 0.45f
```

`drop()` returns `DROP_COOLDOWN` while the value is positive and sets it to `DROP_COOLDOWN_SECONDS` only on an accepted drop. `step()` decrements it with `(value - elapsedSeconds).coerceAtLeast(0f)`. `newRun()` resets it through the default constructor. Remove the moving-body rejection from `shake()` while retaining the empty-board rejection and all impulse caps.

- [ ] **Step 4: Run engine and stress tests**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeEngineTest*' --tests '*FruitMergeStressTest*'`

Expected: all selected tests pass and the one-minute bounded simulation remains finite.

- [ ] **Step 5: Commit the engine slice**

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine
git commit -m "fix: pace fruit drops and enable shake"
```

## Task 2: Explicit Store Effects

**Files:**

- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStore.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreFactory.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreTest.kt`

- [ ] **Step 1: Write failing label-order tests**

Subscribe to `store.labels` and verify exact committed effects:

```kotlin
assertEquals(
    listOf(FruitMergeStore.Label.DropAccepted),
    labelsAfter(FruitMergeStore.Intent.Drop),
)
assertEquals(
    listOf(FruitMergeStore.Label.ShakeApplied),
    labelsAfter(FruitMergeStore.Intent.FreeShake, state = settledBoard),
)
```

Advance a controlled equal-fruit collision and assert one `MergeResolved(level)` followed by `ResultReached` only when each transition is actually committed.

- [ ] **Step 2: Run Store tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeStoreTest*'`

Expected: failure because only `ResultReached` exists.

- [ ] **Step 3: Add committed effect labels**

Define:

```kotlin
sealed interface Label {
    data object DropAccepted : Label
    data class MergeResolved(val level: FruitLevel) : Label
    data object ClearApplied : Label
    data object ShakeApplied : Label
    data object ResultReached : Label
}
```

Publish action labels only when `ActionResult.rejection == null` and state changed. During fixed stepping, compare body IDs/levels before and after each rule step and publish created higher levels in deterministic ordinal order. Preserve the existing single phase-transition guard for `ResultReached`.

- [ ] **Step 4: Run Store tests**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeStoreTest*'`

Expected: all selected tests pass with no label for rejected cooldown or empty-board actions.

- [ ] **Step 5: Commit the Store slice**

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/store
git commit -m "feat: publish fruit merge game effects"
```

## Task 3: Tutorial Persistence and Decompose Session Flow

**Files:**

- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergePersistence.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeComponent.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponent.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergePlayingComponent.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeResultComponent.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionBindings.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergePersistenceTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponentTest.kt`

- [ ] **Step 1: Write failing tutorial persistence tests**

```kotlin
@Test
fun `tutorial seen survives repository recreation`() = runTest {
    val storage = MemoryMiniAppStorage()
    FruitMergePersistence(storage).markTutorialSeen()

    assertTrue(FruitMergePersistence(storage).isTutorialSeen())
}
```

Also verify read/write failures fall back safely and cancellation propagates.

- [ ] **Step 2: Write failing Decompose navigation tests**

Use a lifecycle harness and retained test Store. Assert initial Playing/Result selection from restored phase, `ResultReached` replacement, committed New Game return, and clear-target Back interception.

- [ ] **Step 3: Run persistence and session tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergePersistenceTest*' --tests '*FruitMergeSessionComponentTest*'`

Expected: failure because tutorial storage and the child stack do not exist.

- [ ] **Step 4: Add tutorial persistence**

Add local key `tutorial_seen` and cancellation-safe methods:

```kotlin
suspend fun isTutorialSeen(): Boolean = storage.getBoolean(TUTORIAL_SEEN_KEY, false)
suspend fun markTutorialSeen() = storage.putBoolean(TUTORIAL_SEEN_KEY, true)
```

Keep this preference outside the versioned game snapshot so cooldown and run restoration remain independent.

- [ ] **Step 5: Split session and playing contracts**

Define `FruitMergeSessionComponent` with:

```kotlin
val stack: Value<ChildStack<*, Child>>
val frameMode: Value<MiniAppFrameMode>
fun completePaidAction(token: PaidActionToken)
fun handleBack(): Boolean

sealed interface Child {
    class Playing(val component: FruitMergePlayingComponent) : Child
    class Result(val component: FruitMergeResultComponent) : Child
}
```

Move the current Store-facing methods to `FruitMergePlayingComponent`. Add `TutorialStep.Tap`, `TutorialStep.Drag`, and nullable hidden state to its model. A real accepted tap advances; an accepted drag completes and persists; Skip persists immediately. The component, not Compose, owns these transitions.

- [ ] **Step 6: Implement the ChildStack**

Use serializable unique configs `Playing(runOrdinal)` and `Result(runOrdinal)`, `replaceAll`, and `handleBackButton = false`. Collect Store labels once in the retained session component. `ResultReached` navigates to Result; new-game completion navigates back only after the Store state reports the incremented run ordinal.

- [ ] **Step 7: Update Metro bindings and run tests**

Bind the retained Store, Playing/Result construction inputs, and `FruitMergeSessionComponent` in the public `FruitMergeSessionBindings` container without exposing internal providers.

Run: `./gradlew :game:fruitmerge:allTests :game:fruitmerge:compileAndroidMain :game:fruitmerge:compileKotlinIosSimulatorArm64`

Expected: tests and both platform compilations pass.

- [ ] **Step 8: Commit the session slice**

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionBindings.kt game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/session
git commit -m "feat: add fruit merge session flow and onboarding state"
```

## Task 4: Fruit Crate Music and Gameplay SFX

**Files:**

- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudio.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudioAdapter.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionBindings.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponent.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudioProgramTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/audio/FruitMergeAudioAdapterTest.kt`

- [ ] **Step 1: Write failing audio declaration and routing tests**

Compile the declaration with the public test renderer and assert the exact typed names exist. Record `MiniAppAudio` calls and verify:

```kotlin
when (label) {
    FruitMergeStore.Label.DropAccepted -> FruitMergeAudio.Drop
    is FruitMergeStore.Label.MergeResolved -> when (label.level) {
        FruitLevel.BLUEBERRY, FruitLevel.CHERRY, FruitLevel.STRAWBERRY -> FruitMergeAudio.MergeLow
        FruitLevel.PLUM, FruitLevel.MANDARIN, FruitLevel.APPLE, FruitLevel.PEAR -> FruitMergeAudio.MergeMid
        FruitLevel.PEACH, FruitLevel.PINEAPPLE, FruitLevel.MELON -> FruitMergeAudio.MergeHigh
    }
    FruitMergeStore.Label.ClearApplied -> FruitMergeAudio.Clear
    FruitMergeStore.Label.ShakeApplied -> FruitMergeAudio.Shake
    FruitMergeStore.Label.ResultReached -> FruitMergeAudio.GameOver
}
```

Assert duplicate fullness controls do not enqueue repeated commands and every rejection branch is consumed without retry.

- [ ] **Step 2: Run audio tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeAudio*'`

Expected: failure because the declaration and adapter do not exist.

- [ ] **Step 3: Author the immutable program**

Use fixed seeds and public DSL only. Include renamed `GlassBell`, `PlacementClick`, `PowerUp`, and `SuccessSweep` presets. Add one original low wooden transient instrument and one filtered-noise rolling instrument because no preset expresses the crate role. Use a sparse deterministic pattern, low gains, seeded stereo modulation, and one `fullness` control mapped to restrained density/gain ranges.

- [ ] **Step 4: Route committed labels**

`FruitMergeAudioAdapter.start()` calls `playMusic` once. `updateFullness(bodyCount)` quantizes to a small stable range and sends only changed controls. `play(label)` selects one SFX tier and consumes `AudioCommandResult` exhaustively.

- [ ] **Step 5: Run audio render and adapter tests**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeAudio*'`

Expected: declaration validates, rendered buffers are finite/non-silent within headroom, and routing tests pass.

- [ ] **Step 6: Commit the audio slice**

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/audio game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeSessionComponent.kt game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionBindings.kt game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/audio
git commit -m "feat: add fruit crate music and sfx"
```

## Task 5: Full-Viewport Gestures, Icon HUD, and Fruit Art

**Files:**

- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeFruitArt.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/composeResources/values/strings.xml`
- Modify: `core/uikit/src/commonMain/kotlin/ge/yet/game/uikit/components/icon/BombFilled.kt` only to consume the user's final working-tree version; do not overwrite its vector data.
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreenTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeGestureMapperTest.kt`

- [ ] **Step 1: Write failing gesture mapper tests**

Extract a pure mapper from viewport point and board bounds to normalized X. Assert points above, inside, and below the board map identically by X; out-of-range X clamps to the current preview radius; clear hit testing uses translated board coordinates.

- [ ] **Step 2: Write failing HUD/layout accessibility tests**

At compact and expanded sizes, assert Bomb/Vibration test tags exist, action text does not render, content descriptions include remaining count/ad disclosure, the evolution strip exists, and board bounds occupy the lower layout. Verify every action target is at least 48 dp.

- [ ] **Step 3: Run UI tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeScreenTest*' --tests '*FruitMergeGestureMapperTest*'`

Expected: failure because gestures remain board-local and text buttons remain.

- [ ] **Step 4: Move pointer input to the Playing viewport**

Capture board bounds in viewport coordinates and attach one pointer layer to the full Playing root. Keep callbacks in `rememberUpdatedState`; map every tap/drag release through the pure mapper. Feed gesture kind to the Playing component so tutorial progression depends on an accepted action.

- [ ] **Step 5: Build the adaptive icon HUD and bottom board**

Use design-system cream/surface/coral colors, circular 48 dp icon buttons, `BombFilled`, `Vibration`, numeric/ad badges, and a selected targeting state. Remove normal-play hint text and full-width action buttons. Anchor the maximized square board above the evolution strip in compact mode; use `AdaptiveGameScaffold` supporting content for expanded mode.

- [ ] **Step 6: Draw distinct fruit silhouettes**

Move common face rendering into `FruitMergeFruitArt.kt`. Branch by `FruitLevel` for blueberry crown, strawberry taper/seeds, cherry lobes/stem, and later-level clefts, seams, dimples, and melon texture. Keep blink, squash, impact, and anxious expressions in the same Canvas pass. Add one evolution-strip Canvas that uses the same renderer.

- [ ] **Step 7: Add cooldown and shake motion feedback**

Read cooldown alpha in Canvas draw state and animate one readiness settle with the smallest target-state API. Apply a short visual board translation/rotation when a committed `ShakeApplied` effect ID changes; read the animated transform in `graphicsLayer` to avoid per-frame subtree recomposition.

- [ ] **Step 8: Run UI and module tests**

Run: `./gradlew :game:fruitmerge:allTests :game:fruitmerge:compileAndroidMain :game:fruitmerge:compileKotlinIosSimulatorArm64`

Expected: all tests and platform compilation pass.

- [ ] **Step 9: Commit the Playing UI slice**

```bash
git add core/uikit/src/commonMain/kotlin/ge/yet/game/uikit/components/icon/BombFilled.kt game/fruitmerge/src/commonMain game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui
git commit -m "feat: polish fruit merge playing experience"
```

## Task 6: Block-Blast-Style Tutorial and Result Destination UI

**Files:**

- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeTutorial.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeResultScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeContent.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSession.kt`
- Modify: `game/fruitmerge/src/commonMain/composeResources/values/strings.xml`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeTutorialTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeResultScreenTest.kt`

- [ ] **Step 1: Write failing tutorial motion/semantics tests**

Assert hidden tutorial contributes no semantics, Tap and Drag expose different localized captions, active normal motion starts the demo loop, reduced motion remains static, Skip invokes one persistence event, and successful real gestures pass through to the game.

- [ ] **Step 2: Write failing result destination tests**

Render Result at compact height and 200% font scale. Assert final score, best score, largest-fruit semantics, and reachable New Game action. Assert the old in-board ResultCard tag/content is absent.

- [ ] **Step 3: Run focused UI tests and confirm failure**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeTutorialTest*' --tests '*FruitMergeResultScreenTest*'`

Expected: failure because neither destination exists.

- [ ] **Step 4: Implement the tutorial overlay**

Follow the Block Blast local pattern: offscreen scrim, clear spotlight, pill caption, `Animatable` hand/ghost loop only while visible and active, pass-through input, reduced-motion static pose, Skip at the top, and fade/confetti completion. Use Fruit Merge-owned geometry and resources rather than copying the reference screenshots.

- [ ] **Step 5: Render the ChildStack and result screen**

`FruitMergeContent` renders Decompose `Children` with a fade/scale stack animation. Playing receives ad callbacks; Result uses `FruitMergeResultScreen`. `FruitMergeSession.frameMode` maps the active child to Standard or ContentOnly. The result uses a scroll-safe design-system surface and one primary New Game button.

- [ ] **Step 6: Run tutorial, result, and session tests**

Run: `./gradlew :game:fruitmerge:allTests`

Expected: all common/iOS simulator tests pass.

- [ ] **Step 7: Commit the navigation UI slice**

```bash
git add game/fruitmerge/src/commonMain game/fruitmerge/src/commonTest
git commit -m "feat: add fruit merge onboarding and result screens"
```

## Task 7: Documentation and End-to-End Verification

**Files:**

- Modify: `game/fruitmerge/README.md`
- Modify: `game/fruitmerge/PROVENANCE.md`
- Modify tests only if a final platform graph exposes a real missing cross-module contract.

- [ ] **Step 1: Document behavior and provenance**

Record controls, 450 ms pacing, five clear attempts, three shake attempts, first-launch tutorial persistence, Decompose result flow, and the original procedural fruit-crate audio. State that the screenshots were layout/onboarding references only and no reference asset/audio/code was copied.

- [ ] **Step 2: Run the MiniApp gate**

Run: `./gradlew :game:fruitmerge:verifyMiniApp`

Expected: dependency validation, tests, Android compile, and iOS simulator compile pass.

- [ ] **Step 3: Run final application graphs**

Run: `./gradlew :androidApp:assembleDebug`

Expected: Android APK assembles and Metro resolves Fruit Merge session/audio bindings.

Run: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`

Expected: iOS simulator framework links successfully.

- [ ] **Step 4: Check source hygiene and ownership**

Run: `git diff --check`

Expected: no whitespace errors. Confirm `settings.gradle.kts` remains an unrelated maintainer/user change and is not included in agent commits.

- [ ] **Step 5: Commit documentation**

```bash
git add game/fruitmerge/README.md game/fruitmerge/PROVENANCE.md
git commit -m "docs: document fruit merge polish"
```

- [ ] **Step 6: Review the branch diff**

Run: `git status --short --branch`

Run: `git log --oneline --decorate -12`

Run: `git diff HEAD~7..HEAD --stat`

Expected: only scoped Fruit Merge, approved UIKit Bomb icon, tests, and documentation are committed; the user-owned allowlist edit remains outside the commits.
