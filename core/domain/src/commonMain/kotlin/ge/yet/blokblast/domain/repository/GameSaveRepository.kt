package ge.yet.blokblast.domain.repository

import ge.yet.blokblast.domain.model.GameState


interface GameSaveRepository {
    suspend fun save(state: GameState)
    suspend fun load(): GameState?
    suspend fun clear()
}
