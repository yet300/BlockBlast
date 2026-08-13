package ge.yet.game.feature.home.integration

import ge.yet.game.feature.home.HomeComponent
import ge.yet.game.feature.home.store.HomeStore

internal val stateToModel: (HomeStore.State) -> HomeComponent.Model =
    { state ->
        HomeComponent.Model(
            hasSavedGame = state.hasSavedGame,
        )
    }
