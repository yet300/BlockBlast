package ge.yet.blockblast.feature.settings.main.store

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
        music: Boolean = true,
        sfx: Boolean = true,
        vibration: Boolean = true,
        dark: Boolean = false,
    ): Triple<SettingsStoreFactory, FakeSettings, RecordingAnalytics> {
        val settings = FakeSettings(music, sfx, vibration, dark)
        val analytics = RecordingAnalytics()
        return Triple(
            SettingsStoreFactory(DefaultStoreFactory(), settings, analytics),
            settings,
            analytics,
        )
    }

    @Test
    fun initial_state_mirrors_settings() = runTest {
        val (f, _, _) = make(music = false, sfx = true, vibration = true, dark = true)
        val store = f.create()
        assertFalse(store.state.music)
        assertTrue(store.state.sfx)
        assertTrue(store.state.vibration)
        assertTrue(store.state.dark)
    }

    @Test
    fun external_music_change_propagates_to_state() = runTest {
        val (f, settings, _) = make()
        val store = f.create()
        settings.musicFlow.value = false
        assertFalse(store.state.music)
        assertTrue(store.state.sfx)
    }

    @Test
    fun setMusic_writes_and_logs() = runTest {
        val (f, settings, analytics) = make()
        val store = f.create()
        store.accept(SettingsStore.Intent.SetMusic(false))
        assertFalse(settings.musicFlow.value)
        val ev = analytics.events.last()
        assertEquals("setting_changed", ev.first)
        assertEquals("music", ev.second["setting"])
        assertEquals(false, ev.second["enabled"])
    }

    @Test
    fun setSfx_writes_and_logs() = runTest {
        val (f, settings, analytics) = make()
        val store = f.create()
        store.accept(SettingsStore.Intent.SetSfx(false))
        assertFalse(settings.sfxFlow.value)
        assertNotNull(analytics.events.find { it.first == "setting_changed" && it.second["setting"] == "sfx" })
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
        music: Boolean,
        sfx: Boolean,
        vibration: Boolean,
        dark: Boolean,
    ) : SettingsRepository {
        val musicFlow = MutableStateFlow(music)
        val sfxFlow = MutableStateFlow(sfx)
        val vibrationFlow = MutableStateFlow(vibration)
        val darkFlow = MutableStateFlow(dark)
        override val musicEnabled: StateFlow<Boolean> = musicFlow.asStateFlow()
        override val sfxEnabled: StateFlow<Boolean> = sfxFlow.asStateFlow()
        override val vibrationEnabled: StateFlow<Boolean> = vibrationFlow.asStateFlow()
        override val darkTheme: StateFlow<Boolean> = darkFlow.asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) { musicFlow.value = enabled }
        override suspend fun setSfxEnabled(enabled: Boolean) { sfxFlow.value = enabled }
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
