package ge.yet.game.blockblast.domain.repository

import ge.yet.game.blockblast.domain.model.GameState


interface GameSaveRepository {
    suspend fun save(state: GameState)
    suspend fun load(): GameState?
    suspend fun clear()
}
