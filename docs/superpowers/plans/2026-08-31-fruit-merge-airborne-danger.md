# Fruit Merge Airborne Danger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep newly dropped fruit free of red danger feedback until its first physical contact, without changing saved-game compatibility or game-over rules.

**Architecture:** Add one transient monotonic `hasJoinedPile` flag to `FruitBody`. Physics promotes it only on floor or fruit contact, the engine initializes drop and merge bodies deliberately, persistence restores existing bodies as pile members, and the pure UI danger model gates its existing thresholds on the flag. Side-wall contact remains part of free flight.

**Tech Stack:** Kotlin Multiplatform, common Kotlin tests, Compose Canvas, existing Fruit Merge deterministic physics and snapshot schema.

**Execution constraint:** Work inline in the current `codex/fruit-merge` checkout. Do not create a worktree or Git commit.

---

### Task 1: Gate the pure danger model by contact state

**Files:**
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeVisualModelTest.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeVisualModel.kt`

- [ ] **Step 1: Write the failing visual-model test**

Add an explicit airborne case and pass `hasJoinedPile = true` to the existing threshold cases:

```kotlin
@Test
fun `airborne fruit never shows danger feedback`() {
    val airborne = dangerVisual(
        topY = 0.05f,
        dangerY = 0.10f,
        hasJoinedPile = false,
    )

    assertEquals(DangerVisual(intensity = 0f, crying = false), airborne)
}
```

Update the existing calls to the intended API:

```kotlin
dangerVisual(topY = 0.20f, dangerY = 0.10f, hasJoinedPile = true)
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
rtk ./gradlew :game:fruitmerge:allTests
```

Expected: compilation fails because `dangerVisual` does not accept `hasJoinedPile`.

- [ ] **Step 3: Implement the minimal pure gate**

Change the function to return the shared inactive value before calculating distance:

```kotlin
internal fun dangerVisual(
    topY: Float,
    dangerY: Float,
    hasJoinedPile: Boolean = true,
): DangerVisual {
    if (!hasJoinedPile) return DangerVisual(intensity = 0f, crying = false)
    val distanceBelowLine = topY - dangerY
    val intensity = ((DANGER_WARNING_BAND - distanceBelowLine) / DANGER_WARNING_BAND)
        .coerceIn(0f, 1f)
    return DangerVisual(
        intensity = intensity,
        crying = distanceBelowLine <= DANGER_CRY_THRESHOLD,
    )
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run the same `allTests` task.

Expected: `BUILD SUCCESSFUL`. The temporary default keeps the existing board call source-compatible until the lifecycle is wired in Task 3; Task 3 removes the default so future call sites must choose explicitly.

### Task 2: Make contact a monotonic physics property

**Files:**
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysicsTest.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitBody.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitPhysics.kt`

- [ ] **Step 1: Write failing physics assertions**

Add a free-fall test:

```kotlin
@Test
fun `free fall does not mark a fruit as joined to pile`() {
    val body = FruitBody(
        id = 1,
        level = FruitLevel.BLUEBERRY,
        position = Vec2(0.5f, 0.20f),
    )

    val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

    assertFalse(actual.hasJoinedPile)
}
```

Add a wall-only case that remains outside the pile:

```kotlin
@Test
fun `side wall contact during fall does not join the pile`() {
    val body = FruitBody(
        id = 1,
        level = FruitLevel.BLUEBERRY,
        position = Vec2(0.01f, 0.20f),
        velocity = Vec2(-2f, 0f),
    )

    val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

    assertFalse(actual.hasJoinedPile)
}
```

Add a dedicated floor-contact case so the input is guaranteed to cross the floor constraint in one step:

```kotlin
@Test
fun `floor contact joins the pile`() {
    val body = FruitBody(
        id = 1,
        level = FruitLevel.BLUEBERRY,
        position = Vec2(0.5f, 0.99f),
    )

    val actual = FruitPhysics().step(listOf(body), 1f / 60f).bodies.single()

    assertTrue(actual.hasJoinedPile)
}
```

Extend the overlapping-pair test with:

```kotlin
assertTrue(result.bodies.all(FruitBody::hasJoinedPile))
```

Add the `assertFalse` import.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
rtk ./gradlew :game:fruitmerge:allTests
```

Expected: compilation fails because `FruitBody.hasJoinedPile` does not exist.

- [ ] **Step 3: Add the transient flag**

Append the field so existing constructors retain source compatibility:

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
)
```

- [ ] **Step 4: Promote contact at container boundaries**

In `constrainToContainer`, initialize `var hasJoinedPile = body.hasJoinedPile`, leave it unchanged in both wall branches, set it to `true` only in the floor branch, then include it in the returned copy:

```kotlin
var hasJoinedPile = body.hasJoinedPile

if (x < radius) {
    x = radius
    impact = max(impact, abs(velocityX))
    velocityX = abs(velocityX) * WALL_RESTITUTION
} else if (x > 1f - radius) {
    x = 1f - radius
    impact = max(impact, abs(velocityX))
    velocityX = -abs(velocityX) * WALL_RESTITUTION
}
val floor = FLOOR_Y - radius
if (y > floor) {
    y = floor
    impact = max(impact, abs(velocityY))
    velocityY = -abs(velocityY) * WALL_RESTITUTION
    if (abs(velocityY) < REST_SPEED) velocityY = 0f
    velocityX *= FLOOR_FRICTION
    angularVelocity *= FLOOR_FRICTION
    hasJoinedPile = true
}

return body.copy(
    position = Vec2(x, y),
    velocity = Vec2(velocityX, velocityY),
    angularVelocity = angularVelocity,
    impact = impact.coerceAtMost(MAX_IMPACT),
    hasJoinedPile = hasJoinedPile,
)
```

- [ ] **Step 5: Promote contact for both members of a pair**

Use these assignments in `resolveCirclePair`. Never write `false`, so later bounces and shake impulses cannot erase the state:

```kotlin
bodies[pair.firstIndex] = first.copy(
    position = first.position - correction * inverseMassFirst,
    velocity = firstVelocity,
    impact = max(first.impact, impulseMagnitude),
    hasJoinedPile = true,
)
bodies[pair.secondIndex] = second.copy(
    position = second.position + correction * inverseMassSecond,
    velocity = secondVelocity,
    impact = max(second.impact, impulseMagnitude),
    hasJoinedPile = true,
)
```

- [ ] **Step 6: Run the physics suite and verify GREEN**

Run:

```bash
rtk ./gradlew :game:fruitmerge:allTests
```

Expected: `BUILD SUCCESSFUL` and all physics assertions pass.

### Task 3: Define engine, persistence and renderer initialization

**Files:**
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngineTest.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshotTest.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/engine/FruitMergeEngine.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/persistence/FruitMergeSnapshot.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`

- [ ] **Step 1: Write failing engine lifecycle assertions**

Extend `drop promotes the queued fruit and deterministically refills next`:

```kotlin
assertFalse(dropped.bodies.single().hasJoinedPile)
```

Extend `equal contact merges once and scores the created level`:

```kotlin
assertTrue(next.bodies.single().hasJoinedPile)
```

Add the missing `assertFalse` import.

- [ ] **Step 2: Write the failing restoration assertion**

In `populatedState`, mark both durable pile bodies with `hasJoinedPile = true` so the existing exact round-trip assertion continues to describe durable state:

```kotlin
FruitBody(
    id = 1,
    level = FruitLevel.PLUM,
    position = Vec2(0.42f, 0.74f),
    velocity = Vec2(0.01f, -0.02f),
    hasJoinedPile = true,
)
FruitBody(
    id = 2,
    level = FruitLevel.APPLE,
    position = Vec2(0.63f, 0.79f),
    hasJoinedPile = true,
)
```

Then split the existing round-trip expression into a local and assert the restoration policy:

```kotlin
val restored = FruitMergeSnapshot.from(state).toState(bestScore = state.bestScore)

assertEquals(state, restored)
assertTrue(restored.bodies.all(FruitBody::hasJoinedPile))
```

This establishes that pre-existing saved pile members do not replay an airborne phase after process restoration.

- [ ] **Step 3: Run the tests and verify RED**

Run:

```bash
rtk ./gradlew :game:fruitmerge:allTests
```

Expected: the restoration assertion fails because `toBody()` currently uses the new default `false`. The merge assertion may also fail until initialization is explicit.

- [ ] **Step 4: Initialize engine-created bodies explicitly**

Keep the dropped body airborne:

```kotlin
val body = FruitBody(
    id = state.nextBodyId,
    level = state.previewLevel,
    position = Vec2(state.previewX.coerceIn(radius, 1f - radius), SPAWN_Y),
    hasJoinedPile = false,
)
```

Set merged results as pile members:

```kotlin
created += FruitBody(
    id = nextBodyId,
    level = nextLevel,
    position = (first.position * first.level.mass + second.position * second.level.mass) / totalMass,
    velocity = ((first.velocity * first.level.mass + second.velocity * second.level.mass) / totalMass)
        .clampLength(MAX_MERGE_SPEED),
    angle = (first.angle + second.angle) * 0.5f,
    angularVelocity = ((first.angularVelocity + second.angularVelocity) * 0.5f)
        .coerceIn(-MAX_MERGE_ANGULAR_SPEED, MAX_MERGE_ANGULAR_SPEED),
    impact = 1f,
    hasJoinedPile = true,
)
```

- [ ] **Step 5: Restore snapshot bodies as pile members without changing schema**

Add one constructor argument in `FruitBodySnapshot.toBody()`:

```kotlin
return FruitBody(
    id = id,
    level = restoredLevel,
    position = Vec2(x, y),
    velocity = Vec2(velocityX, velocityY),
    angle = angle,
    angularVelocity = angularVelocity,
    hasJoinedPile = true,
)
```

Do not add a field to `FruitBodySnapshot` and do not increment the snapshot version.

- [ ] **Step 6: Wire the renderer to the lifecycle**

Change the board calculation to:

```kotlin
val danger = dangerVisual(
    topY = body.position.y - body.level.radius,
    dangerY = FruitMergeEngine.DANGER_Y,
    hasJoinedPile = body.hasJoinedPile,
)
```

At the same time, remove `= true` from the `dangerVisual` parameter added in Task 1. The board is now explicit and no compatibility default remains.

- [ ] **Step 7: Run the complete Fruit Merge tests and verify GREEN**

Run:

```bash
rtk ./gradlew :game:fruitmerge:allTests
```

Expected: `BUILD SUCCESSFUL` and all new lifecycle assertions pass.

### Task 4: Regression and platform acceptance

**Files:**
- Verify only; no additional source file should be required.

- [ ] **Step 1: Run module and MiniApp verification**

```bash
rtk ./gradlew :game:fruitmerge:verifyMiniApp :miniapp:bundle:verifyMiniAppBundle
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Compile and package Android**

```bash
rtk ./gradlew :composeApp:compileAndroidMain :androidApp:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a debug APK is assembled.

- [ ] **Step 3: Link the iOS simulator framework**

```bash
rtk ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: `BUILD SUCCESSFUL` with only pre-existing warnings allowed.

- [ ] **Step 4: Check the patch and preserve unrelated work**

```bash
rtk git diff --check
rtk git status --short
```

Expected: no whitespace errors. `settings.gradle.kts` remains an unrelated user-owned modification, and no commit is created.
