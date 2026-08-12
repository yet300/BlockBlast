package ge.yet.game.domain.engine

import ge.yet.game.domain.model.ClearEvent
import ge.yet.game.domain.model.FeedbackEvent
import ge.yet.game.domain.model.GameEvent
import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Piece
import ge.yet.game.domain.model.PointsEvent
import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.model.Position
import ge.yet.game.domain.model.RoundLayoutSource
import ge.yet.game.domain.model.RoundStartInfo

sealed interface GameTransition {
    data class Applied(
        val state: GameState,
        val fact: GameEvent,
    ) : GameTransition

    data class Rejected(
        val reason: PlacementRejection,
    ) : GameTransition
}

enum class PlacementRejection {
    GameAlreadyFinished,
    PieceNotFound,
    OccupiedOrOutOfBounds,
    ReviveUnavailable,
}

data class RoundStartTransition(
    val state: GameState,
    val info: RoundStartInfo,
)

/** Stateless gameplay rules. Every mutation is represented by a returned snapshot. */
class GameSessionReducer(
    private val shapeGenerator: ShapeGenerator,
    private val scoreCalculator: ScoreCalculator,
) {

    fun startNewGame(
        previousState: GameState = GameState(),
        seed: Long? = null,
        bestScore: Long = previousState.bestScore,
        allowStarterLayout: Boolean = false,
    ): RoundStartTransition {
        val startingRound = StarterLayoutGenerator(shapeGenerator).generate(
            seed = seed,
            enabled = allowStarterLayout,
        )
        val initialCursor = maxOf(
            previousState.nextPieceId,
            previousState.currentPieces.maxOfOrNull(Piece::pieceId) ?: 0L,
        )
        val pieces = createPieces(startingRound.shapes, initialCursor)
        val starterLayout = startingRound.starterLayout
        return RoundStartTransition(
            state = GameState(
                grid = startingRound.grid,
                bestScore = bestScore,
                currentPieces = pieces.items,
                bestAtRoundStart = bestScore,
                nextPieceId = pieces.lastId,
                nextRandomSeed = seed?.plus(1),
            ),
            info = RoundStartInfo(
                layoutSource = if (starterLayout == null) {
                    RoundLayoutSource.EMPTY
                } else {
                    RoundLayoutSource.STARTER
                },
                starterTemplateId = starterLayout?.templateId,
                quarterTurns = starterLayout?.quarterTurns,
                reflectedHorizontally = starterLayout?.reflectedHorizontally,
            ),
        )
    }

    fun restore(saved: GameState, persistedBestScore: Long): GameState {
        val mergedBest = maxOf(persistedBestScore, saved.bestScore)
        return saved.copy(
            bestScore = mergedBest,
            bestAtRoundStart = if (persistedBestScore > saved.bestScore) {
                maxOf(saved.bestAtRoundStart, persistedBestScore)
            } else {
                saved.bestAtRoundStart
            },
            nextPieceId = maxOf(
                saved.nextPieceId,
                saved.currentPieces.maxOfOrNull(Piece::pieceId) ?: 0L,
            ),
        )
    }

    fun restoreResult(saved: GameState): GameState = saved.copy(
        grid = Grid(saved.grid.cells.copyOf()),
        nextPieceId = maxOf(
            saved.nextPieceId,
            saved.currentPieces.maxOfOrNull(Piece::pieceId) ?: 0L,
        ),
    )

    fun markReviewPromptFired(state: GameState): GameState =
        if (state.reviewPromptFiredThisRound) state
        else state.copy(reviewPromptFiredThisRound = true)

    fun seedBestScore(state: GameState, persistedBestScore: Long): GameState =
        if (persistedBestScore <= state.bestScore) {
            state
        } else {
            state.copy(
                bestScore = persistedBestScore,
                bestAtRoundStart = maxOf(state.bestAtRoundStart, persistedBestScore),
            )
        }

    fun canPlace(shape: Polyomino, x: Int, y: Int, grid: Grid): Boolean {
        for (cell in shape.cells) {
            val gridX = x + cell.x
            val gridY = y + cell.y
            if (!grid.inBounds(gridX, gridY) || !grid.isEmpty(gridX, gridY)) return false
        }
        return true
    }

    fun place(state: GameState, pieceId: Long, x: Int, y: Int): GameTransition {
        if (state.isGameOver) {
            return GameTransition.Rejected(PlacementRejection.GameAlreadyFinished)
        }
        val piece = state.currentPieces.firstOrNull { it.pieceId == pieceId }
            ?: return GameTransition.Rejected(PlacementRejection.PieceNotFound)
        if (!canPlace(piece.shape, x, y, state.grid)) {
            return GameTransition.Rejected(PlacementRejection.OccupiedOrOutOfBounds)
        }

        val absoluteCells = piece.shape.cells.map { Position(x + it.x, y + it.y) }
        var grid = state.grid.withCells(absoluteCells, piece.colorId)
        val placementPoints = scoreCalculator.placementPoints(piece.shape)

        val fullRows = (0 until Grid.SIZE).filter { row ->
            (0 until Grid.SIZE).all { column -> !grid.isEmpty(column, row) }
        }
        val fullColumns = (0 until Grid.SIZE).filter { column ->
            (0 until Grid.SIZE).all { row -> !grid.isEmpty(column, row) }
        }
        val clearedCells = buildSet {
            for (row in fullRows) for (column in 0 until Grid.SIZE) add(Position(column, row))
            for (column in fullColumns) for (row in 0 until Grid.SIZE) add(Position(column, row))
        }
        val lineCount = fullRows.size + fullColumns.size
        val isCrossClear = fullRows.isNotEmpty() && fullColumns.isNotEmpty()
        if (clearedCells.isNotEmpty()) grid = grid.clearedAt(clearedCells)
        val isBoardEmpty = clearedCells.isNotEmpty() && grid.isBoardEmpty()

        val (combo, movesWithoutClear) = if (lineCount > 0) {
            state.comboLevel + 1 to 0
        } else {
            val misses = state.movesWithoutClear.coerceAtLeast(0) + 1
            if (misses >= GameState.COMBO_RESET_MISS_COUNT) 0 to 0
            else state.comboLevel to misses
        }

        val clearPoints = scoreCalculator.clearPoints(lineCount, combo)
        val allClearPoints = scoreCalculator.allClearBonus(lineCount, isBoardEmpty)
        val totalPoints = placementPoints + clearPoints + allClearPoints
        val score = state.score + totalPoints
        val remaining = state.currentPieces.filterNot { it.pieceId == pieceId }
        val nextSeed = if (remaining.isEmpty()) state.nextRandomSeed?.plus(1) else state.nextRandomSeed
        val generated = if (remaining.isEmpty()) {
            createPieces(shapeGenerator.nextTray(grid, state.nextRandomSeed), state.nextPieceId)
        } else {
            PieceBatch(remaining, state.nextPieceId)
        }
        val gameOver = !anyPieceFits(generated.items, grid)
        val clearedList = clearedCells.toList()
        val feedback = selectVoiceFeedback(
            linesCount = lineCount,
            isCrossClear = isCrossClear,
            isBoardEmpty = isBoardEmpty,
            comboLevel = combo,
        )
        val nextState = state.copy(
            grid = grid,
            score = score,
            bestScore = maxOf(state.bestScore, score),
            comboLevel = combo,
            movesWithoutClear = movesWithoutClear,
            currentPieces = generated.items,
            isGameOver = gameOver,
            lastClearedCells = if (clearedList.isNotEmpty()) {
                ClearEvent(clearedList, state.lastClearedCells.nonce + 1)
            } else state.lastClearedCells,
            lastFeedback = feedback?.let {
                FeedbackEvent(it, state.lastFeedback.nonce + 1)
            } ?: state.lastFeedback,
            lastPointsAwarded = if (totalPoints > 0) {
                PointsEvent(totalPoints, state.lastPointsAwarded.nonce + 1)
            } else state.lastPointsAwarded,
            nextPieceId = generated.lastId,
            nextRandomSeed = nextSeed,
        )
        return GameTransition.Applied(
            state = nextState,
            fact = GameEvent.MoveResolved(
                pieceId = piece.pieceId,
                placedCellCount = piece.shape.size,
                clearedCells = clearedList,
                linesCount = lineCount,
                isCrossClear = isCrossClear,
                isBoardEmpty = isBoardEmpty,
                placementPoints = placementPoints,
                clearPoints = clearPoints,
                allClearPoints = allClearPoints,
                totalPoints = totalPoints,
                comboLevel = combo,
                movesWithoutClear = movesWithoutClear,
                feedback = feedback,
                isGameOver = gameOver,
            ),
        )
    }

    fun revive(state: GameState): GameTransition {
        if (!state.isGameOver || state.revivesUsed >= GameState.MAX_REVIVES) {
            return GameTransition.Rejected(PlacementRejection.ReviveUnavailable)
        }
        val pieces = createPieces(shapeGenerator.smallReviveTray(), state.nextPieceId)
        return GameTransition.Applied(
            state = state.copy(
                currentPieces = pieces.items,
                isGameOver = false,
                revivesUsed = state.revivesUsed + 1,
                comboLevel = 0,
                movesWithoutClear = 0,
                nextPieceId = pieces.lastId,
            ),
            fact = GameEvent.GameStarted,
        )
    }

    private fun createPieces(shapes: List<Polyomino>, firstId: Long): PieceBatch {
        var id = firstId
        val pieces = shapes.map { shape ->
            id += 1
            Piece(
                pieceId = id,
                shape = shape,
                colorId = ((id % 6) + 1).toInt(),
            )
        }
        return PieceBatch(pieces, id)
    }

    private fun anyPieceFits(pieces: List<Piece>, grid: Grid): Boolean {
        for (piece in pieces) {
            for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
                if (canPlace(piece.shape, x, y, grid)) return true
            }
        }
        return false
    }

    private data class PieceBatch(
        val items: List<Piece>,
        val lastId: Long,
    )
}
