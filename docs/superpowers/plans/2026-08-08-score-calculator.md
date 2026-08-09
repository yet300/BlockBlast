# Score Calculator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fractional clear scoring with the approved integer base table, linear combo multiplier, and pure all-clear bonus calculation.

**Architecture:** Keep all arithmetic in the pure `ScoreCalculator`; `GameEngine` continues passing its resulting combo level and consuming the returned clear points. This stage adds the all-clear bonus API but deliberately defers wiring that bonus and the three-miss combo lifecycle to the later atomic move-resolution stage.

**Tech Stack:** Kotlin Multiplatform, `kotlin.test`, Gradle `:core:domain:allTests`

---

### Task 1: Implement approved score arithmetic

**Files:**
- Modify: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculatorTest.kt`
- Modify: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculator.kt`

- [x] **Step 1: Replace calculator expectations with the approved table**

Keep the placement and zero-line tests. Replace the old fractional-multiplier tests in `ScoreCalculatorTest.kt` with:

```kotlin
@Test
fun clearPoints_first_clear_uses_approved_base_table() {
    val expected = listOf(10L, 20L, 60L, 120L, 200L, 300L)

    expected.forEachIndexed { index, points ->
        assertEquals(points, calc.clearPoints(linesCount = index + 1, comboLevel = 1))
    }
}

@Test
fun clearPoints_multiplies_base_by_combo_level() {
    assertEquals(20L, calc.clearPoints(linesCount = 1, comboLevel = 2))
    assertEquals(60L, calc.clearPoints(linesCount = 2, comboLevel = 3))
    assertEquals(480L, calc.clearPoints(linesCount = 4, comboLevel = 4))
}

@Test
fun clearPoints_non_positive_combo_uses_first_clear_multiplier() {
    assertEquals(10L, calc.clearPoints(linesCount = 1, comboLevel = 0))
    assertEquals(20L, calc.clearPoints(linesCount = 2, comboLevel = -5))
}

@Test
fun allClearBonus_requires_a_clear_and_empty_resulting_board() {
    assertEquals(0L, calc.allClearBonus(linesCount = 0, isBoardEmpty = true))
    assertEquals(0L, calc.allClearBonus(linesCount = 1, isBoardEmpty = false))
    assertEquals(300L, calc.allClearBonus(linesCount = 1, isBoardEmpty = true))
}
```

Retain `base_line_reward_is_ten` and add:

```kotlin
@Test
fun all_clear_bonus_is_three_hundred() {
    assertEquals(300, ScoreCalculator.ALL_CLEAR_BONUS)
}
```

Update the two engine integration expectations:

```kotlin
// One placed cell + one-line base 10 * combo level 1.
assertEquals(11L, s.score)
```

```kotlin
// Two placed cells + two-line base 20 * combo level 1.
assertEquals(22L, engine.state.value.score)
```

- [x] **Step 2: Run domain tests and verify RED**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: test compilation fails because `allClearBonus` and `ALL_CLEAR_BONUS` do not exist; after those symbols compile, the old `clearPoints` implementation also disagrees with the new table.

- [x] **Step 3: Implement integer scoring in `ScoreCalculator`**

Replace the old double-based clear calculation with:

```kotlin
fun clearPoints(linesCount: Int, comboLevel: Int): Long {
    if (linesCount <= 0) return 0L
    val base = if (linesCount == 1) {
        BASE_LINE_REWARD.toLong()
    } else {
        BASE_LINE_REWARD.toLong() * linesCount * (linesCount - 1)
    }
    return base * comboLevel.coerceAtLeast(1)
}

fun allClearBonus(linesCount: Int, isBoardEmpty: Boolean): Long =
    if (linesCount > 0 && isBoardEmpty) ALL_CLEAR_BONUS.toLong() else 0L

companion object {
    const val BASE_LINE_REWARD = 10
    const val ALL_CLEAR_BONUS = 300
}
```

Update the KDoc to state that `comboLevel=1` is the first clear multiplier and non-positive levels defensively use `1`.

- [x] **Step 4: Run focused tests and verify GREEN**

Run:

```bash
./gradlew :core:domain:allTests
```

Expected: all `core:domain` tests pass with zero failures.

- [x] **Step 5: Review the uncommitted diff with the user**

Run:

```bash
git diff --check
git status --short
git diff -- core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculator.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculatorTest.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt
```

Expected: only the score-stage plan and three score-stage files are uncommitted. Stop for user review.

- [ ] **Step 6: Commit only after explicit user approval**

```bash
git add docs/superpowers/plans/2026-08-08-score-calculator.md core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculator.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculatorTest.kt core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt
git commit -m "fix(domain): align clear score formula"
```
