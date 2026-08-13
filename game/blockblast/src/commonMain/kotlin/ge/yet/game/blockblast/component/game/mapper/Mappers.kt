package ge.yet.game.blockblast.component.game.mapper

import ge.yet.game.blockblast.component.game.GameComponent
import ge.yet.game.blockblast.domain.model.GameState

internal val stateToModel: (GameState) -> GameComponent.Model = {
    GameComponent.Model(
        game = it,
    )
}
