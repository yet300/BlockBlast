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
    val analyticsReservations: Set<String> = emptySet(),
    val milestoneReservations: Set<Long> = emptySet(),
)

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
) {
    init {
        require(runOrdinal > 0L) { "Run ordinal must be positive: $runOrdinal" }
        require(score >= 0L) { "Score must be non-negative: $score" }
        require(bestScore >= score) { "Best score $bestScore cannot be below current score $score" }
        require(successfulMovesInRun >= 0L) {
            "Successful moves in run must be non-negative: $successfulMovesInRun"
        }
        require(momentumStreak >= 0) { "Momentum streak must be non-negative: $momentumStreak" }
        require(nextTileId > 0L) { "Next tile ID must be positive: $nextTileId" }
    }
}

internal data class RulesState(
    val game: GameState,
    val statistics: GameStatistics,
)
