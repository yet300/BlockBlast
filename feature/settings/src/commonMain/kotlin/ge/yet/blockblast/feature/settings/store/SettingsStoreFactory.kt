package ge.yet.blockblast.feature.settings.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class SettingsStoreFactory constructor(
    private val storeFactory: StoreFactory,
    private val settingsRepository: SettingsRepository,
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
                executorFactory = coroutineExecutorFactory<SettingsStore.Intent, Nothing, SettingsStore.State, SettingsStore.Msg, Nothing> {
                    onAction<Unit>(Unit) {
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
                        launch { settingsRepository.setSoundEnabled(intent.enabled) }
                    }
                    onIntent<SettingsStore.Intent.SetVibration> { intent ->
                        launch { settingsRepository.setVibrationEnabled(intent.enabled) }
                    }
                    onIntent<SettingsStore.Intent.SetDark> { intent ->
                        launch { settingsRepository.setDarkTheme(intent.enabled) }
                    }
                },
                reducer = SettingsReducer,
            ) {}


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
