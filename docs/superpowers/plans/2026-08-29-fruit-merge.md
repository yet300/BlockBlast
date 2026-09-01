# Fruit Merge MiniApp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an original, adaptive, low-allocation Fruit Merge MiniApp with deterministic circle physics, expressive Canvas-rendered fruit, five free clears, three free shakes, and stale-safe advertisement-gated extra actions.

**Architecture:** Generate `:game:fruitmerge` with the repository MiniApp scaffold, then replace the placeholder reducer with focused domain, physics, persistence, Store/session, and UI units. A fixed-step common Kotlin engine owns all rules; MVIKotlin/Decompose own state and lifecycle; Compose renders a single Canvas inside `AdaptiveGameScaffold`; the game consumes only the shared interstitial capability.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform/Material 3, `AdaptiveGameScaffold`, Decompose, MVIKotlin, Metro, kotlinx serialization, `MiniAppStorage`, `MiniAppInterstitialCapability`, kotlin-test, Compose UI tests.

**Branch:** `codex/fruit-merge` (the user explicitly requested a branch without a worktree).

**Baseline evidence:** `./gradlew :miniapp:compose:allTests :core:uikit:allTests` passed on 2026-08-29 before feature implementation.

---

## Locked File Structure

The scaffold initially creates `Fruitmerge*` files because the stable ID segment
is `fruitmerge`. Rename the game-owned types to idiomatic `FruitMerge*` while
preserving the required Metro factory name `createGameFruitmergeSessionGraph`,
which is derived from the complete MiniApp ID.

### Shared contract

- Modify `miniapp/compose/src/commonMain/kotlin/ge/yet/game/miniapp/compose/MiniAppInterstitialCapability.kt` — add clear/shake placements.
- Modify `miniapp/compose/src/commonTest/kotlin/ge/yet/game/miniapp/compose/MiniAppContractsTest.kt` — contract coverage for those placements.

### Module and submission

- Create `game/fruitmerge/build.gradle.kts` — inward UI/MVI dependencies.
- Create `game/fruitmerge/AGENTS.md` — generated local policy.
- Create `game/fruitmerge/submission.json` — schema-valid submission record.
- Create `game/fruitmerge/PROVENANCE.md` — rights, assets, references, and AI record.
- Create `game/fruitmerge/src/commonMain/composeResources/values/strings.xml` — all user-visible strings.
- Create `game/fruitmerge/src/commonMain/composeResources/drawable/miniapp_icon.xml` — original vector catalog icon.

### Domain and physics

- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitLevel.kt` — progression, radii, masses, score, spawn weights.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitBody.kt` — immutable body and vector values.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/RandomState.kt` — deterministic PRNG.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeState.kt` — authoritative immutable run state and actions.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/SpatialGrid.kt` — bounded broad phase and diagnostics.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysics.kt` — fixed-step integration, walls, contacts, merge arbitration.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngine.kt` — drop, score, danger, clear, shake, game over.

### Persistence and runtime

- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshot.kt` — serializable snapshot and strict validation.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergePersistence.kt` — `MiniAppStorage` repository.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStore.kt` — Store intents/state/labels.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreFactory.kt` — MVIKotlin executor/reducer and bounded frame accumulator.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeComponent.kt` — Decompose model, action tokens, lifecycle and visibility.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSession.kt` — session rendering and interstitial gates.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergeSessionGraph.kt` — Metro session bindings.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitMergePlugin.kt` — manifest and retained session.

### UI

- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeContent.kt` — state-to-screen entry point and frame clock.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt` — adaptive primary/supporting panes.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitBoard.kt` — gestures, projection, one Canvas, semantic overlay.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitPainter.kt` — original gradients, silhouettes, faces, effects.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMotion.kt` — expression/motion policy and reduced-motion mapping.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeControls.kt` — score, next, progression, clear/shake actions.
- Create `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeSemantics.kt` — localized board/fruit descriptions.

### Tests

- Create focused tests mirroring each unit under `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/...`.
- Keep generated/renamed `FruitMergePluginContractTest.kt` and expand it rather than creating a second contract suite.

---

### Task 1: Generate the reviewable MiniApp and record provenance

**Files:**
- Create: `game/fruitmerge/**` via the repository scaffold
- Modify: `game/fruitmerge/build.gradle.kts`
- Create: `game/fruitmerge/submission.json`
- Create: `game/fruitmerge/PROVENANCE.md`

- [ ] **Step 1: Confirm the target is unused**

Run:

```bash
test ! -e game/fruitmerge
./gradlew projects | rg ':game:fruitmerge' && exit 1 || true
```

Expected: `game/fruitmerge` does not exist and no project with that path is listed.

- [ ] **Step 2: Generate the required game-profile scaffold**

Run:

```bash
./gradlew createMiniApp \
  -PminiAppId=game.fruitmerge \
  -PminiAppName="Fruit Merge" \
  -PminiAppProfile=game
```

Expected: Gradle reports creation of `:game:fruitmerge`; `settings.gradle.kts`
discovers it on the next invocation, but its `miniApps` allowlist block is unchanged.

- [ ] **Step 3: Add only approved inward dependencies**

Replace `game/fruitmerge/build.gradle.kts` with:

```kotlin
plugins { alias(libs.plugins.miniapp) }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.uikit)
            implementation(libs.bundles.mvi)
        }

        commonTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
```

- [ ] **Step 4: Add the schema-valid submission and provenance**

Create `game/fruitmerge/submission.json` with the exact product contract:

```json
{
  "schemaVersion": 1,
  "id": "game.fruitmerge",
  "displayName": "Fruit Merge",
  "category": "game",
  "authors": ["Logica contributors", "OpenAI Codex"],
  "summary": "Drop expressive fruit, combine matching pairs, and manage the pile without crossing the danger line.",
  "rules": "Drop fruit into a bounded container. Equal fruit merge into the next original level and award score. Two maximum-level melons disappear for a bonus. A fruit continuously above the danger line for 1.5 seconds ends the run.",
  "controls": [
    {"input": "Drag or tap horizontally", "action": "Position the preview fruit"},
    {"input": "Release", "action": "Drop the preview fruit"},
    {"input": "Clear, then fruit", "action": "Remove one fruit"},
    {"input": "Shake", "action": "Apply bounded impulses to the settled pile"}
  ],
  "sessionFlow": ["Start or restore a run", "Drop and merge fruit", "Use bounded actions", "Show result after sustained overflow", "Start a new run"],
  "platforms": {"android": true, "ios": true, "orientations": ["portrait", "landscape"], "deviceClasses": ["phone", "tablet"]},
  "accessibility": {
    "announcements": "Announce clear mode, merge chains, exhausted free actions, and game over.",
    "focus": "Preserve action focus across adaptive rearrangement and move focus to New game on result.",
    "labels": "Label score, next fruit, fill level, actions, counts, and selectable fruit regions.",
    "reducedMotion": "Disable squash, camera displacement, glow expansion, and particles while retaining gameplay physics."
  },
  "visualStyle": {"description": "Original warm orchard palette with matte vector fruit, highlights, leaves, blush, and expressive faces.", "references": []},
  "audioStyle": {"description": "Silent initial version with visual feedback only.", "references": []},
  "storage": {"values": [{"name": "best_score", "type": "long", "default": 0}, {"name": "game_snapshot", "type": "snapshot", "default": null}], "resetBehavior": "Both values are removed by the host-owned MiniApp namespace reset."},
  "capabilities": ["interstitial"],
  "rights": {"classification": "new_expression", "evidence": ["All code and Canvas art are original to this repository; only genre-level rules were referenced."]},
  "provenance": {
    "code": ["Original Kotlin/Compose implementation in :game:fruitmerge"],
    "art": ["Original runtime Canvas primitives and original vector icon"],
    "audio": [],
    "fonts": [],
    "references": ["https://suikagame.jp/", "https://apps.apple.com/us/app/suika-game-aladdin-x/id6469114836", "https://www.nintendo.com/au/games/nintendo-switch/suika-game/"],
    "licenses": [],
    "ai": {"used": true, "tools": ["OpenAI Codex"], "promptArchive": "../../docs/superpowers/specs/2026-08-29-fruit-merge-design.md"}
  },
  "acceptanceScenarios": [
    {"name": "Merge", "given": "Two equal fruit touch", "when": "The fixed step resolves", "then": "They become one next-level fruit and score increases once"},
    {"name": "Overflow grace", "given": "A fruit briefly crosses the danger line", "when": "It falls below before 1.5 seconds", "then": "The run continues"},
    {"name": "Clear gate", "given": "Five free clears are spent", "when": "Clear is requested again", "then": "The ad gate completes before one target opportunity"},
    {"name": "Shake gate", "given": "Three free shakes are spent", "when": "Shake is requested again", "then": "The ad gate completes before one bounded shake"}
  ],
  "knownLimitations": ["Circle collision geometry", "No online leaderboard or multiplayer", "No game-owned audio"]
}
```

Create `PROVENANCE.md` with the same classification, the three URLs, an explicit
statement that no source/art/audio/font was copied, the prompt archive path, and
`NOT ALLOWLISTED` status.

- [ ] **Step 5: Verify generated contracts and commit the bootstrap**

Run:

```bash
./gradlew :game:fruitmerge:allTests :game:fruitmerge:validateMiniAppDependencies
git diff --check
```

Expected: generated tests pass, dependency validation passes, and no whitespace errors.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: scaffold fruit merge miniapp"
```

---

### Task 2: Add semantically correct interstitial placements

**Files:**
- Modify: `miniapp/compose/src/commonTest/kotlin/ge/yet/game/miniapp/compose/MiniAppContractsTest.kt`
- Modify: `miniapp/compose/src/commonMain/kotlin/ge/yet/game/miniapp/compose/MiniAppInterstitialCapability.kt`

- [ ] **Step 1: Write the failing contract test**

Add:

```kotlin
@Test
fun `fruit merge action placements remain explicit host contracts`() {
    assertEquals(
        setOf(
            MiniAppInterstitialPlacement.CONTINUE_AFTER_GAME_OVER,
            MiniAppInterstitialPlacement.FRUIT_MERGE_CLEAR,
            MiniAppInterstitialPlacement.FRUIT_MERGE_SHAKE,
        ),
        MiniAppInterstitialPlacement.entries.toSet(),
    )
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :miniapp:compose:allTests
```

Expected: compilation fails because `FRUIT_MERGE_CLEAR` and `FRUIT_MERGE_SHAKE` do not exist.

- [ ] **Step 3: Add the minimal shared enum entries**

Change the enum to:

```kotlin
enum class MiniAppInterstitialPlacement {
    CONTINUE_AFTER_GAME_OVER,
    FRUIT_MERGE_CLEAR,
    FRUIT_MERGE_SHAKE,
}
```

No host adapter switch is needed: the current adapter applies the same policy to
every placement and passes the placement only as an explicit semantic request.

- [ ] **Step 4: Run GREEN and commit**

Run:

```bash
./gradlew :miniapp:compose:allTests :composeApp:compileAndroidMain
```

Expected: both tasks pass.

Commit:

```bash
git add miniapp/compose
git commit -m "feat: add fruit merge interstitial placements"
```

---

### Task 3: Define deterministic fruit progression and immutable state

**Files:**
- Delete generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeGameState.kt`
- Delete generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeGameEngine.kt`
- Delete generated test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/FruitmergeGameEngineTest.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitLevel.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitBody.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/RandomState.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeState.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitCatalogTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/RandomStateTest.kt`

- [ ] **Step 1: Write failing catalog and PRNG tests**

```kotlin
class FruitCatalogTest {
    @Test
    fun `catalog has ten increasing levels and only first five spawn`() {
        assertEquals(10, FruitLevel.entries.size)
        assertTrue(FruitLevel.entries.zipWithNext().all { (a, b) ->
            b.radius > a.radius && b.mass > a.mass && b.mergeScore > a.mergeScore
        })
        assertEquals(
            setOf(FruitLevel.BLUEBERRY, FruitLevel.CHERRY, FruitLevel.STRAWBERRY, FruitLevel.PLUM, FruitLevel.MANDARIN),
            FruitLevel.spawnable.toSet(),
        )
    }
}

class RandomStateTest {
    @Test
    fun `same seed produces the same bounded sequence`() {
        fun sequence(seed: Long): List<Int> {
            var state = RandomState(seed)
            return List(32) {
                val next = state.nextInt()
                state = next.state
                next.value
            }
        }
        val first = sequence(7)
        val second = sequence(7)
        assertEquals(first, second)
        assertTrue(first.all { it in 0 until Int.MAX_VALUE })
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :game:fruitmerge:allTests
```

Expected: unresolved `FruitLevel` and `RandomState`.

- [ ] **Step 3: Implement the domain values**

Use these public shapes and constants:

```kotlin
enum class FruitLevel(
    val radius: Float,
    val mass: Float,
    val mergeScore: Long,
    val spawnWeight: Int,
) {
    BLUEBERRY(0.035f, 1.0f, 2, 34),
    CHERRY(0.045f, 1.3f, 5, 27),
    STRAWBERRY(0.058f, 1.8f, 12, 20),
    PLUM(0.073f, 2.5f, 26, 12),
    MANDARIN(0.089f, 3.4f, 55, 7),
    APPLE(0.108f, 4.8f, 115, 0),
    PEAR(0.128f, 6.5f, 240, 0),
    PEACH(0.151f, 8.7f, 500, 0),
    PINEAPPLE(0.178f, 11.5f, 1_050, 0),
    MELON(0.210f, 15.0f, 2_200, 0);

    fun nextOrNull(): FruitLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        val spawnable = entries.filter { it.spawnWeight > 0 }
        val totalSpawnWeight = spawnable.sumOf(FruitLevel::spawnWeight)
    }
}

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Float) = Vec2(x * scale, y * scale)
    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun lengthSquared(): Float = dot(this)
    fun length(): Float = kotlin.math.sqrt(lengthSquared())
    fun isFinite(): Boolean = x.isFinite() && y.isFinite()
}

data class FruitBody(
    val id: Long,
    val level: FruitLevel,
    val position: Vec2,
    val velocity: Vec2 = Vec2(0f, 0f),
    val angle: Float = 0f,
    val angularVelocity: Float = 0f,
    val impact: Float = 0f,
)

data class RandomValue(val state: RandomState, val value: Int)

@JvmInline
value class RandomState(val bits: Long) {
    fun nextInt(): RandomValue {
        var x = if (bits == 0L) 0x9E3779B97F4A7C15UL.toLong() else bits
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        return RandomValue(RandomState(x), (x ushr 33).toInt() and Int.MAX_VALUE)
    }
}

enum class RunPhase { PLAYING, RESULT }
enum class TargetingMode { NONE, CLEAR }

data class FruitMergeState(
    val bodies: List<FruitBody> = emptyList(),
    val previewLevel: FruitLevel = FruitLevel.BLUEBERRY,
    val previewX: Float = 0.5f,
    val random: RandomState = RandomState(1),
    val nextBodyId: Long = 1,
    val score: Long = 0,
    val bestScore: Long = 0,
    val freeClears: Int = 5,
    val freeShakes: Int = 3,
    val dangerSeconds: Float = 0f,
    val graceSeconds: Float = 0f,
    val runOrdinal: Long = 1,
    val phase: RunPhase = RunPhase.PLAYING,
    val targetingMode: TargetingMode = TargetingMode.NONE,
)
```

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :game:fruitmerge:allTests`

Expected: catalog and PRNG tests pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: define fruit merge domain model"
```

---

### Task 4: Implement bounded fixed-step physics and spatial broad phase

**Files:**
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/SpatialGrid.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysics.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/SpatialGridTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysicsTest.kt`

- [ ] **Step 1: Write failing physics tests**

```kotlin
class FruitPhysicsTest {
    @Test
    fun `fixed step applies gravity and keeps bodies inside walls and floor`() {
        val body = FruitBody(1, FruitLevel.CHERRY, Vec2(0.01f, 0.90f), Vec2(-2f, 3f))
        val result = FruitPhysics().step(listOf(body), 1f / 60f)
        val actual = result.bodies.single()
        assertTrue(actual.position.x >= actual.level.radius)
        assertTrue(actual.position.y <= FruitPhysics.FLOOR_Y - actual.level.radius)
    }

    @Test
    fun `overlapping circles separate without non finite values`() {
        val bodies = listOf(
            FruitBody(1, FruitLevel.APPLE, Vec2(0.50f, 0.50f)),
            FruitBody(2, FruitLevel.APPLE, Vec2(0.51f, 0.50f)),
        )
        val result = FruitPhysics().step(bodies, 1f / 60f)
        val distance = (result.bodies[1].position - result.bodies[0].position).length()
        assertTrue(distance >= FruitLevel.APPLE.radius * 1.95f)
        assertTrue(result.bodies.all { it.position.x.isFinite() && it.position.y.isFinite() })
    }
}

class SpatialGridTest {
    @Test
    fun `maximum board stays below all pairs candidate count`() {
        val bodies = List(80) { index ->
            val column = index % 10
            val row = index / 10
            FruitBody(
                id = index.toLong() + 1,
                level = FruitLevel.BLUEBERRY,
                position = Vec2(0.05f + column * 0.095f, 0.20f + row * 0.095f),
            )
        }
        val pairs = SpatialGrid().candidatePairs(bodies)
        assertTrue(pairs.size < bodies.size * 12)
        assertEquals(pairs.distinct(), pairs)
    }
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:fruitmerge:allTests`

Expected: unresolved physics/grid APIs.

- [ ] **Step 3: Implement the bounded algorithms**

Use fixed constants and result diagnostics:

```kotlin
internal const val MAX_BODIES = 80
internal const val MAX_CONTACT_PASSES = 4
internal const val MAX_CANDIDATE_PAIRS = 960

data class BodyPair(val firstIndex: Int, val secondIndex: Int)
data class PhysicsResult(
    val bodies: List<FruitBody>,
    val contacts: List<BodyPair>,
    val candidatePairCount: Int,
)

class FruitPhysics(private val grid: SpatialGrid = SpatialGrid()) {
    fun step(input: List<FruitBody>, dt: Float): PhysicsResult

    companion object {
        const val FLOOR_Y = 1f
        const val MAX_STEP_SECONDS = 1f / 30f
    }
}
```

`SpatialGrid.candidatePairs` must clear and reuse buckets, map each circle's AABB
to 0.12-world-unit cells, normalize each pair to `(minIndex, maxIndex)`, dedupe
through a reusable `Long` key set, sort by body IDs for determinism, and stop at
`MAX_CANDIDATE_PAIRS`.

`FruitPhysics.step` must:

```kotlin
fun step(input: List<FruitBody>, dt: Float): PhysicsResult {
    require(dt in 0f..MAX_STEP_SECONDS && dt.isFinite())
    val bodies = input.take(MAX_BODIES).map(::integrate).toMutableList()
    repeat(MAX_CONTACT_PASSES) {
        for (pair in grid.candidatePairs(bodies)) resolveCirclePair(bodies, pair)
        bodies.indices.forEach { index -> bodies[index] = constrainToContainer(bodies[index]) }
    }
    return PhysicsResult(
        bodies = bodies.map { it.copy(impact = it.impact * 0.88f) },
        contacts = contacts.take(MAX_CANDIDATE_PAIRS),
        candidatePairCount = candidates.size,
    )
}
```

Integration uses semi-implicit Euler, gravity `1.65f`, air damping `0.998f`, wall
restitution `0.18f`, contact restitution `0.08f`, floor friction `0.78f`, and
speed clamps. Zero-distance contacts derive a deterministic normal from body IDs.
Contact resolution applies inverse-mass positional correction and normal impulse;
no allocation or coroutine launch occurs inside the pair loop.

- [ ] **Step 4: Run GREEN, stress the unit, and commit**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*SpatialGridTest' --tests '*FruitPhysicsTest'
```

Expected: all focused physics tests pass.

Commit:

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine
git commit -m "feat: add bounded fruit physics"
```

---

### Task 5: Implement drops, merges, danger timing, clear, and shake

**Files:**
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngine.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngineTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeStressTest.kt`

- [ ] **Step 1: Write failing rule tests**

```kotlin
class FruitMergeEngineTest {
    private val engine = FruitMergeEngine()

    @Test
    fun `equal contact merges once and scores the created level`() {
        val state = stateWithTouching(FruitLevel.CHERRY, FruitLevel.CHERRY)
        val next = engine.step(state, 1f / 60f)
        assertEquals(listOf(FruitLevel.STRAWBERRY), next.bodies.map(FruitBody::level))
        assertEquals(FruitLevel.STRAWBERRY.mergeScore, next.score)
    }

    @Test
    fun `two melons disappear for maximum award`() {
        val next = engine.step(stateWithTouching(FruitLevel.MELON, FruitLevel.MELON), 1f / 60f)
        assertTrue(next.bodies.isEmpty())
        assertEquals(FruitLevel.MELON.mergeScore * 2, next.score)
    }

    @Test
    fun `brief overflow resets but sustained overflow ends run`() {
        val overflow = stateWithBodyAboveDangerLine()
        var brief = overflow
        repeat(89) { brief = engine.step(brief, 1f / 60f) }
        assertEquals(RunPhase.PLAYING, brief.phase)
        val terminal = engine.step(brief, 1f / 60f)
        assertEquals(RunPhase.RESULT, terminal.phase)
    }

    @Test
    fun `five clears and three shakes are free and consume only on success`() {
        var state = stateWithBody(FruitLevel.APPLE)
        repeat(5) {
            state = engine.clear(state, state.bodies.single().id).state
            state = state.copy(
                bodies = listOf(FruitBody(state.nextBodyId, FruitLevel.APPLE, Vec2(0.5f, 0.8f))),
                nextBodyId = state.nextBodyId + 1,
            )
        }
        assertEquals(0, state.freeClears)
        repeat(3) {
            state = engine.shake(state).state
            state = state.copy(bodies = state.bodies.map { it.copy(velocity = Vec2(0f, 0f), angularVelocity = 0f) })
        }
        assertEquals(0, state.freeShakes)
    }

    private fun stateWithTouching(first: FruitLevel, second: FruitLevel) = FruitMergeState(
        bodies = listOf(
            FruitBody(1, first, Vec2(0.5f - first.radius, 0.75f)),
            FruitBody(2, second, Vec2(0.5f + second.radius, 0.75f)),
        ),
        nextBodyId = 3,
    )

    private fun stateWithBody(level: FruitLevel) = FruitMergeState(
        bodies = listOf(FruitBody(1, level, Vec2(0.5f, 0.8f))),
        nextBodyId = 2,
    )

    private fun stateWithBodyAboveDangerLine() = FruitMergeState(
        bodies = listOf(FruitBody(1, FruitLevel.MELON, Vec2(0.5f, FruitMergeEngine.DANGER_Y - 0.12f))),
        nextBodyId = 2,
    )
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeEngineTest'`

Expected: unresolved engine rule methods.

- [ ] **Step 3: Implement typed rule results and deterministic arbitration**

Expose:

```kotlin
enum class ActionRejection { GAME_OVER, BOARD_BUSY, BODY_NOT_FOUND, NO_FREE_USE, BODY_LIMIT }
data class ActionResult(val state: FruitMergeState, val rejection: ActionRejection? = null)
data class EngineDiagnostics(val maxCandidatePairs: Int = 0)

interface FruitMergeRules {
    fun movePreview(state: FruitMergeState, normalizedX: Float): FruitMergeState
    fun drop(state: FruitMergeState): ActionResult
    fun step(state: FruitMergeState, elapsedSeconds: Float): FruitMergeState
    fun beginClear(state: FruitMergeState, paid: Boolean = false): ActionResult
    fun clear(state: FruitMergeState, bodyId: Long, paid: Boolean = false): ActionResult
    fun cancelClear(state: FruitMergeState): FruitMergeState
    fun shake(state: FruitMergeState, paid: Boolean = false): ActionResult
    fun newRun(state: FruitMergeState): FruitMergeState
}

class FruitMergeEngine(private val physics: FruitPhysics = FruitPhysics()) : FruitMergeRules {
    val diagnostics: EngineDiagnostics
    companion object { const val DANGER_Y = 0.18f }
}
```

Rules:

- clamp preview so the entire fruit remains in bounds;
- reject drop at `MAX_BODIES` or outside PLAYING;
- choose spawn level by cumulative `spawnWeight` from `RandomState`;
- sort merge contacts by `(minBodyId, maxBodyId)` and mark consumed IDs;
- merge at mass-weighted midpoint with bounded average momentum;
- grant next-level score, or `MELON.mergeScore * 2` when two melons vanish;
- execute at most one merge per source body per fixed step;
- use a 1.5-second danger threshold and a 0.75-second post-shake/merge grace;
- consume clear only after a valid target is removed;
- consume shake only when bodies are settled; derive signed impulses from PRNG,
  clamp horizontal magnitude to `0.55f` and upward magnitude to `0.38f`;
- `paid=true` bypasses the free-count check but never decrements below zero.

- [ ] **Step 4: Add bounded stress coverage**

```kotlin
@Test
fun `ten simulated minutes remain finite and bounded`() {
    var state = FruitMergeState(
        bodies = List(MAX_BODIES) { index ->
            FruitBody(
                id = index.toLong() + 1,
                level = FruitLevel.BLUEBERRY,
                position = Vec2(0.05f + (index % 10) * 0.095f, 0.20f + (index / 10) * 0.095f),
            )
        },
        nextBodyId = MAX_BODIES.toLong() + 1,
    )
    repeat(60 * 60 * 10) { state = engine.step(state, 1f / 60f) }
    assertTrue(state.bodies.size <= MAX_BODIES)
    assertTrue(state.bodies.all { it.position.isFinite() && it.velocity.isFinite() })
    assertTrue(engine.diagnostics.maxCandidatePairs <= MAX_CANDIDATE_PAIRS)
}
```

Keep this deterministic; if runtime is excessive, simulate 60 seconds in the
normal test and mark the ten-minute variant as a local performance gate invoked
explicitly by class name.

- [ ] **Step 5: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeEngineTest' --tests '*FruitMergeStressTest'
```

Expected: all rule and stress tests pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: implement fruit merge rules and actions"
```

---

### Task 6: Persist and strictly validate the run

**Files:**
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshot.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergePersistence.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshotTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergePersistenceTest.kt`

- [ ] **Step 1: Write failing round-trip and corruption tests**

```kotlin
class FruitMergeSnapshotTest {
    @Test
    fun `validated snapshot round trips authoritative run fields`() {
        val state = populatedState(freeClears = 2, freeShakes = 1, randomBits = 99)
        assertEquals(state, FruitMergeSnapshot.from(state).toState(bestScore = state.bestScore))
    }

    @Test
    fun `invalid body and impossible counts are rejected`() {
        assertFails { validSnapshot().copy(freeClears = -1).toState(0) }
        assertFails { validSnapshot().copy(bodies = listOf(validBody().copy(x = Float.NaN))).toState(0) }
        assertFails { validSnapshot().copy(bodies = List(MAX_BODIES + 1) { validBody(id = it.toLong()) }).toState(0) }
    }

    private fun validBody(id: Long = 1L) = FruitBodySnapshot(
        id = id,
        level = FruitLevel.CHERRY.name,
        x = 0.5f,
        y = 0.8f,
        velocityX = 0f,
        velocityY = 0f,
        angle = 0f,
        angularVelocity = 0f,
    )

    private fun validSnapshot() = FruitMergeSnapshot(
        bodies = listOf(validBody()),
        previewLevel = FruitLevel.BLUEBERRY.name,
        previewX = 0.5f,
        randomBits = 7L,
        nextBodyId = 2L,
        score = 0L,
        freeClears = 5,
        freeShakes = 3,
        dangerSeconds = 0f,
        graceSeconds = 0f,
        runOrdinal = 1L,
        phase = RunPhase.PLAYING.name,
    )

    private fun populatedState(freeClears: Int, freeShakes: Int, randomBits: Long) = FruitMergeState(
        bodies = listOf(
            FruitBody(1, FruitLevel.PLUM, Vec2(0.42f, 0.74f), Vec2(0.01f, -0.02f)),
            FruitBody(2, FruitLevel.APPLE, Vec2(0.63f, 0.79f)),
        ),
        previewLevel = FruitLevel.STRAWBERRY,
        previewX = 0.37f,
        random = RandomState(randomBits),
        nextBodyId = 3,
        score = 240,
        bestScore = 800,
        freeClears = freeClears,
        freeShakes = freeShakes,
        runOrdinal = 4,
    )
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeSnapshotTest'`

Expected: snapshot types are unresolved.

- [ ] **Step 3: Implement serialization and repository**

Define `@Serializable` snapshot/body DTOs containing only primitives and enum
names. Configure:

```kotlin
internal const val SNAPSHOT_KEY = "game_snapshot"
internal const val BEST_SCORE_KEY = "best_score"
internal val FruitMergeSnapshotSpec = MiniAppSnapshotSpec(
    serializer = FruitMergeSnapshot.serializer(),
    currentVersion = 1,
)

internal class FruitMergePersistence(private val storage: MiniAppStorage) {
    suspend fun restore(): FruitMergeState? = runCatching {
        val best = storage.getLong(BEST_SCORE_KEY, 0L).coerceAtLeast(0L)
        storage.readSnapshot(SNAPSHOT_KEY, FruitMergeSnapshotSpec)?.toState(best)
    }.getOrNull()

    suspend fun checkpoint(state: FruitMergeState) {
        storage.putLong(BEST_SCORE_KEY, maxOf(state.bestScore, state.score))
        storage.writeSnapshot(SNAPSHOT_KEY, FruitMergeSnapshot.from(state), FruitMergeSnapshotSpec)
    }

    suspend fun clearRun() = storage.remove(SNAPSHOT_KEY)
}
```

Validate finite positions/velocities/angles, world bounds with a small tolerance,
unique positive IDs, known levels, `nextBodyId > maxId`, score/count ranges,
phase, random seed, run ordinal, and maximum body count. Do not persist targeting
mode, effect timestamps, particles, or pending ad actions.

- [ ] **Step 4: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeSnapshotTest' --tests '*FruitMergePersistenceTest'
```

Expected: round trip, invalid snapshot, empty storage, and best-score preservation pass.

Commit:

```bash
git add game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence
git commit -m "feat: persist fruit merge runs"
```

---

### Task 7: Build the Store, stale-safe action gates, and lifecycle component

**Files:**
- Delete generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeComponent.kt`
- Delete generated test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/FruitmergeComponentTest.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStore.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreFactory.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/session/FruitMergeComponent.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/store/FruitMergeStoreTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/session/FruitMergeComponentTest.kt`

- [ ] **Step 1: Write failing bounded-frame and stale-token tests**

```kotlin
@Test
fun `frame gap executes at most three fixed steps`() {
    val harness = storeHarness()
    harness.store.accept(FruitMergeStore.Intent.Frame(1.0f))
    assertEquals(3, harness.engine.stepCalls)
}

@Test
fun `stale paid action token cannot mutate a new run`() {
    val component = componentHarness(freeClears = 0)
    val token = assertNotNull(component.requestClearGate())
    component.newGame()
    component.completePaidAction(token)
    assertEquals(TargetingMode.NONE, component.model.value.game.targetingMode)
}

@Test
fun `duplicate completion applies a paid action once`() {
    val component = componentHarness(freeShakes = 0, settled = true)
    val token = assertNotNull(component.requestShakeGate())
    component.completePaidAction(token)
    component.completePaidAction(token)
    assertEquals(1, component.engine.paidShakeCalls)
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeStoreTest' --tests '*FruitMergeComponentTest'
```

Expected: Store/component APIs unresolved.

- [ ] **Step 3: Implement the Store contract and fixed-step accumulator**

```kotlin
interface FruitMergeStore : Store<FruitMergeStore.Intent, FruitMergeStore.State, FruitMergeStore.Label> {
    sealed interface Intent {
        data class Frame(val elapsedSeconds: Float) : Intent
        data class MovePreview(val x: Float) : Intent
        data object Drop : Intent
        data object BeginFreeClear : Intent
        data class ClearBody(val id: Long) : Intent
        data object CancelClear : Intent
        data object FreeShake : Intent
        data object PaidClear : Intent
        data object PaidShake : Intent
        data object NewGame : Intent
        data object Checkpoint : Intent
    }

    data class State(val game: FruitMergeState, val initialized: Boolean = false)
    sealed interface Label {
        data class Announce(val message: Announcement) : Label
        data object ResultReached : Label
    }
}
```

The executor initializes from persistence, ignores gameplay until initialized,
caps each `Frame` at three `1f / 60f` steps, discards excess accumulator time,
checkpoints after drop/clear/shake/new game/result/background, and never writes on
every tick. Reducer messages replace the immutable state. Destroy cancels the
session scope and performs no later mutation.

- [ ] **Step 4: Implement component tokens and lifecycle visibility**

```kotlin
enum class PaidAction { CLEAR, SHAKE }
data class PaidActionToken(val sessionKey: Long, val runOrdinal: Long, val id: Long, val action: PaidAction)

interface FruitMergeComponent {
    val model: Value<Model>
    fun movePreview(x: Float)
    fun drop()
    fun requestClearGate(): PaidActionToken?
    fun selectClearTarget(id: Long)
    fun cancelClear()
    fun requestShakeGate(): PaidActionToken?
    fun completePaidAction(token: PaidActionToken)
    fun newGame()

    data class Model(val game: FruitMergeState, val initialized: Boolean, val visible: Boolean)
}
```

When a free use remains, `requestClearGate`/`requestShakeGate` dispatch the free
action and return `null`. At zero, create one pending token and return it without
mutation. Completion requires exact token equality, current session key, current
run ordinal, matching action, and a live lifecycle. Clear completion enters
target mode; shake completion executes once. Visibility false stops frame intents
and checkpoints; visibility true resumes without recreating Store/session.

- [ ] **Step 5: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeStoreTest' --tests '*FruitMergeComponentTest'
```

Expected: frame cap, checkpoint policy, visibility, stale token, duplicate token,
and teardown tests pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: add fruit merge runtime state holder"
```

---

### Task 8: Wire the retained session graph and advertisement gates

**Files:**
- Replace generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeSession.kt`
- Replace generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeSessionGraph.kt`
- Replace generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergePlugin.kt`
- Replace generated test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/FruitmergePluginContractTest.kt`
- Create idiomatic files: `FruitMergeSession.kt`, `FruitMergeSessionGraph.kt`, `FruitMergePlugin.kt`, `FruitMergePluginContractTest.kt`

- [ ] **Step 1: Extend the failing plugin contract**

Test that the manifest ID is `game.fruitmerge`, manifest access does not create a
child graph, creating a session returns a retained graph session, destroying is
idempotent, and both placements are requested by the session with a recording
capability.

The recording capability returns:

```kotlin
override fun rememberGate(placement: MiniAppInterstitialPlacement): MiniAppInterstitialGate {
    placements += placement
    return MiniAppInterstitialGate(willShowAd = false) { complete -> complete() }
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergePluginContractTest'`

Expected: idiomatic session/graph/plugin types do not exist yet.

- [ ] **Step 3: Wire Metro without crossing boundaries**

`FruitMergeSessionGraph` must retain these names:

```kotlin
@GraphExtension(MiniAppSessionScope::class)
interface FruitMergeSessionGraph {
    val session: FruitMergeSession

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    fun interface Factory {
        fun createGameFruitmergeSessionGraph(
            @Provides context: MiniAppSessionContext,
        ): FruitMergeSessionGraph
    }
}
```

Providers create persistence from `context.storage`, StoreFactory from engine and
persistence, component from `context.componentContext` and `context.visibility`,
and session from component plus app-scoped `MiniAppInterstitialCapability`.

`FruitMergeSession.Content` obtains both gates:

```kotlin
val clearGate = interstitials.rememberGate(MiniAppInterstitialPlacement.FRUIT_MERGE_CLEAR)
val shakeGate = interstitials.rememberGate(MiniAppInterstitialPlacement.FRUIT_MERGE_SHAKE)
FruitMergeContent(
    component = component,
    requestClearAd = { token -> clearGate.request { component.completePaidAction(token) } },
    requestShakeAd = { token -> shakeGate.request { component.completePaidAction(token) } },
    modifier = modifier,
)
```

`FruitMergePlugin` contributes to the app set, uses Compose resources, sets
`MiniAppId("game.fruitmerge")`, category `game`, and returns
`RetainedMiniAppSession(graph, graph.session)`. Do not edit `settings.gradle.kts`.

- [ ] **Step 4: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:allTests :game:fruitmerge:validateMiniAppDependencies
```

Expected: graph/session contracts and boundary validation pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: wire fruit merge session graph"
```

---

### Task 9: Build adaptive Material 3 gameplay UI

**Files:**
- Replace generated: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/FruitmergeContent.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeContent.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeControls.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreenTest.kt`

- [ ] **Step 1: Write failing adaptive hierarchy tests**

Use Compose UI test constraints at compact portrait, expanded landscape, and
compact-height sizes. Assert one `fruit_merge_board` exists after live resize,
the supporting pane exists, score precedes actions in traversal, action targets
are at least 48 dp, and counts render `5`/`3` initially.

```kotlin
@Test
fun `live compact to wide resize preserves one board and action state`() {
    rule.setContent { TestResizableSurface { FruitMergeScreen(model(), callbacks()) } }
    rule.onNodeWithTag("fruit_merge_board").assertExists()
    resizeTo(width = 1000.dp, height = 700.dp)
    rule.onAllNodesWithTag("fruit_merge_board").assertCountEquals(1)
    rule.onNodeWithTag("fruit_merge_clear").assertTextContains("5")
    rule.onNodeWithTag("fruit_merge_shake").assertTextContains("3")
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeScreenTest'`

Expected: UI functions/tags unresolved.

- [ ] **Step 3: Implement the adaptive structure**

`FruitMergeContent` subscribes to Decompose `Value`, uses one lifecycle-aware
frame loop only while `model.visible && initialized && phase == PLAYING`, clamps
elapsed time before dispatch, and forwards callbacks.

`FruitMergeScreen` uses:

```kotlin
AdaptiveGameScaffold(
    modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
    primary = {
        FruitBoard(
            model = model,
            onPreviewMoved = onPreviewMoved,
            onDrop = onDrop,
            onFruitSelected = onFruitSelected,
            modifier = Modifier.fillMaxSize().testTag("fruit_merge_board"),
        )
    },
    supporting = {
        ScoreAndNext(model)
        FruitProgression()
        FruitMergeActions(model, onClear, onShake, onCancelClear, onNewGame)
    },
)
```

Use the existing design-system buttons where their semantics match; otherwise use
Material 3 `FilledTonalButton`/`OutlinedButton` with theme colors. Do not create a
toolbar, Back, Settings, banner, or navigation host.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeScreenTest'`

Expected: compact/wide/compact-height hierarchy, touch size, and live-resize tests pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: add adaptive fruit merge screen"
```

---

### Task 10: Render expressive fruit in one bounded Canvas

**Files:**
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitBoard.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitPainter.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMotion.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMotionTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitBoardTest.kt`

- [ ] **Step 1: Write failing projection, hit-test, and motion-policy tests**

```kotlin
@Test
fun `world projection preserves physics under viewport changes`() {
    val point = Vec2(0.25f, 0.75f)
    assertEquals(Offset(50f, 300f), WorldProjection(Size(200f, 400f)).toViewport(point))
}

@Test
fun `clear hit test chooses nearest containing fruit and stable id breaks ties`() {
    val bodies = listOf(
        FruitBody(9, FruitLevel.APPLE, Vec2(0.5f, 0.5f)),
        FruitBody(3, FruitLevel.APPLE, Vec2(0.5f, 0.5f)),
    )
    assertEquals(3L, hitTestFruit(bodies, Vec2(0.5f, 0.5f)))
}

@Test
fun `reduced motion disables decorative motion but preserves expression`() {
    val policy = FruitMotionPolicy(reducedMotion = true)
    assertEquals(1f, policy.scaleFor(impact = 1f, ageSeconds = 0.02f))
    assertEquals(0, policy.particleCountForMerge(FruitLevel.MELON))
    assertEquals(FaceExpression.WORRIED, policy.faceFor(nearDanger = true, impact = 0f, falling = false))
}
```

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMotionTest' --tests '*FruitBoardTest'
```

Expected: projection, hit-test, and motion policy unresolved.

- [ ] **Step 3: Implement one Canvas and original painter**

`FruitBoard` must use one `Canvas`, one pointer-input node, normalized world
coordinates, and a maximum `MAX_EFFECTS = 24` pooled visual effect list. A tap in
clear mode hit-tests the bodies; otherwise drag/tap updates preview X and release
drops. The danger line and drop guide use theme-derived high-contrast colors.

`FruitPainter` defines an authored palette per `FruitLevel` and draws, in order:

1. bounded soft shadow ellipse;
2. radial/linear matte body gradient clipped to the circle;
3. level-specific leaf/crown/silhouette accent;
4. restrained highlight arc;
5. two eyes, brows, mouth, and blush from `FaceExpression`;
6. merge glow/particles only when allowed by motion policy.

No bitmap, blur render effect, per-fruit composable, per-fruit coroutine, or
per-fruit `Animatable` is permitted. Blink timing derives from `(body.id,
runOrdinal, frameTimeBucket)` so it is stable and allocation-free.

Use these expressions:

```kotlin
enum class FaceExpression { IDLE, BLINK, SQUINT, SURPRISED, WORRIED, HAPPY }

fun faceFor(nearDanger: Boolean, impact: Float, falling: Boolean, mergedAge: Float?): FaceExpression = when {
    mergedAge != null && mergedAge < 0.35f -> FaceExpression.HAPPY
    nearDanger -> FaceExpression.WORRIED
    impact > 0.65f -> FaceExpression.SURPRISED
    falling -> FaceExpression.SQUINT
    else -> FaceExpression.IDLE
}
```

- [ ] **Step 4: Verify reduced motion and bounded effects**

Add tests that 100 merge events retain only the newest 24 effects, old effects
expire, no decorative scale exceeds `1.12f`, no squash goes below `0.88f`, and
reduced motion always returns neutral transform/zero particles.

- [ ] **Step 5: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMotionTest' --tests '*FruitBoardTest'
```

Expected: all painter policy/projection/hit-test tests pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: render expressive fruit canvas"
```

---

### Task 11: Complete localization, accessibility, icon, and result flow

**Files:**
- Modify: `game/fruitmerge/src/commonMain/composeResources/values/strings.xml`
- Modify: `game/fruitmerge/src/commonMain/composeResources/drawable/miniapp_icon.xml`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeSemantics.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeAccessibilityTest.kt`
- Test: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/FruitMergeLifecycleIntegrationTest.kt`

- [ ] **Step 1: Write failing accessibility and lifecycle tests**

Cover:

- board description includes score, next fruit, fill percentage, and clear mode;
- clear/shake expose remaining counts and unavailable reasons;
- each selectable fruit semantic target has level plus approximate region;
- action traversal is score → best → next → board → clear → shake;
- result focuses New game and exposes final/best score;
- visibility false stops frame dispatch and checkpoints once;
- recreation restores bodies/seed/counts and never restores a pending ad token;
- destroy is idempotent and stale callback cannot affect successor session.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew :game:fruitmerge:iosSimulatorArm64Test --tests '*FruitMergeAccessibilityTest' --tests '*FruitMergeLifecycleIntegrationTest'
```

Expected: missing localized semantics/result behavior.

- [ ] **Step 3: Add all localized resources and semantic mapping**

`strings.xml` must contain title/description; score/best/next; every fruit name;
clear/shake/free/ad/cancel/new-game; danger/board/result descriptions; action
unavailable reasons; and announcement templates. Kotlin code must not hard-code
user-visible English.

`FruitMergeSemantics` maps normalized centers into left/center/right and
top/middle/bottom region resources. In clear mode, expose invisible semantic hit
targets only for current bodies while Canvas remains the sole renderer.

Replace the scaffold square icon with an original 24×24 vector containing two
overlapping warm fruit circles, one leaf, and a simple smiling face. Use only
authored path data and theme-compatible fixed catalog colors.

- [ ] **Step 4: Run GREEN and commit**

Run:

```bash
./gradlew :game:fruitmerge:allTests
```

Expected: accessibility, lifecycle, engine, persistence, and contract tests all pass.

Commit:

```bash
git add game/fruitmerge
git commit -m "feat: complete fruit merge accessibility"
```

---

### Task 12: Final performance, architecture, and platform verification

**Files:**
- Modify only if verification exposes a scoped defect: files already listed above
- Modify: `game/fruitmerge/PROVENANCE.md` only to record final evidence
- Do not modify: root `settings.gradle.kts` `miniApps` allowlist

- [ ] **Step 1: Run focused module verification**

```bash
./gradlew :game:fruitmerge:allTests
./gradlew :game:fruitmerge:validateMiniAppDependencies
./gradlew :game:fruitmerge:compileAndroidMain
./gradlew :game:fruitmerge:compileKotlinIosSimulatorArm64
```

Expected: every command exits 0.

- [ ] **Step 2: Run the complete MiniApp gate**

```bash
./gradlew :game:fruitmerge:verifyMiniApp
```

Expected: tests, dependency validation, Android compile, and iOS Simulator compile pass.

- [ ] **Step 3: Verify shared contract consumers and host compilation**

```bash
./gradlew :miniapp:compose:allTests
./gradlew :composeApp:compileAndroidMain
```

Expected: the new enum entries do not break the host adapter or existing games.

- [ ] **Step 4: Audit performance-sensitive source and shipping boundaries**

Run:

```bash
rg -n "Animatable|launch\(|async\(|ImageBitmap|BlurEffect" game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui
rg -n "com\.russhwolf\.settings|AdMob|monetization\.ads" game/fruitmerge/src/commonMain
rg -n "game\.fruitmerge|:game:fruitmerge" settings.gradle.kts miniapp/bundle
git diff --check
git status --short
```

Expected:

- no per-fruit animation/coroutine, bitmap, raw Settings, AdMob, or native-ad import;
- discovery may mention the project only through generated settings behavior, but
  the root `miniApps` block and production bundle remain unchanged;
- no whitespace errors;
- status contains only intended Fruit Merge/shared-contract changes.

- [ ] **Step 5: Re-index and inspect the architecture graph**

Run the codebase-memory repository index, then verify the new module depends only
on approved inward contracts and no feature/application/native-ad module.

Expected: graph shows `:game:fruitmerge` → MiniApp contracts, core uikit, MVI, and
Metro only; no reverse feature dependency and no app/native-ad edge.

- [ ] **Step 6: Update final evidence and commit**

Append exact successful commands and `NOT ALLOWLISTED` to
`game/fruitmerge/PROVENANCE.md`, then run `git diff --check` again.

Commit:

```bash
git add game/fruitmerge/PROVENANCE.md
git commit -m "docs: record fruit merge verification"
```

- [ ] **Step 7: Request review and finish the branch**

Invoke `superpowers:requesting-code-review`, address valid findings through
`superpowers:receiving-code-review`, rerun the complete verification above, then
invoke `superpowers:finishing-a-development-branch`. Do not push, open a PR,
merge, or allowlist without the user's explicit selected finishing option.

---

## Plan Self-Review Checklist

- [x] Every design requirement maps to a task: genre rules (Tasks 3–5), ad-gated
  actions (Tasks 2, 7, 8), persistence/lifecycle (Tasks 6–8, 11), adaptive design
  (Task 9), original expressive art and reduced motion (Task 10), accessibility
  (Task 11), weak-device performance (Tasks 4, 5, 7, 10, 12), provenance and
  NOT ALLOWLISTED status (Tasks 1, 12).
- [x] The plan never edits the production MiniApp allowlist.
- [x] The public ID is consistently `game.fruitmerge`; Kotlin game-owned names are
  `FruitMerge*`; the required Metro factory remains
  `createGameFruitmergeSessionGraph`.
- [x] Free counts are consistently five clears and three shakes per run.
- [x] Paid completion is consistently immediate when no ad will show and stale-safe
  when an ad callback arrives later.
- [x] No placeholder or speculative subsystem remains in scope.
