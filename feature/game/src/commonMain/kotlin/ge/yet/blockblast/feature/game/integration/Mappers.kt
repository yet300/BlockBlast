package ge.yet.blockblast.feature.game.integration

import ge.yet.blockblast.feature.game.GameComponent
import ge.yet.game.domain.model.GameState

internal val stateToModel: (GameState) -> GameComponent.Model = {
    GameComponent.Model(
        game = it,
    )
}
