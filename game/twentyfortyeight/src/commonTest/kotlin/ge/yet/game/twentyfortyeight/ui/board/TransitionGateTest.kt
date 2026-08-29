package ge.yet.game.twentyfortyeight.ui.board

import ge.yet.game.twentyfortyeight.ui.motion.MotionPolicy
import ge.yet.game.twentyfortyeight.ui.motion.normalTransitionDurationMs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.MoveInput
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.RngState
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.RuntimeTile
import ge.yet.game.twentyfortyeight.engine.SpawnPolicy
import ge.yet.game.twentyfortyeight.engine.TileId
import ge.yet.game.twentyfortyeight.engine.TileValue
import ge.yet.game.twentyfortyeight.engine.UndoTileMotion
import ge.yet.game.twentyfortyeight.engine.UndoTransition
import ge.yet.game.twentyfortyeight.store.VisualTransition
import ge.yet.game.uikit.theme.LogicaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TransitionGateTest {
    @Test
    fun `Move Reverse and Crossfade variants all render and complete`() = runComposeUiTest {
        val move = changedMove()
        val transition = mutableStateOf<VisualTransition>(VisualTransition.Move(41L, move))
        val completed = mutableListOf<Long>()
        setContent {
            LogicaTheme(darkTheme = false) {
                VisualTransitionView(
                    transition = transition.value,
                    policy = MotionPolicy.Normal,
                    onCompleted = { completed += it },
                    modifier = Modifier.size(320.dp),
                )
            }
        }
        waitForIdle()

        transition.value = VisualTransition.Undo(
            transitionId = 42L,
            transition = UndoTransition.Reverse(
                beforeBoard = move.finalBoard,
                restoredBoard = move.beforeBoard,
                motions = move.motions.map { motion ->
                    UndoTileMotion(
                        sourceId = motion.outcomeId,
                        source = motion.target,
                        target = motion.source,
                        restoredId = motion.sourceId,
                    )
                },
            ),
        )
        waitForIdle()

        transition.value = crossfade(43L)
        waitForIdle()

        assertEquals(listOf(41L, 42L, 43L), completed)
    }

    @Test
    fun `normal transition completes once for each increasing ID`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val completed = mutableListOf<Long>()
        val transition = mutableStateOf(crossfade(id = 7L))

        setContent {
            VisualTransitionView(
                transition = transition.value,
                policy = MotionPolicy.Normal,
                onCompleted = { completed += it },
            )
        }
        mainClock.advanceTimeBy(normalTransitionDurationMs - 20L)
        assertTrue(completed.isEmpty())

        mainClock.advanceTimeByFrame()
        assertEquals(listOf(7L), completed)
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(7L), completed)

        transition.value = crossfade(id = 8L)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(normalTransitionDurationMs + 32L)
        assertEquals(listOf(7L, 8L), completed)
    }

    @Test
    fun `reduced transition completes once and stale ID cannot replay`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val completed = mutableListOf<Long>()
        val transition = mutableStateOf(crossfade(id = 11L))

        setContent {
            VisualTransitionView(
                transition = transition.value,
                policy = MotionPolicy.Reduced,
                onCompleted = { completed += it },
            )
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MotionPolicy.Reduced.transitionDurationMs.toLong())
        assertEquals(listOf(11L), completed)

        transition.value = crossfade(id = 10L)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MotionPolicy.Reduced.transitionDurationMs.toLong())
        assertEquals(listOf(11L), completed)
    }

    private fun crossfade(id: Long): VisualTransition = VisualTransition.Undo(
        transitionId = id,
        transition = UndoTransition.Crossfade(
            beforeBoard = board(tileId = 1L, value = 2L),
            restoredBoard = board(tileId = 2L, value = 4L),
        ),
    )

    private fun changedMove(): MoveResult.Changed {
        val before = RuntimeBoard.fromTiles(
            listOf(
                RuntimeTile(TileId(1L), TileValue(2L)),
                RuntimeTile(TileId(2L), TileValue(2L)),
                *arrayOfNulls<RuntimeTile>(14),
            ),
        )
        val engine = MoveEngine(
            SpawnPolicy { state, bound ->
                if (bound == 10) 1 to RngState.fromBits(99uL) else (bound - 1) to state
            },
        )
        return assertIs<MoveResult.Changed>(
            engine.apply(
                input = MoveInput(
                    board = before,
                    score = 0L,
                    rng = RngState.fromBits(7uL),
                    nextTileId = 3L,
                ),
                direction = Direction.Left,
                transitionId = 41L,
            ),
        )
    }

    private fun board(tileId: Long, value: Long): RuntimeBoard = RuntimeBoard.fromTiles(
        listOf(
            RuntimeTile(TileId(tileId), TileValue(value)),
            *arrayOfNulls<RuntimeTile>(15),
        ),
    )
}
