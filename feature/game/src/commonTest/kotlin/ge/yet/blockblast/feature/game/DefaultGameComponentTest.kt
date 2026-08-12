package ge.yet.blockblast.feature.game

import com.app.common.config.AppConfig
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.game.domain.engine.GameSessionReducer
import ge.yet.game.domain.engine.ScoreCalculator
import ge.yet.game.domain.engine.ShapeGenerator
import ge.yet.game.domain.model.FeedbackType
import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Piece
import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.model.Position
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.GameSaveRepository
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGameComponentTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun build(
        isNewGame: Boolean = true,
        reviewCount: Int = 0,
        bestScore: Long = 0L,
        restoredResultState: GameState? = null,
        savedState: GameState? = null,
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val analytics = RecordingAnalytics()
        val audio = RecordingAudio()
        val settings = FakeSettings(bestScore = bestScore, reviewPromptCount = reviewCount)
        val save = StubSaveRepo(savedState)
        val generator = OneByOneGenerator()
        val storeFactory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            gameReducer = GameSessionReducer(generator, ScoreCalculator()),
            audio = audio,
            saveRepository = save,
            settings = settings,
            analytics = analytics,
        )
        val settingsCalls = mutableListOf<Unit>()
        val exitCalls = mutableListOf<Unit>()
        val completions = mutableListOf<Triple<GameState, Boolean, Boolean>>()
        val reviveCompletions = mutableListOf<GameState>()
        val component = DefaultGameComponent(
            componentContext = DefaultComponentContext(lifecycle),
            gameStoreFactory = storeFactory,
            audio = audio,
            analytics = analytics,
            isNewGame = isNewGame,
            restoredResultState = restoredResultState,
            onSettingsClick = { settingsCalls += Unit },
            onExitClickedCb = { exitCalls += Unit },
            onGameCompletedCb = { finalState, canContinue, shouldRequestReview ->
                completions += Triple(finalState, canContinue, shouldRequestReview)
            },
            onReviveCompletedCb = { reviveCompletions += it },
            onReviveFailedCb = {},
        )
        return Setup(
            component,
            lifecycle,
            scope,
            audio,
            analytics,
            settings,
            settingsCalls,
            exitCalls,
            completions,
            reviveCompletions,
            save,
        )
    }

    // ── Navigation ──────────────────────────────────────────────────

    @Test
    fun onSettingsClicked_invokes_parent_callback_and_logs() {
        val s = build()
        s.component.onSettingsClicked()
        assertEquals(1, s.settingsCalls.size)
        assertNotNull(s.analytics.events.find { it.first == "settings_opened" })
        s.dispose()
    }

    // ── Exit ─────────────────────────────────────────────────────────────

    @Test
    fun onExitClicked_invokes_callback_and_logs() {
        val s = build()
        s.component.onExitClicked()
        assertEquals(1, s.exitCalls.size)
        assertNotNull(s.analytics.events.find { it.first == "exit_clicked" })
        s.dispose()
    }

    // ── Intent forwarding ────────────────────────────────────────────────

    @Test
    fun onCellClicked_forwards_Place_intent_to_store() {
        val s = build()
        val piece = s.component.model.value.game.currentPieces.first()
        s.component.onCellClicked(piece.pieceId, 0, 0)
        assertTrue(s.analytics.events.any { it.first == "piece_place_success" })
        s.dispose()
    }

    @Test
    fun onRestartClicked_starts_new_round_via_store() {
        val s = build()
        val piece = s.component.model.value.game.currentPieces.first()
        s.component.onCellClicked(piece.pieceId, 0, 0)
        assertTrue(s.component.model.value.game.score > 0)
        s.component.onRestartClicked()
        assertEquals(0L, s.component.model.value.game.score)
        s.dispose()
    }

    @Test
    fun onReviveClicked_persists_playable_state_before_notifying_parent() = runTest(testDispatcher) {
        val terminal = GameState(isGameOver = true)
        val s = build(restoredResultState = terminal)
        s.component.onReviveClicked()
        runCurrent()

        val state = s.component.model.value.game
        assertEquals(false, state.isGameOver)
        assertEquals(1, state.revivesUsed)
        assertEquals(listOf(state), s.reviveCompletions)
        assertEquals(state, s.save.stored)
        s.dispose()
    }

    @Test
    fun onReviveClicked_keeps_result_when_revive_is_unavailable() = runTest(testDispatcher) {
        val finalState = GameState(
            isGameOver = true,
            revivesUsed = GameState.MAX_REVIVES,
        )
        val s = build(restoredResultState = finalState)
        s.component.onReviveClicked()
        runCurrent()

        assertTrue(s.component.model.value.game.isGameOver)
        assertEquals(GameState.MAX_REVIVES, s.component.model.value.game.revivesUsed)
        assertTrue(s.reviveCompletions.isEmpty())
        s.dispose()
    }

    @Test
    fun game_completion_label_invokes_parent_callback_with_snapshot() = runTest(testDispatcher) {
        val s = build(isNewGame = false, savedState = stateOneMoveFromGameOver(score = 123L))
        s.component.onCellClicked(pieceId = 1, x = 1, y = 0)
        runCurrent()
        val completion = s.completions.single()
        assertTrue(completion.first.isGameOver)
        assertEquals(true, completion.second)
        assertEquals(false, completion.third)
        s.dispose()
    }

    // ── Review prompt sheet ──────────────────────────────────────────────

    @Test
    fun qualifying_game_over_navigates_immediately_with_result_review_flag() = runTest(testDispatcher) {
        val qualifyingScore =
            AppConfig.REVIEW_MIN_SCORE.toLong() + AppConfig.REVIEW_BEST_SCORE_DELTA + 10L
        val s = build(
            isNewGame = false,
            reviewCount = 0,
            savedState = stateOneMoveFromGameOver(score = qualifyingScore).copy(
                bestScore = qualifyingScore,
                bestAtRoundStart = 0,
            ),
        )
        s.component.onCellClicked(pieceId = 1, x = 1, y = 0)
        runCurrent()
        assertEquals(1, s.completions.size)
        assertTrue(s.completions.single().third)
        assertEquals(1, s.settings.reviewPromptCount.value)
        assertTrue(s.component.model.value.game.reviewPromptFiredThisRound)
        assertNull(s.analytics.events.find { it.first == "review_prompt_shown" })
        s.dispose()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Test
    fun destroy_stops_music() = runTest(testDispatcher) {
        val s = build()
        s.lifecycle.resume()
        s.audio.stopMusicCount = 0
        s.lifecycle.destroy()
        runCurrent()
        assertTrue(s.audio.stopMusicCount >= 1)
        s.dispose()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private data class Setup(
        val component: DefaultGameComponent,
        val lifecycle: LifecycleRegistry,
        val scope: CoroutineScope,
        val audio: RecordingAudio,
        val analytics: RecordingAnalytics,
        val settings: FakeSettings,
        val settingsCalls: MutableList<Unit>,
        val exitCalls: MutableList<Unit>,
        val completions: MutableList<Triple<GameState, Boolean, Boolean>>,
        val reviveCompletions: MutableList<GameState>,
        val save: StubSaveRepo,
    ) {
        fun dispose() { scope.cancel() }
    }

    private class OneByOneGenerator : ShapeGenerator {
        private val one = Polyomino("1x1", listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
        override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
    }

    private fun stateOneMoveFromGameOver(score: Long): GameState {
        var grid = Grid()
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
            if ((x + y) % 2 == 0) grid = grid.withCell(x, y, 3)
        }
        val single = Polyomino("single", listOf(Position(0, 0)))
        val horizontalTwo = Polyomino(
            "horizontal_two",
            listOf(Position(0, 0), Position(1, 0)),
        )
        return GameState(
            grid = grid,
            score = score,
            bestScore = score,
            currentPieces = listOf(Piece(1, single, 1), Piece(2, horizontalTwo, 2)),
            nextPieceId = 2,
        )
    }

    private class StubSaveRepo(initial: GameState? = null) : GameSaveRepository {
        var stored: GameState? = initial
            private set
        override suspend fun save(state: GameState) { stored = state }
        override suspend fun load(): GameState? = stored
        override suspend fun clear() { stored = null }
    }

    private class FakeSettings(bestScore: Long = 0L, reviewPromptCount: Int = 0) : SettingsRepository {
        private val bestScoreFlow = MutableStateFlow(bestScore)
        private val reviewFlow = MutableStateFlow(reviewPromptCount)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val adsEnabled = MutableStateFlow(true).asStateFlow()
        override val bestScore: StateFlow<Long> = bestScoreFlow.asStateFlow()
        override val reviewPromptCount: StateFlow<Int> = reviewFlow.asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) {}
        override suspend fun setSfxEnabled(enabled: Boolean) {}
        override suspend fun setVibrationEnabled(enabled: Boolean) {}
        override suspend fun setDarkTheme(enabled: Boolean) {}
        override suspend fun setAdsEnabled(enabled: Boolean) {}
        override suspend fun setBestScore(score: Long) {
            if (score > bestScoreFlow.value) bestScoreFlow.value = score
        }
        override suspend fun incrementReviewPromptCount() { reviewFlow.value += 1 }
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() {}
    }

    private class RecordingAudio : AudioRepository {
        var stopMusicCount = 0
        override suspend fun playVoiceFeedback(type: FeedbackType) {}
        override suspend fun startMusic() {}
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
    }

}
