package ge.yet.blockblast.feature.home.integration

import ge.yet.blockblast.feature.home.HomeComponent
import ge.yet.blockblast.feature.home.store.HomeStore

internal val stateToModel: (HomeStore.State) -> HomeComponent.Model =
    { state ->
        HomeComponent.Model(
            bestScore = state.bestScore,
            hasSavedGame = state.hasSavedGame,
        )
    }
