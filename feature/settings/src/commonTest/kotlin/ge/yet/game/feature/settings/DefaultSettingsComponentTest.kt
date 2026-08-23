package ge.yet.game.feature.settings

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ge.yet.game.feature.settings.libraries.LibrariesProvider
import ge.yet.game.feature.settings.main.store.SettingsStoreFactory
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.feature.settings.DefaultSettingsComponent
import ge.yet.game.feature.settings.SettingsComponent
import ge.yet.game.miniapp.api.MiniAppDataResetResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultSettingsComponentTest {

    @Test
    fun disable_ads_request_pushes_one_confirmation_child_and_back_keeps_ads_enabled() {
        val settings = FakeSettings()
        val analytics = RecordingAnalytics()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            storeFactory = SettingsStoreFactory(
                storeFactory = DefaultStoreFactory(),
                settingsRepository = settings,
                analytics = analytics,
            ),
            librariesProvider = LibrariesProvider { emptyList() },
            settingsRepository = settings,
            analytics = analytics,
            clearGameData = { MiniAppDataResetResult.Success },
            onBackClickedCb = {},
        )

        assertIs<SettingsComponent.Child.Main>(component.stack.value.active.instance)
            .component
            .onMoreClicked()
        val more = assertIs<SettingsComponent.Child.More>(
            component.stack.value.active.instance,
        ).component

        more.onAdsToggled(false)
        more.onAdsToggled(false)

        assertIs<SettingsComponent.Child.DisableAds>(
            component.stack.value.active.instance,
        )
        assertEquals(3, component.stack.value.items.size)
        assertEquals(
            1,
            analytics.events.count { it == "settings_disable_ads_opened" },
        )

        component.onBackClicked()

        assertIs<SettingsComponent.Child.More>(component.stack.value.active.instance)
        assertTrue(settings.adsEnabled.value)
    }

    @Test
    fun reset_request_pushes_one_child_and_back_before_confirmation_does_not_clear() {
        val settings = FakeSettings()
        val analytics = RecordingAnalytics()
        var clearCalls = 0
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            storeFactory = SettingsStoreFactory(DefaultStoreFactory(), settings, analytics),
            librariesProvider = LibrariesProvider { emptyList() },
            settingsRepository = settings,
            analytics = analytics,
            clearGameData = {
                clearCalls += 1
                MiniAppDataResetResult.Success
            },
            onBackClickedCb = {},
        )

        assertIs<SettingsComponent.Child.Main>(component.stack.value.active.instance)
            .component.onMoreClicked()
        val more = assertIs<SettingsComponent.Child.More>(component.stack.value.active.instance).component
        more.onResetGameDataClicked()
        more.onResetGameDataClicked()

        assertIs<SettingsComponent.Child.ResetGameData>(component.stack.value.active.instance)
        assertEquals(3, component.stack.value.items.size)
        component.onBackClicked()
        assertIs<SettingsComponent.Child.More>(component.stack.value.active.instance)
        assertEquals(0, clearCalls)
    }

    private class FakeSettings : SettingsRepository {
        private val adsFlow = MutableStateFlow(true)
        override val musicEnabled = MutableStateFlow(true).asStateFlow()
        override val sfxEnabled = MutableStateFlow(true).asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val adsEnabled = adsFlow.asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) = Unit
        override suspend fun setSfxEnabled(enabled: Boolean) = Unit
        override suspend fun setVibrationEnabled(enabled: Boolean) = Unit
        override suspend fun setDarkTheme(enabled: Boolean) = Unit
        override suspend fun setAdsEnabled(enabled: Boolean) {
            adsFlow.value = enabled
        }
    }

    private class RecordingAnalytics : AnalyticRepository {
        val events = mutableListOf<String>()
        override fun logEvent(eventName: String, params: Map<String, Any>?) {
            events += eventName
        }
        override fun deleteData() = Unit
    }
}
