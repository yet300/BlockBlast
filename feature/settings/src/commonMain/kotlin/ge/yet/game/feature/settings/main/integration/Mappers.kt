package ge.yet.game.feature.settings.main.integration

import ge.yet.game.feature.settings.main.MainSettingsComponent
import ge.yet.game.feature.settings.main.store.SettingsStore

internal val stateToModel: (SettingsStore.State) -> MainSettingsComponent.Model =
    { state ->
        MainSettingsComponent.Model(
            musicEnabled = state.music,
            sfxEnabled = state.sfx,
            vibrationEnabled = state.vibration,
            darkTheme = state.dark,
        )
    }
