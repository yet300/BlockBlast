package ge.yet.blockblast.feature.settings.more

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMoreSettingsComponentTest {

    @Test
    fun model_tracks_repository_ads_state() = runTest {
        val setup = build(adsEnabled = true)

        assertTrue(setup.component.model.value.adsEnabled)

        setup.settings.setAdsEnabled(false)
        runCurrent()

        assertFalse(setup.component.model.value.adsEnabled)
    }

    @Test
    fun turning_ads_off_requests_confirmation_without_writing() = runTest {
        val setup = build(adsEnabled = true)

        setup.component.onAdsToggled(false)

        assertEquals(1, setup.disableAdsRequests)
        assertEquals(0, setup.settings.adsWriteCount)
        assertTrue(setup.settings.adsEnabled.value)
    }

    @Test
    fun turning_ads_on_persists_immediately() = runTest {
        val setup = build(adsEnabled = false)

        setup.component.onAdsToggled(true)
        runCurrent()

        assertTrue(setup.settings.adsEnabled.value)
        assertEquals(1, setup.settings.adsWriteCount)
        assertEquals(listOf("settings_ads_enabled"), setup.analytics.events)
    }

    @Test
    fun support_click_is_reported() = runTest {
        val setup = build(adsEnabled = false)

        setup.component.onSupportClicked()

        assertEquals(listOf("settings_support_clicked"), setup.analytics.events)
    }

    private fun kotlinx.coroutines.test.TestScope.build(adsEnabled: Boolean): Setup {
        val settings = FakeSettings(adsEnabled)
        val analytics = RecordingAnalytics()
        var disableAdsRequests = 0
        val component = DefaultMoreSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            settings = settings,
            analytics = analytics,
            onDisableAdsRequestedCb = { disableAdsRequests++ },
            onLibrariesClickedCb = {},
            onBackClickedCb = {},
            coroutineScope = backgroundScope,
        )
        return Setup(
            component = component,
            settings = settings,
            analytics = analytics,
            disableAdsRequestsProvider = { disableAdsRequests },
        )
    }

    private data class Setup(
        val component: DefaultMoreSettingsComponent,
        val settings: FakeSettings,
        val analytics: RecordingAnalytics,
        val disableAdsRequestsProvider: () -> Int,
    ) {
        val disableAdsRequests: Int get() = disableAdsRequestsProvider()
    }

    private class FakeSettings(adsEnabled: Boolean) : SettingsRepository {
        private val adsFlow = MutableStateFlow(adsEnabled)
        var adsWriteCount = 0
            private set

        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val adsEnabled = adsFlow.asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()

        override suspend fun setMusicEnabled(enabled: Boolean) = Unit
        override suspend fun setSfxEnabled(enabled: Boolean) = Unit
        override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
        override suspend fun setDarkTheme(enabled: Boolean) = Unit
        override suspend fun setAdsEnabled(enabled: Boolean) {
            adsWriteCount++
            adsFlow.value = enabled
        }
        override suspend fun setBestScore(score: Long) = Unit
        override suspend fun incrementReviewPromptCount() = Unit
        override suspend fun suppressReviewPrompts(max: Int) = Unit
        override suspend fun setTutorialSeen() = Unit
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<String>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName
        }
        override fun deleteData() = Unit
    }
}
