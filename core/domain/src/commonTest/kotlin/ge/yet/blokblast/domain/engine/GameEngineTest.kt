package ge.yet.blokblast.domain.engine

import app.cash.turbine.test
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.model.GameEvent
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GameEngine] — full behaviour coverage.
 */
class GameEngineTest {

    private val saveRepo = InMemorySaveRepo()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fixedGen = ControllableShapeGenerator()
    private val engine = GameEngine(
        shapeGenerator = fixedGen,
        scoreCalculator = ScoreCalculator(),
        saveRepository = saveRepo,
        externalScope = scope,
    )

    @AfterTest
    fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // ── seedBestScore ────────────────────────────────────────────────────

    @Test
    fun seedBestScore_lifts_when_persisted_is_higher() {
        engine.seedBestScore(1234)
        assertEquals(1234, engine.state.value.bestScore)
        assertEquals(1234, engine.state.value.bestAtRoundStart)
    }

    @Test
    fun seedBestScore_is_no_op_when_engine_already_higher() {
        engine.startNewGame(bestScore = 5000)
        engine.seedBestScore(1234)
        assertEquals(5000, engine.state.value.bestScore)
    }

    @Test
    fun seedBestScore_keeps_bestAtRoundStart_when_already_higher() {
        engine.startNewGame(bestScore = 5000)
        engine.seedBestScore(3000)
        assertEquals(5000, engine.state.value.bestAtRoundStart)
    }

    // ── startNewGame ─────────────────────────────────────────────────────

    @Test
    fun startNewGame_resets_score_and_combo_but_keeps_best() {
        engine.seedBestScore(900)
        engine.startNewGame(bestScore = engine.state.value.bestScore)
        val s = engine.state.value
        assertEquals(0L, s.score)
        assertEquals(0, s.comboLevel)
        assertEquals(900L, s.bestScore)
        assertEquals(900L, s.bestAtRoundStart)
        assertFalse(s.reviewPromptFiredThisRound)
        assertFalse(s.isGameOver)
        assertEquals(0, s.revivesUsed)
        assertTrue(s.currentPieces.isNotEmpty())
    }

    @Test
    fun startNewGame_initial_grid_is_empty() {
        engine.startNewGame()
        assertTrue(engine.state.value.grid.isBoardEmpty())
    }

    // ── markReviewPromptFired ───────────────────────────────────────────

    @Test
    fun markReviewPromptFired_is_idempotent() {
        engine.startNewGame(bestScore = 0)
        engine.markReviewPromptFired()
        engine.markReviewPromptFired()
        assertTrue(engine.state.value.reviewPromptFiredThisRound)
    }

    @Test
    fun startNewGame_clears_review_flag() {
        engine.startNewGame(bestScore = 0)
        engine.markReviewPromptFired()
        engine.startNewGame(bestScore = engine.state.value.bestScore)
        assertFalse(engine.state.value.reviewPromptFiredThisRound)
    }

    // ── restore ─────────────────────────────────────────────────────────

    @Test
    fun restore_replaces_state() {
        val snapshot = GameState(score = 42L, bestScore = 100L, currentPieces = emptyList())
        engine.restore(snapshot)
        assertEquals(42L, engine.state.value.score)
        assertEquals(100L, engine.state.value.bestScore)
    }

    @Test
    fun restore_emits_GameStarted_for_playable_state() = runTest {
        val playable = GameState(
            grid = Grid().withCell(0, 0, 1),
            currentPieces = listOf(piece(1, ONE_CELL)),
            isGameOver = false,
        )
        engine.events.test {
            engine.restore(playable)
            assertEquals(GameEvent.GameStarted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restore_does_not_emit_for_gameOver_state() = runTest {
        val finished = GameState(
            currentPieces = listOf(piece(1, ONE_CELL)),
            isGameOver = true,
        )
        engine.events.test {
            engine.restore(finished)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restore_does_not_emit_when_pieces_empty() = runTest {
        engine.events.test {
            engine.restore(GameState(currentPieces = emptyList(), isGameOver = false))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── canPlace ────────────────────────────────────────────────────────

    @Test
    fun canPlace_rejects_out_of_bounds() {
        engine.startNewGame()
        val piece = engine.state.value.currentPieces.first()
        assertFalse(engine.canPlace(piece, x = -1, y = 0))
        assertFalse(engine.canPlace(piece, x = 999, y = 0))
        assertFalse(engine.canPlace(piece, x = 0, y = -1))
        assertFalse(engine.canPlace(piece, x = 0, y = Grid.SIZE))
    }

    @Test
    fun canPlace_rejects_overlap_with_existing_cell() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        engine.startNewGame()
        val first = engine.state.value.currentPieces[0]
        assertTrue(engine.placePiece(first.pieceId, 0, 0))
        val second = engine.state.value.currentPieces.first()
        assertFalse(engine.canPlace(second, 0, 0))
    }

    // ── placePiece basic ────────────────────────────────────────────────

    @Test
    fun placePiece_invalid_id_returns_false() {
        engine.startNewGame()
        assertFalse(engine.placePiece(pieceId = 99999L, x = 0, y = 0))
    }

    @Test
    fun placePiece_when_gameOver_returns_false() {
        val p = piece(1, ONE_CELL)
        engine.restore(GameState(currentPieces = listOf(p), isGameOver = true))
        assertFalse(engine.placePiece(p.pieceId, 0, 0))
    }

    @Test
    fun placePiece_overlap_returns_false_and_keeps_state() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        engine.startNewGame()
        val a = engine.state.value.currentPieces[0]
        assertTrue(engine.placePiece(a.pieceId, 3, 3))
        val before = engine.state.value
        val b = engine.state.value.currentPieces.first()
        assertFalse(engine.placePiece(b.pieceId, 3, 3))
        assertEquals(before, engine.state.value)
    }

    @Test
    fun placePiece_awards_one_point_per_block_without_clear() {
        fixedGen.nextTrayPieces = listOf(H2, ONE_CELL, ONE_CELL)
        engine.startNewGame()
        val p = engine.state.value.currentPieces.first { it.shape.id == "h2" }
        assertTrue(engine.placePiece(p.pieceId, 0, 0))
        assertEquals(2L, engine.state.value.score)
        assertEquals(0, engine.state.value.comboLevel)
    }

    @Test
    fun placePiece_removes_placed_piece_from_tray() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, H2, ONE_CELL)
        engine.startNewGame()
        val before = engine.state.value.currentPieces.size
        val p = engine.state.value.currentPieces.first { it.shape.id == "h2" }
        engine.placePiece(p.pieceId, 0, 0)
        assertEquals(before - 1, engine.state.value.currentPieces.size)
        assertTrue(engine.state.value.currentPieces.none { it.pieceId == p.pieceId })
    }

    @Test
    fun tray_refills_when_emptied() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        engine.startNewGame()
        repeat(3) {
            val p = engine.state.value.currentPieces.first()
            engine.placePiece(p.pieceId, it, 0)
        }
        assertEquals(3, engine.state.value.currentPieces.size)
    }

    // ── Clearing lines / combos / feedback ──────────────────────────────

    @Test
    fun clearing_one_row_awards_clear_points_and_combo_one() {
        val grid = fillRow(row = 0, cols = 0..6)
        val placePiece = piece(100, ONE_CELL)
        engine.restore(
            GameState(
                grid = grid,
                currentPieces = listOf(placePiece, piece(101, ONE_CELL), piece(102, ONE_CELL)),
            ),
        )
        assertTrue(engine.placePiece(placePiece.pieceId, 7, 0))
        val s = engine.state.value
        // newCombo=1 is passed to clearPoints (engine increments BEFORE scoring),
        // so multiplier = 1.5. placement=1, clear = 10*1*1.5 = 15 -> total 16.
        assertEquals(16L, s.score)
        assertEquals(1, s.comboLevel)
        for (x in 0 until Grid.SIZE) assertTrue(s.grid.isEmpty(x, 0))
    }

    @Test
    fun clearing_two_rows_simultaneously_emits_GOOD() = runTest {
        var grid = fillRow(0, 0..6)
        for (x in 0..6) grid = grid.withCell(x, 1, 1)
        // Add an isolated cell elsewhere so the board isn't fully empty after clear
        // (otherwise feedback escalates to UNBELIEVABLE).
        grid = grid.withCell(4, 5, 1)
        val placePiece = piece(1, V2)
        engine.restore(GameState(grid = grid, currentPieces = listOf(placePiece)))
        engine.events.test {
            assertTrue(engine.placePiece(placePiece.pieceId, 7, 0))
            var saw: FeedbackType? = null
            while (true) {
                val ev = awaitItem()
                if (ev is GameEvent.Feedback) { saw = ev.type; break }
                if (ev is GameEvent.GameOver) break
            }
            assertEquals(FeedbackType.GOOD, saw)
            cancelAndIgnoreRemainingEvents()
        }
        // newCombo=1 → multiplier 1.5; base 20 * sim 2 * 1.5 = 60; placement = 2 → 62
        assertEquals(62L, engine.state.value.score)
    }

    @Test
    fun cross_clear_is_excellent_and_isCrossClear_event_flag_set() = runTest {
        var g = Grid()
        for (x in 1 until Grid.SIZE) g = g.withCell(x, 0, 1)
        for (y in 1 until Grid.SIZE) g = g.withCell(0, y, 1)
        // Keep one cell off the cleared row/col so the board isn't empty post-clear.
        g = g.withCell(4, 4, 1)
        val p = piece(1, ONE_CELL)
        engine.restore(GameState(grid = g, currentPieces = listOf(p)))

        engine.events.test {
            engine.placePiece(p.pieceId, 0, 0)
            // Expected sequence: PiecePlaced, LinesCleared(cross), Feedback (combo=1, no ComboActive)
            var crossSeen = false
            var feedback: FeedbackType? = null
            repeat(3) {
                val ev = awaitItem()
                if (ev is GameEvent.LinesCleared && ev.isCrossClear) crossSeen = true
                if (ev is GameEvent.Feedback) feedback = ev.type
            }
            assertTrue(crossSeen)
            assertEquals(FeedbackType.EXCELLENT, feedback)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearing_full_board_emits_UNBELIEVABLE() {
        var g = Grid()
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) g = g.withCell(x, y, 1)
        g = g.clearedAt(setOf(Position(0, 0)))
        val p = piece(1, ONE_CELL)
        engine.restore(GameState(grid = g, currentPieces = listOf(p, piece(2, ONE_CELL))))
        engine.placePiece(p.pieceId, 0, 0)
        assertTrue(engine.state.value.grid.isBoardEmpty())
        assertEquals(FeedbackType.UNBELIEVABLE, engine.state.value.lastFeedback.type)
    }

    @Test
    fun no_clear_resets_combo() {
        val grid = fillRow(0, 0..6)
        val first = piece(1, ONE_CELL)
        val second = piece(2, ONE_CELL)
        engine.restore(GameState(grid = grid, currentPieces = listOf(first, second)))
        engine.placePiece(first.pieceId, 7, 0)
        assertEquals(1, engine.state.value.comboLevel)
        engine.placePiece(second.pieceId, 4, 4)
        assertEquals(0, engine.state.value.comboLevel)
    }

    @Test
    fun consecutive_clears_increment_combo() {
        var g = fillRow(0, 0..6)
        for (x in 0..6) g = g.withCell(x, 1, 1)
        val a = piece(1, ONE_CELL)
        val b = piece(2, ONE_CELL)
        engine.restore(GameState(grid = g, currentPieces = listOf(a, b)))
        engine.placePiece(a.pieceId, 7, 0)
        assertEquals(1, engine.state.value.comboLevel)
        engine.placePiece(b.pieceId, 7, 1)
        assertEquals(2, engine.state.value.comboLevel)
    }

    @Test
    fun bestScore_tracks_score_during_round() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        engine.startNewGame(bestScore = 0)
        val p = engine.state.value.currentPieces.first()
        engine.placePiece(p.pieceId, 0, 0)
        assertEquals(1L, engine.state.value.bestScore)
    }

    @Test
    fun bestScore_not_reduced_when_score_drops_below() {
        engine.seedBestScore(500)
        engine.startNewGame(bestScore = 500)
        val p = engine.state.value.currentPieces.first()
        engine.placePiece(p.pieceId, 0, 0)
        assertEquals(500L, engine.state.value.bestScore)
    }

    @Test
    fun pointsAwarded_nonce_increments_only_when_points_gained() {
        fixedGen.nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        engine.startNewGame()
        val before = engine.state.value.lastPointsAwarded.nonce
        val p = engine.state.value.currentPieces.first()
        engine.placePiece(p.pieceId, 0, 0)
        assertEquals(before + 1, engine.state.value.lastPointsAwarded.nonce)
    }

    // ── Game over & revive ──────────────────────────────────────────────

    @Test
    fun game_over_when_no_piece_fits() {
        // Empties: row-0 has (0,0)+(1,0); col-0 has (0,0)+(0,1); plus diagonal
        // (2,2)..(7,7) so every other row/col has exactly one empty. None of those
        // empties are adjacent → after placing 1x1 at (0,0):
        //   - Row 0 still has (1,0) empty   → no row clear
        //   - Col 0 still has (0,1) empty   → no col clear
        //   - Other rows/cols still have their single diagonal empty → no clear
        // Remaining empties {(1,0),(0,1),(2,2)..(7,7)} are all isolated → H2 fits nowhere.
        val empties = buildSet {
            add(Position(0, 0)); add(Position(1, 0)); add(Position(0, 1))
            for (i in 2 until Grid.SIZE) add(Position(i, i))
        }
        var g = Grid()
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
            if (Position(x, y) !in empties) g = g.withCell(x, y, 1)
        }
        val tray = listOf(piece(10, ONE_CELL), piece(11, H2), piece(12, H2))
        engine.restore(GameState(grid = g, currentPieces = tray))
        assertTrue(engine.placePiece(10L, 0, 0), "1x1 should fit at (0,0)")
        assertTrue(engine.state.value.isGameOver, "no H2 should fit; expected game-over")
    }

    @Test
    fun revive_no_op_when_not_game_over() {
        engine.startNewGame()
        assertFalse(engine.continueWithSmallBlocks())
    }

    @Test
    fun revive_succeeds_once_then_capped() {
        engine.restore(
            GameState(
                grid = Grid(),
                currentPieces = listOf(piece(1, ONE_CELL)),
                isGameOver = true,
                revivesUsed = 0,
            ),
        )
        assertTrue(engine.continueWithSmallBlocks())
        assertEquals(1, engine.state.value.revivesUsed)
        assertFalse(engine.state.value.isGameOver)
        assertEquals(0, engine.state.value.comboLevel)
        engine.restore(engine.state.value.copy(isGameOver = true))
        assertFalse(engine.continueWithSmallBlocks())
    }

    @Test
    fun revive_does_not_touch_grid_or_score() {
        val grid = Grid().withCell(0, 0, 5)
        engine.restore(
            GameState(
                grid = grid,
                score = 1234L,
                bestScore = 1234L,
                currentPieces = listOf(piece(1, H2)),
                isGameOver = true,
            ),
        )
        engine.continueWithSmallBlocks()
        assertEquals(grid, engine.state.value.grid)
        assertEquals(1234L, engine.state.value.score)
    }

    @Test
    fun revive_provides_three_small_pieces() {
        engine.restore(
            GameState(currentPieces = listOf(piece(1, H2)), isGameOver = true),
        )
        engine.continueWithSmallBlocks()
        assertEquals(3, engine.state.value.currentPieces.size)
        for (p in engine.state.value.currentPieces) {
            assertTrue(p.shape.size in 1..2)
        }
    }

    // ── Events ordering ─────────────────────────────────────────────────

    @Test
    fun events_emitted_in_order_PiecePlaced_then_LinesCleared() = runTest {
        val grid = fillRow(0, 0..6)
        val p = piece(1, ONE_CELL)
        engine.restore(GameState(grid = grid, currentPieces = listOf(p)))
        engine.events.test {
            engine.placePiece(p.pieceId, 7, 0)
            assertTrue(awaitItem() is GameEvent.PiecePlaced)
            assertTrue(awaitItem() is GameEvent.LinesCleared)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ComboActive_not_emitted_below_level_two() = runTest {
        val grid = fillRow(0, 0..6)
        val p = piece(1, ONE_CELL)
        engine.restore(GameState(grid = grid, currentPieces = listOf(p, piece(2, ONE_CELL))))
        engine.events.test {
            engine.placePiece(p.pieceId, 7, 0)
            val events = mutableListOf<GameEvent>()
            repeat(3) { events += awaitItem() }
            assertTrue(events.none { it is GameEvent.ComboActive })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── autoSave / debounce ─────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun autoSave_debounces_to_single_write() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val repo = CountingSaveRepo()
        val gen = ControllableShapeGenerator().apply {
            nextTrayPieces = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        }
        val engineLocal = GameEngine(
            shapeGenerator = gen,
            scoreCalculator = ScoreCalculator(),
            saveRepository = repo,
            externalScope = testScope,
        )
        engineLocal.startNewGame()
        repeat(3) {
            val p = engineLocal.state.value.currentPieces.first()
            engineLocal.placePiece(p.pieceId, it, 0)
        }
        advanceTimeBy(100)
        runCurrent()
        val midCount = repo.count
        advanceTimeBy(400)
        runCurrent()
        assertTrue(repo.count > midCount)
        testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun fillRow(row: Int, cols: IntRange): Grid {
        var g = Grid()
        for (x in cols) g = g.withCell(x, row, 1)
        return g
    }

    private fun piece(id: Long, shape: Polyomino) =
        ge.yet.blokblast.domain.model.Piece(pieceId = id, shape = shape, colorId = 1)

    private class InMemorySaveRepo : GameSaveRepository {
        var saved: GameState? = null
        override suspend fun save(state: GameState) { saved = state }
        override suspend fun load(): GameState? = saved
        override suspend fun clear() { saved = null }
    }

    private class CountingSaveRepo : GameSaveRepository {
        var count = 0
        override suspend fun save(state: GameState) { count += 1 }
        override suspend fun load(): GameState? = null
        override suspend fun clear() {}
    }

    private class ControllableShapeGenerator : ShapeGenerator {
        var nextTrayPieces: List<Polyomino> = listOf(ONE_CELL, ONE_CELL, ONE_CELL)
        override fun nextTray(seed: Long?): List<Polyomino> = nextTrayPieces
        override fun smallReviveTray(): List<Polyomino> = listOf(ONE_CELL, H2, V2)
    }

    companion object {
        private val ONE_CELL = Polyomino(id = "1x1", cells = listOf(Position(0, 0)))
        private val H2 = Polyomino(id = "h2", cells = listOf(Position(0, 0), Position(1, 0)))
        private val V2 = Polyomino(id = "v2", cells = listOf(Position(0, 0), Position(0, 1)))
    }
}
