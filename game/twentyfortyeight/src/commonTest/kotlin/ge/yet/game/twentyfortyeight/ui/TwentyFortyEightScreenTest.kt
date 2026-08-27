package ge.yet.game.twentyfortyeight.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import ge.yet.game.twentyfortyeight.component.OverlayComponent
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TwentyFortyEightScreenTest {
    @Test
    fun `playing actions expose enabled state targets and full statistics`() = runComposeUiTest {
        var restartRequests = 0
        var statisticsRequests = 0
        var density = 1f
        setContent {
            LogicaTheme(darkTheme = false) {
                density = LocalDensity.current.density
                PlayingContent(
                    model = playingModel(undoEnabled = false),
                    onDirection = {},
                    onUndo = {},
                    onRestart = { restartRequests += 1 },
                    onStatistics = { statisticsRequests += 1 },
                    onSkipTutorial = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithContentDescription("Undo").assertIsNotEnabled()
        onNodeWithContentDescription("Restart").assertHasClickAction().performClick()
        onNodeWithContentDescription("Statistics")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Best, new best, 4,096",
                ),
            )
            .performClick()
        assertEquals(1, restartRequests)
        assertEquals(1, statisticsRequests)
        onNodeWithContentDescription(
            "Best, new best, 4,096",
            useUnmergedTree = true,
        ).assertIsDisplayed()
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

        setContent {
            LogicaTheme(darkTheme = false) {
                OverlayContent(statisticsOverlay())
            }
        }
        onNodeWithText("Statistics").assertIsDisplayed()
        onNodeWithText("Games started").assertIsDisplayed()
        onNodeWithText("Games won").assertIsDisplayed()
        onNodeWithText("Games ended by game over").assertIsDisplayed()
        onNodeWithText("Successful moves").assertIsDisplayed()
        onNodeWithText("Total merges").assertIsDisplayed()
        onNodeWithText("Total score earned").assertIsDisplayed()
        onNodeWithText("Highest tile ever").assertIsDisplayed()
        onNodeWithText("Undo uses").assertIsDisplayed()
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
        onNodeWithText("Games won").assertIsDisplayed()
        onNodeWithText("Successful moves").assertIsDisplayed()
        onNodeWithText("Total merges").assertIsDisplayed()
        onNodeWithText("Games started").assertDoesNotExist()
        onNodeWithText("Undo uses").assertDoesNotExist()
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
                        onStatistics = {},
                        onSkipTutorial = {},
                        modifier = Modifier.size(800.dp, 479.dp),
                    )
                }
            }
        }

        onNodeWithTag("gameplay_viewport").assertIsDisplayed()
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithText("Skip").performScrollTo().assertIsDisplayed()
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
                    onStatistics = {},
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
        var statisticsRequests = 0
        setContent {
            LogicaTheme(darkTheme = false) {
                PlayingContent(
                    model = model,
                    onDirection = {},
                    onUndo = {},
                    onRestart = {},
                    onStatistics = { statisticsRequests += 1 },
                    onSkipTutorial = {},
                    modifier = Modifier.size(viewport.value.width, viewport.value.height),
                )
            }
        }

        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithContentDescription("Statistics").performClick()
        runOnIdle { viewport.value = DpSize(600.dp, 800.dp) }
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithContentDescription("Statistics").performClick()
        runOnIdle { viewport.value = DpSize(840.dp, 800.dp) }
        onNodeWithTag("game_board").assertIsDisplayed()
        onNodeWithTag("supporting_column").assertIsDisplayed()
        onNodeWithContentDescription("Statistics").performClick()

        assertEquals(3, statisticsRequests)
    }

    private fun statisticsOverlay(): OverlayComponent.Statistics = OverlayComponent.Statistics(
        model = MutableValue(
            OverlayComponent.Model.Statistics(
                gamesStarted = 8L,
                gamesWon = 7L,
                gamesEndedByGameOver = 6L,
                successfulMoves = 5L,
                totalMerges = 4L,
                totalScoreEarned = 4096L,
                highestTileEver = 2048L,
                undoUses = 3L,
            ),
        ),
        onDismiss = {},
    )

    private fun playingModel(
        undoEnabled: Boolean,
        tutorialVisible: Boolean = false,
    ) = PlayingComponent.Model(
        board = board(),
        transition = null,
        score = 4096L,
        bestScore = 4096L,
        undoEnabled = undoEnabled,
        tutorialVisible = tutorialVisible,
        overlay = null,
        persistenceStatus = PlayingComponent.PersistenceStatus.Clean,
    )

    private fun resultModel() = ResultComponent.Model(
        score = 4096L,
        bestScore = 8192L,
        highestTile = 2048L,
        statistics = ResultComponent.SelectedStatistics(
            gamesStarted = 8L,
            gamesWon = 7L,
            gamesEndedByGameOver = 6L,
            successfulMoves = 5L,
            totalMerges = 4L,
            undoUses = 3L,
        ),
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
