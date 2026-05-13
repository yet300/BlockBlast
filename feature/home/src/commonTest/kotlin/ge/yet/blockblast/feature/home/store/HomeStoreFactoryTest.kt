package ge.yet.blockblast.feature.home.store

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStoreFactoryTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun factory(
        saved: GameState? = null,
        settingsBest: Long = 0L,
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): Pair<HomeStoreFactory, RecordingAnalytics> {
        val f = HomeStoreFactory(
            storeFactory = DefaultStoreFactory(),
            saveRepository = StubSaveRepo(saved),
            settings = StubSettings(bestScore = settingsBest),
            analytics = analytics,
        )
        return f to analytics
    }

    private fun playableState(best: Long = 0L): GameState = GameState(
        grid = Grid().withCell(0, 0, 1),
        bestScore = best,
        currentPieces = listOf(
            Piece(1L, Polyomino("h2", listOf(Position(0, 0), Position(1, 0))), 1),
        ),
        isGameOver = false,
    )

    @Test
    fun no_save_yields_hasSavedGame_false_and_uses_settings_best() = runTest {
        val (f, _) = factory(saved = null, settingsBest = 750L)
        val store = f.create()
        assertEquals(750L, store.state.bestScore)
        assertFalse(store.state.hasSavedGame)
    }

    @Test
    fun playable_save_yields_hasSavedGame_true() = runTest {
        val (f, _) = factory(saved = playableState(best = 1000L), settingsBest = 500L)
        val store = f.create()
        assertTrue(store.state.hasSavedGame)
        assertEquals(1000L, store.state.bestScore) // max
    }

    @Test
    fun bestScore_is_max_of_settings_and_save() = runTest {
        val (f, _) = factory(saved = playableState(best = 300L), settingsBest = 800L)
        val store = f.create()
        assertEquals(800L, store.state.bestScore)
    }

    @Test
    fun game_over_save_yields_hasSavedGame_false() = runTest {
        val (f, _) = factory(saved = playableState().copy(isGameOver = true))
        val store = f.create()
        assertFalse(store.state.hasSavedGame)
    }

    @Test
    fun empty_grid_save_yields_hasSavedGame_false() = runTest {
        val (f, _) = factory(
            saved = playableState().copy(grid = Grid()),
        )
        val store = f.create()
        assertFalse(store.state.hasSavedGame)
    }

    @Test
    fun refresh_re_reads_state_and_logs_home_shown() = runTest {
        val (f, analytics) = factory(saved = playableState(best = 600L), settingsBest = 100L)
        val store = f.create()
        store.accept(HomeStore.Intent.Refresh)
        assertTrue(store.state.hasSavedGame)
        assertEquals(600L, store.state.bestScore)
        val event = analytics.events.lastOrNull { it.first == "home_shown" }
        assertNotNull(event)
        assertEquals(600L, event.second["best_score"])
        assertEquals(true, event.second["has_saved_game"])
    }

    @Test
    fun initial_load_does_not_log_home_shown() = runTest {
        // home_shown is only emitted on explicit Refresh per current design.
        val (_, analytics) = factory(saved = playableState())
        assertTrue(analytics.events.none { it.first == "home_shown" })
    }

    // ── Fakes ────────────────────────────────────────────────────────────

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
