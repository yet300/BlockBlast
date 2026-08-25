package ge.yet.game.twentyfortyeight.persistence

import ge.yet.game.miniapp.api.MiniAppSnapshotSpec
import ge.yet.game.twentyfortyeight.diagnostics.ContractCode
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.Board
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameState
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.RngState
import ge.yet.game.twentyfortyeight.engine.RunFacts
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.engine.TileValue
import ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason
import ge.yet.game.twentyfortyeight.engine.UndoSnapshot
import kotlinx.serialization.Serializable

@Serializable
internal data class CurrentGameV1(
    val revision: Long,
    val runOrdinal: Long,
    val phase: String,
    val board: List<Long?>,
    val score: Long,
    val rngAlgorithm: String,
    val rngStateHex: String,
    val undo: UndoV1?,
    val victoryReached: Boolean,
    val victoryAcknowledged: Boolean,
    val gamesWonRecorded: Boolean,
    val reviewReserved: Boolean,
    val analyticsReservations: Set<String>,
    val milestoneReservations: Set<Long>,
    val momentumStreak: Int,
    val successfulMovesInRun: Long,
    val bestMirror: BestScoreV1,
    val statisticsMirror: StatisticsV1,
    val tutorialMirror: TutorialV1,
)

@Serializable
internal data class UndoV1(
    val board: List<Long?>,
    val score: Long,
    val rngAlgorithm: String,
    val rngStateHex: String,
    val victoryAcknowledged: Boolean,
    val phase: String,
)

@Serializable
internal data class BestScoreV1(
    val revision: Long,
    val bestScore: Long,
)

@Serializable
internal data class StatisticsV1(
    val revision: Long,
    val gamesStarted: Long,
    val gamesWon: Long,
    val gamesEndedByGameOver: Long,
    val successfulMoves: Long,
    val totalMerges: Long,
    val totalScoreEarned: Long,
    val highestTileEver: Long,
    val undoUses: Long,
)

@Serializable
internal data class TutorialV1(
    val revision: Long,
    val seen: Boolean,
    val reason: String?,
)

internal data class TutorialState(
    val seen: Boolean,
    val reason: TutorialCompletionReason?,
)

internal class SnapshotValidationException(
    val failure: TwentyFortyEightFailure,
) : IllegalArgumentException(failure.toString())

internal object TwentyFortyEightSchemas {
    const val CurrentGameKey: String = "current_game"
    const val BestScoreKey: String = "best_score"
    const val StatisticsKey: String = "statistics"
    const val TutorialKey: String = "tutorial_seen"

    val currentGame = MiniAppSnapshotSpec(CurrentGameV1.serializer(), currentVersion = 1)
    val bestScore = MiniAppSnapshotSpec(BestScoreV1.serializer(), currentVersion = 1)
    val statistics = MiniAppSnapshotSpec(StatisticsV1.serializer(), currentVersion = 1)
    val tutorial = MiniAppSnapshotSpec(TutorialV1.serializer(), currentVersion = 1)

    fun toDomain(payload: CurrentGameV1): Result<GameState> = validated {
        requireCounter(payload.revision)
        requirePositiveCounter(payload.runOrdinal)
        requireCounter(payload.score)
        requireCounter(payload.successfulMovesInRun)
        requireCounter(payload.momentumStreak.toLong())
        requireSameRevision(payload.revision, payload.bestMirror.revision)
        requireSameRevision(payload.revision, payload.statisticsMirror.revision)
        requireSameRevision(payload.revision, payload.tutorialMirror.revision)

        val valueBoard = decodeBoard(payload.board)
        val (runtimeBoard, nextTileId) = RuntimeBoard.restore(valueBoard)
        val rng = decodeRng(payload.rngAlgorithm, payload.rngStateHex)
        val phase = decodePhase(payload.phase)
        val undo = payload.undo?.let(::decodeUndo)
        val best = toBestScore(payload.bestMirror).getOrThrow()
        toStatistics(payload.statisticsMirror).getOrThrow()
        toTutorial(payload.tutorialMirror).getOrThrow()
        requireSnapshot(payload.milestoneReservations.all { it in MILESTONE_VALUES })

        GameState(
            runOrdinal = payload.runOrdinal,
            board = runtimeBoard,
            score = payload.score,
            bestScore = best,
            rng = rng,
            undo = undo,
            facts = RunFacts(
                victoryReached = payload.victoryReached,
                victoryAcknowledged = payload.victoryAcknowledged,
                gamesWonRecorded = payload.gamesWonRecorded,
                reviewReserved = payload.reviewReserved,
                analyticsReservations = payload.analyticsReservations,
                milestoneReservations = payload.milestoneReservations,
            ),
            phase = phase,
            successfulMovesInRun = payload.successfulMovesInRun,
            momentumStreak = payload.momentumStreak,
            nextTileId = nextTileId,
            undoLineage = null,
        )
    }

    fun toBestScore(payload: BestScoreV1): Result<Long> = validated {
        requireCounter(payload.revision)
        requireCounter(payload.bestScore)
        payload.bestScore
    }

    fun toStatistics(payload: StatisticsV1): Result<GameStatistics> = validated {
        requireCounter(payload.revision)
        listOf(
            payload.gamesStarted,
            payload.gamesWon,
            payload.gamesEndedByGameOver,
            payload.successfulMoves,
            payload.totalMerges,
            payload.totalScoreEarned,
            payload.highestTileEver,
            payload.undoUses,
        ).forEach(::requireCounter)
        if (payload.highestTileEver != 0L) {
            try {
                TileValue(payload.highestTileEver)
            } catch (_: IllegalArgumentException) {
                failInvariant(InvariantCode.UnsupportedTile)
            }
        }
        GameStatistics(
            gamesStarted = payload.gamesStarted,
            gamesWon = payload.gamesWon,
            gamesEndedByGameOver = payload.gamesEndedByGameOver,
            successfulMoves = payload.successfulMoves,
            totalMerges = payload.totalMerges,
            totalScoreEarned = payload.totalScoreEarned,
            highestTileEver = payload.highestTileEver,
            undoUses = payload.undoUses,
        )
    }

    fun toTutorial(payload: TutorialV1): Result<TutorialState> = validated {
        requireCounter(payload.revision)
        val reason = when (payload.reason) {
            null -> null
            TUTORIAL_MOVE -> TutorialCompletionReason.Move
            TUTORIAL_SKIP -> TutorialCompletionReason.Skip
            else -> failContract(ContractCode.SnapshotShape)
        }
        requireSnapshot(payload.seen == (reason != null))
        TutorialState(payload.seen, reason)
    }

    private fun decodeUndo(payload: UndoV1): UndoSnapshot {
        requireCounter(payload.score)
        return UndoSnapshot(
            board = decodeBoard(payload.board),
            score = payload.score,
            rng = decodeRng(payload.rngAlgorithm, payload.rngStateHex),
            victoryAcknowledged = payload.victoryAcknowledged,
            phase = decodePhase(payload.phase),
        )
    }

    private fun decodeBoard(values: List<Long?>): Board {
        requireSnapshot(values.size == Board.CELL_COUNT)
        return try {
            Board.fromValues(values)
        } catch (_: IllegalArgumentException) {
            failContract(ContractCode.SnapshotShape)
        }
    }

    private fun decodeRng(algorithm: String, stateHex: String): RngState {
        if (algorithm != RngState.ALGORITHM) failContract(ContractCode.RngAlgorithm)
        return try {
            RngState(algorithm, stateHex)
        } catch (_: IllegalArgumentException) {
            failContract(ContractCode.SnapshotShape)
        }
    }

    private fun decodePhase(value: String): GamePhase = when (value) {
        PHASE_PLAYING -> GamePhase.Playing
        PHASE_GAME_OVER -> GamePhase.GameOver
        else -> failContract(ContractCode.SnapshotShape)
    }

    private fun requireSameRevision(expected: Long, actual: Long) {
        requireCounter(actual)
        if (actual != expected) failInvariant(InvariantCode.RevisionRegression)
    }

    private fun requirePositiveCounter(value: Long) {
        if (value <= 0L) failInvariant(InvariantCode.NegativeCounter)
    }

    private fun requireCounter(value: Long) {
        if (value < 0L) failInvariant(InvariantCode.NegativeCounter)
    }

    private fun requireSnapshot(condition: Boolean) {
        if (!condition) failContract(ContractCode.SnapshotShape)
    }

    private fun failContract(code: ContractCode): Nothing =
        throw SnapshotValidationException(TwentyFortyEightFailure.ContractViolation(code))

    private fun failInvariant(code: InvariantCode): Nothing =
        throw SnapshotValidationException(TwentyFortyEightFailure.InvariantViolation(code))

    private inline fun <T> validated(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (failure: SnapshotValidationException) {
        Result.failure(failure)
    } catch (_: IllegalArgumentException) {
        Result.failure(
            SnapshotValidationException(
                TwentyFortyEightFailure.ContractViolation(ContractCode.SnapshotShape),
            ),
        )
    }

    private val MILESTONE_VALUES: Set<Long> = setOf(
        128L,
        256L,
        512L,
        1024L,
        2048L,
        4096L,
        8192L,
        16384L,
    )
    private const val PHASE_PLAYING: String = "PLAYING"
    private const val PHASE_GAME_OVER: String = "GAME_OVER"
    private const val TUTORIAL_MOVE: String = "MOVE"
    private const val TUTORIAL_SKIP: String = "SKIP"
}
