package ge.yet.blockblast.feature.settings.store

import com.arkivanov.mvikotlin.core.store.Store

internal interface SettingsStore : Store<SettingsStore.Intent, SettingsStore.State, Nothing> {


    data class State(
        val sound: Boolean = true,
        val vibration: Boolean = true,
        val dark: Boolean = false,
    )

    sealed interface Intent {
        data class SetSound(val enabled: Boolean) : Intent

        data class SetVibration(val enabled: Boolean) : Intent

        data class SetDark(val enabled: Boolean) : Intent
    }

    sealed interface Action {
        object Init : Action
    }

    sealed interface Msg {
        data class Snapshot(
            val sound: Boolean,
            val vibration: Boolean,
            val dark: Boolean,
        ) : Msg
    }
}
