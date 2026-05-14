package ge.yet.blockblast.feature.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.blockblast.feature.settings.store.SettingsStore
import ge.yet.blockblast.feature.settings.store.SettingsStoreFactory
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
        assertTrue(component.model.value.soundEnabled)
        assertTrue(component.model.value.vibrationEnabled)
        assertFalse(component.model.value.darkTheme)
    }

    @Test
    fun onSoundToggled_propagates_to_repository_and_model() = runTest {
        val (component, settings, _) = build()
        component.onSoundToggled(false)
        assertFalse(settings.soundFlow.value)
        assertFalse(component.model.value.soundEnabled)
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
        val soundFlow = MutableStateFlow(true)
        val vibrationFlow = MutableStateFlow(true)
        val darkFlow = MutableStateFlow(false)
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

    private class NoopAnalytics : AnalyticRepository {
        override fun logEvent(eventName: String, params: Map<String, Any>?) {}
        override fun deleteData() {}
    }
}
