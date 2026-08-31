# Fruit Merge Next and Danger Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the Fruit Merge queued-fruit flow, first four fruit illustrations, compact score, drop guide and near-loss feedback.

**Architecture:** Keep deterministic presentation decisions in pure common Kotlin helpers and keep all live fruit rendering in Canvas. Add one screen-owned transient transfer animation driven by an accepted drop and measured header/board geometry; do not add game-engine animation state or change persisted enum names.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Canvas, Material 3, Compose animation core, kotlin.test, Compose UI tests.

---

### Task 1: Reusable score and pure danger presentation models

**Files:**
- Create: `core/uikit/src/commonMain/kotlin/ge/yet/game/uikit/components/score/CompactScoreCard.kt`
- Create: `core/uikit/src/commonTest/kotlin/ge/yet/game/uikit/components/score/CompactScoreTest.kt`
- Create: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeVisualModel.kt`
- Create: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeVisualModelTest.kt`

- [ ] **Step 1: Write failing score and danger tests**

Add tests asserting `compactScore(999) == "999"`, `compactScore(1_000) == "1K"`,
`compactScore(1_250) == "1.2K"`, `compactScore(1_000_000) == "1M"`, and
`compactScore(1_250_000_000) == "1.2B"`. Add danger tests asserting zero intensity
outside the warning band, partial intensity inside it, and crying at the line.

- [ ] **Step 2: Run the focused tests and confirm the missing-symbol failure**

Run: `rtk ./gradlew :game:fruitmerge:allTests --tests '*FruitMergeVisualModelTest*'`

Expected: FAIL because the presentation helpers do not exist.

- [ ] **Step 3: Implement the pure helpers**

Implement the public UIKit `compactScore(value: Long): String` with one truncated
decimal digit and no trailing `.0`, plus a reusable two-cell `CompactScoreCard`
that accepts localized labels and exact values. Implement the Fruit Merge-owned
`dangerVisual(topY: Float, dangerY: Float)` returning
an immutable intensity/crying value over a fixed 0.08 world-unit warning band.

- [ ] **Step 4: Run the focused tests**

Run: `rtk ./gradlew :game:fruitmerge:allTests --tests '*FruitMergeVisualModelTest*'`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit the two presentation-model files with message `feat: model fruit merge visual feedback`.

### Task 2: Flat first-four fruit renderer and danger tears

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitVisualSpec.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/composeResources/values/strings.xml`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitVisualSpecTest.kt`

- [ ] **Step 1: Write failing visual-identity tests**

Assert that the second and fourth legacy enum slots expose CLUSTER/DRUPELETS and
CITRUS/WEDGES visual identities, and retain uniqueness across all levels.

- [ ] **Step 2: Run the visual test and confirm failure**

Run: `rtk ./gradlew :game:fruitmerge:allTests --tests '*FruitVisualSpecTest*'`

Expected: FAIL because the new silhouette/detail values do not exist.

- [ ] **Step 3: Implement the four illustrations**

Replace the first four hard-coded bodies with blueberry calyx geometry, a fixed
raspberry drupelet cluster, a tapered strawberry Path and a rind/wedge lime. Update
their specs to a restrained flat palette, and change the visible Cherry/Plum
resource values to Raspberry/Lime without renaming persisted enum constants.

- [ ] **Step 4: Add danger glow and tears**

Replace the boolean anxious input with the pure danger intensity. Draw one warm
halo before the body and two small cyan tear drops after the eyes when crying;
derive their short movement from the existing bounded face clock.

- [ ] **Step 5: Run module tests**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit the renderer, resources and tests with message `feat: redraw early fruit merge levels`.

### Task 3: Score capsule and reference-style drop guide

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreenTest.kt`

- [ ] **Step 1: Add a compact-score semantics UI assertion**

Render a state above one thousand points and assert the score node displays the
compact value while its content description retains the exact score.

- [ ] **Step 2: Run the UI test and confirm failure**

Run: `rtk ./gradlew :game:fruitmerge:allTests --tests '*FruitMergeScreenTest*'`

Expected: FAIL because the score does not yet expose the new tagged compact cell.

- [ ] **Step 3: Redesign the score strip and next pill**

Build a two-cell Material 3 score capsule with labels and compact values. Rework
the Next card into an outlined horizontal pill with a leading fruit and trailing
chevron/label while preserving its test tag and accessible fruit name.

- [ ] **Step 4: Draw the new guide**

Replace the vertical dashed PathEffect with a coral horizontal rail, symmetric
triangular arrowheads and a descending fixed loop of round dots with decreasing
size/alpha. Keep the danger line visually separate.

- [ ] **Step 5: Run module tests**

Run: `rtk ./gradlew :game:fruitmerge:allTests`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit with message `feat: polish fruit merge HUD and drop guide`.

### Task 4: Queued-fruit transfer animation

**Files:**
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreen.kt`
- Modify: `game/fruitmerge/src/commonMain/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeBoard.kt`
- Modify: `game/fruitmerge/src/commonTest/kotlin/ge/yet/game/fruitmerge/ui/FruitMergeScreenTest.kt`

- [ ] **Step 1: Add a cooldown dispatch test**

Render a playing state with positive `dropCooldownSeconds`, tap the viewport and
assert that the component receives no drop call.

- [ ] **Step 2: Run the UI test and confirm failure**

Run: `rtk ./gradlew :game:fruitmerge:allTests --tests '*FruitMergeScreenTest*'`

Expected: FAIL because viewport input currently remains enabled during cooldown.

- [ ] **Step 3: Gate the drop and create the transfer event**

Include cooldown readiness in `dropEnabled`. Wrap accepted drop dispatch to capture
the old queued level and increment a screen-local event id before calling the
component. Measure the queued fruit center and calculate the board preview target
from existing board bounds.

- [ ] **Step 4: Render the flight**

Add a full-viewport Canvas using one `Animatable<Float>` to draw the captured level
along a quadratic arc for roughly 320 ms. Interpolate radius, hide the board preview
during flight, reveal it at completion, and skip motion under reduced motion.

- [ ] **Step 5: Run module and platform verification**

Run: `rtk ./gradlew :game:fruitmerge:verifyMiniApp :miniapp:bundle:verifyMiniAppBundle :composeApp:compileAndroidMain :androidApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

Commit with message `feat: animate queued fruit into play`.

### Task 5: Final repository checks

**Files:**
- Verify only; no planned source changes.

- [ ] **Step 1: Check formatting and scope**

Run: `rtk git diff --check` and `rtk git status --short`.

Expected: no whitespace errors; pre-existing `settings.gradle.kts` remains the only
unrelated modification.

- [ ] **Step 2: Review requirement coverage**

Confirm the Next pill and flight, blueberry/raspberry/strawberry/lime artwork,
compact score, arrow/dot guide and glowing crying danger state are all present.

- [ ] **Step 3: Report verification**

Report exact commands, platform coverage and the preserved unrelated modification.
