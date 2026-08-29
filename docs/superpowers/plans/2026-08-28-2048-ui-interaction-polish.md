# 2048 UI and Interaction Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 2048 statistics surface and split score cards with a compact record-aware presentation, simplify Game Over, make viewport swipes easier, and add a bounded liquid merge effect.

**Architecture:** The engine and persisted `RunFacts` own the irreversible “best improved in this run” fact. Compose derives presentation from Store models, while gesture and merge visuals expose pure bounded calculations for deterministic common tests. Existing domain statistics remain intact; only the Statistics overlay route and cumulative-statistics UI are removed.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Material 3, MVIKotlin, Decompose, kotlinx.serialization, Compose UI test, kotlin.test.

---

## File Map

- `engine/GameState.kt`, `engine/GameRules.kt`: authoritative record-improved run fact.
- `persistence/TwentyFortyEightSchemas.kt`, `persistence/TwentyFortyEightPersistence.kt`: backward-compatible current-game serialization and semantic validation.
- `store/TwentyFortyEightStore.kt`, `store/TwentyFortyEightStoreFactory.kt`: remove Statistics UI intent/state while retaining domain statistics and publish the record fact.
- `component/PlayingComponent.kt`, `component/OverlayComponent.kt`, `component/ResultComponent.kt`: remove Statistics navigation/component and narrow result presentation data.
- `ui/OverlayContent.kt`, `ui/PlayingContent.kt`, `ui/TwentyFortyEightScreen.kt`: remove Statistics UI wiring and the swipe hint.
- `ui/ScoreBestRow.kt`: one non-clickable record-aware score surface.
- `ui/ResultContent.kt`: one scrollable compact Game Over card.
- `ui/SwipeDetector.kt`: lower adaptive threshold and dominance-aware axis locking.
- `ui/MoveTransition.kt`: bounded merge effect descriptions, draw primitives, and source/result transforms.
- `composeResources/values/strings.xml`: remove obsolete Statistics/hint/result-stat strings while preserving accessibility strings still in use.
- Matching `commonTest` files: engine, schema, Store/component, Compose UI, gesture, and transition regression coverage.

### Task 1: Make record improvement an authoritative run fact

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/engine/GameState.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/engine/GameRules.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/engine/GameRulesTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/engine/GameStateTest.kt`

- [ ] **Step 1: Add failing rule tests for strict improvement, tie, Undo, and New Game**

Add tests using the existing board/move helpers. The assertions must be explicit:

```kotlin
@Test
fun `strict best improvement is retained through undo`() {
    val initial = rulesState(score = 0L, bestScore = 4L)
    val changed = changedMove(initial, scoreDelta = 4L, scoreAfter = 8L)

    val accepted = GameRules.acceptChanged(initial, changed)
    assertTrue(accepted.game.facts.bestImprovedInRun)

    val undone = assertIs<UndoResult.Changed>(GameRules.undo(accepted)).state
    assertTrue(undone.game.facts.bestImprovedInRun)
}

@Test
fun `tying best does not mark run as improved`() {
    val initial = rulesState(score = 0L, bestScore = 4L)
    val accepted = GameRules.acceptChanged(
        initial,
        changedMove(initial, scoreDelta = 4L, scoreAfter = 4L),
    )
    assertFalse(accepted.game.facts.bestImprovedInRun)
}

@Test
fun `new game clears record improvement fact`() {
    val previous = rulesState(
        bestScore = 64L,
        facts = RunFacts(bestImprovedInRun = true),
    )
    assertFalse(GameRules.newGame(previous, RngState.fromSeed(7L)).game.facts.bestImprovedInRun)
}
```

- [ ] **Step 2: Run focused engine tests and verify RED**

Run:

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*GameRulesTest' --tests '*GameStateTest'
```

Expected: compilation fails because `RunFacts.bestImprovedInRun` does not exist.

- [ ] **Step 3: Add the fact and set it against the pre-move authoritative best**

Use an immutable, defaulted field:

```kotlin
internal data class RunFacts(
    val victoryReached: Boolean = false,
    val victoryAcknowledged: Boolean = false,
    val gamesWonRecorded: Boolean = false,
    val reviewReserved: Boolean = false,
    val bestImprovedInRun: Boolean = false,
    val analyticsReservations: Set<String> = emptySet(),
    val milestoneReservations: Set<Long> = emptySet(),
)
```

In `acceptChanged()`, add this to the existing `facts.copy` call:

```kotlin
bestImprovedInRun = state.game.facts.bestImprovedInRun ||
    move.scoreAfter > state.game.bestScore,
```

Do not modify the flag in `undo()`. `newGame()` already constructs `RunFacts()` and therefore resets it.

- [ ] **Step 4: Run focused engine tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the engine fact**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/engine/GameState.kt game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/engine/GameRules.kt game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/engine/GameRulesTest.kt game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/engine/GameStateTest.kt
rtk git commit -m "feat: track 2048 run record improvement"
```

### Task 2: Persist the record fact without breaking V1 snapshots

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/persistence/TwentyFortyEightSchemas.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/persistence/TwentyFortyEightPersistence.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/persistence/TwentyFortyEightSchemasTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/persistence/TwentyFortyEightPersistenceTest.kt`

- [ ] **Step 1: Write failing round-trip, legacy-payload, and invalid-combination tests**

Add a round trip with `bestImprovedInRun = true`, decode a literal V1 JSON envelope that omits the new field, and validate isolated recovery for `true` plus zero best:

```kotlin
@Test
fun `current game preserves run record improvement`() {
    val payload = validCurrentGame().copy(
        bestImprovedInRun = true,
        bestMirror = validBest().copy(bestScore = 32L),
        score = 32L,
    )
    assertTrue(TwentyFortyEightSchemas.toDomain(payload).getOrThrow().facts.bestImprovedInRun)
}

@Test
fun `legacy current game without record field defaults false`() {
    val decoded = TwentyFortyEightSchemas.currentGame.decode(legacyCurrentGameEnvelopeWithoutRecordField)
    assertFalse(TwentyFortyEightSchemas.toDomain(requireNotNull(decoded)).getOrThrow().facts.bestImprovedInRun)
}

@Test
fun `improved record with zero best is rejected as contract failure`() {
    val result = TwentyFortyEightSchemas.toDomain(
        validCurrentGame().copy(
            bestImprovedInRun = true,
            bestMirror = validBest().copy(bestScore = 0L),
        ),
    )
    assertIs<TwentyFortyEightFailure.ContractViolation>(
        assertIs<SnapshotValidationException>(result.exceptionOrNull()).failure,
    )
}
```

The persistence integration test must also assert that an invalid current game becomes `game = null`, valid best/statistics/tutorial metadata still load, and the validation failure is present in `LoadResult.Loaded.validationFailures`.

- [ ] **Step 2: Run persistence tests and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*TwentyFortyEightSchemasTest' --tests '*TwentyFortyEightPersistenceTest'
```

Expected: compilation fails on the missing serialized field.

- [ ] **Step 3: Add the defaulted field, semantic validation, and both mappings**

Add to `CurrentGameV1`:

```kotlin
val bestImprovedInRun: Boolean = false,
```

After decoding `bestMirror`, reject only the impossible state:

```kotlin
val best = toBestScore(payload.bestMirror).getOrThrow()
requireSnapshot(!payload.bestImprovedInRun || best > 0L)
```

Map into domain facts:

```kotlin
bestImprovedInRun = payload.bestImprovedInRun,
```

Map out in `GameCommit.toCurrentGameV1()`:

```kotlin
bestImprovedInRun = game.facts.bestImprovedInRun,
```

Keep schema version `1`; the default preserves older JSON payloads.

- [ ] **Step 4: Run persistence tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit persistence compatibility**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/persistence game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/persistence
rtk git commit -m "feat: persist 2048 run record state"
```

### Task 3: Remove the Statistics product surface and swipe hint

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/store/TwentyFortyEightStore.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/store/TwentyFortyEightStoreFactory.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/PlayingComponent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/OverlayComponent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/OverlayContent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/PlayingContent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreen.kt`
- Modify: `game/twentyfortyeight/src/commonMain/composeResources/values/strings.xml`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/store/TwentyFortyEightStoreTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/store/TwentyFortyEightStorePersistenceTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/session/TwentyFortyEightSessionComponentTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/TwentyFortyEightLifecycleIntegrationTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt`

- [ ] **Step 1: Change tests to describe the reduced public UI state space**

Delete tests whose sole subject is opening/restoring/dismissing/replacing Statistics. Update the remaining exhaustive branches so only these overlay states exist:

```kotlin
internal sealed interface OverlayState {
    data object Victory : OverlayState
    data object RestartConfirmation : OverlayState
}
```

Add UI assertions:

```kotlin
onNodeWithText("Swipe anywhere in the game area.").assertDoesNotExist()
onNodeWithText("Statistics").assertDoesNotExist()
```

Retain all tests for `GameStatistics`, storage operations `StatisticsRead`/`StatisticsWrite`, analytics, and result computation.

- [ ] **Step 2: Run affected tests and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*StoreTest' --tests '*StorePersistenceTest' --tests '*SessionComponentTest' --tests '*LifecycleIntegrationTest' --tests '*TwentyFortyEightScreenTest'
```

Expected: old Statistics intent/component/UI remains visible or signatures still require callbacks.

- [ ] **Step 3: Remove only Statistics UI contracts and wiring**

Remove:

```kotlin
TwentyFortyEightStore.Intent.OpenStatistics
OverlayState.Statistics
OverlayConfig.Statistics
PlayingComponent.onStatisticsRequested()
OverlayComponent.Model.Statistics
OverlayComponent.Statistics
```

Delete `openStatistics()`, its executor branch, Statistics dismissal branch, component creation branch, `GameStatistics.toOverlayModel()`, and `StatisticsOverlay()`. Remove `onStatistics` parameters from `PlayingContent`, `ScoreBestRow`, and screen wiring. Remove the supporting hint `Text` and its string resource.

Do not alter `TwentyFortyEightStore.State.statistics`, `ResultSnapshot.statistics`, persistence metadata, diagnostics, or analytics.

- [ ] **Step 4: Run affected tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Confirm no UI Statistics path remains**

```bash
rtk rg -n "OpenStatistics|OverlayState\.Statistics|OverlayConfig\.Statistics|onStatisticsRequested|StatisticsOverlay|supporting_hint" game/twentyfortyeight/src
```

Expected: no matches. Matches for domain `GameStatistics`, diagnostics, persistence, and analytics are allowed outside this expression.

- [ ] **Step 6: Commit surface removal**

```bash
rtk git add game/twentyfortyeight/src
rtk git commit -m "refactor: remove 2048 statistics surface"
```

### Task 4: Replace Score and Best with one record-aware card

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/PlayingComponent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/PlayingContent.kt`
- Rewrite: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/ScoreBestRow.kt`
- Modify: `game/twentyfortyeight/src/commonMain/composeResources/values/strings.xml`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt`

- [ ] **Step 1: Add failing Compose UI tests for all three card states**

Expose `bestImprovedInRun` on `PlayingComponent.Model`, then render models for each state and assert:

```kotlin
// No prior record.
onNodeWithText("128").assertIsDisplayed()
onNodeWithText("Score").assertDoesNotExist()
onNodeWithText("Best").assertDoesNotExist()

// Existing record.
onNodeWithContentDescription("Score, 128").assertIsDisplayed()
onNodeWithContentDescription("Best, 4,096").assertIsDisplayed()
onNodeWithContentDescription("2048 score summary").assertHasNoClickAction()

// Improved record remains visually merged if current score later decreases.
model.value = model.value.copy(score = 64L, bestScore = 256L, bestImprovedInRun = true)
onNodeWithContentDescription("Best, new best, 256").assertIsDisplayed()
onNodeWithText("64").assertDoesNotExist()
onNodeWithContentDescription("Score, 64").assertIsDisplayed()
```

Use distinct values so merged semantics do not make text queries ambiguous.

- [ ] **Step 2: Run the UI test and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*TwentyFortyEightScreenTest'
```

Expected: missing model field and old two-card labels/click semantics.

- [ ] **Step 3: Publish the fact and implement a pure visual state selector**

Add to `PlayingComponent.Model` and mapping:

```kotlin
val bestImprovedInRun: Boolean
// mapping
bestImprovedInRun = state.game?.facts?.bestImprovedInRun == true
```

Use a small closed model in `ScoreBestRow.kt`:

```kotlin
internal enum class ScoreCardState { ScoreOnly, ScoreAndBest, BestOnly }

internal fun scoreCardState(
    bestScore: Long,
    bestImprovedInRun: Boolean,
): ScoreCardState = when {
    bestImprovedInRun -> ScoreCardState.BestOnly
    bestScore > 0L -> ScoreCardState.ScoreAndBest
    else -> ScoreCardState.ScoreOnly
}
```

Render one `Surface` with no `onClick`. Use `AnimatedContent` or bounded alpha/scale values inside that one surface. `ScoreOnly` displays the current score; `ScoreAndBest` displays score, crown, best; `BestOnly` displays crown and best. Visible `Score` and `Best` labels are absent. Provide merged state semantics and invisible semantic child nodes for both current score and best score, including in `BestOnly`. Reduced Motion changes alpha only; normal motion contracts the score section and pulses the crown. Retain the score-delta chip only when it stays inside the same surface and exposes no action.

- [ ] **Step 4: Run UI tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit the unified card**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/PlayingComponent.kt game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/PlayingContent.kt game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/ScoreBestRow.kt game/twentyfortyeight/src/commonMain/composeResources/values/strings.xml game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt
rtk git commit -m "feat: unify 2048 score presentation"
```

### Task 5: Collapse Game Over into one compact scrollable card

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/ResultComponent.kt`
- Rewrite: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/ResultContent.kt`
- Modify: `game/twentyfortyeight/src/commonMain/composeResources/values/strings.xml`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/session/TwentyFortyEightSessionComponentTest.kt`

- [ ] **Step 1: Add failing result-model and compact-height tests**

Narrow the expected model:

```kotlin
data class Model(
    val score: Long,
    val bestScore: Long,
    val highestTile: Long,
)
```

Compose assertions:

```kotlin
onNodeWithText("Game over").assertIsDisplayed()
onNodeWithText("New game").assertHasClickAction()
onNodeWithText("Games won").assertDoesNotExist()
onNodeWithText("Successful moves").assertDoesNotExist()
onNodeWithText("Total merges").assertDoesNotExist()
onNodeWithTag("result_supporting_column").assertDoesNotExist()
```

At `320.dp × 360.dp` and font scale `2f`, scroll the node tagged `result_scroll` and assert `New game` becomes displayed/clickable.

- [ ] **Step 2: Run result tests and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*TwentyFortyEightScreenTest' --tests '*SessionComponentTest'
```

Expected: old selected statistics and two-pane layout remain.

- [ ] **Step 3: Narrow the component model and build one constrained card**

Remove `ResultComponent.SelectedStatistics` and its mapping. Preserve score, best, and highest tile. Replace `AdaptiveGameScaffold` with:

```kotlin
Box(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("result_scroll"),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.game_over),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = model.score.formatScore(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ResultMetric(
                        icon = Crown,
                        description = stringResource(Res.string.best_description, model.bestScore.formatScore()),
                        value = model.bestScore.formatScore(),
                    )
                    ResultMetric(
                        label = stringResource(Res.string.highest_tile),
                        value = model.highestTile.formatScore(),
                    )
                }
                error?.let { code ->
                    Text(
                        text = errorText(code),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                PrimaryTerracottaButton(
                    text = stringResource(Res.string.new_game),
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

Keep the heading focus requester. Use a crown icon plus accessible descriptions. Remove plural helpers used only by result statistics and remove now-unused strings/plurals.

- [ ] **Step 4: Run result tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit compact Game Over**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/component/ResultComponent.kt game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/ResultContent.kt game/twentyfortyeight/src/commonMain/composeResources/values/strings.xml game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight
rtk git commit -m "feat: simplify 2048 game over screen"
```

### Task 6: Make viewport-wide swipes shorter and directionally stable

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/SwipeDetector.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/SwipeDetectorTest.kt`
- Test: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt`

- [ ] **Step 1: Replace diagonal assumptions with failing dominance tests**

Extend configuration and tests:

```kotlin
private val config = SwipeConfig(
    distanceThresholdPx = 28f,
    velocityThresholdPxPerSecond = 480f,
    touchSlopPx = 8f,
    axisDominanceRatio = 1.2f,
)

@Test
fun `ambiguous diagonal remains pending`() {
    assertEquals(
        GestureDecision.PendingTap,
        resolveGesture(
            delta = Offset(30f, 29f),
            velocity = Velocity.Zero,
            cancelled = false,
            enabled = true,
            startRegion = SwipeStartRegion.Gameplay,
            config = config,
        ),
    )
}

@Test
fun `perpendicular noise does not flip dominant axis`() {
    assertMove(Offset(-30f, 18f), Direction.Left)
    assertMove(Offset(17f, -30f), Direction.Up)
}
```

Keep existing cancellation, disabled, scroll-delegation, touch-slop, and invalid-config tests. Add a helper test for the adaptive threshold at compact and expanded viewport sizes.

- [ ] **Step 2: Run swipe tests and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*SwipeDetectorTest' --tests '*TwentyFortyEightScreenTest'
```

Expected: exact diagonal currently resolves horizontally and threshold/velocity defaults are too high.

- [ ] **Step 3: Implement dominance-aware resolution and bounded threshold calculation**

Add validation:

```kotlin
val axisDominanceRatio: Float
require(axisDominanceRatio.isFinite() && axisDominanceRatio > 1f)
```

Extract:

```kotlin
internal fun swipeDistanceThresholdPx(
    widthPx: Int,
    heightPx: Int,
    touchSlopPx: Float,
    maximumPx: Float,
): Float = min(
    max(touchSlopPx, min(widthPx, heightPx) * 0.05f),
    maximumPx,
)
```

Resolve an axis only when its absolute component is at least `axisDominanceRatio` times the perpendicular component. Prefer displacement once distance is crossed; otherwise use velocity for a flick. If neither vector is dominant, return `PendingTap`. Set the maximum distance to `30.dp` and velocity threshold to `480.dp` per second. Preserve the existing one-emission drain loop and vertical support delegation.

- [ ] **Step 4: Run swipe tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit gestures**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/SwipeDetector.kt game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/SwipeDetectorTest.kt game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TwentyFortyEightScreenTest.kt
rtk git commit -m "feat: improve 2048 viewport swipes"
```

### Task 7: Add bounded liquid merge visuals

**Files:**
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/MoveTransition.kt`
- Modify: `game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/TileStylePolicy.kt`
- Create: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/MergeMotionTest.kt`
- Modify: `game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui/TransitionGateTest.kt`

- [ ] **Step 1: Add failing pure visual-model tests**

Expose internal bounded calculations, not rendering internals:

```kotlin
@Test
fun `horizontal source stretches only along travel axis`() {
    val visual = mergeSourceVisual(
        progress = 0.4f,
        spatialProgress = 0.8f,
        from = Position(0, 0),
        to = Position(0, 2),
    )
    assertTrue(visual.scaleX > visual.scaleY)
    assertTrue(visual.scaleX in 0.75f..1.2f)
    assertTrue(visual.scaleY in 0.75f..1.2f)
}

@Test
fun `liquid phases are bounded and finish cleanly`() {
    assertFalse(liquidEffectVisual(0.2f).bridgeVisible)
    assertTrue(liquidEffectVisual(0.48f).bridgeVisible)
    assertTrue(liquidEffectVisual(0.65f).haloAlpha > 0f)
    val end = liquidEffectVisual(1f)
    assertEquals(0f, end.haloAlpha)
    assertEquals(1f, end.resultAlpha)
    assertEquals(1f, end.resultScale)
}

@Test
fun `reduced motion produces no liquid descriptors`() {
    assertTrue(liquidMergeEffects(move, MotionPolicy.Reduced).isEmpty())
}
```

Also assert all values are finite/positive and effect count is at most the merge count, which is bounded by `Board.CELL_COUNT / 2`.

- [ ] **Step 2: Run transition tests and verify RED**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*MergeMotionTest' --tests '*TransitionGateTest'
```

Expected: the liquid model/functions do not exist.

- [ ] **Step 3: Add immutable effect descriptions computed once per transition**

Use focused models:

```kotlin
internal data class LiquidMergeEffect(
    val target: Position,
    val source: Position,
    val resultValue: Long,
)

internal data class LiquidEffectVisual(
    val bridgeVisible: Boolean,
    val bridgeAlpha: Float,
    val bridgeWidthFraction: Float,
    val haloAlpha: Float,
    val haloRadiusFraction: Float,
    val resultAlpha: Float,
    val resultScale: Float,
)
```

Build the list from `MoveResult.Changed.merges` and matching source motions only for `MotionPolicy.Normal`. Require `effects.size <= Board.CELL_COUNT / 2`. Remember this list by transition ID so it is not rebuilt each frame.

Expose the already-computed tile background without duplicating palette logic:

```kotlin
internal fun TileStylePolicy.effectColor(value: Long, theme: TileTheme): Color =
    style(value, theme).background
```

- [ ] **Step 4: Extend per-tile transforms without per-frame composition state**

Change `TransitionTileVisual` to independent axes:

```kotlin
private data class TransitionTileVisual(
    val positionProgress: Float,
    val alpha: Float,
    val scaleX: Float,
    val scaleY: Float,
)
```

For merge sources, calculate travel orientation from `fromIndex`/`toIndex`; stretch along that axis during approach, squash both axes at collision, and fade. For merge results, begin below `1f`, overshoot by at most `0.08f`, and finish exactly at `1f`. All values must be coerced into tested finite bounds. Stable, spawn, fade, and Undo behavior remain unchanged.

- [ ] **Step 5: Draw the bridge and halo with cached primitive geometry**

Wrap the transition layout in a `drawWithCache` modifier. Cache cell centers, tile color, and effect list. In `onDrawBehind`, read the progress provider and draw only:

```kotlin
drawLine(
    color = effectColor.copy(alpha = visual.bridgeAlpha),
    start = sourceCenter,
    end = targetCenter,
    strokeWidth = cellSize * visual.bridgeWidthFraction,
    cap = StrokeCap.Round,
)
drawCircle(
    color = effectColor.copy(alpha = visual.haloAlpha),
    radius = cellSize * visual.haloRadiusFraction,
    center = targetCenter,
    style = Stroke(width = haloStrokeWidth),
)
```

Do not add blur, `RenderEffect`, shaders, bitmaps, draw-time collections, or per-tile coroutines. Ensure tiles render over the bridge/halo. Reduced Motion continues through the existing crossfade path and passes an empty effect list.

- [ ] **Step 6: Run transition tests and full UI tests**

```bash
rtk ./gradlew :game:twentyfortyeight:iosSimulatorArm64Test --tests '*MergeMotionTest' --tests '*TransitionGateTest' --tests '*TwentyFortyEightScreenTest'
```

Expected: PASS.

- [ ] **Step 7: Commit liquid motion**

```bash
rtk git add game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/MoveTransition.kt game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui/TileStylePolicy.kt game/twentyfortyeight/src/commonTest/kotlin/ge/yet/game/twentyfortyeight/ui
rtk git commit -m "feat: add bounded liquid 2048 merges"
```

### Task 8: Run the release verification gate

**Files:**
- Verify only; do not stage `settings.gradle.kts`.

- [ ] **Step 1: Scan removed UI paths and forbidden expensive effects**

```bash
rtk rg -n "OpenStatistics|OverlayState\.Statistics|OverlayConfig\.Statistics|onStatisticsRequested|StatisticsOverlay|supporting_hint" game/twentyfortyeight/src
rtk rg -n "RenderEffect|RuntimeShader|BlurEffect|ImageBitmap" game/twentyfortyeight/src/commonMain/kotlin/ge/yet/game/twentyfortyeight/ui
```

Expected: no matches. Domain/persistence `GameStatistics` remains and is not part of the first expression.

- [ ] **Step 2: Run the complete 2048 multiplatform gate**

```bash
rtk ./gradlew \
  :game:twentyfortyeight:allTests \
  :game:twentyfortyeight:validateMiniAppDependencies \
  :game:twentyfortyeight:compileAndroidMain \
  :game:twentyfortyeight:compileKotlinIosSimulatorArm64 \
  --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify repository hygiene and scope**

```bash
rtk git diff --check
rtk git status --short --branch
rtk git diff --cached --name-only
```

Expected: no whitespace errors, no staged files, and only the pre-existing user-owned `settings.gradle.kts` modification remains outside the implementation commits.

- [ ] **Step 4: Perform manual platform follow-up when devices are available**

Verify on a weak Android device and an iOS simulator/device: short swipes in all directions, support-column vertical scrolling, one move per held gesture, 60/120 Hz merge motion, Reduced Motion crossfade, 200% font scale Game Over scrolling, TalkBack/VoiceOver score semantics, and allocation/frame traces during repeated merges. Record this as unverified in the handoff if no devices are available.

### Task 9: Finalize UI package boundaries and score presentation

**Files:**
- Move production UI into `ui/board`, `ui/common`, `ui/gameplay`, `ui/motion`, `ui/overlay`, `ui/result`, and `ui/screen`.
- Move common UI tests into matching package folders.
- Modify `ui/gameplay/ScoreBestRow.kt` and its Compose UI coverage.

- [ ] **Step 1: Add a failing rendering test for the score container**

Render the score over a distinct parent color, capture its Compose image, and assert the parent color remains visible through the score bounds. Also assert the visible score center aligns with the container center.

- [ ] **Step 2: Remove the score Surface and verify GREEN**

Use a transparent root `Box` with centered content. Preserve record-state animation, accessibility description, traversal order, minimum touch-readable height, and the existing non-clickable contract.

- [ ] **Step 3: Move UI sources and tests by responsibility**

Use actual file moves, update package declarations and imports, and extract only shared UI text formatting into `ui/common`. Do not change Store, component, engine, persistence, or public MiniApp APIs.

- [ ] **Step 4: Run the complete release verification gate again**

Run all 2048 tests, dependency validation, Android/iOS compilation, forbidden-effect scans, `git diff --check`, and repository-scope checks. Stage only the 2048 implementation and these two documentation files.
