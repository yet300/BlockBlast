package ge.yet.game.feature.home.store

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.domain.api.GameSaveApi
import ge.yet.game.domain.repository.AnalyticRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_load_reads_saved_game_status_from_game_api() = runTest {
        val (factory, _) = factory(hasSavedGame = true)

        val store = factory.create()

        assertTrue(store.state.hasSavedGame)
    }

    @Test
    fun initial_load_reports_missing_save() = runTest {
        val (factory, _) = factory(hasSavedGame = false)

        val store = factory.create()

        assertFalse(store.state.hasSavedGame)
    }

    @Test
    fun refresh_re_reads_status_and_logs_home_shown() = runTest {
        val gameSaveApi = MutableGameSaveApi(hasSavedGame = false)
        val (factory, analytics) = factory(gameSaveApi = gameSaveApi)
        val store = factory.create()
        gameSaveApi.hasSavedGame = true

        store.accept(HomeStore.Intent.Refresh)

        assertTrue(store.state.hasSavedGame)
        val event = analytics.events.lastOrNull { it.first == "home_shown" }
        assertNotNull(event)
        assertEquals(mapOf("has_saved_game" to true), event.second)
    }

    @Test
    fun initial_load_does_not_log_home_shown() = runTest {
        val (_, analytics) = factory(hasSavedGame = true)

        assertTrue(analytics.events.none { it.first == "home_shown" })
    }

    private fun factory(
        hasSavedGame: Boolean = false,
        gameSaveApi: GameSaveApi = MutableGameSaveApi(hasSavedGame),
        analytics: RecordingAnalytics = RecordingAnalytics(),
    ): Pair<HomeStoreFactory, RecordingAnalytics> {
        val factory = HomeStoreFactory(
            storeFactory = DefaultStoreFactory(),
            gameSaveApi = gameSaveApi,
            analytics = analytics,
        )
        return factory to analytics
    }

    private class MutableGameSaveApi(
        var hasSavedGame: Boolean,
    ) : GameSaveApi {
        override suspend fun hasSavedGame(): Boolean = hasSavedGame
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()

        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName to (params ?: emptyMap())
        }

        override fun deleteData() = Unit
    }
}
