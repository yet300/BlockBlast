package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.GameSaveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GameEngine] — focused on the regressions we caught during
 * the audit (best-score seeding, save persistence, review-prompt one-shot).
 */
class GameEngineTest {

    private val saveRepo = InMemorySaveRepo()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val engine = GameEngine(
        shapeGenerator = FixedShapeGenerator(),
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
        assertTrue(s.currentPieces.isNotEmpty())
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
        val snapshot = GameState(
            score = 42L,
            bestScore = 100L,
            currentPieces = listOf(),
        )
        engine.restore(snapshot)
        assertEquals(42L, engine.state.value.score)
        assertEquals(100L, engine.state.value.bestScore)
    }

    // ── canPlace boundaries ─────────────────────────────────────────────

    @Test
    fun canPlace_rejects_out_of_bounds() {
        engine.startNewGame()
        val piece = engine.state.value.currentPieces.first()
        assertFalse(engine.canPlace(piece, x = -1, y = 0))
        assertFalse(engine.canPlace(piece, x = 999, y = 0))
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private class InMemorySaveRepo : GameSaveRepository {
        var saved: GameState? = null
        override suspend fun save(state: GameState) { saved = state }
        override suspend fun load(): GameState? = saved
        override suspend fun clear() { saved = null }
    }

    private class FixedShapeGenerator : ShapeGenerator {
        // 1x1 piece — easy to reason about in tests.
        private val one = Polyomino(id = "1x1", cells = listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
        override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
    }
}
