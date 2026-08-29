package ge.yet.game.twentyfortyeight.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import ge.yet.game.twentyfortyeight.component.OverlayComponent
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.twentyfortyeight.ui.gameplay.PlayingContent
import ge.yet.game.twentyfortyeight.ui.gameplay.ScoreBestRow
import ge.yet.game.twentyfortyeight.ui.overlay.OverlayContent
import ge.yet.game.twentyfortyeight.ui.result.ResultContent
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TwentyFortyEightScreenTest {
    @Test
    fun `score presentation has no background and centers its content`() = runComposeUiTest {
        val parentColor = Color.Magenta
        setContent {
            LogicaTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .size(320.dp, 80.dp)
                        .background(parentColor),
                ) {
                    ScoreBestRow(
                        score = 128L,
                        bestScore = 0L,
                        bestImprovedInRun = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val card = onNodeWithTag("score_card").fetchSemanticsNode().boundsInRoot
        val score = onNodeWithTag("score_value", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals(card.center.x, score.center.x, absoluteTolerance = 1f)

        val pixels = onNodeWithTag("score_card").captureToImage().toPixelMap()
        assertEquals(parentColor, pixels[pixels.width / 2, 2])
    }

    @Test
    fun `score presentation stays label-free and non-clickable for every record state`() =
        runComposeUiTest {
            val state = mutableStateOf(
                PlayingComponent.Model(
                    board = null,
                    transition = null,
                    score = 128L,
                    bestScore = 0L,
                    bestImprovedInRun = false,
                    gesturesEnabled = false,
                    undoEnabled = false,
                    tutorialVisible = false,
                    overlay = null,
                    persistenceStatus = PlayingComponent.PersistenceStatus.Clean,
                ),
            )
            setContent {
                LogicaTheme(darkTheme = false) {
                    ScoreBestRow(
                        score = state.value.score,
                        bestScore = state.value.bestScore,
                        bestImprovedInRun = state.value.bestImprovedInRun,
                        modifier = Modifier.size(320.dp, 80.dp),
                    )
                }
            }

            onNodeWithTag("score_card").assertHasNoClickAction()
            onNodeWithTag("score_value", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("best_value", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("Score").assertDoesNotExist()
            onNodeWithText("Best").assertDoesNotExist()

            runOnIdle {
                state.value = state.value.copy(score = 256L, bestScore = 4096L)
            }
            waitForIdle()
            onNodeWithTag("score_value", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("best_value", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("best_crown", useUnmergedTree = true).assertIsDisplayed()

            runOnIdle {
                state.value = state.value.copy(
                    score = 8192L,
                    bestScore = 8192L,
                    bestImprovedInRun = true,
                )
            }
            waitForIdle()
            onNodeWithTag("score_value", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag("best_value", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithContentDescription("Score, 8,192. Best, 8,192").assertIsDisplayed()
        }

    @Test
    fun `playing surface exposes neither statistics nor swipe hint`() = runComposeUiTest {
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = playingModel(undoEnabled = false),
                    onDirection = {},
                    onUndo = {},
                    onRestart = {},
                    onSkipTutorial = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithContentDescription("Statistics").assertDoesNotExist()
        onNodeWithText("Statistics").assertDoesNotExist()
        onNodeWithText("Swipe anywhere in the game area.").assertDoesNotExist()
    }

    @Test
    fun `accessibility traversal follows score best board actions and tutorial`() =
        runComposeUiTest {
            setContent {
                LogicaTheme(darkTheme = false) {
                    PlayingContent(
                        model = playingModel(undoEnabled = true, tutorialVisible = true),
                        onDirection = {},
                        onUndo = {},
                        onRestart = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(800.dp, 600.dp),
                    )
                }
            }

            val score = onNodeWithContentDescription(
                "Score, 4,096. Best, 4,096",
            ).fetchSemanticsNode()
            val board = onNode(
                SemanticsMatcher("board summary") { node ->
                    node.config.contains(SemanticsProperties.ContentDescription) &&
                        node.config[SemanticsProperties.ContentDescription]
                            .singleOrNull()
                            ?.startsWith("Board.") == true
                },
            ).fetchSemanticsNode()
            val undo = onNodeWithContentDescription("Undo").fetchSemanticsNode()
            val restart = onNodeWithContentDescription("Restart").fetchSemanticsNode()
            val tutorial = onNodeWithTag("tutorial").fetchSemanticsNode()

            assertEquals(
                listOf(0f, 2f, 3f, 4f, 5f),
                listOf(score, board, undo, restart, tutorial).map {
                    it.config[SemanticsProperties.TraversalIndex]
                },
            )
        }

    @Test
    fun `playing actions expose enabled state and touch targets`() = runComposeUiTest {
        var restartRequests = 0
        var density = 1f
        setContent {
            LogicaTheme(darkTheme = false) {
                density = LocalDensity.current.density
                PlayingContent(
                    model = playingModel(undoEnabled = false),
                    onDirection = {},
                    onUndo = {},
                    onRestart = { restartRequests += 1 },
                    onSkipTutorial = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithContentDescription("Undo").assertIsNotEnabled()
        onNodeWithContentDescription("Restart").assertHasClickAction().performClick()
        assertEquals(1, restartRequests)
        onNodeWithTag("score_card").assertHasNoClickAction()
        onNodeWithText("Move up").assertDoesNotExist()
        onNodeWithText("Move down").assertDoesNotExist()
        onNodeWithText("Move left").assertDoesNotExist()
        onNodeWithText("Move right").assertDoesNotExist()

        val undoBounds = onNodeWithContentDescription("Undo").fetchSemanticsNode().boundsInRoot
        val restartBounds = onNodeWithContentDescription("Restart").fetchSemanticsNode().boundsInRoot
        assertTrue(undoBounds.width >= 48.dp.value * density)
        assertTrue(undoBounds.height >= 48.dp.value * density)
        assertTrue(restartBounds.width >= 48.dp.value * density)
        assertTrue(restartBounds.height >= 48.dp.value * density)
    }

    @Test
    fun `victory and restart confirmation expose typed actions`() = runComposeUiTest {
        var continued = 0
        var victoryRestarted = 0
        var confirmed = 0

        setContent {
            LogicaTheme(darkTheme = true) {
                OverlayContent(
                    OverlayComponent.Victory(
                        model = MutableValue(OverlayComponent.Model.Victory(4096L, 8192L)),
                        onContinue = { continued += 1 },
                        onRestart = { victoryRestarted += 1 },
                        onDismiss = {},
                    ),
                )
            }
        }
        onNodeWithText("Victory").assertIsDisplayed()
        onNodeWithText("Continue").performClick()
        onNodeWithText("Restart").performClick()
        assertEquals(1, continued)
        assertEquals(1, victoryRestarted)

        setContent {
            LogicaTheme(darkTheme = false) {
                OverlayContent(
                    OverlayComponent.RestartConfirmation(
                        model = MutableValue(
                            OverlayComponent.Model.RestartConfirmation(
                                score = 2048L,
                                successfulMovesInRun = 3L,
                            ),
                        ),
                        onConfirm = { confirmed += 1 },
                        onDismiss = {},
                    ),
                )
            }
        }
        onNodeWithText("Restart game?").assertIsDisplayed()
        onNodeWithText("Restart").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun `result and persistence error are model driven`() = runComposeUiTest {
        var newGameRequests = 0
        setContent {
            LogicaTheme(darkTheme = false) {
                ResultContent(
                    model = resultModel(),
                    error = UiErrorCode.ProgressNotSaved,
                    onNewGame = { newGameRequests += 1 },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithText("Game over").assertIsDisplayed()
        onNodeWithText("4,096").assertIsDisplayed()
        onNodeWithText("Progress could not be saved").assertIsDisplayed()
        onNodeWithText("New game").assertHasClickAction().performClick()
        assertEquals(1, newGameRequests)
        onNodeWithText("Games won").assertDoesNotExist()
        onNodeWithText("Successful moves").assertDoesNotExist()
        onNodeWithText("Total merges").assertDoesNotExist()
        onNodeWithText("Games started").assertDoesNotExist()
        onNodeWithText("Undo uses").assertDoesNotExist()
    }

    @Test
    fun `game over stays reachable on a compact screen at two hundred percent font scale`() =
        runComposeUiTest {
            setContent {
                LogicaTheme(darkTheme = true) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = 2f),
                    ) {
                        ResultContent(
                            model = resultModel(),
                            onNewGame = {},
                            modifier = Modifier.size(320.dp, 360.dp),
                        )
                    }
                }
            }

            onNodeWithTag("result_card").assertIsDisplayed()
            onNodeWithTag("result_supporting_column").assertDoesNotExist()
            onNodeWithText("New game").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `compact height support remains reachable at two hundred percent font scale`() = runComposeUiTest {
        setContent {
            LogicaTheme(darkTheme = true) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 2f),
                ) {
                    PlayingContent(
                        model = playingModel(undoEnabled = true, tutorialVisible = true),
                        onDirection = {},
                        onUndo = {},
                        onRestart = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(800.dp, 479.dp),
                    )
                }
            }
        }

        onNodeWithTag("gameplay_viewport").assertIsDisplayed()
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithText("Skip").assertIsDisplayed()
        val boardBounds = onNodeWithTag("game_board").fetchSemanticsNode().boundsInRoot
        val tutorialBounds = onNodeWithTag("tutorial").fetchSemanticsNode().boundsInRoot
        assertTrue(tutorialBounds.top >= boardBounds.top)
        assertTrue(tutorialBounds.bottom <= boardBounds.bottom)
    }

    @Test
    fun `expanded hierarchy enforces board and support column caps`() = runComposeUiTest {
        var density = 1f
        setContent {
            LogicaTheme(darkTheme = false) {
                density = LocalDensity.current.density
                PlayingContent(
                    model = playingModel(undoEnabled = true),
                    onDirection = {},
                    onUndo = {},
                    onRestart = {},
                    onSkipTutorial = {},
                    modifier = Modifier.size(1200.dp, 900.dp),
                )
            }
        }

        onNodeWithTag("gameplay_viewport").assertIsDisplayed()
        val boardWidth = onNodeWithTag("game_board").fetchSemanticsNode().boundsInRoot.width
        val supportWidth = onNodeWithTag("supporting_column").fetchSemanticsNode().boundsInRoot.width
        assertTrue(boardWidth <= 560f * density + 1f)
        assertTrue(supportWidth in (280f * density - 1f)..(360f * density + 1f))
    }

    @Test
    fun `live layout changes retain the same model and callbacks`() = runComposeUiTest {
        val model = playingModel(undoEnabled = true)
        val viewport = mutableStateOf(DpSize(599.dp, 800.dp))
        var restartRequests = 0
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = model,
                    onDirection = {},
                    onUndo = {},
                    onRestart = { restartRequests += 1 },
                    onSkipTutorial = {},
                    modifier = Modifier.size(viewport.value.width, viewport.value.height),
                )
            }
        }

        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        runOnIdle { viewport.value = DpSize(600.dp, 800.dp) }
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        runOnIdle { viewport.value = DpSize(840.dp, 800.dp) }
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithContentDescription("Restart").performClick()
        assertEquals(1, restartRequests)
    }

    @Test
    fun `horizontal swipe from support pane moves the game through full viewport detector`() =
        runComposeUiTest {
            val directions = mutableListOf<Direction>()
            setContent {
                LogicaTheme(darkTheme = false) {
                    PlayingContent(
                        model = playingModel(undoEnabled = true),
                        onDirection = directions::add,
                        onUndo = {},
                        onRestart = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(800.dp, 479.dp),
                    )
                }
            }

            onNodeWithTag("gameplay_viewport").performTouchInput {
                swipe(
                    start = Offset(width * 0.9f, height * 0.5f),
                    end = Offset(width * 0.7f, height * 0.5f),
                    durationMillis = 250L,
                )
            }

            assertEquals(listOf(Direction.Left), directions)
        }

    @Test
    fun `vertical swipe from support pane delegates scrolling without moving the game`() =
        runComposeUiTest {
            val directions = mutableListOf<Direction>()
            setContent {
                LogicaTheme(darkTheme = false) {
                    PlayingContent(
                        model = playingModel(undoEnabled = true, tutorialVisible = true),
                        onDirection = directions::add,
                        onUndo = {},
                        onRestart = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(800.dp, 479.dp),
                    )
                }
            }

            onNodeWithTag("gameplay_viewport").performTouchInput {
                swipe(
                    start = Offset(width * 0.9f, height * 0.8f),
                    end = Offset(width * 0.9f, height * 0.2f),
                    durationMillis = 250L,
                )
            }

            assertTrue(directions.isEmpty())
        }

    @Test
    fun `disabled viewport does not emit swipe moves`() = runComposeUiTest {
        val directions = mutableListOf<Direction>()
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = playingModel(undoEnabled = false, gesturesEnabled = false),
                    onDirection = directions::add,
                    onUndo = {},
                    onRestart = {},
                    onSkipTutorial = {},
                    modifier = Modifier.size(400.dp, 700.dp),
                )
            }
        }

        onNodeWithTag("gameplay_viewport").performTouchInput { swipeLeft() }

        assertTrue(directions.isEmpty())
    }

    @Test
    fun `short deliberate swipe moves from the whole gameplay viewport`() = runComposeUiTest {
        val directions = mutableListOf<Direction>()
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = playingModel(undoEnabled = true),
                    onDirection = directions::add,
                    onUndo = {},
                    onRestart = {},
                    onSkipTutorial = {},
                    modifier = Modifier.size(400.dp, 700.dp),
                )
            }
        }

        onNodeWithTag("gameplay_viewport").performTouchInput {
            swipe(
                start = Offset(width * 0.25f, height * 0.5f),
                end = Offset(width * 0.31f, height * 0.5f),
                durationMillis = 250L,
            )
        }

        assertEquals(listOf(Direction.Right), directions)
    }

    @Test
    fun `board score background and action regions share swipe arbitration`() = runComposeUiTest {
        val directions = mutableListOf<Direction>()
        var restartRequests = 0
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = playingModel(undoEnabled = true),
                    onDirection = directions::add,
                    onUndo = {},
                    onRestart = { restartRequests += 1 },
                    onSkipTutorial = {},
                    modifier = Modifier.size(400.dp, 700.dp),
                )
            }
        }

        onNodeWithTag("game_board").performTouchInput { swipeUp() }
        onNodeWithContentDescription("Restart").performTouchInput { swipeLeft() }
        onNodeWithTag("gameplay_viewport").performTouchInput {
            swipe(
                start = Offset(5f, 5f),
                end = Offset(100f, 5f),
                durationMillis = 250L,
            )
        }
        onNodeWithContentDescription("Restart").performTouchInput { click() }

        assertEquals(
            listOf(Direction.Up, Direction.Left, Direction.Right),
            directions,
        )
        assertEquals(1, restartRequests)
    }

    @Test
    fun `mouse drag follows the same viewport-wide swipe path`() = runComposeUiTest {
        val directions = mutableListOf<Direction>()
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = playingModel(undoEnabled = true),
                    onDirection = directions::add,
                    onUndo = {},
                    onRestart = {},
                    onSkipTutorial = {},
                    modifier = Modifier.size(400.dp, 700.dp),
                )
            }
        }

        onNodeWithTag("gameplay_viewport").performMouseInput {
            moveTo(center)
            press()
            moveTo(Offset(center.x + 100f, center.y))
            release()
        }

        assertEquals(listOf(Direction.Right), directions)
    }

    @Test
    fun `detector is confined to viewport between host chrome regions`() = runComposeUiTest {
        val directions = mutableListOf<Direction>()
        setContent {
            LogicaTheme(darkTheme = false) {
                Column {
                    Box(Modifier.size(400.dp, 50.dp).testTag("host_toolbar"))
                    PlayingContent(
                        model = playingModel(undoEnabled = true),
                        onDirection = directions::add,
                        onUndo = {},
                        onRestart = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(400.dp, 600.dp),
                    )
                    Box(Modifier.size(400.dp, 50.dp).testTag("host_banner"))
                }
            }
        }

        onNodeWithTag("host_toolbar").performTouchInput { swipeLeft() }
        onNodeWithTag("host_banner").performTouchInput { swipeLeft() }
        assertTrue(directions.isEmpty())

        onNodeWithTag("gameplay_viewport").performTouchInput { swipeLeft() }
        assertEquals(listOf(Direction.Left), directions)
    }

    private fun playingModel(
        undoEnabled: Boolean,
        tutorialVisible: Boolean = false,
        gesturesEnabled: Boolean = true,
        score: Long = 4096L,
        bestScore: Long = 4096L,
        bestImprovedInRun: Boolean = false,
    ) = PlayingComponent.Model(
        board = board(),
        transition = null,
        score = score,
        bestScore = bestScore,
        bestImprovedInRun = bestImprovedInRun,
        gesturesEnabled = gesturesEnabled,
        undoEnabled = undoEnabled,
        tutorialVisible = tutorialVisible,
        overlay = null,
        persistenceStatus = PlayingComponent.PersistenceStatus.Clean,
    )

    private fun resultModel() = ResultComponent.Model(
        score = 4096L,
        bestScore = 8192L,
        highestTile = 2048L,
    )

    private fun board(): RuntimeBoard = RuntimeBoard.restore(
        Board.fromValues(
            listOf(
                2L, 4L, 8L, 16L,
                32L, 64L, 128L, 256L,
                512L, 1024L, 2048L, 4096L,
                null, null, null, null,
            ),
        ),
    ).first
}
