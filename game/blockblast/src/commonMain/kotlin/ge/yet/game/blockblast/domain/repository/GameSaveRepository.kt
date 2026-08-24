package ge.yet.game.blockblast.domain.repository

import ge.yet.game.blockblast.domain.model.GameState

internal interface GameSaveRepository {
    suspend fun save(state: GameState)
    suspend fun load(): GameState?
    suspend fun clear()

    suspend fun hasSavedGame(): Boolean =
        load()?.let { state ->
            !state.isGameOver && !state.grid.isBoardEmpty()
        } == true
}
