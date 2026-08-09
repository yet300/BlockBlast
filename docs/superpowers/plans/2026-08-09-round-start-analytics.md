# Round Start Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attach deterministic opening-layout and initial-tray diagnostics to fresh `game_started` analytics without persisting diagnostic metadata in `GameState`.

**Architecture:** `StarterLayoutGenerator` records the accepted template index and transformation in its internal result. `GameEngine.startNewGame` returns a small public `RoundStartInfo`, while `GameInitializer` forwards it only for genuinely fresh rounds. `GameStoreFactory` combines that result with the freshly published state and logs stable Firebase-friendly scalar/string properties.

**Tech Stack:** Kotlin Multiplatform, MVIKotlin coroutine executor, Firebase analytics abstraction, kotlin.test, Gradle.

---

### Task 1: Preserve starter selection metadata

**Files:**
- Create: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/model/RoundStartInfo.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/StarterLayoutGenerator.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameEngine.kt`
- Test: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/StarterLayoutGeneratorTest.kt`
- Test: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt`

- [ ] **Step 1: Write failing metadata tests**

For a populated generated round, require non-null metadata with a template ID, quarter-turn value in `0..3`, and reflection flag. For disabled or empty generation, require null metadata. Require `GameEngine.startNewGame` to return matching public metadata.

```kotlin
assertNotNull(round.starterLayout)
assertTrue(round.starterLayout.templateId in 1..12)
assertTrue(round.starterLayout.quarterTurns in 0..3)

val info = engine.startNewGame(seed = starterSeed, allowStarterLayout = true)
assertEquals(RoundLayoutSource.STARTER, info.layoutSource)
assertNotNull(info.starterTemplateId)
```

- [ ] **Step 2: Run domain tests and verify RED**

```bash
./gradlew :core:domain:allTests
```

Expected: compilation fails because `starterLayout`, `RoundStartInfo`, and `RoundLayoutSource` do not exist and `startNewGame` returns `Unit`.

- [ ] **Step 3: Add the public non-persisted result**

```kotlin
package ge.yet.blokblast.domain.model

enum class RoundLayoutSource { EMPTY, STARTER }

data class RoundStartInfo(
    val layoutSource: RoundLayoutSource,
    val starterTemplateId: Int? = null,
    val quarterTurns: Int? = null,
    val reflectedHorizontally: Boolean? = null,
)
```

- [ ] **Step 4: Capture accepted generator metadata**

Add internal `StarterLayoutMetadata` to `StartingRound`. Capture the random template index, quarter turns, and reflection before transforming, and attach metadata only to an accepted populated layout. Every empty/fallback return uses `starterLayout = null`.

```kotlin
internal data class StarterLayoutMetadata(
    val templateId: Int,
    val quarterTurns: Int,
    val reflectedHorizontally: Boolean,
)
```

- [ ] **Step 5: Return metadata from `startNewGame`**

Keep all existing state mutations and events, then return:

```kotlin
return RoundStartInfo(
    layoutSource = if (startingRound.starterLayout == null) {
        RoundLayoutSource.EMPTY
    } else {
        RoundLayoutSource.STARTER
    },
    starterTemplateId = startingRound.starterLayout?.templateId,
    quarterTurns = startingRound.starterLayout?.quarterTurns,
    reflectedHorizontally = startingRound.starterLayout?.reflectedHorizontally,
)
```

- [ ] **Step 6: Run domain tests and verify GREEN**

```bash
./gradlew :core:domain:allTests
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Forward metadata only for fresh rounds

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameInitializer.kt`
- Test: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactoryTest.kt`

- [ ] **Step 1: Add an initialization result**

```kotlin
data class Result(
    val source: Source,
    val roundStart: RoundStartInfo? = null,
)
```

Return `Result(Source.New, engine.startNewGame(...))` for both fresh-game branches. Return `Result(Source.Continue)` and `Result(Source.ResultRestore)` without metadata for continuation and result restoration.

- [ ] **Step 2: Update initializer tests**

Assert a post-tutorial seeded fresh round returns `RoundLayoutSource.STARTER`. Assert continue and result restore return `roundStart == null`.

- [ ] **Step 3: Run game tests and verify GREEN**

```bash
./gradlew :feature:game:allTests
```

Expected: `BUILD SUCCESSFUL` after store call sites are updated in Task 3.

### Task 3: Log Firebase-friendly fresh-round properties

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactory.kt`
- Test: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactoryTest.kt`

- [ ] **Step 1: Write the failing analytics test**

Add an internal `newGameSeed` default parameter to `create` for deterministic tests. Start a known populated round and require:

```kotlin
assertTrue(
    deps.analytics.has(
        "game_started",
        mapOf(
            "source" to "new",
            "layout_source" to "starter",
            "initial_occupied_cells" to occupied,
            "initial_tray_shape_ids" to shapeIds,
            "initial_tray_size_categories" to categories,
        ),
    ),
)
```

Also assert `starter_template_id`, `starter_quarter_turns`, and `starter_reflected_horizontally` are present for starter layouts.

- [ ] **Step 2: Run game tests and verify RED**

```bash
./gradlew :feature:game:allTests
```

Expected: the new analytics fields are absent.

- [ ] **Step 3: Build round-start parameters**

For fresh starts only, add:

```kotlin
private fun roundStartAnalyticsParams(
    info: RoundStartInfo,
    state: GameState,
): Map<String, Any> = buildMap {
    put("layout_source", info.layoutSource.name.lowercase())
    put("initial_occupied_cells", state.grid.cells.count { it != Grid.EMPTY })
    put("initial_tray_shape_ids", state.currentPieces.joinToString(",") { it.shape.id })
    put(
        "initial_tray_size_categories",
        state.currentPieces.joinToString(",") { piece ->
            when (piece.shape.size) {
                in 1..2 -> "compact"
                in 3..4 -> "medium"
                else -> "large"
            }
        },
    )
    info.starterTemplateId?.let { put("starter_template_id", it) }
    info.quarterTurns?.let { put("starter_quarter_turns", it) }
    info.reflectedHorizontally?.let { put("starter_reflected_horizontally", it) }
}
```

Merge these values into `game_started` for bootstrap and restart. Do not add them for continue or result restore.

- [ ] **Step 4: Run game tests and verify GREEN**

```bash
./gradlew :feature:game:allTests
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Verify and stop for manual approval

**Files:**
- Verify all files above.

- [ ] **Step 1: Run regression tests and builds**

```bash
./gradlew :core:domain:allTests :core:data:allTests :feature:game:allTests :feature:root:allTests :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 2: Check repository changes**

```bash
git diff --check
git status --short
```

- [ ] **Step 3: Stop before commit**

After the user approves manual testing, commit with:

```bash
git commit -m "feat(analytics): describe fresh round starts"
```
