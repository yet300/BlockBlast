package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioControlPolicyTest {
    @Test
    fun `progress maps two to zero 2048 to one and clamps above`() {
        assertEquals(0f, controlsFor(max = 2L).progress)
        assertEquals(1f, controlsFor(max = 2048L).progress)
        assertEquals(1f, controlsFor(max = 4096L).progress)
    }

    @Test
    fun `danger follows exact sparse and terminal formulas`() {
        val empty = gameStateOf(RuntimeBoard.restore(Board.empty()).first)
        val sparse = gameStateOf(runtimeBoardOf(
            null, null, null, null,
            null, 2L, null, null,
            null, null, null, null,
            null, null, null, null,
        ))
        val terminal = gameStateOf(runtimeBoardOf(
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
        ))

        assertEquals(13f / 32f, AudioControlPolicy.from(empty).danger)
        assertEquals(1f / 32f, AudioControlPolicy.from(sparse).danger)
        assertEquals(1f, AudioControlPolicy.from(terminal).danger)
    }

    @Test
    fun `terminal pressure maps every legal direction count exactly`() {
        assertEquals(1f, AudioControlPolicy.terminalPressure(0))
        assertEquals(0.8f, AudioControlPolicy.terminalPressure(1))
        assertEquals(0.4f, AudioControlPolicy.terminalPressure(2))
        assertEquals(0.15f, AudioControlPolicy.terminalPressure(3))
        assertEquals(0f, AudioControlPolicy.terminalPressure(4))
    }

    @Test
    fun `equal adjacent pairs are counted once horizontally and vertically`() {
        val board = runtimeBoardOf(
            2L, 2L, null, null,
            2L, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        assertEquals(2, AudioControlPolicy.equalAdjacentPairCount(board))
    }

    @Test
    fun `momentum is quantized and capped at six`() {
        assertEquals(0f, AudioControlPolicy.from(gameStateOf(momentumStreak = 0)).momentum)
        assertEquals(5f / 32f, AudioControlPolicy.from(gameStateOf(momentumStreak = 1)).momentum)
        assertEquals(1f, AudioControlPolicy.from(gameStateOf(momentumStreak = 6)).momentum)
        assertEquals(1f, AudioControlPolicy.from(gameStateOf(momentumStreak = 99)).momentum)
    }

    private fun controlsFor(max: Long): AudioControls = AudioControlPolicy.from(
        gameStateOf(runtimeBoardOf(
            max, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )),
    )
}

internal fun gameStateOf(
    board: RuntimeBoard = RuntimeBoard.restore(Board.empty()).first,
    momentumStreak: Int = 0,
): GameState = GameState(
    runOrdinal = 1L,
    board = board,
    score = 0L,
    bestScore = 0L,
    rng = RngState.fromBits(1uL),
    undo = null,
    facts = RunFacts(),
    phase = GamePhase.Playing,
    successfulMovesInRun = 0L,
    momentumStreak = momentumStreak,
    nextTileId = board.tiles.count { it != null }.toLong() + 1L,
)
