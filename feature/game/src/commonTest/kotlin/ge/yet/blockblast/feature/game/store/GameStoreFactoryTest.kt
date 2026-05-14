package ge.yet.blockblast.feature.game.store

import com.app.common.config.AppConfig
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.engine.ScoreCalculator
import ge.yet.blokblast.domain.engine.ShapeGenerator
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameStoreFactoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    // ── Bootstrap branches ───────────────────────────────────────────────

    @Test
    fun bootstrap_new_game_starts_engine() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        // engine should be in a fresh round
        assertEquals(0L, deps.engine.state.value.score)
        assertTrue(deps.engine.state.value.currentPieces.isNotEmpty())
        assertTrue(deps.analytics.has("game_started", mapOf("source" to "new")))
        deps.dispose()
    }

    @Test
    fun bootstrap_continue_with_no_save_starts_new_game() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = false)
        assertTrue(deps.engine.state.value.currentPieces.isNotEmpty())
        assertTrue(deps.analytics.has("game_started", mapOf("source" to "new")))
        deps.dispose()
    }

    @Test
    fun bootstrap_continue_with_playable_save_restores() = runTest {
        val savedState = playableState(score = 77L)
        val deps = TestDeps(savedState = savedState)
        deps.factory().create(isNewGame = false)
        assertEquals(77L, deps.engine.state.value.score)
        assertTrue(deps.analytics.has("game_started", mapOf("source" to "continue")))
        deps.dispose()
    }

    @Test
    fun bootstrap_continue_with_gameOver_save_starts_new_game() = runTest {
        val deps = TestDeps(savedState = playableState().copy(isGameOver = true))
        deps.factory().create(isNewGame = false)
        assertFalse(deps.engine.state.value.isGameOver)
        assertTrue(deps.analytics.has("game_started", mapOf("source" to "new")))
        deps.dispose()
    }

    @Test
    fun bootstrap_seeds_bestScore_from_settings() = runTest {
        val deps = TestDeps(settingsBest = 2500L)
        deps.factory().create(isNewGame = true)
        assertEquals(2500L, deps.engine.state.value.bestScore)
        deps.dispose()
    }

    @Test
    fun bootstrap_warm_continue_does_not_restart_engine() = runTest {
        val deps = TestDeps()
        // Pre-warm engine
        deps.engine.startNewGame(bestScore = 0)
        val pieceCountBefore = deps.engine.state.value.currentPieces.size
        val firstPieceId = deps.engine.state.value.currentPieces.first().pieceId
        deps.factory().create(isNewGame = false)
        // Engine state untouched (same first piece id; engine.startNewGame was NOT called again)
        assertEquals(pieceCountBefore, deps.engine.state.value.currentPieces.size)
        assertEquals(firstPieceId, deps.engine.state.value.currentPieces.first().pieceId)
        deps.dispose()
    }

    // ── State snapshots ──────────────────────────────────────────────────

    @Test
    fun engine_state_emissions_are_reflected_in_store_state() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val piece = deps.engine.state.value.currentPieces.first()
        deps.engine.placePiece(piece.pieceId, 0, 0)
        assertEquals(deps.engine.state.value, store.state.game)
        deps.dispose()
    }

    // ── Best-score persistence ───────────────────────────────────────────

    @Test
    fun bestScore_increase_is_persisted_to_settings() = runTest {
        val deps = TestDeps(settingsBest = 0L)
        deps.factory().create(isNewGame = true)
        // Manually lift bestScore on the engine
        deps.engine.seedBestScore(123L)
        // collector listens distinctUntilChanged on bestScore — should propagate
        assertEquals(123L, deps.settings.bestScore.value)
        deps.dispose()
    }

    // ── SFX wiring ───────────────────────────────────────────────────────

    @Test
    fun piece_placement_triggers_placement_sound() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        val piece = deps.engine.state.value.currentPieces.first()
        deps.engine.placePiece(piece.pieceId, 0, 0)
        assertTrue(deps.audio.placementCount >= 1)
        deps.dispose()
    }

    @Test
    fun line_clear_triggers_clear_sound_and_analytics() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        // Build a clear: fill row 0 cols 0..6 then place 1x1 at (7,0).
        // Use restore for deterministic setup.
        var grid = Grid()
        for (x in 0..6) grid = grid.withCell(x, 0, 1)
        grid = grid.withCell(3, 5, 1) // avoid full-board UNBELIEVABLE
        val placePiece = Piece(
            pieceId = 999L,
            shape = Polyomino("1x1", listOf(Position(0, 0))),
            colorId = 1,
        )
        deps.engine.restore(GameState(grid = grid, currentPieces = listOf(placePiece)))
        deps.engine.placePiece(999L, 7, 0)
        assertEquals(listOf(1), deps.audio.clearedLines)
        assertTrue(deps.analytics.has("lines_cleared"))
        deps.dispose()
    }

    // ── Music gating ─────────────────────────────────────────────────────

    @Test
    fun music_starts_on_active_round() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        assertTrue(deps.audio.startMusicCount >= 1)
        deps.dispose()
    }

    @Test
    fun music_stops_when_game_over() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        assertTrue(deps.audio.stopMusicCount >= 1)
        deps.dispose()
    }

    // ── Game-over edge → countdown ───────────────────────────────────────

    @Test
    fun gameOver_edge_starts_countdown_at_five() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()
        assertEquals(GameStoreState.CONTINUE_COUNTDOWN_SECONDS, store.state.continueCountdown)
        deps.dispose()
    }

    @Test
    fun countdown_ticks_down_each_second() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()
        assertEquals(5, store.state.continueCountdown)
        advanceTimeBy(1100)
        assertEquals(4, store.state.continueCountdown)
        advanceTimeBy(4000)
        assertEquals(0, store.state.continueCountdown)
        deps.dispose()
    }

    @Test
    fun revive_clears_countdown() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()
        assertEquals(5, store.state.continueCountdown)
        // Simulate revive
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = false))
        runCurrent()
        assertEquals(GameStoreState.COUNTDOWN_INACTIVE, store.state.continueCountdown)
        deps.dispose()
    }

    // ── Review prompt qualifier ──────────────────────────────────────────

    @Test
    fun review_label_fires_when_all_conditions_met() = runTest {
        val deps = TestDeps(settingsBest = 0L, reviewCount = 0)
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        // qualifying: score >= 500, beat best by >= 1000
        val qualifying = deps.engine.state.value.copy(
            score = AppConfig.REVIEW_MIN_SCORE + AppConfig.REVIEW_BEST_SCORE_DELTA.toInt() + 10L,
            bestAtRoundStart = 0L,
            isGameOver = true,
            reviewPromptFiredThisRound = false,
        )
        deps.engine.restore(qualifying)
        runCurrent()
        assertEquals(listOf<GameStore.Label>(GameStore.Label.RequestReview), labels)
        assertTrue(deps.engine.state.value.reviewPromptFiredThisRound)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun review_label_does_not_fire_when_score_below_minimum() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        scope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(
            deps.engine.state.value.copy(
                score = (AppConfig.REVIEW_MIN_SCORE - 1).toLong(),
                bestAtRoundStart = 0,
                isGameOver = true,
            ),
        )
        runCurrent()
        assertTrue(labels.isEmpty())
        scope.cancel()
        deps.dispose()
    }

    @Test
    fun review_label_does_not_fire_when_delta_below_threshold() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        scope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(
            deps.engine.state.value.copy(
                score = AppConfig.REVIEW_MIN_SCORE.toLong() + 50,
                // beats by only 50 — below DELTA(1000)
                bestAtRoundStart = AppConfig.REVIEW_MIN_SCORE.toLong(),
                isGameOver = true,
            ),
        )
        runCurrent()
        assertTrue(labels.isEmpty())
        scope.cancel()
        deps.dispose()
    }

    @Test
    fun review_label_does_not_fire_when_max_reached() = runTest {
        val deps = TestDeps(reviewCount = AppConfig.REVIEW_MAX_PROMPTS)
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        scope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(
            deps.engine.state.value.copy(
                score = 10_000L,
                bestAtRoundStart = 0L,
                isGameOver = true,
            ),
        )
        runCurrent()
        assertTrue(labels.isEmpty())
        scope.cancel()
        deps.dispose()
    }

    @Test
    fun review_label_does_not_re_fire_if_already_fired_this_round() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        scope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(
            deps.engine.state.value.copy(
                score = 10_000L,
                bestAtRoundStart = 0L,
                isGameOver = true,
                reviewPromptFiredThisRound = true,
            ),
        )
        runCurrent()
        assertTrue(labels.isEmpty())
        scope.cancel()
        deps.dispose()
    }

    // ── Intents ──────────────────────────────────────────────────────────

    @Test
    fun place_intent_invokes_engine_and_logs() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val piece = deps.engine.state.value.currentPieces.first()
        store.accept(GameStore.Intent.Place(piece.pieceId, 0, 0))
        assertTrue(deps.analytics.has("piece_place_attempt"))
        assertTrue(deps.analytics.has("piece_place_success"))
        deps.dispose()
    }

    @Test
    fun place_intent_logs_failed_on_overlap() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val piece = deps.engine.state.value.currentPieces.first()
        store.accept(GameStore.Intent.Place(piece.pieceId, 0, 0))
        // Try placing another piece at same cell
        val piece2 = deps.engine.state.value.currentPieces.first()
        store.accept(GameStore.Intent.Place(piece2.pieceId, 0, 0))
        assertTrue(deps.analytics.has("piece_place_failed"))
        deps.dispose()
    }

    @Test
    fun restart_intent_starts_new_game() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val piece = deps.engine.state.value.currentPieces.first()
        deps.engine.placePiece(piece.pieceId, 0, 0)
        val scoreBefore = deps.engine.state.value.score
        assertTrue(scoreBefore > 0)
        store.accept(GameStore.Intent.Restart)
        runCurrent()
        assertEquals(0L, deps.engine.state.value.score)
        assertTrue(deps.analytics.has("restart_clicked"))
        deps.dispose()
    }

    @Test
    fun revive_intent_continues_with_small_blocks_when_game_over() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        store.accept(GameStore.Intent.Revive)
        runCurrent()
        assertFalse(deps.engine.state.value.isGameOver)
        assertEquals(1, deps.engine.state.value.revivesUsed)
        assertTrue(deps.analytics.has("revive_clicked"))
        deps.dispose()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun playableState(score: Long = 0L): GameState = GameState(
        grid = Grid().withCell(0, 0, 1),
        score = score,
        bestScore = score,
        currentPieces = listOf(
            Piece(42L, Polyomino("h2", listOf(Position(0, 0), Position(1, 0))), 1),
        ),
        isGameOver = false,
    )

    /** All collaborators wired together so each test gets a fresh engine + factory. */
    private inner class TestDeps(
        savedState: GameState? = null,
        settingsBest: Long = 0L,
        reviewCount: Int = 0,
    ) {
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val saveRepo = StubSaveRepo(savedState)
        val settings = FakeSettings(bestScore = settingsBest, reviewPromptCount = reviewCount)
        val audio = RecordingAudio()
        val analytics = RecordingAnalytics()
        val storeReview = NoopStoreReview()
        val engine = GameEngine(
            shapeGenerator = OneByOneGenerator(),
            scoreCalculator = ScoreCalculator(),
            saveRepository = saveRepo,
            externalScope = scope,
        )

        fun factory(): GameStoreFactory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = engine,
            audio = audio,
            saveRepository = saveRepo,
            settings = settings,
            analytics = analytics,
        )

        fun dispose() { scope.cancel() }
    }
}

private class OneByOneGenerator : ShapeGenerator {
    private val one = Polyomino("1x1", listOf(Position(0, 0)))
    override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
    override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
}

private class StubSaveRepo(initial: GameState? = null) : GameSaveRepository {
    private var stored: GameState? = initial
    override suspend fun save(state: GameState) { stored = state }
    override suspend fun load(): GameState? = stored
    override suspend fun clear() { stored = null }
}

private class FakeSettings(
    bestScore: Long = 0L,
    reviewPromptCount: Int = 0,
) : SettingsRepository {
    private val bestScoreFlow = MutableStateFlow(bestScore)
    private val reviewFlow = MutableStateFlow(reviewPromptCount)
    override val soundEnabled = MutableStateFlow(true).asStateFlow()
    override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
    override val darkTheme = MutableStateFlow(false).asStateFlow()
    override val bestScore: StateFlow<Long> = bestScoreFlow.asStateFlow()
    override val reviewPromptCount: StateFlow<Int> = reviewFlow.asStateFlow()
    override val tutorialSeen = MutableStateFlow(false).asStateFlow()
    override suspend fun setSoundEnabled(enabled: Boolean) {}
    override suspend fun setVibrationEnabled(enabled: Boolean) {}
    override suspend fun setDarkTheme(enabled: Boolean) {}
    override suspend fun setBestScore(score: Long) { if (score > bestScoreFlow.value) bestScoreFlow.value = score }
    override suspend fun incrementReviewPromptCount() { reviewFlow.value += 1 }
    override suspend fun suppressReviewPrompts(max: Int) { if (reviewFlow.value < max) reviewFlow.value = max }
    override suspend fun setTutorialSeen() {}
}

private class RecordingAudio : AudioRepository {
    var placementCount = 0
    val clearedLines = mutableListOf<Int>()
    val feedback = mutableListOf<FeedbackType>()
    val combos = mutableListOf<Int>()
    var startMusicCount = 0
    var stopMusicCount = 0
    override suspend fun playPlacementSound() { placementCount += 1 }
    override suspend fun playClearSound(lines: Int) { clearedLines += lines }
    override suspend fun playVoiceFeedback(type: FeedbackType) { feedback += type }
    override suspend fun playVoiceCombo(combo: Int) { combos += combo }
    override suspend fun startMusic() { startMusicCount += 1 }
    override suspend fun stopMusic() { stopMusicCount += 1 }
    override suspend fun onAppBackground() {}
    override suspend fun onAppForeground() {}
}

private class RecordingAnalytics : AnalyticRepository {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun logEvent(eventName: String, params: Map<String, Any>?) {
        events += eventName to (params ?: emptyMap())
    }
    override fun deleteData() {}
    fun has(name: String, subset: Map<String, Any> = emptyMap()): Boolean =
        events.any { (n, p) -> n == name && subset.all { (k, v) -> p[k] == v } }
}

private class NoopStoreReview : StoreReviewRepository {
    override fun requestInAppReview(): Flow<ReviewCode> = emptyFlow()
    override fun requestInMarketReview(): Flow<ReviewCode> = emptyFlow()
}

