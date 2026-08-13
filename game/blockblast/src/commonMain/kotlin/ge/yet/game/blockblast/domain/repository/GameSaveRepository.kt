package ge.yet.game.blockblast.domain.repository

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.domain.api.GameSaveApi

interface GameSaveRepository : GameSaveApi {
    suspend fun save(state: GameState)
    suspend fun load(): GameState?
    suspend fun clear()

    override suspend fun hasSavedGame(): Boolean =
        load()?.let { state ->
            !state.isGameOver && !state.grid.isBoardEmpty()
        } == true
}
