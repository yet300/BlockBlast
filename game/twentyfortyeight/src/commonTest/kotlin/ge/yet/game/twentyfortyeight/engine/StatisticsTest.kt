package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StatisticsTest {
    @Test
    fun `changed merge updates only its exact cumulative statistics`() {
        val original = rulesState(
            board = runtimeBoardOf(
                2L, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            statistics = GameStatistics(gamesStarted = 1L, highestTileEver = 2L),
        )

        val accepted = GameRules.acceptChanged(original, changedMove(original, Direction.Left))

        assertEquals(1L, accepted.statistics.gamesStarted)
        assertEquals(0L, accepted.statistics.gamesWon)
        assertEquals(0L, accepted.statistics.gamesEndedByGameOver)
        assertEquals(1L, accepted.statistics.successfulMoves)
        assertEquals(1L, accepted.statistics.totalMerges)
        assertEquals(4L, accepted.statistics.totalScoreEarned)
        assertTrue(accepted.statistics.highestTileEver >= 4L)
        assertEquals(0L, accepted.statistics.undoUses)
    }

    @Test
    fun `victory and game over cumulative facts are reserved once`() {
        val nearVictory = rulesState(
            board = runtimeBoardOf(
                1024L, 1024L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
        )
        val victory = GameRules.acceptChanged(nearVictory, changedMove(nearVictory, Direction.Left))
        val continued = GameRules.continueAfterVictory(victory)

        assertTrue(victory.game.facts.victoryReached)
        assertTrue(victory.game.facts.gamesWonRecorded)
        assertTrue(victory.game.facts.reviewReserved)
        assertEquals(1L, victory.statistics.gamesWon)
        assertEquals(1L, continued.statistics.gamesWon)

        val terminalBoard = runtimeBoardOf(
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
            2L, 4L, 2L, 4L,
            4L, 2L, 4L, 2L,
        )
        val terminalCandidate = continued.copy(
            game = continued.game.copy(
                board = terminalBoard,
                nextTileId = terminalBoard.maximumTileId() + 1L,
                undo = null,
                undoLineage = null,
            ),
        )
        val terminal = GameRules.finishIfTerminal(terminalCandidate)

        assertEquals(GamePhase.GameOver, terminal.game.phase)
        assertEquals(1L, terminal.statistics.gamesEndedByGameOver)
        assertEquals(0, terminal.game.momentumStreak)
        assertEquals(terminal, GameRules.finishIfTerminal(terminal))
    }

    @Test
    fun `unchanged and rejected undo update no counters`() {
        val state = rulesState(board = runtimeBoardOf(
            2L, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        ))

        assertSame(state, GameRules.acceptUnchanged(state))
        assertIs<UndoResult.Unavailable>(GameRules.undo(state))
    }

    @Test
    fun `checked counters reject overflow`() {
        val state = rulesState(
            board = runtimeBoardOf(
                2L, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            statistics = GameStatistics(gamesStarted = Long.MAX_VALUE),
        )

        assertFailsWith<ArithmeticException> { GameRules.restart(state, RngState.fromBits(4uL)) }
    }
}
