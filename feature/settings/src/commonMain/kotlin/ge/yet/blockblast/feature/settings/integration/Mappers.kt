package ge.yet.blockblast.feature.settings.integration

import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blockblast.feature.settings.store.SettingsStore

internal val stateToModel: (SettingsStore.State) -> SettingsComponent.Model =
    { state ->
        SettingsComponent.Model(
            soundEnabled = state.sound,
            vibrationEnabled = state.vibration,
            darkTheme = state.dark,
        )
    }
