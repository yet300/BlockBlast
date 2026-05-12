package ge.yet.blockblast.feature.settings.integration

import ge.yet.blockblast.feature.settings.MainSettingsComponent
import ge.yet.blockblast.feature.settings.store.SettingsStore

internal val stateToModel: (SettingsStore.State) -> MainSettingsComponent.Model =
    { state ->
        MainSettingsComponent.Model(
            soundEnabled = state.sound,
            vibrationEnabled = state.vibration,
            darkTheme = state.dark,
        )
    }
