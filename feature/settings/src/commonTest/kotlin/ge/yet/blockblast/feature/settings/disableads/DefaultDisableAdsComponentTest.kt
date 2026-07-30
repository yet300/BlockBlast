package ge.yet.blockblast.feature.settings.disableads

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
class DefaultDisableAdsComponentTest {

    @Test
    fun keep_ads_and_back_pop_without_writing() = runTest {
        val keepSetup = build()

        keepSetup.component.onKeepAdsClicked()

        assertEquals(1, keepSetup.backCalls)
        assertEquals(0, keepSetup.settings.adsWriteCount)
        assertTrue(keepSetup.settings.adsEnabled.value)
        assertEquals(
            listOf("settings_disable_ads_cancelled"),
            keepSetup.analytics.events,
        )

        val backSetup = build()

        backSetup.component.onBackClicked()

        assertEquals(1, backSetup.backCalls)
        assertEquals(0, backSetup.settings.adsWriteCount)
        assertTrue(backSetup.settings.adsEnabled.value)
    }

    @Test
    fun disable_anyway_persists_false_then_pops_once() = runTest {
        val setup = build()

        setup.component.onDisableAdsClicked()
        setup.component.onDisableAdsClicked()
        runCurrent()

        assertFalse(setup.settings.adsEnabled.value)
        assertEquals(1, setup.settings.adsWriteCount)
        assertEquals(1, setup.backCalls)
        assertEquals(listOf("settings_ads_disabled"), setup.analytics.events)
    }

    @Test
    fun failed_disable_stays_on_screen() = runTest {
        val setup = build(failWrites = true)

        setup.component.onDisableAdsClicked()
        runCurrent()

        assertTrue(setup.settings.adsEnabled.value)
        assertEquals(1, setup.settings.adsWriteCount)
        assertEquals(0, setup.backCalls)
        assertEquals(
            listOf("settings_ads_disable_failed"),
            setup.analytics.events,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.build(
        failWrites: Boolean = false,
    ): Setup {
        val settings = FakeSettings(failWrites)
        val analytics = RecordingAnalytics()
        var backCalls = 0
        val component = DefaultDisableAdsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            settings = settings,
            analytics = analytics,
            onBackClickedCb = { backCalls++ },
            coroutineScope = this,
        )
        return Setup(
            component = component,
            settings = settings,
            analytics = analytics,
            backCallsProvider = { backCalls },
        )
    }

    private data class Setup(
        val component: DefaultDisableAdsComponent,
        val settings: FakeSettings,
        val analytics: RecordingAnalytics,
        val backCallsProvider: () -> Int,
    ) {
        val backCalls: Int get() = backCallsProvider()
    }

    private class FakeSettings(
        private val failWrites: Boolean,
    ) : SettingsRepository {
        private val adsFlow = MutableStateFlow(true)
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
            if (failWrites) error("ads write failed")
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
