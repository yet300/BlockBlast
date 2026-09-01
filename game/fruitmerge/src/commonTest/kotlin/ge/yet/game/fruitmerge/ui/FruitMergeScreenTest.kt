package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.Vec2
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.fruitmerge.session.PaidAction
import ge.yet.game.fruitmerge.session.PaidActionToken
import ge.yet.game.fruitmerge.session.TutorialStep
import ge.yet.game.uikit.theme.LogicaTheme
import ge.yet.game.uikit.theme.PieceColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FruitMergeScreenTest {
    @Test
    fun `light market palette is derived from the shared Logica design system`() = runComposeUiTest {
        var capturedPalette: FruitMergePalette? = null
        var capturedScheme: androidx.compose.material3.ColorScheme? = null
        setContent {
            LogicaTheme(darkTheme = false) {
                capturedScheme = MaterialTheme.colorScheme
                capturedPalette = rememberFruitMergePalette()
            }
        }
        waitForIdle()

        val palette = assertNotNull(capturedPalette)
        val scheme = assertNotNull(capturedScheme)
        assertEquals(scheme.background, palette.canvas)
        assertEquals(scheme.surfaceVariant, palette.canvasShade)
        assertEquals(scheme.primary, palette.wood)
        assertEquals(scheme.primaryContainer, palette.woodLight)
        assertEquals(scheme.onPrimaryContainer, palette.woodDark)
        assertEquals(scheme.surface, palette.paper)
        assertEquals(scheme.onSurface, palette.ink)
        assertEquals(scheme.tertiary, palette.coral)
        assertEquals(PieceColors[1], palette.leaf)
        assertEquals(scheme.surface, palette.boardCream)
    }

    @Test
    fun `compact layout keeps board and consumable actions reachable`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Board).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Clear).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Shake).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Evolution).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Next).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Shake).performClick()
        assertEquals(1, component.shakeRequests)
        assertEquals(0, component.dropCalls)
    }

    @Test
    fun `shake control is disabled for the full active interval`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(
            playingModel().copy(
                game = playingModel().game.copy(shakeStepsRemaining = 90),
            ),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Shake).assertIsNotEnabled()
    }

    @Test
    fun `next label stays on one line with enlarged system text`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                LogicaTheme(darkTheme = false) {
                    Box(Modifier.size(390.dp, 760.dp)) {
                        FruitMergeScreen(component, {}, {})
                    }
                }
            }
        }

        val layoutResults = mutableListOf<TextLayoutResult>()
        onNodeWithText("NEXT").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layoutResults)
        }

        assertEquals(1, layoutResults.single().lineCount)
    }

    @Test
    fun `market price tag is compact and exact for accessibility`() = runComposeUiTest {
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    MarketPriceTag(score = 1_250, bestScore = 2_000_000)
                }
            }
        }

        onNodeWithText("1.2K").assertIsDisplayed()
        onNodeWithText("2M").assertIsDisplayed()
        onNodeWithContentDescription("Score 1250").assertIsDisplayed()
        onNodeWithContentDescription("Best 2000000").assertIsDisplayed()
    }

    @Test
    fun `game over is an in screen state and new game stays owned by the game component`() = runComposeUiTest {
        val base = playingModel()
        val component = FakeFruitMergeComponent(
            base.copy(
                game = base.game.copy(
                    score = 12_500,
                    bestScore = 20_000,
                    phase = RunPhase.RESULT,
                ),
            ),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Result).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.ResultScore).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.NewGame).performClick()

        assertEquals(1, component.newGameCalls)
        assertEquals(0, component.dropCalls)
    }

    @Test
    fun `wide layout keeps the phone composition centered and width constrained`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(1000.dp, 900.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        val viewport = onNodeWithTag(FruitMergeTestTags.Viewport).fetchSemanticsNode().boundsInRoot
        val board = onNodeWithTag(FruitMergeTestTags.Board).fetchSemanticsNode().boundsInRoot
        val support = onNodeWithTag(FruitMergeTestTags.Support).fetchSemanticsNode().boundsInRoot
        val maximumBoardWidth = with(density) { 560.dp.toPx() }

        assertEquals(viewport.center.x, board.center.x, absoluteTolerance = 1f)
        assertEquals(board.center.x, support.center.x, absoluteTolerance = 1f)
        assertTrue(support.center.y > board.center.y)
        assertTrue(board.width <= maximumBoardWidth + 1f)
    }

    @Test
    fun `exhausted clear delegates the exact paid token to the ad gate`() = runComposeUiTest {
        val token = PaidActionToken(sessionKey = 7, runOrdinal = 3, id = 11, action = PaidAction.CLEAR)
        val component = FakeFruitMergeComponent(
            playingModel().copy(game = playingModel().game.copy(freeClears = 0)),
            clearToken = token,
        )
        var requested: PaidActionToken? = null
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, { requested = it }, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Clear).performClick()

        assertEquals(token, requested)
        assertEquals(1, component.clearRequests)
    }

    @Test
    fun `tap above the board still drops through the full viewport`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(playingModel())
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Viewport).performTouchInput {
            click(Offset(20f, 20f))
        }

        assertEquals(1, component.dropCalls)
        assertEquals(false, component.lastDropDragged)
    }

    @Test
    fun `drop input is disabled while the engine cooldown is active`() = runComposeUiTest {
        val base = playingModel()
        val component = FakeFruitMergeComponent(
            base.copy(game = base.game.copy(dropCooldownSeconds = 0.20f)),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Viewport).performTouchInput {
            click(Offset(20f, 20f))
        }

        assertEquals(0, component.dropCalls)
    }

    @Test
    fun `tutorial stays pass through while skip remains actionable`() = runComposeUiTest {
        val component = FakeFruitMergeComponent(
            playingModel().copy(tutorialStep = TutorialStep.Gesture),
        )
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(Modifier.size(390.dp, 760.dp)) {
                    FruitMergeScreen(component, {}, {})
                }
            }
        }

        onNodeWithTag(FruitMergeTestTags.Tutorial).assertIsDisplayed()
        onNodeWithTag(FruitMergeTestTags.Viewport).performTouchInput {
            click(Offset(40f, 300f))
        }
        onNodeWithTag(FruitMergeTestTags.TutorialSkip).performClick()

        assertEquals(1, component.dropCalls)
        assertEquals(1, component.skipTutorialCalls)
    }

    private fun playingModel(): FruitMergeComponent.Model = FruitMergeComponent.Model(
        game = FruitMergeState(
            bodies = listOf(FruitBody(1, FruitLevel.APPLE, Vec2(0.5f, 0.8f))),
            nextBodyId = 2,
        ),
        initialized = true,
        visible = false,
        tutorialReady = true,
    )
}

private class FakeFruitMergeComponent(
    initial: FruitMergeComponent.Model,
    private val clearToken: PaidActionToken? = null,
) : FruitMergeComponent {
    private val mutableModel = MutableValue(initial)
    override val model: Value<FruitMergeComponent.Model> = mutableModel
    override val presentationEvents: Flow<FruitMergeComponent.PresentationEvent> = emptyFlow()

    var clearRequests = 0
    var shakeRequests = 0
    var dropCalls = 0
    var lastDropDragged: Boolean? = null
    var newGameCalls = 0
    var skipTutorialCalls = 0

    override fun frame(elapsedSeconds: Float) = Unit
    override fun movePreview(x: Float) = Unit
    override fun drop(dragged: Boolean) {
        dropCalls += 1
        lastDropDragged = dragged
    }

    override fun requestClearGate(): PaidActionToken? {
        clearRequests += 1
        return clearToken
    }

    override fun selectClearTarget(id: Long) = Unit
    override fun cancelClear() = Unit
    override fun requestShakeGate(): PaidActionToken? {
        shakeRequests += 1
        return null
    }
    override fun completePaidAction(token: PaidActionToken) = Unit

    override fun newGame() {
        newGameCalls += 1
    }

    override fun skipTutorial() {
        skipTutorialCalls += 1
    }

    override fun completeTutorial() = Unit

    override fun handleBack(): Boolean = false
}
