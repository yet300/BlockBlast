package ge.yet.blockblast.feature.settings.main

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blockblast.feature.settings.main.store.SettingsStore
import ge.yet.blockblast.feature.settings.main.store.SettingsStoreFactory
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMainSettingsComponentTest {

    @BeforeTest
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun build(): Triple<DefaultMainSettingsComponent, FakeSettings, MutableList<String>> {
        val settings = FakeSettings()
        val store = SettingsStoreFactory(
            storeFactory = DefaultStoreFactory(),
            settingsRepository = settings,
            analytics = NoopAnalytics(),
        ).create()
        val nav = mutableListOf<String>()
        val component = DefaultMainSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            store = store,
            onMoreClickedCb = { nav += "more" },
            onBackClickedCb = { nav += "back" },
        )
        return Triple(component, settings, nav)
    }

    @Test
    fun model_reflects_initial_state() = runTest {
        val (component, _, _) = build()
        assertTrue(component.model.value.musicEnabled)
        assertTrue(component.model.value.sfxEnabled)
        assertTrue(component.model.value.vibrationEnabled)
        assertFalse(component.model.value.darkTheme)
    }

    @Test
    fun onMusicToggled_propagates_to_repository_and_model() = runTest {
        val (component, settings, _) = build()
        component.onMusicToggled(false)
        assertFalse(settings.musicFlow.value)
        assertFalse(component.model.value.musicEnabled)
        assertTrue(component.model.value.sfxEnabled)
    }

    @Test
    fun onSfxToggled_propagates_to_repository_and_model() = runTest {
        val (component, settings, _) = build()
        component.onSfxToggled(false)
        assertFalse(settings.sfxFlow.value)
        assertFalse(component.model.value.sfxEnabled)
        assertTrue(component.model.value.musicEnabled)
    }

    @Test
    fun onVibrationToggled_propagates() = runTest {
        val (component, settings, _) = build()
        component.onVibrationToggled(false)
        assertFalse(settings.vibrationFlow.value)
    }

    @Test
    fun onDarkThemeToggled_propagates() = runTest {
        val (component, settings, _) = build()
        component.onDarkThemeToggled(true)
        assertTrue(settings.darkFlow.value)
    }

    @Test
    fun onMoreClicked_invokes_callback() {
        val (component, _, nav) = build()
        component.onMoreClicked()
        assertEquals(listOf("more"), nav)
    }

    @Test
    fun onBackClicked_invokes_callback() {
        val (component, _, nav) = build()
        component.onBackClicked()
        assertEquals(listOf("back"), nav)
    }

    private class FakeSettings : SettingsRepository {
        val musicFlow = MutableStateFlow(true)
        val sfxFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val darkFlow = MutableStateFlow(false)
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

    private class NoopAnalytics : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) {}
        override fun deleteData() {}
    }
}
