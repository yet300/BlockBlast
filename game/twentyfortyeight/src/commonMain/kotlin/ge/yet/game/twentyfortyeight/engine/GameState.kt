package ge.yet.game.twentyfortyeight.engine

internal enum class GamePhase {
    Playing,
    GameOver,
}

internal data class UndoSnapshot(
    val board: Board,
    val score: Long,
    val rng: RngState,
    val victoryAcknowledged: Boolean,
    val phase: GamePhase,
) {
    init {
        require(score >= 0L) { "Undo score must be non-negative: $score" }
    }
}

internal data class RunFacts(
    val victoryReached: Boolean = false,
    val victoryAcknowledged: Boolean = false,
    val gamesWonRecorded: Boolean = false,
    val reviewReserved: Boolean = false,
    val bestImprovedInRun: Boolean = false,
    val analyticsReservations: Set<String> = emptySet(),
    val milestoneReservations: Set<Long> = emptySet(),
)

internal data class UndoLineage(
    val beforeBoard: RuntimeBoard,
    val afterBoard: RuntimeBoard,
    val motions: List<TileMotion>,
    val restoredNextTileId: Long,
)

internal data class UndoTileMotion(
    val sourceId: TileId,
    val source: Position,
    val target: Position,
    val restoredId: TileId,
)

internal sealed interface UndoTransition {
    data class Reverse(
        val beforeBoard: RuntimeBoard,
        val restoredBoard: RuntimeBoard,
        val motions: List<UndoTileMotion>,
    ) : UndoTransition

    data class Crossfade(
        val beforeBoard: RuntimeBoard,
        val restoredBoard: RuntimeBoard,
    ) : UndoTransition
}

internal sealed interface UndoResult {
    data object Unavailable : UndoResult

    data class Changed(
        val state: RulesState,
        val transition: UndoTransition,
    ) : UndoResult
}

internal data class GameState(
    val runOrdinal: Long,
    val board: RuntimeBoard,
    val score: Long,
    val bestScore: Long,
    val rng: RngState,
    val undo: UndoSnapshot?,
    val facts: RunFacts,
    val phase: GamePhase,
    val successfulMovesInRun: Long,
    val momentumStreak: Int,
    val nextTileId: Long,
    val undoLineage: UndoLineage? = null,
) {
    init {
        require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        require(score >= 0L) { "Score must be non-negative: $score" }
        require(bestScore >= score) { "Best score $bestScore cannot be below current score $score" }
        require(successfulMovesInRun >= 0L) {
            "Successful moves in run must be non-negative: $successfulMovesInRun"
        }
        require(momentumStreak >= 0) { "Momentum streak must be non-negative: $momentumStreak" }
        require(nextTileId > board.maximumTileId()) {
            "Next tile ID $nextTileId must be greater than every board tile ID"
        }
    }
}

internal data class RulesState(
    val game: GameState,
    val statistics: GameStatistics,
)
