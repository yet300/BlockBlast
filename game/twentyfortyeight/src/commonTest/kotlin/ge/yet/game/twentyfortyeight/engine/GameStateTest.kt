package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameStateTest {
    @Test
    fun `new game creates two tiles and a single started-game fact`() {
        val state = GameRules.newGame(previous = null, seed = RngState.fromBits(7uL))

        assertEquals(1L, state.game.runOrdinal)
        assertEquals(2, state.game.board.tiles.count { it != null })
        assertEquals(0L, state.game.score)
        assertEquals(1L, state.statistics.gamesStarted)
        assertEquals(state.game.board.values().filterNotNull().maxOrNull(), state.statistics.highestTileEver)
        assertNull(state.game.undo)
        assertEquals(3L, state.game.nextTileId)
    }

    @Test
    fun `successful move captures exactly one undo and unchanged preserves it`() {
        val original = rulesState(
            board = runtimeBoardOf(
                2L, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            rng = RngState.fromBits(7uL),
        )
        val changed = GameRules.acceptChanged(original, changedMove(original, Direction.Left))

        assertEquals(original.game.board.valueBoard(), changed.game.undo?.board)
        assertEquals(original.game.score, changed.game.undo?.score)
        assertEquals(original.game.rng, changed.game.undo?.rng)

        val preserved = GameRules.acceptUnchanged(changed)
        assertSame(changed, preserved)
        assertEquals(changed.game.undo, preserved.game.undo)
    }

    @Test
    fun `undo restores run values but never rolls cumulative facts backward`() {
        val original = rulesState(
            board = runtimeBoardOf(
                4L, 4L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            score = 12L,
            rng = RngState.fromBits(22uL),
            facts = RunFacts(victoryReached = true, victoryAcknowledged = false, gamesWonRecorded = true),
        )
        val afterMerge = GameRules.acceptChanged(original, changedMove(original, Direction.Left))
        val continued = GameRules.continueAfterVictory(afterMerge)
        val undone = GameRules.undo(continued)

        assertEquals(original.game.board.valueBoard(), undone.game.board.valueBoard())
        assertEquals(original.game.score, undone.game.score)
        assertEquals(original.game.rng, undone.game.rng)
        assertEquals(false, undone.game.facts.victoryAcknowledged)
        assertTrue(undone.game.facts.gamesWonRecorded)
        assertEquals(afterMerge.statistics.copy(undoUses = afterMerge.statistics.undoUses + 1), undone.statistics)
        assertNull(undone.game.undo)
        assertEquals(0, undone.game.momentumStreak)
    }

    @Test
    fun `restart replaces run while preserving best and cumulative metadata`() {
        val state = rulesState(
            board = runtimeBoardOf(
                8L, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            score = 80L,
            bestScore = 100L,
            statistics = GameStatistics(gamesStarted = 4L, successfulMoves = 12L, highestTileEver = 128L),
        )

        val restarted = GameRules.restart(state, RngState.fromBits(99uL))

        assertEquals(2L, restarted.game.runOrdinal)
        assertEquals(0L, restarted.game.score)
        assertEquals(100L, restarted.game.bestScore)
        assertEquals(5L, restarted.statistics.gamesStarted)
        assertEquals(12L, restarted.statistics.successfulMoves)
        assertEquals(128L, restarted.statistics.highestTileEver)
        assertNull(restarted.game.undo)
        assertEquals(0, restarted.game.momentumStreak)
    }

    @Test
    fun `values above 2048 remain ordinary playing values`() {
        val state = rulesState(
            board = runtimeBoardOf(
                2048L, 2048L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            facts = RunFacts(victoryReached = true, victoryAcknowledged = true, gamesWonRecorded = true),
        )

        val accepted = GameRules.acceptChanged(state, changedMove(state, Direction.Left))

        assertEquals(4096L, accepted.game.board[Position(0, 0)]?.value?.value)
        assertEquals(GamePhase.Playing, accepted.game.phase)
        assertEquals(0L, accepted.statistics.gamesWon)
    }

    @Test
    fun `merge increments momentum and successful non-merge resets it`() {
        val mergeState = rulesState(
            board = runtimeBoardOf(
                2L, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            momentumStreak = 3,
        )
        val slidingState = rulesState(
            board = runtimeBoardOf(
                null, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            momentumStreak = 3,
        )

        val merged = GameRules.acceptChanged(mergeState, changedMove(mergeState, Direction.Left))
        val slid = GameRules.acceptChanged(slidingState, changedMove(slidingState, Direction.Left))

        assertEquals(4, merged.game.momentumStreak)
        assertEquals(0, slid.game.momentumStreak)
        assertEquals(merged.game.score, merged.game.bestScore)
    }
}

internal fun rulesState(
    board: RuntimeBoard,
    score: Long = 0L,
    bestScore: Long = score,
    rng: RngState = RngState.fromBits(1uL),
    facts: RunFacts = RunFacts(),
    statistics: GameStatistics = GameStatistics(),
    phase: GamePhase = GamePhase.Playing,
    momentumStreak: Int = 0,
): RulesState = RulesState(
    game = GameState(
        runOrdinal = 1L,
        board = board,
        score = score,
        bestScore = bestScore,
        rng = rng,
        undo = null,
        facts = facts,
        phase = phase,
        successfulMovesInRun = 0L,
        momentumStreak = momentumStreak,
        nextTileId = board.tiles.count { it != null }.toLong() + 1L,
    ),
    statistics = statistics,
)

internal fun changedMove(state: RulesState, direction: Direction): MoveResult.Changed =
    MoveEngine(SpawnPolicy()).apply(
        input = MoveInput(
            board = state.game.board,
            score = state.game.score,
            rng = state.game.rng,
            nextTileId = state.game.nextTileId,
            victoryAlreadyReached = state.game.facts.victoryReached,
        ),
        direction = direction,
        transitionId = 1L,
    ) as MoveResult.Changed
