# Fruit Merge Market Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Fruit Merge into a warm market-stall game with deterministic fruit-specific physics, one Playing/GameOver screen, coherent Canvas art, synchronized motion/audio, and preserved low-end-device performance.

**Architecture:** Keep the retained MVIKotlin Store as the single authority, the Decompose session component as the lifecycle boundary, the current fixed-step circle solver and spatial grid as the simulation core, and the host-owned banner unchanged. Add immutable per-tier physics/visual profiles and bounded semantic labels; route committed labels once to audio and UI presentation, while authoritative state remains persistable and transient animation stays UI-local.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Canvas, Material 3 Adaptive, Decompose `Value`, MVIKotlin, Metro, kotlinx.serialization, MiniApp procedural audio, kotlin.test, Compose UI tests.

---

## Execution constraints

- Work in the existing `codex/fruit-merge` checkout. Do not create a worktree.
- Preserve unrelated user changes, especially `settings.gradle.kts` and the existing Bomb icon work.
- Do not create commits unless the user explicitly changes the earlier no-commit instruction. Each task ends with a clean-test checkpoint instead of a commit.
- Follow strict red-green-refactor: production Kotlin changes begin only after the focused test has failed for the intended reason.
- Use `apply_patch` for source edits and `rtk` for shell commands.
- Re-index codebase-memory after structural Kotlin changes.

## File map

### Engine and persistence

- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysicsProfile.kt` — immutable profile catalog and validation.
- Modify `FruitLevel.kt` — public tier identities, stable ordering, legacy-name parser.
- Modify `FruitBody.kt` — persisted wall-grip and one-shot trait activation state.
- Modify `FruitPhysics.kt` — profile-aware wall/floor/pair response, angular transfer, bounded Watermelon shock.
- Modify `FruitMergeEngine.kt` — decaying 2.2-second shake and new tier names.
- Modify `FruitMergeSnapshot.kt` and `FruitMergePersistence.kt` — v2 trait persistence and v1 compatibility.

### State, component, and events

- Modify `FruitMergeStore.kt` — typed semantic labels carrying committed level/position information.
- Modify `FruitMergeStoreFactory.kt` — edge detection for landing, shake pulses, danger entry, merge, clear, and result.
- Modify `FruitMergeComponent.kt` — sealed Playing/GameOver model, three-step tutorial, bounded presentation-event flow.
- Modify `FruitMergeSessionComponent.kt` — one permanent game component; remove internal Playing/Result navigation.
- Delete `FruitMergeResultComponent.kt` after its data is mapped into GameOver state.
- Modify `FruitMergeContent.kt` and `FruitMergeSession.kt` — one screen, one background, top-bar score.

### UI

- Create `ui/FruitMergeMarketScene.kt` — market background, adaptive crate composition, basket, tools, evolution shelf.
- Create `ui/FruitMergeResultOverlay.kt` — in-place GameOver price-tag state.
- Create `ui/FruitMergePresentation.kt` — bounded merge/cut/landing overlays driven by presentation events.
- Modify `ui/FruitMergeScreen.kt` — state-holder/plain-UI split, whole-viewport input, event collection, adaptive composition.
- Modify `ui/FruitMergeBoard.kt` — wooden crate, guide, traits, unified expression drawing.
- Modify `ui/FruitVisualSpec.kt` and `ui/FruitMergeVisualModel.kt` — strict art bible and event-driven faces.
- Modify `ui/FruitMergeTutorial.kt` — wordless Gesture/Merge/Traits sequence.
- Delete `ui/FruitMergeResultScreen.kt` after the overlay is covered.
- Modify Compose resources in `commonMain/composeResources/values/strings.xml` for renamed fruits and accessibility copy.

### Audio and reusable UI

- Reuse `core/uikit/.../score/CompactScoreCard.kt` and `compactScore`; do not move market-specific decoration into UIKit.
- Modify `audio/FruitMergeAudio.kt` — revised crate groove and complete typed SFX set.
- Modify `audio/FruitMergeAudioAdapter.kt` — exact semantic-label mapping and intensity transitions.

## Task 1: Rename public fruit identities without losing saves

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitLevel.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshot.kt`
- Modify: `game/fruitmerge/src/commonMain/composeResources/values/strings.xml`
- Modify mechanically: every `FruitLevel.CHERRY`, `FruitLevel.PLUM`, and `FruitLevel.MELON` reference under `game/fruitmerge/src`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitCatalogTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshotTest.kt`

- [ ] **Step 1: Write failing identity and legacy-restore tests**

```kotlin
@Test
fun `catalog exposes the market identities in stable merge order`() {
    assertEquals(
        listOf(
            FruitLevel.BLUEBERRY,
            FruitLevel.RASPBERRY,
            FruitLevel.STRAWBERRY,
            FruitLevel.LIME,
            FruitLevel.MANDARIN,
            FruitLevel.APPLE,
            FruitLevel.PEAR,
            FruitLevel.PEACH,
            FruitLevel.PINEAPPLE,
            FruitLevel.WATERMELON,
        ),
        FruitLevel.entries,
    )
}

@Test
fun `legacy fruit names restore to their market identities`() {
    assertEquals(FruitLevel.RASPBERRY, FruitLevel.fromPersistedName("CHERRY"))
    assertEquals(FruitLevel.LIME, FruitLevel.fromPersistedName("PLUM"))
    assertEquals(FruitLevel.WATERMELON, FruitLevel.fromPersistedName("MELON"))
}
```

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: compilation fails on unresolved `RASPBERRY`, `LIME`, `WATERMELON`, and `fromPersistedName`.

- [ ] **Step 3: Implement stable identities and alias parsing**

```kotlin
enum class FruitLevel(
    val radius: Float,
    val mass: Float,
    val mergeScore: Long,
    val spawnWeight: Int,
) {
    BLUEBERRY(0.035f, 1.0f, 2, 34),
    RASPBERRY(0.045f, 1.3f, 5, 27),
    STRAWBERRY(0.058f, 1.8f, 12, 20),
    LIME(0.073f, 2.5f, 26, 12),
    MANDARIN(0.089f, 3.4f, 55, 7),
    APPLE(0.108f, 4.8f, 115, 0),
    PEAR(0.128f, 6.5f, 240, 0),
    PEACH(0.151f, 8.7f, 500, 0),
    PINEAPPLE(0.178f, 11.5f, 1_050, 0),
    WATERMELON(0.210f, 15.0f, 2_200, 0),
    ;

    fun nextOrNull(): FruitLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        val spawnable: List<FruitLevel> = entries.filter { it.spawnWeight > 0 }
        val totalSpawnWeight: Int = spawnable.sumOf(FruitLevel::spawnWeight)

        fun fromPersistedName(name: String): FruitLevel? = when (name) {
            "CHERRY" -> RASPBERRY
            "PLUM" -> LIME
            "MELON" -> WATERMELON
            else -> entries.firstOrNull { it.name == name }
        }
    }
}
```

Use `FruitLevel.fromPersistedName` for body, preview, and next-preview decoding. Replace old enum references across production/tests. Rename resource keys `cherry -> raspberry`, `plum -> lime`, and `melon -> watermelon`, update every generated-resource reference, and set their visible values to Raspberry, Lime, and Watermelon.

- [ ] **Step 4: Run GREEN verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: all Fruit Merge tests pass with the renamed chain and legacy aliases.

- [ ] **Step 5: Checkpoint**

Run: `rtk git diff --check`

Expected: no whitespace errors. Do not commit.

## Task 2: Introduce the deterministic physics-profile catalog

**Files:**
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysicsProfile.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysicsProfileTest.kt`

- [ ] **Step 1: Write the failing catalog tests**

```kotlin
class FruitPhysicsProfileTest {
    @Test
    fun `every tier has one finite bounded profile`() {
        FruitLevel.entries.forEach { level ->
            val profile = fruitPhysicsProfile(level)
            assertTrue(profile.massMultiplier in 0.5f..1.5f)
            assertTrue(profile.contactRestitution in 0f..0.5f)
            assertTrue(profile.floorRetention in 0f..1f)
            assertTrue(profile.contactTangentRetention in 0f..1f)
            assertTrue(profile.wallGripSeconds in 0f..0.5f)
            assertTrue(profile.spinTransfer in 0f..0.8f)
            assertTrue(profile.balanceTorque in -0.4f..0.4f)
            assertTrue(profile.shockImpulse in 0f..0.7f)
        }
    }

    @Test
    fun `signature traits belong to exact fruit tiers`() {
        assertTrue(fruitPhysicsProfile(FruitLevel.BLUEBERRY).contactRestitution > 0.2f)
        assertTrue(fruitPhysicsProfile(FruitLevel.RASPBERRY).contactTangentRetention < 0.7f)
        assertTrue(fruitPhysicsProfile(FruitLevel.STRAWBERRY).wallGripSeconds > 0f)
        assertTrue(fruitPhysicsProfile(FruitLevel.MANDARIN).floorRetention > 0.9f)
        assertTrue(fruitPhysicsProfile(FruitLevel.APPLE).massMultiplier > 1f)
        assertTrue(fruitPhysicsProfile(FruitLevel.PEAR).balanceTorque != 0f)
        assertTrue(fruitPhysicsProfile(FruitLevel.WATERMELON).shockImpulse > 0f)
    }
}
```

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: compilation fails because `FruitPhysicsProfile` and `fruitPhysicsProfile` do not exist.

- [ ] **Step 3: Add the minimal immutable catalog**

```kotlin
data class FruitPhysicsProfile(
    val massMultiplier: Float = 1f,
    val contactRestitution: Float = 0.08f,
    val floorRetention: Float = 0.78f,
    val contactTangentRetention: Float = 0.82f,
    val wallGripSeconds: Float = 0f,
    val spinTransfer: Float = 0.12f,
    val balanceTorque: Float = 0f,
    val shockImpulse: Float = 0f,
)

private val FruitPhysicsProfiles = listOf(
    FruitPhysicsProfile(contactRestitution = 0.30f, floorRetention = 0.82f),
    FruitPhysicsProfile(contactRestitution = 0.06f, floorRetention = 0.60f, contactTangentRetention = 0.56f),
    FruitPhysicsProfile(contactRestitution = 0.08f, floorRetention = 0.68f, wallGripSeconds = 0.42f),
    FruitPhysicsProfile(contactRestitution = 0.10f, floorRetention = 0.86f, spinTransfer = 0.58f),
    FruitPhysicsProfile(contactRestitution = 0.09f, floorRetention = 0.94f, spinTransfer = 0.30f),
    FruitPhysicsProfile(massMultiplier = 1.24f, contactRestitution = 0.05f, floorRetention = 0.70f),
    FruitPhysicsProfile(massMultiplier = 1.05f, contactRestitution = 0.07f, floorRetention = 0.73f, balanceTorque = 0.28f),
    FruitPhysicsProfile(contactRestitution = 0.03f, floorRetention = 0.58f, contactTangentRetention = 0.62f),
    FruitPhysicsProfile(massMultiplier = 1.08f, contactRestitution = 0.04f, floorRetention = 0.55f, contactTangentRetention = 0.48f),
    FruitPhysicsProfile(massMultiplier = 1.35f, contactRestitution = 0.05f, floorRetention = 0.66f, shockImpulse = 0.56f),
)

fun fruitPhysicsProfile(level: FruitLevel): FruitPhysicsProfile = FruitPhysicsProfiles[level.ordinal]
```

- [ ] **Step 4: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: success; no commits.

## Task 3: Apply traits in the existing circle solver

**Files:**
- Modify: `engine/FruitBody.kt`
- Modify: `engine/FruitPhysics.kt`
- Test: `engine/FruitPhysicsTest.kt`

- [ ] **Step 1: Write one failing test per contact behavior**

Add focused tests proving: Blueberry rebounds more than Peach from identical floor velocity; Strawberry gets a positive bounded grip timer on a side-wall contact and releases after 31 fixed steps; Mandarin retains more horizontal velocity than Raspberry on the floor; Apple transfers more velocity to Blueberry than Peach does; Lime gains more angular velocity from the same off-center pair contact; Pear receives deterministic signed torque; Pineapple damps tangential slip more than Mandarin.

The Strawberry test must use this exact observable contract:

```kotlin
@Test
fun `strawberry wall grip is temporary`() {
    var body = FruitBody(
        id = 1,
        level = FruitLevel.STRAWBERRY,
        position = Vec2(0.01f, 0.30f),
        velocity = Vec2(-1f, 0.4f),
    )
    body = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()
    assertTrue(body.wallGripSecondsRemaining > 0f)

    repeat(31) { body = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single() }
    assertEquals(0f, body.wallGripSecondsRemaining)
}
```

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: compilation fails on `wallGripSecondsRemaining`; remaining trait assertions fail against global constants.

- [ ] **Step 3: Add authoritative transient trait state**

```kotlin
data class FruitBody(
    val id: Long,
    val level: FruitLevel,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val angle: Float = 0f,
    val angularVelocity: Float = 0f,
    val impact: Float = 0f,
    val hasJoinedPile: Boolean = false,
    val wallGripSecondsRemaining: Float = 0f,
    val shockAvailable: Boolean = level == FruitLevel.WATERMELON,
)
```

In `integrate`, decrement grip with `(remaining - dt).coerceAtLeast(0f)`. In side-wall constraints, start grip only for Strawberry and damp downward velocity while the timer is positive. Replace global floor/contact coefficients with the body's profile. In pair resolution:

```kotlin
val firstProfile = fruitPhysicsProfile(first.level)
val secondProfile = fruitPhysicsProfile(second.level)
val inverseMassFirst = 1f / (first.level.mass * firstProfile.massMultiplier)
val inverseMassSecond = 1f / (second.level.mass * secondProfile.massMultiplier)
val restitution = (firstProfile.contactRestitution + secondProfile.contactRestitution) * 0.5f
val tangentRetention = minOf(
    firstProfile.contactTangentRetention,
    secondProfile.contactTangentRetention,
)
```

Apply bounded tangential velocity damping, equal-and-opposite spin transfer, and Pear torque derived from contact normal and the profile constant. Clamp angular velocity with the existing maximum.

- [ ] **Step 4: Run GREEN verification and the stress suite**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: trait tests and existing finite/bounded stress tests pass.

- [ ] **Step 5: Checkpoint**

Run: `rtk git diff --check`

Expected: no whitespace errors; no commit.

## Task 4: Add one-shot Watermelon shock and persist trait state

**Files:**
- Modify: `engine/FruitPhysics.kt`
- Modify: `persistence/FruitMergeSnapshot.kt`
- Modify: `persistence/FruitMergePersistence.kt`
- Test: `engine/FruitPhysicsTest.kt`
- Test: `persistence/FruitMergeSnapshotTest.kt`
- Test: `persistence/FruitMergePersistenceTest.kt`

- [ ] **Step 1: Write failing shock and v1 migration tests**

```kotlin
@Test
fun `watermelon shock fires once and remains bounded`() {
    val source = FruitBody(
        id = 1,
        level = FruitLevel.WATERMELON,
        position = Vec2(0.5f, 0.70f),
        velocity = Vec2(0f, 1.2f),
        hasJoinedPile = true,
        shockAvailable = true,
    )
    val neighbor = FruitBody(2, FruitLevel.BLUEBERRY, Vec2(0.72f, 0.70f), hasJoinedPile = true)
    val first = FruitPhysics().step(listOf(source, neighbor), 1f / 60f).bodies
    assertFalse(first.first { it.id == 1L }.shockAvailable)
    assertTrue(first.all { it.velocity.length() <= 3.5f })

    val second = FruitPhysics().step(first, 1f / 60f).bodies
    assertFalse(second.first { it.id == 1L }.shockAvailable)
}
```

Add a storage test that writes a v1 JSON snapshot without the two new body fields and expects restore with `wallGripSecondsRemaining == 0f`, `shockAvailable == false` for settled legacy bodies, and renamed fruit aliases intact.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: shock remains armed or produces no radial impulse; snapshot version expectation fails.

- [ ] **Step 3: Implement bounded edge-triggered shock**

After contact passes, collect only armed Watermelons whose committed impact crosses the threshold. For each source, set `shockAvailable = false` and apply this bounded update to other bodies:

```kotlin
val delta = target.position - source.position
val distance = delta.length().coerceAtLeast(CONTACT_EPSILON)
val reach = source.level.radius * WATERMELON_SHOCK_REACH_MULTIPLIER
if (distance < reach) {
    val falloff = 1f - distance / reach
    val impulse = delta / distance * (profile.shockImpulse * falloff)
    target.copy(velocity = (target.velocity + impulse).clampLength(MAX_SPEED))
}
```

Limit sources to the bounded body list and never re-arm on ordinary frames.

- [ ] **Step 4: Persist trait fields with schema version 2**

Add defaulted `wallGripSecondsRemaining` and `shockAvailable` fields to `FruitBodySnapshot`, validate their bounds, round-trip them, and configure:

```kotlin
internal val FruitMergeSnapshotSpec = MiniAppSnapshotSpec(
    serializer = FruitMergeSnapshot.serializer(),
    currentVersion = 2,
    migrations = mapOf(1 to MiniAppSnapshotMigration { payload -> payload }),
)
```

The identity migration is intentional: kotlinx.serialization defaults the absent v1 fields; semantic legacy names are handled by `FruitLevel.fromPersistedName`.

- [ ] **Step 5: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: shock and migration tests pass; no commit.

## Task 5: Make shake a synchronized 2.2-second physical event

**Files:**
- Modify: `engine/FruitMergeEngine.kt`
- Modify: `ui/FruitMergeUiPolicy.kt`
- Test: `engine/FruitMergeEngineTest.kt`
- Test: `ui/FruitMergeUiPolicyTest.kt`

- [ ] **Step 1: Write failing duration, envelope, and disabled-state policy tests**

Use exact constants `SHAKE_DURATION_STEPS == 132` and `SHAKE_IMPULSE_INTERVAL_STEPS == 12`. Assert the first impulse changes velocity more than the last impulse, a duplicate shake returns `SHAKE_ACTIVE`, and `shakeVisualTransform` reaches zero at completion and uses a smaller transform under reduced motion.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: duration/interval assertions fail against `135/27` and the impulse magnitude is not decayed.

- [ ] **Step 3: Implement the shared decaying phase**

```kotlin
const val SHAKE_DURATION_STEPS: Int = 132
const val SHAKE_IMPULSE_INTERVAL_STEPS: Int = 12

private fun shakeEnvelope(stepsRemaining: Int): Float =
    (stepsRemaining.toFloat() / SHAKE_DURATION_STEPS).coerceIn(0f, 1f)
```

Multiply horizontal, upward, and angular shake additions by the envelope. Make `shakeVisualTransform` use the same normalized phase and a multi-frequency lateral/rotational curve. Keep UI button enablement tied to `shakeStepsRemaining == 0`; do not add a second UI timer.

- [ ] **Step 4: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: all tests pass; no commit.

## Task 6: Publish committed semantic events once

**Files:**
- Modify: `store/FruitMergeStore.kt`
- Modify: `store/FruitMergeStoreFactory.kt`
- Modify: `audio/FruitMergeAudioAdapter.kt` temporarily to compile exhaustive `when`
- Test: `store/FruitMergeStoreTest.kt`
- Test: `audio/FruitMergeAudioAdapterTest.kt`

- [ ] **Step 1: Write failing label tests**

Define tests for this exact contract:

```kotlin
sealed interface Label {
    data class DropReleased(val level: FruitLevel) : Label
    data class FruitLanded(val level: FruitLevel, val position: Vec2) : Label
    data class MergeResolved(val level: FruitLevel, val position: Vec2) : Label
    data class ClearApplied(val level: FruitLevel, val position: Vec2) : Label
    data object ShakeStarted : Label
    data class ShakePulse(val index: Int) : Label
    data object DangerEntered : Label
    data object ResultReached : Label
}
```

Tests must prove: a rejected second drop emits nothing; a landing is emitted only on `hasJoinedPile false -> true`; each shake impulse emits one monotonically increasing pulse; danger emits only on the zero-to-positive transition; result remains exactly once.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: old label types do not match the new assertions.

- [ ] **Step 3: Implement edge detection at the Store boundary**

Capture `beforeStep` and `afterStep` inside the existing fixed-step loop. Use body ID maps only when a relevant edge can exist. Landing detection compares the two `hasJoinedPile` values; merge detection reuses the existing newly-created-body logic and includes the created position; shake pulse detection uses the exact interval boundary; danger checks `before.dangerSeconds == 0f && after.dangerSeconds > 0f`.

For clear, capture the selected body before calling rules and publish its level/position only after the action is accepted. Publish `DropReleased(state().game.previewLevel)` only after the drop changes state.

- [ ] **Step 4: Keep the adapter exhaustive without redesigning sound yet**

Map new labels temporarily to the nearest existing SFX names so the module compiles; Task 12 replaces this mapping and declaration together.

- [ ] **Step 5: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: exact event-order tests pass; no commit.

## Task 7: Collapse Playing and Result into one Decompose component

**Files:**
- Modify: `session/FruitMergeSessionComponent.kt`
- Modify: `session/FruitMergeComponent.kt`
- Modify: `FruitMergeContent.kt`
- Modify: `FruitMergeSession.kt`
- Delete: `session/FruitMergeResultComponent.kt`
- Test: `session/FruitMergeSessionComponentTest.kt`
- Test: `FruitMergeBackgroundPolicyTest.kt`

- [ ] **Step 1: Replace navigation tests with single-component state tests**

```kotlin
@Test
fun `terminal restore and restart reuse the same game component`() = runTest {
    val component = createSession(restoredPhase = RunPhase.RESULT)
    advanceUntilIdle()
    val game = component.game
    assertIs<FruitMergeComponent.ScreenState.GameOver>(game.model.value.screen)

    game.newGame()
    advanceUntilIdle()

    assertSame(game, component.game)
    assertIs<FruitMergeComponent.ScreenState.Playing>(game.model.value.screen)
}
```

Also assert `frameMode` is `ContentOnly` in GameOver and `Standard` in Playing, and `handleBack()` returns false in GameOver unless clear targeting is active.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: `component.game` and sealed screen state are missing.

- [ ] **Step 3: Introduce the sealed screen model**

```kotlin
sealed interface ScreenState {
    val game: FruitMergeState

    data class Playing(override val game: FruitMergeState) : ScreenState
    data class GameOver(
        override val game: FruitMergeState,
        val largestFruit: FruitLevel,
    ) : ScreenState
}
```

Map `RunPhase` to this state in the component model update. `largestFruit` is `game.bodies.maxByOrNull { it.level.ordinal }?.level ?: game.previewLevel`.

- [ ] **Step 4: Remove the internal stack**

Expose `val game: FruitMergeComponent` on `FruitMergeSessionComponent`. Construct it once with `childContext(key = "FruitMergeGame")`. Derive `frameMode` from `game.model.map { model -> ... }`. Keep the retained Store and its existing single label collector for audio. Task 10 extends that same collector with the UI presentation bridge; no second Store-label collector is introduced.

`FruitMergeContent` renders only `FruitMergeScreen(component.game, ...)`. Delete the obsolete Result component and navigation configuration.

- [ ] **Step 5: Use one full-frame background**

`FruitMergeSession.Background` always renders the same market background role. It must not switch colors from `frameMode`; status/navigation-bar regions therefore remain identical across Playing/GameOver.

- [ ] **Step 6: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: component/background tests pass; no commit.

## Task 8: Build the state-holder split and adaptive market composition

**Files:**
- Create: `ui/FruitMergeMarketScene.kt`
- Modify: `ui/FruitMergeScreen.kt`
- Modify: `FruitMergeSession.kt`
- Modify: `commonMain/composeResources/values/strings.xml`
- Test: `ui/FruitMergeScreenTest.kt`
- Test: `core/uikit/src/commonTest/kotlin/ge/yet/game/uikit/components/score/CompactScoreTest.kt`

- [ ] **Step 1: Write failing UI tests for the world-owned layout**

Add test tags `MarketCrate`, `NextBasket`, `FruitSlicer`, `CrateHandle`, `PriceTag`, and `EvolutionShelf`. Assert all are displayed at `390x760`; at `1000x620`, the crate remains left of the supporting tool rail; the handle is disabled throughout active shake; exhausted tools still delegate exact paid tokens; score text is compact while semantics retain exact values.

Retain and strengthen the existing full-viewport test: tap above the measured crate must move preview and drop exactly once, while clicking the slicer or handle must produce zero drop calls.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: market tags and the plain UI overload are missing.

- [ ] **Step 3: Split state-holder wiring from layout**

Keep this outer function responsible for Decompose subscription, frame clock, event collection, and ad callbacks:

```kotlin
@Composable
internal fun FruitMergeScreen(
    component: FruitMergeComponent,
    requestClearAd: (PaidActionToken) -> Unit,
    requestShakeAd: (PaidActionToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    FruitMergeScreen(
        state = model,
        onMovePreview = component::movePreview,
        onDrop = component::drop,
        onClear = { component.requestClearGate()?.let(requestClearAd) },
        onClearTarget = component::selectClearTarget,
        onCancelClear = component::cancelClear,
        onShake = { component.requestShakeGate()?.let(requestShakeAd) },
        onNewGame = component::newGame,
        onSkipTutorial = component::skipTutorial,
        modifier = modifier,
    )
}
```

The plain overload accepts immutable state and explicit callbacks only.

- [ ] **Step 4: Compose the market scene**

Use the existing `AdaptiveGameScaffold`; compact height prioritizes the crate, wider constraints cap the crate and place tools in the supporting pane. Draw the warm stall background, fabric canopy, wooden crate shell, basket, shelf, and tool mounts in game-owned composables. Keep all hit targets at least `48.dp`.

Move score/best out of the viewport header. `FruitMergeSession.TopBarContent` subscribes to the model and renders a game-owned paper `MarketPriceTag` using UIKit's tested `compactScore` function.

- [ ] **Step 5: Preserve host banner ownership**

Do not change `RootChildContent`, `MiniAppFrame`, or monetization policy. Keep the game viewport ending above the existing stable banner reservation. Existing `MiniAppFrameLayoutPolicyTest` remains the acceptance proof.

- [ ] **Step 6: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests :composeApp:allTests && rtk git diff --check`

Expected: compact/wide/input/score/banner tests pass; no commit.

## Task 9: Enforce the art bible and event-driven faces

**Files:**
- Modify: `ui/FruitVisualSpec.kt`
- Modify: `ui/FruitMergeVisualModel.kt`
- Modify: `ui/FruitMergeBoard.kt`
- Test: `ui/FruitVisualSpecTest.kt`
- Test: `ui/FruitMergeVisualModelTest.kt`

- [ ] **Step 1: Write failing art-contract tests**

Assert all ten specs share one outline color and light direction, expose exactly base/shadow/highlight colors, have unique silhouette/detail keys, and the first four silhouettes are `BERRY`, `CLUSTER`, `HEART`, `CITRUS`. Add a pure face-state test:

```kotlin
assertEquals(FruitExpression.RESTING, fruitExpression(0f, 0f, DangerVisual(0f, false), false))
assertEquals(FruitExpression.FALLING, fruitExpression(0.5f, 0f, DangerVisual(0f, false), false))
assertEquals(FruitExpression.IMPACT, fruitExpression(0f, 0.8f, DangerVisual(0f, false), false))
assertEquals(FruitExpression.CRYING, fruitExpression(0f, 0f, DangerVisual(1f, true), false))
assertEquals(FruitExpression.MERGING, fruitExpression(0f, 0f, DangerVisual(0f, false), true))
```

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: shared outline/shadow and expression API are missing.

- [ ] **Step 3: Replace face-first specs with silhouette-first specs**

Use one `MarketFruitOutline = Color(0xFF55372D)`, one upper-left highlight convention, and add explicit shadow color. Remove per-fruit blush as a required field. Resting rendering uses restrained eyes/mark only; mouth and cheeks appear only for Impact/Merging/Crying where appropriate.

Retain the approved silhouettes: flattened crowned Blueberry, irregular Raspberry drupelets, angular heart Strawberry, pointed oval Lime. Bring the remaining six under the same outline thickness, two-tone shading, and highlight geometry.

- [ ] **Step 4: Make render behavior pure and bounded**

Derive expression from velocity, impact, danger, and active presentation event. Continue drawing all bodies in the existing Canvas loop; do not add per-body composables or `Animatable`s. Use body `angle` for Pear/Lime character and profile-based squash multipliers without changing collider geometry.

- [ ] **Step 5: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: art-contract tests and existing danger regression pass; no commit.

## Task 10: Add basket transfer, physical tools, guide, and bounded presentation events

**Files:**
- Create: `ui/FruitMergePresentation.kt`
- Modify: `session/FruitMergeComponent.kt`
- Modify: `ui/FruitMergeScreen.kt`
- Modify: `ui/FruitMergeBoard.kt`
- Modify: `ui/FruitMergeMarketScene.kt`
- Test: `session/FruitMergeComponentTest.kt`
- Test: `ui/FruitMergeScreenTest.kt`
- Test: `ui/FruitMergeUiPolicyTest.kt`

- [ ] **Step 1: Write failing event and motion-policy tests**

Expose `Flow<FruitMergeComponent.PresentationEvent>` with bounded single-consumer semantics. Define the sealed event contract in `FruitMergeComponent.kt` with `Landing`, `Merge`, `Clear`, and `ShakePulse` payloads carrying only the committed label data. Verify Store labels map to those events only while visible. Add pure tests for a `120–150 ms` merge squeeze curve, a `340 ms` basket-to-preview arc, guide alpha reaching zero during cooldown, and shake handle rotation following the shared shake phase.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: presentation event/curve APIs are unresolved.

- [ ] **Step 3: Add the bounded event bridge**

Use `Channel<FruitMergeComponent.PresentationEvent>(capacity = Channel.BUFFERED)` owned by `DefaultFruitMergeComponent` and expose it as `Flow<PresentationEvent>` through `receiveAsFlow()`. Keep the concrete component in `DefaultFruitMergeSessionComponent` so its single Store-label collector can call internal `onStoreLabel(label)`. That method maps labels and uses `trySend` only while the component is alive and visible. Do not retry a full channel; visual loss is preferable to unbounded memory.

- [ ] **Step 4: Render synchronized overlays without per-body animation objects**

Maintain one small UI-local list of active events keyed by monotonic ID and expiry. Draw merge source silhouettes compressing toward the committed position, the slicer arc at the removed position, and small bounded landing accents. Prune expired events in the existing frame clock. Use draw-phase reads for frame-rate progress.

The next basket uses one `Animatable` because it is one global interruptible transfer, not one per body. Keep its source/target coordinates in root space and convert once to viewport space. The basket empties during transfer; the board preview hides until completion.

- [ ] **Step 5: Replace generic tools and guide**

Render a fruit-slicer icon/prop and a crate-handle prop with badges `5..1` or `AD`; preserve exact accessibility descriptions. Replace the generic line/dots with short hand-drawn marks and wooden side arrows. Disabled cooldown lowers guide alpha; active shake locks handle interaction.

- [ ] **Step 6: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: event/motion/input tests pass; no commit.

## Task 11: Replace the separate result screen with an in-place GameOver state

**Files:**
- Create: `ui/FruitMergeResultOverlay.kt`
- Modify: `ui/FruitMergeScreen.kt`
- Delete: `ui/FruitMergeResultScreen.kt`
- Delete or replace: `ui/FruitMergeResultScreenTest.kt`
- Test: `ui/FruitMergeScreenTest.kt`
- Test: `FruitMergeBackgroundPolicyTest.kt`

- [ ] **Step 1: Write failing in-place result tests**

Render a `FruitMergeComponent.Model` whose screen is `GameOver`. Assert the same `MarketCrate` node remains mounted, `GameOverPriceTag`, exact score/best semantics, largest fruit, and New Game control are displayed, Playing-only drop/tool controls are disabled, and pressing New Game invokes the existing callback once.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: old result screen renders separately and the shared crate/tag assertions fail.

- [ ] **Step 3: Implement one coordinated state transition**

Use `rememberTransition(targetState = screenKind, label = "fruit-merge-screen")` for crate dim/settle and result-tag offset/alpha. Keep `MarketScene` outside the state branch. Use `AnimatedVisibility` only for controls that must leave composition. Respect reduced motion with zero-duration or static terminal values.

Delete the obsolete result screen/component files only after their score, best, largest-fruit, new-game, and accessibility coverage exists in the unified screen tests.

- [ ] **Step 4: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: one-screen GameOver tests pass and no obsolete Result references remain.

## Task 12: Rebuild the tutorial as three wordless market demonstrations

**Files:**
- Modify: `session/FruitMergeComponent.kt`
- Modify: `session/FruitMergeSessionComponent.kt`
- Modify: `ui/FruitMergeTutorial.kt`
- Test: `session/FruitMergeSessionComponentTest.kt`
- Test: `ui/FruitMergeTutorialTest.kt`
- Test: `ui/FruitMergeScreenTest.kt`

- [ ] **Step 1: Write failing tutorial-state tests**

Replace `Tap/Drag` with `Gesture/Merge/Traits`. Assert first accepted drop advances Gesture to Merge, first committed `MergeResolved` label advances Merge to Traits, the Traits demonstration calls `completeTutorial()` after its finite animation, and Skip persists completion from any step. Preserve pass-through game input while the skip control remains actionable.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: new steps and completion callback are missing.

- [ ] **Step 3: Implement the component tutorial state machine**

Keep tutorial state in the component model, not domain game state. Accepted drop advances Gesture; the session's label bridge advances Merge only on a committed merge; `completeTutorial` clears Traits and persists `tutorial_seen`. Guard all async persistence with the existing `alive` lifecycle flag.

- [ ] **Step 4: Implement wordless overlays**

Gesture step spotlights basket/guide/crate and animates tap then drag. Merge step draws two equal fruits compressing into the next tier. Traits step demonstrates Strawberry wall grip, Apple push, and crate handle in three short staged poses. Use pictograms, three progress dots, focus lighting, and a compact skip icon; do not render large text cards. Reduced motion uses static poses and shorter timing.

- [ ] **Step 5: Run GREEN verification and checkpoint**

Run: `rtk ./gradlew :game:fruitmerge:allTests && rtk git diff --check`

Expected: tutorial state, Compose clock, accessibility, and pass-through tests pass; no commit.

## Task 13: Re-author the crate groove and tactile SFX

**Files:**
- Modify: `audio/FruitMergeAudio.kt`
- Modify: `audio/FruitMergeAudioAdapter.kt`
- Test: `audio/FruitMergeAudioTest.kt`
- Test: `audio/FruitMergeAudioAdapterTest.kt`

- [ ] **Step 1: Write failing exact-contract and acoustic tests**

Require typed SFX names:

```kotlin
release
landing_small
landing_medium
landing_heavy
merge_low
merge_mid
merge_high
clear_slice
shake_left
shake_right
danger_enter
game_over
```

Assert every SFX is deterministic, finite, audible, and below clipping. Render rapid overlaps of landing + merge + shake and assert peak headroom. Adapter tests must map level mass groups, alternating shake pulse parity, one danger entry, clear, merge tiers, and game over exactly once without retrying rejected commands.

- [ ] **Step 2: Run RED verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: new names and mappings are absent.

- [ ] **Step 3: Re-author the immutable program**

Keep an original `82–86 BPM` program with three bounded music layers: low wooden crate knocks, stereo fruit rolls, and sparse GlassBell market melody. Use explicit stable seeds. Add one `intensity` control only if the current public preset/control API can map it without engine changes; otherwise keep the deterministic arrangement static and document that no engine modification was needed.

Release uses a quiet filtered leaf transient. Landing groups use progressively lower sine/triangle bodies plus restrained brown/pink noise. Clear uses a fast band-passed blade transient, soft low pluck, and short upward tonal finish. Shake uses two short alternating variants. Danger is one quiet squeak; GameOver is a crate settle with two descending soft tones.

- [ ] **Step 4: Map committed labels only**

The adapter receives session-bound `MiniAppAudio`, starts music once, maps each semantic label to one typed SFX, updates intensity only on meaningful danger transitions, consumes `Rejected` results without loops, and leaves visibility/settings/teardown to the host.

- [ ] **Step 5: Run GREEN verification and platform compilation**

Run: `rtk ./gradlew :game:fruitmerge:allTests :game:fruitmerge:compileAndroidMain :game:fruitmerge:compileKotlinIosSimulatorArm64`

Expected: deterministic render and adapter tests pass; both targets compile.

- [ ] **Step 6: Checkpoint**

Run: `rtk git diff --check`

Expected: no whitespace errors; no commit.

## Task 14: Performance, accessibility, and full acceptance

**Files:**
- Modify if failing evidence requires it: focused files from Tasks 1–13 only
- Test: `engine/FruitMergeStressTest.kt`
- Test: `ui/FruitMergeScreenTest.kt`
- Test: `ui/FruitMergeTutorialTest.kt`
- Test: `composeApp/src/commonTest/kotlin/ge/yet/game/screen/miniapp/MiniAppFrameLayoutPolicyTest.kt`
- Update: `docs/superpowers/specs/2026-08-31-fruit-merge-market-identity-design.md` only if implementation reveals an approved design correction

- [ ] **Step 1: Extend stress assertions before optimization**

Add a deterministic one-minute simulation with all trait types and repeated shake. Assert body/candidate-pair bounds, finite position/velocity/angle/grip values, and identical final state for identical seed/input. Add UI assertions for exact content descriptions, `48.dp` tool targets, reduced-motion terminal states, and no drop leakage from controls.

- [ ] **Step 2: Run RED or evidence verification**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: any newly exposed performance/accessibility regression fails specifically; if all assertions already pass, record that no optimization change is justified.

- [ ] **Step 3: Fix only measured failures**

Keep frame-rate progress reads in Canvas/draw or block-form `graphicsLayer`; keep measurement callbacks from back-writing state read in composition; ensure transient event lists and particles are bounded; avoid new per-body coroutines, composables, or animations. Do not weaken physics limits or tests to obtain green output.

- [ ] **Step 4: Re-index and inspect structural impact**

Run codebase-memory fast indexing for `/Users/yet/development/Multiplatform/BlockBlast`, then search for stale `FruitLevel.CHERRY|PLUM|MELON`, `FruitMergeResultComponent`, `FruitMergeResultScreen`, and internal Fruit Merge `ChildStack` references. Expected: none remain outside deliberate legacy string aliases/tests.

- [ ] **Step 5: Run the complete acceptance matrix**

```bash
rtk ./gradlew \
  :game:fruitmerge:verifyMiniApp \
  :miniapp:bundle:verifyMiniAppBundle \
  :composeApp:compileAndroidMain \
  :androidApp:assembleDebug \
  :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL`. The existing iOS bundle-ID warning may remain; no new warnings/errors are accepted.

- [ ] **Step 6: Final repository checks**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: diff check passes; status contains only intended Fruit Merge/UIKit/docs work plus preserved unrelated user changes. Do not commit.

## Final manual QA matrix

- Compact portrait: `390x760`, banner eligible and ineligible.
- Compact-height landscape: controls reachable, crate not clipped.
- Medium/expanded: live resize preserves run/tutorial/tool state.
- Fresh tutorial and previously-seen tutorial.
- Five free slices, rewarded sixth slice, three free shakes, rewarded fourth shake.
- Full 2.2-second shake with disabled handle and reduced motion.
- Each fruit trait visible and deterministic.
- Airborne fruit never glows red; settled near-line fruit glows and cries.
- GameOver stays on the same market scene; status/navigation regions retain the same background.
- Music off/SFX on, music on/SFX off, obscured session, and resumed session.
