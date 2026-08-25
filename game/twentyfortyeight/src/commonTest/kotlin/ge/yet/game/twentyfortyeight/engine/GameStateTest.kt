package ge.yet.game.twentyfortyeight.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
        val undoResult = assertIs<UndoResult.Changed>(GameRules.undo(continued))
        val undone = undoResult.state
        val transition = assertIs<UndoTransition.Reverse>(undoResult.transition)

        assertEquals(original.game.board.valueBoard(), undone.game.board.valueBoard())
        assertEquals(original.game.board, undone.game.board)
        assertEquals(original.game.score, undone.game.score)
        assertEquals(original.game.rng, undone.game.rng)
        assertEquals(false, undone.game.facts.victoryAcknowledged)
        assertTrue(undone.game.facts.gamesWonRecorded)
        assertEquals(afterMerge.statistics.copy(undoUses = afterMerge.statistics.undoUses + 1), undone.statistics)
        assertNull(undone.game.undo)
        assertEquals(0, undone.game.momentumStreak)
        assertEquals(continued.game.board, transition.beforeBoard)
        assertEquals(original.game.board, transition.restoredBoard)
        assertEquals(
            listOf(
                UndoTileMotion(
                    sourceId = TileId(3L),
                    source = Position(0, 0),
                    target = Position(0, 0),
                    restoredId = TileId(1L),
                ),
                UndoTileMotion(
                    sourceId = TileId(3L),
                    source = Position(0, 0),
                    target = Position(0, 1),
                    restoredId = TileId(2L),
                ),
            ),
            transition.motions,
        )
    }

    @Test
    fun `undo after process restore emits typed crossfade without persisted lineage`() {
        val original = rulesState(
            board = runtimeBoardOf(
                2L, 2L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
        )
        val afterMove = GameRules.acceptChanged(original, changedMove(original, Direction.Left))
        val processRestored = afterMove.copy(
            game = afterMove.game.copy(undoLineage = null),
        )

        val result = assertIs<UndoResult.Changed>(GameRules.undo(processRestored))
        val transition = assertIs<UndoTransition.Crossfade>(result.transition)

        assertEquals(processRestored.game.board, transition.beforeBoard)
        assertEquals(original.game.board.valueBoard(), transition.restoredBoard.valueBoard())
        assertEquals(original.game.board.valueBoard(), result.state.game.board.valueBoard())
        assertNull(result.state.game.undoLineage)
    }

    @Test
    fun `game state rejects next tile ID at or below the board maximum`() {
        val board = runtimeBoardWithIds(
            9L to 2L, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )

        listOf(9L, 8L).forEach { invalidNextTileId ->
            assertFailsWith<IllegalArgumentException> {
                rulesState(board = board, nextTileId = invalidNextTileId)
            }
        }
    }

    @Test
    fun `accept changed rejects stale board identity without changing authoritative state`() {
        val moveBoard = runtimeBoardWithIds(
            1L to 2L, 2L to 2L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val authoritativeBoard = runtimeBoardWithIds(
            10L to 2L, 11L to 2L, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
        )
        val moveState = rulesState(moveBoard)
        val authoritative = rulesState(authoritativeBoard)
        val staleMove = changedMove(moveState, Direction.Left)

        val failure = assertFailsWith<IllegalArgumentException> {
            GameRules.acceptChanged(authoritative, staleMove)
        }

        assertEquals("Move input board identity does not match authoritative state", failure.message)
        assertSame(authoritativeBoard, authoritative.game.board)
        assertEquals(authoritativeBoard, authoritative.game.board)
        assertNull(authoritative.game.undo)
    }

    @Test
    fun `milestones reserve only the whitelist and never duplicate 2048`() {
        val sixteenK = rulesState(
            board = runtimeBoardOf(
                8192L, 8192L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
        )
        val thirtyTwoK = rulesState(
            board = runtimeBoardOf(
                16384L, 16384L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
        )
        val repeated2048 = rulesState(
            board = runtimeBoardOf(
                1024L, 1024L, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
            ),
            facts = RunFacts(
                victoryReached = true,
                victoryAcknowledged = true,
                gamesWonRecorded = true,
                milestoneReservations = setOf(2048L),
            ),
        )

        val accepted16K = GameRules.acceptChanged(sixteenK, changedMove(sixteenK, Direction.Left))
        val accepted32K = GameRules.acceptChanged(thirtyTwoK, changedMove(thirtyTwoK, Direction.Left))
        val accepted2048 = GameRules.acceptChanged(repeated2048, changedMove(repeated2048, Direction.Left))

        assertEquals(setOf(16384L), accepted16K.game.facts.milestoneReservations)
        assertEquals(emptySet(), accepted32K.game.facts.milestoneReservations)
        assertEquals(setOf(2048L), accepted2048.game.facts.milestoneReservations)
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
    nextTileId: Long = (board.tiles.maxOfOrNull { it?.id?.value ?: 0L } ?: 0L) + 1L,
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
        nextTileId = nextTileId,
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
