package ge.yet.blockblast.feature.settings.main.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Inject
internal class SettingsStoreFactory(
    private val storeFactory: StoreFactory,
    private val settingsRepository: SettingsRepository,
    private val analytics: AnalyticRepository,
) {
    fun create(): SettingsStore =
        object :
            SettingsStore,
            Store<SettingsStore.Intent, SettingsStore.State, Nothing> by storeFactory.create(
                name = "SettingsStore",
                initialState = SettingsStore.State(
                    sound = settingsRepository.soundEnabled.value,
                    vibration = settingsRepository.vibrationEnabled.value,
                    dark = settingsRepository.darkTheme.value,
                ),
                bootstrapper = SimpleBootstrapper(SettingsStore.Action.Init),
                executorFactory = coroutineExecutorFactory<SettingsStore.Intent, SettingsStore.Action, SettingsStore.State, SettingsStore.Msg, Nothing> {
                    onAction<SettingsStore.Action.Init> {
                        launch {
                            combine(
                                settingsRepository.soundEnabled,
                                settingsRepository.vibrationEnabled,
                                settingsRepository.darkTheme,
                            ) { s, v, d -> SettingsStore.Msg.Snapshot(s, v, d) }
                                .collect { dispatch(it) }
                        }
                    }
                    onIntent<SettingsStore.Intent.SetSound> { intent ->
                        logSettingChanged(setting = "sound", enabled = intent.enabled)
                        launch { settingsRepository.setSoundEnabled(intent.enabled) }
                    }
                    onIntent<SettingsStore.Intent.SetVibration> { intent ->
                        logSettingChanged(setting = "vibration", enabled = intent.enabled)
                        launch { settingsRepository.setVibrationEnabled(intent.enabled) }
                    }
                    onIntent<SettingsStore.Intent.SetDark> { intent ->
                        logSettingChanged(setting = "dark_theme", enabled = intent.enabled)
                        launch { settingsRepository.setDarkTheme(intent.enabled) }
                    }
                },
                reducer = SettingsReducer,
            ) {}

    private fun logSettingChanged(setting: String, enabled: Boolean) {
        analytics.logEvent(
            eventName = "setting_changed",
            params = mapOf(
                "setting" to setting,
                "enabled" to enabled,
            ),
        )
    }

    internal object SettingsReducer : Reducer<SettingsStore.State, SettingsStore.Msg> {
        override fun SettingsStore.State.reduce(msg: SettingsStore.Msg): SettingsStore.State =
            when (msg) {
                is SettingsStore.Msg.Snapshot -> copy(
                    sound = msg.sound,
                    vibration = msg.vibration,
                    dark = msg.dark
                )
            }
    }
}
