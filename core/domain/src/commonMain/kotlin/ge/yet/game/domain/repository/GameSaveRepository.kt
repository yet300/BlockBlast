package ge.yet.game.domain.repository

import ge.yet.game.domain.model.GameState


interface GameSaveRepository {
    suspend fun save(state: GameState)
    suspend fun load(): GameState?
    suspend fun clear()
}
