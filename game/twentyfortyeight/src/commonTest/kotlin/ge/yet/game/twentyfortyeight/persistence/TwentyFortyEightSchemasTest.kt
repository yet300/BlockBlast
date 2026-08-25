package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.twentyfortyeight.diagnostics.ContractCode
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwentyFortyEightSchemasTest {
    @Test
    fun `four local names and versions are stable`() {
        assertEquals("current_game", TwentyFortyEightSchemas.CurrentGameKey)
        assertEquals("best_score", TwentyFortyEightSchemas.BestScoreKey)
        assertEquals("statistics", TwentyFortyEightSchemas.StatisticsKey)
        assertEquals("tutorial_seen", TwentyFortyEightSchemas.TutorialKey)
        assertEquals(1, TwentyFortyEightSchemas.currentGame.currentVersion)
        assertEquals(1, TwentyFortyEightSchemas.bestScore.currentVersion)
        assertEquals(1, TwentyFortyEightSchemas.statistics.currentVersion)
        assertEquals(1, TwentyFortyEightSchemas.tutorial.currentVersion)
    }

    @Test
    fun `current game payload round trips every persisted field`() {
        val source = currentGameV1(revision = 7L)
        val json = Json.encodeToString(TwentyFortyEightSchemas.currentGame.serializer, source)
        val restored = Json.decodeFromString(TwentyFortyEightSchemas.currentGame.serializer, json)

        assertEquals(source, restored)
        assertEquals(7L, restored.revision)
        assertEquals(16, restored.board.size)
        assertEquals("splitmix64-v1", restored.rngAlgorithm)
        assertNotNull(restored.undo)
        assertTrue(restored.victoryAcknowledged)
        assertEquals(setOf("victory"), restored.analyticsReservations)
        assertEquals(setOf(128L, 2048L), restored.milestoneReservations)
        assertEquals(4, restored.momentumStreak)
        assertEquals("GAME_OVER", restored.phase)
        assertEquals(120L, restored.bestMirror.bestScore)
        assertEquals(9L, restored.statisticsMirror.successfulMoves)
        assertEquals("MOVE", restored.tutorialMirror.reason)
        assertFalse(json.contains("undoLineage"))
        assertFalse(json.contains("TileId"))
    }

    @Test
    fun `absent undo remains absent`() {
        val result = TwentyFortyEightSchemas.toDomain(currentGameV1(undo = null))

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().undo)
    }

    @Test
    fun `board and undo shapes reject invalid lengths and tile values`() {
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1(board = List(15) { 2L })),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1(board = List(16) { 3L })),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(
                currentGameV1(undo = undoV1().copy(board = List(15) { 2L })),
            ),
        )
    }

    @Test
    fun `unknown RNG algorithm and phase are typed contract failures`() {
        assertFailure(
            ContractCode.RngAlgorithm,
            TwentyFortyEightSchemas.toDomain(currentGameV1(rngAlgorithm = "future-rng")),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1(phase = "PAUSED")),
        )
    }

    @Test
    fun `negative revisions and counters are typed invariant failures`() {
        assertFailure(
            InvariantCode.NegativeCounter,
            TwentyFortyEightSchemas.toDomain(currentGameV1(revision = -1L)),
        )
        assertFailure(
            InvariantCode.NegativeCounter,
            TwentyFortyEightSchemas.toDomain(currentGameV1(score = -1L)),
        )
        assertFailure(
            InvariantCode.NegativeCounter,
            TwentyFortyEightSchemas.toStatistics(statisticsV1().copy(totalMerges = -1L)),
        )
    }

    @Test
    fun `unknown analytics reservation is rejected`() {
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(
                currentGameV1().copy(analyticsReservations = setOf("future_event")),
            ),
        )
    }

    @Test
    fun `victory flags must describe a reachable rules state`() {
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1().copy(victoryReached = false)),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1().copy(gamesWonRecorded = false)),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1().copy(reviewReserved = false)),
        )
    }

    @Test
    fun `momentum above the engine maximum is rejected`() {
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toDomain(currentGameV1().copy(momentumStreak = 7)),
        )
    }

    @Test
    fun `tutorial seen and completion reason must agree`() {
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toTutorial(TutorialV1(1L, seen = false, reason = "MOVE")),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toTutorial(TutorialV1(1L, seen = true, reason = null)),
        )
        assertFailure(
            ContractCode.SnapshotShape,
            TwentyFortyEightSchemas.toTutorial(TutorialV1(1L, seen = true, reason = "OTHER")),
        )
    }

    @Test
    fun `valid payload becomes terminal domain state with deterministic runtime IDs`() {
        val game = TwentyFortyEightSchemas.toDomain(currentGameV1()).getOrThrow()

        assertEquals(1L, game.board.tiles.first()?.id?.value)
        assertEquals(3L, game.nextTileId)
        assertEquals(80L, game.score)
        assertEquals(120L, game.bestScore)
        assertEquals(4, game.momentumStreak)
        assertEquals("GameOver", game.phase.name)
        assertTrue(game.facts.gamesWonRecorded)
        assertNull(game.undoLineage)
    }

    private fun assertFailure(expected: Any, result: Result<*>) {
        val failure = assertFailsWith<SnapshotValidationException> { result.getOrThrow() }.failure
        val actual = when (failure) {
            is TwentyFortyEightFailure.ContractViolation -> failure.code
            is TwentyFortyEightFailure.InvariantViolation -> failure.code
            else -> error("Unexpected failure: $failure")
        }
        assertEquals(expected, actual)
    }
}

internal fun currentGameV1(
    revision: Long = 7L,
    phase: String = "GAME_OVER",
    board: List<Long?> = listOf(64L, 128L) + List(14) { null },
    score: Long = 80L,
    rngAlgorithm: String = "splitmix64-v1",
    undo: UndoV1? = undoV1(),
): CurrentGameV1 = CurrentGameV1(
    revision = revision,
    runOrdinal = 3L,
    phase = phase,
    board = board,
    score = score,
    rngAlgorithm = rngAlgorithm,
    rngStateHex = "0123456789abcdef",
    undo = undo,
    victoryReached = true,
    victoryAcknowledged = true,
    gamesWonRecorded = true,
    reviewReserved = true,
    analyticsReservations = setOf("victory"),
    milestoneReservations = setOf(128L, 2048L),
    momentumStreak = 4,
    successfulMovesInRun = 9L,
    bestMirror = BestScoreV1(revision, 120L),
    statisticsMirror = statisticsV1(revision),
    tutorialMirror = TutorialV1(revision, seen = true, reason = "MOVE"),
)

internal fun undoV1(): UndoV1 = UndoV1(
    board = listOf(32L, 64L) + List(14) { null },
    score = 40L,
    rngAlgorithm = "splitmix64-v1",
    rngStateHex = "fedcba9876543210",
    victoryAcknowledged = false,
    phase = "PLAYING",
)

internal fun statisticsV1(revision: Long = 7L): StatisticsV1 = StatisticsV1(
    revision = revision,
    gamesStarted = 4L,
    gamesWon = 1L,
    gamesEndedByGameOver = 1L,
    successfulMoves = 9L,
    totalMerges = 5L,
    totalScoreEarned = 80L,
    highestTileEver = 2048L,
    undoUses = 2L,
)
