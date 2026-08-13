package ge.yet.game.feature.home

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.domain.api.GameSaveApi
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.feature.home.store.HomeStoreFactory
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeComponentTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        continueCalls.clear()
        newGameCalls.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun model_reflects_initial_saved_game_status() = runTest {
        val (component, _, _) = build(hasSavedGame = true)

        assertTrue(component.model.value.hasSavedGame)
    }

    @Test
    fun onContinueClicked_invokes_callback_with_false_and_logs() {
        val (component, _, analytics) = build(hasSavedGame = true)

        component.onContinueClicked()

        assertEquals(listOf(false), continueCalls)
        val event = analytics.events.find { it.first == "continue_clicked" }
        assertNotNull(event)
        assertEquals(mapOf("has_saved_game" to true), event.second)
    }

    @Test
    fun onNewGameClicked_invokes_callback_with_true_and_logs() {
        val (component, _, analytics) = build()

        component.onNewGameClicked()

        assertEquals(listOf(true), newGameCalls)
        assertNotNull(analytics.events.find { it.first == "new_game_clicked" })
    }

    @Test
    fun lifecycle_resume_triggers_refresh_and_home_shown_event() {
        val (_, lifecycle, analytics) = build(hasSavedGame = true)

        lifecycle.resume()

        val home = analytics.events.firstOrNull { it.first == "home_shown" }
        assertNotNull(home)
        assertEquals(mapOf("has_saved_game" to true), home.second)
    }

    @Test
    fun returning_to_home_re_fires_refresh() {
        val (_, lifecycle, analytics) = build(hasSavedGame = true)
        lifecycle.resume()
        lifecycle.stop()
        analytics.events.clear()

        lifecycle.resume()

        assertNotNull(analytics.events.firstOrNull { it.first == "home_shown" })
    }

    private val continueCalls = mutableListOf<Boolean>()
    private val newGameCalls = mutableListOf<Boolean>()

    private fun build(hasSavedGame: Boolean = false): Setup {
        val lifecycle = LifecycleRegistry()
        val analytics = RecordingAnalytics()
        val component = DefaultHomeComponent(
            componentContext = DefaultComponentContext(lifecycle),
            homeStoreFactory = HomeStoreFactory(
                storeFactory = DefaultStoreFactory(),
                gameSaveApi = StubGameSaveApi(hasSavedGame),
                analytics = analytics,
            ),
            analytics = analytics,
            onContinueClickedCb = { continueCalls += it },
            onNewGameClickedCb = { newGameCalls += it },
        )
        return Setup(component, lifecycle, analytics)
    }

    private data class Setup(
        val component: DefaultHomeComponent,
        val lifecycle: LifecycleRegistry,
        val analytics: RecordingAnalytics,
    )

    private class StubGameSaveApi(
        private val hasSavedGame: Boolean,
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
