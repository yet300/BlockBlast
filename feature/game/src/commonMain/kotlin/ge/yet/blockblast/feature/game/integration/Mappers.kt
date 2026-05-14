package ge.yet.blockblast.feature.game.integration

import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.blockblast.feature.game.store.GameStoreState

internal val stateToModel: (GameStoreState) -> GameComponent.Model = {
    GameComponent.Model(
        game = it.game,
        continueCountdown = it.continueCountdown,
    )
}
