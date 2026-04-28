package ge.yet.blockblast.feature.home.store

import com.arkivanov.mvikotlin.core.store.Store

internal interface HomeStore : Store<HomeStore.Intent, HomeStore.State, Nothing> {

    data class State(
        val bestScore: Long = 0L,
        val hasSavedGame: Boolean = false,
    )

    sealed interface Intent {
        /** UI re-trigger (e.g. screen returned to foreground). */
        data object Refresh : Intent
    }

    sealed interface Action {
        /** First load on store creation, fired by the bootstrapper. */
        data object LoadStarted : Action
    }

    sealed interface Msg {
        data class Loaded(val bestScore: Long, val hasSavedGame: Boolean) : Msg
    }

}
