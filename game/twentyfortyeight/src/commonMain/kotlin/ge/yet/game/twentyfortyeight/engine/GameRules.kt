package ge.yet.game.twentyfortyeight.engine

internal object GameRules {
    fun newGame(previous: RulesState?, seed: RngState): RulesState {
        val (board, rngAfter) = SpawnPolicy().newBoard(seed)
        val (runtimeBoard, nextTileId) = RuntimeBoard.restore(board)
        val previousStatistics = previous?.statistics ?: GameStatistics()
        val highestTile = board.maxTile()?.value ?: 0L
        val statistics = previousStatistics.copy(
            gamesStarted = checkedCounterAdd(previousStatistics.gamesStarted, 1L),
            highestTileEver = maxOf(previousStatistics.highestTileEver, highestTile),
        )
        val runOrdinal = previous?.game?.runOrdinal?.let { checkedCounterAdd(it, 1L) } ?: 1L
        val bestScore = previous?.game?.bestScore ?: 0L
        return RulesState(
            game = GameState(
                runOrdinal = runOrdinal,
                board = runtimeBoard,
                score = 0L,
                bestScore = bestScore,
                rng = rngAfter,
                undo = null,
                facts = RunFacts(),
                phase = GamePhase.Playing,
                successfulMovesInRun = 0L,
                momentumStreak = 0,
                nextTileId = nextTileId,
            ),
            statistics = statistics,
        )
    }

    fun acceptChanged(state: RulesState, move: MoveResult.Changed): RulesState {
        require(state.game.phase == GamePhase.Playing) { "Cannot accept a move after Game Over" }
        require(state.game.board == move.beforeBoard) {
            "Move input board identity does not match authoritative state"
        }
        require(state.game.score == move.scoreBefore) { "Move input score does not match authoritative state" }
        require(state.game.rng == move.rngBefore) { "Move input RNG does not match authoritative state" }

        val firstVictory = move.victory == VictoryTransition.FirstReached && !state.game.facts.gamesWonRecorded
        val mergeValues = move.merges.map { it.resultValue.value }
        val milestoneReservations = state.game.facts.milestoneReservations +
            mergeValues.filter { it in MILESTONE_VALUES }
        val facts = state.game.facts.copy(
            victoryReached = state.game.facts.victoryReached || firstVictory,
            gamesWonRecorded = state.game.facts.gamesWonRecorded || firstVictory,
            reviewReserved = state.game.facts.reviewReserved || firstVictory,
            bestImprovedInRun = state.game.facts.bestImprovedInRun ||
                move.scoreAfter > state.game.bestScore,
            analyticsReservations = if (firstVictory) {
                state.game.facts.analyticsReservations + VICTORY_ANALYTICS_RESERVATION
            } else {
                state.game.facts.analyticsReservations
            },
            milestoneReservations = milestoneReservations,
        )
        val highestTile = move.finalBoard.values().filterNotNull().maxOrNull() ?: 0L
        val statistics = state.statistics.copy(
            gamesWon = checkedCounterAdd(state.statistics.gamesWon, if (firstVictory) 1L else 0L),
            successfulMoves = checkedCounterAdd(state.statistics.successfulMoves, 1L),
            totalMerges = checkedCounterAdd(state.statistics.totalMerges, move.merges.size.toLong()),
            totalScoreEarned = checkedCounterAdd(state.statistics.totalScoreEarned, move.scoreDelta),
            highestTileEver = maxOf(state.statistics.highestTileEver, highestTile),
        )
        val nextTileId = checkedCounterAdd(move.spawn.id.value, 1L)
        val bestScore = maxOf(state.game.bestScore, move.scoreAfter)
        return RulesState(
            game = state.game.copy(
                board = move.finalBoard,
                score = move.scoreAfter,
                bestScore = bestScore,
                rng = move.rngAfter,
                undo = UndoSnapshot(
                    board = state.game.board.valueBoard(),
                    score = state.game.score,
                    rng = state.game.rng,
                    victoryAcknowledged = state.game.facts.victoryAcknowledged,
                    phase = state.game.phase,
                ),
                undoLineage = UndoLineage(
                    beforeBoard = move.beforeBoard,
                    afterBoard = move.finalBoard,
                    motions = move.motions,
                    restoredNextTileId = state.game.nextTileId,
                ),
                facts = facts,
                successfulMovesInRun = checkedCounterAdd(state.game.successfulMovesInRun, 1L),
                momentumStreak = if (move.merges.isEmpty()) {
                    0
                } else {
                    (state.game.momentumStreak.coerceAtMost(MAX_MOMENTUM) + 1)
                        .coerceAtMost(MAX_MOMENTUM)
                },
                nextTileId = nextTileId,
            ),
            statistics = statistics,
        )
    }

    fun acceptUnchanged(state: RulesState): RulesState = state

    fun undo(state: RulesState): UndoResult {
        val snapshot = state.game.undo ?: return UndoResult.Unavailable
        val lineage = state.game.undoLineage?.takeIf {
            it.afterBoard == state.game.board && it.beforeBoard.valueBoard() == snapshot.board
        }
        val (board, nextTileId, transition) = if (lineage != null) {
            val restoredBoard = lineage.beforeBoard
            Triple(
                restoredBoard,
                lineage.restoredNextTileId,
                UndoTransition.Reverse(
                    beforeBoard = state.game.board,
                    restoredBoard = restoredBoard,
                    motions = lineage.motions.map { motion ->
                        UndoTileMotion(
                            sourceId = motion.outcomeId,
                            source = motion.target,
                            target = motion.source,
                            restoredId = motion.sourceId,
                        )
                    },
                ),
            )
        } else {
            val (restoredBoard, restoredNextTileId) = RuntimeBoard.restore(snapshot.board)
            Triple(
                restoredBoard,
                restoredNextTileId,
                UndoTransition.Crossfade(
                    beforeBoard = state.game.board,
                    restoredBoard = restoredBoard,
                ),
            )
        }
        val restoredState = RulesState(
            game = state.game.copy(
                board = board,
                score = snapshot.score,
                rng = snapshot.rng,
                undo = null,
                undoLineage = null,
                facts = state.game.facts.copy(
                    victoryAcknowledged = snapshot.victoryAcknowledged,
                ),
                phase = snapshot.phase,
                momentumStreak = 0,
                nextTileId = nextTileId,
            ),
            statistics = state.statistics.copy(
                undoUses = checkedCounterAdd(state.statistics.undoUses, 1L),
            ),
        )
        return UndoResult.Changed(restoredState, transition)
    }

    fun continueAfterVictory(state: RulesState): RulesState {
        if (!state.game.facts.victoryReached || state.game.facts.victoryAcknowledged) return state
        return state.copy(
            game = state.game.copy(
                facts = state.game.facts.copy(victoryAcknowledged = true),
            ),
        )
    }

    fun finishIfTerminal(state: RulesState): RulesState {
        if (state.game.phase == GamePhase.GameOver) return state
        if (legalDirections(state.game.board).isNotEmpty()) return state
        return RulesState(
            game = state.game.copy(
                phase = GamePhase.GameOver,
                facts = state.game.facts.copy(
                    analyticsReservations = state.game.facts.analyticsReservations +
                        GAME_OVER_ANALYTICS_RESERVATION,
                ),
                momentumStreak = 0,
            ),
            statistics = state.statistics.copy(
                gamesEndedByGameOver = checkedCounterAdd(state.statistics.gamesEndedByGameOver, 1L),
            ),
        )
    }

    fun restart(state: RulesState, seed: RngState): RulesState = newGame(state, seed)

    fun legalDirections(board: RuntimeBoard): Set<Direction> = Direction.entries
        .filterTo(linkedSetOf()) { direction -> canMove(board, direction) }

    private fun canMove(board: RuntimeBoard, direction: Direction): Boolean {
        board.tiles.forEachIndexed { index, tile ->
            if (tile == null) return@forEachIndexed
            val source = Position.fromIndex(index)
            val target = source.next(direction) ?: return@forEachIndexed
            val targetTile = board[target]
            if (
                targetTile == null ||
                targetTile.value == tile.value && tile.value.value <= TileValue.MAX_MERGE_INPUT
            ) {
                return true
            }
        }
        return false
    }

    private fun checkedCounterAdd(value: Long, delta: Long): Long =
        checkedAdd(value, delta) ?: throw CounterOverflowException()

    private const val MAX_MOMENTUM: Int = 6
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
    private const val VICTORY_ANALYTICS_RESERVATION: String = "victory"
    private const val GAME_OVER_ANALYTICS_RESERVATION: String = "game_over"
}

internal class CounterOverflowException : ArithmeticException("2048 counter overflows Long")

private fun Position.next(direction: Direction): Position? {
    val nextRow = when (direction) {
        Direction.Up -> row - 1
        Direction.Down -> row + 1
        Direction.Left, Direction.Right -> row
    }
    val nextColumn = when (direction) {
        Direction.Left -> column - 1
        Direction.Right -> column + 1
        Direction.Up, Direction.Down -> column
    }
    if (nextRow !in 0 until Board.SIZE || nextColumn !in 0 until Board.SIZE) return null
    return Position(nextRow, nextColumn)
}
