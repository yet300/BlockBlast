# Atomic Move Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the per-placement burst of independent gameplay events with one immutable `MoveResolved` event consumed serially by the game store.

**Architecture:** `GameEngine` remains the only place that calculates placement, clears, scoring, combo, feedback, and game over. After publishing the new `GameState`, it emits one `GameEvent.MoveResolved` carrying the complete result. `GameStoreFactory` maps that single event to placement SFX, clear SFX, voice, and analytics in narrative order; lifecycle-only `GameStarted` remains separate.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines `SharedFlow`, MVIKotlin coroutine executor, kotlin.test, Turbine, Gradle.

---

### Task 1: Define and emit one move result

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/model/GameEvent.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameEngine.kt`
- Test: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt`

- [ ] **Step 1: Write the failing event-contract test**

Add a test that places a three-cell vertical piece to clear three rows and expects exactly one event:

```kotlin
val event = assertIs<GameEvent.MoveResolved>(awaitItem())
assertEquals(placePiece.pieceId, event.pieceId)
assertEquals(3, event.placedCellCount)
assertEquals(3, event.linesCount)
assertEquals(24, event.clearedCells.size)
assertEquals(3L, event.placementPoints)
assertEquals(60L, event.clearPoints)
assertEquals(0L, event.allClearPoints)
assertEquals(63L, event.totalPoints)
assertEquals(1, event.comboLevel)
assertEquals(0, event.movesWithoutClear)
assertEquals(FeedbackType.GREAT, event.feedback)
assertFalse(event.isGameOver)
expectNoEvents()
```

- [ ] **Step 2: Run the domain suite and verify RED**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: compilation fails because `GameEvent.MoveResolved` does not exist.

- [ ] **Step 3: Add the immutable event model**

Replace the move-specific event variants with:

```kotlin
@Serializable
data class MoveResolved(
    val pieceId: Long,
    val placedCellCount: Int,
    val clearedCells: List<Position>,
    val linesCount: Int,
    val isCrossClear: Boolean,
    val isBoardEmpty: Boolean,
    val placementPoints: Long,
    val clearPoints: Long,
    val allClearPoints: Long,
    val totalPoints: Long,
    val comboLevel: Int,
    val movesWithoutClear: Int,
    val feedback: FeedbackType?,
    val isGameOver: Boolean,
) : GameEvent
```

Keep only `GameStarted` as the separate lifecycle event.

- [ ] **Step 4: Emit `MoveResolved` once after the state snapshot**

In `GameEngine.placePiece`, replace the `PiecePlaced`, `LinesCleared`, `Feedback`, `ComboActive`, and `GameOver` emissions with one `events.tryEmit(GameEvent.MoveResolved(...))` populated from the already calculated local values. Use `emptyList()` when no cells were cleared.

- [ ] **Step 5: Update existing event assertions**

Change domain tests to inspect `MoveResolved` fields instead of counting or filtering legacy event variants. Preserve the existing scoring, feedback, combo, game-over, and state assertions.

- [ ] **Step 6: Run the domain suite and verify GREEN**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: `BUILD SUCCESSFUL` with no failed tests.

### Task 2: Consume the result serially in GameStore

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactory.kt`
- Test: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactoryTest.kt`

- [ ] **Step 1: Write the failing orchestration test**

Record calls in the fake audio repository and assert one clearing move produces:

```kotlin
assertEquals(
    listOf("placement", "clear:3", "voice:GREAT"),
    deps.audio.calls,
)
```

The fixture must clear three rows without emptying the board.

- [ ] **Step 2: Run the game suite and verify RED**

Run:

```bash
./gradlew :feature:game:allTests
```

Expected: compilation fails until the store handles `MoveResolved`, or the order assertion fails before the store is migrated.

- [ ] **Step 3: Process `MoveResolved` in narrative order**

In the existing lifecycle-bound `engine.events.collect` block:

```kotlin
is GameEvent.MoveResolved -> {
    audio.playPlacementSound()
    if (event.linesCount > 0) audio.playClearSound(event.linesCount)
    event.feedback?.let { audio.playVoiceFeedback(it) }
    if (event.linesCount > 0) {
        logger.log("lines_cleared", engine.state.value, moveAnalytics(event))
    }
    if (event.comboLevel >= 2 && event.linesCount > 0) {
        logger.log(
            "combo_reached",
            engine.state.value,
            mapOf("combo_level" to event.comboLevel),
        )
    }
}
```

Keep `GameStarted` as a no-op in this collector because music remains derived from continuous state.

- [ ] **Step 4: Extend clear analytics from the authoritative result**

Pass `lines_count`, `cleared_cells`, `is_cross_clear`, `is_all_clear`, placement points, clear points, all-clear points, total points, combo level, grace counter, feedback name or `none`, and `is_game_over` from `MoveResolved`. Do not reconstruct these values from multiple state snapshots.

- [ ] **Step 5: Run domain and game suites and verify GREEN**

Run:

```bash
./gradlew :core:domain:allTests :feature:game:allTests
```

Expected: `BUILD SUCCESSFUL` with audio and analytics regression tests passing.

### Task 3: Verify the stage for manual approval

**Files:**
- Verify all modified files above.

- [ ] **Step 1: Run affected and integration tests**

```bash
./gradlew :core:domain:allTests :core:data:allTests :feature:game:allTests :feature:root:allTests
```

- [ ] **Step 2: Build Android and the iOS simulator framework**

```bash
./gradlew :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 3: Check the diff**

```bash
git diff --check
git status --short
```

- [ ] **Step 4: Stop for user testing**

Do not commit this stage until the user confirms the APK behavior. After approval, commit with:

```bash
git commit -m "refactor(game): publish atomic move results"
```
