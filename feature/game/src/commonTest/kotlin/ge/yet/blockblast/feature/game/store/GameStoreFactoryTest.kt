package ge.yet.blockblast.feature.game.store

import com.app.common.config.AppConfig
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.engine.ScoreCalculator
import ge.yet.blokblast.domain.engine.ShapeGenerator
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.ReviewCode
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertIs
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
    fun initializer_keeps_the_tutorial_round_empty() = runTest {
        val deps = TestDeps(tutorialSeen = false)
        val starterSeed = knownStarterSeed(deps.engine)

        GameInitializer(deps.engine, deps.saveRepo, deps.settings).initialize(
            isNewGame = true,
            newGameSeed = starterSeed,
        )

        assertTrue(deps.engine.state.value.grid.isBoardEmpty())
        deps.dispose()
    }

    @Test
    fun initializer_allows_a_starter_after_the_tutorial() = runTest {
        val deps = TestDeps(tutorialSeen = true)
        val starterSeed = knownStarterSeed(deps.engine)

        GameInitializer(deps.engine, deps.saveRepo, deps.settings).initialize(
            isNewGame = true,
            newGameSeed = starterSeed,
        )

        assertFalse(deps.engine.state.value.grid.isBoardEmpty())
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

    @Test
    fun bootstrap_result_restore_preserves_exact_final_state_without_republishing_or_saving() = runTest {
        val finalState = playableState(score = 812L).copy(
            grid = Grid().withCell(4, 5, 3),
            bestScore = 1_200L,
            bestAtRoundStart = 700L,
            isGameOver = true,
        )
        val deps = TestDeps(savedState = finalState, settingsBest = finalState.bestScore)
        val store = deps.factory().create(
            isNewGame = true,
            restoredResultState = finalState,
        )
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        runCurrent()
        assertEquals(finalState, deps.engine.state.value)
        assertEquals(finalState, store.state.game)
        assertEquals(0, deps.saveRepo.saveCount)
        assertTrue(labels.none { it is GameStore.Label.GameCompleted })
        assertFalse(deps.analytics.has("game_over"))
        labelScope.cancel()
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

    @Test
    fun clearing_move_plays_placement_clear_and_voice_in_narrative_order() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        var grid = Grid().withCell(4, 5, 1)
        for (row in 0..2) {
            for (x in 0..6) grid = grid.withCell(x, row, 1)
        }
        val placePiece = Piece(
            pieceId = 999L,
            shape = Polyomino(
                id = "v3",
                cells = listOf(Position(0, 0), Position(0, 1), Position(0, 2)),
            ),
            colorId = 1,
        )
        deps.engine.restore(GameState(grid = grid, currentPieces = listOf(placePiece)))

        assertTrue(deps.engine.placePiece(placePiece.pieceId, 7, 0))

        assertEquals(
            listOf("placement", "clear:3", "voice:GREAT"),
            deps.audio.calls,
        )
        assertTrue(
            deps.analytics.has(
                "lines_cleared",
                mapOf(
                    "lines_count" to 3,
                    "cleared_cells" to 24,
                    "placement_points" to 3L,
                    "clear_points" to 60L,
                    "all_clear_points" to 0L,
                    "total_points" to 63L,
                    "feedback" to "great",
                ),
            ),
        )
        deps.dispose()
    }

    @Test
    fun combo_three_plays_amazing_once_and_combo_four_does_not_repeat_it() = runTest {
        val deps = TestDeps()
        deps.factory().create(isNewGame = true)
        var grid = Grid().withCell(7, 7, 1)
        for (row in 0..3) {
            for (x in 0..6) grid = grid.withCell(x, row, 1)
        }
        val pieces = (1L..4L).map { id ->
            Piece(id, Polyomino("1x1", listOf(Position(0, 0))), colorId = 1)
        }
        deps.engine.restore(GameState(grid = grid, currentPieces = pieces))

        pieces.forEachIndexed { row, piece ->
            assertTrue(deps.engine.placePiece(piece.pieceId, 7, row))
        }

        assertEquals(listOf(FeedbackType.AMAZING), deps.audio.voices)
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

    // ── Game-over edge → immutable Result hand-off ──────────────────────

    @Test
    fun gameOver_persists_final_state_and_best_before_completion_label() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch {
            store.labels.collect {
                deps.operations += "label"
                labels += it
            }
        }
        deps.operations.clear()
        val finalState = deps.engine.state.value.copy(
            score = 321L,
            bestScore = 321L,
            isGameOver = true,
        )
        deps.engine.restore(finalState)
        val emittedFinalState = deps.engine.state.value
        runCurrent()
        assertEquals(listOf("save", "best", "label"), deps.operations.takeLast(3))
        assertEquals(emittedFinalState, deps.saveRepo.saved)
        assertEquals(321L, deps.settings.bestScore.value)
        assertTrue(labels.single() is GameStore.Label.GameCompleted)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun terminal_save_failure_still_publishes_completion_and_logs_failure() = runTest {
        val deps = TestDeps(saveRepositoryOverride = ThrowingSaveRepository())
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }

        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()

        assertEquals(1, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
        assertTrue(
            deps.analytics.has(
                "game_persistence_failed",
                mapOf(
                    "operation" to "terminal_save",
                    "error_type" to "IllegalStateException",
                ),
            ),
        )
        deps.dispose()
    }

    @Test
    fun terminal_cancellation_is_rethrown_without_completion_or_failure_log() = runTest {
        val deps = TestDeps(
            saveRepositoryOverride = ThrowingSaveRepository(
                failure = CancellationException("cancelled"),
            ),
        )
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }

        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()

        assertTrue(labels.none { it is GameStore.Label.GameCompleted })
        assertFalse(deps.analytics.has("game_persistence_failed"))
        deps.dispose()
    }

    @Test
    fun terminal_best_score_failure_still_publishes_completion_and_logs_failure() = runTest {
        val deps = TestDeps(settingsRepositoryOverride = ThrowingBestSettingsRepository())
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }

        deps.engine.restore(
            deps.engine.state.value.copy(
                score = 321L,
                bestScore = 321L,
                isGameOver = true,
            ),
        )
        runCurrent()

        assertEquals(1, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
        assertTrue(
            deps.analytics.has(
                "game_persistence_failed",
                mapOf("operation" to "terminal_best_score"),
            ),
        )
        deps.dispose()
    }

    @Test
    fun terminal_edge_cancels_pending_autosave_before_delayed_explicit_save() = runTest {
        val deps = TestDeps(saveDelayMillis = 500L)
        val store = deps.factory().create(isNewGame = true)
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch {
            store.labels.collect { label ->
                if (label is GameStore.Label.GameCompleted) {
                    deps.engine.restoreResult(label.finalState)
                }
            }
        }

        val piece = deps.engine.state.value.currentPieces.first()
        deps.engine.placePiece(piece.pieceId, 0, 0)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, deps.saveRepo.saveCount)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun terminal_save_drains_replaced_in_flight_autosave_and_remains_last() = runTest {
        val repository = BlockingFirstSaveRepository()
        val deps = TestDeps(saveRepositoryOverride = repository)
        deps.factory().create(isNewGame = true)

        advanceTimeBy(300)
        runCurrent()
        val staleState = repository.startedStates.single()

        val piece = deps.engine.state.value.currentPieces.first()
        deps.engine.placePiece(piece.pieceId, 0, 0)
        val finalState = deps.engine.state.value.copy(isGameOver = true)
        deps.engine.restore(finalState)
        val emittedFinalState = deps.engine.state.value
        runCurrent()

        val finalStartedBeforeRelease = repository.startedStates.any { it.isGameOver }
        repository.releaseFirstSave()
        advanceUntilIdle()

        assertFalse(finalStartedBeforeRelease)
        assertEquals(listOf(staleState, emittedFinalState), repository.completedStates)
        assertEquals(emittedFinalState, repository.stored)
        deps.dispose()
    }

    @Test
    fun gameOver_emits_deep_copied_snapshot_and_continue_availability() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        val finalState = deps.engine.state.value.copy(
            grid = Grid().withCell(2, 3, 4),
            score = 777L,
            bestScore = 900L,
            revivesUsed = 0,
            isGameOver = true,
        )
        deps.engine.restore(finalState)
        val emittedFinalState = deps.engine.state.value
        runCurrent()
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertEquals(emittedFinalState, completed.finalState)
        assertTrue(completed.canContinue)
        finalState.grid.cells[3 * Grid.SIZE + 2] = 0
        assertEquals(4, completed.finalState.grid.colorAt(2, 3))
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun gameOver_with_all_revives_used_disables_continue() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(
            deps.engine.state.value.copy(
                revivesUsed = GameState.MAX_REVIVES,
                isGameOver = true,
            ),
        )
        runCurrent()
        assertFalse((labels.single() as GameStore.Label.GameCompleted).canContinue)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun gameOver_emits_completion_once_until_play_resumes() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        deps.engine.restore(deps.engine.state.value.copy(score = 5L, isGameOver = true))
        runCurrent()
        assertEquals(1, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = false))
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()
        assertEquals(2, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
        labelScope.cancel()
        deps.dispose()
    }

    // ── Review prompt qualifier ──────────────────────────────────────────

    @Test
    fun qualifying_review_is_marked_and_persisted_before_result_navigation() = runTest {
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
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertTrue(completed.shouldRequestReview)
        assertTrue(deps.engine.state.value.reviewPromptFiredThisRound)
        assertTrue(completed.finalState.reviewPromptFiredThisRound)
        assertEquals(completed.finalState, deps.saveRepo.saved)
        assertEquals(1, deps.settings.reviewPromptCount.value)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun qualifying_review_is_counted_only_once_across_repeated_terminal_edges() = runTest {
        val deps = TestDeps(settingsBest = 0L, reviewCount = 0)
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }
        val qualifying = deps.engine.state.value.copy(
            score = AppConfig.REVIEW_MIN_SCORE.toLong() + AppConfig.REVIEW_BEST_SCORE_DELTA + 10L,
            bestAtRoundStart = 0L,
            isGameOver = true,
        )

        deps.engine.restore(qualifying)
        runCurrent()
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = false))
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()

        val completions = labels.filterIsInstance<GameStore.Label.GameCompleted>()
        assertEquals(listOf(true, false), completions.map { it.shouldRequestReview })
        assertEquals(1, deps.settings.reviewPromptCount.value)
        labelScope.cancel()
        deps.dispose()
    }

    @Test
    fun review_count_failure_does_not_show_prompt_or_block_result_navigation() = runTest {
        val deps = TestDeps(failReviewPromptCount = true)
        val store = deps.factory().create(isNewGame = true)
        val labels = mutableListOf<GameStore.Label>()
        val labelScope = CoroutineScope(testDispatcher + SupervisorJob())
        labelScope.launch { store.labels.collect { labels += it } }

        deps.engine.restore(
            deps.engine.state.value.copy(
                score = AppConfig.REVIEW_MIN_SCORE.toLong() +
                    AppConfig.REVIEW_BEST_SCORE_DELTA +
                    10L,
                bestAtRoundStart = 0L,
                isGameOver = true,
            ),
        )
        runCurrent()

        val completed = labels.single() as GameStore.Label.GameCompleted
        assertFalse(completed.shouldRequestReview)
        assertFalse(completed.finalState.reviewPromptFiredThisRound)
        assertTrue(deps.analytics.has("game_persistence_failed"))
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
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertFalse(completed.shouldRequestReview)
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
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertFalse(completed.shouldRequestReview)
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
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertFalse(completed.shouldRequestReview)
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
        val completed = labels.single() as GameStore.Label.GameCompleted
        assertFalse(completed.shouldRequestReview)
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
    fun revive_intent_saves_playable_state_before_publishing_completion() = runTest {
        val deps = TestDeps()
        val store = deps.factory().create(
            isNewGame = false,
            restoredResultState = GameState(isGameOver = true),
        )
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }
        store.accept(GameStore.Intent.Revive)
        runCurrent()

        assertFalse(deps.engine.state.value.isGameOver)
        assertEquals(1, deps.engine.state.value.revivesUsed)
        assertEquals(1, deps.saveRepo.saveCount)
        val completion = assertIs<GameStore.Label.ReviveCompleted>(labels.single())
        assertEquals(deps.engine.state.value, completion.playableState)
        assertTrue(deps.analytics.has("revive_clicked"))
        deps.dispose()
    }

    @Test
    fun revive_save_failure_restores_terminal_state_and_publishes_failure() = runTest {
        val terminalState = GameState(
            grid = Grid().withCell(3, 4, 2),
            score = 900L,
            bestScore = 1_200L,
            isGameOver = true,
        )
        val deps = TestDeps(saveRepositoryOverride = ThrowingSaveRepository())
        val store = deps.factory().create(
            isNewGame = false,
            restoredResultState = terminalState,
        )
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }

        store.accept(GameStore.Intent.Revive)
        runCurrent()

        assertEquals(terminalState, deps.engine.state.value)
        assertTrue(labels.any { it is GameStore.Label.ReviveFailed })
        assertTrue(labels.none { it is GameStore.Label.ReviveCompleted })
        assertTrue(
            deps.analytics.has(
                "game_persistence_failed",
                mapOf("operation" to "revive_save"),
            ),
        )
        deps.dispose()
    }

    @Test
    fun failed_rollback_then_successful_retry_does_not_suppress_next_real_game_over() = runTest {
        val repository = FailFirstSaveRepository()
        val terminalState = GameState(isGameOver = true)
        val deps = TestDeps(saveRepositoryOverride = repository)
        val store = deps.factory().create(
            isNewGame = false,
            restoredResultState = terminalState,
        )
        val labels = mutableListOf<GameStore.Label>()
        deps.scope.launch { store.labels.collect { labels += it } }

        store.accept(GameStore.Intent.Revive)
        runCurrent()
        store.accept(GameStore.Intent.Revive)
        runCurrent()
        deps.engine.restore(deps.engine.state.value.copy(isGameOver = true))
        runCurrent()

        assertEquals(1, labels.filterIsInstance<GameStore.Label.ReviveFailed>().size)
        assertEquals(1, labels.filterIsInstance<GameStore.Label.ReviveCompleted>().size)
        assertEquals(1, labels.filterIsInstance<GameStore.Label.GameCompleted>().size)
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

    private fun knownStarterSeed(engine: GameEngine): Long =
        (0L until 1_000L).first { seed ->
            engine.startNewGame(seed = seed, allowStarterLayout = true)
            !engine.state.value.grid.isBoardEmpty()
        }

    /** All collaborators wired together so each test gets a fresh engine + factory. */
    private inner class TestDeps(
        savedState: GameState? = null,
        settingsBest: Long = 0L,
        reviewCount: Int = 0,
        tutorialSeen: Boolean = false,
        failReviewPromptCount: Boolean = false,
        saveDelayMillis: Long = 0L,
        saveRepositoryOverride: GameSaveRepository? = null,
        settingsRepositoryOverride: SettingsRepository? = null,
    ) {
        val operations = mutableListOf<String>()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val saveRepo = StubSaveRepo(
            initial = savedState,
            saveDelayMillis = saveDelayMillis,
            onSave = { operations += "save" },
        )
        private val saveRepository = saveRepositoryOverride ?: saveRepo
        val settings = FakeSettings(
            bestScore = settingsBest,
            reviewPromptCount = reviewCount,
            tutorialSeenInitially = tutorialSeen,
            failReviewPromptCount = failReviewPromptCount,
            onBestScoreSet = { operations += "best" },
        )
        private val settingsRepository = settingsRepositoryOverride ?: settings
        val audio = RecordingAudio()
        val analytics = RecordingAnalytics()
        val storeReview = NoopStoreReview()
        val engine = GameEngine(
            shapeGenerator = OneByOneGenerator(),
            scoreCalculator = ScoreCalculator(),
            saveRepository = saveRepository,
            externalScope = scope,
        )

        fun factory(): GameStoreFactory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = engine,
            audio = audio,
            saveRepository = saveRepository,
            settings = settingsRepository,
            analytics = analytics,
        )

        fun dispose() { scope.cancel() }
    }
}

private class BlockingFirstSaveRepository : GameSaveRepository {
    private val firstSaveRelease = CompletableDeferred<Unit>()

    val startedStates = mutableListOf<GameState>()
    val completedStates = mutableListOf<GameState>()
    var stored: GameState? = null
        private set

    override suspend fun save(state: GameState) {
        startedStates += state
        if (startedStates.size == 1) {
            withContext(NonCancellable) {
                firstSaveRelease.await()
            }
        }
        completedStates += state
        stored = state
    }

    override suspend fun load(): GameState? = stored

    override suspend fun clear() {
        stored = null
    }

    fun releaseFirstSave() {
        firstSaveRelease.complete(Unit)
    }
}

private class ThrowingSaveRepository(
    private val failure: Throwable = IllegalStateException("save failed"),
) : GameSaveRepository {
    override suspend fun save(state: GameState) {
        throw failure
    }

    override suspend fun load(): GameState? = null

    override suspend fun clear() = Unit
}

private class FailFirstSaveRepository : GameSaveRepository {
    private var attempts = 0
    private var stored: GameState? = null
    override suspend fun save(state: GameState) {
        attempts += 1
        if (attempts == 1) error("first save failed")
        stored = state
    }
    override suspend fun load(): GameState? = stored
    override suspend fun clear() { stored = null }
}

private class ThrowingBestSettingsRepository : SettingsRepository {
    override val musicEnabled = MutableStateFlow(true).asStateFlow()
    override val sfxEnabled = MutableStateFlow(true).asStateFlow()
    override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
    override val darkTheme = MutableStateFlow(false).asStateFlow()
    override val adsEnabled = MutableStateFlow(true).asStateFlow()
    override val bestScore = MutableStateFlow(0L).asStateFlow()
    override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
    override val tutorialSeen = MutableStateFlow(false).asStateFlow()
    override suspend fun setMusicEnabled(enabled: Boolean) = Unit
    override suspend fun setSfxEnabled(enabled: Boolean) = Unit
    override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
    override suspend fun setDarkTheme(enabled: Boolean) = Unit
    override suspend fun setAdsEnabled(enabled: Boolean) = Unit
    override suspend fun setBestScore(score: Long) {
        if (score > 0L) error("best score failed")
    }
    override suspend fun incrementReviewPromptCount() = Unit
    override suspend fun suppressReviewPrompts(max: Int) = Unit
    override suspend fun setTutorialSeen() = Unit
}

private class OneByOneGenerator : ShapeGenerator {
    private val one = Polyomino("1x1", listOf(Position(0, 0)))
    override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
    override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
}

private class StubSaveRepo(
    initial: GameState? = null,
    private val saveDelayMillis: Long = 0L,
    private val onSave: () -> Unit = {},
) : GameSaveRepository {
    private var stored: GameState? = initial
    var saveCount: Int = 0
        private set
    val saved: GameState? get() = stored
    override suspend fun save(state: GameState) {
        withContext(NonCancellable) {
            if (saveDelayMillis > 0L) delay(saveDelayMillis)
            stored = state
            saveCount += 1
            onSave()
        }
    }
    override suspend fun load(): GameState? = stored
    override suspend fun clear() { stored = null }
}

private class FakeSettings(
    bestScore: Long = 0L,
    reviewPromptCount: Int = 0,
    tutorialSeenInitially: Boolean = false,
    private val failReviewPromptCount: Boolean = false,
    private val onBestScoreSet: () -> Unit = {},
) : SettingsRepository {
    private val bestScoreFlow = MutableStateFlow(bestScore)
    private val reviewFlow = MutableStateFlow(reviewPromptCount)
    override val musicEnabled = MutableStateFlow(true).asStateFlow()
    override val sfxEnabled = MutableStateFlow(true).asStateFlow()
    override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
    override val darkTheme = MutableStateFlow(false).asStateFlow()
    override val adsEnabled = MutableStateFlow(true).asStateFlow()
    override val bestScore: StateFlow<Long> = bestScoreFlow.asStateFlow()
    override val reviewPromptCount: StateFlow<Int> = reviewFlow.asStateFlow()
    override val tutorialSeen = MutableStateFlow(tutorialSeenInitially).asStateFlow()
    override suspend fun setMusicEnabled(enabled: Boolean) {}
    override suspend fun setSfxEnabled(enabled: Boolean) {}
    override suspend fun setVibrationEnabled(enabled: Boolean) {}
    override suspend fun setDarkTheme(enabled: Boolean) {}
    override suspend fun setAdsEnabled(enabled: Boolean) {}
    override suspend fun setBestScore(score: Long) {
        onBestScoreSet()
        if (score > bestScoreFlow.value) bestScoreFlow.value = score
    }
    override suspend fun incrementReviewPromptCount() {
        if (failReviewPromptCount) error("review prompt count failed")
        reviewFlow.value += 1
    }
    override suspend fun suppressReviewPrompts(max: Int) { if (reviewFlow.value < max) reviewFlow.value = max }
    override suspend fun setTutorialSeen() {}
}

private class RecordingAudio : AudioRepository {
    var placementCount = 0
    val clearedLines = mutableListOf<Int>()
    val voices = mutableListOf<FeedbackType>()
    val calls = mutableListOf<String>()
    var startMusicCount = 0
    var stopMusicCount = 0
    override suspend fun playPlacementSound() {
        placementCount += 1
        calls += "placement"
    }
    override suspend fun playClearSound(lines: Int) {
        clearedLines += lines
        calls += "clear:$lines"
    }
    override suspend fun playVoiceFeedback(type: FeedbackType) {
        voices += type
        calls += "voice:${type.name}"
    }
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
