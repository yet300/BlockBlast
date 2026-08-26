package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.RuntimeTile
import ge.yet.game.twentyfortyeight.engine.TileId
import ge.yet.game.twentyfortyeight.engine.TileValue
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.board_empty_cell
import ge.yet.game.twentyfortyeight.generated.resources.board_row_summary
import ge.yet.game.twentyfortyeight.generated.resources.board_summary
import ge.yet.game.twentyfortyeight.generated.resources.move_down
import ge.yet.game.twentyfortyeight.generated.resources.move_left
import ge.yet.game.twentyfortyeight.generated.resources.move_right
import ge.yet.game.twentyfortyeight.generated.resources.move_up
import ge.yet.game.uikit.theme.LogicaTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BoardSemanticsTest {
    @Test
    fun `RTL board exposes one row-major summary and no tile descriptions`() = runComposeUiTest {
        var expectedSummary = ""
        setContent {
            LogicaTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ResourceExpectations { summary, _ -> expectedSummary = summary }
                    TwentyFortyEightBoard(
                        model = BoardModel(board()),
                        onDirection = {},
                        modifier = Modifier.size(320.dp),
                    )
                }
            }
        }

        waitForIdle()
        onAllNodesWithContentDescription(expectedSummary).assertCountEquals(1)
        onAllNodesWithContentDescription(
            label = expectedSummary,
            useUnmergedTree = true,
        ).assertCountEquals(1)
        onAllNodes(
            matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("131072", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `RTL custom actions preserve physical direction order`() = runComposeUiTest {
        val received = mutableListOf<Direction>()
        var expectedSummary = ""
        var expectedActionLabels = emptyList<String>()
        setContent {
            LogicaTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ResourceExpectations { summary, actionLabels ->
                        expectedSummary = summary
                        expectedActionLabels = actionLabels
                    }
                    TwentyFortyEightBoard(
                        model = BoardModel(board()),
                        onDirection = { direction: Direction -> received += direction },
                        modifier = Modifier.size(320.dp),
                    )
                }
            }
        }

        waitForIdle()
        val node = onAllNodesWithContentDescription(expectedSummary)[0].fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        assertEquals(expectedActionLabels, actions.map { it.label })

        actions.forEach { action -> assertTrue(action.action()) }

        assertEquals(
            listOf(Direction.Up, Direction.Down, Direction.Left, Direction.Right),
            received,
        )
    }

    @Test
    fun `minimum board fits one through six digit tiles at two hundred percent font scale`() =
        runComposeUiTest {
            val textLayouts = mutableMapOf<Long, TextLayoutResult>()
            var expectedSummary = ""
            setContent {
                LogicaTheme(darkTheme = false) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = 2f),
                    ) {
                        ResourceExpectations { summary, _ -> expectedSummary = summary }
                        TwentyFortyEightBoard(
                            model = BoardModel(board()),
                            onDirection = {},
                            modifier = Modifier.size(240.dp),
                            onTileTextLayout = { value, result -> textLayouts[value] = result },
                        )
                    }
                }
            }

            waitForIdle()
            listOf(2L, 16L, 128L, 1024L, 16384L, 131072L).forEach { value ->
                val result = assertNotNull(textLayouts[value], "No text layout captured for $value")
                assertTrue(!result.didOverflowWidth, "$value overflowed width at 200% font scale")
                assertTrue(!result.didOverflowHeight, "$value overflowed height at 200% font scale")
            }
            onAllNodesWithContentDescription(expectedSummary).assertCountEquals(1)
            assertTrue(expectedSummary.contains("1152921504606846976"))
            onAllNodes(
                matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            ).assertCountEquals(1)
            onAllNodesWithText("131072", useUnmergedTree = true).assertCountEquals(0)
        }

    private fun board(): RuntimeBoard = RuntimeBoard.fromTiles(
        listOf(
            2L, null, 4L, 8L,
            16L, 32L, 64L, 128L,
            256L, 512L, 1024L, 2048L,
            4096L, 1152921504606846976L, 16384L, 131072L,
        ).mapIndexed { index, value ->
            value?.let {
                RuntimeTile(
                    id = TileId(index + 1L),
                    value = TileValue(it),
                )
            }
        },
    )

    @Composable
    private fun ResourceExpectations(onResolved: (String, List<String>) -> Unit) {
        val empty = stringResource(Res.string.board_empty_cell)
        val firstRow = stringResource(Res.string.board_row_summary, 1, "2", empty, "4", "8")
        val secondRow = stringResource(Res.string.board_row_summary, 2, "16", "32", "64", "128")
        val thirdRow = stringResource(
            Res.string.board_row_summary,
            3,
            "256",
            "512",
            "1024",
            "2048",
        )
        val fourthRow = stringResource(
            Res.string.board_row_summary,
            4,
            "4096",
            "1152921504606846976",
            "16384",
            "131072",
        )
        val summary = stringResource(
            Res.string.board_summary,
            firstRow,
            secondRow,
            thirdRow,
            fourthRow,
        )
        val actionLabels = listOf(
            stringResource(Res.string.move_up),
            stringResource(Res.string.move_down),
            stringResource(Res.string.move_left),
            stringResource(Res.string.move_right),
        )
        SideEffect { onResolved(summary, actionLabels) }
    }
}
