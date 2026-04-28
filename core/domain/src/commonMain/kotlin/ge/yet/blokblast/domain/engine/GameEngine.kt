package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.GameEvent
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.GameSaveRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.domain.model.ClearEvent
import ge.yet.blokblast.domain.model.FeedbackEvent
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.model.PointsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single source of truth for game state. Singleton-scoped via Metro.
 *
 * Pure state machine — no UI, no platform code. Communicates outwards through:
 *   - [state] : MutableStateFlow snapshots of the current [GameState]
 *   - [events]: SharedFlow of one-shot effects (animations, sounds)
 */
@SingleIn(AppScope::class)
@Inject
class GameEngine(
    private val shapeGenerator: ShapeGenerator,
    private val scoreCalculator: ScoreCalculator,
    private val saveRepository: GameSaveRepository,
    private val externalScope: CoroutineScope,
) {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var pieceIdCounter: Long = 0
    private var deterministicSeed: Long? = null
    private var saveJob: Job? = null

    // ---------- Lifecycle ----------

    /** Start a fresh game. Pass [seed] for deterministic tests. */
    fun startNewGame(seed: Long? = null, bestScore: Long = _state.value.bestScore) {
        deterministicSeed = seed
        pieceIdCounter = 0
        _state.value = GameState(
            grid = Grid(),
            score = 0,
            bestScore = bestScore,
            comboLevel = 0,
            currentPieces = generateTray(),
            isGameOver = false,
            revivesUsed = 0,
        )
        autoSave()
    }

    /** Restore a previously persisted state. */
    fun restore(state: GameState) {
        _state.value = state
    }

    /**
     * Lift the engine's known best score to [persistedBest] without disturbing
     * the rest of the state. Called when a fresh process learns the user's
     * lifetime best from disk — the engine starts at 0 and otherwise wouldn't
     * know about it, so subsequent `max(currentBest, score)` updates would
     * silently shadow the real high score with whatever the player just scored.
     *
     * No-op if [persistedBest] isn't actually higher.
     */
    fun seedBestScore(persistedBest: Long) {
        val current = _state.value
        if (persistedBest > current.bestScore) {
            _state.value = current.copy(bestScore = persistedBest)
        }
    }

    // ---------- Public API used by UI ----------

    /**
     * Pure check: can this [piece] be placed so that its local origin lands at ([x],[y])?
     * Used during drag-and-drop hover.
     */
    fun canPlace(piece: Piece, x: Int, y: Int): Boolean =
        canPlace(piece.shape, x, y, _state.value.grid)

    fun canPlace(shape: Polyomino, x: Int, y: Int, grid: Grid = _state.value.grid): Boolean {
        for (cell in shape.cells) {
            val gx = x + cell.x
            val gy = y + cell.y
            if (!grid.inBounds(gx, gy)) return false
            if (!grid.isEmpty(gx, gy)) return false
        }
        return true
    }

    /**
     * Place [pieceId] at absolute grid origin ([x],[y]).
     * Returns true on success. Emits all relevant events and updates [state].
     */
    fun placePiece(pieceId: Long, x: Int, y: Int): Boolean {
        val current = _state.value
        if (current.isGameOver) return false
        val piece = current.currentPieces.firstOrNull { it.pieceId == pieceId } ?: return false
        if (!canPlace(piece, x, y)) return false

        // 1. Stamp the piece onto the grid.
        val absoluteCells = piece.shape.cells.map { Position(x + it.x, y + it.y) }
        var newGrid = current.grid.withCells(absoluteCells, piece.colorId)

        // 2. Placement points (1 per block).
        val placementPts = scoreCalculator.placementPoints(piece.shape)

        // 3. Detect full rows & columns. A cell at an intersection is unioned via Set
        //    so it cannot be cleared twice and never causes IndexOutOfBounds.
        val fullRows = (0 until Grid.SIZE).filter { row ->
            (0 until Grid.SIZE).all { col -> !newGrid.isEmpty(col, row) }
        }
        val fullCols = (0 until Grid.SIZE).filter { col ->
            (0 until Grid.SIZE).all { row -> !newGrid.isEmpty(col, row) }
        }
        val clearedCells: Set<Position> = buildSet {
            for (row in fullRows) for (col in 0 until Grid.SIZE) add(Position(col, row))
            for (col in fullCols) for (row in 0 until Grid.SIZE) add(Position(col, row))
        }
        val totalLines = fullRows.size + fullCols.size
        val isCrossClear = fullRows.isNotEmpty() && fullCols.isNotEmpty()

        if (clearedCells.isNotEmpty()) {
            newGrid = newGrid.clearedAt(clearedCells)
        }

        val isBoardEmpty = clearedCells.isNotEmpty() && newGrid.isBoardEmpty()

        // 4. Combo: +1 if cleared, reset to 0 if not.
        val newCombo = if (totalLines > 0) current.comboLevel + 1 else 0

        // 5. Clear points (with current combo level applied).
        val clearPts = scoreCalculator.clearPoints(totalLines, newCombo)
        val newScore = current.score + placementPts + clearPts
        val newBest = maxOf(current.bestScore, newScore)

        // 6. Remove placed piece from tray; refill if empty.
        val remaining = current.currentPieces.filter { it.pieceId != pieceId }
        val nextTray = if (remaining.isEmpty()) generateTray() else remaining

        // 7. Game-over detection: no remaining piece can be placed anywhere.
        val gameOver = !anyPieceFits(nextTray, newGrid)

        // Precompute once — used in both the state copy and the event emissions below.
        val clearedList = if (clearedCells.isNotEmpty()) clearedCells.toList() else null
        val feedback = feedbackFor(fullRows, fullCols, isBoardEmpty)
        val totalPoints = placementPts + clearPts

        val newState = current.copy(
            grid = newGrid,
            score = newScore,
            bestScore = newBest,
            comboLevel = newCombo,
            currentPieces = nextTray,
            isGameOver = gameOver,
            lastClearedCells = if (clearedList != null) {
                ClearEvent(clearedList, current.lastClearedCells.nonce + 1)
            } else current.lastClearedCells,
            lastFeedback = feedback?.let {
                FeedbackEvent(it, current.lastFeedback.nonce + 1)
            } ?: current.lastFeedback,
            lastPointsAwarded = if (totalPoints > 0) {
                PointsEvent(totalPoints, current.lastPointsAwarded.nonce + 1)
            } else current.lastPointsAwarded,
        )
        _state.value = newState

        // 8. Emit events in narrative order via tryEmit (buffer = 16, always room).
        // tryEmit is synchronous and preserves FIFO; launch{emit} could re-order
        // events if the coroutine scheduler interleaves two placePiece calls.
        _events.tryEmit(GameEvent.PiecePlaced(totalPoints))
        if (clearedList != null) {
            _events.tryEmit(
                GameEvent.LinesCleared(
                    clearedCells = clearedList,
                    linesCount = totalLines,
                    isCrossClear = isCrossClear,
                )
            )
            feedback?.let { _events.tryEmit(GameEvent.Feedback(it)) }
            if (newCombo >= 2) _events.tryEmit(GameEvent.ComboActive(newCombo))
        }
        if (gameOver) _events.tryEmit(GameEvent.GameOver)

        autoSave()
        return true
    }

    /**
     * Ad-reward revive. Does NOT touch the grid or score.
     * Replaces the unplaceable tray with 3 guaranteed small pieces and resumes play.
     * No-op if the player has already used all revives.
     */
    fun continueWithSmallBlocks(): Boolean {
        val current = _state.value
        if (!current.isGameOver) return false
        if (current.revivesUsed >= GameState.MAX_REVIVES) return false

        val smallPieces = shapeGenerator.smallReviveTray().map { wrapInPiece(it) }
        _state.value = current.copy(
            currentPieces = smallPieces,
            isGameOver = false,
            revivesUsed = current.revivesUsed + 1,
            comboLevel = 0,
        )
        autoSave()
        return true
    }

    // ---------- Internals ----------

    private fun generateTray(): List<Piece> {
        val pieces = shapeGenerator.nextTray(deterministicSeed).map { wrapInPiece(it) }
        // Advance the seed so the next tray is different but still deterministic.
        deterministicSeed = deterministicSeed?.plus(1)
        return pieces
    }

    private fun wrapInPiece(shape: Polyomino): Piece = Piece(
        pieceId = ++pieceIdCounter,
        shape = shape,
        colorId = ((pieceIdCounter % 6) + 1).toInt(),
    )

    private fun anyPieceFits(pieces: List<Piece>, grid: Grid): Boolean {
        for (piece in pieces) {
            for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
                if (canPlace(piece.shape, x, y, grid)) return true
            }
        }
        return false
    }

    /**
     * Rules (checked in priority order):
     *   UNBELIEVABLE — entire board wiped clean in one move
     *   EXCELLENT    — cross-clear (both rows AND cols) or 4+ lines at once
     *   GREAT        — exactly 3 columns cleared (no rows involved)
     *   GOOD         — any 2+ lines cleared
     *   null         — 0-1 lines: no voice
     */
    private fun feedbackFor(
        fullRows: List<Int>,
        fullCols: List<Int>,
        isBoardEmpty: Boolean,
    ): FeedbackType? {
        val totalLines = fullRows.size + fullCols.size
        val isCross = fullRows.isNotEmpty() && fullCols.isNotEmpty()
        return when {
            totalLines == 0 -> null
            isBoardEmpty -> FeedbackType.UNBELIEVABLE
            isCross || totalLines >= 4 -> FeedbackType.EXCELLENT
            fullCols.size == 3 && fullRows.isEmpty() -> FeedbackType.GREAT
            totalLines >= 2 -> FeedbackType.GOOD
            else -> null
        }
    }

    private fun autoSave() {
        // Debounce: cancel any pending save and reschedule. During rapid placements
        // this coalesces N disk writes into one, fired 300 ms after the last move.
        saveJob?.cancel()
        saveJob = externalScope.launch {
            delay(300)
            saveRepository.save(_state.value)
        }
    }
}
