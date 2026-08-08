# Starter Layouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. The user requires inline execution on branch `1.6.5`, without worktrees or subagents, and review before commit.

**Goal:** Start about half of post-tutorial fresh rounds from a varied, partially occupied, playable 8×8 grid while preserving an empty tutorial round and all restore flows.

**Architecture:** Add a pure domain `StarterLayoutGenerator` that owns twelve templates, seeded transformations, bounded validation, and an empty fallback. `GameEngine` asks it for a complete starting grid plus the matching initial shapes so validation and the visible tray cannot diverge. `feature:game` supplies the policy flag from `SettingsRepository.tutorialSeen`; saved and result-restored rounds bypass generation.

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines/StateFlow, MVIKotlin, Metro DI, kotlin.test, Gradle.

---

### Task 1: Pure starter-layout generator

**Files:**
- Create: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/StarterLayoutGenerator.kt`
- Create: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/StarterLayoutGeneratorTest.kt`

- [x] **Step 1: Write failing catalog and deterministic-selection tests**

Add tests that call `StarterLayoutGenerator(WeightedShapeGenerator()).generate(seed, enabled = true)` and prove:

```kotlin
@Test
fun enabled_generation_is_deterministic_for_the_same_seed() {
    val first = generator.generate(seed = 7L, enabled = true)
    val second = generator.generate(seed = 7L, enabled = true)
    assertEquals(first, second)
}

@Test
fun disabled_generation_always_returns_an_empty_round() {
    repeat(50) { seed ->
        val round = generator.generate(seed.toLong(), enabled = false)
        assertTrue(round.grid.isBoardEmpty())
    }
}
```

Inspect all enabled seeded results and assert that every non-empty result occupies 14–22 cells and contains no complete row or column. Across 200 seeds, assert both empty and non-empty results occur and that the non-empty share stays between 40% and 60%.

- [x] **Step 2: Run the tests and verify RED**

Run:

```bash
rtk ./gradlew :core:domain:allTests
```

Expected: compilation fails because `StarterLayoutGenerator` is absent.

- [x] **Step 3: Implement twelve templates and seeded transforms**

Create these focused types:

```kotlin
internal data class StartingRound(
    val grid: Grid,
    val shapes: List<Polyomino>,
)

internal class StarterLayoutGenerator(
    private val shapeGenerator: ShapeGenerator,
) {
    fun generate(seed: Long?, enabled: Boolean): StartingRound
}
```

The implementation must:

- return `Grid()` plus `shapeGenerator.nextTray(Grid(), seed)` when disabled;
- use `Random(seed)` when a seed is supplied and `Random.Default` otherwise;
- use one seeded `nextBoolean()` as the 50% starter gate;
- keep twelve explicit `Set<Position>` base templates with 14–22 cells;
- choose a template, 0/90/180/270-degree rotation, and optional horizontal reflection from the same RNG;
- map occupied cells to color ids `1..6` without affecting geometry;
- reject transformed grids with a complete row or column.

- [x] **Step 4: Add bounded tray-playability validation**

For each candidate grid, obtain the exact initial shapes with:

```kotlin
val shapes = shapeGenerator.nextTray(grid = candidateGrid, seed = seed)
```

Require every initial shape to have at least one legal placement on the untouched starter grid, then search all shape orders and legal placements to depth three. Each simulated placement must stamp cells, clear completed rows/columns, and recurse with the remaining shapes. Stop after 25,000 expanded states. Accept only when all three shapes can be placed in one sequence. Try at most twelve seeded template/transformation candidates; on exhaustion return an empty grid with the tray generated for that empty grid.

- [x] **Step 5: Run domain tests and verify GREEN**

Run `rtk ./gradlew :core:domain:allTests` and expect `BUILD SUCCESSFUL`.

---

### Task 2: Integrate the complete starting round into GameEngine

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameEngine.kt`
- Modify: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/GameEngineTest.kt`

- [x] **Step 1: Write failing engine tests**

Add tests proving:

```kotlin
engine.startNewGame(seed = starterSeed, allowStarterLayout = false)
assertTrue(engine.state.value.grid.isBoardEmpty())

engine.startNewGame(seed = starterSeed, allowStarterLayout = true)
assertFalse(engine.state.value.grid.isBoardEmpty())
assertEquals(validatedShapeIds, engine.state.value.currentPieces.map { it.shape.id })
```

Also retain the existing deterministic-seed tests and assert that a subsequent tray advances the seed once, not once per rejected starter candidate.

- [x] **Step 2: Run domain tests and verify RED**

Run `rtk ./gradlew :core:domain:allTests`; expect failure because `allowStarterLayout` is absent.

- [x] **Step 3: Implement engine integration**

Extend the public entry point without changing existing callers:

```kotlin
fun startNewGame(
    seed: Long? = null,
    bestScore: Long = state.value.bestScore,
    allowStarterLayout: Boolean = false,
)
```

Build `StarterLayoutGenerator(shapeGenerator)` inside the engine, request one `StartingRound`, wrap its already-validated shapes in `Piece`, and set its grid and tray in the same `GameState` publication. Advance `deterministicSeed` exactly once after accepting the starting tray. Keep `pieceIdCounter` monotonic for the lifetime of `GameEngine` so Restart cannot collide with live tray-slot identities. Refill generation continues using the current grid as implemented in `5f7feab`.

- [x] **Step 4: Run domain tests and verify GREEN**

Run `rtk ./gradlew :core:domain:allTests`; expect `BUILD SUCCESSFUL`.

---

### Task 3: Apply tutorial policy only to fresh rounds

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameInitializer.kt`
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactory.kt`
- Modify: `feature/game/src/commonTest/kotlin/ge/yet/blockblast/feature/game/store/GameStoreFactoryTest.kt`

- [x] **Step 1: Write failing bootstrap and restart tests**

Use the existing fake `SettingsRepository.tutorialSeen` flow to prove:

- explicit new game with `tutorialSeen = false` always starts empty;
- explicit new game with `tutorialSeen = true` passes starter permission;
- Restart uses the same permission;
- Continue restores the saved grid unchanged;
- ResultRestore preserves the terminal grid unchanged.

- [x] **Step 2: Run feature tests and verify RED**

Run `rtk ./gradlew :feature:game:allTests`; expect starter-policy assertions to fail.

- [x] **Step 3: Pass the policy flag at fresh-round call sites**

In `GameInitializer`, replace both fresh-round calls with:

```kotlin
engine.startNewGame(
    bestScore = current.bestScore,
    allowStarterLayout = settings.tutorialSeen.value,
)
```

In the Restart intent handler, pass `settings.tutorialSeen.value` in the same way. Do not modify `restore`, `restoreResult`, revive, or save-loading branches.

- [x] **Step 4: Run feature tests and verify GREEN**

Run `rtk ./gradlew :feature:game:allTests :feature:root:allTests`; expect `BUILD SUCCESSFUL`.

---

### Task 4: Cross-platform verification and review checkpoint

**Files:**
- Update checkboxes in this plan only.

- [x] **Step 1: Run complete verification**

```bash
rtk ./gradlew \
  :core:domain:allTests \
  :feature:game:allTests \
  :feature:root:allTests \
  :androidApp:assembleDebug \
  :composeApp:linkDebugFrameworkIosSimulatorArm64
rtk git diff --check
```

Expected: both commands exit `0` and Gradle reports `BUILD SUCCESSFUL`.

- [x] **Step 2: Present the APK without committing**

Provide `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Ask the user to verify that the tutorial round remains empty, repeated post-tutorial new games alternate between empty and populated boards, and populated boards remain playable.

- [ ] **Step 3: Commit only after explicit approval**

After the user approves the installed build:

```bash
rtk git add core/domain feature/game docs/superpowers/plans/2026-08-08-starter-layouts.md
rtk git commit -m "feat(domain): add safe starter layouts"
```
