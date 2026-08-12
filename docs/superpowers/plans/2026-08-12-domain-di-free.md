# DI-Free Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Metro from `core:domain` and move construction of gameplay rules into `feature:game`.

**Architecture:** Domain keeps plain Kotlin rule types and hides `WeightedShapeGenerator` behind a public `ShapeGenerator.default()` factory. `GameBindings` explicitly provides the default generator, calculator, and reducer, while platform graphs stop registering a domain-owned DI container.

**Tech Stack:** Kotlin Multiplatform, Metro 0.12, Gradle, kotlin.test

---

### Task 1: Add a DI-independent default generator API

**Files:**
- Modify: `core/domain/src/commonTest/kotlin/ge/yet/blokblast/domain/engine/ShapeGeneratorTest.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeGenerator.kt`

- [ ] **Step 1: Write the failing factory test**

Add a test which constructs `ShapeGenerator.default()` and verifies that a seeded tray has the same deterministic shape IDs as `WeightedShapeGenerator`.

```kotlin
@Test
fun default_factory_creates_the_standard_generator() {
    val expected = WeightedShapeGenerator().nextTray(seed = 42L).map { it.id }

    val actual = ShapeGenerator.default().nextTray(seed = 42L).map { it.id }

    assertEquals(expected, actual)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :core:domain:iosSimulatorArm64Test
```

Expected: compilation fails because `ShapeGenerator.default()` does not exist.

- [ ] **Step 3: Add the minimal domain factory**

Add a companion factory to the interface while keeping the implementation internal:

```kotlin
interface ShapeGenerator {
    // existing methods

    companion object {
        fun default(): ShapeGenerator = WeightedShapeGenerator()
    }
}
```

- [ ] **Step 4: Run the focused domain test and verify GREEN**

Run:

```bash
./gradlew :core:domain:iosSimulatorArm64Test
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Move gameplay composition into GameBindings

**Files:**
- Modify: `feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/di/GameBindings.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/GameSessionReducer.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ScoreCalculator.kt`
- Modify: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/engine/ShapeGenerator.kt`
- Modify: `core/domain/build.gradle.kts`
- Delete: `core/domain/src/commonMain/kotlin/ge/yet/blokblast/domain/di/DomainBindings.kt`
- Modify: `composeApp/src/androidMain/kotlin/ge/yet3/blokblast/di/AndroidAppGraph.kt`
- Modify: `composeApp/src/iosMain/kotlin/ge/yet3/blokblast/di/NativeAppGraph.kt`

- [ ] **Step 1: Verify the architectural assertion is RED**

Run:

```bash
rg -n "dev\.zacsweers\.metro|libs\.plugins\.metro" core/domain
```

Expected: matches in the build file, domain rule classes, and `DomainBindings`.

- [ ] **Step 2: Add explicit providers to GameBindings**

Keep the existing abstract bindings and add a companion object:

```kotlin
companion object {
    @Provides
    @SingleIn(AppScope::class)
    internal fun provideShapeGenerator(): ShapeGenerator = ShapeGenerator.default()

    @Provides
    internal fun provideScoreCalculator(): ScoreCalculator = ScoreCalculator()

    @Provides
    internal fun provideGameSessionReducer(
        shapeGenerator: ShapeGenerator,
        scoreCalculator: ScoreCalculator,
    ): GameSessionReducer = GameSessionReducer(shapeGenerator, scoreCalculator)
}
```

- [ ] **Step 3: Remove Metro from domain**

Remove Metro imports and annotations from `GameSessionReducer`, `ScoreCalculator`, and `WeightedShapeGenerator`. Remove `alias(libs.plugins.metro)` from `core/domain/build.gradle.kts`, then delete `DomainBindings.kt`.

- [ ] **Step 4: Remove DomainBindings from platform graphs**

Delete the `DomainBindings` import and list entry from both `AndroidAppGraph` and `NativeAppGraph`. Keep explicit registration of all remaining binding containers unchanged.

- [ ] **Step 5: Verify the architectural assertion is GREEN**

Run:

```bash
if rg -n "dev\.zacsweers\.metro|libs\.plugins\.metro" core/domain; then exit 1; fi
```

Expected: exit code 0 with no matches.

- [ ] **Step 6: Compile both platform graphs**

Run:

```bash
./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL`, proving Metro resolves the moved providers for Android and iOS.

### Task 3: Regression verification and commit

**Files:**
- Verify all files changed in Tasks 1 and 2.

- [ ] **Step 1: Run the relevant test suites**

Run:

```bash
./gradlew :core:domain:allTests :core:data:allTests :feature:game:allTests :feature:root:allTests
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check the complete diff**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and only the planned files are changed.

- [ ] **Step 3: Commit the implementation**

```bash
git add core/domain feature/game/src/commonMain/kotlin/ge/yet/blockblast/feature/game/di/GameBindings.kt composeApp/src/androidMain/kotlin/ge/yet3/blokblast/di/AndroidAppGraph.kt composeApp/src/iosMain/kotlin/ge/yet3/blokblast/di/NativeAppGraph.kt
git commit -m "refactor(domain): remove Metro dependency"
```
