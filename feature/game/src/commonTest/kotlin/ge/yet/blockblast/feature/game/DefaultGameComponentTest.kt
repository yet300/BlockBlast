package ge.yet.blockblast.feature.game

import com.app.common.config.AppConfig
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.engine.ScoreCalculator
import ge.yet.blokblast.domain.engine.ShapeGenerator
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.model.GameState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        val analytics = RecordingAnalytics()
        val audio = RecordingAudio()
        val storeReview = RecordingStoreReview()
        val settings = FakeSettings(bestScore = bestScore, reviewPromptCount = reviewCount)
        val save = StubSaveRepo()
        val engine = GameEngine(
            shapeGenerator = OneByOneGenerator(),
            scoreCalculator = ScoreCalculator(),
            saveRepository = save,
            externalScope = scope,
        )
        val storeFactory = GameStoreFactory(
            storeFactory = DefaultStoreFactory(),
            engine = engine,
            audio = audio,
            storeReview = storeReview,
            saveRepository = save,
            settings = settings,
            analytics = analytics,
        )
        val exitCalls = mutableListOf<Unit>()
        val component = DefaultGameComponent(
            componentContext = DefaultComponentContext(lifecycle),
            gameStoreFactory = storeFactory,
            settingsComponent = StubSettingsFactory(),
            audio = audio,
            settings = settings,
            storeReview = storeReview,
            analytics = analytics,
            isNewGame = isNewGame,
            onExitClickedCb = { exitCalls += Unit },
        )
        return Setup(component, lifecycle, scope, engine, audio, analytics, settings, storeReview, exitCalls)
    }

    // ── Navigation: settings sheet ───────────────────────────────────────

    @Test
    fun onSettingsClicked_opens_settings_sheet_and_logs() {
        val s = build()
        s.component.onSettingsClicked()
        assertIs<GameComponent.SheetChild.Settings>(s.component.sheetSlot.value.child?.instance)
        assertNotNull(s.analytics.events.find { it.first == "settings_opened" })
        s.dispose()
    }

    @Test
    fun onDismissSheet_closes_settings_and_logs() {
        val s = build()
        s.component.onSettingsClicked()
        s.component.onDismissSheet()
        assertNull(s.component.sheetSlot.value.child)
        assertNotNull(s.analytics.events.find { it.first == "settings_closed" })
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
        val piece = s.engine.state.value.currentPieces.first()
        s.component.onCellClicked(piece.pieceId, 0, 0)
        assertTrue(s.analytics.events.any { it.first == "piece_place_success" })
        s.dispose()
    }

    @Test
    fun onRestartClicked_starts_new_round_via_engine() {
        val s = build()
        val piece = s.engine.state.value.currentPieces.first()
        s.engine.placePiece(piece.pieceId, 0, 0)
        assertTrue(s.engine.state.value.score > 0)
        s.component.onRestartClicked()
        assertEquals(0L, s.engine.state.value.score)
        s.dispose()
    }

    @Test
    fun onReviveClicked_restores_play_when_game_over() {
        val s = build()
        s.engine.restore(s.engine.state.value.copy(isGameOver = true))
        s.component.onReviveClicked()
        assertEquals(false, s.engine.state.value.isGameOver)
        assertEquals(1, s.engine.state.value.revivesUsed)
        s.dispose()
    }

    // ── Review prompt sheet ──────────────────────────────────────────────

    @Test
    fun review_request_label_activates_review_prompt_sheet() = runTest(testDispatcher) {
        val s = build(reviewCount = 0)
        // Trigger qualifying game-over → store publishes RequestReview label.
        s.engine.restore(
            s.engine.state.value.copy(
                score = AppConfig.REVIEW_MIN_SCORE + AppConfig.REVIEW_BEST_SCORE_DELTA + 10L,
                bestAtRoundStart = 0L,
                isGameOver = true,
            ),
        )
        runCurrent()
        assertIs<GameComponent.SheetChild.ReviewPrompt>(s.component.sheetSlot.value.child?.instance)
        assertNotNull(s.analytics.events.find { it.first == "review_prompt_shown" })
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
        val engine: GameEngine,
        val audio: RecordingAudio,
        val analytics: RecordingAnalytics,
        val settings: FakeSettings,
        val storeReview: RecordingStoreReview,
        val exitCalls: MutableList<Unit>,
    ) {
        fun dispose() { scope.cancel() }
    }

    private class OneByOneGenerator : ShapeGenerator {
        private val one = Polyomino("1x1", listOf(Position(0, 0)))
        override fun nextTray(seed: Long?): List<Polyomino> = listOf(one, one, one)
        override fun smallReviveTray(): List<Polyomino> = listOf(one, one, one)
    }

    private class StubSaveRepo : GameSaveRepository {
        private var stored: GameState? = null
        override suspend fun save(state: GameState) { stored = state }
        override suspend fun load(): GameState? = stored
        override suspend fun clear() { stored = null }
    }

    private class FakeSettings(bestScore: Long = 0L, reviewPromptCount: Int = 0) : SettingsRepository {
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
        override suspend fun setBestScore(score: Long) {
            if (score > bestScoreFlow.value) bestScoreFlow.value = score
        }
        override suspend fun incrementReviewPromptCount() { reviewFlow.value += 1 }
        override suspend fun setTutorialSeen() {}
    }

    private class RecordingAudio : AudioRepository {
        var stopMusicCount = 0
        override suspend fun playPlacementSound() {}
        override suspend fun playClearSound(lines: Int) {}
        override suspend fun playVoiceFeedback(type: FeedbackType) {}
        override suspend fun playVoiceCombo(combo: Int) {}
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

    private class RecordingStoreReview : StoreReviewRepository {
        var inAppRequests = 0
        override fun requestInAppReview(): Flow<ReviewCode> {
            inAppRequests += 1
            return flowOf(ReviewCode.NO_ERROR)
        }
        override fun requestInMarketReview(): Flow<ReviewCode> = flowOf(ReviewCode.NO_ERROR)
    }

    private class StubSettingsFactory : SettingsComponent.Factory {
        override fun create(
            componentContext: ComponentContext,
            onBackClicked: () -> Unit,
        ): SettingsComponent = StubSettingsComponent(componentContext, onBackClicked)
    }

    private class StubSettingsComponent(
        componentContext: ComponentContext,
        val onBack: () -> Unit,
    ) : SettingsComponent, ComponentContext by componentContext {
        // Tests inspect only the sheet wrapper type, never this stack.
        override val stack
            get() = error("StubSettingsComponent.stack must not be read in tests")
        override fun onBackClicked() = onBack()
    }
}
