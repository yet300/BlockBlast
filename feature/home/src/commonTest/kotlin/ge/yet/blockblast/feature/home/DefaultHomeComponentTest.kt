package ge.yet.blockblast.feature.home

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blockblast.feature.home.store.HomeStoreFactory
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.model.Piece
import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeComponentTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun build(
        saved: GameState? = null,
        bestScore: Long = 0L,
    ): Setup {
        val lifecycle = LifecycleRegistry()
        val analytics = RecordingAnalytics()
        val component = DefaultHomeComponent(
            componentContext = DefaultComponentContext(lifecycle),
            homeStoreFactory = HomeStoreFactory(
                storeFactory = DefaultStoreFactory(),
                saveRepository = StubSaveRepo(saved),
                settings = StubSettings(bestScore),
                analytics = analytics,
            ),
            analytics = analytics,
            onContinueClickedCb = { continueCalls += it },
            onNewGameClickedCb = { newGameCalls += it },
        )
        return Setup(component, lifecycle, analytics)
    }

    private val continueCalls = mutableListOf<Boolean>()
    private val newGameCalls = mutableListOf<Boolean>()

    @Test
    fun model_reflects_initial_store_state() = runTest {
        val (component, _, _) = build(
            saved = playableSave(bestScore = 900L),
            bestScore = 100L,
        )
        assertEquals(900L, component.model.value.bestScore) // max
        assertTrue(component.model.value.hasSavedGame)
    }

    @Test
    fun onContinueClicked_invokes_callback_with_false_and_logs() {
        val (component, _, analytics) = build(saved = playableSave())
        component.onContinueClicked()
        assertEquals(listOf(false), continueCalls)
        assertNotNull(analytics.events.find { it.first == "continue_clicked" })
    }

    @Test
    fun onNewGameClicked_invokes_callback_with_true_and_logs() {
        val (component, _, analytics) = build()
        component.onNewGameClicked()
        assertEquals(listOf(true), newGameCalls)
        assertNotNull(analytics.events.find { it.first == "new_game_clicked" })
    }

    @Test
    fun lifecycle_resume_triggers_Refresh_and_home_shown_event() {
        val (_, lifecycle, analytics) = build(saved = playableSave(bestScore = 500L))
        // resume = onCreate + onStart + onResume — doOnStart fires Refresh
        lifecycle.resume()
        val home = analytics.events.firstOrNull { it.first == "home_shown" }
        assertNotNull(home)
        assertEquals(500L, home.second["best_score"])
        assertEquals(true, home.second["has_saved_game"])
    }

    @Test
    fun returning_to_home_re_fires_Refresh() {
        val (_, lifecycle, analytics) = build(saved = playableSave())
        lifecycle.resume()
        lifecycle.stop()
        analytics.events.clear()
        lifecycle.resume()
        assertNotNull(analytics.events.firstOrNull { it.first == "home_shown" })
    }

    private fun playableSave(bestScore: Long = 0L): GameState = GameState(
        grid = Grid().withCell(0, 0, 1),
        bestScore = bestScore,
        currentPieces = listOf(
            Piece(1L, Polyomino("h2", listOf(Position(0, 0), Position(1, 0))), 1),
        ),
        isGameOver = false,
    )

    private data class Setup(
        val component: DefaultHomeComponent,
        val lifecycle: LifecycleRegistry,
        val analytics: RecordingAnalytics,
    )

    private class StubSaveRepo(private val state: GameState?) : GameSaveRepository {
        override suspend fun save(state: GameState) {}
        override suspend fun load(): GameState? = state
        override suspend fun clear() {}
    }

    private class StubSettings(bestScore: Long) : SettingsRepository {
        override val soundEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val bestScore = MutableStateFlow(bestScore).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setSoundEnabled(enabled: Boolean) {}
        override suspend fun setVibrationEnabled(enabled: Boolean) {}
        override suspend fun setDarkTheme(enabled: Boolean) {}
        override suspend fun setBestScore(score: Long) {}
        override suspend fun incrementReviewPromptCount() {}
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() {}
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName to (params ?: emptyMap())
        }
        override fun deleteData() {}
    }
}
