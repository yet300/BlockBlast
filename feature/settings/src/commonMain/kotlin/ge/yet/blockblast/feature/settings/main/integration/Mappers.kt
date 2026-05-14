package ge.yet.blockblast.feature.settings.main.integration

import ge.yet.blockblast.feature.settings.main.MainSettingsComponent
import ge.yet.blockblast.feature.settings.main.store.SettingsStore

internal val stateToModel: (SettingsStore.State) -> MainSettingsComponent.Model =
    { state ->
        MainSettingsComponent.Model(
            soundEnabled = state.sound,
            vibrationEnabled = state.vibration,
            darkTheme = state.dark,
        )
    }
