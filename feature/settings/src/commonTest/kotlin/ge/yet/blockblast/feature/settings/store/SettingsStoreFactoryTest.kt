package ge.yet.blockblast.feature.settings.store

import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class SettingsStoreFactoryTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun make(
        sound: Boolean = true,
        vibration: Boolean = true,
        dark: Boolean = false,
    ): Triple<SettingsStoreFactory, FakeSettings, RecordingAnalytics> {
        val settings = FakeSettings(sound, vibration, dark)
        val analytics = RecordingAnalytics()
        return Triple(
            SettingsStoreFactory(DefaultStoreFactory(), settings, analytics),
            settings,
            analytics,
        )
    }

    @Test
    fun initial_state_mirrors_settings() = runTest {
        val (f, _, _) = make(sound = false, vibration = true, dark = true)
        val store = f.create()
        assertFalse(store.state.sound)
        assertTrue(store.state.vibration)
        assertTrue(store.state.dark)
    }

    @Test
    fun external_settings_change_propagates_to_state() = runTest {
        val (f, settings, _) = make()
        val store = f.create()
        settings.soundFlow.value = false
        assertFalse(store.state.sound)
    }

    @Test
    fun setSound_writes_and_logs() = runTest {
        val (f, settings, analytics) = make()
        val store = f.create()
        store.accept(SettingsStore.Intent.SetSound(false))
        assertFalse(settings.soundFlow.value)
        val ev = analytics.events.last()
        assertEquals("setting_changed", ev.first)
        assertEquals("sound", ev.second["setting"])
        assertEquals(false, ev.second["enabled"])
    }

    @Test
    fun setVibration_writes_and_logs() = runTest {
        val (f, settings, analytics) = make()
        val store = f.create()
        store.accept(SettingsStore.Intent.SetVibration(false))
        assertFalse(settings.vibrationFlow.value)
        assertNotNull(analytics.events.find { it.first == "setting_changed" && it.second["setting"] == "vibration" })
    }

    @Test
    fun setDark_writes_and_logs() = runTest {
        val (f, settings, analytics) = make()
        val store = f.create()
        store.accept(SettingsStore.Intent.SetDark(true))
        assertTrue(settings.darkFlow.value)
        assertNotNull(analytics.events.find { it.first == "setting_changed" && it.second["setting"] == "dark_theme" })
    }

    // ── Fakes ────────────────────────────────────────────────────────────

    private class FakeSettings(
        sound: Boolean,
        vibration: Boolean,
        dark: Boolean,
    ) : SettingsRepository {
        val soundFlow = MutableStateFlow(sound)
        val vibrationFlow = MutableStateFlow(vibration)
        val darkFlow = MutableStateFlow(dark)
        override val soundEnabled: StateFlow<Boolean> = soundFlow.asStateFlow()
        override val vibrationEnabled: StateFlow<Boolean> = vibrationFlow.asStateFlow()
        override val darkTheme: StateFlow<Boolean> = darkFlow.asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setSoundEnabled(enabled: Boolean) { soundFlow.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationFlow.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) { darkFlow.value = enabled }
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
